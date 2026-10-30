package io.github.adang101.alpineshred.design.paint;

import java.util.ArrayDeque;
import java.util.Locale;

/**
 * The editor's tools (design doc section 22, "Editor tools") as pure operations on a
 * {@link Canvas}, so the screen, the templates and the tests share them. Every tool honours the
 * mirror toggles; none paints outside the outline.
 */
public final class PaintTools {
    private PaintTools() {
    }

    /** Horizontal (left-right) and vertical (top-bottom) symmetry. */
    public record Mirror(boolean horizontal, boolean vertical) {
        public static final Mirror NONE = new Mirror(false, false);

        /** Runs {@code plot} for {@code (x, y)} and each mirror image of it. */
        void each(Canvas c, int x, int y, Plot plot) {
            int mx = c.width - 1 - x, my = c.height - 1 - y;
            plot.at(x, y);
            if (horizontal && mx != x) plot.at(mx, y);
            if (vertical && my != y) plot.at(x, my);
            if (horizontal && vertical && mx != x && my != y) plot.at(mx, my);
        }

        /**
         * For tools that cover the face (gradients, patterns): the point whose value a pixel takes, so
         * the half holding {@code (sx, sy)} is computed and the other half mirrors it.
         */
        int sourceX(Canvas c, int x, int sx) {
            if (!horizontal) return x;
            boolean sourceLeft = sx < c.width / 2.0;
            boolean left = x < c.width / 2.0;
            return left == sourceLeft ? x : c.width - 1 - x;
        }

        int sourceY(Canvas c, int y, int sy) {
            if (!vertical) return y;
            boolean sourceTop = sy < c.height / 2.0;
            boolean top = y < c.height / 2.0;
            return top == sourceTop ? y : c.height - 1 - y;
        }
    }

    interface Plot {
        void at(int x, int y);
    }

    // ---- pencil, eraser, line, rectangle --------------------------------------------------------

    /** Brush sizes 1 to 3: a square of that many pixels around (x, y). */
    public static final int MIN_BRUSH = 1;
    public static final int MAX_BRUSH = 3;

    /** Pencil (and, with color 0, the eraser): a square brush of {@code size} at (x, y). */
    public static void brush(Canvas c, Mirror m, int x, int y, int size, int color) {
        int s = Math.max(MIN_BRUSH, Math.min(MAX_BRUSH, size));
        int from = -(s - 1) / 2, to = s / 2;
        for (int dy = from; dy <= to; dy++) {
            for (int dx = from; dx <= to; dx++) {
                m.each(c, x + dx, y + dy, (px, py) -> c.set(px, py, color));
            }
        }
    }

    /** A straight line from (x0, y0) to (x1, y1) with the brush. */
    public static void line(Canvas c, Mirror m, int x0, int y0, int x1, int y1, int size, int color) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        int x = x0, y = y0;
        while (true) {
            brush(c, m, x, y, size, color);
            if (x == x1 && y == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y += sy;
            }
        }
    }

    /** A rectangle between two corners: its outline (brush wide) or filled. */
    public static void rect(Canvas c, Mirror m, int x0, int y0, int x1, int y1, boolean filled, int size, int color) {
        int ax = Math.min(x0, x1), bx = Math.max(x0, x1), ay = Math.min(y0, y1), by = Math.max(y0, y1);
        if (filled) {
            for (int y = ay; y <= by; y++) {
                for (int x = ax; x <= bx; x++) m.each(c, x, y, (px, py) -> c.set(px, py, color));
            }
            return;
        }
        line(c, m, ax, ay, bx, ay, size, color);
        line(c, m, bx, ay, bx, by, size, color);
        line(c, m, bx, by, ax, by, size, color);
        line(c, m, ax, by, ax, ay, size, color);
    }

    // ---- fill and eyedropper --------------------------------------------------------------------

    /** Flood fill: 4-connected, exact color match, stopping at the outline. */
    public static void fill(Canvas c, Mirror m, int x, int y, int color) {
        m.each(c, x, y, (sx, sy) -> flood(c, sx, sy, color));
    }

    private static void flood(Canvas c, int x, int y, int color) {
        if (!c.editable(x, y)) return;
        int target = c.get(x, y);
        int replacement = (color >>> 24) >= 128 ? 0xFF000000 | (color & 0xFFFFFF) : 0;
        if (target == replacement) return;
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{x, y});
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            int px = p[0], py = p[1];
            if (!c.editable(px, py) || c.get(px, py) != target) continue;
            c.set(px, py, replacement);
            queue.add(new int[]{px + 1, py});
            queue.add(new int[]{px - 1, py});
            queue.add(new int[]{px, py + 1});
            queue.add(new int[]{px, py - 1});
        }
    }

    /** Eyedropper: the color under (x, y), or 0 where it is transparent. */
    public static int pick(Canvas c, int x, int y) {
        return c.get(x, y);
    }

    // ---- gradients --------------------------------------------------------------------------------

    public enum GradientType {
        LINEAR, RADIAL;

        public static GradientType byId(String id) {
            return "radial".equalsIgnoreCase(id) ? RADIAL : LINEAR;
        }
    }

    /**
     * A gradient from A to B, primary to secondary through an optional middle stop (-1 for none).
     * {@code dither} replaces the blend with the 4 by 4 Bayer pattern between the two nearest
     * stops; {@code steps} of 2 or more posterizes into that many bands (2 is a hard split).
     */
    public record Gradient(GradientType type, int ax, int ay, int bx, int by, int primary, int secondary, int middle,
                           boolean dither, int steps, boolean lockTransparent) {
    }

    /** The 4 by 4 Bayer matrix, values 0 to 15. */
    private static final int[][] BAYER = {{0, 8, 2, 10}, {12, 4, 14, 6}, {3, 11, 1, 9}, {15, 7, 13, 5}};

    public static void gradient(Canvas c, Mirror m, Gradient g) {
        double vx = g.bx() - g.ax(), vy = g.by() - g.ay();
        double len2 = vx * vx + vy * vy;
        for (int y = 0; y < c.height; y++) {
            for (int x = 0; x < c.width; x++) {
                if (!c.editable(x, y) || (g.lockTransparent() && !c.opaque(x, y))) continue;
                int sx = m.sourceX(c, x, g.ax()), sy = m.sourceY(c, y, g.ay());
                double t;
                if (len2 < 1.0e-9) {
                    t = 0;
                } else if (g.type() == GradientType.RADIAL) {
                    t = Math.sqrt(((sx - g.ax()) * (sx - g.ax()) + (sy - g.ay()) * (sy - g.ay())) / len2);
                } else {
                    t = ((sx - g.ax()) * vx + (sy - g.ay()) * vy) / len2;
                }
                t = Math.max(0, Math.min(1, t));
                c.set(x, y, gradientColor(g, t, x, y));
            }
        }
    }

    /** The color at {@code t} (0 at A, 1 at B) for pixel (x, y). */
    static int gradientColor(Gradient g, double t, int x, int y) {
        if (g.steps() >= 2) t = Math.min(g.steps() - 1, Math.floor(t * g.steps())) / (g.steps() - 1);
        int c0, c1;
        double local;
        if (g.middle() >= 0) {
            if (t < 0.5) {
                c0 = g.primary();
                c1 = g.middle();
                local = t * 2;
            } else {
                c0 = g.middle();
                c1 = g.secondary();
                local = t * 2 - 1;
            }
        } else {
            c0 = g.primary();
            c1 = g.secondary();
            local = t;
        }
        if (g.dither()) {
            double threshold = (BAYER[Math.floorMod(y, 4)][Math.floorMod(x, 4)] + 0.5) / 16.0;
            return 0xFF000000 | (local > threshold ? c1 : c0);
        }
        return lerp(c0, c1, local);
    }

    /** Channel-wise blend of two colors, opaque. */
    public static int lerp(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int gr = (int) Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | r << 16 | gr << 8 | bl;
    }

    // ---- patterns -----------------------------------------------------------------------------------

    public enum Pattern {
        CHECKER, STRIPES_HORIZONTAL, STRIPES_VERTICAL, STRIPES_DIAGONAL, DOTS;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Pattern byId(String id) {
            for (Pattern p : values()) {
                if (p.id().equals(id)) return p;
            }
            return switch (id) {
                case "stripes", "stripes_h" -> STRIPES_HORIZONTAL;
                case "stripes_v" -> STRIPES_VERTICAL;
                case "diagonal", "stripes_d" -> STRIPES_DIAGONAL;
                default -> CHECKER;
            };
        }
    }

    /** Pattern fill across the face (only painted pixels when {@code lockTransparent}), cells of {@code size}. */
    public static void pattern(Canvas c, Mirror m, Pattern pattern, int primary, int secondary, int size, boolean lockTransparent) {
        int s = Math.max(1, size);
        for (int y = 0; y < c.height; y++) {
            for (int x = 0; x < c.width; x++) {
                if (!c.editable(x, y) || (lockTransparent && !c.opaque(x, y))) continue;
                int px = m.sourceX(c, x, 0), py = m.sourceY(c, y, 0);
                c.set(x, y, patternAt(pattern, px, py, s) ? primary : secondary);
            }
        }
    }

    /** True where the pattern shows the primary color. */
    static boolean patternAt(Pattern pattern, int x, int y, int s) {
        return switch (pattern) {
            case CHECKER -> ((x / s) + (y / s)) % 2 == 0;
            case STRIPES_HORIZONTAL -> (y / s) % 2 == 0;
            case STRIPES_VERTICAL -> (x / s) % 2 == 0;
            case STRIPES_DIAGONAL -> ((x + y) / s) % 2 == 0;
            case DOTS -> {
                int cell = 2 * s;
                int row = y / cell;
                int ox = (x + (row % 2) * s) % cell;
                yield ox < s && y % cell < s;
            }
        };
    }
}
