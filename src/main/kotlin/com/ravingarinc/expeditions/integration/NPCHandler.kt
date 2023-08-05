package com.ravingarinc.expeditions.integration

import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.api.module.SuspendingModule
import com.ravingarinc.expeditions.integration.npc.CitizensNPC
import com.ravingarinc.expeditions.integration.npc.ExpeditionNPC
import com.ravingarinc.expeditions.play.PlayHandler
import net.citizensnpcs.api.CitizensAPI
import net.citizensnpcs.api.event.NPCRightClickEvent
import net.citizensnpcs.api.npc.MemoryNPCDataStore
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener

class NPCHandler(plugin: RavinPlugin) : SuspendingModule(NPCHandler::class.java, plugin, isRequired = false) {
    private lateinit var provider: (String) -> ExpeditionNPC?

    private var listener: CitizensListener? = null
    override suspend fun suspendLoad() {
        if(plugin.server.pluginManager.getPlugin("Citizens") != null) {
            provider = { CitizensNPC(it) }
            listener = CitizensListener(plugin)
            CitizensAPI.createNamedNPCRegistry("expeditions", MemoryNPCDataStore())
            plugin.server.pluginManager.registerEvents(listener!!, plugin)
            return
        }
        provider = { null }
    }

    fun createNPC(id: String) : ExpeditionNPC? {
        return provider.invoke(id)
    }

    override suspend fun suspendCancel() {
        listener?.let {
            HandlerList.unregisterAll(it)
        }
    }
}

class CitizensListener(val plugin: RavinPlugin) : Listener {
    val playHandler: PlayHandler = plugin.getModule(PlayHandler::class.java)
    @EventHandler
    fun onNPCRightClick(event: NPCRightClickEvent) {
        val player = event.clicker
        playHandler.getJoinedExpedition(player)?.onNPCClick(player, event.npc.id)
    }
}