package io.github.adang101.alpineshred.client.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

import java.util.IdentityHashMap;
import java.util.Map;
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

    /** Draws an item whose model is {@code builtin/entity}, in the item's model space (0 to 1). */
    @FunctionalInterface
    public interface BuiltinItemRenderer {
        void render(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay);
    }

    private static final Map<Item, BuiltinItemRenderer> BUILTIN = new IdentityHashMap<>();

    /**
     * {@code registerBuiltinItemRenderer} (section 3's hook list, section 22): {@code renderer} draws
     * {@code item} wherever the game draws it (held, dropped, in inventories, framed). One mixin into
     * the vanilla built-in renderer serves both loaders, so this needs no per-loader code.
     */
    public static void registerBuiltinItemRenderer(Item item, BuiltinItemRenderer renderer) {
        BUILTIN.put(item, renderer);
    }

    /** From the mixin: true when a registered renderer drew the stack. */
    public static boolean renderBuiltinItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers,
                                            int light, int overlay) {
        BuiltinItemRenderer renderer = BUILTIN.get(stack.getItem());
        if (renderer == null) return false;
        renderer.render(stack, context, pose, buffers, light, overlay);
        return true;
    }
}
