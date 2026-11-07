package io.github.adang101.alpineshred.fabric.compat.create;

import io.github.adang101.alpineshred.compat.create.CreateCompat;

/** Entry point of the Fabric Create integration; call {@link #init()} only when Create is installed. */
public final class FabricCreateCompat {
    private FabricCreateCompat() {
    }

    public static void init() {
        FabricKineticDrive.register();
        CreateCompat.install(new FabricCreateBridge());
    }
}
