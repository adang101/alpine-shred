package io.github.adang101.alpineshred.gear;

import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Per-player {@link GearProfile} cache (design doc section 6): rebuilt on any equipment change
 * event, with a fallback rebuild every {@value #FALLBACK_TICKS} ticks to catch missed events.
 * Keyed weakly by the player entity, so client and server players never collide and nothing
 * leaks when a player leaves.
 */
public final class GearProfiles {
    public static final int FALLBACK_TICKS = 10;

    private static final Map<Player, Entry> CACHE = new WeakHashMap<>();

    private GearProfiles() {
    }

    private static final class Entry {
        GearProfile profile;
        int builtAt;
        boolean dirty;
    }

    public static void register() {
        GearAccess.current().onGearChanged(GearProfiles::invalidate);
    }

    public static synchronized GearProfile get(Player player) {
        Entry entry = CACHE.get(player);
        int now = player.tickCount;
        if (entry == null) {
            entry = new Entry();
            CACHE.put(player, entry);
            entry.dirty = true;
        }
        if (entry.dirty || entry.profile == null || now - entry.builtAt >= FALLBACK_TICKS || now < entry.builtAt) {
            entry.profile = GearProfile.build(player);
            entry.builtAt = now;
            entry.dirty = false;
        }
        return entry.profile;
    }

    public static synchronized void invalidate(Player player) {
        Entry entry = CACHE.get(player);
        if (entry != null) entry.dirty = true;
    }

    public static synchronized void forget(Player player) {
        CACHE.remove(player);
    }
}
