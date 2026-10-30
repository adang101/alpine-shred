package io.github.adang101.alpineshred.design;

import dev.architectury.event.Event;
import dev.architectury.event.EventFactory;
import dev.architectury.event.EventResult;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Server events for other mods (design doc section 22, "Server controls and moderation"):
 * {@link #BEFORE_SAVE} can refuse a save that passed validation (the player sees "not allowed" and
 * pays nothing); {@link #SAVED} follows every save, to log, screenshot or review designs.
 */
public final class DesignEvents {
    @FunctionalInterface
    public interface BeforeSave {
        /** {@link EventResult#interruptFalse()} refuses the save; {@link EventResult#pass()} lets it through. */
        EventResult beforeSave(ServerPlayer player, ItemStack gear, Design design);
    }

    @FunctionalInterface
    public interface Saved {
        void saved(ServerPlayer player, ItemStack gear, Design design);
    }

    public static final Event<BeforeSave> BEFORE_SAVE = EventFactory.createEventResult();
    public static final Event<Saved> SAVED = EventFactory.createLoop();

    private DesignEvents() {
    }
}
