package io.github.adang101.alpineshred.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;
import dev.architectury.platform.Platform;
import io.github.adang101.alpineshred.gear.GearAccess;

/**
 * Loader hooks (design doc section 3, rule 2). Implementations live in
 * {@code platform.forge.PlatformHooksImpl} and {@code platform.fabric.PlatformHooksImpl}.
 *
 * <p>{@code isModLoaded} and {@code configDir} from the doc's hook list are already provided by
 * Architectury's {@link Platform} and are used directly. Client hooks are in
 * {@code client.platform.ClientPlatformHooks} so a dedicated server never loads them. The
 * {@code onDatapackSync} and {@code registerBuiltinItemRenderer} hooks arrive with phases 2 and 6.
 */
public final class PlatformHooks {
    private PlatformHooks() {
    }

    /** Curios on Forge, Trinkets on Fabric. */
    @ExpectPlatform
    public static GearAccess gearAccess() {
        throw new AssertionError("ExpectPlatform not transformed");
    }

    public static boolean isCreatePresent() {
        return Platform.isModLoaded("create");
    }
}
