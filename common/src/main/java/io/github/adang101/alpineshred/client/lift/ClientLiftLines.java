package io.github.adang101.alpineshred.client.lift;

import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import io.github.adang101.alpineshred.lift.ChairEntity;
import io.github.adang101.alpineshred.lift.LiftLineSync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The client's cache of synced lift lines (design doc section 15). Each client tick steps every line
 * with the server's motion rules; a sync replaces a line's state after catching it up to the client
 * level's game time. The cache clears on disconnect and when the client level changes. Chairs read it
 * through {@link ChairEntity#clientLineLookup}.
 */
public final class ClientLiftLines {
    private static final Map<UUID, ClientLine> LINES = new HashMap<>();
    /** Node position to its line's id, for the block entity renderers (a node's block entity does not know its line on the client). */
    private static final Map<BlockPos, UUID> NODES = new HashMap<>();
    private static ClientLevel level;
    /** The level's game time at the previous tick, or MIN_VALUE when there was none. */
    private static long lastGameTime = Long.MIN_VALUE;

    private ClientLiftLines() {
    }

    public static void register() {
        ChairEntity.clientLineLookup = ClientLiftLines::get;
        ClientTickEvent.CLIENT_POST.register(ClientLiftLines::tick);
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> clear());
    }

    public static ClientLine get(UUID id) {
        return LINES.get(id);
    }

    /** The line that has a node at {@code pos}, or null. */
    public static ClientLine lineAt(BlockPos pos) {
        UUID id = NODES.get(pos);
        return id == null ? null : LINES.get(id);
    }

    public static Collection<ClientLine> all() {
        return LINES.values();
    }

    public static int size() {
        return LINES.size();
    }

    public static void clear() {
        LINES.clear();
        NODES.clear();
        level = null;
        lastGameTime = Long.MIN_VALUE;
    }

    /** Handles a sync on the main thread. */
    public static void accept(LiftLineSync sync) {
        ClientLevel current = Minecraft.getInstance().level;
        if (current == null) return;
        if (current != level) {
            LINES.clear();
            NODES.clear();
            level = current;
        }
        if (sync.removed()) {
            LINES.remove(sync.id());
            NODES.values().removeIf(sync.id()::equals);
            return;
        }
        long now = current.getGameTime();
        ClientLine line = LINES.get(sync.id());
        if (line == null) {
            try {
                LINES.put(sync.id(), new ClientLine(sync, now));
            } catch (IllegalArgumentException e) {
                // Nodes that make no path: ignore the line.
            }
        } else {
            line.apply(sync, now);
        }
        ClientLine synced = LINES.get(sync.id());
        if (synced != null) {
            NODES.values().removeIf(sync.id()::equals);
            for (BlockPos node : synced.nodes()) NODES.put(node, sync.id());
        }
    }

    private static void tick(Minecraft minecraft) {
        if (minecraft.level != level) {
            LINES.clear();
            NODES.clear();
            level = minecraft.level;
            lastGameTime = Long.MIN_VALUE;
        }
        if (minecraft.isPaused() || level == null) return;
        // Follow the level's game time rather than counting ticks: the server's time packet moves a drifted
        // client's clock, and the lines must move with it or the chairs sit off by the drift until the next sync.
        long now = level.getGameTime();
        long delta = lastGameTime == Long.MIN_VALUE ? 1 : now - lastGameTime;
        lastGameTime = now;
        for (ClientLine line : LINES.values()) {
            if (delta == 1) line.tick();
            else line.shift(delta);
        }
    }
}
