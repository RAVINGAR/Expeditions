package com.ravingarinc.expeditions.play.event;

import com.ravingarinc.expeditions.locale.type.Expedition;
import org.bukkit.entity.Player;

public class ExpeditionLootCrateEvent extends ExpeditionEvent {
    private final String area;
    private final Player player;
    public ExpeditionLootCrateEvent(Player player, String area, Expedition expedition) {
        super(expedition);
        this.area = area;
        this.player = player;
    }

    public String getArea() {
        return area;
    }

    public Player getPlayer() {
        return player;
    }
}
