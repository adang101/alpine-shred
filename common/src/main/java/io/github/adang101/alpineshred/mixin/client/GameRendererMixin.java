package io.github.adang101.alpineshred.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.adang101.alpineshred.client.feel.RideEffects;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Camera roll and speed field of view while riding (design doc section 9, mixin inventory "Camera
 * roll and FOV"). The roll goes in where vanilla applies its hurt tilt, before the camera's own
 * rotation, so it rolls the view about the line of sight (and the held item with it). Also the
 * hook for the riding screen effects, under the GUI.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "bobHurt", at = @At("HEAD"))
    private void alpineshred$rideRoll(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        float roll = RideEffects.roll(partialTick);
        if (roll != 0) poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
    }

    /** Screen effects go under the whole vanilla GUI (hotbar, crosshair, chat), on both loaders. */
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;render(Lnet/minecraft/client/gui/GuiGraphics;F)V"))
    private void alpineshred$screenEffects(float partialTick, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        RideEffects.renderScreenEffects(partialTick);
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void alpineshred$speedFov(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Double> cir) {
        if (!useFovSetting) return;
        float add = RideEffects.fov(partialTick);
        if (add != 0) cir.setReturnValue(cir.getReturnValue() + add);
    }
}
