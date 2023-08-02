package com.ravingarinc.expeditions.play.instance

import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import com.github.shynixn.mccoroutine.bukkit.ticks
import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.expeditions.api.blockWithChunk
import com.ravingarinc.expeditions.api.withChunk
import com.ravingarinc.expeditions.integration.NPCHandler
import com.ravingarinc.expeditions.integration.npc.ExpeditionNPC
import com.ravingarinc.expeditions.locale.type.Area
import com.ravingarinc.expeditions.locale.type.Expedition
import kotlinx.coroutines.delay
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.ChatColor
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.util.BlockVector
import org.bukkit.util.Vector
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * This class is disposed of after each cycle. If a server is shutdown. Then dispose() should
 * never be called. As loading chunks during a shutdown is prohibited.
 */
class AreaInstance(val plugin: RavinPlugin, val expedition: Expedition, val area: Area) {
    private val spawnedMobs: MutableSet<Entity> = ConcurrentHashMap.newKeySet()
    private val spawnedChests: MutableMap<BlockVector, LootableChest> = ConcurrentHashMap()
    private val limit: Int = (area.lootLimit * area.lootLocations.size).toInt()
    private var bossCooldown: Long = 0
    private var boss: Entity? = null
    private var npc: ExpeditionNPC? = null

    val inArea: MutableSet<Player> = HashSet()

    private val previousNPCInteractions: MutableSet<UUID> = HashSet()
    private val availableLootLocations: MutableSet<BlockVector> = ConcurrentHashMap.newKeySet()
    init {
        area.lootLocations.forEach {
            availableLootLocations.add(it)
        }
    }

    /**
     * Initialise the area belonging to this instance. This may involve force loading chunks.
     */
    fun initialise(plugin: RavinPlugin, world: World) {
        area.initialise(plugin, world)
        spawnNPC(world)
    }
    /**
     * Initialise the area belonging to this instance. This may involve force loading chunks.
     */
    suspend fun dispose(plugin: RavinPlugin, world: World) {
        destroyNPC()
        area.dispose(plugin, world)
        spawnedMobs.forEach {
            if(it.isValid) { world.blockWithChunk(plugin, it.location) { _ -> it.remove() } }
        }
        spawnedMobs.clear()
        spawnedChests.forEach { entry ->
            world.blockWithChunk(plugin, entry.key.blockX shr 4, entry.key.blockZ shr 4) { entry.value.destroy() }
        }
        spawnedChests.clear()
        inArea.clear()
        previousNPCInteractions.clear()
    }

    fun getNPCId() : Int {
        return npc?.numericalId() ?: -1
    }

    fun getNPC() : ExpeditionNPC? {
        return npc
    }

    fun startFollowing(player: Player) {
        npc?.let {
            it.startFollowing(player)
            if(previousNPCInteractions.contains(player.uniqueId)) {
                area.npcRefollowText?.let { text ->
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', text))
                }
            } else {
                previousNPCInteractions.add(player.uniqueId)
                area.npcFollowText?.let { text ->
                    player.sendMessage(text)
                }
            }
        }
    }

    fun stopFollowing(player: Player?) {
        npc?.let {
            it.stopFollowing(player)
            area.npcUnfollowText?.let { text ->
                player?.sendMessage(text)
            }
        }
    }

    fun spawnNPC(world: World) {
        if(area.npcIdentifier == null) return
        if(npc != null) return
        val spawnLoc = area.npcSpawnLoc ?: return
        npc = plugin.getModule(NPCHandler::class.java).createNPC(area.npcIdentifier)
        npc?.let { internalNPC ->
            plugin.launch(plugin.minecraftDispatcher) {
                world.blockWithChunk(plugin, spawnLoc.blockX shr 4, spawnLoc.blockZ shr 4) {
                    internalNPC.spawn(spawnLoc.x, spawnLoc.y, spawnLoc.z, world)
                }
                area.npcOnSpawn.forEach {
                    plugin.server.dispatchCommand(plugin.server.consoleSender, it.replace("{id}", internalNPC.numericalId().toString()))
                }
            }


        }
    }

    fun resetNPC(world: World) {
        val spawnLoc = area.npcSpawnLoc ?: return
        npc?.let {
            it.stopFollowing(null)
            it.teleport(spawnLoc.x, spawnLoc.y, spawnLoc.z, world)
        }
    }

    fun destroyNPC() {
        npc?.let {
            if(it.isValid()) it.destroy()
            npc = null
        }
    }

    fun tickMobs(random: Random, world: World) {
        if(boss == null) {
            if(bossCooldown > 0L) {
                bossCooldown -= 20L
            } else if(area.bossSpawnChance != 0.0 && random.nextDouble() < area.bossSpawnChance) {
                area.bossSpawnLocation?.let {
                    if(!world.isChunkLoaded(it.blockX shr 4, it.blockZ shr 4)) return@let
                    boss = area.bossType.spawn(area.bossLevel, it, world)
                }
            }
        }

        val chance = area.mobSpawnChance
        if(chance == 0.0) return
        if(area.mobCollection.isEmpty()) return

        ArrayList(spawnedMobs).forEach {
            if(!it.isValid) { spawnedMobs.remove(it) }
        }

        for(i in 0 until expedition.mobSpawnAmount) {
            val loc = area.mobLocations.randomOrNull(random) ?: break
            if(!world.isChunkLoaded(loc.blockX shr 4, loc.blockZ shr 4)) continue
            if(spawnedMobs.size >= area.maxMobs) break
            if(chance != 1.0 && random.nextDouble() > chance) continue

            val pair = area.mobCollection.random()
            area.mobLocations.randomOrNull(random)?.let { vector ->
                pair.first.spawn(pair.second.random(random), vector, world)?.let { spawnedMobs.add(it) }
            }
        }
    }

    fun tickLoot(random: Random, world: World) {
        val chance = area.lootChance
        if(chance == 0.0) return
        if(area.lootCollection.isEmpty()) return

        if(availableLootLocations.size >= limit) {
            val usingList = buildList {
                for(loc in availableLootLocations) {
                    if(chance != 1.0 && random.nextDouble() > chance) continue
                    this.add(Pair(loc, area.lootCollection.random()))
                }
            }
            usingList.forEach {
                availableLootLocations.remove(it.first)
                world.withChunk(it.first.blockX shr 4, it.first.blockZ shr 4) { _ ->
                    val loot = LootableChest(it.second, this, it.first, world)
                    spawnedChests[it.first] = loot
                    val vector = Vector(it.first.x, it.first.y, it.first.z)
                    val radSquared = expedition.lootRange * expedition.lootRange
                    inArea.forEach { player ->
                        if(player.location.toVector().distanceSquared(vector) < radSquared) {
                            loot.show(player)
                        } else {
                            loot.hide(player)
                        }
                    }
                }

            }
        }
    }

    fun onBlockInteract(plugin: RavinPlugin, block: Block, player: Player) : Boolean {
        val vector = BlockVector(block.x, block.y, block.z)
        val loot = spawnedChests.remove(vector) ?: return false
        plugin.launch {
            loot.loot(player)
            delay(10.ticks)
            availableLootLocations.add(vector)
        }
        return true
    }

    fun onMove(player: Player) : Boolean {
        val loc = player.location.toVector()
        return if(area.isInArea(loc.x.toInt(), loc.y.toInt(), loc.z.toInt())) {
            if(inArea.add(player)) {
                val component = Component.text().append(Component
                    .text(area.displayName).color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD)
                    .append(Component.text(" | ").color(NamedTextColor.GRAY))
                    .append(Component.text(area.displayType).color(NamedTextColor.YELLOW)))
                player.sendActionBar(component)
            }
            val radSquared = expedition.lootRange * expedition.lootRange
            spawnedChests.forEach {
                if(it.key.distanceSquared(loc) < radSquared) {
                    it.value.show(player)
                } else {
                    it.value.hide(player)
                }
            }
            true
        } else {
            if(inArea.remove(player)) {
                spawnedChests.forEach {
                    it.value.hide(player)
                }
            }
            false
        }
    }

    fun onDeath(entity: Entity) : Boolean {
        if(entity is Player) {
            if(inArea.remove(entity)) {
                spawnedChests.forEach {
                    it.value.hide(entity)
                }
            }
        }
        if(boss != null) {
            if(boss == entity) {
                boss = null
                bossCooldown = area.bossCooldown
                return true
            }
        }
        return spawnedMobs.remove(entity)
    }
}