package io.github.adang101.alpineshred.platform.fabric;

import io.github.adang101.alpineshred.fabric.TrinketsGearAccess;
import io.github.adang101.alpineshred.gear.GearAccess;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

/** Fabric implementation of {@link io.github.adang101.alpineshred.platform.PlatformHooks}. */
public final class PlatformHooksImpl {
    private PlatformHooksImpl() {
    }

    public static GearAccess gearAccess() {
        return TrinketsGearAccess.INSTANCE;
    }

    /** Fires once per player, both at login ({@code joined}) and after {@code /reload}. */
    public static void onDatapackSync(Consumer<ServerPlayer> action) {
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) -> action.accept(player));
    }
}
