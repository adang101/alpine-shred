package io.github.adang101.alpineshred.design;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.adang101.alpineshred.design.paint.Canvas;
import io.github.adang101.alpineshred.design.paint.Colors;
import io.github.adang101.alpineshred.design.paint.DesignTemplate;
import io.github.adang101.alpineshred.design.paint.EditorState;
import io.github.adang101.alpineshred.design.paint.PaintTools;
import io.github.adang101.alpineshred.design.paint.UndoStack;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Section 22's editor unit tests: flood fill, gradients and dither, undo, mirror, patterns, templates, colors. */
class PaintToolsTest {
    private static final int RED = 0xFFFF0000, BLUE = 0xFF0000FF, WHITE = 0xFFFFFFFF;
    private static final PaintTools.Mirror NONE = PaintTools.Mirror.NONE;

    private static int count(Canvas c, int color) {
        int n = 0;
        for (int p : c.pixels()) if (p == color) n++;
        return n;
    }

    @Test
    void floodFillIsFourConnectedExactAndStopsAtTheOutline() {
        Canvas c = new Canvas(DesignKind.BOARD);
        PaintTools.fill(c, NONE, 32, 8, RED);
        assertEquals(Outline.count(DesignKind.BOARD), count(c, RED), "fills the whole outline and nothing outside");
        assertEquals(0, c.get(0, 0));
        // A wall of blue across the board: the fill stays on its side, diagonal gaps do not leak.
        c.clear();
        for (int y = 0; y < 16; y++) c.set(20 + (y % 2), y, BLUE);
        PaintTools.fill(c, NONE, 40, 8, RED);
        assertEquals(0, c.get(10, 8), "leaked through a diagonal wall");
        assertEquals(RED, c.get(40, 8));
        // Exact match: a different shade is a border.
        Canvas d = new Canvas(DesignKind.SKI);
        PaintTools.fill(d, NONE, 30, 4, 0xFF808080);
        d.set(31, 4, 0xFF808081);
        PaintTools.fill(d, NONE, 30, 4, RED);
        assertEquals(0xFF808081, d.get(31, 4));
        assertEquals(RED, d.get(32, 4));
    }

    @Test
    void gradientEndpointsAndMiddleStop() {
        Canvas c = new Canvas(DesignKind.BOARD);
        PaintTools.gradient(c, NONE, new PaintTools.Gradient(PaintTools.GradientType.LINEAR, 10, 8, 50, 8, RED, BLUE, -1, false, 0, false));
        assertEquals(RED, c.get(10, 8), "A is the primary");
        assertEquals(RED, c.get(5, 8), "before A clamps");
        assertEquals(BLUE, c.get(50, 8), "B is the secondary");
        assertEquals(BLUE, c.get(60, 8));
        assertEquals(PaintTools.lerp(RED, BLUE, 0.5), c.get(30, 8));
        PaintTools.gradient(c, NONE, new PaintTools.Gradient(PaintTools.GradientType.LINEAR, 10, 8, 50, 8, RED, BLUE, 0xFFFFFF, false, 0, false));
        assertEquals(WHITE, c.get(30, 8), "the middle stop");
        PaintTools.gradient(c, NONE, new PaintTools.Gradient(PaintTools.GradientType.RADIAL, 32, 8, 32, 0, RED, BLUE, -1, false, 0, false));
        assertEquals(RED, c.get(32, 8));
        assertEquals(BLUE, c.get(32 + 20, 8), "past the radius");
        PaintTools.gradient(c, NONE, new PaintTools.Gradient(PaintTools.GradientType.LINEAR, 0, 8, 63, 8, RED, BLUE, -1, false, 2, false));
        assertEquals(RED, c.get(31, 8), "two steps: a hard split");
        assertEquals(BLUE, c.get(33, 8));
    }

    @Test
    void ditherIsDeterministicAndUsesOnlyTheStops() {
        PaintTools.Gradient g = new PaintTools.Gradient(PaintTools.GradientType.LINEAR, 0, 0, 63, 0, RED, BLUE, -1, true, 0, false);
        Canvas a = new Canvas(DesignKind.BOARD), b = new Canvas(DesignKind.BOARD);
        PaintTools.gradient(a, NONE, g);
        PaintTools.gradient(b, NONE, g);
        assertArrayEquals(a.pixels(), b.pixels());
        int red = count(a, RED), blue = count(a, BLUE);
        assertEquals(Outline.count(DesignKind.BOARD), red + blue, "dither only uses the two colors");
        assertTrue(Math.abs(red - blue) < 120, "about half each: " + red + " / " + blue);
        // Left quarter mostly red, right quarter mostly blue.
        int leftRed = 0;
        for (int y = 4; y < 12; y++) for (int x = 8; x < 16; x++) if (a.get(x, y) == RED) leftRed++;
        assertTrue(leftRed > 48, "left is mostly the primary: " + leftRed);
    }

    @Test
    void lockTransparentOnlyRepaintsPaintedPixels() {
        Canvas c = new Canvas(DesignKind.BOARD);
        PaintTools.brush(c, NONE, 30, 8, 3, WHITE);
        PaintTools.pattern(c, NONE, PaintTools.Pattern.CHECKER, RED, BLUE, 1, true);
        assertEquals(9, count(c, RED) + count(c, BLUE));
        assertEquals(0, c.get(10, 8));
    }

    @Test
    void mirrorAppliesToEveryTool() {
        Canvas c = new Canvas(DesignKind.BOARD);
        PaintTools.Mirror both = new PaintTools.Mirror(true, true);
        PaintTools.brush(c, both, 12, 3, 1, RED);
        assertEquals(RED, c.get(12, 3));
        assertEquals(RED, c.get(63 - 12, 3));
        assertEquals(RED, c.get(12, 15 - 3));
        assertEquals(RED, c.get(63 - 12, 15 - 3));
        assertEquals(4, count(c, RED));
        c.clear();
        PaintTools.gradient(c, new PaintTools.Mirror(true, false),
                new PaintTools.Gradient(PaintTools.GradientType.LINEAR, 0, 8, 31, 8, RED, BLUE, -1, false, 0, false));
        for (int x = 0; x < 64; x++) assertEquals(c.get(x, 8), c.get(63 - x, 8), "symmetric at " + x);
        assertEquals(RED, c.get(0, 8));
        assertEquals(RED, c.get(63, 8));
    }

    @Test
    void linesRectanglesAndPatterns() {
        Canvas c = new Canvas(DesignKind.BOARD);
        PaintTools.line(c, NONE, 10, 2, 50, 12, 1, RED);
        assertEquals(RED, c.get(10, 2));
        assertEquals(RED, c.get(50, 12));
        assertEquals(41, count(c, RED), "one pixel per column on a shallow line");
        c.clear();
        PaintTools.rect(c, NONE, 10, 3, 20, 9, false, 1, BLUE);
        assertEquals(2 * 11 + 2 * 5, count(c, BLUE), "outline");
        PaintTools.rect(c, NONE, 10, 3, 20, 9, true, 1, BLUE);
        assertEquals(11 * 7, count(c, BLUE), "filled");
        c.clear();
        PaintTools.pattern(c, NONE, PaintTools.Pattern.STRIPES_HORIZONTAL, RED, BLUE, 2, false);
        assertEquals(RED, c.get(30, 0));
        assertEquals(RED, c.get(30, 1));
        assertEquals(BLUE, c.get(30, 2));
        PaintTools.pattern(c, NONE, PaintTools.Pattern.DOTS, RED, BLUE, 2, false);
        assertTrue(count(c, RED) > 150 && count(c, BLUE) > count(c, RED), "dots on a ground");
        assertEquals(PaintTools.Pattern.STRIPES_VERTICAL, PaintTools.Pattern.byId("stripes_vertical"));
    }

    @Test
    void undoStackKeeps32Steps() {
        UndoStack<Integer> u = new UndoStack<>();
        for (int i = 0; i < 40; i++) u.push(i);
        assertEquals(32, u.size());
        int current = 40;
        int undone = 0;
        Integer s;
        while ((s = u.undo(current)) != null) {
            current = s;
            undone++;
        }
        assertEquals(32, undone);
        assertEquals(8, current, "the oldest 8 steps were dropped");
        assertEquals(9, (int) u.redo(current));
        u.push(100);
        assertFalse(u.canRedo(), "a new stroke forgets redo");
    }

    @Test
    void anEditorStrokeIsOneUndoStepAndShapesPreview() {
        EditorState e = new EditorState(DesignKind.BOARD, 0x111111);
        e.setPrimary(0xFF0000);
        e.press(0, 10, 8);
        for (int x = 11; x <= 30; x++) e.drag(x, 8);
        e.release(30, 8);
        assertEquals(21, count(e.canvas(), RED));
        e.setTool(EditorState.Tool.LINE);
        e.setPrimary(0x0000FF);
        e.press(0, 10, 2);
        e.drag(40, 14);
        e.drag(20, 2);              // the preview moves; the first position leaves no trail
        e.release(20, 2);
        assertEquals(11, count(e.canvas(), BLUE));
        assertTrue(e.undo());
        assertEquals(0, count(e.canvas(), BLUE));
        assertEquals(21, count(e.canvas(), RED));
        assertTrue(e.undo());
        assertEquals(0, count(e.canvas(), RED));
        assertFalse(e.undo());
        assertTrue(e.redo());
        assertEquals(21, count(e.canvas(), RED));
        assertEquals(List.of(0x0000FF, 0xFF0000), e.recent(), "recent colors, newest first");
        // Right button paints the secondary; the eyedropper picks into either.
        e.setTool(EditorState.Tool.EYEDROPPER);
        e.press(1, 15, 8);
        assertEquals(0xFF0000, e.secondary());
        e.setTool(EditorState.Tool.ERASER);
        e.press(0, 15, 8);
        e.release(15, 8);
        assertEquals(0, e.canvas().get(15, 8));
        assertEquals(EditorState.Tool.GRADIENT, EditorState.toolForKey('g'));
    }

    @Test
    void facesCopyAndTheDesignRoundTrips() {
        EditorState e = new EditorState(DesignKind.SKI, 0x222222);
        e.press(0, 20, 4);
        e.release(20, 4);
        e.copyToOtherFace();
        assertEquals(e.canvas(0).get(20, 4), e.canvas(1).get(20, 4));
        e.togglePair();
        e.setName("  My skis§r ");
        Design d = e.toDesign();
        assertEquals("My skis", d.name());
        assertEquals(Design.Pair.MIRROR, d.pair());
        EditorState f = new EditorState(DesignKind.SKI, 0);
        f.load(d);
        assertTrue(f.toDesign().sameArt(d));
        assertFalse(f.canUndo(), "loading starts a fresh history");
        e.clearAll();
        assertTrue(e.toDesign().isBlank());
        assertTrue(e.undo());
        assertFalse(e.toDesign().isBlank());
    }

    @Test
    void everyBuiltInTemplateParsesAndPaints() throws Exception {
        Path dir = Path.of("common/src/main/resources/data/alpineshred/design_templates");
        if (!Files.isDirectory(dir)) dir = Path.of("src/main/resources/data/alpineshred/design_templates");
        List<DesignTemplate> all = new ArrayList<>();
        try (var files = Files.list(dir)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                JsonObject json = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
                String id = "alpineshred:" + p.getFileName().toString().replace(".json", "");
                all.add(DesignTemplate.parse(id, json));
            }
        }
        assertEquals(12, all.size(), "section 22: twelve starter templates");
        List<DesignTemplate> sorted = DesignTemplate.sorted(all);
        assertEquals("alpineshred:solid", sorted.get(0).id());
        for (DesignTemplate t : all) {
            for (DesignKind kind : DesignKind.values()) {
                Design d = t.design(kind, 0);
                int painted = 0;
                for (int p : d.top()) if (p != 0) painted++;
                assertEquals(Outline.count(kind), painted, t.id() + " covers the " + kind.id + " top");
                assertTrue(DesignCodec.topZ(d).length + DesignCodec.baseZ(d).length <= DesignCodec.DEFAULT_MAX_COMPRESSED, t.id());
            }
        }
        // Distinct looks.
        assertNotEquals(all.get(0).design(DesignKind.BOARD, 0).hash(), all.get(1).design(DesignKind.BOARD, 0).hash());
        // Deterministic camo.
        DesignTemplate camo = all.stream().filter(t -> t.id().endsWith(":camo")).findFirst().orElseThrow();
        assertArrayEquals(camo.paint(DesignKind.BOARD)[0], camo.paint(DesignKind.BOARD)[0]);
    }

    @Test
    void brokenTemplatesSayWhy() {
        JsonObject bad = JsonParser.parseString("{\"top\":[{\"op\":\"spray\"}]}").getAsJsonObject();
        try {
            DesignTemplate.parse("x:bad", bad);
            throw new AssertionError("parsed");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("spray"), e.getMessage());
        }
        JsonObject noColor = JsonParser.parseString("{\"top\":[{\"op\":\"fill\",\"color\":\"red\"}]}").getAsJsonObject();
        try {
            DesignTemplate.parse("x:bad", noColor);
            throw new AssertionError("parsed");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("color"), e.getMessage());
        }
    }

    @Test
    void colorPickerMath() {
        for (int rgb : new int[]{0xFF0000, 0x00FF00, 0x0000FF, 0x123456, 0xFEDCBA, 0x808080, 0x000000, 0xFFFFFF}) {
            double[] hsv = Colors.rgbToHsv(rgb);
            assertEquals(rgb, Colors.hsvToRgb(hsv[0], hsv[1], hsv[2]), Colors.hex(rgb));
        }
        assertEquals(0xFF0000, Colors.hsvToRgb(0, 1, 1));
        assertEquals(0x00FFFF, Colors.hsvToRgb(180, 1, 1));
        assertEquals(0xAABBCC, Colors.parseHex("#aabbcc"));
        assertEquals(0xAABBCC, Colors.parseHex("abc"));
        assertEquals(-1, Colors.parseHex("#12345"));
        assertEquals(-1, Colors.parseHex("zzzzzz"));
        assertEquals("#0a0b0c", Colors.hex(0x0A0B0C));
        assertEquals(32, Colors.palette().length);
        assertEquals(32, Arrays.stream(Colors.palette()).distinct().count());
        assertEquals(List.of(3, 1, 2), Colors.remember(List.of(1, 2, 3), 3, 8));
        assertNotNull(Colors.remember(List.of(), 5, 8));
    }
}
