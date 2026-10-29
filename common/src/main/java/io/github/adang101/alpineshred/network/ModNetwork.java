package io.github.adang101.alpineshred.network;

import dev.architectury.networking.NetworkManager;
import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.config.AlpineConfig;
import io.github.adang101.alpineshred.gear.GearStatsRegistry;
import io.github.adang101.alpineshred.movement.RideServer;
import io.github.adang101.alpineshred.movement.RideState;
import io.github.adang101.alpineshred.movement.StrapCheck;
import io.github.adang101.alpineshred.movement.StrapController;
import io.github.adang101.alpineshred.movement.TuningData;
import io.github.adang101.alpineshred.platform.PlatformHooks;
import io.github.adang101.alpineshred.trick.AirReport;
import io.github.adang101.alpineshred.trick.GrindReport;
import io.github.adang101.alpineshred.trick.TrickCodec;
import io.github.adang101.alpineshred.trick.TrickResult;
import io.github.adang101.alpineshred.trick.TrickServer;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Packets (design doc section 15). Ids are {@code alpineshred:c2s/<name>} and
 * {@code alpineshred:s2c/<name>}. Every receiver queues onto the main thread before touching game
 * state. This class registers the server-side receivers and holds the senders; the client's
 * receivers register from {@code client.ClientNetwork}, so a dedicated server never loads them.
 *
 * <p>Rider state itself is not a packet to observers: the server writes accepted
 * {@code ride_state} claims into synced entity data, which vanilla delivers to every tracking client.
 */
public final class ModNetwork {
    public static final ResourceLocation STRAP_TOGGLE = AlpineShred.id("c2s/strap_toggle");
    public static final ResourceLocation RIDE_STATE = AlpineShred.id("c2s/ride_state");
    public static final ResourceLocation RIDE_ACTION = AlpineShred.id("c2s/ride_action");
    public static final ResourceLocation AIR_REPORT = AlpineShred.id("c2s/air_report");
    public static final ResourceLocation GRIND_REPORT = AlpineShred.id("c2s/grind_report");
    public static final ResourceLocation RIDE_CORRECT = AlpineShred.id("s2c/ride_correct");
    /** A landing, grind entry, bank or bail (section 8); the rider's entity id first. */
    public static final ResourceLocation TRICK_RESULT = AlpineShred.id("s2c/trick_result");
    public static final ResourceLocation TUNING_SYNC = AlpineShred.id("s2c/tuning_sync");
    public static final ResourceLocation CONFIG_SYNC = AlpineShred.id("s2c/config_sync");
    /** {@code /alpineshred debug ride}: overlay on or off. */
    public static final ResourceLocation DEBUG_RIDE = AlpineShred.id("s2c/debug_ride");
    /** Validator read-out for the debug overlay, every {@value RideServer#DEBUG_INTERVAL_TICKS} ticks while it is on. */
    public static final ResourceLocation DEBUG_STATS = AlpineShred.id("s2c/debug_stats");

    public static final int MAX_SYNC_BYTES = 1 << 20;
    public static final int MAX_REASON_CHARS = 96;

    /** Strap toggles are "on key"; 4 per second is far above what a key press produces. */
    private static final PacketRateLimiter STRAP_LIMIT = new PacketRateLimiter(4, 4);
    private static final PacketRateLimiter STATE_LIMIT = new PacketRateLimiter(10, 10);
    private static final PacketRateLimiter ACTION_LIMIT = new PacketRateLimiter(20, 20);
    /** Reports are once per landing or grind end: a few per second at the very most. */
    private static final PacketRateLimiter REPORT_LIMIT = new PacketRateLimiter(5, 8);

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
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, RIDE_STATE, (buf, context) -> {
            int bits = buf.readInt();
            context.queue(() -> {
                if (!(context.getPlayer() instanceof ServerPlayer player)) return;
                if (!STATE_LIMIT.tryAcquire(player)) {
                    if (STATE_LIMIT.dropShouldCount(player)) RideServer.flagDroppedPacket(player, "ride_state");
                    return;
                }
                RideServer.handleState(player, bits);
            });
        });
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, RIDE_ACTION, (buf, context) -> {
            RideAction action = RideAction.byId(buf.readByte());
            int value = buf.readVarInt();
            context.queue(() -> {
                if (!(context.getPlayer() instanceof ServerPlayer player) || action == null) return;
                if (!ACTION_LIMIT.tryAcquire(player)) {
                    if (ACTION_LIMIT.dropShouldCount(player)) RideServer.flagDroppedPacket(player, "ride_action");
                    return;
                }
                RideServer.handleAction(player, action, value);
            });
        });

        NetworkManager.registerReceiver(NetworkManager.Side.C2S, AIR_REPORT, (buf, context) -> {
            AirReport report = read(buf, TrickCodec::readAir);
            context.queue(() -> {
                if (!(context.getPlayer() instanceof ServerPlayer player)) return;
                if (report == null) {
                    RideServer.flagReport(player, "unreadable air report");
                    return;
                }
                if (!REPORT_LIMIT.tryAcquire(player)) {
                    if (REPORT_LIMIT.dropShouldCount(player)) RideServer.flagDroppedPacket(player, "air_report");
                    return;
                }
                TrickServer.handleAir(player, report);
            });
        });
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, GRIND_REPORT, (buf, context) -> {
            GrindReport report = read(buf, TrickCodec::readGrind);
            context.queue(() -> {
                if (!(context.getPlayer() instanceof ServerPlayer player)) return;
                if (report == null) {
                    RideServer.flagReport(player, "unreadable grind report");
                    return;
                }
                if (!REPORT_LIMIT.tryAcquire(player)) {
                    if (REPORT_LIMIT.dropShouldCount(player)) RideServer.flagDroppedPacket(player, "grind_report");
                    return;
                }
                TrickServer.handleGrind(player, report);
            });
        });

        // Login and datapack reload: config first, then tuning (section 15, "Sync lifecycle").
        PlatformHooks.onDatapackSync(player -> {
            sendConfigSync(player);
            sendTuningSync(player);
        });
    }

    private static FriendlyByteBuf buffer() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    @FunctionalInterface
    public interface Reader<T> {
        T read(java.io.DataInput in) throws java.io.IOException;
    }

    @FunctionalInterface
    private interface Writer {
        void write(java.io.DataOutput out) throws java.io.IOException;
    }

    /** Reads a {@link TrickCodec} payload, or null when it is malformed. */
    @org.jetbrains.annotations.Nullable
    public static <T> T read(FriendlyByteBuf buf, Reader<T> reader) {
        try {
            return reader.read(new io.netty.buffer.ByteBufInputStream(buf));
        } catch (java.io.IOException | RuntimeException e) {
            return null;
        }
    }

    private static FriendlyByteBuf write(Writer writer) {
        FriendlyByteBuf buf = buffer();
        try {
            writer.write(new io.netty.buffer.ByteBufOutputStream(buf));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        return buf;
    }

    /** Client side: what the last air session was (once per landing). */
    public static void sendAirReport(AirReport report) {
        NetworkManager.sendToServer(AIR_REPORT, write(out -> TrickCodec.writeAir(out, report)));
    }

    /** Client side: what the last rail or box was (once per grind end). */
    public static void sendGrindReport(GrindReport report) {
        NetworkManager.sendToServer(GRIND_REPORT, write(out -> TrickCodec.writeGrind(out, report)));
    }

    public static void sendTrickResult(ServerPlayer to, ServerPlayer rider, TrickResult result) {
        FriendlyByteBuf buf = write(out -> TrickCodec.writeResult(out, result));
        FriendlyByteBuf framed = buffer();
        framed.writeVarInt(rider.getId());
        framed.writeBytes(buf);
        NetworkManager.sendToPlayer(to, TRICK_RESULT, framed);
    }

    // ---- client to server -------------------------------------------------------------------------

    /** Client side: ask the server to strap in ({@code true}) or out. */
    public static void sendStrapToggle(boolean wanted) {
        FriendlyByteBuf buf = buffer();
        buf.writeBoolean(wanted);
        NetworkManager.sendToServer(STRAP_TOGGLE, buf);
    }

    public static void sendRideState(int bits) {
        FriendlyByteBuf buf = buffer();
        buf.writeInt(bits);
        NetworkManager.sendToServer(RIDE_STATE, buf);
    }

    public static void sendRideAction(RideAction action, int value) {
        FriendlyByteBuf buf = buffer();
        buf.writeByte(action.id());
        buf.writeVarInt(value);
        NetworkManager.sendToServer(RIDE_ACTION, buf);
    }

    // ---- server to client -------------------------------------------------------------------------

    public static void sendRideCorrect(ServerPlayer player, double x, double y, double z, RideState state) {
        FriendlyByteBuf buf = buffer();
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeByte(state.id());
        NetworkManager.sendToPlayer(player, RIDE_CORRECT, buf);
    }

    public static void sendTuningSync(ServerPlayer player) {
        byte[] payload = SyncCodec.deflate(SyncCodec.tuningPayload(TuningData.tuning(), TuningData.surfaces(), GearStatsRegistry.all(), TuningData.boxMaterials()));
        FriendlyByteBuf buf = buffer();
        buf.writeByteArray(payload);
        NetworkManager.sendToPlayer(player, TUNING_SYNC, buf);
    }

    /** The server-authoritative settings the client controller needs, as this server sees them. */
    public static SyncCodec.Settings serverSettings() {
        return new SyncCodec.Settings(StrapCheck.ridingEnabled(), AlpineConfig.MOVEMENT_MAX_SPEED_MUL.value(),
                AlpineConfig.MOVEMENT_SCRAPE_ENABLED.on());
    }

    public static void sendConfigSync(ServerPlayer player) {
        FriendlyByteBuf buf = buffer();
        buf.writeVarInt(SyncCodec.PROTOCOL_VERSION);
        buf.writeByteArray(SyncCodec.deflate(SyncCodec.configPayload(serverSettings())));
        NetworkManager.sendToPlayer(player, CONFIG_SYNC, buf);
    }

    /** After {@code /alpineshred config reload} or {@code tuning reload}. */
    public static void resyncAll(MinecraftServer server, boolean config, boolean tuning) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (config) sendConfigSync(player);
            if (tuning) sendTuningSync(player);
        }
    }

    public static void sendDebugToggle(ServerPlayer player, boolean on) {
        FriendlyByteBuf buf = buffer();
        buf.writeBoolean(on);
        NetworkManager.sendToPlayer(player, DEBUG_RIDE, buf);
    }

    public static void sendDebugStats(ServerPlayer player, int violations, int corrections, double observedSpeed, String reason) {
        FriendlyByteBuf buf = buffer();
        buf.writeVarInt(violations);
        buf.writeVarInt(corrections);
        buf.writeFloat((float) observedSpeed);
        buf.writeUtf(reason.length() > MAX_REASON_CHARS ? reason.substring(0, MAX_REASON_CHARS) : reason, MAX_REASON_CHARS);
        NetworkManager.sendToPlayer(player, DEBUG_STATS, buf);
    }
}
