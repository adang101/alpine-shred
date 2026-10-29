package io.github.adang101.alpineshred.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.adang101.alpineshred.client.park.ParkOverlay;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The park overlays (design doc section 10, "Placement and tools"): the rail ghost preview and the
 * wrench's path overlay, drawn after the world with its depth buffer, on both loaders.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void alpineshred$parkOverlay(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline,
                                         Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projection,
                                         CallbackInfo ci) {
        ParkOverlay.render(poseStack, camera);
    }
}
