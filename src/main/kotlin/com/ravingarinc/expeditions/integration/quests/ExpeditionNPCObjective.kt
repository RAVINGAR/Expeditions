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
    private val expeditions: MutableList<String> = instruction.getList { it.lowercase() }
    private val npcIdentifiers: MutableList<String> = instruction.getList { it.lowercase() }

    init {
        if(expeditions.contains("all")) expeditions.clear()
        if(npcIdentifiers.contains("all")) npcIdentifiers.clear()
        targetAmount = instruction.getVarNum()
    }

    @EventHandler
    fun onExpeditionNPCExtraction(event: ExpeditionNPCExtractEvent) {
        val profile = PlayerConverter.getID(event.player)
        if(!containsPlayer(profile)) return
        val npc = event.npc

        val expedition = event.expedition.displayName.replace(" ", "_", ignoreCase = false).lowercase()
        if(expeditions.isNotEmpty() && expeditions.stream().noneMatch { it == expedition}) return

        val identifier = npc.identifier().replace(" ", "_", ignoreCase = false).lowercase()
        if(npcIdentifiers.isNotEmpty() && npcIdentifiers.stream().noneMatch { it == identifier }) return

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