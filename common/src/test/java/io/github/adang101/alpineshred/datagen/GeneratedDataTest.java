package io.github.adang101.alpineshred.datagen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generated data is complete (phase 9, WP5): every item has a recipe and every block has a loot table and a
 * mineable tag, unless the reason is listed below. The ids come from the generated language file, which names
 * every item and block the mod registers (a block's item has the block's id). Reading the committed JSON instead of
 * the registries keeps this a plain unit test; CI's "generated data is up to date" step ties the JSON to datagen.
 * A reason that no longer applies fails too, so the lists cannot go stale.
 */
class GeneratedDataTest {
    private static final String NAMESPACE = "alpineshred";

    /** Items without a crafting recipe, with why. */
    private static final Map<String, String> NO_RECIPE = Map.of(
            "exposed_copper_pipe_rail", "copper rails weather in the world; they are never crafted",
            "weathered_copper_pipe_rail", "copper rails weather in the world; they are never crafted",
            "oxidized_copper_pipe_rail", "copper rails weather in the world; they are never crafted");

    /** Blocks without a loot table, with why. */
    private static final Map<String, String> NO_LOOT_TABLE = Map.of(
            "quarter_pipe", "breaking any block of the pipe drops one item in code (PipeBlock.playerWillDestroy)",
            "kinetic_lift_drive", "drops itself in code, since a table naming it would not load without Create (README 78)");

    /** Blocks without a mineable tag, with why. */
    private static final Map<String, String> NOT_MINEABLE = Map.of();

    private static final String[] MINEABLE_TOOLS = {"pickaxe", "axe", "shovel", "hoe"};

    private static JsonObject json(String path) throws IOException {
        try (InputStream in = GeneratedDataTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static boolean exists(String path) throws IOException {
        try (InputStream in = GeneratedDataTest.class.getResourceAsStream(path)) {
            return in != null;
        }
    }

    /** Ids from keys like {@code block.alpineshred.<id>}; keys with more dots are tooltips and the like. */
    private static Set<String> ids(JsonObject lang, String kind) {
        String prefix = kind + "." + NAMESPACE + ".";
        Set<String> out = new TreeSet<>();
        for (String key : lang.keySet()) {
            if (!key.startsWith(prefix)) continue;
            String id = key.substring(prefix.length());
            if (id.indexOf('.') < 0) out.add(id);
        }
        return out;
    }

    private static Set<String> recipeResults() throws IOException, URISyntaxException {
        URL dir = GeneratedDataTest.class.getResource("/data/" + NAMESPACE + "/recipes");
        assertNotNull(dir, "no generated recipes");
        Set<String> results = new TreeSet<>();
        try (Stream<Path> files = Files.list(Path.of(dir.toURI()))) {
            for (Path file : (Iterable<Path>) files.sorted()::iterator) {
                JsonObject recipe = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonElement result = recipe.get("result");
                if (result == null) continue; // special recipes (wax, goggles lens) make no new item
                String item = result.isJsonObject() ? result.getAsJsonObject().get("item").getAsString() : result.getAsString();
                if (item.startsWith(NAMESPACE + ":")) results.add(item.substring(NAMESPACE.length() + 1));
            }
        }
        return results;
    }

    private static Set<String> mineable() throws IOException {
        Set<String> out = new TreeSet<>();
        for (String tool : MINEABLE_TOOLS) {
            String path = "/data/minecraft/tags/blocks/mineable/" + tool + ".json";
            if (!exists(path)) continue;
            for (JsonElement entry : json(path).getAsJsonArray("values")) {
                String id = entry.isJsonObject() ? entry.getAsJsonObject().get("id").getAsString() : entry.getAsString();
                if (id.startsWith(NAMESPACE + ":")) out.add(id.substring(NAMESPACE.length() + 1));
            }
        }
        return out;
    }

    @Test
    void everyItemHasARecipeOrAReason() throws Exception {
        JsonObject lang = json("/assets/" + NAMESPACE + "/lang/en_us.json");
        Set<String> items = ids(lang, "item");
        items.addAll(ids(lang, "block"));
        assertTrue(items.size() >= 40, "the language file names only " + items.size() + " items and blocks");
        Set<String> crafted = recipeResults();
        List<String> missing = new ArrayList<>();
        List<String> stale = new ArrayList<>();
        for (String id : items) {
            if (!crafted.contains(id) && !NO_RECIPE.containsKey(id)) missing.add(id);
        }
        for (String id : NO_RECIPE.keySet()) {
            if (crafted.contains(id) || !items.contains(id)) stale.add(id);
        }
        assertTrue(missing.isEmpty(), "items with no recipe and no listed reason: " + missing);
        assertTrue(stale.isEmpty(), "NO_RECIPE lists items that have a recipe or do not exist: " + stale);
    }

    @Test
    void everyBlockHasALootTableAndAMineableTagOrAReason() throws Exception {
        JsonObject lang = json("/assets/" + NAMESPACE + "/lang/en_us.json");
        Set<String> blocks = ids(lang, "block");
        assertTrue(blocks.size() >= 25, "the language file names only " + blocks.size() + " blocks");
        Set<String> mineable = mineable();
        List<String> noLoot = new ArrayList<>();
        List<String> noTag = new ArrayList<>();
        List<String> stale = new ArrayList<>();
        for (String id : blocks) {
            boolean hasLoot = exists("/data/" + NAMESPACE + "/loot_tables/blocks/" + id + ".json");
            if (!hasLoot && !NO_LOOT_TABLE.containsKey(id)) noLoot.add(id);
            if (hasLoot && NO_LOOT_TABLE.containsKey(id)) stale.add(id + " (has a loot table)");
            boolean isMineable = mineable.contains(id);
            if (!isMineable && !NOT_MINEABLE.containsKey(id)) noTag.add(id);
            if (isMineable && NOT_MINEABLE.containsKey(id)) stale.add(id + " (is mineable)");
        }
        for (String id : NO_LOOT_TABLE.keySet()) if (!blocks.contains(id)) stale.add(id + " (not a block)");
        assertTrue(noLoot.isEmpty(), "blocks with no loot table and no listed reason: " + noLoot);
        assertTrue(noTag.isEmpty(), "blocks in no minecraft:mineable tag and with no listed reason: " + noTag);
        assertTrue(stale.isEmpty(), "reasons that no longer apply: " + stale);
    }
}
