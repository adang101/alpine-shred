package io.github.adang101.alpineshred.forge.compat.create;

import io.github.adang101.alpineshred.compat.create.CreateCompat;
import io.github.adang101.alpineshred.compat.create.WrenchActions;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/** Entry point of the Forge Create integration; call {@link #init()} only when Create is installed. */
public final class ForgeCreateCompat {
    private ForgeCreateCompat() {
    }

    public static void init() {
        CreateCompat.install(new ForgeCreateBridge());
        MinecraftForge.EVENT_BUS.addListener(ForgeCreateCompat::onRightClickBlock);
    }

    /** Create's wrench on our blocks (README 80): translate the event, cancel it when {@link WrenchActions} handled the click. */
    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        InteractionResult result = WrenchActions.onClick(event.getLevel(), event.getPos(), event.getEntity(), event.getItemStack());
        if (result != InteractionResult.PASS) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }
}
