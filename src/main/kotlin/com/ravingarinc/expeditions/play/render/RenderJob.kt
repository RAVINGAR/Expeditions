package com.ravingarinc.expeditions.play.render

import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.expeditions.locale.type.Expedition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.awt.Color

class RenderJob(private val plugin: RavinPlugin, private val expedition: Expedition) {
    private val colourCache: Array<Color> = Array(16384) { RenderColour.STONE.id }
    private val scope: CoroutineScope = CoroutineScope(plugin.minecraftDispatcher)
    private var startTime: Long = 0
    fun start() : Job = scope.launch(Dispatchers.IO) {
        startTime = System.currentTimeMillis()
        val centreX = expedition.centreX
        val centreZ = expedition.centreZ
        val radius = expedition.radius
        val topLeftX = centreX - radius
        val topLeftZ = centreZ - radius
        val offsetX = topLeftX % 16
        val offsetZ = topLeftZ % 16
        val chunkX = topLeftX shr 4
        val chunkZ = topLeftZ shr 4
        // Basically if we are mid chunk, then lets say calculating pixel at 0,0
        // Then this must be the block from chunk at topLeftX shr 4, with starting offset
        /*
        128 x 128 Pixels
        FIrst step is calculate all the chunks that will be involved

        Maximum chunks to scan
        */
        val minChunkX = ((centreX - radius) shr 4); val maxChunkX = ((centreX + radius) shr 4)
        val minChunkZ = ((centreZ - radius) shr 4); val maxChunkZ = ((centreZ + radius) shr 4)
        for(cX in minChunkX..maxChunkX) {
            for(cZ in minChunkZ..maxChunkZ) {

            }
        }
    }

    private fun processChunk() {

    }
}