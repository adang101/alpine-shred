package io.github.adang101.alpineshred.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlpineConfigTest {
    @TempDir
    Path dir;

    private final List<String> warnings = new ArrayList<>();

    @BeforeEach
    void reset() {
        AlpineConfig.COMMON.resetToDefaults();
        AlpineConfig.CLIENT.resetToDefaults();
        warnings.clear();
    }

    @Test
    void missingFileIsCreatedWithEveryKey() throws Exception {
        AlpineConfig.loadCommon(dir, warnings::add);
        assertTrue(warnings.isEmpty(), warnings.toString());
        JsonObject written = read("alpineshred-common.json");
        assertEquals(AlpineConfig.SCHEMA_VERSION, written.get("schemaVersion").getAsInt());
        assertTrue(written.getAsJsonObject("movement").getAsJsonObject("validation").has("strict"));
        assertEquals(AlpineConfig.COMMON.entries().size(), countLeaves(written) - 1);
    }

    @Test
    void partialFileKeepsValuesAndFillsDefaults() throws Exception {
        write("alpineshred-common.json", "{\"schemaVersion\":1,\"movement\":{\"maxSpeedMul\":1.5},\"lift\":{\"seats\":3}}");
        AlpineConfig.loadCommon(dir, warnings::add);
        assertEquals(1.5, AlpineConfig.MOVEMENT_MAX_SPEED_MUL.value(), 0);
        assertEquals(3, AlpineConfig.LIFT_SEATS.value());
        assertTrue(AlpineConfig.MOVEMENT_ENABLED.on());
        assertTrue(read("alpineshred-common.json").getAsJsonObject("trick").has("maxComboScore"));
    }

    @Test
    void badValuesAreClampedOrDefaultedAndLogged() throws Exception {
        write("alpineshred-common.json", "{\"lift\":{\"seats\":9,\"spacing\":\"wide\",\"speedLevels\":[1,99]},"
                + "\"trick\":{\"broadcast\":\"everyone\"},\"bogus\":true}");
        AlpineConfig.loadCommon(dir, warnings::add);
        assertEquals(4, AlpineConfig.LIFT_SEATS.value());
        assertEquals(24, AlpineConfig.LIFT_SPACING.value());
        assertEquals(List.of(1.0, 10.0), AlpineConfig.LIFT_SPEED_LEVELS.get());
        assertEquals("self", AlpineConfig.TRICK_BROADCAST.get());
        assertEquals(5, warnings.size(), warnings.toString());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("bogus")));
    }

    @Test
    void corruptFileIsBackedUpAndReplaced() throws Exception {
        write("alpineshred-client.json", "{ this is not json");
        AlpineConfig.loadClient(dir, warnings::add);
        assertTrue(Files.exists(dir.resolve("alpineshred-client.json.bak")));
        assertEquals("ride", AlpineConfig.CLIENT_STEER_MODE.get());
        assertEquals(1, warnings.size());
    }

    @Test
    void newerSchemaIsReadButNotOverwritten() throws Exception {
        String original = "{\"schemaVersion\":99,\"client\":{\"stance\":\"goofy\",\"futureKey\":1}}";
        write("alpineshred-client.json", original);
        AlpineConfig.loadClient(dir, warnings::add);
        assertEquals("goofy", AlpineConfig.CLIENT_STANCE.get());
        assertEquals(original, Files.readString(dir.resolve("alpineshred-client.json")));
    }

    @Test
    void strictValidationAutoFollowsServerType() {
        assertTrue(AlpineConfig.strictValidation(true));
        assertFalse(AlpineConfig.strictValidation(false));
        AlpineConfig.MOVEMENT_VALIDATION_STRICT.set("true");
        assertTrue(AlpineConfig.strictValidation(false));
    }

    @Test
    void stringBooleansAreAccepted() throws Exception {
        write("alpineshred-client.json", "{\"client\":{\"hints\":\"false\"}}");
        AlpineConfig.loadClient(dir, warnings::add);
        assertFalse(AlpineConfig.CLIENT_HINTS.on());
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    private void write(String name, String content) throws Exception {
        Files.writeString(dir.resolve(name), content, StandardCharsets.UTF_8);
    }

    private JsonObject read(String name) throws Exception {
        return JsonParser.parseString(Files.readString(dir.resolve(name))).getAsJsonObject();
    }

    private static int countLeaves(JsonObject object) {
        int n = 0;
        for (String key : object.keySet()) {
            n += object.get(key).isJsonObject() ? countLeaves(object.getAsJsonObject(key)) : 1;
        }
        return n;
    }
}
