package io.github.adang101.alpineshred.park;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * One entry of {@code data/alpineshred/box_materials.json} (design doc section 11, "Box materials"):
 * what some blocks are made of when slid or ground, and their friction in blocks per tick squared.
 * Blocks are ids or {@code #tags}; the first entry that matches a block wins.
 */
public record BoxMaterial(GrindMaterial material, double friction, List<String> blocks) {
    public static final double MAX_FRICTION = 0.1;

    public static final List<BoxMaterial> DEFAULTS = List.of(
            new BoxMaterial(GrindMaterial.PLASTIC, 0.0016, List.of("alpineshred:plastic_box")),
            new BoxMaterial(GrindMaterial.COPPER, 0.0012, List.of("minecraft:waxed_copper_block", "minecraft:waxed_exposed_copper",
                    "minecraft:waxed_weathered_copper", "minecraft:waxed_oxidized_copper", "minecraft:waxed_cut_copper",
                    "minecraft:waxed_exposed_cut_copper", "minecraft:waxed_weathered_cut_copper", "minecraft:waxed_oxidized_cut_copper",
                    "minecraft:waxed_cut_copper_slab", "minecraft:waxed_exposed_cut_copper_slab",
                    "minecraft:waxed_weathered_cut_copper_slab", "minecraft:waxed_oxidized_cut_copper_slab")),
            new BoxMaterial(GrindMaterial.METAL, 0.0020, List.of("alpineshred:metal_box", "minecraft:iron_block")),
            new BoxMaterial(GrindMaterial.CONCRETE, 0.0020, concretes()),
            new BoxMaterial(GrindMaterial.STONE, 0.0024, List.of("minecraft:smooth_stone", "minecraft:smooth_stone_slab",
                    "minecraft:stone_bricks", "minecraft:stone_brick_slab", "minecraft:quartz_block", "minecraft:quartz_slab",
                    "minecraft:smooth_quartz", "minecraft:smooth_quartz_slab", "#minecraft:walls")),
            new BoxMaterial(GrindMaterial.WOOD, 0.0030, List.of("alpineshred:wood_box", "#minecraft:planks", "#minecraft:wooden_slabs",
                    "#minecraft:wooden_fences")));

    private static List<String> concretes() {
        List<String> out = new ArrayList<>();
        for (String color : new String[]{"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray",
                "cyan", "purple", "blue", "brown", "green", "red", "black"}) {
            out.add("minecraft:" + color + "_concrete");
        }
        return List.copyOf(out);
    }

    public BoxMaterial {
        blocks = List.copyOf(blocks);
    }

    public static JsonObject toJson(List<BoxMaterial> table) {
        JsonArray array = new JsonArray();
        for (BoxMaterial m : table) {
            JsonObject o = new JsonObject();
            o.addProperty("material", m.material().serializedName());
            o.addProperty("friction", m.friction());
            JsonArray blocks = new JsonArray();
            m.blocks().forEach(blocks::add);
            o.add("blocks", blocks);
            array.add(o);
        }
        JsonObject json = new JsonObject();
        json.add("materials", array);
        return json;
    }

    /** Parses the file; bad entries are skipped with a warning. An unusable file gives the defaults. */
    public static List<BoxMaterial> fromJson(JsonObject json, Consumer<String> warn) {
        JsonElement materials = json.get("materials");
        if (materials == null || !materials.isJsonArray()) {
            warn.accept("box_materials: no 'materials' list, using the built-in table");
            return DEFAULTS;
        }
        List<BoxMaterial> out = new ArrayList<>();
        int i = 0;
        for (JsonElement e : materials.getAsJsonArray()) {
            i++;
            if (!e.isJsonObject()) {
                warn.accept("box_materials: entry " + i + " is not an object");
                continue;
            }
            JsonObject o = e.getAsJsonObject();
            GrindMaterial material = o.has("material") ? GrindMaterial.byName(o.get("material").getAsString()) : null;
            if (material == null) {
                warn.accept("box_materials: entry " + i + " has no known 'material' (" + GrindMaterial.values().length + " kinds)");
                continue;
            }
            double friction;
            try {
                friction = o.get("friction").getAsDouble();
            } catch (RuntimeException ex) {
                warn.accept("box_materials: entry " + i + " has no numeric 'friction'");
                continue;
            }
            if (friction < 0 || friction > MAX_FRICTION) {
                warn.accept("box_materials: entry " + i + " friction " + friction + " clamped");
                friction = Math.max(0, Math.min(MAX_FRICTION, friction));
            }
            List<String> blocks = new ArrayList<>();
            JsonElement b = o.get("blocks");
            if (b != null && b.isJsonArray()) {
                for (JsonElement x : b.getAsJsonArray()) blocks.add(x.getAsString());
            } else if (b != null && b.isJsonPrimitive()) {
                blocks.add(b.getAsString());
            }
            if (blocks.isEmpty()) warn.accept("box_materials: entry " + i + " lists no blocks");
            out.add(new BoxMaterial(material, friction, blocks));
        }
        return out.isEmpty() ? DEFAULTS : List.copyOf(out);
    }
}
