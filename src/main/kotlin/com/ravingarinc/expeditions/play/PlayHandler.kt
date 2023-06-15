package com.ravingarinc.expeditions.play

import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.ticks
import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.api.module.SuspendingModule
import com.ravingarinc.expeditions.api.getDuration
import com.ravingarinc.expeditions.api.getMaterialList
import com.ravingarinc.expeditions.command.ExpeditionGui
import com.ravingarinc.expeditions.integration.MultiverseHandler
import com.ravingarinc.expeditions.locale.ExpeditionManager
import com.ravingarinc.expeditions.locale.type.Expedition
import com.ravingarinc.expeditions.persistent.ConfigManager
import com.ravingarinc.expeditions.play.instance.ExpeditionInstance
import kotlinx.coroutines.delay
import org.bukkit.Material
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

class PlayHandler(plugin: RavinPlugin) : SuspendingModule(PlayHandler::class.java, plugin, ExpeditionManager::class.java) {
    private lateinit var expeditions: ExpeditionManager
    private lateinit var multiverse: MultiverseHandler
    private lateinit var manager: ConfigManager

    private var initialInstances = 1
    private val joinCommands: MutableList<String> = ArrayList()
    private var extractionTime: Long = 0L

    private val instances: MutableMap<String, MutableList<ExpeditionInstance>> = ConcurrentHashMap()
    private lateinit var ticker: PlayTicker
    private val overhangingBlocks: MutableSet<Material> = HashSet()

    private val expeditionPlayers: MutableMap<Player, ExpeditionInstance> = ConcurrentHashMap()
    private val abandonedPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    override suspend fun suspendLoad() {
        manager = plugin.getModule(ConfigManager::class.java)
        manager.config.consume("general") {
            initialInstances = it.getInt("initial-instances", 1)
            it.getStringList("on-join-expedition-commands").forEach { str ->
                joinCommands.add(str)
            }
            it.getMaterialList("overhanging-blocks").forEach { mat ->
                overhangingBlocks.add(mat)
            }
            extractionTime = it.getDuration("extraction-time") ?: 0L
        }
        manager.data.config.getStringList("abandoned-players").forEach {
            val uuid = UUID.fromString(it)
            abandonedPlayers.add(uuid)
        }


        expeditions = plugin.getModule(ExpeditionManager::class.java)
        multiverse = plugin.getModule(MultiverseHandler::class.java)

        ticker = PlayTicker(plugin, instances.values)

        plugin.launch {
            delay(20.ticks)
            for(type in expeditions.getMaps()) {
                val list = LinkedList<ExpeditionInstance>()
                instances[type.identifier] = list
                for(i in 0..initialInstances) {
                    createInstance(type)?.let { list.add(it) }
                }
            }
            delay(5.ticks)
            ticker.start()
        }
    }

    override suspend fun suspendCancel() {
        ExpeditionGui.dispose()
        ticker.cancel()
        instances.values.forEach { list -> list.forEach {
            it.end()
            it.getQuitPlayers().forEach { uuid -> addAbandon(uuid) }
            multiverse.deleteWorld(it.world)
        }}
        instances.clear()

        abandonedPlayers.forEach { manager.addAbandonedPlayer(it) }
        abandonedPlayers.clear()
        joinCommands.clear()
        overhangingBlocks.clear()
    }

    fun getOverhangingBlocks() : Set<Material> {
        return overhangingBlocks
    }

    fun getInstances(): Map<String,List<ExpeditionInstance>> {
        return instances
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

    fun joinExpedition(identifier: String, player: Player) : Boolean {
        val list = instances[identifier] ?: return false
        val randomList = ArrayList(list)
        randomList.shuffle()
        for(i in randomList) {
            if(i.canJoin() && i.participate(player)) {
                joinCommands.forEach {
                    plugin.server.dispatchCommand(plugin.server.consoleSender, it.replace("@player", player.name))
                }
                expeditionPlayers[player] = i
                return true
            }
        }
        return false
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
        instances.values.forEach { list ->
            var total = 0
            var maxTotal = 0
            for(i in list) {
                total += i.getJoinedPlayers().size
                maxTotal += i.expedition.maxPlayers
            }
            val capacity = total / maxTotal.toDouble()
            if(capacity > 0.5) {
                // increase maps
            } else {
                // todo here remove unused maps
            }
        }
    }

    fun createInstance(expedition: Expedition): ExpeditionInstance? {
        val instanceWorld = multiverse.cloneWorld(expedition.world) ?: return null
        val instance = ExpeditionInstance(plugin, expedition, instanceWorld, extractionTime)
        plugin.launch {
            instance.start()
        }
        return instance
    }

    /**
     * Called when the instance should be forcefully destroyed
     */
    fun destroyInstance(instance: ExpeditionInstance) {
        // todo
    }

}