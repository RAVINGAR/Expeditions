package com.ravingarinc.expeditions.locale.type

import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.expeditions.play.instance.ExpeditionRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.World

class Expedition(val identifier: String,
                 val description: String,
                 val displayName: String,
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
                 val lootRange: Double) {
    private val areas: MutableList<Area> = ArrayList()

    private val formatted: String
    val colourCache: Array<Byte> = Array(16384) { ExpeditionRenderer.MapColour.STONE.id }

    init {
        val builder = StringBuilder()
        description.split("\n".toRegex()).forEach {
            builder.append("\n")
            builder.append(ChatColor.GRAY)
            builder.append(it)
        }
        formatted = builder.toString()
    }

    fun render(plugin: RavinPlugin) : Job {
        val topLeftX = centreX - radius
        val topLeftZ = centreZ - radius
        return plugin.launch(Dispatchers.IO) {
            for(xF in 1 .. 4) {
                for(xZ in 1 .. 4) {
                    plugin.launch(Dispatchers.IO) {
                        for(x in (xF - 1) * 32 until xF * 32) {
                            for(z in (xZ - 1) * 32 until xZ * 32) {
                                val sumX = topLeftX + (x / 128) * radius * 2
                                val sumZ = topLeftZ + (z / 128) * radius * 2
                                val type = withContext(plugin.minecraftDispatcher) {
                                    world.getHighestBlockAt(sumX, sumZ).type
                                }
                                ExpeditionRenderer.MapColour.values().forEach {
                                    if(it.predicate.invoke(type)) {
                                        colourCache[z * 128 + x] = it.id
                                        return@forEach
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
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