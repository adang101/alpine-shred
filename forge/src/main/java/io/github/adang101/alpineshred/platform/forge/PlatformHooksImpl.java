package io.github.adang101.alpineshred.platform.forge;

import io.github.adang101.alpineshred.forge.CuriosGearAccess;
import io.github.adang101.alpineshred.gear.GearAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;

import java.util.function.Consumer;

/** Forge implementation of {@link io.github.adang101.alpineshred.platform.PlatformHooks}. */
public final class PlatformHooksImpl {
    private PlatformHooksImpl() {
    }

    public static GearAccess gearAccess() {
        return CuriosGearAccess.INSTANCE;
    }

    /** {@code getPlayer()} is the joining player at login, or null after a reload (then: everyone). */
    public static void onDatapackSync(Consumer<ServerPlayer> action) {
        MinecraftForge.EVENT_BUS.addListener((OnDatapackSyncEvent event) -> {
            if (event.getPlayer() != null) {
                action.accept(event.getPlayer());
            } else {
                event.getPlayerList().getPlayers().forEach(action);
            }
        });
    }
}
