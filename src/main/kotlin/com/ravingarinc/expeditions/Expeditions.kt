package com.ravingarinc.expeditions

import com.ravingarinc.api.module.RavinPluginKotlin
import com.ravingarinc.expeditions.command.ExpeditionCommand
import com.ravingarinc.expeditions.integration.MultiverseHandler
import com.ravingarinc.expeditions.integration.MythicListener
import com.ravingarinc.expeditions.locale.MapListener
import com.ravingarinc.expeditions.locale.ExpeditionManager
import com.ravingarinc.expeditions.persistent.ConfigManager
import com.ravingarinc.expeditions.play.PlayHandler

class Expeditions : RavinPluginKotlin() {

    override fun loadModules() {
        addModule(ConfigManager::class.java)

        addModule(MultiverseHandler::class.java)
        //addModule(WorldGuardHandler::class.java)

        addModule(ExpeditionManager::class.java)
        addModule(MythicListener::class.java)

        addModule(PlayHandler::class.java)
        addModule(MapListener::class.java)
    }

    override fun loadCommands() {
        ExpeditionCommand(this).register()
    }
}