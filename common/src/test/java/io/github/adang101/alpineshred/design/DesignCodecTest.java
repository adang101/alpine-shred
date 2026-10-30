package io.github.adang101.alpineshred.design;

import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Section 22's unit tests: the codec, its rejections, the outline, share codes and the hash. */
class DesignCodecTest {
    private static final int MAX = DesignCodec.DEFAULT_MAX_COMPRESSED;

    /** A design with a few colors in blocks (compresses like real art) and some transparency. */
    static Design sample(DesignKind kind, long seed) {
        Random r = new Random(seed);
        int[] top = new int[kind.pixels()], base = new int[kind.pixels()];
        int[] colors = {0xFFE03030, 0xFF3050E0, 0xFFF0F0F0, 0xFF202020, 0};
        for (int i = 0; i < top.length; i++) {
            int x = i % kind.width, y = i / kind.width;
            top[i] = colors[(x / 8 + y / 4 + (int) (seed % 3)) % colors.length];
            base[i] = r.nextInt(6) == 0 ? colors[r.nextInt(colors.length)] : colors[(x / 16) % 4];
        }
        return new Design(kind, top, base, 0x123456, kind == DesignKind.SKI ? Design.Pair.MIRROR : Design.Pair.SAME,
                "Sample " + seed, "", null);
    }

    @Test
    void roundTripsBoardsAndSkis() throws Exception {
        for (DesignKind kind : DesignKind.values()) {
            for (long seed = 0; seed < 5; seed++) {
                Design d = sample(kind, seed);
                byte[] top = DesignCodec.topZ(d), base = DesignCodec.baseZ(d);
                assertTrue(top.length + base.length < 3000, "compresses: " + (top.length + base.length));
                Design back = DesignCodec.decode(kind, top, base, d.edge(), d.pair(), d.name(), "Alex", UUID.randomUUID(), MAX);
                assertTrue(d.sameArt(back), kind + " " + seed);
                assertEquals(d.hash(), back.hash());
                assertEquals("Alex", back.author());
            }
        }
    }

    @Test
    void wrongDimensionsAreRejected() {
        Design ski = sample(DesignKind.SKI, 1);
        DesignCodec.InvalidDesign e = assertThrows(DesignCodec.InvalidDesign.class, () -> DesignCodec.decode(DesignKind.BOARD,
                DesignCodec.topZ(ski), DesignCodec.baseZ(ski), 0, Design.Pair.SAME, "", "", null, MAX));
        assertEquals(DesignStatus.INVALID, e.status);
        // A board face decoded as a ski face has bytes left over.
        Design board = sample(DesignKind.BOARD, 1);
        e = assertThrows(DesignCodec.InvalidDesign.class, () -> DesignCodec.decode(DesignKind.SKI,
                DesignCodec.topZ(board), DesignCodec.baseZ(board), 0, Design.Pair.SAME, "", "", null, MAX));
        assertEquals(DesignStatus.TOO_LARGE, e.status);
    }

    @Test
    void partialAlphaIsRejected() {
        byte[] raw = DesignCodec.raw(sample(DesignKind.BOARD, 2).top());
        raw[3 + 4 * 200] = (byte) 128;
        DesignCodec.InvalidDesign e = assertThrows(DesignCodec.InvalidDesign.class,
                () -> DesignCodec.decode(DesignKind.BOARD, DesignCodec.deflate(raw), DesignCodec.baseZ(sample(DesignKind.BOARD, 2)),
                        0, Design.Pair.SAME, "", "", null, MAX));
        assertEquals(DesignStatus.INVALID, e.status);
    }

    @Test
    void oversizeDesignsAreRejected() {
        // Stored (uncompressed) deflate blocks: two board faces are just over 8 KB.
        byte[] raw = DesignCodec.raw(sample(DesignKind.BOARD, 5).top());
        java.util.zip.Deflater stored = new java.util.zip.Deflater(java.util.zip.Deflater.NO_COMPRESSION);
        stored.setInput(raw);
        stored.finish();
        byte[] buf = new byte[8192];
        int n = stored.deflate(buf);
        stored.end();
        byte[] z = java.util.Arrays.copyOf(buf, n);
        assertTrue(z.length * 2 > MAX, "stored face is " + z.length);
        DesignCodec.InvalidDesign e = assertThrows(DesignCodec.InvalidDesign.class,
                () -> DesignCodec.decode(DesignKind.BOARD, z, z, 0, Design.Pair.SAME, "", "", null, MAX));
        assertEquals(DesignStatus.TOO_LARGE, e.status);
    }

    @Test
    void decompressionBombsStopAtTheFaceSize() {
        byte[] bomb = DesignCodec.deflate(new byte[4 * 1024 * 1024]);
        byte[] base = DesignCodec.baseZ(sample(DesignKind.BOARD, 1));
        assertTrue(bomb.length + base.length < MAX, "a bomb fits the cap: " + bomb.length);
        long start = System.nanoTime();
        DesignCodec.InvalidDesign e = assertThrows(DesignCodec.InvalidDesign.class,
                () -> DesignCodec.decode(DesignKind.BOARD, bomb, base, 0, Design.Pair.SAME, "", "", null, MAX));
        assertEquals(DesignStatus.TOO_LARGE, e.status);
        assertTrue(System.nanoTime() - start < 200_000_000L, "inflation was not cut short");
        assertThrows(DesignCodec.InvalidDesign.class, () -> DesignCodec.inflate(new byte[]{1, 2, 3, 4, 5}, 4096));
        byte[] shortFace = DesignCodec.deflate(new byte[100]);
        assertEquals(DesignStatus.INVALID, assertThrows(DesignCodec.InvalidDesign.class, () -> DesignCodec.inflate(shortFace, 4096)).status);
    }

    @Test
    void pixelsOutsideTheOutlineAreForcedTransparent() throws Exception {
        for (DesignKind kind : DesignKind.values()) {
            int[] full = new int[kind.pixels()];
            java.util.Arrays.fill(full, 0xFFFF00FF);
            Design d = DesignCodec.decode(kind, DesignCodec.deflate(DesignCodec.raw(full)), DesignCodec.deflate(DesignCodec.raw(full)),
                    0xFF123456, Design.Pair.SAME, "", "", null, MAX);
            assertEquals(0, d.pixel(false, 0, 0), kind + " corner");
            assertEquals(0, d.pixel(true, kind.width - 1, 0), kind + " nose corner");
            assertEquals(0xFFFF00FF, d.pixel(false, kind.width / 2, kind.height / 2));
            assertEquals(0x123456, d.edge(), "edge masked to 24 bits");
            int painted = 0;
            for (int p : d.top()) if (p != 0) painted++;
            assertEquals(Outline.count(kind), painted);
        }
        assertFalse(Outline.inside(DesignKind.BOARD, 0, 0));
        assertTrue(Outline.inside(DesignKind.BOARD, 0, 8));
        assertFalse(Outline.inside(DesignKind.SKI, 63, 0), "ski tip is rounded");
        assertTrue(Outline.inside(DesignKind.SKI, 63, 4));
    }

    @Test
    void namesAreSanitized() {
        assertEquals("Red name", DesignCodec.sanitizeName("  \u00a7cRed\u0007 name\n "));
        assertEquals("ab", DesignCodec.sanitizeName("a\u200bb\u00a7c")); // zero-width (format) dropped, a code and its letter dropped
        String longName = "x".repeat(50);
        assertEquals(32, DesignCodec.sanitizeName(longName).length());
        assertEquals("", DesignCodec.sanitizeName(null));
        assertEquals(32, new Design(DesignKind.BOARD, new int[1024], new int[1024], 0, Design.Pair.SAME, longName, "", null).name().length());
    }

    @Test
    void shareCodesRoundTripAndCheckTheKind() throws Exception {
        for (DesignKind kind : DesignKind.values()) {
            Design d = sample(kind, 3);
            String code = ShareCode.encode(d);
            assertTrue(code.startsWith("AS1:") && code.length() < 4000, code.length() + " characters");
            assertTrue(code.substring(4).matches("[A-Za-z0-9_-]+"), "base64url");
            Design back = ShareCode.decode(code, kind, MAX);
            assertTrue(d.sameArt(back), kind.id);
            DesignKind other = kind == DesignKind.BOARD ? DesignKind.SKI : DesignKind.BOARD;
            assertThrows(DesignCodec.InvalidDesign.class, () -> ShareCode.decode(code, other, MAX));
        }
        assertThrows(DesignCodec.InvalidDesign.class, () -> ShareCode.decode("hello", DesignKind.BOARD, MAX));
        assertThrows(DesignCodec.InvalidDesign.class, () -> ShareCode.decode("AS1:!!!", DesignKind.BOARD, MAX));
        assertThrows(DesignCodec.InvalidDesign.class, () -> ShareCode.decode("AS1:AAAA", DesignKind.BOARD, MAX));
        String good = ShareCode.encode(sample(DesignKind.BOARD, 4));
        assertThrows(DesignCodec.InvalidDesign.class, () -> ShareCode.decode(good.substring(0, good.length() - 10), DesignKind.BOARD, MAX));
    }

    @Test
    void theHashIsStableAndIgnoresTheNameAndAuthor() {
        Design d = sample(DesignKind.BOARD, 0);
        assertEquals(d.hash(), d.withName("Other").withAuthor("Steve", UUID.randomUUID()).hash());
        assertEquals(d.hash(), sample(DesignKind.BOARD, 0).hash());
        assertNotEquals(d.hash(), sample(DesignKind.BOARD, 1).hash());
        // Pinned: a change to the hash would make every cached texture key and stored hash stale.
        Design blank = Design.blank(DesignKind.SKI, 0xABCDEF);
        assertEquals(0xABCDEF, blank.edge());
        assertEquals(DesignCodec.hash(DesignKind.SKI, new byte[2048], new byte[2048], 0xABCDEF), blank.hash());
        assertEquals(0x81c80490, DesignCodec.hash(DesignKind.BOARD, new byte[4096], new byte[4096], 0));
    }

    @Test
    void theAtlasShowsTheDefaultsThroughAndCutsTheOutline() {
        DesignKind kind = DesignKind.BOARD;
        int[] defaults = new int[kind.width * kind.atlasHeight()];
        java.util.Arrays.fill(defaults, 0xFF00AA00);
        int[] top = new int[kind.pixels()];
        top[8 * 64 + 32] = 0xFFFF0000;
        Design d = new Design(kind, top, new int[kind.pixels()], 0x0000FF, Design.Pair.SAME, "", "", null);
        int[] atlas = DesignAtlas.build(kind, d, defaults, 0);
        assertEquals(0xFFFF0000, atlas[8 * 64 + 32], "painted");
        assertEquals(0xFF00AA00, atlas[8 * 64 + 31], "transparent shows the default");
        assertEquals(0, atlas[0], "outside the outline");
        assertEquals(0, atlas[kind.pixels()], "base corner");
        assertEquals(0xFF0000FF, atlas[2 * kind.pixels() + 5], "edge strip");
        int[] plain = DesignAtlas.build(kind, null, null, 0x808080);
        assertEquals(0xFF808080, plain[8 * 64]);
        assertEquals(0x00AA00, DesignAtlas.defaultEdge(kind, defaults, 0));
    }
}
