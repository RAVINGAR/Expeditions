package com.ravingarinc.expeditions.play.event;

import com.ravingarinc.expeditions.locale.type.Expedition;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public class ExpeditionKillEntityEvent extends ExpeditionEvent {
    private final Player player;
    private final Entity entity;

    private final String area;
    public ExpeditionKillEntityEvent(final Player player, final Entity entity, final String area, final Expedition expedition) {
        super(expedition);
        this.player = player;
        this.area = area;
        this.entity = entity;
    }

    public String getArea() {
        return area;
    }

    public Player getPlayer() {
        return player;
    }

    public Entity getEntity() {
        return entity;
    }
}
