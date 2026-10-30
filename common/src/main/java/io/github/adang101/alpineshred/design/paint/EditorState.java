package io.github.adang101.alpineshred.design.paint;

import io.github.adang101.alpineshred.design.Design;
import io.github.adang101.alpineshred.design.DesignKind;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The paint editor's working copy and tool state (design doc section 22, "Editor tools"), free of
 * any screen: the screen turns mouse and keys into {@link #press}, {@link #drag} and
 * {@link #release} on canvas pixels and the shortcut methods, and draws what is here. Each stroke
 * (press to release) is one undo step; line, rectangle and gradient preview while dragging.
 */
public final class EditorState {
    public enum Tool {
        PENCIL('B'), ERASER('E'), FILL('F'), LINE('L'), RECTANGLE('R'), GRADIENT('G'), PATTERN('P'), EYEDROPPER('I');

        /** The tool's shortcut key (section 22). */
        public final char key;

        Tool(char key) {
            this.key = key;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Both faces and the edge, as one undo step holds them. */
    record Snapshot(int[] top, int[] base, int edge) {
        boolean same(Snapshot o) {
            return edge == o.edge && Arrays.equals(top, o.top) && Arrays.equals(base, o.base);
        }
    }

    public static final int RECENT_COLORS = 8;
    public static final int MAX_PATTERN_SIZE = 8;

    public final DesignKind kind;
    private final Canvas[] faces;
    private int face;
    private int edge;
    private Design.Pair pair = Design.Pair.SAME;
    private String name = "";

    private Tool tool = Tool.PENCIL;
    private int primary = 0xE03030;
    private int secondary = 0xFFFFFF;
    private List<Integer> recent = new ArrayList<>();
    private int brush = 1;
    private boolean mirrorH, mirrorV, lockTransparent, rectFilled, dither, useMiddle, grid = true, zoom;
    private int middle = 0xFFFFFF;
    private PaintTools.GradientType gradientType = PaintTools.GradientType.LINEAR;
    private PaintTools.Pattern pattern = PaintTools.Pattern.CHECKER;
    private int patternSize = 2;

    private final UndoStack<Snapshot> undo = new UndoStack<>();
    private Snapshot strokeBefore;
    private int strokeButton, startX, startY, lastX, lastY;
    private boolean dirty;

    public EditorState(DesignKind kind, int edge) {
        this.kind = kind;
        this.faces = new Canvas[]{new Canvas(kind), new Canvas(kind)};
        this.edge = edge & 0xFFFFFF;
    }

    /** Starts from a saved design (or a blank one): the faces, edge, pair and name; history is cleared. */
    public void load(Design design) {
        faces[0].load(design.top());
        faces[1].load(design.base());
        edge = design.edge();
        pair = design.pair();
        name = design.name();
        undo.clear();
        strokeBefore = null;
        dirty = false;
    }

    /** The working copy as a design (no author: the server writes it). */
    public Design toDesign() {
        return new Design(kind, faces[0].pixels(), faces[1].pixels(), edge, pair, name, "", null);
    }

    // ---- strokes ----------------------------------------------------------------------------------

    /** Mouse down on canvas pixel (x, y) of the current face; {@code button} 0 paints the primary color, 1 the secondary. */
    public void press(int button, int x, int y) {
        if (strokeBefore != null) release(lastX, lastY);
        if (tool == Tool.EYEDROPPER) {
            pickInto(button, x, y);
            return;
        }
        strokeButton = button;
        startX = lastX = x;
        startY = lastY = y;
        strokeBefore = snapshot();
        Canvas c = canvas();
        switch (tool) {
            case PENCIL, ERASER -> PaintTools.brush(c, mirror(), x, y, brush, strokeColor());
            case FILL -> {
                PaintTools.fill(c, mirror(), x, y, strokeColor());
                finishStroke();
            }
            case PATTERN -> {
                PaintTools.pattern(c, mirror(), pattern, opaque(color(button)), opaque(color(1 - button)), patternSize, lockTransparent);
                finishStroke();
            }
            default -> drawShape(x, y);
        }
    }

    /** Mouse moved to (x, y) with the button held. */
    public void drag(int x, int y) {
        if (strokeBefore == null) return;
        switch (tool) {
            case PENCIL, ERASER -> PaintTools.line(canvas(), mirror(), lastX, lastY, x, y, brush, strokeColor());
            case LINE, RECTANGLE, GRADIENT -> {
                restore(strokeBefore);
                drawShape(x, y);
            }
            default -> {
            }
        }
        lastX = x;
        lastY = y;
    }

    /** Mouse up at (x, y): the stroke becomes one undo step (if it changed anything). */
    public void release(int x, int y) {
        if (strokeBefore == null) return;
        drag(x, y);
        finishStroke();
    }

    public boolean stroking() {
        return strokeBefore != null;
    }

    private void finishStroke() {
        Snapshot before = strokeBefore;
        strokeBefore = null;
        if (before == null || before.same(snapshot())) return;
        undo.push(before);
        dirty = true;
        if (tool != Tool.ERASER) remember(color(strokeButton));
    }

    private void drawShape(int x, int y) {
        Canvas c = canvas();
        switch (tool) {
            case LINE -> PaintTools.line(c, mirror(), startX, startY, x, y, brush, strokeColor());
            case RECTANGLE -> PaintTools.rect(c, mirror(), startX, startY, x, y, rectFilled, brush, strokeColor());
            case GRADIENT -> PaintTools.gradient(c, mirror(), new PaintTools.Gradient(gradientType, startX, startY, x, y,
                    opaque(color(strokeButton)), opaque(color(1 - strokeButton)), useMiddle ? middle : -1, dither, 0, lockTransparent));
            default -> {
            }
        }
    }

    private int strokeColor() {
        return tool == Tool.ERASER ? 0 : opaque(color(strokeButton));
    }

    private static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    /** Eyedropper (also middle click): the color under (x, y) into the primary (button 0) or secondary; a transparent pixel picks nothing. */
    public void pickInto(int button, int x, int y) {
        int c = PaintTools.pick(canvas(), x, y);
        if (c == 0) return;
        if (button == 1) secondary = c & 0xFFFFFF;
        else primary = c & 0xFFFFFF;
    }

    // ---- whole-design actions (each one undo step) -------------------------------------------------

    public boolean undo() {
        if (strokeBefore != null) return false;
        Snapshot s = undo.undo(snapshot());
        if (s == null) return false;
        restore(s);
        dirty = true;
        return true;
    }

    public boolean redo() {
        if (strokeBefore != null) return false;
        Snapshot s = undo.redo(snapshot());
        if (s == null) return false;
        restore(s);
        dirty = true;
        return true;
    }

    /**
     * Copy to other face: the other face becomes this one, as seen from its own side. Each face is
     * stored as seen from its own side with the nose to the right, so a straight copy reads the same
     * way on the base (on the gear it is mirrored across the length, as a flipped board is).
     */
    public void copyToOtherFace() {
        change(() -> faces[1 - face].load(faces[face].pixels()));
    }

    /** A template replaces both faces (and the edge, if it names one). */
    public void applyTemplate(DesignTemplate template) {
        change(() -> {
            int[][] painted = template.paint(kind);
            faces[0].load(painted[0]);
            faces[1].load(painted[1]);
            if (template.edge() >= 0) edge = template.edge();
        });
    }

    /** Reset: every pixel transparent, so the gear shows its own look again. */
    public void clearAll() {
        change(() -> {
            faces[0].clear();
            faces[1].clear();
        });
    }

    /** Pastes a design (from a share code) over the working copy, keeping the history. */
    public void paste(Design design) {
        change(() -> {
            faces[0].load(design.top());
            faces[1].load(design.base());
            edge = design.edge();
            pair = design.pair();
            if (!design.name().isEmpty()) name = design.name();
        });
    }

    public void setEdge(int rgb) {
        change(() -> edge = rgb & 0xFFFFFF);
    }

    private void change(Runnable action) {
        if (strokeBefore != null) release(lastX, lastY);
        Snapshot before = snapshot();
        action.run();
        if (!before.same(snapshot())) {
            undo.push(before);
            dirty = true;
        }
    }

    private Snapshot snapshot() {
        return new Snapshot(faces[0].pixels(), faces[1].pixels(), edge);
    }

    private void restore(Snapshot s) {
        faces[0].load(s.top());
        faces[1].load(s.base());
        edge = s.edge();
    }

    // ---- shared controls ----------------------------------------------------------------------------

    public void swapColors() {
        int t = primary;
        primary = secondary;
        secondary = t;
    }

    public void brushBigger() {
        brush = Math.min(PaintTools.MAX_BRUSH, brush + 1);
    }

    public void brushSmaller() {
        brush = Math.max(PaintTools.MIN_BRUSH, brush - 1);
    }

    public void switchFace() {
        if (strokeBefore != null) release(lastX, lastY);
        face = 1 - face;
    }

    public void toggleGrid() {
        grid = !grid;
    }

    /** The tool for a shortcut key ({@code B E F L R G P I}), or null. */
    public static Tool toolForKey(char key) {
        char k = Character.toUpperCase(key);
        for (Tool t : Tool.values()) {
            if (t.key == k) return t;
        }
        return null;
    }

    private void remember(int rgb) {
        recent = Colors.remember(recent, rgb, RECENT_COLORS);
    }

    // ---- getters and setters --------------------------------------------------------------------------

    public Canvas canvas() {
        return faces[face];
    }

    public Canvas canvas(int which) {
        return faces[which];
    }

    /** 0 for the top (front) face, 1 for the base. */
    public int face() {
        return face;
    }

    public void setFace(int which) {
        if (strokeBefore != null) release(lastX, lastY);
        face = which == 1 ? 1 : 0;
    }

    public Tool tool() {
        return tool;
    }

    public void setTool(Tool t) {
        if (strokeBefore != null) release(lastX, lastY);
        tool = t;
    }

    public int color(int button) {
        return button == 1 ? secondary : primary;
    }

    public int primary() {
        return primary;
    }

    public int secondary() {
        return secondary;
    }

    public void setPrimary(int rgb) {
        primary = rgb & 0xFFFFFF;
    }

    public void setSecondary(int rgb) {
        secondary = rgb & 0xFFFFFF;
    }

    /** The colors used most recently, newest first. */
    public List<Integer> recent() {
        return List.copyOf(recent);
    }

    public int brush() {
        return brush;
    }

    public PaintTools.Mirror mirror() {
        return new PaintTools.Mirror(mirrorH, mirrorV);
    }

    public boolean mirrorH() {
        return mirrorH;
    }

    public boolean mirrorV() {
        return mirrorV;
    }

    public void toggleMirrorH() {
        mirrorH = !mirrorH;
    }

    public void toggleMirrorV() {
        mirrorV = !mirrorV;
    }

    public boolean lockTransparent() {
        return lockTransparent;
    }

    public void toggleLockTransparent() {
        lockTransparent = !lockTransparent;
    }

    public boolean rectFilled() {
        return rectFilled;
    }

    public void toggleRectFilled() {
        rectFilled = !rectFilled;
    }

    public boolean dither() {
        return dither;
    }

    public void toggleDither() {
        dither = !dither;
    }

    public boolean useMiddle() {
        return useMiddle;
    }

    public void toggleMiddle() {
        useMiddle = !useMiddle;
    }

    public int middle() {
        return middle;
    }

    public void setMiddle(int rgb) {
        middle = rgb & 0xFFFFFF;
    }

    public PaintTools.GradientType gradientType() {
        return gradientType;
    }

    public void toggleGradientType() {
        gradientType = gradientType == PaintTools.GradientType.LINEAR ? PaintTools.GradientType.RADIAL : PaintTools.GradientType.LINEAR;
    }

    public PaintTools.Pattern pattern() {
        return pattern;
    }

    public void nextPattern() {
        PaintTools.Pattern[] all = PaintTools.Pattern.values();
        pattern = all[(pattern.ordinal() + 1) % all.length];
    }

    public int patternSize() {
        return patternSize;
    }

    public void setPatternSize(int size) {
        patternSize = Math.max(1, Math.min(MAX_PATTERN_SIZE, size));
    }

    public boolean grid() {
        return grid;
    }

    /** The 12x canvas option instead of 8x. */
    public boolean zoom() {
        return zoom;
    }

    public void toggleZoom() {
        zoom = !zoom;
    }

    public int edge() {
        return edge;
    }

    public Design.Pair pair() {
        return pair;
    }

    public void togglePair() {
        if (kind == DesignKind.SKI) pair = pair == Design.Pair.SAME ? Design.Pair.MIRROR : Design.Pair.SAME;
    }

    public String name() {
        return name;
    }

    public void setName(String newName) {
        name = newName == null ? "" : newName;
    }

    public boolean canUndo() {
        return undo.canUndo();
    }

    public boolean canRedo() {
        return undo.canRedo();
    }

    /** True once anything was painted since the design was loaded. */
    public boolean dirty() {
        return dirty;
    }
}
