package com.ravingarinc.expeditions.play.instance

import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.ticks
import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.expeditions.locale.type.Area
import com.ravingarinc.expeditions.locale.type.Expedition
import com.sk89q.worldedit.math.BlockVector3
import kotlinx.coroutines.delay
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.util.BlockVector
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * This class is disposed of after each cycle. If a server is shutdown. Then dispose() should
 * never be called. As loading chunks during a shutdown is prohibited.
 */
class AreaInstance(private val expedition: Expedition, val area: Area) {
    private val spawnedMobs: MutableSet<Entity> = ConcurrentHashMap.newKeySet()
    private val spawnedChests: MutableMap<BlockVector, LootableChest> = ConcurrentHashMap()
    private val limit: Int = (area.lootLimit * area.lootLocations.size).toInt()
    private var bossCooldown: Long = 0
    private var boss: Entity? = null

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
    }
    /**
     * Initialise the area belonging to this instance. This may involve force loading chunks.
     */
    fun dispose(plugin: RavinPlugin, world: World) {
        area.dispose(plugin, world)
        spawnedMobs.forEach {
            world.getChunkAt(it.location)
            it.remove()
        }
        spawnedMobs.clear()
        spawnedChests.forEach {
            world.getChunkAt(it.key.blockX, it.key.blockZ)
            it.value.destroy()
        }
        spawnedChests.clear()
    }

    fun tickMobs(random: Random, world: World) {
        if(boss == null) {
            if(bossCooldown > 0L) {
                bossCooldown -= 20L
            } else if(area.bossSpawnChance != 0.0 && random.nextDouble() < area.bossSpawnChance) {
                area.bossSpawnLocation?.let {
                    if(!world.isChunkLoaded(it.blockX, it.blockZ)) return@let
                    boss = area.bossType.spawn(area.bossLevel, it, world)
                }
            }
        }

        val chance = area.mobSpawnChance
        if(chance == 0.0) return
        if(area.mobCollection.isEmpty()) return

        for(i in 0..expedition.mobSpawnAmount) {
            val loc = area.mobLocations.randomOrNull(random) ?: break
            if(!world.isChunkLoaded(loc.blockX, loc.blockZ)) break
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
                world.getChunkAt(it.first.blockX, it.first.blockZ) // Load chunk
                spawnedChests[it.first] = LootableChest(it.second, it.first, world)
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

    fun onDeath(entity: Entity) : Boolean {
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