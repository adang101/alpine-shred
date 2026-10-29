package io.github.adang101.alpineshred.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.adang101.alpineshred.client.RideDriver;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * First person while riding (design doc section 9, "First person"): the bare arm is hidden, since
 * the hands are down at the rider's sides. Held items, poles among them, still draw.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Inject(method = "renderPlayerArm", at = @At("HEAD"), cancellable = true)
    private void alpineshred$hideBareArmWhileRiding(PoseStack poseStack, MultiBufferSource buffers, int light,
                                                     float equippedProgress, float swingProgress, HumanoidArm arm,
                                                     CallbackInfo ci) {
        if (RideDriver.INSTANCE.sliding()) ci.cancel();
    }
}
