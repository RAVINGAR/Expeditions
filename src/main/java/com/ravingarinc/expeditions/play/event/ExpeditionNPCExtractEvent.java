package com.ravingarinc.expeditions.play.event;

import com.ravingarinc.expeditions.integration.npc.ExpeditionNPC;
import com.ravingarinc.expeditions.locale.type.Expedition;
import org.bukkit.entity.Player;

public class ExpeditionNPCExtractEvent extends ExpeditionEvent {
    private final ExpeditionNPC npc;
    private final Player player;
    public ExpeditionNPCExtractEvent(final Player player, final ExpeditionNPC npc, final Expedition expedition) {
        super(expedition);
        this.player = player;
        this.npc = npc;
    }

    public ExpeditionNPC getNpc() {
        return npc;
    }

    public Player getPlayer() {
        return player;
    }
}
