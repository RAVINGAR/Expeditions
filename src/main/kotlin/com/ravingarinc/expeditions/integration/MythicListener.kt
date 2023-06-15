package com.ravingarinc.expeditions.integration

import com.github.shynixn.mccoroutine.bukkit.launch
import com.ravingarinc.api.module.ModuleLoadException
import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.api.module.SuspendingModuleListener
import com.ravingarinc.expeditions.locale.ExpeditionManager
import io.lumine.mythic.bukkit.events.MythicReloadedEvent
import org.bukkit.event.EventHandler

class MythicListener(plugin: RavinPlugin) : SuspendingModuleListener(MythicListener::class.java, plugin, ExpeditionManager::class.java) {
    private lateinit var manager: ExpeditionManager
    override suspend fun suspendLoad() {
        if(plugin.server.pluginManager.getPlugin("MythicMobs") == null) {
            throw ModuleLoadException(this, ModuleLoadException.Reason.EXCEPTION, IllegalStateException("Could not find MythicMobs plugin!"))
        }
        manager = plugin.getModule(ExpeditionManager::class.java)
        super.suspendLoad()
    }

    @EventHandler
    fun onMythicReload(event: MythicReloadedEvent) {
        manager.reloadMobs()
    }
}