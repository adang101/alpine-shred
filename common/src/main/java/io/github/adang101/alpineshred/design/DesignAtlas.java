package io.github.adang101.alpineshred.design;

/**
 * The rendering atlas of a board or ski (design doc section 22, "Rendering"): the top face in rows
 * 0 to h - 1, the base in rows h to 2h - 1 and the edge strip in the 8 rows below, as ARGB. A
 * painted pixel is drawn as painted; a transparent one shows the gear's default look at that
 * pixel; outside the outline everything is alpha 0, so the cutout shader gives the gear its shape.
 */
public final class DesignAtlas {
    private DesignAtlas() {
    }

    /**
     * @param design   the design, or null for the default look alone
     * @param defaults the default texture in the same layout ({@code w * atlasHeight} ARGB), or null
     * @param core     the color under everything when there is no default texture
     */
    public static int[] build(DesignKind kind, Design design, int[] defaults, int core) {
        int w = kind.width, h = kind.height;
        int[] out = new int[w * kind.atlasHeight()];
        if (defaults != null && defaults.length != out.length) defaults = null;
        boolean[] mask = Outline.mask(kind);
        for (int face = 0; face < 2; face++) {
            for (int i = 0; i < kind.pixels(); i++) {
                int at = face * kind.pixels() + i;
                if (!mask[i]) continue;
                int painted = design == null ? 0 : design.pixel(face == 1, i % w, i / w);
                out[at] = painted != 0 ? painted : 0xFF000000 | (defaults != null ? defaults[at] : core);
            }
        }
        int edgeStart = 2 * kind.pixels();
        for (int i = edgeStart; i < out.length; i++) {
            out[i] = 0xFF000000 | (design != null ? design.edge() : defaults != null ? defaults[i] : core);
        }
        return out;
    }

    /** The default look's edge color: the first pixel of its edge strip (or {@code core}). */
    public static int defaultEdge(DesignKind kind, int[] defaults, int core) {
        if (defaults == null || defaults.length != kind.width * kind.atlasHeight()) return core & 0xFFFFFF;
        return defaults[2 * kind.pixels()] & 0xFFFFFF;
    }

    /** The default look's color at a face pixel (what a transparent pixel shows), for the editor. */
    public static int defaultPixel(DesignKind kind, int[] defaults, int core, boolean base, int x, int y) {
        if (defaults == null || defaults.length != kind.width * kind.atlasHeight()) return core & 0xFFFFFF;
        return defaults[(base ? kind.pixels() : 0) + y * kind.width + x] & 0xFFFFFF;
    }
}
