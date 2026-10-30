package io.github.adang101.alpineshred.design.paint;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.adang101.alpineshred.design.Design;
import io.github.adang101.alpineshred.design.DesignKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A starter template (design doc section 22, "Color and templates"): a data file in
 * {@code data/<namespace>/design_templates/}, so packs can add more. A template is a list of
 * painting operations per face in face fractions (0 to 1 along each side), so one template fits
 * boards and skis alike:
 *
 * <pre>
 * {
 *   "name": "Racing stripe",            shown when the lang file has no design_template.ns.id
 *   "order": 10,                        optional: lower comes first in the list (default 1000)
 *   "edge": "#202020",                  optional
 *   "top":  [ op, ... ],
 *   "base": [ op, ... ] or "top"        "top" paints the base the same way
 * }
 * op: {"op": "fill", "color": "#rrggbb"}
 *     {"op": "rect", "from": [x, y], "to": [x, y], "color": ...}
 *     {"op": "line", "from": [x, y], "to": [x, y], "color": ..., "size": 1}
 *     {"op": "pattern", "pattern": "checker" | "stripes_horizontal" | "stripes_vertical" | "stripes_diagonal" | "dots",
 *      "primary": ..., "secondary": ..., "size": 2}
 *     {"op": "gradient", "type": "linear" | "radial", "from": [x, y], "to": [x, y], "primary": ..., "secondary": ...,
 *      "middle": ... (optional), "dither": false, "steps": 0}
 *     {"op": "camo", "colors": [base, ...], "seed": 1, "blobs": 24}
 * </pre>
 */
public final class DesignTemplate {
    private final String id;
    private final String name;
    private final int order;
    private final int edge;
    private final JsonArray top;
    private final JsonArray base;

    private DesignTemplate(String id, String name, int order, int edge, JsonArray top, JsonArray base) {
        this.id = id;
        this.name = name;
        this.order = order;
        this.edge = edge;
        this.top = top;
        this.base = base;
    }

    /** Parses and checks a template by painting it once on each kind; throws with the reason if it is broken. */
    public static DesignTemplate parse(String id, JsonObject json) {
        String name = json.has("name") ? json.get("name").getAsString() : id;
        int order = json.has("order") ? json.get("order").getAsInt() : 1000;
        int edge = json.has("edge") ? color(json.get("edge")) : -1;
        JsonArray top = json.getAsJsonArray("top");
        if (top == null) throw new IllegalArgumentException("no \"top\" operations");
        JsonElement b = json.get("base");
        JsonArray base = b == null ? new JsonArray() : b.isJsonPrimitive() && "top".equals(b.getAsString()) ? top : b.getAsJsonArray();
        DesignTemplate t = new DesignTemplate(id, name, order, edge, top, base);
        for (DesignKind kind : DesignKind.values()) t.paint(kind);
        return t;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    /** The template's edge color, or -1 to keep the current one. */
    public int edge() {
        return edge;
    }

    /** {top, base} faces for {@code kind}. */
    public int[][] paint(DesignKind kind) {
        Canvas t = new Canvas(kind), b = new Canvas(kind);
        run(t, top);
        run(b, base);
        return new int[][]{t.pixels(), b.pixels()};
    }

    /** The template as a design for {@code kind}, with {@code fallbackEdge} when it names no edge. */
    public Design design(DesignKind kind, int fallbackEdge) {
        int[][] faces = paint(kind);
        return new Design(kind, faces[0], faces[1], edge >= 0 ? edge : fallbackEdge, Design.Pair.SAME, "", "", null);
    }

    private static void run(Canvas c, JsonArray ops) {
        for (JsonElement e : ops) {
            JsonObject op = e.getAsJsonObject();
            String kind = op.has("op") ? op.get("op").getAsString() : "";
            switch (kind) {
                case "fill" -> {
                    int color = opaque(op, "color");
                    for (int y = 0; y < c.height; y++) for (int x = 0; x < c.width; x++) c.set(x, y, color);
                }
                case "rect" -> {
                    int[] a = point(c, op, "from"), b = point(c, op, "to");
                    PaintTools.rect(c, PaintTools.Mirror.NONE, a[0], a[1], b[0], b[1], true, 1, opaque(op, "color"));
                }
                case "line" -> {
                    int[] a = point(c, op, "from"), b = point(c, op, "to");
                    int size = op.has("size") ? op.get("size").getAsInt() : 1;
                    PaintTools.line(c, PaintTools.Mirror.NONE, a[0], a[1], b[0], b[1], size, opaque(op, "color"));
                }
                case "pattern" -> PaintTools.pattern(c, PaintTools.Mirror.NONE,
                        PaintTools.Pattern.byId(op.has("pattern") ? op.get("pattern").getAsString() : "checker"),
                        opaque(op, "primary"), opaque(op, "secondary"), op.has("size") ? op.get("size").getAsInt() : 2, false);
                case "gradient" -> {
                    int[] a = point(c, op, "from"), b = point(c, op, "to");
                    PaintTools.gradient(c, PaintTools.Mirror.NONE, new PaintTools.Gradient(
                            PaintTools.GradientType.byId(op.has("type") ? op.get("type").getAsString() : "linear"),
                            a[0], a[1], b[0], b[1], opaque(op, "primary"), opaque(op, "secondary"),
                            op.has("middle") ? color(op.get("middle")) : -1,
                            op.has("dither") && op.get("dither").getAsBoolean(), op.has("steps") ? op.get("steps").getAsInt() : 0, false));
                }
                case "camo" -> camo(c, op);
                default -> throw new IllegalArgumentException("unknown op \"" + kind + "\"");
            }
        }
    }

    /** Blobs of each color after the first over the first, placed by a seeded random (the same everywhere). */
    private static void camo(Canvas c, JsonObject op) {
        JsonArray colors = op.getAsJsonArray("colors");
        if (colors == null || colors.size() < 2) throw new IllegalArgumentException("camo needs two or more colors");
        Random random = new Random(op.has("seed") ? op.get("seed").getAsLong() : 1);
        int blobs = op.has("blobs") ? op.get("blobs").getAsInt() : 24;
        int ground = 0xFF000000 | color(colors.get(0));
        for (int y = 0; y < c.height; y++) for (int x = 0; x < c.width; x++) c.set(x, y, ground);
        for (int i = 1; i < colors.size(); i++) {
            int color = 0xFF000000 | color(colors.get(i));
            for (int n = 0; n < blobs; n++) {
                double cx = random.nextDouble() * c.width, cy = random.nextDouble() * c.height;
                double rx = 2 + random.nextDouble() * 4, ry = 1 + random.nextDouble() * Math.max(1, c.height / 5.0);
                for (int y = (int) (cy - ry) - 1; y <= cy + ry + 1; y++) {
                    for (int x = (int) (cx - rx) - 1; x <= cx + rx + 1; x++) {
                        double dx = (x + 0.5 - cx) / rx, dy = (y + 0.5 - cy) / ry;
                        if (dx * dx + dy * dy <= 1) c.set(x, y, color);
                    }
                }
            }
        }
    }

    private static int[] point(Canvas c, JsonObject op, String key) {
        JsonArray a = op.getAsJsonArray(key);
        if (a == null || a.size() != 2) throw new IllegalArgumentException("\"" + key + "\" must be [x, y]");
        double fx = a.get(0).getAsDouble(), fy = a.get(1).getAsDouble();
        return new int[]{(int) Math.round(fx * (c.width - 1)), (int) Math.round(fy * (c.height - 1))};
    }

    private static int opaque(JsonObject op, String key) {
        if (!op.has(key)) throw new IllegalArgumentException("missing \"" + key + "\"");
        return 0xFF000000 | color(op.get(key));
    }

    private static int color(JsonElement e) {
        int c = Colors.parseHex(e.getAsString());
        if (c < 0) throw new IllegalArgumentException("not a color: " + e);
        return c;
    }

    /** Every template in {@code all} in list order: by {@code order}, then id. */
    public static List<DesignTemplate> sorted(List<DesignTemplate> all) {
        List<DesignTemplate> out = new ArrayList<>(all);
        out.sort((a, b) -> a.order != b.order ? Integer.compare(a.order, b.order) : a.id.compareTo(b.id));
        return out;
    }
}
