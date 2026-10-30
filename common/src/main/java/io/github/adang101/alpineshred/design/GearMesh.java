package io.github.adang101.alpineshred.design;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The procedural geometry of boards and skis (design doc section 22, "Rendering"), free of any
 * renderer so it can be tested and previewed anywhere: each deck is a run of thin sections (tail
 * kick, flat middle, nose kick) with a top quad and a base quad per section, textured from the
 * design atlas, and edge walls that follow the outline mask pixel by pixel (so they match the
 * cutout of the faces). Bindings are boxes textured from the item's model sheet.
 *
 * <p>Positions are item model pixels (16 to a block), as the JSON models before were laid out: the
 * board's middle runs z 0 to 16 at x 6 to 10 with its deck at y 8.25; skis run z -5.5 to 22.5 at x 5
 * to 7 and 9 to 11. The nose (canvas x high) points +z. Seen from above with the nose to the right,
 * the top canvas's first row is at +x; seen from below, the base canvas's first row is at -x, so
 * each face reads the right way from its own side.
 */
public final class GearMesh {
    /** A quad: four corners (x, y, z, u, v) in order, and its normal. */
    public record Quad(float[] corners, float nx, float ny, float nz) {
        public float x(int i) {
            return corners[i * 5];
        }

        public float y(int i) {
            return corners[i * 5 + 1];
        }

        public float z(int i) {
            return corners[i * 5 + 2];
        }

        public float u(int i) {
            return corners[i * 5 + 3];
        }

        public float v(int i) {
            return corners[i * 5 + 4];
        }
    }

    /** The deck quads (atlas texture) and the binding quads (model sheet texture). */
    public record Mesh(List<Quad> deck, List<Quad> bindings) {
    }

    public static final float BASE_Y = 7.75f;
    public static final float THICK = 0.5f;
    private static final float[] BOARD_Z = {-3.75f, 0f, 16f, 19.75f};
    private static final float[] BOARD_RISE = {1.0f, 0f, 0f, 1.0f};
    private static final float[] BOARD_X = {6f, 10f};
    private static final float[] SKI_Z = {-5.5f, -4f, 19f, 22.5f};
    private static final float[] SKI_RISE = {0.2f, 0f, 0f, 1.1f};
    private static final float[][] SKI_X = {{5f, 7f}, {9f, 11f}};

    private static final Mesh BOARD = build(DesignKind.BOARD, false);
    private static final Mesh SKIS = build(DesignKind.SKI, false);
    private static final Mesh SKIS_MIRRORED = build(DesignKind.SKI, true);

    private GearMesh() {
    }

    /** The mesh for {@code kind}; for skis, {@code mirrorPair} flips the rows of the left ski (x 9 to 11). */
    public static Mesh of(DesignKind kind, boolean mirrorPair) {
        if (kind == DesignKind.BOARD) return BOARD;
        return mirrorPair ? SKIS_MIRRORED : SKIS;
    }

    private static Mesh build(DesignKind kind, boolean mirrorPair) {
        List<Quad> deck = new ArrayList<>();
        List<Quad> bindings = new ArrayList<>();
        float top = BASE_Y + THICK;
        if (kind == DesignKind.BOARD) {
            deck(deck, kind, BOARD_X[0], BOARD_X[1], BOARD_Z, BOARD_RISE, false);
            box(bindings, 6.4f, top, 3.5f, 9.6f, 9.6f, 6f);
            box(bindings, 6.4f, top, 10f, 9.6f, 9.6f, 12.5f);
        } else {
            deck(deck, kind, SKI_X[0][0], SKI_X[0][1], SKI_Z, SKI_RISE, false);
            deck(deck, kind, SKI_X[1][0], SKI_X[1][1], SKI_Z, SKI_RISE, mirrorPair);
            for (float[] x : SKI_X) box(bindings, x[0] + 0.2f, top, 6f, x[1] - 0.2f, 9.25f, 10.5f);
        }
        return new Mesh(Collections.unmodifiableList(deck), Collections.unmodifiableList(bindings));
    }

    /** One deck between x0 and x1; {@code mirrorRows} flips the rows of both faces. */
    private static void deck(List<Quad> out, DesignKind kind, float x0, float x1, float[] zs, float[] rise, boolean mirrorRows) {
        int w = kind.width, h = kind.height;
        float atlasH = kind.atlasHeight();
        float length = zs[zs.length - 1] - zs[0];
        float vTop0 = (mirrorRows ? h : 0) / atlasH, vTop1 = (mirrorRows ? 0 : h) / atlasH;
        float vBase0 = (mirrorRows ? 2 * h : h) / atlasH, vBase1 = (mirrorRows ? h : 2 * h) / atlasH;
        for (int s = 0; s + 1 < zs.length; s++) {
            float za = zs[s], zb = zs[s + 1];
            float ya = BASE_Y + rise[s], yb = BASE_Y + rise[s + 1];
            float ua = (za - zs[0]) / length, ub = (zb - zs[0]) / length;
            float dz = zb - za, dy = yb - ya, len = (float) Math.sqrt(dz * dz + dy * dy);
            float ny = dz / len, nz = -dy / len;
            out.add(quad(x1, ya + THICK, za, ua, vTop0, x1, yb + THICK, zb, ub, vTop0,
                    x0, yb + THICK, zb, ub, vTop1, x0, ya + THICK, za, ua, vTop1, 0, ny, nz));
            out.add(quad(x0, ya, za, ua, vBase0, x0, yb, zb, ub, vBase0,
                    x1, yb, zb, ub, vBase1, x1, ya, za, ua, vBase1, 0, -ny, -nz));
        }
        boolean[] mask = Outline.mask(kind);
        float edgeV = (2 * h + DesignKind.EDGE_ROWS / 2f) / atlasH;
        float colW = length / w, rowW = (x1 - x0) / h;
        int[] before = null;
        for (int col = 0; col <= w; col++) {
            int[] span = col < w ? span(mask, w, h, col) : null;
            float z = zs[0] + col * colW;
            float y = BASE_Y + riseAt(zs, rise, z);
            float u = (Math.min(col, w - 1) + 0.5f) / w;
            if (span != null) {
                float zEnd = z + colW, yEnd = BASE_Y + riseAt(zs, rise, zEnd);
                out.add(wall(x1 - span[0] * rowW, z, y, zEnd, yEnd, u, edgeV, 1));
                out.add(wall(x1 - (span[1] + 1) * rowW, z, y, zEnd, yEnd, u, edgeV, -1));
            }
            // Where the span changes between columns, and at the two ends, walls across the deck.
            if (before == null && span != null) {
                out.add(cross(x1 - (span[1] + 1) * rowW, x1 - span[0] * rowW, z, y, u, edgeV, -1));
            } else if (before != null && span == null) {
                out.add(cross(x1 - (before[1] + 1) * rowW, x1 - before[0] * rowW, z, y, u, edgeV, 1));
            } else if (before != null) {
                float hiB = x1 - before[0] * rowW, hiA = x1 - span[0] * rowW;
                float loB = x1 - (before[1] + 1) * rowW, loA = x1 - (span[1] + 1) * rowW;
                if (hiB != hiA) out.add(cross(Math.min(hiB, hiA), Math.max(hiB, hiA), z, y, u, edgeV, hiA < hiB ? 1 : -1));
                if (loB != loA) out.add(cross(Math.min(loB, loA), Math.max(loB, loA), z, y, u, edgeV, loA > loB ? 1 : -1));
            }
            before = span;
        }
    }

    /** First and last row inside the outline in a column, or null if none. */
    static int[] span(boolean[] mask, int w, int h, int col) {
        int lo = -1, hi = -1;
        for (int row = 0; row < h; row++) {
            if (mask[row * w + col]) {
                if (lo < 0) lo = row;
                hi = row;
            }
        }
        return lo < 0 ? null : new int[]{lo, hi};
    }

    /** The base's rise at z, linear between stations. */
    static float riseAt(float[] zs, float[] rise, float z) {
        if (z <= zs[0]) return rise[0];
        for (int i = 0; i + 1 < zs.length; i++) {
            if (z <= zs[i + 1]) return rise[i] + (rise[i + 1] - rise[i]) * (z - zs[i]) / (zs[i + 1] - zs[i]);
        }
        return rise[rise.length - 1];
    }

    private static Quad wall(float x, float za, float ya, float zb, float yb, float u, float v, int side) {
        return quad(x, ya, za, u, v, x, ya + THICK, za, u, v, x, yb + THICK, zb, u, v, x, yb, zb, u, v, side, 0, 0);
    }

    private static Quad cross(float x0, float x1, float z, float y, float u, float v, int side) {
        return quad(x0, y, z, u, v, x0, y + THICK, z, u, v, x1, y + THICK, z, u, v, x1, y, z, u, v, 0, 0, side);
    }

    /** A box with the model sheet's binding square (8 to 12 of 16, both ways) on every face. */
    private static void box(List<Quad> out, float x0, float y0, float z0, float x1, float y1, float z1) {
        float u0 = 0.5f, u1 = 0.75f, v0 = 0.5f, v1 = 0.75f;
        out.add(quad(x0, y1, z0, u0, v0, x0, y1, z1, u0, v1, x1, y1, z1, u1, v1, x1, y1, z0, u1, v0, 0, 1, 0));
        out.add(quad(x0, y0, z0, u0, v0, x1, y0, z0, u1, v0, x1, y0, z1, u1, v1, x0, y0, z1, u0, v1, 0, -1, 0));
        out.add(quad(x0, y0, z0, u0, v1, x0, y1, z0, u0, v0, x1, y1, z0, u1, v0, x1, y0, z0, u1, v1, 0, 0, -1));
        out.add(quad(x0, y0, z1, u0, v1, x1, y0, z1, u1, v1, x1, y1, z1, u1, v0, x0, y1, z1, u0, v0, 0, 0, 1));
        out.add(quad(x0, y0, z0, u0, v1, x0, y0, z1, u1, v1, x0, y1, z1, u1, v0, x0, y1, z0, u0, v0, -1, 0, 0));
        out.add(quad(x1, y0, z0, u0, v1, x1, y1, z0, u0, v0, x1, y1, z1, u1, v0, x1, y0, z1, u1, v1, 1, 0, 0));
    }

    private static Quad quad(float x0, float y0, float z0, float u0, float v0,
                             float x1, float y1, float z1, float u1, float v1,
                             float x2, float y2, float z2, float u2, float v2,
                             float x3, float y3, float z3, float u3, float v3,
                             float nx, float ny, float nz) {
        return new Quad(new float[]{x0, y0, z0, u0, v0, x1, y1, z1, u1, v1, x2, y2, z2, u2, v2, x3, y3, z3, u3, v3}, nx, ny, nz);
    }
}
