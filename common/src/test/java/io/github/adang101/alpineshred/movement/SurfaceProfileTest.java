package io.github.adang101.alpineshred.movement;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceProfileTest {

    @Test
    void defaultsMatchSectionSevenTable() {
        assertEquals(0.0006, SurfaceProfile.GROOMED.friction(), 0);
        assertEquals(1.10, SurfaceProfile.GROOMED.grip(), 0);
        assertEquals(0.0011, SurfaceProfile.SNOW_BLOCK.friction(), 0);
        assertEquals(0.0014, SurfaceProfile.SNOW_LAYER.friction(), 0);
        assertEquals(0.0004, SurfaceProfile.ICE.friction(), 0);
        assertEquals(0.0003, SurfaceProfile.PACKED_ICE.friction(), 0);
        assertEquals(0.00017, SurfaceProfile.BLUE_ICE.friction(), 0);
        assertEquals(0.0040, SurfaceProfile.POWDER.friction(), 0);
        assertEquals(0.0180, SurfaceProfile.SCRAPE.friction(), 0);
        assertEquals(0.70, SurfaceProfile.SCRAPE.grip(), 0);
    }

    @Test
    void shippedProfileFilesEqualBuiltInDefaults() throws Exception {
        for (Map.Entry<String, SurfaceProfile> entry : SurfaceProfile.DEFAULTS.entrySet()) {
            String path = "/data/alpineshred/surface_profiles/" + entry.getKey() + ".json";
            try (var in = getClass().getResourceAsStream(path)) {
                assertNotNull(in, path + " missing");
                JsonObject json = JsonParser.parseReader(new InputStreamReader(in)).getAsJsonObject();
                List<String> warnings = new ArrayList<>();
                SurfaceProfile parsed = SurfaceProfile.fromJson(entry.getKey(), json, warnings::add);
                assertEquals(List.of(), warnings, path);
                assertEquals(entry.getValue(), parsed, path);
            }
        }
    }

    @Test
    void customProfileNeedsFriction() {
        List<String> warnings = new ArrayList<>();
        assertNull(SurfaceProfile.fromJson("mud", new JsonObject(), warnings::add));
        SurfaceProfile slush = SurfaceProfile.fromJson("slush",
                JsonParser.parseString("{\"category\":\"snow\",\"friction\":0.5,\"grip\":0.9,\"blocks\":[\"#c:slush\"]}").getAsJsonObject(),
                warnings::add);
        assertNotNull(slush);
        assertEquals(SurfaceProfile.MAX_FRICTION, slush.friction(), 0);
        assertTrue(warnings.size() >= 2);
    }

    @Test
    void groomedOutranksGenericSnow() {
        assertTrue(SurfaceProfile.GROOMED.priority() > SurfaceProfile.SNOW_BLOCK.priority());
        assertTrue(SurfaceProfile.SNOW_LAYER.priority() > SurfaceProfile.SNOW_BLOCK.priority());
    }
}
