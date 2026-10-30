package io.github.adang101.alpineshred.design;

/**
 * The gear's outline on a face (section 22): pixels outside it are locked in the editor, forced
 * transparent on save, and cut away by the renderer's cutout. A board has rounded nose and tail
 * corners; a ski has a long rounded tip at the nose (x high) and small rounded tail corners.
 * Each corner is a quarter ellipse with semi-axes {@code rx} along the length and {@code ry} across.
 */
public final class Outline {
    private static final boolean[] BOARD = build(DesignKind.BOARD, 10, 7, 10, 7);
    private static final boolean[] SKI = build(DesignKind.SKI, 3, 2, 12, 4);

    private Outline() {
    }

    public static boolean inside(DesignKind kind, int x, int y) {
        if (x < 0 || y < 0 || x >= kind.width || y >= kind.height) return false;
        return mask(kind)[y * kind.width + x];
    }

    /** The mask, row by row; do not modify. */
    public static boolean[] mask(DesignKind kind) {
        return kind == DesignKind.BOARD ? BOARD : SKI;
    }

    /** Pixels inside the outline. */
    public static int count(DesignKind kind) {
        int n = 0;
        for (boolean b : mask(kind)) if (b) n++;
        return n;
    }

    private static boolean[] build(DesignKind kind, double tailRx, double tailRy, double noseRx, double noseRy) {
        int w = kind.width, h = kind.height;
        boolean[] out = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double px = x + 0.5, py = y + 0.5;
                out[y * w + x] = corner(px, py, tailRx, tailRy, h) && corner(w - px, py, noseRx, noseRy, h);
            }
        }
        return out;
    }

    /** Inside the end whose tip is at distance 0: {@code d} is the distance from that tip along the length. */
    private static boolean corner(double d, double py, double rx, double ry, int h) {
        if (d >= rx) return true;
        double cy;
        if (py < ry) {
            cy = ry;
        } else if (py > h - ry) {
            cy = h - ry;
        } else {
            return true;
        }
        double ex = (d - rx) / rx, ey = (py - cy) / ry;
        return ex * ex + ey * ey <= 1.0;
    }
}
