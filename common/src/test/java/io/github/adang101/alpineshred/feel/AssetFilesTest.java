package io.github.adang101.alpineshred.feel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sound and particle assets are complete: every file referenced exists, every sound has a subtitle. */
class AssetFilesTest {
    private static JsonObject json(String path) throws IOException {
        try (InputStream in = AssetFilesTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static void exists(String path) throws IOException {
        try (InputStream in = AssetFilesTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing " + path);
        }
    }

    @Test
    void everySoundEventHasFilesAndATranslatedSubtitle() throws IOException {
        JsonObject sounds = json("/assets/alpineshred/sounds.json");
        JsonObject lang = json("/assets/alpineshred/lang/en_us.json");
        assertTrue(sounds.size() >= 16, "sound events: " + sounds.size());
        for (Map.Entry<String, JsonElement> event : sounds.entrySet()) {
            JsonObject def = event.getValue().getAsJsonObject();
            String subtitle = def.get("subtitle").getAsString();
            assertTrue(lang.has(subtitle), event.getKey() + ": subtitle " + subtitle + " not in en_us.json");
            for (JsonElement s : def.getAsJsonArray("sounds")) {
                String name = s.isJsonObject() ? s.getAsJsonObject().get("name").getAsString() : s.getAsString();
                String[] id = name.split(":", 2);
                exists("/assets/" + id[0] + "/sounds/" + id[1] + ".ogg");
            }
        }
    }

    @Test
    void everyParticleHasItsTextures() throws IOException {
        for (String particle : List.of("snow_spray", "snow_poof", "snow_trail", "rail_spark")) {
            JsonObject def = json("/assets/alpineshred/particles/" + particle + ".json");
            for (JsonElement t : def.getAsJsonArray("textures")) {
                String[] id = t.getAsString().split(":", 2);
                exists("/assets/" + id[0] + "/textures/particle/" + id[1] + ".png");
            }
        }
    }

    @Test
    void everyClipFileExists() throws IOException {
        for (String clip : RiderClips.ALL) exists("/assets/alpineshred/player_animation/" + clip + ".json");
    }
}
