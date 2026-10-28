package io.github.adang101.alpineshred.platform.forge;

import io.github.adang101.alpineshred.forge.CuriosGearAccess;
import io.github.adang101.alpineshred.gear.GearAccess;

/** Forge implementation of {@link io.github.adang101.alpineshred.platform.PlatformHooks}. */
public final class PlatformHooksImpl {
    private PlatformHooksImpl() {
    }

    public static GearAccess gearAccess() {
        return CuriosGearAccess.INSTANCE;
    }
}
