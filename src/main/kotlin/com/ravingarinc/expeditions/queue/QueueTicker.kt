package com.ravingarinc.expeditions.queue

import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.api.module.warn
import com.ravingarinc.expeditions.api.Ticker
import com.ravingarinc.expeditions.locale.ExpeditionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext

class QueueTicker(plugin: RavinPlugin, val maxWaitTime: Long) : Ticker(plugin, 30000) {
    private val queueManager = plugin.getModule(QueueManager::class.java)
    private val expeditions = plugin.getModule(ExpeditionManager::class.java)
    override suspend fun CoroutineScope.tick() {
        //I.log(Level.WARNING, "Debug -> Starting tick cycle for queue ticker")
        for(rotation in queueManager.getRotations()) {
            val indexed = queueManager.getIndexedPlayers(rotation.key)

            for(pair in indexed.sortedBy { pair -> pair.second.size }) {
                val list = pair.second
                val originalSize = list.sumOf { it.size() }
                if(originalSize == 0) continue
                val chosenMap = expeditions.getMapByIdentifier(rotation.getNextMap())
                if(chosenMap == null) {
                    warn("Could not find expedition called '${rotation.getNextMap()}' within rotation ${rotation.key}")
                    rotation.randomiseMap()
                    continue
                }

                //I.log(Level.WARNING, "Debug -> Sorting rotation with size of players " + pair.second.size)

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
                //I.log(Level.WARNING, "Debug -> Found a total of ${size} players for expedition which requires '${minimumPlayersRequired}' players!")
                if(size < minimumPlayersRequired) {
                    requests.forEach { request ->
                        if(request.joinTime + maxWaitTime < System.currentTimeMillis()) {
                            // If time is less the current time, then we have exceeded max wait time and must queue players
                            val inst = withContext(plugin.minecraftDispatcher) {
                                return@withContext queueManager.findLowestPopInst(rotation, request.score, request.size())
                            }
                            if(inst == null) {
                                queueManager.enqueueRequest(rotation.key, request, true)
                            } else {
                                queueManager.dequeueRequest(inst, request)
                            }
                        } else {
                            queueManager.enqueueRequest(rotation.key, request, true)
                        }
                    }
                } else {
                    plugin.launch(plugin.minecraftDispatcher) {
                        queueManager.dequeueGroup(rotation.key, pair.first, chosenMap, requests)
                    }.join()
                }
            }
            rotation.randomiseMap()
        }
    }
}