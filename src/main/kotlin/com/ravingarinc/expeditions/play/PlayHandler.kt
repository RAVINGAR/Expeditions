package com.ravingarinc.expeditions.play

import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import com.github.shynixn.mccoroutine.bukkit.ticks
import com.ravingarinc.api.I
import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.api.module.SuspendingModule
import com.ravingarinc.api.module.warn
import com.ravingarinc.expeditions.api.Ticker
import com.ravingarinc.expeditions.api.formatMilliseconds
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
import com.ravingarinc.expeditions.play.render.RenderJob
import kotlinx.coroutines.CoroutineScope
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level

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

    private val lockedState = AtomicBoolean(false)

    private lateinit var capacityJob: CapacityTicker

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

        ticker = PlayTicker(plugin, this)
        capacityJob = CapacityTicker(plugin, this, tickInterval)

        for(type in expeditions.getMaps()) {
            val list = LinkedList<ExpeditionInstance>()
            instances[type.identifier.lowercase()] = list
            for(i in 1..initialInstances) {
                createInstance(type)?.let { list.add(it) }
            }
        }
        manager.getRespawningPlayers().forEach {
            respawningPlayers[it.player.uniqueId] = it
        }
        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, { ->
            plugin.launch {
                // Render after copying the worlds... such to avoid chunk glitch issues.
                for(type in expeditions.getMaps()) {
                    val startTime = System.currentTimeMillis()
                    val deferred = RenderJob.render(plugin, type.centreX, type.centreZ, type.radius, type.world, type.world.minHeight)
                    type.assignJob(deferred)
                    deferred.invokeOnCompletion {
                        if(it == null) {
                            I.log(Level.INFO, "Successfully rendered expedition map for '${type.displayName}' in ${(System.currentTimeMillis() - startTime).formatMilliseconds()}!")
                        } else {
                            I.log(Level.SEVERE, "Encountered exception whilst rendering expedition map for '${type.displayName}'!", it)
                        }

                    }
                }
                capacityJob.start()
                ticker.start()
            }
        },20L)


    }

    override suspend fun suspendCancel() {
        ExpeditionGui.dispose()
        lockedState.setRelease(true)
        ticker.cancel()
        capacityJob.cancel()
        instances.values.forEach { list -> list.forEach { destroyInstance(it) }}
        instances.clear()

        abandonedPlayers.clear()
        respawningPlayers.clear()
        expeditionPlayers.clear()
        overhangingBlocks.clear()

        lockedState.setRelease(false)
    }

    fun getAllInstances() : Collection<ExpeditionInstance> {
        return buildList {
            instances.values.forEach { list -> list.forEach { this.add(it) }}
        }
    }

    fun lockExpeditions(locked: Boolean) {
        this.lockedState.setRelease(locked)
        plugin.launch(plugin.minecraftDispatcher) {
            ExpeditionGui.refreshAll()
        }
    }

    fun areExpeditionsLocked() : Boolean {
        return this.lockedState.acquire
    }

    fun getOverhangingBlocks() : Set<Material> {
        return overhangingBlocks
    }

    fun getInstances(identifier: String) : Collection<ExpeditionInstance>? {
        return instances[identifier.lowercase()]
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

    fun isLocked() : Boolean {
        return lockedState.acquire
    }

    fun tryJoinExpedition(identifier: String, player: Player) : Boolean {
        if(lockedState.acquire) return false
        val expedition = expeditions.getMapByIdentifier(identifier) ?: return false
        val provider = parties.getProvider() ?: return joinExpedition(identifier, player)
        with(provider) {
            if(player.isInParty()) {
                if(player.isPartyLeader()) {
                    val members = player.getPartyMembers()
                    if(members.size > expedition.maxPlayers) {
                        player.sendMessage("${ChatColor.RED}Your party is too big for this expedition!")
                        return false
                    }
                    for(member in members) {
                        if(expedition.permission != null && !member.hasPermission(expedition.permission)) {
                            player.sendMessage("${ChatColor.RED}You cannot join this expedition as not all party members have the required permissions!")
                            return false
                        }
                        if(getJoinedExpedition(member) != null) {
                            player.sendMessage("${ChatColor.RED}You cannot join a new expedition whilst one of your party members is still in an expedition!")
                            return false
                        }
                    }
                    val list = instances[identifier.lowercase()]!!
                    val randomList = ArrayList(list)
                    randomList.shuffle()
                    for(i in randomList) {
                        if(i.canJoin() && i.getJoinedPlayers().size + members.size <= expedition.maxPlayers) {
                            i.participate(members)
                            return true
                        }
                    }
                    player.sendMessage("${ChatColor.RED}Could not find any expedition instances that have enough free slots for your party! Please try again later.")
                    return false
                } else {
                    player.sendMessage("${ChatColor.RED}Only the party leader can perform this action!")
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
        if(lockedState.acquire) return false
        val list = instances[identifier.lowercase()] ?: return false
        val randomList = ArrayList(list)
        randomList.shuffle()
        for(i in randomList) {
            if(i.canJoin()) {
                i.participate(listOf(player))
                return true
            }
        }
        player.sendMessage("${ChatColor.RED}Could not join any instances for this expedition at this time! Please try again later.")
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
                    if(joined == 0 && phase.getIdleTime() + 300000 < System.currentTimeMillis()) { emptyMaps.add(i) }
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
                /* TODO Empty maps should only be removed by queue ticker really...
                emptyMaps.forEach { if(list.size > initialInstances && list.remove(it)) {
                    destroyInstance(it)
                    removeInstance(it)
                } }*/
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
        var list = this.instances[instance.expedition.identifier.lowercase()]
        if(list == null) {
            list = LinkedList()
            this.instances[instance.expedition.identifier.lowercase()] = list
        }
        list.add(instance)
    }

    fun removeInstance(instance: ExpeditionInstance) {
        this.instances[instance.expedition.identifier.lowercase()]?.remove(instance)
    }

    fun createInstance(expedition: Expedition): ExpeditionInstance? {
        val instanceWorld = multiverse.cloneWorld(expedition.world) ?: return null
        val instance = ExpeditionInstance(plugin, expedition, instanceWorld)
        instance.start()
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
        if(!instance.getTickLock().tryLock()) {
            warn("Failed to lock the tick lock of destroyed instance! This may cause issues!")
        }

    }
}

class CapacityTicker(plugin: RavinPlugin, private val handler: PlayHandler, interval: Int) : Ticker(plugin, interval.ticks) {
    override suspend fun CoroutineScope.tick() {
        handler.checkCapacity()
    }
}