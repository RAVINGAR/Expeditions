package com.ravingarinc.expeditions.integration

import com.onarandombox.MultiverseCore.MultiverseCore
import com.ravingarinc.api.module.ModuleLoadException
import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.api.module.SuspendingModule

class WorldGuardHandler(plugin: RavinPlugin) : SuspendingModule(WorldGuardHandler::class.java, plugin) {

    override suspend fun suspendLoad() {
        if (!plugin.server.pluginManager.isPluginEnabled("WorldGuard")) {
            throw ModuleLoadException(this, ModuleLoadException.Reason.DEPENDENCY, IllegalStateException("Could not load WorldGuardHandler as WorldGuard was not loaded!"))
        }
        if (!plugin.server.pluginManager.isPluginEnabled("WorldEdit")) {
            throw ModuleLoadException(this, ModuleLoadException.Reason.DEPENDENCY, IllegalStateException("Could not load WorldGuardHandler as WorldEdit was not loaded!"))
        }
        // Todo, do we need this?
    }

    override suspend fun suspendCancel() {

    }
}