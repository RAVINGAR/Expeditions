package com.ravingarinc.expeditions.locale.type

import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.expeditions.play.item.LootTable
import com.ravingarinc.expeditions.play.mob.MobType
import org.bukkit.World
import org.bukkit.util.BlockVector

class PointOfInterest(displayName: String,
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
                      bossCooldown: Long
) : Area(displayName, startLoc, endLoc, lootLimit, lootChance, lootTypes, lootLocations, mobSpawnChance, maxMobs, mobs, mobLocations, bossType, bossLevel, bossSpawnChance, bossSpawnLocation, bossCooldown) {
    override val displayType: String = "Point of Interest"
    override fun initialise(plugin: RavinPlugin, world: World) {

    }

    override fun dispose(plugin: RavinPlugin, world: World) {


    }
}