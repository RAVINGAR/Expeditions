package com.ravingarinc.expeditions.integration.quests

import com.ravingarinc.expeditions.api.parseMobType
import com.ravingarinc.expeditions.play.event.ExpeditionKillEntityEvent
import com.ravingarinc.expeditions.play.mob.MobType
import org.betonquest.betonquest.BetonQuest
import org.betonquest.betonquest.Instruction
import org.betonquest.betonquest.api.CountingObjective
import org.betonquest.betonquest.utils.PlayerConverter
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener

class ExpeditionMobObjective(instruction: Instruction) : CountingObjective(instruction), Listener {
    private val expeditions: MutableList<String> = instruction.getList { it.lowercase() }
    private val mobTypes: MutableList<MobType> = ArrayList()
    init {
        val mobStrings = instruction.getList { it }
        if(!mobStrings.contains("all")) {
            mobStrings.forEach { parseMobType(it)?.let { it1 -> mobTypes.add(it1) } }
        }
    }

    private val pois: MutableList<String> = instruction.getList { it.lowercase() }
    private val all: Boolean = pois.contains("all")
    init {
        targetAmount = instruction.getVarNum()
    }

    @EventHandler
    fun onExpeditionMobKill(event: ExpeditionKillEntityEvent) {
        val profile = PlayerConverter.getID(event.player)
        if(!containsPlayer(profile)) return
        val entity = event.entity
        val expedition = event.expedition.displayName.replace(" ", "_", ignoreCase = false).lowercase()
        if(!expeditions.contains("all") && expeditions.isNotEmpty() && expeditions.stream().noneMatch { it == expedition}) return

        val poi = event.area.replace(" ", "_", ignoreCase = false).lowercase()
        if(!all && pois.isNotEmpty() && pois.stream().noneMatch { it == poi }) return

        if(mobTypes.isNotEmpty() && mobTypes.stream().noneMatch { it.isSameAs(entity) }) return

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