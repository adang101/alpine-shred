package io.github.adang101.alpineshred.network;

import dev.architectury.networking.NetworkManager;
import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.movement.StrapController;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Packets (design doc section 15). Ids are {@code alpineshred:c2s/<name>} and
 * {@code alpineshred:s2c/<name>}. Every receiver queues onto the main thread before touching game
 * state. Phase 1 needs only {@code c2s/strap_toggle}; strap feedback uses vanilla action-bar
 * messages and the rider state travels as synced entity data.
 */
public final class ModNetwork {
    public static final ResourceLocation STRAP_TOGGLE = AlpineShred.id("c2s/strap_toggle");

    /** Strap toggles are "on key"; 4 per second is far above what a key press produces. */
    private static final PacketRateLimiter STRAP_LIMIT = new PacketRateLimiter(4, 4);

    private ModNetwork() {
    }

    public static void registerCommon() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, STRAP_TOGGLE, (buf, context) -> {
            boolean wanted = buf.readBoolean();
            context.queue(() -> {
                if (!(context.getPlayer() instanceof ServerPlayer player)) return;
                if (!STRAP_LIMIT.tryAcquire(player)) return;
                StrapController.handleToggle(player, wanted);
            });
        });
    }

    /** Client side: ask the server to strap in ({@code true}) or out. */
    public static void sendStrapToggle(boolean wanted) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBoolean(wanted);
        NetworkManager.sendToServer(STRAP_TOGGLE, buf);
    }
}
