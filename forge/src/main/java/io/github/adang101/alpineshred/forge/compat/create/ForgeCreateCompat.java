package io.github.adang101.alpineshred.forge.compat.create;

import io.github.adang101.alpineshred.compat.create.CreateCompat;

/** Entry point of the Forge Create integration; call {@link #init()} only when Create is installed. */
public final class ForgeCreateCompat {
    private ForgeCreateCompat() {
    }

    public static void init() {
        ForgeKineticDrive.register();
        CreateCompat.install(new ForgeCreateBridge());
    }
}
