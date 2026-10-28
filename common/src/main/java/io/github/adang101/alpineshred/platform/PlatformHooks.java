package io.github.adang101.alpineshred.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;
import dev.architectury.platform.Platform;
import io.github.adang101.alpineshred.gear.GearAccess;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

/**
 * Loader hooks (design doc section 3, rule 2). Implementations live in
 * {@code platform.forge.PlatformHooksImpl} and {@code platform.fabric.PlatformHooksImpl}.
 *
 * <p>{@code isModLoaded} and {@code configDir} from the doc's hook list are already provided by
 * Architectury's {@link Platform} and are used directly. Client hooks are in
 * {@code client.platform.ClientPlatformHooks} so a dedicated server never loads them. The
 * {@code registerBuiltinItemRenderer} hook arrives with phase 6.
 */
public final class PlatformHooks {
    private PlatformHooks() {
    }

    /** Curios on Forge, Trinkets on Fabric. */
    @ExpectPlatform
    public static GearAccess gearAccess() {
        throw new AssertionError("ExpectPlatform not transformed");
    }

    /**
     * Runs {@code action} for each player that receives the server's data packs: at login, and for
     * every online player after a datapack reload (section 15, "Sync lifecycle"). Forge's
     * {@code OnDatapackSyncEvent}, Fabric's {@code SYNC_DATA_PACK_CONTENTS}.
     */
    @ExpectPlatform
    public static void onDatapackSync(Consumer<ServerPlayer> action) {
        throw new AssertionError("ExpectPlatform not transformed");
    }

    public static boolean isCreatePresent() {
        return Platform.isModLoaded("create");
    }
}
