package io.github.adang101.alpineshred.lift;

import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import io.github.adang101.alpineshred.config.AlpineConfig;
import io.github.adang101.alpineshred.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Sends lift lines to clients (design doc section 15). Each player has a set of the line ids they
 * were sent.
 * <ul>
 * <li>Every {@link #SCAN_INTERVAL} ticks a line with any node within {@code lift.syncRadius} blocks of
 * the player, measured horizontally (a lift is long and the radius is about how far along the ground
 * a player can see, not how high up the line is), is sent in full and added to the set.</li>
 * <li>Every {@link #RESEND_INTERVAL} ticks each line in the set whose nodes are still in range is sent
 * again, to correct drift.</li>
 * <li>When {@link LiftManager} reports a speed target or flag change, everyone with the line in their
 * set gets it. Speed changes during a ramp are deterministic and need no packet.</li>
 * <li>On removal, everyone with the line gets a {@code removed} sync and drops it.</li>
 * </ul>
 * A line that leaves a player radius stays in their set. Sets clear on logout and dimension change.
 * Server thread only. The sender and the player source can be replaced so tests need no connections.
 */
public final class LiftSync implements LiftManager.Listener {
    public static final int SCAN_INTERVAL = 40;
    public static final int RESEND_INTERVAL = 100;

    /** Where syncs go. */
    @FunctionalInterface
    public interface Sender {
        void send(ServerPlayer player, LiftLineSync sync);
    }

    private static final LiftSync INSTANCE = new LiftSync();
    private static final Map<UUID, Set<UUID>> SYNCED = new HashMap<>();
    private static Sender sender = ModNetwork::sendLiftLineSync;
    private static Function<ServerLevel, List<ServerPlayer>> players = level -> new ArrayList<>(level.players());

    private LiftSync() {
    }

    public static void register() {
        LiftManager.setListener(INSTANCE);
        // Registered after LiftManager's own post-tick, so the state sent is this tick's.
        TickEvent.SERVER_LEVEL_POST.register(level -> pass(level, level.getGameTime()));
        PlayerEvent.PLAYER_QUIT.register(player -> forget(player.getUUID()));
        PlayerEvent.CHANGE_DIMENSION.register((player, oldLevel, newLevel) -> forget(player.getUUID()));
    }

    // ---- test hooks ----

    public static void setSender(Sender s) {
        sender = s;
    }

    public static void setPlayers(Function<ServerLevel, List<ServerPlayer>> source) {
        players = source;
    }

    /** Restores the real sender and player source and clears every set. */
    public static void reset() {
        sender = ModNetwork::sendLiftLineSync;
        players = level -> new ArrayList<>(level.players());
        SYNCED.clear();
    }

    public static Set<UUID> synced(UUID player) {
        Set<UUID> set = SYNCED.get(player);
        return set == null ? Set.of() : Collections.unmodifiableSet(set);
    }

    public static void forget(UUID player) {
        SYNCED.remove(player);
    }

    // ---- the periodic passes ----

    /** Whether any node of {@code line} is within {@code radius} blocks of the point, horizontally. */
    public static boolean inRange(LiftLine line, double x, double z, double radius) {
        double r2 = radius * radius;
        for (BlockPos p : line.nodePositions()) {
            double dx = p.getX() + 0.5 - x, dz = p.getZ() + 0.5 - z;
            if (dx * dx + dz * dz <= r2) return true;
        }
        return false;
    }

    /** The work of one level tick at {@code gameTime}: the 100-tick resend, then the 40-tick scan. */
    public static void pass(ServerLevel level, long gameTime) {
        boolean scan = gameTime % SCAN_INTERVAL == 0;
        boolean resend = gameTime % RESEND_INTERVAL == 0;
        if (!scan && !resend) return;
        List<LiftLine> lines = LiftManager.snapshot(level);
        if (lines.isEmpty()) return;
        double radius = AlpineConfig.LIFT_SYNC_RADIUS.get();
        for (ServerPlayer player : players.apply(level)) {
            Set<UUID> set = SYNCED.computeIfAbsent(player.getUUID(), k -> new HashSet<>());
            double x = player.getX(), z = player.getZ();
            if (resend) {
                for (LiftLine line : lines) {
                    if (set.contains(line.id) && inRange(line, x, z, radius)) {
                        sender.send(player, LiftLineSync.of(line, gameTime));
                    }
                }
            }
            if (scan) {
                for (LiftLine line : lines) {
                    if (!set.contains(line.id) && inRange(line, x, z, radius)) {
                        sender.send(player, LiftLineSync.of(line, gameTime));
                        set.add(line.id);
                    }
                }
            }
        }
    }

    // ---- LiftManager.Listener ----

    @Override
    public void changed(ServerLevel level, LiftLine line) {
        LiftLineSync sync = null;
        for (ServerPlayer player : players.apply(level)) {
            Set<UUID> set = SYNCED.get(player.getUUID());
            if (set != null && set.contains(line.id)) {
                if (sync == null) sync = LiftLineSync.of(line, level.getGameTime());
                sender.send(player, sync);
            }
        }
    }

    @Override
    public void removed(ServerLevel level, UUID id) {
        LiftLineSync sync = LiftLineSync.removed(id);
        for (ServerPlayer player : players.apply(level)) {
            Set<UUID> set = SYNCED.get(player.getUUID());
            if (set != null && set.remove(id)) sender.send(player, sync);
        }
    }
}
