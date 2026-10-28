package io.github.adang101.alpineshred.gear;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The five tuning multipliers of a board or pair of skis (design doc section 5).
 * Loaded from {@code data/alpineshred/gear/<item id>.json} so balance changes need no recompile.
 *
 * <p>Durability is not part of this record: an item's maximum damage is fixed when the item is
 * registered and vanilla 1.20.1 has no per-stack hook on Fabric, so it lives in
 * {@link #DEFAULT_DURABILITY} and the server-side {@code gear.durabilityMul} wear multiplier is
 * the data-driven way to change gear life.
 */
public record GearStats(
        GearKind kind,
        double speed,
        double grip,
        double pop,
        double powderFloat,
        double landingForgiveness) {

    public static final double MIN_MULTIPLIER = 0.25;
    public static final double MAX_MULTIPLIER = 4.0;

    public static final GearStats NEUTRAL_BOARD = new GearStats(GearKind.BOARD, 1, 1, 1, 1, 1);
    public static final GearStats NEUTRAL_SKIS = new GearStats(GearKind.SKIS, 1, 1, 1, 1, 1);

    /** Built-in defaults, identical to the shipped data files. Keys are item ids without namespace. */
    public static final Map<String, GearStats> DEFAULTS;
    /** Registered maximum damage per item id. */
    public static final Map<String, Integer> DEFAULT_DURABILITY;

    static {
        Map<String, GearStats> stats = new LinkedHashMap<>();
        Map<String, Integer> durability = new LinkedHashMap<>();
        put(stats, durability, "snowboard", GearKind.BOARD, 1.00, 1.00, 1.00, 1.00, 1.00, 480);
        put(stats, durability, "park_snowboard", GearKind.BOARD, 0.95, 0.95, 1.15, 0.90, 1.20, 640);
        put(stats, durability, "freeride_snowboard", GearKind.BOARD, 1.12, 1.10, 0.90, 1.35, 0.90, 640);
        put(stats, durability, "pro_snowboard", GearKind.BOARD, 1.15, 1.15, 1.10, 1.10, 1.10, 1200);
        put(stats, durability, "skis", GearKind.SKIS, 1.00, 1.10, 0.85, 1.00, 1.00, 480);
        put(stats, durability, "carving_skis", GearKind.SKIS, 1.20, 1.25, 0.75, 0.90, 0.90, 640);
        put(stats, durability, "powder_skis", GearKind.SKIS, 1.05, 0.95, 0.85, 1.40, 1.00, 640);
        put(stats, durability, "park_skis", GearKind.SKIS, 0.95, 1.00, 1.10, 0.90, 1.20, 640);
        put(stats, durability, "pro_skis", GearKind.SKIS, 1.18, 1.20, 1.00, 1.15, 1.10, 1200);
        DEFAULTS = Collections.unmodifiableMap(stats);
        DEFAULT_DURABILITY = Collections.unmodifiableMap(durability);
    }

    private static void put(Map<String, GearStats> stats, Map<String, Integer> durability, String id, GearKind kind,
                            double speed, double grip, double pop, double powder, double landing, int maxDamage) {
        stats.put(id, new GearStats(kind, speed, grip, pop, powder, landing));
        durability.put(id, maxDamage);
    }

    public static GearStats neutral(GearKind kind) {
        return kind == GearKind.SKIS ? NEUTRAL_SKIS : NEUTRAL_BOARD;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("kind", kind.serializedName());
        json.addProperty("speed", speed);
        json.addProperty("grip", grip);
        json.addProperty("pop", pop);
        json.addProperty("powder_float", powderFloat);
        json.addProperty("landing_forgiveness", landingForgiveness);
        return json;
    }

    /**
     * Parses a gear file. Missing values fall back to {@code fallback}; out-of-range values are
     * clamped and reported through {@code warn}.
     */
    public static GearStats fromJson(String id, JsonObject json, GearStats fallback, Consumer<String> warn) {
        GearKind kind = fallback.kind();
        if (json.has("kind")) {
            GearKind parsed = GearKind.bySerializedName(json.get("kind").getAsString());
            if (parsed == null) {
                warn.accept("gear/" + id + ": unknown kind '" + json.get("kind").getAsString() + "', using " + kind.serializedName());
            } else {
                kind = parsed;
            }
        }
        return new GearStats(kind,
                multiplier(id, json, "speed", fallback.speed(), warn),
                multiplier(id, json, "grip", fallback.grip(), warn),
                multiplier(id, json, "pop", fallback.pop(), warn),
                multiplier(id, json, "powder_float", fallback.powderFloat(), warn),
                multiplier(id, json, "landing_forgiveness", fallback.landingForgiveness(), warn));
    }

    private static double multiplier(String id, JsonObject json, String key, double fallback, Consumer<String> warn) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) return fallback;
        double value;
        try {
            value = element.getAsDouble();
        } catch (RuntimeException e) {
            warn.accept("gear/" + id + ": '" + key + "' is not a number, using " + fallback);
            return fallback;
        }
        if (!Double.isFinite(value) || value < MIN_MULTIPLIER || value > MAX_MULTIPLIER) {
            double clamped = Double.isFinite(value) ? Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER, value)) : fallback;
            warn.accept("gear/" + id + ": '" + key + "' = " + value + " is outside ["
                    + MIN_MULTIPLIER + ", " + MAX_MULTIPLIER + "], using " + clamped);
            return clamped;
        }
        return value;
    }
}
