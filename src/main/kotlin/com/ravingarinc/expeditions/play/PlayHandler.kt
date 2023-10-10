package com.ravingarinc.expeditions.play

import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import com.github.shynixn.mccoroutine.bukkit.ticks
import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.api.module.SuspendingModule
import com.ravingarinc.expeditions.api.Ticker
import com.ravingarinc.expeditions.api.getDuration
import com.ravingarinc.expeditions.api.getMaterialList
import com.ravingarinc.expeditions.command.ExpeditionGui
import com.ravingarinc.expeditions.integration.MultiverseHandler
import com.ravingarinc.expeditions.locale.ExpeditionManager
import com.ravingarinc.expeditions.locale.type.Expedition
import com.ravingarinc.expeditions.party.PartyManager
import com.ravingarinc.expeditions.persistent.ConfigManager
import com.ravingarinc.expeditions.play.instance.CachedPlayer
import com.ravingarinc.expeditions.play.instance.ExpeditionInstance
import com.ravingarinc.expeditions.play.instance.IdlePhase
import com.ravingarinc.expeditions.play.instance.PlayPhase
import kotlinx.coroutines.CoroutineScope
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class PlayHandler(plugin: RavinPlugin) : SuspendingModule(PlayHandler::class.java, plugin, true, ExpeditionManager::class.java) {
    private lateinit var expeditions: ExpeditionManager
    private lateinit var multiverse: MultiverseHandler
    private lateinit var manager: ConfigManager
    private lateinit var parties: PartyManager

    private var initialInstances = 1
    private var maxInstances: Int = 4
    private var tickInterval: Int = 1200

    private val instances: MutableMap<String, MutableList<ExpeditionInstance>> = ConcurrentHashMap()
    private lateinit var ticker: PlayTicker
    private val overhangingBlocks: MutableSet<Material> = HashSet()

    private val expeditionPlayers: MutableMap<Player, ExpeditionInstance> = ConcurrentHashMap()
    private val abandonedPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private val respawningPlayers: MutableMap<UUID, CachedPlayer> = ConcurrentHashMap()

    private lateinit var capacityJob: CapacityTicker

    // Todo might be worthwhile adding a 'join-queue' such that join requests aren't overloaded for specific expeditions

    override suspend fun suspendLoad() {
        manager = plugin.getModule(ConfigManager::class.java)
        parties = plugin.getModule(PartyManager::class.java)
        manager.config.consume("general") {
            tickInterval = (it.getDuration("tick-interval") ?: 1200).toInt()
            initialInstances = it.getInt("initial-instances", 1)
            maxInstances = it.getInt("max-instances", 4)
            it.getMaterialList("overhanging-blocks").forEach { mat ->
                overhangingBlocks.add(mat)
            }
            overhangingBlocks.add(Material.AIR) // Should always have air
        }
        manager.data.config.getStringList("abandoned-players").forEach {
            val uuid = UUID.fromString(it)
            abandonedPlayers.add(uuid)
        }
        expeditions = plugin.getModule(ExpeditionManager::class.java)
        multiverse = plugin.getModule(MultiverseHandler::class.java)

        ticker = PlayTicker(plugin, instances.values)
        capacityJob = CapacityTicker(plugin, this, tickInterval)

        for(type in expeditions.getMaps()) {
            val list = LinkedList<ExpeditionInstance>()
            instances[type.identifier] = list
            for(i in 1..initialInstances) {
                createInstance(type)?.let { list.add(it) }
            }
        }
        plugin.launch {
            // Render after copying the worlds... such to avoid chunk glitch issues.
            for(type in expeditions.getMaps()) {
                type.render(plugin)
            }
            capacityJob.start(tickInterval)
        }
        ticker.start()

        manager.getRespawningPlayers().forEach {
            respawningPlayers[it.player.uniqueId] = it
        }
    }

    override suspend fun suspendCancel() {
        ExpeditionGui.dispose()
        ticker.cancel()
        capacityJob.cancel()
        instances.values.forEach { list -> list.forEach { destroyInstance(it) }}
        instances.clear()

        abandonedPlayers.clear()
        respawningPlayers.clear()
        expeditionPlayers.clear()
        overhangingBlocks.clear()
    }

    fun getOverhangingBlocks() : Set<Material> {
        return overhangingBlocks
    }

    fun getInstances(): Map<String,List<ExpeditionInstance>> {
        return instances
    }

    fun addRespawn(cache: CachedPlayer) {
        this.respawningPlayers[cache.player.uniqueId] = cache
        manager.addRespawningPlayer(cache)
    }

    fun removeRespawn(player: Player) : CachedPlayer? {
        val value = this.respawningPlayers.remove(player.uniqueId)
        if(value != null) {
            manager.removeRespawningPlayer(value)
        }
        return value
    }

    fun addAbandon(uuid: UUID) {
        abandonedPlayers.add(uuid)
        manager.addAbandonedPlayer(uuid)
    }

    fun didAbandon(player: Player) : Boolean {
        return abandonedPlayers.contains(player.uniqueId)
    }

    fun removeAbandon(player: Player) {
        abandonedPlayers.remove(player.uniqueId)
        manager.removeAbandonedPlayer(player.uniqueId)
    }

    fun saveData() {
        manager.saveData()
    }

    fun tryJoinExpedition(identifier: String, player: Player) : Boolean {
        val expedition = expeditions.getMapByIdentifier(identifier) ?: return false
        val provider = parties.getProvider() ?: return joinExpedition(identifier, player)
        with(provider) {
            if(player.isInParty()) {
                if(player.isPartyLeader()) {
                    val members = player.getPartyMembers()
                    if(members.size > expedition.maxPlayers) {
                        player.sendMessage("${ChatColor.RED}Your party is too big for this expedition!")
                        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_SNARE, 0.8F, 0.5F)
                        return false
                    }
                    for(member in members) {
                        if(expedition.permission != null && !member.hasPermission(expedition.permission)) {
                            player.sendMessage("${ChatColor.RED}You cannot join this expedition as not all party members have the required permissions!")
                            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_SNARE, 0.8F, 0.5F)
                            return false
                        }
                        if(getJoinedExpedition(member) != null) {
                            player.sendMessage("${ChatColor.RED}You cannot join a new expedition whilst one of your party members is still in an expedition!")
                            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_SNARE, 0.8F, 0.5F)
                            return false
                        }
                    }
                    val list = instances[identifier]!!
                    val randomList = ArrayList(list)
                    randomList.shuffle()
                    for(i in randomList) {
                        if(i.canJoin() && i.getJoinedPlayers().size + members.size <= expedition.maxPlayers) {
                            for(member in members) i.participate(member)
                            return true
                        }
                    }
                    player.sendMessage("${ChatColor.RED}Could not find any expedition instances that have enough free slots for your party! Please try again later.")
                    player.playSound(player, Sound.BLOCK_NOTE_BLOCK_SNARE, 0.8F, 0.5F)
                    return false
                } else {
                    player.sendMessage("${ChatColor.RED}Only the party leader can perform this action!")
                    player.playSound(player, Sound.BLOCK_NOTE_BLOCK_SNARE, 0.8F, 0.5F)
                    return false
                }
            } else {
                joinExpedition(identifier, player)
            }
        }
        return true
    }


    /**
     * Try and join an expedition for a singular player, when handling parties a different method must be used.
     */
    fun joinExpedition(identifier: String, player: Player) : Boolean {
        val list = instances[identifier] ?: return false
        val randomList = ArrayList(list)
        randomList.shuffle()
        for(i in randomList) {
            if(i.canJoin()) {
                i.participate(player)
                return true
            }
        }
        return false
    }

    fun addJoinedExpedition(player: Player, instance: ExpeditionInstance) {
        this.expeditionPlayers[player] = instance
    }

    fun getJoinedExpedition(player: Player) : ExpeditionInstance? {
        return expeditionPlayers[player]
    }

    fun hasJoinedExpedition(player: Player) : Boolean {
        return getJoinedExpedition(player) != null
    }

    fun removeJoinedExpedition(player: Player) {
        expeditionPlayers.remove(player)
    }

    fun checkCapacity() {
        instances.forEach { (key, list) ->
            var total = 0
            var maxTotal = 0
            val emptyMaps = ArrayList<ExpeditionInstance>()
            for(i in list) {
                val phase = i.getPhase()
                val joined = i.getJoinedPlayers().size
                if(phase is IdlePhase) {
                    if(joined == 0) { emptyMaps.add(i) }
                } else if (phase is PlayPhase) {
                    total += joined
                    maxTotal += i.expedition.maxPlayers
                }
            }
            plugin.launch(plugin.minecraftDispatcher) {
                if(emptyMaps.size == 0 && list.size < maxInstances) {
                    if(maxTotal > 0 && (total / maxTotal.toDouble()) > 0.75) {
                        expeditions.getMapByIdentifier(key)?.let { expedition ->
                            createInstance(expedition)?.let { list.add(it) } }
                    }
                }
                emptyMaps.forEach { if(list.size > initialInstances && list.remove(it)) { destroyInstance(it) } }
            }
        }
    }

    fun isExpeditionWorld(world: World) : Boolean {
        return getInstance(world) != null
    }

    fun getInstance(world: World) : ExpeditionInstance? {
        instances.values.forEach { list ->
            list.forEach {
                if(it.world == world) {
                    return it
                }
            }
        }
        return null
    }

    fun addInstance(instance: ExpeditionInstance) {
        this.instances[instance.expedition.identifier]?.add(instance)
    }

    fun removeInstance(instance: ExpeditionInstance) {
        this.instances[instance.expedition.identifier]?.remove(instance)
    }

    fun createInstance(expedition: Expedition): ExpeditionInstance? {
        val instanceWorld = multiverse.cloneWorld(expedition.world) ?: return null
        val instance = ExpeditionInstance(plugin, expedition, instanceWorld)
        plugin.launch { instance.start() }
        return instance
    }

    /**
     * Called when the instance should be forcefully destroyed
     */
    fun destroyInstance(instance: ExpeditionInstance) {
        instance.end()
        instance.getQuitPlayers().forEach { uuid -> addAbandon(uuid) }
        saveData()
        multiverse.deleteWorld(instance.world)
    }
}

class CapacityTicker(plugin: RavinPlugin, private val handler: PlayHandler, interval: Int) : Ticker(plugin, interval.ticks) {
    override suspend fun CoroutineScope.tick() {
        handler.checkCapacity()
    }
}