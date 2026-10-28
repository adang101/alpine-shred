package io.github.adang101.alpineshred.movement;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * How a ground surface rides (design doc section 7, "Surface profiles"). Profiles live in
 * {@code data/alpineshred/surface_profiles/*.json} and match blocks by id or {@code #tag};
 * the highest {@link #priority} match wins, and anything solid that matches nothing uses
 * {@link #SCRAPE}.
 *
 * @param friction deceleration in blocks per tick squared at flat contact
 * @param grip     multiplies the velocity-to-heading blend; low grip drifts
 */
public record SurfaceProfile(
        String id,
        Category category,
        double friction,
        double grip,
        int priority,
        List<String> blocks) {

    public enum Category {
        /** Groomed, snow blocks and layers. Wax works, carving wears edges. */
        SNOW,
        ICE,
        /** Powder snow: rider floats only above the float speed. */
        POWDER,
        /** Any other solid block. Fast board wear. */
        SCRAPE;

        public static Category byName(String name) {
            for (Category c : values()) {
                if (c.name().equalsIgnoreCase(name)) return c;
            }
            return null;
        }
    }

    public static final double MAX_FRICTION = 0.1;

    public static final SurfaceProfile GROOMED = builtin("groomed", Category.SNOW, 0.0006, 1.10, 20, "alpineshred:groomed_snow");
    public static final SurfaceProfile SNOW_BLOCK = builtin("snow_block", Category.SNOW, 0.0011, 1.00, 0, "minecraft:snow_block", "#alpineshred:slide/snow");
    public static final SurfaceProfile SNOW_LAYER = builtin("snow_layer", Category.SNOW, 0.0014, 1.00, 10, "minecraft:snow");
    public static final SurfaceProfile ICE = builtin("ice", Category.ICE, 0.0004, 0.55, 0, "minecraft:ice", "#alpineshred:slide/ice");
    public static final SurfaceProfile PACKED_ICE = builtin("packed_ice", Category.ICE, 0.0003, 0.60, 10, "minecraft:packed_ice");
    public static final SurfaceProfile BLUE_ICE = builtin("blue_ice", Category.ICE, 0.00017, 0.50, 10, "minecraft:blue_ice");
    public static final SurfaceProfile POWDER = builtin("powder", Category.POWDER, 0.0040, 0.80, 10, "minecraft:powder_snow", "#alpineshred:slide/powder");
    /** Fallback for every other solid block; never matched by block lists. */
    public static final SurfaceProfile SCRAPE = builtin("scrape", Category.SCRAPE, 0.0180, 0.70, Integer.MIN_VALUE);

    /** Built-in profiles keyed by id, identical to the shipped data files. */
    public static final Map<String, SurfaceProfile> DEFAULTS;

    static {
        Map<String, SurfaceProfile> map = new LinkedHashMap<>();
        for (SurfaceProfile p : List.of(GROOMED, SNOW_BLOCK, SNOW_LAYER, ICE, PACKED_ICE, BLUE_ICE, POWDER, SCRAPE)) {
            map.put(p.id(), p);
        }
        DEFAULTS = Collections.unmodifiableMap(map);
    }

    public SurfaceProfile {
        blocks = List.copyOf(blocks);
    }

    private static SurfaceProfile builtin(String id, Category category, double friction, double grip, int priority, String... blocks) {
        return new SurfaceProfile(id, category, friction, grip, priority, List.of(blocks));
    }

    public boolean isSnow() {
        return category == Category.SNOW || category == Category.POWDER;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("category", category.name().toLowerCase(java.util.Locale.ROOT));
        json.addProperty("friction", friction);
        json.addProperty("grip", grip);
        json.addProperty("priority", priority);
        JsonArray array = new JsonArray();
        blocks.forEach(array::add);
        json.add("blocks", array);
        return json;
    }

    /** Parses one profile file; returns {@code null} (after warning) if it cannot be used. */
    public static SurfaceProfile fromJson(String id, JsonObject json, Consumer<String> warn) {
        SurfaceProfile base = DEFAULTS.get(id);
        Category category = base != null ? base.category() : Category.SNOW;
        if (json.has("category")) {
            Category parsed = Category.byName(json.get("category").getAsString());
            if (parsed == null) {
                warn.accept("surface_profiles/" + id + ": unknown category '" + json.get("category").getAsString() + "'");
            } else {
                category = parsed;
            }
        }
        double friction = number(json, "friction", base != null ? base.friction() : Double.NaN, id, warn);
        double grip = number(json, "grip", base != null ? base.grip() : 1.0, id, warn);
        if (Double.isNaN(friction)) {
            warn.accept("surface_profiles/" + id + ": missing 'friction', profile skipped");
            return null;
        }
        friction = clamp(friction, 0, MAX_FRICTION, "friction", id, warn);
        grip = clamp(grip, 0.02, 2.0, "grip", id, warn);
        int priority = (int) number(json, "priority", base != null ? base.priority() : 0, id, warn);

        List<String> blocks = new ArrayList<>();
        JsonElement element = json.get("blocks");
        if (element != null && element.isJsonArray()) {
            for (JsonElement e : element.getAsJsonArray()) blocks.add(e.getAsString());
        } else if (element != null && element.isJsonPrimitive()) {
            blocks.add(element.getAsString());
        } else if (base != null) {
            blocks.addAll(base.blocks());
        }
        if (blocks.isEmpty() && category != Category.SCRAPE) {
            warn.accept("surface_profiles/" + id + ": no 'blocks', profile will never match");
        }
        return new SurfaceProfile(id, category, friction, grip, priority, blocks);
    }

    private static double number(JsonObject json, String key, double fallback, String id, Consumer<String> warn) {
        JsonElement e = json.get(key);
        if (e == null) return fallback;
        try {
            return e.getAsDouble();
        } catch (RuntimeException ex) {
            warn.accept("surface_profiles/" + id + ": '" + key + "' is not a number");
            return fallback;
        }
    }

    private static double clamp(double v, double lo, double hi, String key, String id, Consumer<String> warn) {
        if (v < lo || v > hi) {
            double c = Math.max(lo, Math.min(hi, v));
            warn.accept("surface_profiles/" + id + ": '" + key + "' = " + v + " clamped to " + c);
            return c;
        }
        return v;
    }
}
