package io.github.adang101.alpineshred.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.adang101.alpineshred.client.SnowGearLayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ski poles in a player's hands are drawn by {@link SnowGearLayer} as a pair; skip the held sprite. */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void alpineshred$poles(LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm,
                                   PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (SnowGearLayer.drawsPoles(entity, stack)) ci.cancel();
    }
}
