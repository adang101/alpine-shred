package io.github.adang101.alpineshred.client.platform.forge;

import dev.architectury.platform.forge.EventBuses;
import io.github.adang101.alpineshred.AlpineShred;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.client.event.EntityRenderersEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;

/** Forge implementation of {@link io.github.adang101.alpineshred.client.platform.ClientPlatformHooks}. */
public final class ClientPlatformHooksImpl {
    private static final List<BiFunction<RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>,
            EntityRendererProvider.Context, RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>>> FACTORIES =
            new CopyOnWriteArrayList<>();
    private static boolean listening;

    private ClientPlatformHooksImpl() {
    }

    public static synchronized void registerPlayerRenderLayer(BiFunction<RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>,
            EntityRendererProvider.Context, RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>> factory) {
        FACTORIES.add(factory);
        if (!listening) {
            listening = true;
            EventBuses.getModEventBus(AlpineShred.MOD_ID)
                    .orElseThrow(() -> new IllegalStateException("Alpine Shred mod event bus not registered"))
                    .addListener(ClientPlatformHooksImpl::addLayers);
        }
    }

    private static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer == null) continue;
            for (var factory : FACTORIES) {
                renderer.addLayer(factory.apply(renderer, event.getContext()));
            }
        }
    }
}
