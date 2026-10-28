package io.github.adang101.alpineshred.mixin.client;

import io.github.adang101.alpineshred.movement.RideStateHolder;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Auto-jump presses jump for a tick when the player walks at a step; while riding, a one-tick
 * jump is a pop, so auto-jump stays off (the raised step height climbs the block instead).
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Inject(method = "updateAutoJump", at = @At("HEAD"), cancellable = true)
    private void alpineshred$noAutoJumpWhileRiding(float dx, float dz, CallbackInfo ci) {
        if (((RideStateHolder) (Object) this).alpineshred$isControlled()) ci.cancel();
    }
}
