package com.ravingarinc.expeditions.play.event;

import com.ravingarinc.expeditions.locale.type.Expedition;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class ExpeditionExtractEvent extends ExpeditionEvent {
    private final Player player;
    private Location returningLocation;
    private final String area;
    public ExpeditionExtractEvent(final Player player, final String area, final Expedition expedition, final Location returningLocation) {
        super(expedition);
        this.player = player;
        this.area = area;
        this.returningLocation = returningLocation;
    }

    public String getArea() {
        return area;
    }

    public void setReturningLocation(Location returningLocation) {
        this.returningLocation = returningLocation;
    }

    public Location getReturningLocation() {
        return returningLocation;
    }

    public Player getPlayer() {
        return player;
    }
}
