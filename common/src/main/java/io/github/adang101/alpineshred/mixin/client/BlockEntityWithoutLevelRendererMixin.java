package io.github.adang101.alpineshred.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.adang101.alpineshred.client.platform.ClientPlatformHooks;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The built-in item renderer hook (design doc sections 3 and 22): items whose model is
 * {@code builtin/entity} end up here on both loaders (Forge's default item extension and Fabric's
 * unregistered path both call the vanilla renderer), so one injection serves
 * {@link ClientPlatformHooks#registerBuiltinItemRenderer}.
 */
@Mixin(BlockEntityWithoutLevelRenderer.class)
abstract class BlockEntityWithoutLevelRendererMixin {
    @Inject(method = "renderByItem", at = @At("HEAD"), cancellable = true)
    private void alpineshred$builtinItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers,
                                         int light, int overlay, CallbackInfo ci) {
        if (ClientPlatformHooks.renderBuiltinItem(stack, context, pose, buffers, light, overlay)) ci.cancel();
    }
}
