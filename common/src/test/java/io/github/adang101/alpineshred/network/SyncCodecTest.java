package io.github.adang101.alpineshred.network;

import com.google.gson.JsonObject;
import io.github.adang101.alpineshred.gear.GearKind;
import io.github.adang101.alpineshred.gear.GearStats;
import io.github.adang101.alpineshred.movement.MovementTuning;
import io.github.adang101.alpineshred.movement.SurfaceProfile;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncCodecTest {

    @Test
    void configPayloadRoundTripsAndToleratesJunk() throws IOException {
        SyncCodec.Settings settings = new SyncCodec.Settings(false, 1.5, false);
        assertEquals(settings, SyncCodec.readConfig(SyncCodec.inflate(SyncCodec.deflate(SyncCodec.configPayload(settings)))));

        JsonObject junk = new JsonObject();
        junk.addProperty("movement.maxSpeedMul", "fast");
        junk.addProperty("movement.enabled", 7);
        junk.addProperty("future.key", true);
        SyncCodec.Settings read = SyncCodec.readConfig(junk);
        assertEquals(SyncCodec.Settings.DEFAULTS.maxSpeedMul(), read.maxSpeedMul(), 1e-9);
        assertEquals(SyncCodec.Settings.DEFAULTS.scrapeEnabled(), read.scrapeEnabled());

        JsonObject huge = new JsonObject();
        huge.addProperty("movement.maxSpeedMul", 1e9);
        assertEquals(3.0, SyncCodec.readConfig(huge).maxSpeedMul(), 1e-9, "clamped to the config range");
    }

    @Test
    void tuningPayloadRoundTripsThroughCompression() throws IOException {
        MovementTuning tuning = MovementTuning.DEFAULT.with(Map.of("maxSpeed", 1.2, "dragCoeff", 0.006), w -> {
        });
        Map<String, GearStats> gear = new LinkedHashMap<>(GearStats.DEFAULTS);
        gear.put("snowboard", new GearStats(GearKind.BOARD, 1.3, 1, 1, 1, 1));
        byte[] bytes = SyncCodec.deflate(SyncCodec.tuningPayload(tuning, SurfaceProfile.DEFAULTS.values(), gear));
        assertTrue(bytes.length < 4096, "payload " + bytes.length + " bytes");

        List<String> warnings = new ArrayList<>();
        SyncCodec.Tuning back = SyncCodec.readTuning(SyncCodec.inflate(bytes), warnings::add);
        assertEquals(List.of(), warnings);
        assertEquals(tuning.values(), back.tuning().values());
        assertEquals(List.copyOf(SurfaceProfile.DEFAULTS.values()), back.surfaces());
        assertEquals(gear, back.gear());
    }

    @Test
    void decompressionBombsAreRejected() {
        byte[] zeros = new byte[SyncCodec.MAX_INFLATED_BYTES * 4];
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(zeros);
        deflater.finish();
        byte[] buf = new byte[1 << 16];
        int n = deflater.deflate(buf);
        deflater.end();
        byte[] bomb = Arrays.copyOf(buf, n);
        assertThrows(IOException.class, () -> SyncCodec.inflate(bomb));
    }

    @Test
    void truncatedAndGarbagePayloadsAreRejected() {
        byte[] good = SyncCodec.deflate(new JsonObject());
        assertThrows(IOException.class, () -> SyncCodec.inflate(Arrays.copyOf(good, good.length / 2)));
        assertThrows(IOException.class, () -> SyncCodec.inflate(new byte[]{1, 2, 3, 4, 5}));
    }

    @Test
    void missingSectionsFallBackToDefaults() {
        SyncCodec.Tuning t = SyncCodec.readTuning(new JsonObject(), w -> {
        });
        assertEquals(MovementTuning.DEFAULT.values(), t.tuning().values());
        assertEquals(SurfaceProfile.DEFAULTS.size(), t.surfaces().size());
        assertTrue(t.gear().isEmpty());
    }
}
