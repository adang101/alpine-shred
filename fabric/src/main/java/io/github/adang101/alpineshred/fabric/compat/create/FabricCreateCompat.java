package io.github.adang101.alpineshred.fabric.compat.create;

import io.github.adang101.alpineshred.compat.create.CreateCompat;
import io.github.adang101.alpineshred.compat.create.WrenchActions;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

/** Entry point of the Fabric Create integration; call {@link #init()} only when Create is installed. */
public final class FabricCreateCompat {
    private FabricCreateCompat() {
    }

    public static void init() {
        CreateCompat.install(new FabricCreateBridge());
        // Create's wrench on our blocks (README 80): a non-PASS result ends the click.
        UseBlockCallback.EVENT.register((player, level, hand, hit) ->
                WrenchActions.onClick(level, hit.getBlockPos(), player, player.getItemInHand(hand)));
    }
}
