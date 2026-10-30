package io.github.adang101.alpineshred.client.design;

import dev.architectury.event.events.client.ClientLifecycleEvent;
import dev.architectury.registry.ReloadListenerRegistry;
import dev.architectury.registry.menu.MenuRegistry;
import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.client.platform.ClientPlatformHooks;
import io.github.adang101.alpineshred.design.Design;
import io.github.adang101.alpineshred.design.DesignCodec;
import io.github.adang101.alpineshred.design.DesignStatus;
import io.github.adang101.alpineshred.network.ModNetwork;
import io.github.adang101.alpineshred.registry.ModItems;
import io.github.adang101.alpineshred.registry.ModMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/**
 * Client wiring for designs (design doc section 22): the built-in item renderer for every board
 * and pair of skis, the Paint Bench screen, the texture cache's reload, and the save round trip.
 */
public final class DesignClient {
    /** The last save's result, for the editor's status line (and the smoke test). */
    private static volatile DesignStatus lastResult;
    private static volatile int results;

    private DesignClient() {
    }

    public static void register() {
        ClientLifecycleEvent.CLIENT_SETUP.register(minecraft -> {
            for (var item : ModItems.boardsAndSkis()) {
                ClientPlatformHooks.registerBuiltinItemRenderer(item.get(),
                        (stack, context, pose, buffers, light, overlay) -> DesignedGearRenderer.render(stack, pose, buffers, light, overlay));
            }
            MenuRegistry.registerScreenFactory(ModMenus.PAINT_BENCH.get(), PaintBenchScreen::new);
        });
        ReloadListenerRegistry.register(PackType.CLIENT_RESOURCES, (ResourceManagerReloadListener) manager -> DesignTextureCache.clear(),
                AlpineShred.id("design_textures"));
    }

    /** Sends the working copy to the server for the gear in the open bench ({@code containerId}). */
    public static void save(int containerId, Design design) {
        ModNetwork.sendDesignSave(containerId, false, design.kind(), DesignCodec.topZ(design), DesignCodec.baseZ(design),
                design.edge(), design.pair(), design.name());
    }

    /** Reset: asks the server to remove the design (free). */
    public static void reset(int containerId, Design design) {
        ModNetwork.sendDesignSave(containerId, true, design.kind(), new byte[0], new byte[0], 0, Design.Pair.SAME, "");
    }

    /** {@code s2c/design_result}. */
    public static void onResult(DesignStatus status) {
        lastResult = status;
        results++;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof DesignEditorScreen editor) {
            editor.onResult(status);
        } else if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable(status.translationKey()), true);
        }
    }

    public static DesignStatus lastResult() {
        return lastResult;
    }

    /** Results received so far (the smoke test waits for the count to move). */
    public static int results() {
        return results;
    }
}
