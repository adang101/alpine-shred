package io.github.adang101.alpineshred.smoke;

import io.github.adang101.alpineshred.gear.GearAccess;
import io.github.adang101.alpineshred.gear.GogglesItem;
import io.github.adang101.alpineshred.gear.Lens;
import io.github.adang101.alpineshred.registry.ModBlocks;
import io.github.adang101.alpineshred.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;

/**
 * The smoke tests' snow run (server side, so a dedicated server can build it too): a platform, a
 * 1-in-3 staircase slope, a flat with room for a pop, a kicker, a powder patch and a long flat,
 * nine blocks wide, along +z from {@code base}. Lengths in blocks from the start.
 */
public final class SmokeCourse {
    public static final int SLOPE_START = 3, SLOPE_END = 48, KICKER = 60, POWDER_FROM = 84, POWDER_TO = 92, LENGTH = 130;
    /** Height of the start platform above {@code base}. */
    public static final int TOP = 16;

    private SmokeCourse() {
    }

    /** Column height of the run at {@code dz}: platform, 1-in-3 staircase, flat, kicker, flat. */
    public static double height(int dz) {
        if (dz < SLOPE_START) return TOP;
        if (dz <= SLOPE_END) return Math.max(1, TOP - Math.floor((dz - SLOPE_START) / 3.0) - 1);
        if (dz >= KICKER && dz < KICKER + 4) return 1 + 0.375 * (dz - KICKER + 1);
        return 1;
    }

    public static void build(ServerLevel level, BlockPos base) {
        Block groomed = ModBlocks.GROOMED_SNOW.get();
        for (int dz = 0; dz < LENGTH; dz++) {
            double h = height(dz);
            for (int dx = -4; dx <= 4; dx++) {
                for (int dy = 0; dy < (int) Math.floor(h); dy++) {
                    level.setBlock(base.offset(dx, dy, dz), groomed.defaultBlockState(), 2);
                }
                double frac = h - Math.floor(h);
                if (frac > 1.0e-6) {
                    int layers = (int) Math.round(frac * 8) + 1;
                    level.setBlock(base.offset(dx, (int) Math.floor(h), dz),
                            Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, Math.min(8, layers)), 2);
                }
                if (dz >= POWDER_FROM && dz < POWDER_TO) {
                    level.setBlock(base.offset(dx, (int) Math.floor(h) - 1, dz), Blocks.POWDER_SNOW.defaultBlockState(), 2);
                }
            }
        }
    }

    /** Snowboard, boots, goggles, mitts and helmet; out of creative flight. */
    public static void dressForBoard(ServerPlayer player) {
        // Phase 6: the board carries a design, so every picture shows it and the watchers can
        // check that the gear slot's sync brings the design to them.
        ItemStack board = new ItemStack(ModItems.SNOWBOARD.get());
        io.github.adang101.alpineshred.design.DesignNbt.write(board, smokeDesign().withAuthor(player.getGameProfile().getName(), player.getUUID()));
        GearAccess.current().set(player, GearAccess.Slot.BOARD, board);
        GearAccess.current().set(player, GearAccess.Slot.GOGGLES, GogglesItem.withLens(new ItemStack(ModItems.GOGGLES.get()), Lens.AMBER));
        GearAccess.current().set(player, GearAccess.Slot.MITTS, new ItemStack(ModItems.MITTS.get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.SNOWBOARD_BOOTS.get()));
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.SKI_HELMET.get()));
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
    }

    /** The smoke runs' board design: a checker top with a red stripe, a gradient base, red edges. */
    public static io.github.adang101.alpineshred.design.Design smokeDesign() {
        var kind = io.github.adang101.alpineshred.design.DesignKind.BOARD;
        var none = io.github.adang101.alpineshred.design.paint.PaintTools.Mirror.NONE;
        var top = new io.github.adang101.alpineshred.design.paint.Canvas(kind);
        var base = new io.github.adang101.alpineshred.design.paint.Canvas(kind);
        io.github.adang101.alpineshred.design.paint.PaintTools.pattern(top, none,
                io.github.adang101.alpineshred.design.paint.PaintTools.Pattern.CHECKER, 0xFF101010, 0xFFF4F4F4, 4, false);
        io.github.adang101.alpineshred.design.paint.PaintTools.rect(top, none, 0, 6, 63, 9, true, 1, 0xFFD62828);
        io.github.adang101.alpineshred.design.paint.PaintTools.gradient(base, none, new io.github.adang101.alpineshred.design.paint.PaintTools.Gradient(
                io.github.adang101.alpineshred.design.paint.PaintTools.GradientType.LINEAR, 0, 8, 63, 8, 0xFFF77F00, 0xFF003049, -1, false, 0, false));
        return new io.github.adang101.alpineshred.design.Design(kind, top.pixels(), base.pixels(), 0xD62828,
                io.github.adang101.alpineshred.design.Design.Pair.SAME, "Smoke", "", null);
    }

    /** The start: on the platform, facing down the run. */
    public static void toTop(ServerPlayer player, BlockPos base) {
        player.teleportTo(player.serverLevel(), base.getX() + 0.5, base.getY() + TOP, base.getZ() + 1.5, 0f, 15f);
    }
}
