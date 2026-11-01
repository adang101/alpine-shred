package io.github.adang101.alpineshred.client.lift;

import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import io.github.adang101.alpineshred.lift.ChairEntity;
import io.github.adang101.alpineshred.lift.LiftLineSync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

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
    private static ClientLevel level;

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

    public static int size() {
        return LINES.size();
    }

    public static void clear() {
        LINES.clear();
        level = null;
    }

    /** Handles a sync on the main thread. */
    public static void accept(LiftLineSync sync) {
        ClientLevel current = Minecraft.getInstance().level;
        if (current == null) return;
        if (current != level) {
            LINES.clear();
            level = current;
        }
        if (sync.removed()) {
            LINES.remove(sync.id());
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
    }

    private static void tick(Minecraft minecraft) {
        if (minecraft.level != level) {
            LINES.clear();
            level = minecraft.level;
        }
        if (minecraft.isPaused()) return;
        for (ClientLine line : LINES.values()) line.tick();
    }
}
