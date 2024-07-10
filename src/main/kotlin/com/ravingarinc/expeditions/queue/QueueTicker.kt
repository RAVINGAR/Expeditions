package com.ravingarinc.expeditions.queue

import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import com.github.shynixn.mccoroutine.bukkit.ticks
import com.ravingarinc.api.I
import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.api.module.warn
import com.ravingarinc.expeditions.api.Ticker
import com.ravingarinc.expeditions.api.formatMilliseconds
import com.ravingarinc.expeditions.locale.ExpeditionManager
import com.ravingarinc.expeditions.play.PlayHandler
import com.ravingarinc.expeditions.play.instance.ExpeditionInstance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.TitlePart
import org.bukkit.Sound
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

class QueueTicker(plugin: RavinPlugin, val maxWaitTime: Long) : Ticker(plugin, 20.ticks) {
    private val queueManager = plugin.getModule(QueueManager::class.java)
    private val expeditions = plugin.getModule(ExpeditionManager::class.java)
    private val handler = plugin.getModule(PlayHandler::class.java)
    private val syncLock = Mutex(false)

    private val toRemove: MutableSet<ExpeditionInstance> = ConcurrentHashMap.newKeySet()

    private var ticks = 0L
    override suspend fun CoroutineScope.tick() {
        //I.log(Level.WARNING, "Debug -> Starting tick cycle for queue ticker")
        for(rotation in queueManager.getRotations()) {
            val indexed = queueManager.getIndexedPlayers(rotation.key)
            asSync {
                indexed.forEach { it.second.forEach { request ->
                    val component = Component.text()
                        .append(Component.text("Queued ↔ ").color(NamedTextColor.GRAY))
                        .append(Component.text(rotation.key.uppercase()).color(NamedTextColor.GOLD))
                        .append(Component.text(" | ").color(NamedTextColor.GRAY))
                        .append(Component.text("Position ↔ ").color(NamedTextColor.GRAY))
                        .append(Component.text("1 / ${request.players.size}").color(NamedTextColor.YELLOW))
                        .append(Component.text(" | ").color(NamedTextColor.GRAY))
                        .append(Component.text("Time  ↔ ").color(NamedTextColor.GRAY))
                        .append(Component.text((System.currentTimeMillis() - request.joinTime).formatMilliseconds()).color(NamedTextColor.YELLOW)).build()
                    request.players.forEach { player -> player.sendActionBar(component) }
                } }
            }
        }

        ticks += 20L

        if(ticks % 100L != 0L) return
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
                            asSync {
                                val inst = queueManager.findExistingInst(rotation, request.score, request.size())
                                    ?: queueManager.findLowestPopInst(rotation, request.score, request.size())
                                if(inst == null) {
                                    queueManager.enqueueRequest(request, true)
                                } else {
                                    val startTime = System.currentTimeMillis()
                                    sendTitle(request, inst.expedition.displayName)
                                    delay(5000 - (System.currentTimeMillis() - startTime))
                                    queueManager.dequeueRequest(inst, request)
                                }
                            }
                        } else {
                            queueManager.enqueueRequest(request, true)
                        }
                    }
                } else {
                    asSync {
                        requests.forEach { sendTitle(it, chosenMap.displayName)}
                        queueManager.dequeueGroup(pair.first, chosenMap, requests)
                    }
                }
            }
            rotation.randomiseMap()

            if(toRemove.isEmpty()) return
            ArrayList(toRemove).forEach {
                if(toRemove.remove(it)) {
                    handler.destroyInstance(it)
                    handler.removeInstance(it)
                }
            }
        }
    }

    fun queueForRemoval(inst: ExpeditionInstance) {
        toRemove.add(inst)
    }

    private fun sendTitle(request: JoinRequest, displayName: String) {
        val mainTitle = Component.text("Expedition Found!").color(NamedTextColor.GOLD)
        val subTitle = Component.text("'${displayName}' will begin shortly...").color(NamedTextColor.YELLOW)
        val bar = Component.text()
            .append(Component.text("Queued ↔ ").color(NamedTextColor.GRAY))
            .append(Component.text(request.rotation.uppercase()).color(NamedTextColor.GOLD))
            .append(Component.text(" | ").color(NamedTextColor.GRAY))
            .append(Component.text("Expedition Found ↔ ").color(NamedTextColor.GRAY))
            .append(Component.text(displayName).color(NamedTextColor.GREEN)).build()
        request.players.forEach {
            it.sendTitlePart(TitlePart.TITLE, mainTitle)
            it.sendTitlePart(TitlePart.SUBTITLE, subTitle)
            it.sendActionBar(bar)
            it.playSound(it, Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 0.5F)
        }
    }

    private suspend fun asSync(block: suspend CoroutineScope.() -> Unit) {
        syncLock.lock()
        val job = scope.launch(plugin.minecraftDispatcher) {
            block.invoke(this)
        }
        job.invokeOnCompletion {
            if(it != null && it !is CancellationException) {
                I.log(Level.SEVERE, "Encountered unexpected exception in QueueTicker!", it)
            }
            syncLock.unlock()
        }
    }
}