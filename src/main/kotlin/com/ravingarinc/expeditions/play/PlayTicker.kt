package com.ravingarinc.expeditions.play

import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import com.github.shynixn.mccoroutine.bukkit.ticks
import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.api.module.warn
import com.ravingarinc.expeditions.api.Ticker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

class PlayTicker(plugin: RavinPlugin, private val handler: PlayHandler) : Ticker(plugin, 5.ticks) {
    private val random = Random.Default
    override suspend fun CoroutineScope.tick() {
        for(inst in handler.getAllInstances()) {
            if(!scope.isActive) return
            val phase = inst.getPhase()
            if(!phase.isActive()) continue
            val lock = inst.getTickLock()
            if(lock.isLocked) {
                warn("Could not tick expedition as tick lock is currently in use! Is your server running behind?")
                continue
            }
            lock.lock()
            scope.launch(plugin.minecraftDispatcher) {
                phase.tick(random, inst)
                lock.unlock()
            }
        }
    }
}