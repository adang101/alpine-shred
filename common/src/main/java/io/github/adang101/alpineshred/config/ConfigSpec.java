package io.github.adang101.alpineshred.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * A JSON config file described in code (design doc section 18): typed entries with defaults and
 * ranges, nested by dotted path, a {@code schemaVersion}, missing keys filled with defaults, bad
 * values clamped and logged. Gson only, so one code path serves both loaders with no
 * config-library dependency.
 */
public final class ConfigSpec {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final String fileName;
    private final int schemaVersion;
    private final List<Entry<?>> entries = new ArrayList<>();

    public ConfigSpec(String fileName, int schemaVersion) {
        this.fileName = fileName;
        this.schemaVersion = schemaVersion;
    }

    public String fileName() {
        return fileName;
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public List<Entry<?>> entries() {
        return Collections.unmodifiableList(entries);
    }

    // ---- entry factories ----------------------------------------------------------------------

    public BoolEntry bool(String path, boolean def) {
        return add(new BoolEntry(path, def));
    }

    public IntEntry integer(String path, int def, int min, int max) {
        return add(new IntEntry(path, def, min, max));
    }

    public DoubleEntry decimal(String path, double def, double min, double max) {
        return add(new DoubleEntry(path, def, min, max));
    }

    public ChoiceEntry choice(String path, String def, String... allowed) {
        return add(new ChoiceEntry(path, def, List.of(allowed)));
    }

    public StringListEntry strings(String path, List<String> def) {
        return add(new StringListEntry(path, def));
    }

    public DoubleListEntry decimals(String path, List<Double> def, double min, double max, int minSize, int maxSize) {
        return add(new DoubleListEntry(path, def, min, max, minSize, maxSize));
    }

    private <E extends Entry<?>> E add(E entry) {
        for (Entry<?> existing : entries) {
            if (existing.path.equals(entry.path)) throw new IllegalStateException("Duplicate config path " + entry.path);
        }
        entries.add(entry);
        return entry;
    }

    // ---- load and save ------------------------------------------------------------------------

    /**
     * Loads {@code dir/fileName}, filling defaults and rewriting the normalized file. A file with
     * a newer schema is read but never overwritten; an unreadable file is backed up to
     * {@code .bak} and replaced with defaults.
     */
    public void load(Path dir, Consumer<String> warn) {
        Path file = dir.resolve(fileName);
        JsonObject root = new JsonObject();
        boolean writeBack = true;
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (parsed != null && parsed.isJsonObject()) {
                    root = parsed.getAsJsonObject();
                } else {
                    throw new IOException("top level is not a JSON object");
                }
            } catch (IOException | RuntimeException e) {
                warn.accept(fileName + ": could not be read (" + e.getMessage() + "); backing it up and using defaults");
                backup(file, warn);
                root = new JsonObject();
            }
        }
        int fileSchema = root.has("schemaVersion") ? safeInt(root.get("schemaVersion"), schemaVersion) : schemaVersion;
        if (fileSchema > schemaVersion) {
            warn.accept(fileName + ": schemaVersion " + fileSchema + " is newer than this mod's " + schemaVersion
                    + "; reading known keys and leaving the file untouched");
            writeBack = false;
        }
        readFrom(root, warn);
        if (writeBack) save(dir, warn);
    }

    public void save(Path dir, Consumer<String> warn) {
        Path file = dir.resolve(fileName);
        try {
            Files.createDirectories(dir);
            Path tmp = dir.resolve(fileName + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(toJson(), writer);
                writer.write(System.lineSeparator());
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            warn.accept(fileName + ": could not be written (" + e.getMessage() + ")");
        }
    }

    private void backup(Path file, Consumer<String> warn) {
        try {
            Files.move(file, file.resolveSibling(fileName + ".bak"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            warn.accept(fileName + ": backup failed (" + e.getMessage() + ")");
        }
    }

    /** Reads every entry from a (possibly partial) nested object. Unknown keys are reported. */
    public void readFrom(JsonObject root, Consumer<String> warn) {
        for (Entry<?> entry : entries) {
            JsonElement element = find(root, entry.path);
            entry.read(element, msg -> warn.accept(fileName + ": " + msg));
        }
        reportUnknown(root, "", warn);
    }

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", schemaVersion);
        for (Entry<?> entry : entries) {
            put(root, entry.path, entry.writeCurrent());
        }
        return root;
    }

    public void resetToDefaults() {
        entries.forEach(Entry::reset);
    }

    private void reportUnknown(JsonObject object, String prefix, Consumer<String> warn) {
        for (String key : object.keySet()) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            if (path.equals("schemaVersion") || key.startsWith("_")) continue;
            JsonElement value = object.get(key);
            boolean isGroup = entries.stream().anyMatch(e -> e.path.startsWith(path + "."));
            boolean isEntry = entries.stream().anyMatch(e -> e.path.equals(path));
            if (isGroup && value.isJsonObject()) {
                reportUnknown(value.getAsJsonObject(), path, warn);
            } else if (!isEntry) {
                warn.accept(fileName + ": unknown key '" + path + "' ignored");
            }
        }
    }

    private static JsonElement find(JsonObject root, String path) {
        JsonElement current = root;
        for (String part : path.split("\\.")) {
            if (current == null || !current.isJsonObject()) return null;
            current = current.getAsJsonObject().get(part);
        }
        return current;
    }

    private static void put(JsonObject root, String path, JsonElement value) {
        String[] parts = path.split("\\.");
        JsonObject current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            JsonElement next = current.get(parts[i]);
            if (next == null || !next.isJsonObject()) {
                next = new JsonObject();
                current.add(parts[i], next);
            }
            current = next.getAsJsonObject();
        }
        current.add(parts[parts.length - 1], value);
    }

    private static int safeInt(JsonElement e, int fallback) {
        try {
            return e.getAsInt();
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    // ---- entries --------------------------------------------------------------------------------

    public abstract static class Entry<T> {
        public final String path;
        public final T defaultValue;
        private volatile T value;

        Entry(String path, T defaultValue) {
            this.path = path;
            this.defaultValue = defaultValue;
            this.value = defaultValue;
        }

        public T get() {
            return value;
        }

        /** For commands and tests; normal changes go through the file. */
        public void set(T newValue) {
            this.value = newValue;
        }

        void reset() {
            value = defaultValue;
        }

        void read(JsonElement element, Consumer<String> warn) {
            if (element == null || element.isJsonNull()) {
                value = defaultValue;
                return;
            }
            try {
                value = parse(element, warn);
            } catch (RuntimeException e) {
                warn.accept("'" + path + "' has an invalid value (" + element + "), using default " + defaultValue);
                value = defaultValue;
            }
        }

        JsonElement writeCurrent() {
            return write(value);
        }

        abstract T parse(JsonElement element, Consumer<String> warn);

        abstract JsonElement write(T v);
    }

    public static final class BoolEntry extends Entry<Boolean> {
        BoolEntry(String path, boolean def) {
            super(path, def);
        }

        public boolean on() {
            return get();
        }

        @Override
        Boolean parse(JsonElement e, Consumer<String> warn) {
            JsonPrimitive p = e.getAsJsonPrimitive();
            if (p.isBoolean()) return p.getAsBoolean();
            if (p.isString()) {
                String s = p.getAsString().trim().toLowerCase(Locale.ROOT);
                if (s.equals("true") || s.equals("false")) return Boolean.parseBoolean(s);
            }
            throw new IllegalArgumentException("not a boolean");
        }

        @Override
        JsonElement write(Boolean v) {
            return new JsonPrimitive(v);
        }
    }

    public static final class IntEntry extends Entry<Integer> {
        public final int min, max;

        IntEntry(String path, int def, int min, int max) {
            super(path, def);
            this.min = min;
            this.max = max;
        }

        public int value() {
            return get();
        }

        @Override
        Integer parse(JsonElement e, Consumer<String> warn) {
            double d = e.getAsJsonPrimitive().getAsDouble();
            if (!Double.isFinite(d)) throw new IllegalArgumentException("not finite");
            long rounded = Math.round(d);
            if (rounded != d) warn.accept("'" + path + "' = " + d + " is not a whole number, using " + rounded);
            if (rounded < min || rounded > max) {
                long clamped = Math.max(min, Math.min(max, rounded));
                warn.accept("'" + path + "' = " + rounded + " is outside [" + min + ", " + max + "], clamped to " + clamped);
                rounded = clamped;
            }
            return (int) rounded;
        }

        @Override
        JsonElement write(Integer v) {
            return new JsonPrimitive(v);
        }
    }

    public static final class DoubleEntry extends Entry<Double> {
        public final double min, max;

        DoubleEntry(String path, double def, double min, double max) {
            super(path, def);
            this.min = min;
            this.max = max;
        }

        public double value() {
            return get();
        }

        @Override
        Double parse(JsonElement e, Consumer<String> warn) {
            double d = e.getAsJsonPrimitive().getAsDouble();
            if (!Double.isFinite(d)) throw new IllegalArgumentException("not finite");
            if (d < min || d > max) {
                double clamped = Math.max(min, Math.min(max, d));
                warn.accept("'" + path + "' = " + d + " is outside [" + min + ", " + max + "], clamped to " + clamped);
                d = clamped;
            }
            return d;
        }

        @Override
        JsonElement write(Double v) {
            return new JsonPrimitive(v);
        }
    }

    public static final class ChoiceEntry extends Entry<String> {
        public final List<String> allowed;

        ChoiceEntry(String path, String def, List<String> allowed) {
            super(path, def);
            this.allowed = allowed;
            if (!allowed.contains(def)) throw new IllegalArgumentException(path + ": default not allowed");
        }

        public boolean is(String choice) {
            return get().equals(choice);
        }

        @Override
        String parse(JsonElement e, Consumer<String> warn) {
            String s = e.getAsJsonPrimitive().getAsString().trim().toLowerCase(Locale.ROOT);
            if (!allowed.contains(s)) {
                warn.accept("'" + path + "' = '" + s + "' is not one of " + allowed + ", using " + defaultValue);
                return defaultValue;
            }
            return s;
        }

        @Override
        JsonElement write(String v) {
            return new JsonPrimitive(v);
        }
    }

    public static final class StringListEntry extends Entry<List<String>> {
        StringListEntry(String path, List<String> def) {
            super(path, List.copyOf(def));
        }

        @Override
        List<String> parse(JsonElement e, Consumer<String> warn) {
            List<String> out = new ArrayList<>();
            if (e.isJsonArray()) {
                for (JsonElement item : e.getAsJsonArray()) out.add(item.getAsString().trim());
            } else {
                out.add(e.getAsString().trim());
            }
            out.removeIf(String::isEmpty);
            return List.copyOf(out);
        }

        @Override
        JsonElement write(List<String> v) {
            JsonArray array = new JsonArray();
            v.forEach(array::add);
            return array;
        }
    }

    public static final class DoubleListEntry extends Entry<List<Double>> {
        public final double min, max;
        public final int minSize, maxSize;

        DoubleListEntry(String path, List<Double> def, double min, double max, int minSize, int maxSize) {
            super(path, List.copyOf(def));
            this.min = min;
            this.max = max;
            this.minSize = minSize;
            this.maxSize = maxSize;
        }

        @Override
        List<Double> parse(JsonElement e, Consumer<String> warn) {
            List<Double> out = new ArrayList<>();
            for (JsonElement item : e.getAsJsonArray()) {
                double d = item.getAsDouble();
                if (!Double.isFinite(d)) continue;
                double clamped = Math.max(min, Math.min(max, d));
                if (clamped != d) warn.accept("'" + path + "' entry " + d + " clamped to " + clamped);
                out.add(clamped);
            }
            if (out.size() < minSize || out.size() > maxSize) {
                warn.accept("'" + path + "' needs " + minSize + " to " + maxSize + " values, using default " + defaultValue);
                return defaultValue;
            }
            return List.copyOf(out);
        }

        @Override
        JsonElement write(List<Double> v) {
            JsonArray array = new JsonArray();
            v.forEach(array::add);
            return array;
        }
    }
}
