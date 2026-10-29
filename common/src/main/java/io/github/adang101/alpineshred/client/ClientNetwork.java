package io.github.adang101.alpineshred.client;

import dev.architectury.networking.NetworkManager;
import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.gear.GearStatsRegistry;
import io.github.adang101.alpineshred.movement.TuningData;
import io.github.adang101.alpineshred.network.ModNetwork;
import io.github.adang101.alpineshred.network.SyncCodec;

import java.io.IOException;

/**
 * Client-side receivers for the server-to-client packets (design doc section 15). Registered from
 * {@link AlpineShredClient} only, so a dedicated server never loads this class.
 */
public final class ClientNetwork {
    private ClientNetwork() {
    }

    public static void register() {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, ModNetwork.CONFIG_SYNC, (buf, context) -> {
            int protocol = buf.readVarInt();
            byte[] payload = buf.readByteArray(ModNetwork.MAX_SYNC_BYTES);
            context.queue(() -> {
                SyncCodec.Settings settings = SyncCodec.Settings.DEFAULTS;
                if (protocol == SyncCodec.PROTOCOL_VERSION) {
                    try {
                        settings = SyncCodec.readConfig(SyncCodec.inflate(payload));
                    } catch (IOException e) {
                        AlpineShred.LOGGER.warn("Ignoring bad config_sync from server: {}", e.getMessage());
                    }
                }
                RideDriver.INSTANCE.onConfigSync(protocol, settings);
            });
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, ModNetwork.TUNING_SYNC, (buf, context) -> {
            byte[] payload = buf.readByteArray(ModNetwork.MAX_SYNC_BYTES);
            context.queue(() -> {
                try {
                    SyncCodec.Tuning tuning = SyncCodec.readTuning(SyncCodec.inflate(payload), AlpineShred.LOGGER::warn);
                    // In singleplayer these are the integrated server's own values coming back.
                    TuningData.setTuning(tuning.tuning());
                    TuningData.setSurfaces(tuning.surfaces());
                    TuningData.setBoxMaterials(tuning.boxMaterials());
                    if (!tuning.gear().isEmpty()) GearStatsRegistry.replace(tuning.gear());
                } catch (IOException e) {
                    AlpineShred.LOGGER.warn("Ignoring bad tuning_sync from server: {}", e.getMessage());
                }
            });
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, ModNetwork.RIDE_CORRECT, (buf, context) -> {
            buf.readDouble();
            buf.readDouble();
            buf.readDouble();
            buf.readByte();
            // The position arrives with the vanilla teleport packet; this stops the controller dead.
            context.queue(RideDriver.INSTANCE::onCorrection);
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, ModNetwork.DEBUG_RIDE, (buf, context) -> {
            boolean on = buf.readBoolean();
            context.queue(() -> RideDebugOverlay.setEnabled(on));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, ModNetwork.DEBUG_STATS, (buf, context) -> {
            int violations = buf.readVarInt();
            int corrections = buf.readVarInt();
            float speed = buf.readFloat();
            String reason = buf.readUtf(ModNetwork.MAX_REASON_CHARS);
            context.queue(() -> RideDebugOverlay.serverStats(violations, corrections, speed, reason));
        });
    }
}
