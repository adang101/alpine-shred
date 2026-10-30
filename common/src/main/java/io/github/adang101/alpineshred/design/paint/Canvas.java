package io.github.adang101.alpineshred.design.paint;

import io.github.adang101.alpineshred.design.DesignKind;
import io.github.adang101.alpineshred.design.Outline;

/**
 * One face being painted: ARGB pixels (0 is transparent) on the kind's grid. Pixels outside the
 * gear's outline are locked: nothing paints them.
 */
public final class Canvas {
    public final DesignKind kind;
    public final int width;
    public final int height;
    private final int[] pixels;
    private final boolean[] mask;

    public Canvas(DesignKind kind) {
        this.kind = kind;
        this.width = kind.width;
        this.height = kind.height;
        this.pixels = new int[kind.pixels()];
        this.mask = Outline.mask(kind);
    }

    public Canvas(DesignKind kind, int[] pixels) {
        this(kind);
        load(pixels);
    }

    public boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    /** Inside the outline: paintable. */
    public boolean editable(int x, int y) {
        return inBounds(x, y) && mask[y * width + x];
    }

    /** The pixel, or 0 outside the grid. */
    public int get(int x, int y) {
        return inBounds(x, y) ? pixels[y * width + x] : 0;
    }

    /** Paints one pixel if it is inside the outline: opaque colors are forced to full alpha, anything else clears. */
    public void set(int x, int y, int argb) {
        if (!editable(x, y)) return;
        pixels[y * width + x] = (argb >>> 24) >= 128 ? 0xFF000000 | (argb & 0xFFFFFF) : 0;
    }

    public boolean opaque(int x, int y) {
        return get(x, y) != 0;
    }

    public int[] pixels() {
        return pixels.clone();
    }

    /** Replaces every pixel (outside the outline stays clear). */
    public void load(int[] source) {
        if (source.length != pixels.length) throw new IllegalArgumentException("face of " + source.length + " pixels");
        for (int i = 0; i < pixels.length; i++) {
            int p = source[i];
            pixels[i] = mask[i] && (p >>> 24) >= 128 ? 0xFF000000 | (p & 0xFFFFFF) : 0;
        }
    }

    public void clear() {
        java.util.Arrays.fill(pixels, 0);
    }

    public Canvas copy() {
        return new Canvas(kind, pixels);
    }
}
