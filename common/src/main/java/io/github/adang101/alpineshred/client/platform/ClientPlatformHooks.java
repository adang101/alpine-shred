package io.github.adang101.alpineshred.client.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

import java.util.function.BiFunction;

/**
 * Client-only loader hooks (design doc section 3, rule 2). Implementations live in
 * {@code client.platform.forge} and {@code client.platform.fabric}.
 */
public final class ClientPlatformHooks {
    private ClientPlatformHooks() {
    }

    /**
     * Adds a render layer to both the default and slim player renderers: Forge's
     * {@code EntityRenderersEvent.AddLayers}, Fabric's {@code LivingEntityFeatureRendererRegistrationCallback}.
     */
    @ExpectPlatform
    public static void registerPlayerRenderLayer(BiFunction<RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>,
            EntityRendererProvider.Context, RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>> factory) {
        throw new AssertionError("ExpectPlatform not transformed");
    }
}
