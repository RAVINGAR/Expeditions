package com.ravingarinc.expeditions.play.event

import com.ravingarinc.expeditions.integration.npc.ExpeditionNPC
import com.ravingarinc.expeditions.locale.type.Expedition
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Defines a miscellaneous expedition event.
 */
sealed class ExpeditionEvent(val expedition: Expedition) : Event() {
    override fun getHandlers(): HandlerList {
        return HANDLER_LIST
    }

    companion object {
        val HANDLER_LIST = HandlerList()
    }
}

class ExpeditionLootCrateEvent(val player: Player, val area: String, expedition: Expedition) : ExpeditionEvent(expedition)

class ExpeditionNPCExtractEvent(val player: Player, val npc: ExpeditionNPC, expedition: Expedition) : ExpeditionEvent(expedition)
class ExpeditionExtractEvent(val player: Player, expedition: Expedition, var returningLocation: Location) : ExpeditionEvent(expedition)

class ExpeditionKillEntityEvent(val player: Player, val mob: Entity, val area: String, expedition: Expedition) : ExpeditionEvent(expedition)