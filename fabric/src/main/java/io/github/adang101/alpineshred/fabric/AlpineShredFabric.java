package io.github.adang101.alpineshred.fabric;

import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.fabric.compat.create.FabricCreateCompat;
import io.github.adang101.alpineshred.platform.PlatformHooks;
import net.fabricmc.api.ModInitializer;

/** Fabric entry point. Holds no logic beyond wiring (section 3, rule 1). */
public final class AlpineShredFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        AlpineShred.init();
        if (PlatformHooks.isCreatePresent()) FabricCreateCompat.init();
    }
}
