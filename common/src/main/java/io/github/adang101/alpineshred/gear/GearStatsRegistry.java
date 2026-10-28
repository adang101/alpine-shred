package io.github.adang101.alpineshred.gear;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Current board and ski stats, replaced atomically when {@code data/alpineshred/gear/} reloads.
 * Keyed by item id path (namespace {@code alpineshred}). Until data loads, and on clients
 * connected to a dedicated server before the phase 2 tuning sync, the built-in defaults apply.
 */
public final class GearStatsRegistry {
    private static volatile Map<String, GearStats> current = GearStats.DEFAULTS;
    private static volatile boolean loadedFromData;

    private GearStatsRegistry() {
    }

    public static GearStats get(String itemId, GearKind kind) {
        GearStats stats = current.get(itemId);
        if (stats == null) return GearStats.neutral(kind);
        return stats.kind() == kind ? stats : new GearStats(kind, stats.speed(), stats.grip(), stats.pop(),
                stats.powderFloat(), stats.landingForgiveness());
    }

    public static Map<String, GearStats> all() {
        return current;
    }

    /** Replaces the table; built-in defaults fill any item the data did not define. */
    public static void replace(Map<String, GearStats> fromData) {
        Map<String, GearStats> merged = new LinkedHashMap<>(GearStats.DEFAULTS);
        merged.putAll(fromData);
        current = Collections.unmodifiableMap(merged);
        loadedFromData = true;
    }

    public static boolean loadedFromData() {
        return loadedFromData;
    }
}
