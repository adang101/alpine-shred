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
    /** Follows the level's game time; see {@link LineClock}. */
    private static final LineClock CLOCK = new LineClock();

    /** Smoke diagnostics only (null otherwise): receives a line for each sync and each client tick whose game time moved by other than one. */
    public static volatile java.util.function.Consumer<String> trace;

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
        CLOCK.reset();
    }

    /** Handles a sync on the main thread. */
    public static void accept(LiftLineSync sync) {
        ClientLevel current = Minecraft.getInstance().level;
        if (current == null) return;
        if (current != level) {
            LINES.clear();
            NODES.clear();
            level = current;
            CLOCK.reset();
        }
        if (sync.removed()) {
            LINES.remove(sync.id());
            NODES.values().removeIf(sync.id()::equals);
            return;
        }
        long now = current.getGameTime();
        // Move the other lines over any clock jump that came with this sync, so the next tick does not repeat it.
        long moved = CLOCK.follow(LINES.values(), now, 0);
        java.util.function.Consumer<String> t = trace;
        if (t != null) t.accept("lift: sync stamp " + sync.gameTime() + " at client gt" + now + ", clock moved " + moved + " since the last tick");
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
            CLOCK.reset();
        }
        if (minecraft.isPaused() || level == null) return;
        // Follow the level's game time rather than counting ticks: the server's time packet moves a drifted
        // client's clock, and the lines must move with it or the chairs sit off by the drift until the next sync.
        long moved = CLOCK.follow(LINES.values(), level.getGameTime(), 1);
        java.util.function.Consumer<String> t = trace;
        if (moved != 1 && t != null) t.accept("lift: client clock moved " + moved + " ticks in one tick, now gt" + level.getGameTime());
    }
}
