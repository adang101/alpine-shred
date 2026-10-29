package io.github.adang101.alpineshred.park;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The park blocks' client assets are complete: a blockstate per block, every model it names and
 * every texture those models (and their parents) use exist, every block has an item model, and the
 * blockstates cover every value of the properties that pick models.
 */
class ParkAssetsTest {
    /** The ids {@code ModBlocks} registers for the park (kept in step by hand; the game tests check the registry). */
    static final List<String> BLOCKS = List.of("pipe_rail", "flat_rail",
            "copper_pipe_rail", "exposed_copper_pipe_rail", "weathered_copper_pipe_rail", "oxidized_copper_pipe_rail",
            "waxed_copper_pipe_rail", "waxed_exposed_copper_pipe_rail", "waxed_weathered_copper_pipe_rail", "waxed_oxidized_copper_pipe_rail",
            "rail_post", "wood_box", "metal_box", "plastic_box", "park_ramp", "quarter_pipe", "pipe_coping",
            "slope_marker_green", "slope_marker_blue", "slope_marker_black", "park_flag", "start_gate");

    private static JsonObject json(String path) throws IOException {
        try (InputStream in = ParkAssetsTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static boolean exists(String path) throws IOException {
        try (InputStream in = ParkAssetsTest.class.getResourceAsStream(path)) {
            return in != null;
        }
    }

    private static String path(String id, String folder, String ext) {
        String[] p = id.contains(":") ? id.split(":", 2) : new String[]{"minecraft", id};
        return "/assets/" + p[0] + "/" + folder + "/" + p[1] + ext;
    }

    /** Checks a model and its own-namespace parents; vanilla parents are trusted. */
    private static void checkModel(String id, Set<String> seen) throws IOException {
        if (!id.startsWith("alpineshred:") || !seen.add(id)) return;
        JsonObject model = json(path(id, "models", ".json"));
        if (model.has("textures")) {
            for (Map.Entry<String, JsonElement> t : model.getAsJsonObject("textures").entrySet()) {
                String tex = t.getValue().getAsString();
                if (tex.startsWith("#")) continue;
                assertTrue(exists(path(tex, "textures", ".png")), id + ": texture " + tex + " missing");
            }
        }
        if (model.has("elements")) {
            for (JsonElement e : model.getAsJsonArray("elements")) {
                JsonObject el = e.getAsJsonObject();
                for (String axis : new String[]{"from", "to"}) {
                    for (JsonElement v : el.getAsJsonArray(axis)) {
                        double d = v.getAsDouble();
                        assertTrue(d >= -16 && d <= 32, id + ": element out of range " + el);
                    }
                }
            }
        }
        if (model.has("parent")) checkModel(model.get("parent").getAsString(), seen);
    }

    private static List<String> modelsOf(JsonObject blockstate) {
        List<String> out = new ArrayList<>();
        if (blockstate.has("variants")) {
            for (Map.Entry<String, JsonElement> v : blockstate.getAsJsonObject("variants").entrySet()) {
                out.add(v.getValue().getAsJsonObject().get("model").getAsString());
            }
        }
        if (blockstate.has("multipart")) {
            for (JsonElement part : blockstate.getAsJsonArray("multipart")) {
                out.add(part.getAsJsonObject().getAsJsonObject("apply").get("model").getAsString());
            }
        }
        return out;
    }

    @Test
    void everyParkBlockHasABlockstateModelsTexturesAndAnItem() throws IOException {
        Set<String> seen = new HashSet<>();
        for (String block : BLOCKS) {
            JsonObject state = json("/assets/alpineshred/blockstates/" + block + ".json");
            List<String> models = modelsOf(state);
            assertTrue(!models.isEmpty(), block + ": blockstate names no model");
            for (String m : models) checkModel(m, seen);
            JsonObject item = json("/assets/alpineshred/models/item/" + block + ".json");
            if (item.has("parent")) checkModel(item.get("parent").getAsString(), seen);
            if (item.has("textures")) {
                for (Map.Entry<String, JsonElement> t : item.getAsJsonObject("textures").entrySet()) {
                    assertTrue(exists(path(t.getValue().getAsString(), "textures", ".png")), block + ": item texture missing");
                }
            }
        }
    }

    @Test
    void variantBlockstatesCoverEveryState() throws IOException {
        // facing x slope x step x lip; facing x index x radius; axis x ends x half.
        assertEquals(4 * 3 * 4 * 2, json("/assets/alpineshred/blockstates/park_ramp.json").getAsJsonObject("variants").size());
        assertEquals(4 * 10 * 2, json("/assets/alpineshred/blockstates/quarter_pipe.json").getAsJsonObject("variants").size());
        for (String box : List.of("wood_box", "metal_box", "plastic_box")) {
            assertEquals(2 * 2 * 2 * 2, json("/assets/alpineshred/blockstates/" + box + ".json").getAsJsonObject("variants").size());
        }
    }

    @Test
    void railModelsCoverEveryShapeAndFacing() throws IOException {
        JsonObject state = json("/assets/alpineshred/blockstates/pipe_rail.json");
        Set<String> bars = new HashSet<>();
        for (JsonElement part : state.getAsJsonArray("multipart")) {
            JsonObject when = part.getAsJsonObject().getAsJsonObject("when");
            if (when.size() == 2) bars.add(when.get("shape").getAsString() + "/" + when.get("facing").getAsString());
        }
        for (RailShape shape : RailShape.values()) {
            for (Facing f : Facing.values()) assertTrue(bars.contains(shape.serializedName() + "/" + f.serializedName()), "no bar for " + shape + " " + f);
        }
    }
}
