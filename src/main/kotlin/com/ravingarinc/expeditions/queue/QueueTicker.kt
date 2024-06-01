package com.ravingarinc.expeditions.queue

import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import com.ravingarinc.api.I
import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.api.module.warn
import com.ravingarinc.expeditions.api.Ticker
import com.ravingarinc.expeditions.locale.ExpeditionManager
import kotlinx.coroutines.CoroutineScope
import java.util.logging.Level

class QueueTicker(plugin: RavinPlugin) : Ticker(plugin, 30000) {
    private val queueManager = plugin.getModule(QueueManager::class.java)
    private val expeditions = plugin.getModule(ExpeditionManager::class.java)
    override suspend fun CoroutineScope.tick() {
        I.log(Level.WARNING, "Debug -> Starting tick cycle for queue ticker")
        for(rotation in queueManager.getRotations()) {
            val indexed = queueManager.getIndexedPlayers(rotation.key)

            for(pair in indexed.sortedBy { pair -> pair.second.size }) {
                val chosenMap = expeditions.getMapByIdentifier(rotation.getNextMap())
                if(chosenMap == null) {
                    warn("Could not find expedition called '${rotation.getNextMap()}' within rotation ${rotation.key}")
                    rotation.randomiseMap()
                    continue
                }

                I.log(Level.WARNING, "Debug -> Sorting rotation with size of players " + pair.second.size)
                val list = pair.second
                val originalSize = list.size
                if(originalSize == 0) continue
                val requests = ArrayList<JoinRequest>()
                val minimumPlayersRequired = chosenMap.maxPlayers * queueManager.getMinimumPLayers()

                var size = 0
                while(size < chosenMap.maxPlayers && list.isNotEmpty()) {
                    val request = list.getOrNull(0) ?: break
                    if(list.remove(request)) {
                        requests.add(request)
                        size += request.size()
                    }
                }
                if(originalSize < minimumPlayersRequired) {
                    for(group in queueManager.getRelativeGroups(rotation.key, pair.first)) {
                        if(size >= minimumPlayersRequired) break
                        do {
                            val request = group.getOrNull(0) ?: break
                            if(group.remove(request)) {
                                requests.add(request)
                                size += request.size()
                            }
                        } while(size < minimumPlayersRequired && group.isNotEmpty())
                    }
                }
                I.log(Level.WARNING, "Debug -> Found a total of ${size} players for expedition which requires '${minimumPlayersRequired}' players!")
                if(size < minimumPlayersRequired) {
                    plugin.launch(plugin.minecraftDispatcher) {
                        requests.forEach { request -> queueManager.enqueueRequest(rotation.key, request, true) }
                    }
                } else {
                    plugin.launch(plugin.minecraftDispatcher) {
                        queueManager.dequeueGroup(rotation.key, pair.first, chosenMap, requests)
                    }
                }
            }
        }
    }
}