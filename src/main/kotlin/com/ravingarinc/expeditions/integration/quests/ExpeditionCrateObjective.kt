package com.ravingarinc.expeditions.integration.quests

import com.ravingarinc.expeditions.play.event.ExpeditionLootCrateEvent
import org.betonquest.betonquest.BetonQuest
import org.betonquest.betonquest.Instruction
import org.betonquest.betonquest.api.CountingObjective
import org.betonquest.betonquest.utils.PlayerConverter
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener

class ExpeditionCrateObjective(instruction: Instruction) : CountingObjective(instruction), Listener {
    private val pois: MutableList<String> = instruction.getList { it }
    init {
        targetAmount = instruction.getVarNum()
    }

    @EventHandler
    fun onExpeditionLoot(event: ExpeditionLootCrateEvent) {
        val profile = PlayerConverter.getID(event.player)
        if(!containsPlayer(profile)) return
        val poi = event.area.replace(" ", "_", ignoreCase = false)
        if(pois.isNotEmpty() && pois.stream().noneMatch { it == poi }) return

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