package com.ravingarinc.expeditions.locale.type

import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.expeditions.api.blockWithChunk
import com.ravingarinc.expeditions.play.item.LootTable
import com.ravingarinc.expeditions.play.mob.MobType
import net.kyori.adventure.text.ComponentLike
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.World
import org.bukkit.map.MapCursor
import org.bukkit.util.BlockVector

class ExtractionZone(val chance: Double,
                     val beaconLoc: BlockVector?,
                     val particleHeight: Double,
                     displayName: String,
                     startLoc: BlockVector,
                     endLoc: BlockVector,
                     lootLimit: Double,
                     lootChance: Double,
                     lootTypes: List<Pair<LootTable, Double>>,
                     lootLocations: List<BlockVector>,
                     mobSpawnChance: Double,
                     maxMobs: Int,
                     mobs: List<Triple<MobType, Double, IntRange>>,
                     mobLocations: List<BlockVector>,
                     bossType: MobType,
                     bossLevel: Int,
                     bossSpawnChance: Double,
                     bossSpawnLocation: BlockVector?,
                     bossCooldown: Long,
                     npcIdentifier: String?,
                     npcSpawnLoc: BlockVector?,
                     npcOnSpawn: List<String>,
                     npcOnExtract: List<String>,
                     npcFollowText: String,
                     npcRefollowText: String,
                     npcUnfollowText: String,
                     cursorType: MapCursor.Type,
                     enterMessage: ComponentLike
) : Area(displayName, startLoc, endLoc, lootLimit, lootChance, lootTypes, lootLocations, mobSpawnChance, maxMobs, mobs, mobLocations, bossType, bossLevel, bossSpawnChance, bossSpawnLocation, bossCooldown, npcIdentifier, npcSpawnLoc, npcOnSpawn, npcOnExtract, npcFollowText, npcRefollowText, npcUnfollowText, cursorType, enterMessage) {

    override val displayType: String = "Extraction Zone"

    private val particleData = Particle.DustTransition(Color.fromRGB(215,49,12), Color.fromRGB(255,179,39), 0.5F)

    override fun initialise(plugin: RavinPlugin, world: World) {
        beaconLoc?.let {
            world.blockWithChunk(plugin, it.blockX shr 4, it.blockZ shr 4) { chunk ->
                world.getBlockAt(it.blockX, it.blockY, it.blockZ).type = Material.BEACON
            }
        }
    }

    override fun dispose(plugin: RavinPlugin, world: World) {
        beaconLoc?.let {
            world.blockWithChunk(plugin, it.blockX shr 4, it.blockZ shr 4) { chunk ->
                world.getBlockAt(it.blockX, it.blockY, it.blockZ).type = Material.STONE
            }
        }
    }

    override fun tick(world: World) {
        val startX = startLoc.x.toInt()
        val endX = endLoc.x.toInt()
        val startZ = startLoc.z.toInt()
        val endZ = endLoc.z.toInt()
        for(x in xRange) {
            if(x == startX || x == endX) for(z in zRange) {
                if(z == startZ || z == endZ) {
                    world.spawnParticle(Particle.REDSTONE, x.toDouble(), particleHeight, z.toDouble(), 1, 0.0, 0.05, 0.0, particleData)
                }
            }
        }
    }
}