package com.ravingarinc.expeditions.play

import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import com.github.shynixn.mccoroutine.bukkit.ticks
import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.expeditions.api.Ticker
import com.ravingarinc.expeditions.play.instance.ExpeditionInstance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

class PlayTicker(plugin: RavinPlugin, private val instances: Collection<List<ExpeditionInstance>>) : Ticker(plugin, 20.ticks) {
    private val random = Random.Default
    override suspend fun CoroutineScope.tick() {
        instances.forEach { list -> list.forEach {
            if(!scope.isActive) return
            scope.launch(plugin.minecraftDispatcher) {
                it.tick(random)
            }
        } }
    }
}