package io.github.adang101.alpineshred.client.platform.fabric;

import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

import java.util.function.BiFunction;

/** Fabric implementation of {@link io.github.adang101.alpineshred.client.platform.ClientPlatformHooks}. */
public final class ClientPlatformHooksImpl {
    private ClientPlatformHooksImpl() {
    }

    public static void registerPlayerRenderLayer(BiFunction<RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>,
            EntityRendererProvider.Context, RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>> factory) {
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
            if (entityRenderer instanceof PlayerRenderer playerRenderer) {
                helper.register(factory.apply(playerRenderer, context));
            }
        });
    }
}
