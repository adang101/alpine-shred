package io.github.adang101.alpineshred.gear;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GearStatsTest {

    @Test
    void defaultsMatchSectionFiveTable() {
        assertEquals(9, GearStats.DEFAULTS.size());
        GearStats pro = GearStats.DEFAULTS.get("pro_snowboard");
        assertEquals(GearKind.BOARD, pro.kind());
        assertEquals(1.15, pro.speed(), 0);
        assertEquals(1.15, pro.grip(), 0);
        assertEquals(1.10, pro.pop(), 0);
        assertEquals(1.10, pro.powderFloat(), 0);
        assertEquals(1.10, pro.landingForgiveness(), 0);
        GearStats powder = GearStats.DEFAULTS.get("powder_skis");
        assertEquals(GearKind.SKIS, powder.kind());
        assertEquals(1.40, powder.powderFloat(), 0);
        assertEquals(Map.of(
                "snowboard", 480, "park_snowboard", 640, "freeride_snowboard", 640, "pro_snowboard", 1200,
                "skis", 480, "carving_skis", 640, "powder_skis", 640, "park_skis", 640, "pro_skis", 1200),
                GearStats.DEFAULT_DURABILITY);
    }

    @Test
    void shippedGearFilesEqualBuiltInDefaults() throws Exception {
        for (Map.Entry<String, GearStats> entry : GearStats.DEFAULTS.entrySet()) {
            String path = "/data/alpineshred/gear/" + entry.getKey() + ".json";
            try (var in = getClass().getResourceAsStream(path)) {
                assertNotNull(in, path + " missing");
                JsonObject json = JsonParser.parseReader(new InputStreamReader(in)).getAsJsonObject();
                List<String> warnings = new ArrayList<>();
                GearStats parsed = GearStats.fromJson(entry.getKey(), json, GearStats.neutral(entry.getValue().kind()), warnings::add);
                assertEquals(List.of(), warnings, path);
                assertEquals(entry.getValue(), parsed, path);
            }
        }
    }

    @Test
    void parseClampsAndFallsBack() {
        JsonObject json = JsonParser.parseString("{\"kind\":\"ski\",\"speed\":9,\"grip\":\"x\",\"pop\":0.5}").getAsJsonObject();
        List<String> warnings = new ArrayList<>();
        GearStats s = GearStats.fromJson("test", json, GearStats.NEUTRAL_BOARD, warnings::add);
        assertEquals(GearKind.SKIS, s.kind());
        assertEquals(GearStats.MAX_MULTIPLIER, s.speed(), 0);
        assertEquals(1.0, s.grip(), 0);
        assertEquals(0.5, s.pop(), 0);
        assertEquals(2, warnings.size(), warnings.toString());
    }

    @Test
    void jsonRoundTrip() {
        for (GearStats stats : GearStats.DEFAULTS.values()) {
            GearStats back = GearStats.fromJson("x", stats.toJson(), GearStats.NEUTRAL_BOARD, msg -> {
                throw new AssertionError(msg);
            });
            assertEquals(stats, back);
        }
    }

    @Test
    void kindNamesAcceptDocSpellings() {
        assertEquals(GearKind.SKIS, GearKind.bySerializedName("skis"));
        assertEquals(GearKind.SKIS, GearKind.bySerializedName("ski"));
        assertEquals(GearKind.BOARD, GearKind.bySerializedName("BOARD"));
        assertTrue(GearKind.bySerializedName("sled") == null);
    }
}
