package com.ravingarinc.expeditions.integration.quests

import com.ravingarinc.expeditions.play.event.ExpeditionNPCExtractEvent
import org.betonquest.betonquest.BetonQuest
import org.betonquest.betonquest.Instruction
import org.betonquest.betonquest.api.CountingObjective
import org.betonquest.betonquest.utils.PlayerConverter
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener

class ExpeditionNPCObjective(instruction: Instruction) : CountingObjective(instruction), Listener {
    private val npcIdentifiers: MutableList<String> = instruction.getList { it }

    init {
        targetAmount = instruction.getVarNum("amount")
    }

    @EventHandler
    fun onExpeditionNPCExtraction(event: ExpeditionNPCExtractEvent) {
        val profile = PlayerConverter.getID(event.player)
        if(!containsPlayer(profile)) return
        val npc = event.npc
        if(npcIdentifiers.isNotEmpty() && npcIdentifiers.stream().noneMatch { it == npc.identifier() }) return

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