package io.github.adang101.alpineshred.client.design;

import io.github.adang101.alpineshred.design.Design;
import io.github.adang101.alpineshred.design.DesignNbt;
import io.github.adang101.alpineshred.design.PaintBenchMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The Paint Bench's slots (design doc section 22, "Bench and flow"): a board or skis on the left, the
 * dye a save costs on the right, the inventory below. Placing gear opens the editor
 * ({@link DesignEditorScreen}); the Paint button opens it again. Drawn with fills, in the vanilla
 * container style, so it needs no background texture.
 */
public class PaintBenchScreen extends AbstractContainerScreen<PaintBenchMenu> {
    private Button paint;
    private ItemStack lastGear = ItemStack.EMPTY;
    private final Inventory inventory;

    public PaintBenchScreen(PaintBenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.inventory = inventory;
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    /** Back from the editor: the same menu, so the slots stay where they were. */
    PaintBenchScreen reopen() {
        return new PaintBenchScreen(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        lastGear = menu.gear().copy();
        paint = addRenderableWidget(Button.builder(Component.translatable("gui.alpineshred.paint_bench.paint"), b -> openEditor())
                .bounds(leftPos + 64, topPos + 58, 48, 16).build());
    }

    private void openEditor() {
        if (minecraft != null && PaintBenchMenu.isGear(menu.gear())) minecraft.setScreen(new DesignEditorScreen(this, menu));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ItemStack gear = menu.gear();
        paint.active = PaintBenchMenu.isGear(gear);
        // Placing gear opens the editor.
        if (!gear.isEmpty() && lastGear.isEmpty() && menu.getCarried().isEmpty()) {
            lastGear = gear.copy();
            openEditor();
            return;
        }
        lastGear = gear.copy();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        panel(g, x, y, imageWidth, imageHeight);
        slot(g, x + PaintBenchMenu.GEAR_X - 1, y + PaintBenchMenu.GEAR_Y - 1);
        slot(g, x + PaintBenchMenu.DYE_X - 1, y + PaintBenchMenu.DYE_Y - 1);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) slot(g, x + 7 + col * 18, y + 83 + row * 18);
        }
        for (int col = 0; col < 9; col++) slot(g, x + 7 + col * 18, y + 141);
        // A brush between the two slots.
        g.fill(x + 70, y + 42, x + 106, y + 44, 0xFF8B5A2B);
        g.fill(x + 104, y + 39, x + 110, y + 47, 0xFFE03030);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
        g.drawString(font, Component.translatable("gui.alpineshred.paint_bench.gear"), PaintBenchMenu.GEAR_X - 2, PaintBenchMenu.GEAR_Y - 11, 0x404040, false);
        g.drawString(font, Component.translatable("gui.alpineshred.paint_bench.dye"), PaintBenchMenu.DYE_X - 2, PaintBenchMenu.DYE_Y - 11, 0x404040, false);
        Design design = DesignNbt.read(menu.gear());
        if (design != null && !design.name().isEmpty()) {
            Component line = Component.literal(design.name()).withStyle(ChatFormatting.ITALIC);
            g.drawString(font, line, (imageWidth - font.width(line)) / 2, 22, 0x505050, false);
        }
    }

    /** The vanilla container panel: light gray with a bevel. */
    static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFFC6C6C6);
        g.fill(x, y, x + w - 1, y + 1, 0xFFFFFFFF);
        g.fill(x, y, x + 1, y + h - 1, 0xFFFFFFFF);
        g.fill(x + 1, y + h - 1, x + w, y + h, 0xFF555555);
        g.fill(x + w - 1, y + 1, x + w, y + h, 0xFF555555);
    }

    /** A slot frame at (x, y), 18 by 18. */
    static void slot(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, 0xFF8B8B8B);
        g.fill(x, y, x + 17, y + 1, 0xFF373737);
        g.fill(x, y, x + 1, y + 17, 0xFF373737);
        g.fill(x + 1, y + 17, x + 18, y + 18, 0xFFFFFFFF);
        g.fill(x + 17, y + 1, x + 18, y + 18, 0xFFFFFFFF);
    }
}
