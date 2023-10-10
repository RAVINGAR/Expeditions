package com.ravingarinc.expeditions.party

import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.api.module.SuspendingModule

class PartyManager(plugin: RavinPlugin) : SuspendingModule(PartyManager::class.java, plugin, isRequired = false) {
    private var provider: PartyProvider? = null
    override suspend fun suspendLoad() {
        val parties = plugin.server.pluginManager.getPlugin("Parties")
        if(parties != null && parties.isEnabled) {
            provider = PartiesPluginProvider()
        }
    }

    fun getProvider() : PartyProvider? {
        return provider
    }

    override suspend fun suspendCancel() {
        provider = null
    }

}