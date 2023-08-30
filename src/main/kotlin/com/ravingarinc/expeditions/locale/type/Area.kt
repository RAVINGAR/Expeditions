package com.ravingarinc.expeditions.locale.type

import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.expeditions.api.WeightedCollection
import com.ravingarinc.expeditions.play.item.LootTable
import com.ravingarinc.expeditions.play.mob.MobType
import org.bukkit.World
import org.bukkit.map.MapCursor
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
                    val bossCooldown: Long,
                    val npcIdentifier: String?,
                    val npcSpawnLoc: BlockVector?,
                    val npcOnSpawn: List<String>,
                    val npcOnExtract: List<String>,
                    val npcFollowText: String,
                    val npcRefollowText: String,
                    val npcUnfollowText: String,
                    val cursorType: MapCursor.Type) {
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

    private val xRange = min(startLoc.x, endLoc.x).toInt() .. max(startLoc.x, endLoc.x).toInt()
    private val yRange = min(startLoc.y, endLoc.y).toInt() .. max(startLoc.y, endLoc.y).toInt()
    private val zRange = min(startLoc.z, endLoc.z).toInt() .. max(startLoc.z, endLoc.z).toInt()

    fun isInArea(x: Int, y: Int, z: Int) : Boolean {
        return xRange.contains(x) && yRange.contains(y) && zRange.contains(z)
    }
}