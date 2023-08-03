package com.ravingarinc.expeditions.locale.type

import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.expeditions.api.blockWithChunk
import com.ravingarinc.expeditions.play.item.LootTable
import com.ravingarinc.expeditions.play.mob.MobType
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.util.BlockVector

class ExtractionZone(val chance: Double,
                     val beaconLoc: BlockVector?,
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
                     npcUnfollowText: String
) : Area(displayName, startLoc, endLoc, lootLimit, lootChance, lootTypes, lootLocations, mobSpawnChance, maxMobs, mobs, mobLocations, bossType, bossLevel, bossSpawnChance, bossSpawnLocation, bossCooldown, npcIdentifier, npcSpawnLoc, npcOnSpawn, npcOnExtract, npcFollowText, npcRefollowText, npcUnfollowText) {

    override val displayType: String = "Extraction Zone"
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
}