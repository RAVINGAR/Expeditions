package com.ravingarinc.expeditions.play.instance

import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.ticks
import com.ravingarinc.api.I
import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.api.module.warn
import com.ravingarinc.expeditions.api.atomic
import com.ravingarinc.expeditions.api.roll
import com.ravingarinc.expeditions.integration.models.ModelManager
import com.ravingarinc.expeditions.locale.type.Expedition
import com.ravingarinc.expeditions.locale.type.ExtractionZone
import com.ravingarinc.expeditions.play.PlayHandler
import com.ravingarinc.expeditions.play.event.ExpeditionExtractEvent
import com.ravingarinc.expeditions.play.event.ExpeditionKillEntityEvent
import com.ravingarinc.expeditions.play.event.ExpeditionNPCExtractEvent
import com.ravingarinc.expeditions.play.render.ExpeditionRenderer
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.SoundStop
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.MapMeta
import org.bukkit.map.MapView
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.BlockVector
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Level
import kotlin.math.abs
import kotlin.random.Random

class ExpeditionInstance(val plugin: RavinPlugin, val expedition: Expedition, val world: World) {
    private val handler = plugin.getModule(PlayHandler::class.java)
    private var phase: Phase = IdlePhase(expedition)
    val bossBar = plugin.server.createBossBar(
        NamespacedKey(plugin, "${world.name}_bossbar"),
        "${expedition.displayName} Expedition",
        BarColor.BLUE, BarStyle.SEGMENTED_12)

    private val warningMessageLog: MutableSet<UUID> = HashSet()

    private val npcFollowers: MutableMap<Player, AreaInstance> = HashMap()

    private val mapView: MapView

    val renderer: ExpeditionRenderer = ExpeditionRenderer(expedition)

    private val spawnedMobs: MutableMap<String,Int> = HashMap()
    private val trackedMobs: MutableMap<UUID, String> = HashMap()

    private var lastSpawn: AtomicReference<BlockVector?> = AtomicReference(null)

    private val availableSpawns: Queue<BlockVector> = ConcurrentLinkedQueue(expedition.spawnLocations.shuffled())

    private val fallingPlayers: MutableSet<Player> = ConcurrentHashMap.newKeySet()

    var score by atomic(-1)

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
        for(it in areaInstances) {
            if(it.area is ExtractionZone && it.isInArea(player)) {
                it.leaveArea(player, false)
                if(warningMessageLog.add(player.uniqueId)) {
                    player.sendMessage(
                        Component
                            .text("Your extraction progress was reset! You must not take damage whilst extracting!")
                            .color(NamedTextColor.RED))
                }
                player.playSound(player, Sound.BLOCK_NOTE_BLOCK_SNARE, 0.8F, 0.5F)
                break
            }
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
    }

    fun getTickLock() : Mutex {
        return tickLock
    }

    fun tickExpedition(random: Random, tickMobs: Boolean, tickLoot: Boolean, tickRandomMobs: Boolean) {
        for(player in fallingPlayers) {
            val loc = player.location
            val material = player.world.getBlockAt(loc.blockX, loc.blockY - 1, loc.blockZ).type
            if(!material.isAir && (material.isCollidable || material.isSolid)) {
                removeFallingEffects(player)
            }
        }
        areaInstances.forEach {
            if(tickMobs) it.tickMobs(random, this)
            if(tickLoot) it.tickLoot(random, score, world)
            it.tick(this)
        }
        if(!tickRandomMobs) return
        joinedPlayers.values.mapNotNull { it.player.player }.forEach { player ->
            val loc = player.location
            if(!fallingPlayers.contains(player)) {
                for(i in 0 until expedition.randomSpawnsAmount) {
                    if(!expedition.randomSpawnChance.roll()) continue
                    val mobLoc = findSuitableLocation(world, loc.blockX, loc.blockZ, 32, handler.getOverhangingBlocks(), 24)
                    if(getMobSpawns(mobLoc.blockX, mobLoc.blockZ) > expedition.maxMobsPerChunk) continue
                    val mob = expedition.randomMobCollection.random()
                    mob.first.spawn(mob.second.random(), BlockVector(mobLoc.blockX, mobLoc.blockY, mobLoc.blockZ), world)?.let {
                        incrementMobSpawns(it.uniqueId, mobLoc.blockX, mobLoc.blockZ)
                    }
                }
            }
        }
    }

    fun breakBlock(block: Block, material: Material) {
        brokenBlocks[BlockVector(block.x, block.y, block.z)] = Pair(block, material)
    }

    fun incrementMobSpawns(mob: UUID, x: Int, z: Int) {
        val hash = "${x shr 4}|${z shr 4}"
        val amount = spawnedMobs[hash] ?: 0
        spawnedMobs[hash] = amount + 1
        trackedMobs[mob] = hash
    }

    fun decrementMobSpawns(mob: UUID) {
        val hash = trackedMobs[mob] ?: return
        val amount = spawnedMobs[hash] ?: 0
        if(amount > 0) {
            spawnedMobs[hash] = amount - 1
        }
    }

    fun getMobSpawns(x: Int, z: Int) : Int {
        val hash = "${x shr 4}|${z shr 4}"
        return spawnedMobs[hash] ?: 0
    }

    fun clearMobSpawns() {
        spawnedMobs.clear()
        trackedMobs.clear()
    }

    private fun addFallingEffects(player: Player) {
        if(fallingPlayers.contains(player)) return
        fallingPlayers.add(player)
        player.addPotionEffect(PotionEffect(PotionEffectType.SLOW_FALLING, 1000000, 0, true, false, false))
        val models = plugin.getModule(ModelManager::class.java)
        if(models.isLoaded) {
            models.attachModel(player)
            player.playSound(player.location, Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.8F, 0.3F)
            player.playSound(player.location, Sound.ITEM_TRIDENT_RIPTIDE_1, 0.4F, 0.6F)
            player.playSound(net.kyori.adventure.sound.Sound.sound(Key.key("item.elytra.flying"), net.kyori.adventure.sound.Sound.Source.PLAYER, 0.5F, 1.0F), net.kyori.adventure.sound.Sound.Emitter.self())
        }
    }

    fun removeFallingEffects(player: Player) {
        if(!fallingPlayers.remove(player)) return
        player.removePotionEffect(PotionEffectType.SLOW_FALLING)
        val models = plugin.getModule(ModelManager::class.java)
        if(models.isLoaded) {
            models.detachModel(player)
            player.world.spawnParticle(Particle.CLOUD, player.location, 15, 1.0, 1.0, 1.0, 0.2)
            player.stopSound(SoundStop.named(Key.key("item.elytra.flying")))
            player.playSound(player.location, Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.8F, 0.3F)
            player.playSound(player.location, Sound.ENTITY_COW_STEP, 0.6F, 0.5F)
        }
    }

    fun participate(collection: Collection<Player>) {
        if(phase is IdlePhase) {
            phase.next(this)
            join(collection)
        } else if(phase is PlayPhase) {
            join(collection)
        }
    }

    private fun join(collection: Collection<Player>) {
        val loc = if(expedition.spawnLocations.isEmpty()) {
            findSuitableLocation(world, expedition.centreX, expedition.centreZ, expedition.radius - 8, handler.getOverhangingBlocks(), 0, 32)
        } else {
            getRandomLocation(world)
        }
        lastSpawn.setRelease(BlockVector(loc.blockX, loc.blockY, loc.blockZ))
        val locations = LinkedList<Location>()
        locations.add(loc)
        for(i in 1 until collection.size) {
            locations.add(findSuitableLocation(world, loc.blockX, loc.blockZ, 8, handler.getOverhangingBlocks(), 1))
        }
        for(player in collection) {
            if(!player.isOnline) continue
            val playerLoc = locations.poll()!!
            val projectedLoc = if(expedition.parachuteYOffset != -1) {
                playerLoc.clone().add(0.0, expedition.parachuteYOffset.toDouble(), 0.0)
            } else {
                playerLoc
            }
            addPlayer(player, projectedLoc, expedition.parachuteYOffset != -1)
            expedition.onJoinCommands.forEach { plugin.server.dispatchCommand(plugin.server.consoleSender, it.replace("@player", player.name)) }
        }
    }

    private fun getRandomLocation(world: World) : Location {
        var vector : BlockVector? = null
        var i = 0
        val total = expedition.spawnLocations.size
        while(vector == null && i < total) {
            val nextVec = (if(availableSpawns.isEmpty()) reshuffleLocations() else availableSpawns.poll()) ?: expedition.spawnLocations.random()
            val lastVec = lastSpawn.acquire
            if(lastVec == null || nextVec.distanceSquared(lastVec) > 1024) {
                vector = nextVec
            }
            i++
        }
        if(vector == null) vector = expedition.spawnLocations.random()
        return Location(world, vector.x + 0.5, vector.y + 0.1, vector.z + 0.5)
    }

    private fun reshuffleLocations() : BlockVector? {
        expedition.spawnLocations.shuffled().forEach { availableSpawns.add(it) }
        return availableSpawns.poll()
    }

    private fun findSuitableLocation(
        world: World,
        x: Int,
        z: Int,
        radius: Int,
        overhanging: Set<Material>,
        minimumDistance: Int = 0,
        minimumDifference: Int = 0
    ): Location {
        for (i in 0..15) {
            var randomX = Random.nextInt(-radius, radius)
            var randomZ = Random.nextInt(-radius, radius)
            if(randomX == 0) randomX = 1
            if(randomZ == 0) randomZ = 1
            val nextX = if(minimumDistance > 0 && ((-minimumDistance)..minimumDistance).contains(randomX)) {
                x + randomX + (minimumDistance * (randomX / abs(randomX)))
            } else {
                x + randomX
            }
            val nextZ = if(minimumDistance > 0 && ((-minimumDistance)..minimumDistance).contains(randomZ)) {
                z + randomZ + (minimumDistance * (randomZ / abs(randomZ)))
            } else {
                z + randomZ
            }

            val block = world.getHighestBlockAt(nextX, nextZ)
            val location = block.location
            val type = block.type
            if (type == Material.WATER || type == Material.LAVA) {
                continue
            }
            var isValid = false
            val chosenY = if(block.y > expedition.highestY) expedition.highestY else block.y
            val vec = lastSpawn.acquire
            if(minimumDifference > 0 && vec != null && vec.distanceSquared(BlockVector(nextX, chosenY, nextZ)) < (minimumDifference * minimumDifference)) {
                continue
            }
            for (y in chosenY downTo expedition.lowestY) {
                val b = world.getBlockAt(nextX, y, nextZ).type
                if(!block.isCollidable) continue
                if(overhanging.contains(b)) continue

                location.y = y.toDouble()
                isValid = true
                break
            }
            if (!isValid) {
                continue
            }
            location.add(0.5, 1.0, 0.5)
            val newX = location.blockX
            val newY = location.blockY
            val newZ = location.blockZ
            for (dX in -2 until 2) {
                for (dZ in -2 until 2) {
                    val blockType =world.getBlockAt(Location(world, newX.toDouble(), newY.toDouble(), newZ.toDouble())).type
                    if(blockType.isAir) continue
                    if(blockType.isBlock && !blockType.isCollidable) continue

                    isValid = false
                    break
                }
            }
            if (isValid) {
                return Location(world, location.x, location.y, location.z)
            }
        }
        warn("Warning! Random location was unable to find a suitable location, using next immediately available coordinates for spawning!")
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
            addPlayer(player, it.previousLocale, it.wasParachuting)
            return true
        }
        return false
    }

    /**
     * Called when a player is added to this expedition. This is either through re-joining or
     * a new join
     */
    fun addPlayer(player: Player, location: Location, shouldAddFallingEffects: Boolean = false) {
        val uuid = player.uniqueId
        val previousLocale = player.location
        if(!player.teleport(location, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
            I.log(Level.WARNING, "Could not teleport '${player.name}' for unknown reason! Something is cancelling this teleportation! Trying again in 60 ticks...")
            plugin.launch {
                delay(60.ticks)
                if(player.isOnline) {
                    addPlayer(player, location, shouldAddFallingEffects)
                }
            }
            return
        }
        if(shouldAddFallingEffects) {
            addFallingEffects(player)
        }
        joinedPlayers[uuid] = CachedPlayer(player, previousLocale, shouldAddFallingEffects)
        bossBar.addPlayer(player)
        onMoveEvent(player)
        handler.addJoinedExpedition(player, this)
        giveMap(player)
    }

    /**
     * Remove player from this expedition instance. Called by death, extraction or quit.
     */
    fun removePlayer(player: Player, reason: RemoveReason) {
        joinedPlayers.remove(player.uniqueId)?.let { cache ->
            removeFallingEffects(player)
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
                            plugin.server.pluginManager.callEvent(ExpeditionNPCExtractEvent(player, npc, expedition))
                            it.destroyNPC()
                        }
                    }
                    expedition.onExtractCommands.forEach { command ->
                        plugin.server.dispatchCommand(plugin.server.consoleSender, command.replace("@player", player.name))
                    }
                    val event = ExpeditionExtractEvent(player, getPlayerArea(player), expedition, cache.previousLocale)
                    plugin.server.pluginManager.callEvent(event)
                    player.teleport(event.returningLocation)
                }
            }
            removeMap(player)

            handler.removeJoinedExpedition(player)
            bossBar.removePlayer(player)
            areaInstances.forEach { it.leaveArea(player, false) }
            warningMessageLog.remove(player.uniqueId)
        }
        if(joinedPlayers.isEmpty()) {
            score = -1
        }
    }

    /**
     * Handle player quit event. This checks if a player is currently participating in this instance.
     */
    fun onQuitEvent(player: Player) {
        val uuid = player.uniqueId
        joinedPlayers[uuid]?.let {
            quitPlayers[uuid] = CachedPlayer(player, player.location, fallingPlayers.contains(player))
            player.teleport(it.previousLocale)
            removePlayer(player, RemoveReason.QUIT)
        }
    }

    fun onMoveEvent(player: Player) {
        areaInstances.forEach { it.onMove(player) }
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
        if(world != entity.world) return false
        if(entity is Player && joinedPlayers.containsKey(entity.uniqueId)) {
            removePlayer(entity, RemoveReason.DEATH)
            areaInstances.forEach { it.leaveArea(entity, false) }
            return true
        }
        for(it in areaInstances) {
            if(it.onDeath(entity)) return true
        }
        entity.killer?.let {
            plugin.server.pluginManager.callEvent(ExpeditionKillEntityEvent(it, entity, "", expedition))
        }
        return true
    }

    fun setPhase(phase: Phase) {
        this.phase.end(this)
        this.phase = phase
        this.phase.start(this)
    }

    fun canJoin() : Boolean {
        if(!phase.isActive()) return false
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
data class CachedPlayer(val player: OfflinePlayer, val previousLocale: Location, val wasParachuting: Boolean)

enum class RemoveReason {
    DEATH, QUIT, EXTRACTION
}