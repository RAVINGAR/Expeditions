package com.ravingarinc.expeditions.play.event;

import com.ravingarinc.expeditions.locale.type.Expedition;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class ExpeditionExtractEvent extends ExpeditionEvent {
    private final Player player;
    private Location returningLocation;
    public ExpeditionExtractEvent(final Player player, final Expedition expedition, final Location returningLocation) {
        super(expedition);
        this.player = player;
        this.returningLocation = returningLocation;
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
