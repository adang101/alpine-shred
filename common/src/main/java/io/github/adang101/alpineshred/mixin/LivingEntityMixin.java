package io.github.adang101.alpineshred.mixin;

import io.github.adang101.alpineshred.movement.Riders;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Strapped riders cannot sprint (design doc section 6). Filtering the argument of
 * {@code setSprinting} works on both sides and catches every path that starts a sprint
 * (double-tap, toggle-sprint, held key), before the sprint speed modifier is applied.
 */
@Mixin(value = LivingEntity.class, priority = 900)
public abstract class LivingEntityMixin {
    @ModifyVariable(method = "setSprinting", at = @At("HEAD"), argsOnly = true)
    private boolean alpineshred$noSprintWhileStrapped(boolean sprinting) {
        return sprinting && !Riders.isStrapped((LivingEntity) (Object) this);
    }
}
