package com.ravingarinc.expeditions.locale.type

import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import com.ravingarinc.api.I
import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.api.module.warn
import com.ravingarinc.expeditions.api.WeightedCollection
import com.ravingarinc.expeditions.play.mob.MobType
import com.ravingarinc.expeditions.play.render.RenderColour
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.util.BlockVector
import java.awt.Color
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level

class Expedition(val identifier: String,
                 description: List<String>,
                 val displayName: String,
                 val permission: String?,
                 val lockedMessage: String,
                 val world: World,
                 val maxPlayers: Int,
                 val centreX: Int,
                 val centreZ: Int,
                 val radius: Int,
                 val mobSpawnAmount: Int,
                 val calmPhaseDuration: Long,
                 val stormPhaseDuration: Long,
                 val mobInterval: Long,
                 val mobModifier: Double,
                 val lootInterval: Long,
                 val lootModifier: Double,
                 val lootBlock: Material,
                 val extractionTime: Long,
                 val lootRange: Double,
                 val spawnLocations: List<BlockVector>,
                 val onJoinCommands: List<String>,
                 val onExtractCommands: List<String>,
                 randomMobs: List<Triple<MobType, Double, IntRange>>,
                 val randomMobInterval: Long,
                 val randomSpawnsAmount: Int,
                 val randomSpawnChance: Double,
                 val maxMobsPerChunk: Int,
                 val lowestY: Int,
                 val highestY: Int) {
    private val areas: MutableList<Area> = ArrayList()

    private val formatted: String

    val colourCache: Array<Color> = Array(16384) { RenderColour.STONE.id }

    private var mapJob: Job? = null
    private var isMapDone = AtomicBoolean(false)

    val randomMobCollection: WeightedCollection<Pair<MobType, IntRange>> = WeightedCollection()

    init {
        randomMobs.forEach {
            randomMobCollection.add(Pair(it.first, it.third), it.second)
        }
    }

    init {
        val builder = StringBuilder()
        for((i, line) in description.withIndex()) {
            builder.append(ChatColor.translateAlternateColorCodes('&', line))
            if(i + 1 < description.size) {
                builder.append("\n")
            }
        }
        formatted = builder.toString()
    }

    suspend fun render(plugin: RavinPlugin) {
        if(mapJob != null) {
            warn("Cannot render expedition '$displayName' as it is already being rendered!")
            return
        }
        val startTime = System.currentTimeMillis()
        val topLeftX = centreX - radius
        val topLeftZ = centreZ - radius

        mapJob = plugin.launch(Dispatchers.IO) {
            val jobs = ArrayList<Job>()
            for(xF in 1 .. 4) {
                for(xZ in 1 .. 4) {
                    jobs.add(this.launch(plugin.minecraftDispatcher) {
                        val children = ArrayList<Job>()
                        for(x in (xF - 1) * 32 until xF * 32) {
                            for(z in (xZ - 1) * 32 until xZ * 32) {
                                val sumX = (topLeftX + (x / 128.0) * radius * 2).toInt()
                                val sumZ = (topLeftZ + (z / 128.0) * radius * 2).toInt()

                                children.add(this.launch(Dispatchers.IO) {
                                    val colours = ArrayList<Color>()
                                    for(innerX in 0 until 2) {
                                        for(innerZ in 0 until 2) {
                                            val type = world.getHighestBlockAt(sumX + innerX, sumZ + innerZ).type
                                            if(type == Material.BARRIER) continue
                                            for(colour in RenderColour.values()) {
                                                if(colour.predicate.invoke(type)) {
                                                    //colours[innerX + 2 * innerZ] = colour.id
                                                    colours.add(colour.id)
                                                    break
                                                }
                                            }
                                        }
                                    }
                                    this@Expedition.colourCache[z * 128 + x] = if(colours.isEmpty()) {
                                        RenderColour.STONE.id
                                    } else {
                                        var totalRed = 0; var totalGreen = 0; var totalBlue = 0;
                                        for(c in colours) {
                                            totalRed += c.red
                                            totalGreen += c.green
                                            totalBlue += c.blue
                                        }
                                        Color(totalRed / colours.size, totalGreen / colours.size, totalBlue / colours.size)
                                    }
                                })
                            }
                        }
                        children.joinAll()
                    })
                }
            }
            jobs.joinAll()
        }
        mapJob?.invokeOnCompletion {
            if(it == null) {
                I.log(Level.INFO, "Successfully rendered map for '${displayName}' expedition, taking ${System.currentTimeMillis() - startTime} ms!")
                isMapDone.set(true)
            } else {
                I.log(Level.WARNING, "Encountered exception rendering map for '${displayName}' expedition!", it)
            }
            mapJob = null
        }
    }

    fun isMapRendered() : Boolean {
        return isMapDone.get()
    }

    fun addArea(area: Area) {
        areas.add(area)
    }

    fun getFormattedDescription() : String {
        return formatted
    }

    fun getArea(x: Int, y: Int, z: Int) : Area? {
        areas.forEach {
            if(it.isInArea(x, y, z)) {
                return it
            }
        }
        return null
    }

    fun getAreas() : List<Area> {
        return areas
    }
}