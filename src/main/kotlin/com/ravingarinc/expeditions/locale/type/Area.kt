package com.ravingarinc.expeditions.locale.type

import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.expeditions.api.WeightedCollection
import com.ravingarinc.expeditions.play.item.LootTable
import com.ravingarinc.expeditions.play.mob.MobType
import org.apache.commons.lang.math.DoubleRange
import org.bukkit.World
import org.bukkit.util.BlockVector
import kotlin.math.max
import kotlin.math.min

abstract class Area(val displayName: String,
                    val startLoc: BlockVector,
                    val endLoc: BlockVector,
                    val lootLimit: Double,
                    val lootChance: Double,
                    lootTypes: List<Pair<LootTable, Double>>,
                    val lootLocations: List<BlockVector>,
                    val mobSpawnChance: Double,
                    val maxMobs: Int,
                    mobs: List<Triple<MobType, Double, IntRange>>,
                    val mobLocations: List<BlockVector>,
                    val bossType: MobType,
                    val bossLevel: Int,
                    val bossSpawnChance: Double,
                    val bossSpawnLocation: BlockVector?,
                    val bossCooldown: Long) {
    abstract val displayType: String

    val lootCollection: WeightedCollection<LootTable> = WeightedCollection()
    val mobCollection: WeightedCollection<Pair<MobType, IntRange>> = WeightedCollection()
    init {
        lootTypes.forEach {
            lootCollection.add(it.first, it.second)
        }
        mobs.forEach {
            mobCollection.add(Pair(it.first, it.third), it.second)
        }
    }

    fun centre() : Pair<Int, Int> {
        return Pair((startLoc.blockX + endLoc.blockX) / 2, (startLoc.blockZ + endLoc.blockZ) / 2)
    }

    /**
     * Initialise this area when it is instanced. This may involve force loading chunks.
     */
    abstract fun initialise(plugin: RavinPlugin, world: World)

    /**
     * Dispose this area after it's been instanced. This may involve force loading chunks.
     */
    abstract fun dispose(plugin: RavinPlugin, world: World)

    private val xRange = DoubleRange(min(startLoc.x, endLoc.x), max(startLoc.x, endLoc.x))
    private val yRange = DoubleRange(min(startLoc.y, endLoc.y), max(startLoc.y, endLoc.y))
    private val zRange = DoubleRange(min(startLoc.z, endLoc.z), max(startLoc.z, endLoc.z))

    fun isInArea(x: Int, y: Int, z: Int) : Boolean {
        return xRange.containsDouble(x) && yRange.containsDouble(y) && zRange.containsDouble(z)
    }
}