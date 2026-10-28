package io.github.adang101.alpineshred.movement;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementTuningTest {

    @Test
    void defaultsMatchTheDesignDocTable() {
        MovementTuning t = MovementTuning.DEFAULT;
        assertEquals(0.35, t.slopeAccelScale, 0);
        assertEquals(0.005, t.dragCoeff, 0);
        assertEquals(1.40, t.maxSpeed, 0);
        assertEquals(7.0, t.baseTurnRate, 0);
        assertEquals(0.45, t.turnSpeedRef, 0);
        assertEquals(0.35, t.gripBlend, 0);
        assertEquals(8.0, t.brakeFrictionMul, 0);
        assertEquals(6.0, t.skiBrakeFrictionMul, 0);
        assertEquals(0.8, t.tuckDragMul, 0);
        assertEquals(1.6, t.flareDragMul, 0);
        assertEquals(0.42, t.popVelocity, 0);
        assertEquals(0.6, t.popChargeMax, 0);
        assertEquals(1.0, t.snapDown, 0);
        assertEquals(0.35, t.launchGrade, 0);
        assertEquals(1.0, t.stepUp, 0);
        assertEquals(0.10, t.poleImpulse, 0);
        assertEquals(0.25, t.powderFloatSpeed, 0);
        assertEquals(0.5, t.wallHitSpeed, 0);
        assertEquals(30, t.wipeoutTicks);
        assertEquals(-0.35, t.strappedWalkMod, 0);
        assertEquals(0.5, t.edgeTurnBoost, 0);
        assertEquals(0.0015, t.edgeScrub, 0);
        assertEquals(0.6, t.counterEdgeGrip, 0);
        assertEquals(1.5, t.lookDeadzone, 0);
        assertEquals(0.1, t.pivotSpeed, 0);
        assertEquals(6.0, t.pivotRate, 0);
        assertEquals(0.08 * 0.35, t.effectiveGravity(), 1e-12);
    }

    @Test
    void shippedTuningFileEqualsBuiltInDefaults() throws Exception {
        try (var in = getClass().getResourceAsStream("/data/alpineshred/movement/tuning.json")) {
            if (in == null) throw new AssertionError("tuning.json missing from resources");
            JsonObject json = JsonParser.parseReader(new java.io.InputStreamReader(in)).getAsJsonObject();
            List<String> warnings = new ArrayList<>();
            MovementTuning parsed = MovementTuning.fromJson(json, warnings::add);
            assertEquals(List.of(), warnings);
            assertEquals(MovementTuning.DEFAULT.values(), parsed.values());
            for (String key : MovementTuning.SPECS.keySet()) {
                assertTrue(json.has(key), "tuning.json should list every key; missing " + key);
            }
        }
    }

    @Test
    void partialFileKeepsDefaultsForMissingKeys() {
        JsonObject json = new JsonObject();
        json.addProperty("maxSpeed", 1.2);
        List<String> warnings = new ArrayList<>();
        MovementTuning t = MovementTuning.fromJson(json, warnings::add);
        assertEquals(1.2, t.maxSpeed, 0);
        assertEquals(0.35, t.slopeAccelScale, 0);
        assertTrue(warnings.isEmpty());
    }

    @Test
    void badValuesAreClampedOrIgnoredWithWarnings() {
        JsonObject json = JsonParser.parseString(
                "{\"maxSpeed\": 99, \"dragCoeff\": \"fast\", \"notAKey\": 1, \"gripBlend\": -5, \"_comment\": \"ok\"}")
                .getAsJsonObject();
        List<String> warnings = new ArrayList<>();
        MovementTuning t = MovementTuning.fromJson(json, warnings::add);
        assertEquals(3.0, t.maxSpeed, 0);
        assertEquals(0.005, t.dragCoeff, 0);
        assertEquals(0.02, t.gripBlend, 0);
        assertEquals(4, warnings.size(), warnings.toString());
    }

    @Test
    void unknownKeyLookupFailsLoudly() {
        assertThrows(IllegalArgumentException.class, () -> MovementTuning.DEFAULT.get("maxSpeeed"));
    }

    @Test
    void withReturnsValidatedCopy() {
        MovementTuning t = MovementTuning.DEFAULT.with(Map.of("baseTurnRate", 9.0), msg -> {
        });
        assertEquals(9.0, t.baseTurnRate, 0);
        assertEquals(7.0, MovementTuning.DEFAULT.baseTurnRate, 0);
    }

    @Test
    void everySpecDefaultIsInsideItsRange() {
        MovementTuning.SPECS.forEach((key, spec) -> assertTrue(
                spec.defaultValue() >= spec.min() && spec.defaultValue() <= spec.max(), key));
    }
}
