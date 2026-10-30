package io.github.adang101.alpineshred.client.design;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.architectury.platform.Platform;
import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.config.AlpineConfig;
import io.github.adang101.alpineshred.design.Design;
import io.github.adang101.alpineshred.design.DesignAtlas;
import io.github.adang101.alpineshred.design.DesignCodec;
import io.github.adang101.alpineshred.design.DesignKind;
import io.github.adang101.alpineshred.design.DesignNbt;
import io.github.adang101.alpineshred.design.DesignStatus;
import io.github.adang101.alpineshred.design.PaintBenchMenu;
import io.github.adang101.alpineshred.design.ShareCode;
import io.github.adang101.alpineshred.design.paint.Canvas;
import io.github.adang101.alpineshred.design.paint.Colors;
import io.github.adang101.alpineshred.design.paint.DesignTemplate;
import io.github.adang101.alpineshred.design.paint.DesignTemplates;
import io.github.adang101.alpineshred.design.paint.EditorState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The paint editor (design doc section 22, "Editor tools", "Color and templates"): tools across the
 * top, the front and back canvases in the middle, a turning 3D preview, the color picker, palette and
 * recent colors on the right, and the tool options, name, templates, share codes, export and Save
 * along the bottom. All painting happens on {@link EditorState}, a working copy; Save sends it to the
 * server, which checks and writes it (and takes the dye); Back returns to the bench's slots.
 */
public class DesignEditorScreen extends Screen {
    private static final int M = 6;
    private static final int PANEL_W = 128;
    private static final int TOP_H = 20;
    private static final int ROW_H = 16;
    private static final int[] PALETTE = Colors.palette();

    private final PaintBenchScreen parent;
    private final PaintBenchMenu menu;
    private final ItemStack gearAtOpen;
    private final DesignKind kind;
    private final EditorState state;
    private final int[] defaults;
    private final ResourceLocation sheet;

    // Layout, from init().
    private int scale, canvasX, canvasW;
    private final int[] canvasY = new int[2];
    private int panelX, previewY, svX, svY, hueX, swatchY, paletteY, recentY;
    private static final int SV = 56, HUE_W = 8, SWATCH = 13;

    private EditBox nameField, hexField;
    private Button resetButton, templateButton;
    /** Button labels that follow the state, refreshed every frame. */
    private final List<Runnable> liveLabels = new ArrayList<>();
    private boolean resetArmed;
    private int templateIndex;
    private double hue;
    private boolean dragCanvas, dragSv, dragHue;
    private int dragButton;
    private Component status = Component.empty();
    private int statusColor = 0xFFFFFF;
    private long statusUntil;

    // The preview's working texture.
    private DynamicTexture preview;
    private ResourceLocation previewId;
    private int previewKey = Integer.MIN_VALUE;
    private float previewSpin;

    public DesignEditorScreen(PaintBenchScreen parent, PaintBenchMenu menu) {
        super(Component.translatable("gui.alpineshred.design_editor"));
        this.parent = parent;
        this.menu = menu;
        this.gearAtOpen = menu.gear().copy();
        this.kind = DesignNbt.kindOf(gearAtOpen) == null ? DesignKind.BOARD : DesignNbt.kindOf(gearAtOpen);
        ResourceLocation item = BuiltInRegistries.ITEM.getKey(gearAtOpen.getItem());
        this.defaults = DesignTextureCache.defaults(item, kind);
        this.sheet = DesignedGearRenderer.sheet(gearAtOpen);
        this.state = new EditorState(kind, DesignAtlas.defaultEdge(kind, defaults, 0xB9C2CC));
        Design existing = DesignNbt.read(gearAtOpen);
        if (existing != null) state.load(existing);
        if (!AlpineConfig.CLIENT_DESIGN_GRID.on() && state.grid()) state.toggleGrid();
        this.hue = Colors.rgbToHsv(state.primary())[0];
    }

    /** The working copy, for the smoke test. */
    public EditorState state() {
        return state;
    }

    // ---- layout -----------------------------------------------------------------------------------------

    @Override
    protected void init() {
        // Canvases: as large as fits, up to 8 (or 12, zoomed) screen pixels per design pixel.
        int labelH = 10;
        int bottomRows = AlpineConfig.CLIENT_HINTS.on() ? 3 : 2;
        int availW = width - 3 * M - PANEL_W;
        int availH = height - M - TOP_H - 2 * labelH - bottomRows * (ROW_H + 2) - 2 * M;
        int preferred = state.zoom() ? 12 : 8;
        scale = Math.max(1, Math.min(preferred, Math.min(availW / kind.width, availH / (2 * kind.height))));
        canvasW = kind.width * scale;
        canvasX = M + Math.max(0, (availW - canvasW) / 2);
        canvasY[0] = M + TOP_H + labelH;
        canvasY[1] = canvasY[0] + kind.height * scale + labelH + 2;
        panelX = width - M - PANEL_W;
        previewY = M;
        svX = panelX;
        svY = previewY + 56;
        hueX = svX + SV + 4;
        swatchY = svY + SV + 4;
        paletteY = swatchY + 18;
        recentY = paletteY + 4 * SWATCH + 3;

        // Tools along the top.
        int x = M;
        for (EditorState.Tool tool : EditorState.Tool.values()) {
            EditorState.Tool t = tool;
            addRenderableWidget(new ToggleButton(x, M, 18, 18, Component.literal(String.valueOf(tool.key)),
                    () -> state.tool() == t, b -> state.setTool(t), Component.translatable("gui.alpineshred.design_editor.tool." + tool.id())));
            x += 20;
        }
        x += 4;
        addRenderableWidget(new ToggleButton(x, M, 18, 18, Component.literal("↔"), state::mirrorH, b -> state.toggleMirrorH(),
                Component.translatable("gui.alpineshred.design_editor.mirror_h")));
        x += 20;
        addRenderableWidget(new ToggleButton(x, M, 18, 18, Component.literal("↕"), state::mirrorV, b -> state.toggleMirrorV(),
                Component.translatable("gui.alpineshred.design_editor.mirror_v")));
        x += 24;
        addRenderableWidget(small(x, M, 18, Component.literal("↶"), b -> state.undo(), "gui.alpineshred.design_editor.undo"));
        x += 20;
        addRenderableWidget(small(x, M, 18, Component.literal("↷"), b -> state.redo(), "gui.alpineshred.design_editor.redo"));
        x += 20;
        addRenderableWidget(new ToggleButton(x, M, 18, 18, Component.literal("#"), state::grid, b -> state.toggleGrid(),
                Component.translatable("gui.alpineshred.design_editor.grid")));
        x += 20;
        addRenderableWidget(new ToggleButton(x, M, 18, 18, Component.literal("+"), state::zoom, b -> {
            state.toggleZoom();
            rebuild();
        }, Component.translatable("gui.alpineshred.design_editor.zoom")));

        // Hex field under the swatches.
        hexField = new EditBox(font, panelX + 2 * SWATCH + 8, swatchY + 1, PANEL_W - 2 * SWATCH - 8, 14, Component.translatable("gui.alpineshred.design_editor.hex"));
        hexField.setMaxLength(7);
        hexField.setValue(Colors.hex(state.primary()));
        hexField.setResponder(s -> {
            int c = Colors.parseHex(s);
            if (c >= 0 && hexField.isFocused()) {
                state.setPrimary(c);
                hue = Colors.rgbToHsv(c)[0];
            }
        });
        addRenderableWidget(hexField);

        // Bottom rows, laid out left to right and wrapping.
        Flow flow = new Flow(M, height - M - (bottomRows) * (ROW_H + 2) + 2, width - 2 * M);
        optionButtons(flow);
        flow.newLine();
        nameField = new EditBox(font, 0, 0, 96, ROW_H, Component.translatable("gui.alpineshred.design_editor.name"));
        nameField.setMaxLength(Design.NAME_MAX);
        nameField.setValue(state.name());
        nameField.setHint(Component.translatable("gui.alpineshred.design_editor.name").withStyle(ChatFormatting.DARK_GRAY));
        nameField.setResponder(state::setName);
        flow.place(nameField, 96);
        addRenderableWidget(nameField);
        flow.button(this, 12, Component.literal("<"), b -> cycleTemplate(-1), "gui.alpineshred.design_editor.template_prev");
        templateButton = flow.button(this, 64, Component.empty(), b -> applyTemplate(), "gui.alpineshred.design_editor.template_apply");
        templateButton.setMessage(templateLabel());
        flow.button(this, 12, Component.literal(">"), b -> cycleTemplate(1), "gui.alpineshred.design_editor.template_next");
        flow.button(this, 36, Component.translatable("gui.alpineshred.design_editor.copy_code"), b -> copyCode(), "gui.alpineshred.design_editor.copy_code.tip");
        flow.button(this, 36, Component.translatable("gui.alpineshred.design_editor.paste_code"), b -> pasteCode(), "gui.alpineshred.design_editor.paste_code.tip");
        flow.button(this, 40, Component.translatable("gui.alpineshred.design_editor.export"), b -> exportPng(), "gui.alpineshred.design_editor.export.tip");
        resetButton = flow.button(this, 40, Component.translatable("gui.alpineshred.design_editor.reset"), b -> reset(), "gui.alpineshred.design_editor.reset.tip");
        flow.button(this, 40, Component.translatable("gui.alpineshred.design_editor.back"), b -> onClose(), "gui.alpineshred.design_editor.back.tip");
        flow.button(this, 44, Component.translatable("gui.alpineshred.design_editor.save").withStyle(ChatFormatting.GREEN), b -> save(),
                "gui.alpineshred.design_editor.save.tip");
    }

    private void rebuild() {
        clearWidgets();
        liveLabels.clear();
        init();
    }

    /** The current tool's options, and the face actions. */
    private void optionButtons(Flow flow) {
        flow.button(this, 14, Component.literal("-"), b -> state.brushSmaller(), "gui.alpineshred.design_editor.brush_smaller");
        flow.label(this, () -> Component.translatable("gui.alpineshred.design_editor.brush", state.brush()), 34);
        flow.button(this, 14, Component.literal("+"), b -> state.brushBigger(), "gui.alpineshred.design_editor.brush_bigger");
        flow.toggle(this, 40, Component.translatable("gui.alpineshred.design_editor.filled"), state::rectFilled, state::toggleRectFilled,
                "gui.alpineshred.design_editor.filled.tip");
        flow.button(this, 44, Component.empty(), b -> state.toggleGradientType(), "gui.alpineshred.design_editor.gradient_type");
        Button type = flow.last();
        flow.live(this, type, () -> Component.translatable("gui.alpineshred.design_editor.gradient." + state.gradientType().name().toLowerCase(Locale.ROOT)));
        flow.toggle(this, 38, Component.translatable("gui.alpineshred.design_editor.dither"), state::dither, state::toggleDither,
                "gui.alpineshred.design_editor.dither.tip");
        flow.toggle(this, 40, Component.translatable("gui.alpineshred.design_editor.middle"), state::useMiddle, () -> {
            if (!state.useMiddle()) state.setMiddle(state.primary());
            state.toggleMiddle();
        }, "gui.alpineshred.design_editor.middle.tip");
        flow.button(this, 56, Component.empty(), b -> state.nextPattern(), "gui.alpineshred.design_editor.pattern");
        Button pattern = flow.last();
        flow.live(this, pattern, () -> Component.translatable("gui.alpineshred.design_editor.pattern." + state.pattern().id()));
        flow.button(this, 14, Component.literal("-"), b -> state.setPatternSize(state.patternSize() - 1), "gui.alpineshred.design_editor.pattern_smaller");
        flow.label(this, () -> Component.literal(String.valueOf(state.patternSize())), 10);
        flow.button(this, 14, Component.literal("+"), b -> state.setPatternSize(state.patternSize() + 1), "gui.alpineshred.design_editor.pattern_bigger");
        flow.toggle(this, 48, Component.translatable("gui.alpineshred.design_editor.lock"), state::lockTransparent, state::toggleLockTransparent,
                "gui.alpineshred.design_editor.lock.tip");
        flow.button(this, 64, Component.translatable("gui.alpineshred.design_editor.copy_face"), b -> state.copyToOtherFace(),
                "gui.alpineshred.design_editor.copy_face.tip");
        flow.button(this, 44, Component.translatable("gui.alpineshred.design_editor.edge"), b -> state.setEdge(state.primary()),
                "gui.alpineshred.design_editor.edge.tip");
        if (kind == DesignKind.SKI) {
            flow.button(this, 56, Component.empty(), b -> state.togglePair(), "gui.alpineshred.design_editor.pair.tip");
            Button pair = flow.last();
            flow.live(this, pair, () -> Component.translatable("gui.alpineshred.design_editor.pair." + state.pair().id()));
        }
    }

    private Component templateLabel() {
        List<DesignTemplate> all = DesignTemplates.all();
        if (all.isEmpty()) return Component.translatable("gui.alpineshred.design_editor.no_templates");
        DesignTemplate t = all.get(Math.floorMod(templateIndex, all.size()));
        String key = "design_template." + t.id().replace(':', '.');
        return Component.translatableWithFallback(key, t.name());
    }

    private void cycleTemplate(int step) {
        templateIndex += step;
        if (templateButton != null) templateButton.setMessage(templateLabel());
    }

    private void applyTemplate() {
        List<DesignTemplate> all = DesignTemplates.all();
        if (all.isEmpty()) return;
        state.applyTemplate(all.get(Math.floorMod(templateIndex, all.size())));
    }

    // ---- actions -----------------------------------------------------------------------------------------

    /** Save: the working copy goes to the server, which answers with {@link #onResult}. */
    public void save() {
        if (!gearStillThere()) return;
        DesignClient.save(menu.containerId, state.toDesign());
        setStatus(Component.translatable("gui.alpineshred.design_editor.saving"), 0xFFFFA0);
    }

    private void reset() {
        if (!resetArmed) {
            resetArmed = true;
            resetButton.setMessage(Component.translatable("gui.alpineshred.design_editor.reset_sure").withStyle(ChatFormatting.RED));
            return;
        }
        resetArmed = false;
        resetButton.setMessage(Component.translatable("gui.alpineshred.design_editor.reset"));
        state.clearAll();
        if (DesignNbt.has(menu.gear())) DesignClient.reset(menu.containerId, state.toDesign());
    }

    void onResult(DesignStatus result) {
        if (result == DesignStatus.OK) {
            setStatus(Component.translatable(result.translationKey()), 0x80FF80);
        } else {
            setStatus(Component.translatable(result.translationKey()), 0xFF8080);
        }
    }

    private void setStatus(Component text, int color) {
        status = text;
        statusColor = color;
        statusUntil = System.currentTimeMillis() + 4000;
    }

    private void copyCode() {
        if (minecraft == null) return;
        minecraft.keyboardHandler.setClipboard(ShareCode.encode(state.toDesign()));
        setStatus(Component.translatable("gui.alpineshred.design_editor.code_copied"), 0x80FF80);
    }

    private void pasteCode() {
        if (minecraft == null) return;
        try {
            state.paste(ShareCode.decode(minecraft.keyboardHandler.getClipboard(), kind, AlpineConfig.DESIGN_MAX_COMPRESSED_BYTES.value()));
            nameField.setValue(state.name());
            setStatus(Component.translatable("gui.alpineshred.design_editor.code_pasted"), 0x80FF80);
        } catch (DesignCodec.InvalidDesign e) {
            setStatus(Component.translatable("gui.alpineshred.design_editor.code_bad", e.getMessage()), 0xFF8080);
        }
    }

    /** Export PNG: each face at native size and 8 times, into {@code config/alpineshred/exports/}. */
    private void exportPng() {
        Design d = state.toDesign();
        String base = (d.name().isEmpty() ? "design" : d.name()).replaceAll("[^A-Za-z0-9_-]+", "_") + "-" + kind.id + "-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path dir = Platform.getConfigFolder().resolve("alpineshred").resolve("exports");
        try {
            Files.createDirectories(dir);
            for (int face = 0; face < 2; face++) {
                String side = face == 0 ? "top" : "base";
                for (int s : new int[]{1, 8}) {
                    try (NativeImage image = new NativeImage(kind.width * s, kind.height * s, true)) {
                        for (int y = 0; y < kind.height; y++) {
                            for (int x = 0; x < kind.width; x++) {
                                int p = d.pixel(face == 1, x, y);
                                for (int dy = 0; dy < s; dy++) {
                                    for (int dx = 0; dx < s; dx++) image.setPixelRGBA(x * s + dx, y * s + dy, DesignTextureCache.abgr(p));
                                }
                            }
                        }
                        image.writeToFile(dir.resolve(base + "-" + side + (s == 1 ? "" : "-x" + s) + ".png"));
                    }
                }
            }
            setStatus(Component.translatable("gui.alpineshred.design_editor.exported", dir.toString()), 0x80FF80);
        } catch (Exception e) {
            AlpineShred.LOGGER.warn("Design export failed", e);
            setStatus(Component.translatable("gui.alpineshred.design_editor.export_failed"), 0xFF8080);
        }
    }

    private boolean gearStillThere() {
        ItemStack now = menu.gear();
        return !now.isEmpty() && now.getItem() == gearAtOpen.getItem();
    }

    @Override
    public void tick() {
        super.tick();
        hexField.tick();
        nameField.tick();
        // The gear left the bench (or the bench closed): back to the slots.
        if (minecraft != null && (!gearStillThere() || minecraft.player == null || minecraft.player.containerMenu != menu)) {
            minecraft.setScreen(minecraft.player != null && minecraft.player.containerMenu == menu ? parent.reopen() : null);
            return;
        }
        if (!hexField.isFocused()) hexField.setValue(Colors.hex(state.primary()));
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent.reopen());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        if (previewId != null && minecraft != null) minecraft.getTextureManager().release(previewId);
        previewId = null;
        preview = null;
    }

    // ---- input ----------------------------------------------------------------------------------------------

    /** The face under (mx, my), or -1. */
    private int faceAt(double mx, double my) {
        for (int f = 0; f < 2; f++) {
            if (mx >= canvasX && mx < canvasX + canvasW && my >= canvasY[f] && my < canvasY[f] + kind.height * scale) return f;
        }
        return -1;
    }

    private int px(double mx) {
        return Math.max(0, Math.min(kind.width - 1, (int) Math.floor((mx - canvasX) / scale)));
    }

    private int py(double my, int face) {
        return Math.max(0, Math.min(kind.height - 1, (int) Math.floor((my - canvasY[face]) / scale)));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int face = faceAt(mx, my);
        if (face >= 0) {
            state.setFace(face);
            setFocused(null);
            if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                state.pickInto(0, px(mx), py(my, face));
                hue = Colors.rgbToHsv(state.primary())[0];
                return true;
            }
            if (button > 1) return false;
            state.press(button, px(mx), py(my, face));
            if (state.tool() == EditorState.Tool.EYEDROPPER) hue = Colors.rgbToHsv(state.color(button))[0];
            dragCanvas = state.stroking();
            dragButton = button;
            return true;
        }
        if (button <= 1 && mx >= svX && mx < svX + SV && my >= svY && my < svY + SV) {
            dragSv = true;
            dragButton = button;
            pickSv(mx, my);
            return true;
        }
        if (button <= 1 && mx >= hueX && mx < hueX + HUE_W && my >= svY && my < svY + SV) {
            dragHue = true;
            dragButton = button;
            pickHue(my);
            return true;
        }
        if (button <= 1) {
            int swatch = swatchAt(mx, my, paletteY, 4);
            if (swatch >= 0 && swatch < PALETTE.length) {
                setColor(button, PALETTE[swatch]);
                return true;
            }
            List<Integer> recent = state.recent();
            int r = swatchAt(mx, my, recentY, 1);
            if (r >= 0 && r < recent.size()) {
                setColor(button, recent.get(r));
                return true;
            }
            if (my >= swatchY && my < swatchY + SWATCH + 2 && mx >= panelX && mx < panelX + 2 * SWATCH + 4) {
                state.swapColors();
                hue = Colors.rgbToHsv(state.primary())[0];
                return true;
            }
        }
        return false;
    }

    private int swatchAt(double mx, double my, int y0, int rows) {
        if (mx < panelX || my < y0 || my >= y0 + rows * SWATCH) return -1;
        int col = (int) ((mx - panelX) / SWATCH), row = (int) ((my - y0) / SWATCH);
        if (col >= 8) return -1;
        return row * 8 + col;
    }

    private void setColor(int button, int rgb) {
        if (button == 1) {
            state.setSecondary(rgb);
        } else {
            state.setPrimary(rgb);
            hue = Colors.rgbToHsv(rgb)[0];
        }
    }

    private void pickSv(double mx, double my) {
        double s = Math.max(0, Math.min(1, (mx - svX) / (SV - 1)));
        double v = 1 - Math.max(0, Math.min(1, (my - svY) / (SV - 1)));
        int rgb = Colors.hsvToRgb(hue, s, v);
        if (dragButton == 1) state.setSecondary(rgb);
        else state.setPrimary(rgb);
    }

    private void pickHue(double my) {
        hue = Math.max(0, Math.min(359.9, (my - svY) / SV * 360));
        double[] hsv = Colors.rgbToHsv(dragButton == 1 ? state.secondary() : state.primary());
        int rgb = Colors.hsvToRgb(hue, Math.max(hsv[1], 0.05), Math.max(hsv[2], 0.05));
        if (dragButton == 1) state.setSecondary(rgb);
        else state.setPrimary(rgb);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragCanvas) {
            int face = state.face();
            state.drag((int) Math.floor((mx - canvasX) / scale), (int) Math.floor((my - canvasY[face]) / scale));
            return true;
        }
        if (dragSv) {
            pickSv(mx, my);
            return true;
        }
        if (dragHue) {
            pickHue(my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragCanvas) {
            int face = state.face();
            state.release((int) Math.floor((mx - canvasX) / scale), (int) Math.floor((my - canvasY[face]) / scale));
            dragCanvas = false;
            return true;
        }
        dragSv = dragHue = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        boolean typing = (nameField.isFocused() || hexField.isFocused());
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (typing) {
                setFocused(null);
                return true;
            }
            onClose();
            return true;
        }
        if (typing) return super.keyPressed(key, scan, modifiers);
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 || (modifiers & GLFW.GLFW_MOD_SUPER) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (ctrl && key == GLFW.GLFW_KEY_Z) {
            if (shift) state.redo();
            else state.undo();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_Y) {
            state.redo();
            return true;
        }
        if (ctrl) return super.keyPressed(key, scan, modifiers);
        switch (key) {
            case GLFW.GLFW_KEY_X -> state.swapColors();
            case GLFW.GLFW_KEY_LEFT_BRACKET -> state.brushSmaller();
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> state.brushBigger();
            case GLFW.GLFW_KEY_TAB -> state.switchFace();
            case GLFW.GLFW_KEY_M -> {
                if (shift) state.toggleMirrorV();
                else state.toggleMirrorH();
            }
            default -> {
                if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
                    EditorState.Tool tool = EditorState.toolForKey((char) ('A' + key - GLFW.GLFW_KEY_A));
                    if (tool != null) {
                        state.setTool(tool);
                        return true;
                    }
                }
                return super.keyPressed(key, scan, modifiers);
            }
        }
        return true;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (c == '#' && !nameField.isFocused() && !hexField.isFocused()) {
            state.toggleGrid();
            return true;
        }
        return super.charTyped(c, modifiers);
    }

    // ---- drawing ----------------------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        for (int f = 0; f < 2; f++) {
            Component label = Component.translatable(f == 0 ? "gui.alpineshred.design_editor.front" : "gui.alpineshred.design_editor.back_face");
            g.drawString(font, label, canvasX, canvasY[f] - 10, state.face() == f ? 0xFFFFFF : 0xA0A0A0);
            drawCanvas(g, f, mouseX, mouseY);
        }
        drawPreview(g, partialTick);
        drawPicker(g);
        super.render(g, mouseX, mouseY, partialTick);
        // Status and hints.
        int infoY = height - M - 10;
        if (System.currentTimeMillis() < statusUntil) {
            g.drawString(font, status, M, canvasY[1] + kind.height * scale + 4, statusColor);
        }
        if (AlpineConfig.CLIENT_HINTS.on()) {
            g.drawString(font, Component.translatable("gui.alpineshred.design_editor.hints").withStyle(ChatFormatting.GRAY), M, infoY, 0xA0A0A0);
        }
        String cost = AlpineConfig.DESIGN_SAVE_COST_COUNT.value() > 0 && minecraft != null && minecraft.player != null
                && !minecraft.player.getAbilities().instabuild
                ? Component.translatable("gui.alpineshred.design_editor.cost", AlpineConfig.DESIGN_SAVE_COST_COUNT.value(), menu.dye().getCount()).getString()
                : "";
        if (!cost.isEmpty()) g.drawString(font, cost, canvasX + canvasW - font.width(cost), canvasY[0] - 10, 0xC0C0C0);
        for (var child : children()) {
            if (child instanceof Tipped t && child instanceof Button b && b.isHovered()) {
                g.renderTooltip(font, t.tip(), mouseX, mouseY);
            }
        }
    }

    private void drawCanvas(GuiGraphics g, int face, int mouseX, int mouseY) {
        Canvas c = state.canvas(face);
        int x0 = canvasX, y0 = canvasY[face];
        g.fill(x0 - 2, y0 - 2, x0 + canvasW + 2, y0 + kind.height * scale + 2, state.face() == face ? 0xFFE0E0E0 : 0xFF505050);
        for (int y = 0; y < kind.height; y++) {
            for (int x = 0; x < kind.width; x++) {
                int sx = x0 + x * scale, sy = y0 + y * scale;
                int color;
                if (!c.editable(x, y)) {
                    color = ((x + y) & 1) == 0 ? 0xFF202020 : 0xFF262626; // locked, dimmed
                } else if (c.opaque(x, y)) {
                    color = c.get(x, y);
                } else {
                    // Transparent: the gear's own look shows through, a little faded.
                    int d = DesignAtlas.defaultPixel(kind, defaults, 0x808080, face == 1, x, y);
                    color = 0xFF000000 | fade(d);
                }
                g.fill(sx, sy, sx + scale, sy + scale, color);
            }
        }
        if (state.grid() && scale >= 4) {
            for (int x = 1; x < kind.width; x++) g.fill(x0 + x * scale, y0, x0 + x * scale + 1, y0 + kind.height * scale, 0x30000000);
            for (int y = 1; y < kind.height; y++) g.fill(x0, y0 + y * scale, x0 + canvasW, y0 + y * scale + 1, 0x30000000);
        }
        // The brush under the mouse.
        if (faceAt(mouseX, mouseY) == face) {
            int bx = px(mouseX), by = py(mouseY, face);
            int size = state.tool() == EditorState.Tool.PENCIL || state.tool() == EditorState.Tool.ERASER
                    || state.tool() == EditorState.Tool.LINE || state.tool() == EditorState.Tool.RECTANGLE ? state.brush() : 1;
            int from = -(size - 1) / 2;
            int sx = x0 + (bx + from) * scale, sy = y0 + (by + from) * scale, len = size * scale;
            g.renderOutline(sx, sy, len, len, 0xC0FFFFFF);
        }
    }

    private void drawPicker(GuiGraphics g) {
        // Saturation across, value down, at the current hue.
        for (int x = 0; x < SV; x++) {
            int top = 0xFF000000 | Colors.hsvToRgb(hue, x / (double) (SV - 1), 1);
            g.fillGradient(svX + x, svY, svX + x + 1, svY + SV, top, 0xFF000000);
        }
        double[] hsv = Colors.rgbToHsv(state.primary());
        int mx = svX + (int) Math.round(hsv[1] * (SV - 1)), my = svY + (int) Math.round((1 - hsv[2]) * (SV - 1));
        g.renderOutline(mx - 2, my - 2, 5, 5, hsv[2] > 0.5 ? 0xFF000000 : 0xFFFFFFFF);
        for (int y = 0; y < SV; y++) {
            g.fill(hueX, svY + y, hueX + HUE_W, svY + y + 1, 0xFF000000 | Colors.hsvToRgb(y * 360.0 / SV, 1, 1));
        }
        int hy = svY + (int) Math.round(hue / 360 * SV);
        g.fill(hueX - 1, hy, hueX + HUE_W + 1, hy + 1, 0xFFFFFFFF);
        // Edge color beside the picker.
        int ex = hueX + HUE_W + 6;
        g.drawString(font, Component.translatable("gui.alpineshred.design_editor.edge_label"), ex, svY, 0xC0C0C0);
        g.fill(ex, svY + 10, ex + 20, svY + 22, 0xFF000000 | state.edge());
        g.renderOutline(ex, svY + 10, 20, 12, 0xFF000000);
        // Primary over secondary.
        g.fill(panelX + SWATCH / 2 + 2, swatchY + 4, panelX + SWATCH / 2 + 2 + SWATCH, swatchY + 4 + SWATCH, 0xFF000000 | state.secondary());
        g.fill(panelX, swatchY, panelX + SWATCH, swatchY + SWATCH, 0xFF000000 | state.primary());
        g.renderOutline(panelX, swatchY, SWATCH, SWATCH, 0xFFFFFFFF);
        // Palette and recent colors.
        for (int i = 0; i < PALETTE.length; i++) {
            int sx = panelX + (i % 8) * SWATCH, sy = paletteY + (i / 8) * SWATCH;
            g.fill(sx, sy, sx + SWATCH - 1, sy + SWATCH - 1, 0xFF000000 | PALETTE[i]);
        }
        List<Integer> recent = state.recent();
        for (int i = 0; i < recent.size(); i++) {
            int sx = panelX + i * SWATCH;
            g.fill(sx, recentY, sx + SWATCH - 1, recentY + SWATCH - 1, 0xFF000000 | recent.get(i));
        }
    }

    /** The working copy on the gear, turning, lit like an inventory entity. */
    private void drawPreview(GuiGraphics g, float partialTick) {
        if (minecraft == null) return;
        Design design = state.toDesign();
        int key = design.hash() * 31 + design.pair().ordinal();
        if (preview == null) {
            preview = new DynamicTexture(new NativeImage(NativeImage.Format.RGBA, kind.width, kind.atlasHeight(), false));
            previewId = minecraft.getTextureManager().register("alpineshred_design_preview", preview);
        }
        if (key != previewKey && preview.getPixels() != null) {
            DesignTextureCache.fill(preview.getPixels(), kind, DesignAtlas.build(kind, design, defaults, 0x808080));
            preview.upload();
            previewKey = key;
        }
        previewSpin += minecraft.getDeltaFrameTime() * 2.0f;
        g.fill(panelX, previewY, panelX + PANEL_W, previewY + 52, 0xFF2A3340);
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(panelX + PANEL_W / 2f, previewY + 26, 150);
        float s = kind == DesignKind.BOARD ? 5.0f : 4.2f;
        pose.scale(16 * s, -16 * s, 16 * s);
        pose.mulPose(Axis.XP.rotationDegrees(25));
        pose.mulPose(Axis.YP.rotationDegrees(previewSpin));
        pose.translate(-0.5f, -0.5f, -0.5f);
        Lighting.setupForEntityInInventory();
        DesignedGearRenderer.draw(kind, previewId, sheet, state.pair() == Design.Pair.MIRROR, pose, g.bufferSource(),
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        g.flush();
        Lighting.setupFor3DItems();
        pose.popPose();
    }

    // ---- small widgets ----------------------------------------------------------------------------------------

    interface Tipped {
        Component tip();
    }

    private static Button small(int x, int y, int w, Component label, Button.OnPress onPress, String tipKey) {
        return new TipButton(x, y, w, 18, label, onPress, Component.translatable(tipKey));
    }

    /** A button with a hover tip. */
    static class TipButton extends Button implements Tipped {
        private final Component tip;

        TipButton(int x, int y, int w, int h, Component label, OnPress onPress, Component tip) {
            super(x, y, w, h, label, onPress, DEFAULT_NARRATION);
            this.tip = tip;
        }

        @Override
        public Component tip() {
            return tip;
        }
    }

    /** A button drawn pressed while {@code on} holds. */
    static class ToggleButton extends TipButton {
        private final BooleanSupplier on;

        ToggleButton(int x, int y, int w, int h, Component label, BooleanSupplier on, OnPress onPress, Component tip) {
            super(x, y, w, h, label, onPress, tip);
            this.on = on;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(g, mouseX, mouseY, partialTick);
            if (on.getAsBoolean()) g.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xFFFFE070);
        }
    }

    /** Lays widgets out left to right, wrapping at the right margin. */
    private static final class Flow {
        private final int left, right;
        private int x, y;
        private Button last;

        Flow(int left, int top, int width) {
            this.left = left;
            this.right = left + width;
            this.x = left;
            this.y = top;
        }

        void newLine() {
            x = left;
            y += ROW_H + 2;
        }

        void place(net.minecraft.client.gui.components.AbstractWidget w, int width) {
            if (x + width > right && x > left) newLine();
            w.setX(x);
            w.setY(y);
            x += width + 2;
        }

        Button button(DesignEditorScreen screen, int width, Component label, Button.OnPress onPress, String tipKey) {
            TipButton b = new TipButton(0, 0, width, ROW_H, label, onPress, Component.translatable(tipKey));
            place(b, width);
            screen.addRenderableWidget(b);
            last = b;
            return b;
        }

        void toggle(DesignEditorScreen screen, int width, Component label, BooleanSupplier on, Runnable toggle, String tipKey) {
            ToggleButton b = new ToggleButton(0, 0, width, ROW_H, label, on, x -> toggle.run(), Component.translatable(tipKey));
            place(b, width);
            screen.addRenderableWidget(b);
            last = b;
        }

        /** Text that follows a value, drawn as an inactive button. */
        void label(DesignEditorScreen screen, Supplier<Component> text, int width) {
            Button b = new Button(0, 0, width, ROW_H, text.get(), x -> {
            }, Button.DEFAULT_NARRATION) {
                @Override
                public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
                    setMessage(text.get());
                    g.drawCenteredString(screen.font, getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, 0xE0E0E0);
                }
            };
            b.active = false;
            place(b, width);
            screen.addRenderableWidget(b);
        }

        /** Keeps a button's label following a value. */
        void live(DesignEditorScreen screen, Button b, Supplier<Component> text) {
            b.setMessage(text.get());
            screen.liveLabels.add(() -> b.setMessage(text.get()));
        }

        Button last() {
            return last;
        }
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        super.renderBackground(g);
        for (Runnable r : liveLabels) r.run();
    }

    /** A transparent pixel's color in the editor: the default look, faded toward gray. */
    private static int fade(int rgb) {
        int r = (rgb >> 16) & 0xFF, gr = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return (r * 3 / 4 + 32) << 16 | (gr * 3 / 4 + 32) << 8 | (b * 3 / 4 + 32);
    }
}
