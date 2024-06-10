package com.ravingarinc.expeditions.queue

import com.ravingarinc.api.Sync.SyncOnly
import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.api.module.SuspendingModuleListener
import com.ravingarinc.api.module.warn
import com.ravingarinc.expeditions.api.getDuration
import com.ravingarinc.expeditions.api.getPercentage
import com.ravingarinc.expeditions.api.parseItem
import com.ravingarinc.expeditions.locale.ExpeditionManager
import com.ravingarinc.expeditions.locale.type.Expedition
import com.ravingarinc.expeditions.persistent.ConfigFile
import com.ravingarinc.expeditions.persistent.ConfigManager
import com.ravingarinc.expeditions.play.PlayHandler
import com.ravingarinc.expeditions.play.instance.ExpeditionInstance
import com.ravingarinc.expeditions.play.instance.IdlePhase
import com.ravingarinc.expeditions.play.instance.PlayPhase
import com.ravingarinc.expeditions.play.item.ItemType
import kotlinx.coroutines.delay
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerAttemptPickupItemEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.optionals.getOrElse
import kotlin.math.floor
import kotlin.math.min

class QueueManager(plugin: RavinPlugin) : SuspendingModuleListener(QueueManager::class.java, plugin, isRequired = false, ConfigManager::class.java, ExpeditionManager::class.java) {
    private val queues: MutableMap<Rotation, List<Bucket>> = HashMap()
    private val gearMap: MutableMap<String, Int> = HashMap()

    private val mappedRequests: MutableMap<UUID, JoinRequest> = ConcurrentHashMap()

    companion object {
        val minGroups = 5
        val maxGroups = 15
    }
    @Deprecated("Divisor must be handled automagically as a scaling value based on player count")
    private val divisor = 10.0
    private var maxScore: Int = 100
    private var slippage: Double = 0.25
    private var maxSlippage: Double = 1.0
    private var maxWaitTime: Long = -1L
    private var maxItems = 9
    private var minimumPlayerPercent = 0.5

    private lateinit var ticker: QueueTicker
    private lateinit var handler: PlayHandler

    override suspend fun suspendLoad() {
        val manager = plugin.getModule(ConfigManager::class.java)
        handler = plugin.getModule(PlayHandler::class.java)
        loadQueue(manager.queue)
        loadGear(manager.gear)

        ticker = QueueTicker(plugin, maxWaitTime)
        ticker.start(20)
        super.suspendLoad()

        // TODO If a player equips new items that increase their gear score more than the slippage percentage then change
        //   bucket they're in!
    }

    override suspend fun suspendCancel() {
        ticker.cancel()
        queues.values.forEach { list -> list.forEach { it.players.clear() } }
        queues.clear()
        gearMap.clear()
        mappedRequests.clear()
    }

    private fun loadQueue(file: ConfigFile) {
        slippage = file.config.getPercentage("queue.min-slippage")
        maxSlippage = file.config.getPercentage("queue.max-slippage")
        maxWaitTime = file.config.getDuration("queue.max-wait-time")?.times(50) ?: -1L
        minimumPlayerPercent = file.config.getPercentage("queue.min-players")
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
            if(foundMaps.isEmpty()) {
                warn("Could not load rotation '$key' as no valid maps could be found!")
            } else {
                queues[Rotation(key, foundMaps)] = createBucketList()
            }

        }
    }

    private fun loadGear(file: ConfigFile) {
        maxScore = file.config.getInt("gear.maximum-score", 100)
        maxItems = file.config.getInt("gear.max-items-to-consider", 9)

        file.config.getStringList("scores").forEach {
            val split = it.split(":")
            val weight = split[split.size - 1]
            val raw = it.substring(0, it.length - weight.length - 1)
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
            getScoreRanges().forEach {
                this.add(Bucket(it))
            }
        }
    }

    fun getScoreRanges() : List<Int> {
        val max = floor(maxScore / divisor).toInt()
        return buildList {
            for(i in 0 .. max) {
                this.add(floor(i * divisor).toInt())
            }
        }
    }

    fun queueForRemoval(inst: ExpeditionInstance) {
        ticker.queueForRemoval(inst)
    }

    fun getDivisor(): Double {
        return divisor
    }

    fun getRelativeGroups(rotation: String, score: Int) : Collection<MutableList<JoinRequest>> {
        return buildList {
            getBucketList(rotation)?.let {
                for(bucket in it) {
                    if(((score * (1.0 - slippage)).toInt() .. (score * (1.0 + slippage)).toInt()).contains(bucket.score)) {
                        this.add(bucket.players)
                    }
                }
            }
        }
    }

    fun getQueuedRequests() : Collection<JoinRequest> {
        return buildList {
            queues.values.forEach { list -> list.forEach { this.addAll(it.players) }}
        }
    }

    fun isRotation(rotation: String) : Boolean {
        return getRotations().any { it.key.equals(rotation, true) }
    }


    fun enqueueRequest(request: JoinRequest, priority: Boolean) {
        val buckets = getBucketList(request.rotation) ?: throw IllegalArgumentException("Could not find rotation called '${request.rotation}'!")
        val bucket = buckets[getBucketIndex(request.score)]
        if(priority) {
            bucket.players.add(0, request) // This actually isn't thread safe but as long as ONLY queueticker calls it we should be fine....
        } else {
            bucket.players.add(request)
        }
        request.players.forEach {
            mappedRequests[it.uniqueId] = request
        }
    }

    fun getMinimumPLayers() : Double {
        return minimumPlayerPercent;
    }

    @SyncOnly
    suspend fun dequeueGroup(score: Int, expedition: Expedition, requests: Collection<JoinRequest>) {
        val startTime = System.currentTimeMillis()
        val scoreRange = (score * (1.0 - slippage)).toInt() .. (score * (1.0 + slippage)).toInt()
        val totalSize = requests.sumOf { it.size() }
        val inst = findValidInst(expedition.identifier, scoreRange, totalSize).getOrElse {
            val nInst = handler.createInstance(expedition)
            nInst?.let { handler.addInstance(it) }
            delay(5000)
            return@getOrElse nInst
        }
        if(inst == null) {
            requests.forEach { request ->
                request.players.forEach { it.sendMessage(Component
                    .text("Sorry! Something went wrong joining the expedition. You have rejoined the queue with priority!")
                    .color(NamedTextColor.RED))
                }
                enqueueRequest(request, true)
            }
            warn("Something went wrong forming new expedition for group!")
        } else {
            if(inst.score == -1) inst.score = score
            val phase = inst.getPhase()
            if(phase is IdlePhase) phase.next(inst)
            delay(5000 - (System.currentTimeMillis() - startTime))
            requests.forEach { request -> dequeueRequest(inst, request) }
        }
    }

    fun findExistingInst(rotation: Rotation, score: Int, size: Int, slippage: Double = this.slippage) : ExpeditionInstance? {
        val scoreRange = (score * (1.0 - slippage)).toInt() .. (score * (1.0 + slippage)).toInt()
        var inst: ExpeditionInstance? = null
        for(key in rotation.set) {
            inst = findValidInst(key, scoreRange, size).orElse(null)
            if(inst != null) break
        }
        return inst
    }

    @SyncOnly
    suspend fun findLowestPopInst(rotation: Rotation, score: Int, size: Int) : ExpeditionInstance? {
        var inst: ExpeditionInstance? = findExistingInst(rotation, score, size, maxSlippage)
        if(inst == null) {
            val expedition = plugin.getModule(ExpeditionManager::class.java).getMapByIdentifier(rotation.getNextMap())
            if(expedition == null) {
                warn("Could not find expedition called '${rotation.getNextMap()}' specified in rotation '${rotation.key}'!")
                return null
            }
            inst = handler.createInstance(expedition)
            inst?.let { handler.addInstance(inst) }
            delay(5000)
        }
        if(inst == null) {
            warn("Failed to find/create valid expedition instance for rotation '${rotation.key}' for unknown reason!")
        } else {
            if(inst.score == -1) inst.score = score
            val phase = inst.getPhase()
            if(phase is IdlePhase) { phase.next(inst) }
        }
        return inst
    }

    private fun findValidInst(identifier: String, scoreRange: IntRange, playerSize: Int) : Optional<ExpeditionInstance> {
        return handler.getInstances(identifier)?.stream()?.filter {
            val phase = it.getPhase()
            if(phase !is IdlePhase && phase !is PlayPhase) return@filter false
            if(phase is PlayPhase) {
                if(phase.getTicksRemaining() < 100L) return@filter false
                if(it.score != -1 && !scoreRange.contains(it.score)) return@filter false
            }
            return@filter it.getAmountOfPlayers() + playerSize <= it.expedition.maxPlayers
        }?.findFirst() ?: Optional.empty()
    }

    @SyncOnly
    fun dequeueRequest(inst: ExpeditionInstance, request: JoinRequest) {
        request.players.forEach { mappedRequests.remove(it.uniqueId) }
        inst.participate(request.players)
    }

    fun removePlayer(player: Player) {
        for(set in queues.values) for(bucket in set) for(request in ArrayList(bucket.players)) {
            if(!request.contains(player)) continue
            if(request is PartyRequest) {
                request.remove(player)
                if(request.players.isEmpty()) bucket.players.remove(request)
            } else {
                bucket.players.remove(request)
            }
            request.players.forEach { mappedRequests.remove(it.uniqueId) }
            break
        }
    }

    /**
     * This may not always be 100% accurate and should only be used by third parties for things such as placeholders!
     */
    fun getRequest(player: Player) : JoinRequest? {
        return mappedRequests[player.uniqueId]
    }

    fun getQueuedAmount(rotation: String) : Int {
        val entry = queues.entries.firstOrNull { it.key.key == rotation } ?: return 0
        return entry.value.sumOf { b -> b.players.sumOf { it.size() } }
    }

    fun getRotations() : Collection<Rotation> {
        return this.queues.keys
    }

    fun getRotationNames() : List<String> {
        return buildList {
            queues.keys.forEach { this.add(it.key) }
        }
    }

    fun getIndexedPlayers(rotation: String) : List<Pair<Int,MutableList<JoinRequest>>> {
        return buildList {
            val list = getBucketList(rotation) ?: return@buildList
            list.forEach {
                this.add(Pair(it.score, it.players))
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
        if(inventory.isEmpty()) return 0
        val scores = ArrayList<Int>()
        for(item in inventory) {
            if(item == null) continue
            if(item.type.isAir) continue
            getItemScore(item)?.let { scores.add(it) }
        }
        val sorted = scores.sortedDescending()
        var total = 0
        var average = 0
        for(i in 0 until maxItems) {
            if(i < sorted.size) {
                total += sorted[i]
            } else {
                if(average == 0) {
                    average = total / (i + 1)
                }
                total += average
            }
        }
        return min(total, (maxScore * (1.0 + slippage)).toInt())
    }

    fun getItemScore(item: ItemStack) : Int? {
        val type = ItemType.convertAsString(item)
        return gearMap[type]
    }

    fun getBucketIndex(score: Int) : Int {
        return ((score.toDouble() / maxScore.toDouble()) * (maxScore.toDouble() / divisor)).toInt()
    }


    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        removePlayer(player)

        // TODO There is nothing to handle if a player gets kicked out of a party whilst in an expedition!
    }

    @EventHandler(ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {

    }
    @EventHandler(ignoreCancelled = true)
    fun onInventoryDrag(event: InventoryDragEvent) {

    }

    @EventHandler(ignoreCancelled = true)
    fun onPlayerItemPickup(event: PlayerAttemptPickupItemEvent) {

    }
}

private class Bucket(val score: Int) {
    val players: MutableList<JoinRequest> = LinkedList()
}