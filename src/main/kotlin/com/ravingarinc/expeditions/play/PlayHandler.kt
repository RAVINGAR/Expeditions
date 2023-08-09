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
import com.ravingarinc.expeditions.persistent.ConfigManager
import com.ravingarinc.expeditions.play.instance.CachedPlayer
import com.ravingarinc.expeditions.play.instance.ExpeditionInstance
import com.ravingarinc.expeditions.play.instance.IdlePhase
import com.ravingarinc.expeditions.play.instance.PlayPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class PlayHandler(plugin: RavinPlugin) : SuspendingModule(PlayHandler::class.java, plugin, true, ExpeditionManager::class.java) {
    private lateinit var expeditions: ExpeditionManager
    private lateinit var multiverse: MultiverseHandler
    private lateinit var manager: ConfigManager

    private var initialInstances = 1
    private var maxInstances: Int = 4
    private var tickInterval: Int = 1200
    private val joinCommands: MutableList<String> = ArrayList()

    private val instances: MutableMap<String, MutableList<ExpeditionInstance>> = ConcurrentHashMap()
    private lateinit var ticker: PlayTicker
    private val overhangingBlocks: MutableSet<Material> = HashSet()

    private val expeditionPlayers: MutableMap<Player, ExpeditionInstance> = ConcurrentHashMap()
    private val abandonedPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private val respawningPlayers: MutableMap<UUID, CachedPlayer> = ConcurrentHashMap()

    private lateinit var capacityJob: CapacityTicker

    override suspend fun suspendLoad() {
        manager = plugin.getModule(ConfigManager::class.java)
        manager.config.consume("general") {
            tickInterval = (it.getDuration("tick-interval") ?: 1200).toInt()
            initialInstances = it.getInt("initial-instances", 1)
            maxInstances = it.getInt("max-instances", 4)
            it.getStringList("on-join-expedition-commands").forEach { str ->
                joinCommands.add(str)
            }
            it.getMaterialList("overhanging-blocks").forEach { mat ->
                overhangingBlocks.add(mat)
            }
        }
        manager.data.config.getStringList("abandoned-players").forEach {
            val uuid = UUID.fromString(it)
            abandonedPlayers.add(uuid)
        }
        expeditions = plugin.getModule(ExpeditionManager::class.java)
        multiverse = plugin.getModule(MultiverseHandler::class.java)

        ticker = PlayTicker(plugin, instances.values)
        capacityJob = CapacityTicker(plugin, this, tickInterval)

        plugin.launch {
            delay(20.ticks)
            for(type in expeditions.getMaps()) {
                val list = LinkedList<ExpeditionInstance>()
                instances[type.identifier] = list
                for(i in 1..initialInstances) {
                    createInstance(type)?.let { list.add(it) }
                }
            }
            delay(5.ticks)
            ticker.start()
            capacityJob.start(tickInterval)
        }
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
        joinCommands.clear()
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

    fun joinExpedition(identifier: String, player: Player) : Boolean {
        val list = instances[identifier] ?: return false
        val randomList = ArrayList(list)
        randomList.shuffle()
        for(i in randomList) {
            if(i.canJoin() && i.participate(player)) {
                joinCommands.forEach {
                    plugin.server.dispatchCommand(plugin.server.consoleSender, it.replace("@player", player.name))
                }
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
        instances.values.forEach { list ->
            list.forEach {
                if(it.world == world) {
                    return true
                }
            }
        }
        return false
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
        plugin.launch {
            instance.start()
        }
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