package io.github.adang101.alpineshred.design;

import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

/**
 * A painted design (design doc section 22): the top and base faces as ARGB pixels (alpha 0 or
 * 255; a transparent pixel shows the gear's own look), the edge color, the pair setting for skis,
 * a name and the author the server recorded. Immutable; faces are copied in and out. Transparent
 * pixels are kept as 0 and pixels outside the outline are always transparent, so equal designs
 * have equal bytes and equal hashes.
 */
public final class Design {
    public static final int VERSION = 1;
    public static final int NAME_MAX = 32;

    /** Skis only: both skis the same, or the left one mirrored across its length. */
    public enum Pair {
        SAME, MIRROR;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** The pair named {@code id}; anything else is {@code SAME}. */
        public static Pair byId(String id) {
            return "mirror".equals(id) ? MIRROR : SAME;
        }
    }

    private final DesignKind kind;
    private final int[] top;
    private final int[] base;
    private final int edge;
    private final Pair pair;
    private final String name;
    private final String author;
    private final UUID authorId;
    private final int hash;

    /**
     * Normalizes as it builds: alpha snapped (anything under 128 is transparent), transparent and
     * outside pixels cleared, the edge masked to 24 bits, the name sanitized. Faces must have
     * {@code kind.pixels()} entries. {@code author} and {@code authorId} may be empty and null.
     */
    public Design(DesignKind kind, int[] top, int[] base, int edge, Pair pair, String name, String author, UUID authorId) {
        if (top.length != kind.pixels() || base.length != kind.pixels()) {
            throw new IllegalArgumentException("face size " + top.length + "/" + base.length + " for " + kind.id);
        }
        this.kind = kind;
        this.top = normalize(kind, top);
        this.base = normalize(kind, base);
        this.edge = edge & 0xFFFFFF;
        this.pair = kind == DesignKind.SKI ? pair : Pair.SAME;
        this.name = DesignCodec.sanitizeName(name);
        this.author = author == null ? "" : author;
        this.authorId = authorId;
        this.hash = DesignCodec.hash(kind, DesignCodec.raw(this.top), DesignCodec.raw(this.base), this.edge);
    }

    /** An empty design: every pixel transparent, so the gear looks as it always did. */
    public static Design blank(DesignKind kind, int edge) {
        return new Design(kind, new int[kind.pixels()], new int[kind.pixels()], edge, Pair.SAME, "", "", null);
    }

    private static int[] normalize(DesignKind kind, int[] face) {
        boolean[] mask = Outline.mask(kind);
        int[] out = new int[face.length];
        for (int i = 0; i < face.length; i++) {
            int argb = face[i];
            out[i] = mask[i] && (argb >>> 24) >= 128 ? 0xFF000000 | (argb & 0xFFFFFF) : 0;
        }
        return out;
    }

    public DesignKind kind() {
        return kind;
    }

    public int[] top() {
        return top.clone();
    }

    public int[] base() {
        return base.clone();
    }

    /** Pixel {@code (x, y)} of the top ({@code base == false}) or base face. */
    public int pixel(boolean base, int x, int y) {
        return (base ? this.base : this.top)[y * kind.width + x];
    }

    public int edge() {
        return edge;
    }

    public Pair pair() {
        return pair;
    }

    public String name() {
        return name;
    }

    public String author() {
        return author;
    }

    /** The author's UUID, or null when none was recorded. */
    public UUID authorId() {
        return authorId;
    }

    /** CRC32 of the kind, the raw faces and the edge: the texture cache's key. */
    public int hash() {
        return hash;
    }

    public Design withName(String newName) {
        return new Design(kind, top, base, edge, pair, newName, author, authorId);
    }

    public Design withAuthor(String newAuthor, UUID newAuthorId) {
        return new Design(kind, top, base, edge, pair, name, newAuthor, newAuthorId);
    }

    /** True when no pixel is painted on either face. */
    public boolean isBlank() {
        for (int p : top) if (p != 0) return false;
        for (int p : base) if (p != 0) return false;
        return true;
    }

    /** Same pixels, edge, pair and name (the author is who saved it, not what it looks like). */
    public boolean sameArt(Design other) {
        return other != null && kind == other.kind && edge == other.edge && pair == other.pair && name.equals(other.name)
                && Arrays.equals(top, other.top) && Arrays.equals(base, other.base);
    }

    @Override
    public String toString() {
        return "Design[" + kind.id + " \"" + name + "\" by " + (author.isEmpty() ? "?" : author)
                + String.format(Locale.ROOT, " edge #%06x hash %08x]", edge, hash);
    }
}
