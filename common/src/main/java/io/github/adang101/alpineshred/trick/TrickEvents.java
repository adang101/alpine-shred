package io.github.adang101.alpineshred.trick;

import dev.architectury.event.Event;
import dev.architectury.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side events for other mods (design doc section 8, "Persistence and hooks"):
 * {@link #LANDED} fires for every scored landing, grind entry, bank and bail with the
 * {@link TrickResult} the rider's popup shows.
 */
public final class TrickEvents {
    @FunctionalInterface
    public interface Landed {
        void landed(ServerPlayer player, TrickResult result);
    }

    public static final Event<Landed> LANDED = EventFactory.createLoop();

    private TrickEvents() {
    }
}
