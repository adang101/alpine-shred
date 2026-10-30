package io.github.adang101.alpineshred.design;

/**
 * What a design is painted for (design doc section 22, "Data format"): a board's faces are 64 by 16
 * pixels, a ski's 64 by 8. The rendering atlas stacks the top face, the base face and an 8-row
 * edge strip. Faces run tail to nose along x (x = 0 is the tail).
 */
public enum DesignKind {
    BOARD("board", 64, 16),
    SKI("ski", 64, 8);

    /** Rows of the edge strip under the two faces in the atlas. */
    public static final int EDGE_ROWS = 8;

    public final String id;
    public final int width;
    public final int height;

    DesignKind(String id, int width, int height) {
        this.id = id;
        this.width = width;
        this.height = height;
    }

    public int pixels() {
        return width * height;
    }

    /** Raw RGBA bytes of one face before compression. */
    public int faceBytes() {
        return pixels() * 4;
    }

    public int atlasHeight() {
        return height * 2 + EDGE_ROWS;
    }

    /** The kind with {@code id}, or null. */
    public static DesignKind byId(String id) {
        for (DesignKind k : values()) {
            if (k.id.equals(id)) return k;
        }
        return null;
    }
}
