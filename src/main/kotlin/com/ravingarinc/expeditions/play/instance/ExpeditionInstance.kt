package com.ravingarinc.expeditions.play.instance

import com.github.shynixn.mccoroutine.bukkit.launch
import com.ravingarinc.api.I
import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.expeditions.locale.type.Expedition
import com.ravingarinc.expeditions.locale.type.ExtractionZone
import com.ravingarinc.expeditions.play.PlayHandler
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.MapMeta
import org.bukkit.map.MapView
import org.bukkit.util.BlockVector
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import kotlin.random.Random

class ExpeditionInstance(val plugin: RavinPlugin, val expedition: Expedition, val world: World, val extractionTime: Long) {
    private var phase: Phase = IdlePhase(expedition)
    val bossBar = plugin.server.createBossBar(
        NamespacedKey(plugin, "${world.name}_bossbar"),
        "${expedition.displayName} Expedition",
        BarColor.BLUE, BarStyle.SEGMENTED_12)

    val sneakingPlayers: MutableMap<Player, Long> = ConcurrentHashMap()

    val mapView: MapView
    init {
        val view = Bukkit.createMap(world)
        view.centerX = expedition.centreX
        view.centerZ = expedition.centreZ
        view.scale = getScaleFromSize(expedition.radius)
        view.isTrackingPosition = true
        view.isUnlimitedTracking = true
        view.isLocked = true

        mapView = view
    }
    val brokenBlocks: MutableMap<BlockVector, Pair<Block, Material>> = Hashtable()

    private val joinedPlayers: MutableMap<UUID, CachedPlayer> = HashMap()
    private val quitPlayers: MutableMap<UUID, CachedPlayer> = HashMap()

    private val tickLock = Mutex(false)

    val areaInstances: MutableList<AreaInstance> = ArrayList()

    fun start() {
        phase.start(this)
    }

    /**
     * Called when an expedition is force ended. This means the plugin is reloaded or the server shuts down.
     * Players should be teleported to their previous location.
     */
    fun end() {

        bossBar.removeAll()
        Hashtable(joinedPlayers).forEach {
            it.value.player.player.let { player ->
                if(player == null) {
                    val uuid = it.value.player.uniqueId
                    joinedPlayers.remove(uuid)
                    quitPlayers[uuid] = it.value
                } else {
                    extract(player)
                }
            }
        }
    }

    fun contains(player: Player) : Boolean {
        return joinedPlayers.containsKey(player.uniqueId)
    }

    fun getJoinedPlayers() : Collection<UUID> {
        return joinedPlayers.keys
    }

    fun getRemainingPlayers() : Collection<Player> {
        val list = ArrayList<Player>()
        joinedPlayers.values.forEach {
            it.player.player.let { player ->
                if(player == null) {
                    quitPlayers[it.player.uniqueId] = it
                } else {
                    list.add(player)
                }

            }
        }
        return list
    }

    fun getQuitPlayers() : Collection<UUID> {
        return quitPlayers.keys
    }

    fun clearPlayers() {
        joinedPlayers.clear()
        quitPlayers.clear()
        sneakingPlayers.clear()
    }

    fun clear() {
        brokenBlocks.clear()
        areaInstances.clear()
    }

    suspend fun tick(random: Random) {
        if(tickLock.isLocked) {
            I.log(Level.WARNING, "Tick on ExpeditionInstance was locked whilst ticking! Server main tick must be behind!")
        }
        tickLock.withLock {
            if(phase.isActive()) {
                phase.tick(random, this)
            }
        }
    }

    fun tickMobs(random: Random) {
        areaInstances.forEach {
            it.tickMobs(random, world)
        }
    }

    fun tickLoot(random: Random) {
        areaInstances.forEach {
            it.tickLoot(random, world)
        }
    }

    fun breakBlock(block: Block, material: Material) {
        brokenBlocks[BlockVector(block.x, block.y, block.z)] = Pair(block, material)
    }

    /**
     * Try to join this player to this expedition instance. Returns true if successful, or false
     * if not.
     */
    fun participate(player: Player) : Boolean {
        if(!phase.isActive()) {
            return false
        }
        if(phase is IdlePhase) {
            phase.next(this)
            join(player)
        } else if(phase is PlayPhase) {
            join(player)
        }
        return true
    }

    fun join(player: Player) {
        // Todo, teleport to the event randomly, also check if they are in a party?
        val handler = plugin.getModule(PlayHandler::class.java)
        plugin.launch {
            val loc = findSuitableLocation(world, expedition.centreX, expedition.centreZ, expedition.radius - 8, handler.getOverhangingBlocks())
            player.teleport(loc)
            giveMap(player)
        }
        bossBar.addPlayer(player)
    }

    fun extract(player: Player) : Boolean {
        // Remove player from event
        removeMap(player)
        joinedPlayers.remove(player.uniqueId)?.let {
            player.teleport(it.previousLocale)
        }
        bossBar.removePlayer(player)
        plugin.getModule(PlayHandler::class.java).removeJoinedExpedition(player)
        return true
    }

    private fun findSuitableLocation(
        world: World,
        x: Int,
        z: Int,
        radius: Int,
        overhanging: Set<Material>
    ): Location {
        for (i in 0..7) {
            val randomX = Random.nextInt(-radius, radius)
            val randomZ = Random.nextInt(-radius, radius)
            val nextX = x + randomX
            val nextZ = z + randomZ
            val block = world.getHighestBlockAt(nextX, nextZ)
            val location = block.location
            val type = block.type
            if (type == Material.WATER || type == Material.LAVA) {
                continue
            }
            var isValid = false
            for (y in block.y downTo 64) {
                val b = world.getBlockAt(nextX, y, nextZ).type
                if (!overhanging.contains(b)) {
                    isValid = true
                    break
                }
            }
            if (!isValid) {
                continue
            }
            location.add(0.0, 1.0, 0.0)
            val newX = location.blockX
            val newY = location.blockY
            val newZ = location.blockZ
            for (dX in -2..1) {
                for (dZ in -2..1) {
                    if (!world.getBlockAt(Location(world, newX.toDouble(), newY.toDouble(), newZ.toDouble())).type.isAir) {
                        isValid = false
                        break
                    }
                }
            }
            if (isValid) {
                return location
            }
        }
        val randomX = Random.nextInt(-radius, radius)
        val randomZ = Random.nextInt(-radius, radius)
        val nextX = x + randomX
        val nextZ = z + randomZ
        return Location(
            world,
            nextX.toDouble(),
            (world.getHighestBlockYAt(nextX, nextZ) + 1).toDouble(),
            nextZ.toDouble()
        )
    }

    /**
     * Handle player joining event. This checks if a player has participated in this instance before.
     */
    fun onJoinEvent(player: Player) : Boolean {
        val uuid = player.uniqueId
        quitPlayers.remove(uuid)?.let {
            joinedPlayers[uuid] = CachedPlayer(player, it.previousLocale)
            bossBar.addPlayer(player)
            return true
        }
        return false
    }

    /**
     * Handle player quit event. This checks if a player is currently participating in this instance.
     */
    fun onQuitEvent(player: Player) {
        val uuid = player.uniqueId
        joinedPlayers.remove(uuid)?.let {
            quitPlayers[uuid] = it
            sneakingPlayers.remove(player)
        }
    }

    fun onSpawnEvent(player: Player) {
        joinedPlayers.remove(player.uniqueId)?.let {
            // Todo add send message here
            player.teleport(it.previousLocale)
            removeMap(player)
        }
    }

    fun onSneakEvent(player: Player, isSneaking: Boolean) {
        if(isSneaking) {
            val loc = player.location
            getAreaFromLocation(loc.blockX, loc.blockY, loc.blockZ)?.let {
                if(it.area is ExtractionZone) {
                    sneakingPlayers[player] = System.currentTimeMillis()
                }
            }
        } else {
            sneakingPlayers.remove(player)
        }
    }

    fun getAreaFromLocation(x: Int, y: Int, z: Int) : AreaInstance? {
        for(area in areaInstances) {
            if(area.area.isInArea(x, y, z)) {
                return area
            }
        }
        return null
    }

    fun onMoveEvent(player: Player) {
        if(sneakingPlayers.containsKey(player)) {
            sneakingPlayers[player] = System.currentTimeMillis()
            player.sendMessage("${ChatColor.RED}You must remain still whilst waiting for extraction!")
        }
    }

    fun giveMap(player: Player) {
        val mapItem = ItemStack(Material.FILLED_MAP)
        val meta = (mapItem.itemMeta as MapMeta)
        meta.setCustomModelData(4)
        meta.mapView = mapView
        mapItem.setItemMeta(meta)
        player.inventory.addItem(mapItem)
    }

    fun removeMap(player: Player) {
        player.inventory.forEach { item ->
            if(item != null && item.type == Material.FILLED_MAP && item.itemMeta.customModelData == 4) {
                item.type = Material.AIR
                return@forEach
            }
        }
    }

    fun getScaleFromSize(radius: Int) : MapView.Scale {
        val rad = radius * 2
        if((0..127).contains(rad)) {
            return MapView.Scale.CLOSEST
        } else if((128..256).contains(rad)) {
            return MapView.Scale.CLOSE
        } else if ((257..512).contains(rad)) {
            return MapView.Scale.NORMAL
        } else if ((513..1024).contains(rad)) {
            return MapView.Scale.FAR
        } else {
            return MapView.Scale.FARTHEST
        }
    }

    fun onBlockInteract(block: Block, player: Player) {
        for(it in areaInstances) {
            if(it.onBlockInteract(plugin, block, player)) break
        }
    }

    fun onDeathEvent(event: EntityDeathEvent) : Boolean {
        val entity = event.entity
        if(entity is Player) return false
        for(it in areaInstances) {
            if(it.onDeath(entity)) return true
        }
        return false
    }

    fun setPhase(phase: Phase) {
        this.phase.end(this)
        this.phase = phase
        this.phase.start(this)
    }

    fun canJoin() : Boolean {
        if(phase is IdlePhase || phase is PlayPhase) {
            return joinedPlayers.size < expedition.maxPlayers
        }
        return false
    }

    private data class CachedPlayer(val player: OfflinePlayer, val previousLocale: Location)
}