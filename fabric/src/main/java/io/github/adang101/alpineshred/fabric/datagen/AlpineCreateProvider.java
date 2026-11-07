package io.github.adang101.alpineshred.fabric.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.adang101.alpineshred.AlpineShred;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.CompletableFuture;

/**
 * Data for the kinetic lift drive (phase 9, README 78), written as JSON by id because datagen runs without Create
 * and the block only exists when Create is present, so no {@code Block} or {@code Item} object is available. A
 * recipe whose result is not a registered item is skipped by both loaders' load conditions before it is parsed,
 * so a world without Create loads it as nothing. The recipe's advancement unlocks on the drive station (an item that
 * always exists) because an item predicate naming Create's items would not parse without Create. There is no loot
 * table: the block drops itself in code ({@code KineticLiftDriveBlock.getDrops}), since a table naming this item
 * would be a load error without Create. Tag entries are optional and written by the tag provider.
 */
public final class AlpineCreateProvider implements DataProvider {
    /** The kinetic drive's id: block, item and block entity share it. */
    public static final ResourceLocation KINETIC_DRIVE = AlpineShred.id("kinetic_lift_drive");

    private final PackOutput.PathProvider recipes;
    private final PackOutput.PathProvider advancements;

    public AlpineCreateProvider(FabricDataOutput output) {
        this.recipes = output.createPathProvider(PackOutput.Target.DATA_PACK, "recipes");
        this.advancements = output.createPathProvider(PackOutput.Target.DATA_PACK, "advancements");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        return CompletableFuture.allOf(
                DataProvider.saveStable(cache, recipe(), recipes.json(KINETIC_DRIVE)),
                DataProvider.saveStable(cache, advancement(), advancements.json(AlpineShred.id("recipes/redstone/kinetic_lift_drive"))));
    }

    /** A gearbox, a large cogwheel for the cable wheel, and an iron block for the frame; the balance pass (WP5) may change it. */
    private static JsonElement recipe() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:crafting_shapeless");
        json.addProperty("category", "redstone");
        json.add("conditions", array(object("type", "forge:mod_loaded", "modid", "create")));
        JsonObject fabricCondition = object("condition", "fabric:all_mods_loaded");
        JsonArray mods = new JsonArray();
        mods.add("create");
        fabricCondition.add("values", mods);
        json.add("fabric:load_conditions", array(fabricCondition));
        json.add("ingredients", array(object("item", "create:gearbox"), object("item", "create:large_cogwheel"), object("item", "minecraft:iron_block")));
        json.add("result", object("item", KINETIC_DRIVE.toString()));
        return json;
    }

    private static JsonElement advancement() {
        JsonObject has = new JsonObject();
        JsonObject hasConditions = new JsonObject();
        hasConditions.add("items", array(itemsPredicate("alpineshred:lift_drive_station")));
        has.add("conditions", hasConditions);
        has.addProperty("trigger", "minecraft:inventory_changed");

        JsonObject unlocked = new JsonObject();
        unlocked.add("conditions", object("recipe", KINETIC_DRIVE.toString()));
        unlocked.addProperty("trigger", "minecraft:recipe_unlocked");

        JsonObject criteria = new JsonObject();
        criteria.add("has_lift_drive_station", has);
        criteria.add("has_the_recipe", unlocked);

        JsonObject rewards = new JsonObject();
        rewards.add("recipes", array(new com.google.gson.JsonPrimitive(KINETIC_DRIVE.toString())));

        JsonArray requirement = new JsonArray();
        requirement.add("has_lift_drive_station");
        requirement.add("has_the_recipe");
        JsonArray requirements = new JsonArray();
        requirements.add(requirement);

        JsonObject json = new JsonObject();
        json.addProperty("parent", "minecraft:recipes/root");
        json.add("criteria", criteria);
        json.add("requirements", requirements);
        json.add("rewards", rewards);
        json.addProperty("sends_telemetry_event", false);
        return json;
    }

    private static JsonObject itemsPredicate(String item) {
        JsonObject predicate = new JsonObject();
        JsonArray items = new JsonArray();
        items.add(item);
        predicate.add("items", items);
        return predicate;
    }

    private static JsonObject object(String... keyValues) {
        JsonObject o = new JsonObject();
        for (int i = 0; i < keyValues.length; i += 2) o.addProperty(keyValues[i], keyValues[i + 1]);
        return o;
    }

    private static JsonArray array(JsonElement... elements) {
        JsonArray a = new JsonArray();
        for (JsonElement e : elements) a.add(e);
        return a;
    }

    @Override
    public String getName() {
        return "Alpine Shred Create data";
    }
}
