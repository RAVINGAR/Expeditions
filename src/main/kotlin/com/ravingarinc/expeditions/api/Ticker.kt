package com.ravingarinc.expeditions.api

import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import com.github.shynixn.mccoroutine.bukkit.ticks
import com.ravingarinc.api.I
import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.api.module.warn
import kotlinx.coroutines.*
import java.util.logging.Level
import kotlin.coroutines.CoroutineContext
import kotlin.system.measureTimeMillis

abstract class Ticker(protected val plugin: RavinPlugin, private val period: Long, private val context: CoroutineContext = Dispatchers.IO) {
    protected val scope = CoroutineScope(plugin.minecraftDispatcher)
    fun start(delay: Int = 0) {
        if (!scope.isActive) {
            I.log(Level.WARNING, "Cannot start this ticker as it has already been used!")
        }
        scope.launch(context) {
            delay(delay.ticks)
            while (isActive) {
                val time = measureTimeMillis {
                    tick()
                }
                val next = period - time
                if (next < 0) {
                    warn(
                        "Ticker is running ${(next * -1).formatMilliseconds()} behind! Please consider " +
                                "increasing the tick interval!"
                    )
                } else {
                    delay(next)
                }

            }
        }
    }
    fun cancel() {
        if (scope.isActive) {
            scope.cancel()
        }
    }

    abstract suspend fun CoroutineScope.tick()
}