package com.ravingarinc.expeditions

import com.ravingarinc.api.I
import com.ravingarinc.api.Version
import com.ravingarinc.api.Versions
import com.ravingarinc.api.module.RavinPluginKotlin
import com.ravingarinc.expeditions.command.ExpeditionCommand
import com.ravingarinc.expeditions.integration.MultiverseHandler
import com.ravingarinc.expeditions.integration.MythicListener
import com.ravingarinc.expeditions.integration.NPCHandler
import com.ravingarinc.expeditions.integration.PlaceholderInjector
import com.ravingarinc.expeditions.locale.ExpeditionListener
import com.ravingarinc.expeditions.locale.ExpeditionManager
import com.ravingarinc.expeditions.party.PartyManager
import com.ravingarinc.expeditions.persistent.ConfigManager
import com.ravingarinc.expeditions.play.PlayHandler
import java.util.logging.Level

class Expeditions : RavinPluginKotlin() {

    override fun onEnable() {
        try {
            Versions.initialise(arrayOf(
                Version.V1_19_2,
                Version.V1_19_3,
                Version.V1_19_4,
                Version.V1_20,
                Version.V1_20_2
            ))
        } catch(exception: IllegalStateException) {
            I.log(Level.SEVERE, "Encountered exception loading plugin!", exception)
        }
        super.onEnable()
    }
    override fun loadModules() {
        addModule(ConfigManager::class.java)
        addModule(MultiverseHandler::class.java)
        addModule(ExpeditionManager::class.java)
        addModule(MythicListener::class.java)
        addModule(PartyManager::class.java)
        addModule(PlayHandler::class.java)
        addModule(NPCHandler::class.java)
        addModule(ExpeditionListener::class.java)
        addModule(PlaceholderInjector::class.java)
    }

    override fun loadCommands() {
        ExpeditionCommand(this).register()
    }
}