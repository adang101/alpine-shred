package io.github.adang101.alpineshred.gametest;

import io.github.adang101.alpineshred.gear.GearAccess;
import io.github.adang101.alpineshred.movement.StrapCheck;
import io.github.adang101.alpineshred.movement.StrapController;
import io.github.adang101.alpineshred.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Builds riding courses inside a game test structure, in test-relative coordinates. A course is a
 * lane five blocks wide (x 2 to 6) running along +z; {@link #column} sets the surface height of one
 * z slice, with snow layers for the eighths, so ramps and kickers can be built without custom blocks.
 * Riders stand on the lane center line, x = 4.5.
 */
final class Course {
    static final int LANE_MIN_X = 2;
    static final int LANE_MAX_X = 6;
    static final double CENTER_X = 4.5;
    /** Lowest block of every column; the structure's own floor is below it. */
    static final int BASE_Y = 1;

    private final GameTestHelper helper;

    Course(GameTestHelper helper) {
        this.helper = helper;
    }

    /** Fills the z slice so its walkable top is at {@code top}: full blocks of {@code fill}, then a snow layer for the fraction. */
    Course column(int z, double top, Block fill) {
        int full = (int) Math.floor(top + 1.0e-9);
        double fraction = top - full;
        int layers = (int) Math.round(fraction * 8) + 1; // collision height of a snow layer is (layers - 1) / 8
        for (int x = LANE_MIN_X; x <= LANE_MAX_X; x++) {
            for (int y = BASE_Y; y < full; y++) helper.setBlock(new BlockPos(x, y, z), fill);
            if (layers > 1) {
                helper.setBlock(new BlockPos(x, full, z), Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, Math.min(8, layers)));
            }
        }
        return this;
    }

    Course flat(int fromZ, int toZ, double top, Block fill) {
        for (int z = fromZ; z <= toZ; z++) column(z, top, fill);
        return this;
    }

    /** A wall across the lane from {@code fromY} to {@code toY} inclusive. */
    Course wall(int z, int fromY, int toY, Block block) {
        for (int x = LANE_MIN_X; x <= LANE_MAX_X; x++) {
            for (int y = fromY; y <= toY; y++) helper.setBlock(new BlockPos(x, y, z), block);
        }
        return this;
    }

    Course set(int x, int y, int z, BlockState state) {
        helper.setBlock(new BlockPos(x, y, z), state);
        return this;
    }

    Vec3 at(double x, double y, double z) {
        return helper.absoluteVec(new Vec3(x, y, z));
    }

    /** Test-relative position of a player. */
    Vec3 relative(Player player) {
        return helper.relativeVec(player.position());
    }

    /** Places a rider on the lane at test-relative (center, y, z), facing +z, already moving at {@code speed}. */
    void place(Player player, double y, double z, double speed) {
        Vec3 pos = at(CENTER_X, y, z);
        player.setPos(pos.x, pos.y, pos.z);
        player.setYRot(0);
        player.setOnGround(true);
        player.setDeltaMovement(0, 0, speed);
        player.resetFallDistance();
    }

    /** A mock survival player wearing a snowboard and snowboard boots, strapped in. */
    static Player boarder(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        equipBoard(player);
        return player;
    }

    static void equipBoard(Player player) {
        GearAccess.current().set(player, GearAccess.Slot.BOARD, new ItemStack(ModItems.SNOWBOARD.get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.SNOWBOARD_BOOTS.get()));
        StrapCheck.Result result = StrapController.tryStrap(player);
        if (!result.ok()) throw new GameTestAssertException("could not strap the test rider in: " + result);
    }
}
