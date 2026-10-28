package io.github.adang101.alpineshred.mixin;

import io.github.adang101.alpineshred.movement.RideHooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.PowderSnowBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Riders above the float speed are carried by powder snow (design doc section 7, "Powder"). */
@Mixin(PowderSnowBlock.class)
public abstract class PowderSnowBlockMixin {
    @Inject(method = "canEntityWalkOnPowderSnow", at = @At("HEAD"), cancellable = true)
    private static void alpineshred$floatAtSpeed(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Player player && RideHooks.floatsOnPowder(player)) cir.setReturnValue(true);
    }
}
