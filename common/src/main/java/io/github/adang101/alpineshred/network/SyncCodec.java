package io.github.adang101.alpineshred.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.adang101.alpineshred.gear.GearKind;
import io.github.adang101.alpineshred.gear.GearStats;
import io.github.adang101.alpineshred.movement.MovementTuning;
import io.github.adang101.alpineshred.park.BoxMaterial;
import io.github.adang101.alpineshred.movement.SurfaceProfile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Payload encoding for {@code s2c/tuning_sync} and {@code s2c/config_sync} (design doc section 15):
 * compressed JSON. Pure Java so it is unit-tested; the packet classes only move the bytes.
 *
 * <p>The tuning payload carries gear stats as well as tuning and surface profiles: the client-side
 * slide controller reads them, so a datapack that retunes a board must reach clients too.
 */
public final class SyncCodec {
    /** Bumped whenever a packet layout or a synced data format changes incompatibly. */
    public static final int PROTOCOL_VERSION = 1;
    /** Inflation cap; a real payload is a few kilobytes. */
    public static final int MAX_INFLATED_BYTES = 1 << 20;

    private SyncCodec() {
    }

    public static byte[] deflate(JsonObject json) {
        byte[] raw = json.toString().getBytes(StandardCharsets.UTF_8);
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            deflater.setInput(raw);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, raw.length / 4));
            byte[] buffer = new byte[4096];
            while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer));
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    /** Inflates and parses; rejects anything that inflates past {@link #MAX_INFLATED_BYTES}. */
    public static JsonObject inflate(byte[] data) throws IOException {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(data);
            ByteArrayOutputStream out = new ByteArrayOutputStream(data.length * 4);
            byte[] buffer = new byte[4096];
            while (!inflater.finished()) {
                int n = inflater.inflate(buffer);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    throw new IOException("truncated payload");
                }
                out.write(buffer, 0, n);
                if (out.size() > MAX_INFLATED_BYTES) throw new IOException("payload too large");
            }
            JsonElement parsed = JsonParser.parseString(out.toString(StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) throw new IOException("payload is not a JSON object");
            return parsed.getAsJsonObject();
        } catch (DataFormatException | RuntimeException e) {
            throw new IOException("bad payload: " + e.getMessage(), e);
        } finally {
            inflater.end();
        }
    }

    // ---- tuning_sync ----------------------------------------------------------------------------

    public record Tuning(MovementTuning tuning, List<SurfaceProfile> surfaces, Map<String, GearStats> gear, List<BoxMaterial> boxMaterials) {
    }

    public static JsonObject tuningPayload(MovementTuning tuning, Collection<SurfaceProfile> surfaces, Map<String, GearStats> gear) {
        return tuningPayload(tuning, surfaces, gear, BoxMaterial.DEFAULTS);
    }

    public static JsonObject tuningPayload(MovementTuning tuning, Collection<SurfaceProfile> surfaces, Map<String, GearStats> gear,
                                           List<BoxMaterial> boxMaterials) {
        JsonObject json = new JsonObject();
        json.add("tuning", tuning.toJson());
        JsonObject s = new JsonObject();
        for (SurfaceProfile profile : surfaces) s.add(profile.id(), profile.toJson());
        json.add("surfaces", s);
        JsonObject g = new JsonObject();
        gear.forEach((id, stats) -> g.add(id, stats.toJson()));
        json.add("gear", g);
        json.add("box_materials", BoxMaterial.toJson(boxMaterials));
        return json;
    }

    public static Tuning readTuning(JsonObject json, Consumer<String> warn) {
        MovementTuning tuning = json.has("tuning") && json.get("tuning").isJsonObject()
                ? MovementTuning.fromJson(json.getAsJsonObject("tuning"), warn)
                : MovementTuning.DEFAULT;
        List<SurfaceProfile> surfaces = new ArrayList<>();
        if (json.has("surfaces") && json.get("surfaces").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("surfaces").entrySet()) {
                if (!e.getValue().isJsonObject()) continue;
                SurfaceProfile profile = SurfaceProfile.fromJson(e.getKey(), e.getValue().getAsJsonObject(), warn);
                if (profile != null) surfaces.add(profile);
            }
        }
        if (surfaces.isEmpty()) surfaces.addAll(SurfaceProfile.DEFAULTS.values());
        Map<String, GearStats> gear = new LinkedHashMap<>();
        if (json.has("gear") && json.get("gear").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("gear").entrySet()) {
                if (!e.getValue().isJsonObject()) continue;
                GearStats fallback = GearStats.DEFAULTS.getOrDefault(e.getKey(), GearStats.neutral(GearKind.BOARD));
                gear.put(e.getKey(), GearStats.fromJson(e.getKey(), e.getValue().getAsJsonObject(), fallback, warn));
            }
        }
        List<BoxMaterial> boxes = json.has("box_materials") && json.get("box_materials").isJsonObject()
                ? BoxMaterial.fromJson(json.getAsJsonObject("box_materials"), warn)
                : BoxMaterial.DEFAULTS;
        return new Tuning(tuning, surfaces, gear, boxes);
    }

    // ---- config_sync ----------------------------------------------------------------------------

    /**
     * The server-authoritative settings the client-side controller needs. The protocol version
     * travels outside the compressed JSON (a VarInt in front), so a client can always read it.
     *
     * @param ridingEnabled {@code movement.enabled} and no {@code movement.disableWithMods} mod present
     */
    public record Settings(boolean ridingEnabled, double maxSpeedMul, boolean scrapeEnabled) {
        /** What a client assumes before the server says otherwise. */
        public static final Settings DEFAULTS = new Settings(true, 1.0, true);

        public Settings {
            if (!(maxSpeedMul >= 0.1 && maxSpeedMul <= 3.0)) maxSpeedMul = Double.isNaN(maxSpeedMul) ? 1.0 : Math.max(0.1, Math.min(3.0, maxSpeedMul));
        }
    }

    public static JsonObject configPayload(Settings settings) {
        JsonObject json = new JsonObject();
        json.addProperty("movement.enabled", settings.ridingEnabled());
        json.addProperty("movement.maxSpeedMul", settings.maxSpeedMul());
        json.addProperty("movement.scrapeEnabled", settings.scrapeEnabled());
        return json;
    }

    /** Missing or malformed keys keep their defaults. */
    public static Settings readConfig(JsonObject json) {
        Settings d = Settings.DEFAULTS;
        return new Settings(
                bool(json, "movement.enabled", d.ridingEnabled()),
                number(json, "movement.maxSpeedMul", d.maxSpeedMul()),
                bool(json, "movement.scrapeEnabled", d.scrapeEnabled()));
    }

    private static boolean bool(JsonObject json, String key, boolean fallback) {
        try {
            return json.has(key) ? json.get(key).getAsBoolean() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static double number(JsonObject json, String key, double fallback) {
        try {
            return json.has(key) ? json.get(key).getAsDouble() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
