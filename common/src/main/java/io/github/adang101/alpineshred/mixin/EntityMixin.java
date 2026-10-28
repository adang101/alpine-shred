package io.github.adang101.alpineshred.mixin;

import io.github.adang101.alpineshred.movement.RideHooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Entity-level hooks for riders (design doc section 7, mixin inventory):
 * <ul>
 *   <li>Step height: {@code maxUpStep()} is raised for controller moves. Forge's
 *       {@code IForgeEntity.getStepHeight()} reads {@code maxUpStep()}, so this one hook serves both
 *       loaders.</li>
 *   <li>Descending: sneaking (braking) would make powder snow non-solid again; a floating rider
 *       keeps floating while braking.</li>
 * </ul>
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "maxUpStep", at = @At("RETURN"), cancellable = true)
    private void alpineshred$rideStep(CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof Player player) {
            float step = RideHooks.stepHeight(player);
            if (step > cir.getReturnValueF()) cir.setReturnValue(step);
        }
    }

    @Inject(method = "isDescending", at = @At("HEAD"), cancellable = true)
    private void alpineshred$keepFloatingWhileBraking(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player && RideHooks.floatsOnPowder(player)) cir.setReturnValue(false);
    }
}
