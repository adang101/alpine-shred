package io.github.adang101.alpineshred.platform.fabric;

import io.github.adang101.alpineshred.fabric.TrinketsGearAccess;
import io.github.adang101.alpineshred.gear.GearAccess;

/** Fabric implementation of {@link io.github.adang101.alpineshred.platform.PlatformHooks}. */
public final class PlatformHooksImpl {
    private PlatformHooksImpl() {
    }

    public static GearAccess gearAccess() {
        return TrinketsGearAccess.INSTANCE;
    }
}
