package com.ravingarinc.expeditions.locale.type

import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.expeditions.api.WeightedCollection
import com.ravingarinc.expeditions.play.item.LootTable
import com.ravingarinc.expeditions.play.mob.MobType
import net.kyori.adventure.text.ComponentLike
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
                    val lootTypes: List<Pair<LootTable, Double>>,
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
                    val cursorType: MapCursor.Type,
                    val enterMessage: ComponentLike) {
    abstract val displayType: String

    val lootCollection: WeightedCollection<LootTable> = WeightedCollection()
    val mobCollection: WeightedCollection<Pair<MobType, IntRange>> = WeightedCollection()

    val mappedLootScores: Map<Int, WeightedCollection<LootTable> = HashMap()
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



    protected val xRange = min(startLoc.x, endLoc.x).toInt() .. max(startLoc.x, endLoc.x).toInt()
    protected val yRange = min(startLoc.y, endLoc.y).toInt() .. max(startLoc.y, endLoc.y).toInt()
    protected val zRange = min(startLoc.z, endLoc.z).toInt() .. max(startLoc.z, endLoc.z).toInt()

    fun isInArea(x: Int, y: Int, z: Int) : Boolean {
        return xRange.contains(x) && yRange.contains(y) && zRange.contains(z)
    }

    fun getLootGroup(plugin: RavinPlugin, score: Int) : WeightedCollection<LootTable> {
        val divisor = plugin.getModule(QueueManager::class.java).getDivisor()
        val standardised = floor(score / divisor).toInt() * divisor
        // todo I do not like having to pass in the RavinPlugin bit here, also having a delayed initialisation is bad practice!
        if(mappedLootScores.isEmpty()) {
            mapLootTables(plugin)
        }
        mappedLootScores[standardised]?.let {
            return it
        }
        I.log(Level.WARNING, "Debug -> Could not find loot table for area $displayName for score $standardised - using first entry to compensate.")
        // Todo keep in mind this may not actually be the first one.
        return mappedLootScores.values().iterator().next()
    }

    private fun mapLootTables(plugin: RavinPlugin) {
        val manager = plugin.getModule(QueueManager::class.java)
        val slippage = manager.getSlippage()
        manager.getScoreRanges().forEach {
            val collection = WeightedCollection<LootTable>()
            val range = (it * (1.0 - slippage))..(it * (1.0 + slippage))
            for(type in lootTypes) {
                if(range.contains(type.first.getScore(plugin))) {
                    collection.add(it.first, it.second)
                }
            }
            mappedLootScores[it] = collection
        }
    }

    abstract fun isHidden() : Boolean

    abstract fun tick(world: World)
}