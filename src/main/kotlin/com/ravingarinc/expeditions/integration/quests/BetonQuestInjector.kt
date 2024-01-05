package com.ravingarinc.expeditions.integration.quests

import com.ravingarinc.api.module.ModuleLoadException
import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.api.module.SuspendingModule
import org.betonquest.betonquest.BetonQuest
import org.betonquest.betonquest.api.logger.BetonQuestLogger

class BetonQuestInjector(plugin: RavinPlugin) : SuspendingModule(BetonQuestInjector::class.java, plugin) {
    private lateinit var logger: BetonQuestLogger

    override suspend fun suspendLoad() {
        if(plugin.server.pluginManager.getPlugin("BetonQuest") == null)
            throw ModuleLoadException(this, ModuleLoadException.Reason.PLUGIN_DEPEND)

        logger = plugin.server.servicesManager.load(BetonQuestLogger::class.java)!!
        val instance = BetonQuest.getInstance()
        instance.registerObjectives("expeditionloot", ExpeditionCrateObjective::class.java)
        instance.registerObjectives("expeditionextraction", ExpeditionExtractionObjective::class.java)
        instance.registerObjectives("expeditionmobkill", ExpeditionMobObjective::class.java)
        instance.registerObjectives("expeditionnpcextraction", ExpeditionNPCObjective::class.java)
    }

    override suspend fun suspendCancel() {

    }
}