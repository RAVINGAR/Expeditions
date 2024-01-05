package com.ravingarinc.expeditions.integration.quests

import com.ravingarinc.expeditions.play.event.ExpeditionExtractEvent
import org.betonquest.betonquest.BetonQuest
import org.betonquest.betonquest.Instruction
import org.betonquest.betonquest.api.CountingObjective
import org.betonquest.betonquest.utils.PlayerConverter
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener

class ExpeditionExtractionObjective(instruction: Instruction) : CountingObjective(instruction), Listener {
    init {
        targetAmount = instruction.getVarNum("amount")
    }

    @EventHandler
    fun onExpeditionExtraction(event: ExpeditionExtractEvent) {
        val profile = PlayerConverter.getID(event.player)
        if(!containsPlayer(profile)) return
        if(checkConditions(profile)) {
            getCountingData(profile).progress()
            completeIfDoneOrNotify(profile)
        }
    }

    override fun start() {
        Bukkit.getPluginManager().registerEvents(this, BetonQuest.getInstance())
    }

    override fun stop() {
        HandlerList.unregisterAll(this)
    }
}