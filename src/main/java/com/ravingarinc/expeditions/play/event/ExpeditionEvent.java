package com.ravingarinc.expeditions.play.event;

import com.ravingarinc.expeditions.locale.type.Expedition;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public abstract class ExpeditionEvent extends Event {
    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Expedition expedition;
    public ExpeditionEvent(Expedition expedition) {
        this.expedition = expedition;
    }

    public Expedition getExpedition() {
        return expedition;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
