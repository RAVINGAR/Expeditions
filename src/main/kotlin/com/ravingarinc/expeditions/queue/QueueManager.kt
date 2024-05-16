package com.ravingarinc.expeditions.queue

import com.github.shynixn.mccoroutine.bukkit.launch
import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.api.module.SuspendingModuleListener
import com.ravingarinc.api.module.warn
import com.ravingarinc.expeditions.api.getDuration
import com.ravingarinc.expeditions.api.getPercentage
import com.ravingarinc.expeditions.api.parseItem
import com.ravingarinc.expeditions.locale.ExpeditionManager
import com.ravingarinc.expeditions.persistent.ConfigFile
import com.ravingarinc.expeditions.persistent.ConfigManager
import com.ravingarinc.expeditions.play.item.ItemType
import kotlinx.coroutines.Dispatchers
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor

class QueueManager(plugin: RavinPlugin) : SuspendingModuleListener(QueueManager::class.java, plugin, isRequired = false, ConfigManager::class.java, ExpeditionManager::class.java) {
    private val queues: MutableMap<Rotation, List<Bucket>> = HashMap()
    private val gearMap: MutableMap<String, Int> = HashMap()

    private var divisor = 100.0
    private var maxScore: Double = 1000.0
    private var slippage: Double = 0.25
    private var maxWaitTime: Long = -1L
    private var maxItems = 9

    override suspend fun suspendLoad() {
        val manager = plugin.getModule(ConfigManager::class.java)
        loadQueue(manager.queue)
        loadGear(manager.gear)

        TODO("NEXT Is the ticker that actually checks the buckets and puts players into an expedition")
        super.suspendLoad()
    }

    private fun loadQueue(file: ConfigFile) {
        divisor = file.config.getDouble("gear.divisor", 10.0)
        maxScore = file.config.getDouble("gear.maximum-score", 100.0)
        maxItems = file.config.getInt("gear.max-items-to-consider", 9)

        slippage = file.config.getPercentage("queue.max-slippage")
        maxWaitTime = file.config.getDuration("queue.maximum-wait-time")?.times(50) ?: -1L
        val expeditions = plugin.getModule(ExpeditionManager::class.java)
        for(map in file.config.getMapList("rotations")) {
            val key = map["key"]?.toString()
            if(key == null) {
                warn("Encountered error in queue.yml! Every rotation must have a 'key'!")
                continue
            }
            val maps = (map["maps"] as? List<*>)
            if(maps == null) {
                warn("Could not find any maps for given rotation with key $key!")
                continue
            }
            val foundMaps = buildSet {
                maps.forEach {
                    if(expeditions.getMapByIdentifier(it.toString()) == null) {
                        warn("Could not find expedition called '${it.toString()}' specified in queue.yml under rotations.$key")
                    } else {
                        this.add(it.toString())
                    }
                }
            }
            queues[Rotation(key, foundMaps)] = createBucketList()
        }
    }

    private fun loadGear(file: ConfigFile) {
        file.config.getStringList("gear").forEach {
            val split = it.split(":".toRegex())
            val weight = split[split.size - 1]
            val raw = it.substring(0, weight.length - 1)
            if(gearMap.containsKey(raw)) {
                warn("Encountered duplicate item entry in gear.yml for item '$raw'!")
            } else {
                parseItem(raw)?.let { item ->
                    gearMap[item.toString()] = weight.toInt()
                }
            }
        }
    }

    private fun createBucketList() : List<Bucket> {
        return buildList {
            val max = floor(maxScore / divisor).toInt()
            for(i in 0 .. max) {
                this.add(Bucket(i * divisor))
            }
        }
    }
    override suspend fun suspendCancel() {
        queues.values.forEach { list -> list.forEach { it.players.clear() } }
        queues.clear()
        gearMap.clear()
    }

    fun enqueuePlayer(rotation: String, player: Player) {
        val buckets = getBucketList(rotation) ?: throw IllegalArgumentException("Could not find rotation called '$rotation'!")
        val inventory = player.inventory.contents
        plugin.launch(Dispatchers.IO) {
            val score = calculateGearScore(inventory)
            val bucket = buckets[getBucketIndex(score)]
            bucket.players.add(player)
        }
    }

    fun dequeuePlayer(player: Player) {
        for(set in queues.values) {
            for(bucket in set) {
                if(bucket.players.remove(player)) break
            }
        }
    }

    private fun getBucketList(rotation: String) : List<Bucket>? {
        for(entry in queues.entries) {
            if(entry.key.key.equals(rotation, true)) {
                return entry.value
            }
        }
        return null
    }

    fun calculateGearScore(inventory: Array<ItemStack?>) : Int {
        val scores = ArrayList<Int>()
        for(item in inventory) {
            if(item == null) continue
            if(item.type.isAir) continue
            val type = ItemType.convertAsString(item)
            gearMap[type]?.let { scores.add(it) }
        }
        val sorted = scores.sortedDescending()
        var total = 0
        for(i in 0 until maxItems) {
            total += sorted[i]
        }
        return total
    }

    fun getBucketIndex(score: Int) : Int {
        return (score / maxScore * (maxScore / divisor)).toInt()
    }


    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        dequeuePlayer(player)
    }
}

private class Bucket(val score: Double) {
    val players: MutableSet<Player> = ConcurrentHashMap.newKeySet()
}