package com.ravingarinc.expeditions.play.render

import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import com.google.common.collect.Iterables
import com.google.common.collect.LinkedHashMultiset
import com.google.common.collect.Multiset
import com.google.common.collect.Multisets
import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.expeditions.api.blockWithChunk
import kotlinx.coroutines.*
import org.bukkit.*
import org.bukkit.block.data.BlockData
import org.bukkit.block.data.Levelled
import org.bukkit.util.BlockVector
import org.bukkit.util.NumberConversions.square
import java.awt.Color
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log

class RenderJob(private val plugin: RavinPlugin, private val centreX: Int, private val centreZ: Int, private val radius: Int, private val world: World, private val minBuildHeight: Int) {
    private val colourCache: Array<Color> = Array(16384) { RenderColour.NONE.id }
    private val scope: CoroutineScope = CoroutineScope(plugin.minecraftDispatcher)

    private fun run() : Deferred<Array<Color>> = scope.async(Dispatchers.IO) {
        val i: Int = 1 shl ceil(log(radius * 2 / 128.0, 2.0)).toInt() // scale

        val i4 = 128 * i
        val j: Int = ((floor((centreX + 64.0) / i4)) * i4 + i4 / 2 - 64).toInt()
        val k: Int = ((floor((centreZ + 64.0) / i4)) * i4 + i4 / 2 - 64).toInt()

        val j1 = 128 / i

        val r = i4 / 2
        val minChunkX = ((j - r - 15) shr 4); val maxChunkX = ((j + r + 15) shr 4)
        val minChunkZ = ((k - r - 15) shr 4); val maxChunkZ = ((k + r + 15) shr 4)
        val snapshots = withContext(plugin.minecraftDispatcher) {
            val chunks: MutableMap<Long, ChunkSnapshot> = HashMap()
            for(cX in minChunkX until maxChunkX) {
                for(cZ in minChunkZ until maxChunkZ) {
                    world.blockWithChunk(plugin, cX, cZ) {
                        chunks[Chunk.getChunkKey(cX, cZ)] = (it.getChunkSnapshot(true, true, false))
                    }
                }
            }
            return@withContext chunks
        }

        var flag = false
        var step = 0

        val blockPos = BlockVector()
        val blockPos1 = BlockVector()
        for(x in 0 until 128) {
            for(z in 0 until 128) {
                val entityX = floor(j - radius + (x / 128.0) * radius * 2)
                val entityZ = floor(k - radius + (z / 128.0) * radius * 2)
                val l: Int = ((entityX - j.toDouble()) / i + 64).toInt()
                val i1: Int = ((entityZ - k.toDouble()) / i + 64).toInt()
                ++step
                var k1: Int = l - j1 + 1
                while(k1 < l + j1) {
                    if((k1 and 15) == (step and 15) || flag) {
                        flag = false
                        var d0 = 0.0
                        var l1 = i1 - j1 - 1
                        while(l1 < i1 + j1) {
                            if(k1 >= 0 && l1 >= -1 && k1 < 128 && l1 < 128) {
                                val i2 = (square((k1 - l).toDouble()) + square((l1 - i1).toDouble())).toInt()
                                val flag1 = i2 > (j1 - 2) * (j1 - 2)
                                val j2 = (j / i + k1 - 64) * i
                                val k2 = (k / i + l1 - 64) * i
                                val multiset: Multiset<RenderColour> = LinkedHashMultiset.create()
                                val chunk = snapshots[Chunk.getChunkKey(j2 shr 4, k2 shr 4)]!!
                                var l2 = 0
                                var d1 = 0.0

                                for(i3 in 0 until i) {
                                    for(j3 in 0 until i) {
                                        blockPos.setX(j2 + i3)
                                        blockPos.setY(0)
                                        blockPos.setZ(k2 + j3)

                                        var k3 = chunk.getHighestBlockYAt(blockPos.blockX.mod(16), blockPos.blockZ.mod(16)) + 1
                                        var data: BlockData
                                        if(k3 > minBuildHeight + 1) {
                                            do {
                                                --k3
                                                blockPos.setY(k3)
                                                data = chunk.getBlockData(blockPos.blockX.mod(16), blockPos.blockY, blockPos.blockZ.mod(16))
                                            } while ( RenderColour.NONE.predicate.invoke(data.material) && k3 > minBuildHeight)

                                            // isEmpty
                                            // TRUE should be if
                                            if(k3 > minBuildHeight && !(data is Levelled && data.level == data.minimumLevel)) {
                                                // This block should execute only if block is a fluid and is not empty
                                                var l3 = k3 - 1
                                                var data1: BlockData
                                                blockPos1.setX(blockPos.blockX)
                                                blockPos1.setY(blockPos.blockY)
                                                blockPos1.setZ(blockPos.blockZ)
                                                do {
                                                    blockPos1.setY(l3--)
                                                    data1 = chunk.getBlockData(blockPos1.blockX.mod(16), blockPos1.blockY, blockPos1.blockZ.mod(16))
                                                    ++l2
                                                } while (l3 > minBuildHeight && !(data1 is Levelled && data1.level == data1.minimumLevel))
                                            }
                                        } else {
                                            data = Bukkit.createBlockData(Material.BEDROCK) // todo is this async safe?
                                        }

                                        d1 += k3.toDouble() / (i * i).toDouble()
                                        multiset.add(RenderColour.match(data.material))
                                    }
                                }
                                l2 /= i * i
                                val mapColour = Iterables.getFirst(Multisets.copyHighestCountFirst(multiset), RenderColour.NONE)!!
                                var d2: Double
                                var brightness: RenderColour.Brightness
                                if(mapColour == RenderColour.WATER) {
                                    d2 = l2 * 0.1 + (k1 + l1 and 1).toDouble() * 0.2
                                    brightness = if(d2 < 0.5) {
                                        RenderColour.Brightness.HIGH
                                    } else if(d2 > 0.9) {
                                        RenderColour.Brightness.LOW
                                    } else {
                                        RenderColour.Brightness.NORMAL
                                    }
                                } else {
                                    d2 = (d1 - d0) * 4.0 / (i + 4).toDouble() + ((k1 + l1 and 1).toDouble() - 0.5) * 0.4
                                    brightness = if(d2 > 0.6) {
                                        RenderColour.Brightness.HIGH
                                    } else if(d2 < -0.6) {
                                        RenderColour.Brightness.LOW
                                    } else {
                                        RenderColour.Brightness.NORMAL
                                    }
                                }

                                d0 = d1
                                if(l1 >= 0 && i2 < j1 * j1 && (!flag1 || (k1 + l1 and 1) != 0)) {
                                    flag = flag or setColor(k1, l1, mapColour.withBrightness(brightness))
                                }
                            }
                            ++l1
                        }
                    }
                    ++k1
                }
            }
            scope.ensureActive()
        }
        return@async colourCache
    }

    private fun setColor(x: Int, z: Int, colour: Color) : Boolean {
        val oldColour = colourCache[x + z * 128]

        if(oldColour != colour) {
            colourCache[x + z * 128] = colour
            return true
        }
        return false
    }

    fun getProgress() : String {
        return ""
    }

    companion object {
        fun render(plugin: RavinPlugin, centreX: Int, centreZ: Int, radius: Int, world: World, minBuildHeight: Int) : Deferred<Array<Color>> {
            val render = RenderJob(plugin, centreX, centreZ, radius, world, minBuildHeight)
            return render.run()
        }
    }
}