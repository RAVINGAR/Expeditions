package com.ravingarinc.expeditions.play.instance

import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.ticks
import com.ravingarinc.api.I
import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.expeditions.locale.type.Expedition
import com.ravingarinc.expeditions.locale.type.ExtractionZone
import com.ravingarinc.expeditions.play.PlayHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.MapMeta
import org.bukkit.map.MapView
import org.bukkit.util.BlockVector
import org.bukkit.util.Vector
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import kotlin.random.Random

class ExpeditionInstance(val plugin: RavinPlugin, val expedition: Expedition, val world: World) {
    private val handler = plugin.getModule(PlayHandler::class.java)
    private var phase: Phase = IdlePhase(expedition)
    val bossBar = plugin.server.createBossBar(
        NamespacedKey(plugin, "${world.name}_bossbar"),
        "${expedition.displayName} Expedition",
        BarColor.BLUE, BarStyle.SEGMENTED_12)

    val sneakingPlayers: MutableMap<Player, Pair<Long, Vector>> = ConcurrentHashMap()

    val npcFollowers: MutableMap<Player, AreaInstance> = HashMap()

    val mapView: MapView

    val renderer: ExpeditionRenderer = ExpeditionRenderer(expedition)
    init {
        val view = Bukkit.createMap(world)
        view.centerX = expedition.centreX
        view.centerZ = expedition.centreZ
        view.scale = getScaleFromSize(expedition.radius)
        view.isTrackingPosition = true
        view.isUnlimitedTracking = true
        view.isLocked = true

        ArrayList(view.renderers).forEach {
            view.removeRenderer(it)
        }
        view.addRenderer(renderer)

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
        npcFollowers.forEach { it.value.stopFollowing(null) }
        npcFollowers.clear()
        Hashtable(joinedPlayers).forEach {
            it.value.player.player.let { player ->
                if(player == null) {
                    val uuid = it.value.player.uniqueId
                    joinedPlayers.remove(uuid)
                    quitPlayers[uuid] = it.value
                } else {
                    removePlayer(player, RemoveReason.EXTRACTION)
                }
            }
        }
        areaInstances.forEach { it.destroyNPC() }
    }

    fun contains(player: Player) : Boolean {
        return joinedPlayers.containsKey(player.uniqueId)
    }

    fun getJoinedPlayers() : Collection<UUID> {
        return joinedPlayers.keys
    }

    fun getRemainingPlayers() : Collection<CachedPlayer> {
        val list = ArrayList<CachedPlayer>()
        joinedPlayers.values.forEach {
            if(it.player.isOnline) {
                list.add(it)
            } else {
                quitPlayers[it.player.uniqueId] = it
            }
        }
        return list
    }

    fun onPlayerDamage(player: Player) {
        npcFollowers[player]?.let {
            it.stopFollowing(player)
            npcFollowers.remove(player)
        }
    }

    fun onNPCClick(player: Player, entityId: Int) {
        npcFollowers[player]?.let {
            if(it.getNPCId() == entityId) {
                it.stopFollowing(player)
                npcFollowers.remove(player)
            } else {
                player.sendMessage("${ChatColor.RED}You can only have one follower at a time!")
            }
            return
        }
        for(area in areaInstances) {
            if(area.getNPCId() == entityId) {
                val npc = area.getNPC() ?: continue
                if(npc.getFollowing() == null) {
                    area.startFollowing(player)
                    npcFollowers[player] = area
                }
                break
            }
        }
    }

    fun getQuitPlayers() : Collection<UUID> {
        return quitPlayers.keys
    }

    fun clearPlayers() {
        joinedPlayers.clear()
        quitPlayers.clear()
        sneakingPlayers.clear()
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

    fun tickNPC() {
        areaInstances.forEach {
            it.tickNPC(world)
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

    private fun join(player: Player) {
        // Todo, also check if they are in a party?
        plugin.launch {
            val loc = findSuitableLocation(world, expedition.centreX, expedition.centreZ, expedition.radius - 8, handler.getOverhangingBlocks())
            addPlayer(player, loc)
        }
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
                return Location(world, location.x, location.y, location.z)
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
            handler.removeAbandon(player)
            addPlayer(player, it.previousLocale)
            return true
        }
        return false
    }

    /**
     * Called when a player is added to this expedition. This is either through re-joining or
     * a new join
     */
    fun addPlayer(player: Player, location: Location) {
        val uuid = player.uniqueId
        val previousLocale = player.location
        if(!player.teleport(location)) {
            I.log(Level.WARNING, "Could not teleport '${player.name}' for unknown reason! Something is cancelling this teleportation! Trying again in 20 ticks...")
            plugin.launch {
                delay(20.ticks)
                addPlayer(player, location)
            }
            return
        }
        joinedPlayers[uuid] = CachedPlayer(player, previousLocale)
        bossBar.addPlayer(player)
        areaInstances.forEach { it.onMove(player) }
        handler.addJoinedExpedition(player, this)

        giveMap(player)
    }

    /**
     * Remove player from this expedition instance. Called by death, extraction or quit.
     */
    fun removePlayer(player: Player, reason: RemoveReason) {
        joinedPlayers.remove(player.uniqueId)?.let { cache ->
            when(reason) {
                RemoveReason.QUIT -> {
                    npcFollowers.remove(player)?.stopFollowing(null)
                    handler.addAbandon(player.uniqueId)
                }
                RemoveReason.DEATH -> {
                    npcFollowers.remove(player)?.resetNPC(world)
                    handler.addRespawn(cache)
                }
                RemoveReason.EXTRACTION -> {
                    npcFollowers.remove(player)?.let {
                        val npc = it.getNPC()
                        if(npc != null) {
                            it.area.npcOnExtract.forEach { command ->
                                plugin.server.dispatchCommand(plugin.server.consoleSender, command
                                        .replace("{id}", npc.numericalId().toString())
                                        .replace("{npc}", npc.identifier())
                                        .replace("{player}", player.name))
                            }
                            it.destroyNPC()
                        }
                    }
                    player.teleport(cache.previousLocale)
                }
            }
            removeMap(player)

            handler.removeJoinedExpedition(player)
            bossBar.removePlayer(player)
            sneakingPlayers.remove(player)
            areaInstances.forEach {
                it.inArea.remove(player)
            }
        }
    }

    /**
     * Handle player quit event. This checks if a player is currently participating in this instance.
     */
    fun onQuitEvent(player: Player) {
        val uuid = player.uniqueId
        joinedPlayers[uuid]?.let {
            quitPlayers[uuid] = CachedPlayer(player, player.location)
            player.teleport(it.previousLocale)
            removePlayer(player, RemoveReason.QUIT)
        }
    }

    fun onSneakEvent(player: Player, isSneaking: Boolean) {
        if(isSneaking) {
            areaInstances.forEach {
                if(it.inArea.contains(player) && it.area is ExtractionZone) {
                    sneakingPlayers[player] = Pair(System.currentTimeMillis(), player.location.toVector())
                    return
                }
            }
        }
        if(sneakingPlayers.remove(player) != null) {
            player.sendMessage("${ChatColor.YELLOW}You are no longer extracting!")
        }
    }

    fun onMoveEvent(player: Player) {
        areaInstances.forEach {
            if(it.onMove(player)) {
                if(it.area is ExtractionZone && player.isSneaking && !sneakingPlayers.containsKey(player)) {
                    sneakingPlayers[player] = Pair(System.currentTimeMillis(), player.location.toVector())
                }
                return@forEach
            }
        }
    }

    private fun giveMap(player: Player) {
        val mapItem = ItemStack(Material.FILLED_MAP)
        val meta = (mapItem.itemMeta as MapMeta)
        meta.setDisplayName("${ChatColor.GOLD}${expedition.displayName} Map")
        meta.lore = buildList {
            this.add("${ChatColor.YELLOW}View information about")
            this.add("${ChatColor.YELLOW}your current expedition.")
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        meta.setCustomModelData(4)
        meta.mapView = mapView
        mapItem.setItemMeta(meta)
        player.inventory.addItem(mapItem)
    }

    private fun removeMap(player: Player) {
        if(!player.inventory.isEmpty) {
            for(i in 0 until player.inventory.size) {
                val item = player.inventory.getItem(i) ?: continue
                val meta = item.itemMeta
                if(!meta.hasCustomModelData()) continue
                if(item.type == Material.FILLED_MAP && item.itemMeta.customModelData == 4) {
                    player.inventory.setItem(i, null)
                    return
                }
            }
        }
        renderer.removePlayer(player)
    }

    /**
     * Returns the time left in milliseconds
     */
    fun getTimeLeft() : Long {
        if(phase is PlayPhase) {
            return (expedition.calmPhaseDuration + expedition.stormPhaseDuration - phase.currentTicks) * 50
        } else if (phase is StormPhase) {
            return (expedition.stormPhaseDuration - phase.currentTicks) * 50
        }
        return 0L
    }

    fun getPlayerArea(player: Player) : String {
        for(area in areaInstances) {
            if(area.inArea.contains(player)) {
                return area.area.displayName
            }
        }
        return ""
    }

    private fun getScaleFromSize(radius: Int) : MapView.Scale {
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

    fun onBlockInteract(block: Block, player: Player) : Boolean {
        for(it in areaInstances) {
            if(it.onBlockInteract(plugin, block, player)) return true
        }
        return false
    }

    fun getAmountOfPlayers() : Int {
        return joinedPlayers.size
    }

    fun onDeathEvent(event: EntityDeathEvent) : Boolean {
        val entity = event.entity
        var result = false
        if(entity is Player && joinedPlayers.containsKey(entity.uniqueId)) {
            removePlayer(entity, RemoveReason.DEATH)
            result = true
        }
        for(it in areaInstances) {
            if(it.onDeath(entity)) {
                result = true
                break
            }
        }

        return result
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

    fun getPhase() : Phase {
        return phase
    }

    fun getPhaseName() : String {
        return phase.name
    }
}
data class CachedPlayer(val player: OfflinePlayer, val previousLocale: Location)

enum class RemoveReason {
    DEATH, QUIT, EXTRACTION
}