package io.github.adang101.alpineshred.design.paint;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The templates in use: on the server, the ones its data packs define; on a client, the ones the
 * server sent ({@code s2c/design_templates}), since the editor runs on the client but packs live
 * on the server. Each template is kept as its JSON text too, so it can be sent on.
 */
public final class DesignTemplates {
    private static volatile List<DesignTemplate> templates = List.of();
    private static volatile Map<String, String> sources = Map.of();

    private DesignTemplates() {
    }

    /** Parses {@code id -> JSON text}, skipping (and reporting) broken ones, and makes them the current set. */
    public static void set(Map<String, String> json, Consumer<String> warn) {
        Map<String, String> kept = new LinkedHashMap<>();
        List<DesignTemplate> parsed = new ArrayList<>();
        for (Map.Entry<String, String> e : json.entrySet()) {
            try {
                JsonObject obj = JsonParser.parseString(e.getValue()).getAsJsonObject();
                parsed.add(DesignTemplate.parse(e.getKey(), obj));
                kept.put(e.getKey(), e.getValue());
            } catch (RuntimeException ex) {
                warn.accept("Design template " + e.getKey() + " skipped: " + ex.getMessage());
            }
        }
        templates = Collections.unmodifiableList(DesignTemplate.sorted(parsed));
        sources = Collections.unmodifiableMap(kept);
    }

    /** In list order. */
    public static List<DesignTemplate> all() {
        return templates;
    }

    /** {@code id -> JSON text}, for the sync packet. */
    public static Map<String, String> sources() {
        return sources;
    }
}
