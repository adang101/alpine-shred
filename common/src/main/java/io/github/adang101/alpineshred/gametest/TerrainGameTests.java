package io.github.adang101.alpineshred.gametest;

import io.github.adang101.alpineshred.gametest.RideGameTests.Ride;
import io.github.adang101.alpineshred.movement.RideInput;
import io.github.adang101.alpineshred.movement.RideState;
import io.github.adang101.alpineshred.movement.SlideController;
import io.github.adang101.alpineshred.trick.GrabType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import static io.github.adang101.alpineshred.gametest.RideGameTests.check;

/**
 * Feedback round 1 game tests: braking down a steep slope, riding through leaves, and tree trunks
 * (README clarifications 92 to 94). Like {@link RideGameTests}, the controller is driven tick by tick
 * through {@link Ride} on real collision, in the {@value RideGameTests#LARGE} template.
 */
public final class TerrainGameTests {
    private TerrainGameTests() {
    }

    private static final RideInput SNEAK = new RideInput(0, 0, 0, true, false, GrabType.NONE);

    /** A 1-in-2 voxel staircase down (about 27 degrees) is steeper than a rider's friction holds; the brake still does. */
    public static void brakingOnASteepSlopeStopsAndHolds(GameTestHelper helper) {
        Course course = new Course(helper);
        for (int z = 0; z <= 43; z++) course.column(z, 22 - Math.floor(z / 2.0), Blocks.SNOW_BLOCK);
        Player coaster = Course.boarder(helper);
        Player braker = Course.boarder(helper);
        // Two riders on the same lane would collide with each other; they start at opposite edges of it.
        course.place(coaster, 22, 1.5, 0.8);
        course.place(braker, 22, 1.5, 0.8);
        coaster.setPos(course.at(2.5, 22, 1.5));
        braker.setPos(course.at(6.5, 22, 1.5));
        Ride free = new Ride(coaster);
        Ride brake = new Ride(braker);

        int stoppedAt = -1;
        double freeTop = 0;
        for (int tick = 0; tick < 100; tick++) {
            free.tick(RideInput.coast(0));
            if (course.relative(coaster).z < 40) freeTop = Math.max(freeTop, free.c().speed());
            brake.tick(SNEAK);
            check(brake.c().state() != RideState.WIPEOUT, "wiped out while braking at tick " + tick + " " + brake.describe());
            if (stoppedAt < 0 && brake.c().speed() < 0.02) stoppedAt = tick;
        }
        check(stoppedAt >= 0 && stoppedAt < 90, "the braking rider never stopped: " + brake.describe());
        check(freeTop > 0.5, "the control rider should be running away down the slope, top speed " + freeTop);
        double stopZ = course.relative(braker).z;
        for (int tick = 0; tick < 40; tick++) brake.tick(SNEAK);
        check(brake.c().speed() < 0.02, "the stopped rider slid off: " + brake.describe());
        check(course.relative(braker).z - stopZ < 0.5, "the stopped rider crept " + (course.relative(braker).z - stopZ) + " blocks");
        helper.succeed();
    }

    /** The collision mixin: leaves are empty to a strapped rider and solid to everyone else. */
    public static void leavesHaveNoCollisionForRidersOnly(GameTestHelper helper) {
        Course course = new Course(helper);
        course.flat(0, 30, 2, Blocks.SNOW_BLOCK);
        BlockPos pos = new BlockPos(4, 2, 10);
        helper.setBlock(pos, persistentLeaves());
        BlockState leaves = helper.getBlockState(pos);
        BlockPos absolute = helper.absolutePos(pos);
        Player rider = Course.boarder(helper);
        Player walker = helper.makeMockSurvivalPlayer();
        check(leaves.getCollisionShape(helper.getLevel(), absolute, CollisionContext.of(rider)).isEmpty(),
                "a strapped rider is stopped by leaves");
        check(!leaves.getCollisionShape(helper.getLevel(), absolute, CollisionContext.of(walker)).isEmpty(),
                "an unstrapped player walks through leaves");
        check(!leaves.getCollisionShape(helper.getLevel(), absolute, CollisionContext.empty()).isEmpty(),
                "a query with no entity sees leaves as solid");
        helper.succeed();
    }

    public static void ridingIntoLeavesPassesThroughAndSlowsDown(GameTestHelper helper) {
        Course course = new Course(helper);
        course.flat(0, 40, 2, Blocks.SNOW_BLOCK);
        // Three blocks deep, two high: a hedge of leaves across the left half of the lane.
        for (int x = 2; x <= 4; x++) {
            for (int z = 12; z <= 14; z++) {
                for (int y = 2; y <= 3; y++) helper.setBlock(new BlockPos(x, y, z), persistentLeaves());
            }
        }
        Player leafy = Course.boarder(helper);
        Player clear = Course.boarder(helper);
        course.place(leafy, 2, 0.8, 0.8);
        course.place(clear, 2, 0.8, 0.8);
        leafy.setPos(course.at(3.0, 2, 0.8));
        clear.setPos(course.at(6.0, 2, 0.8));
        Ride through = new Ride(leafy);
        Ride control = new Ride(clear);

        int entries = 0;
        for (int tick = 0; tick < 40; tick++) {
            through.tick(RideInput.coast(0));
            control.tick(RideInput.coast(0));
            for (SlideController.Event e : through.c().events()) {
                if (e instanceof SlideController.ThroughLeaves) entries++;
            }
            check(through.c().state() != RideState.WIPEOUT, "leaves wiped the rider out at tick " + tick + " " + through.describe());
        }
        check(course.relative(leafy).z > 16, "did not pass through the leaves: " + through.describe());
        check(entries == 1, "expected one ThroughLeaves event, got " + entries);
        check(through.c().speed() < control.c().speed() * 0.7,
                "leaves did not slow the rider: " + through.describe() + " vs " + control.describe());
        helper.succeed();
    }

    /** The same moderate speed wipes out on a log and is only a bump against stone (0.2 tree, 0.5 wall). */
    public static void aLogWipesOutAtModerateSpeedButStoneDoesNot(GameTestHelper helper) {
        Course course = new Course(helper);
        course.flat(0, 30, 2, Blocks.SNOW_BLOCK);
        for (int y = 2; y <= 4; y++) {
            for (int x = 2; x <= 4; x++) helper.setBlock(new BlockPos(x, y, 14), Blocks.OAK_LOG.defaultBlockState());
            for (int x = 5; x <= 6; x++) helper.setBlock(new BlockPos(x, y, 14), Blocks.STONE.defaultBlockState());
        }
        Player intoLog = Course.boarder(helper);
        Player intoStone = Course.boarder(helper);
        // 0.4 at the start is about 0.35 at the wall: over the tree speed (0.2), under the wall speed (0.5).
        course.place(intoLog, 2, 6.0, 0.4);
        course.place(intoStone, 2, 6.0, 0.4);
        intoLog.setPos(course.at(3.0, 2, 6.0));
        intoStone.setPos(course.at(5.7, 2, 6.0));
        Ride log = new Ride(intoLog);
        Ride stone = new Ride(intoStone);

        boolean loggedWipeout = false, stoneWipeout = false;
        double impact = Double.NaN;
        for (int tick = 0; tick < 60; tick++) {
            log.tick(RideInput.coast(0));
            stone.tick(RideInput.coast(0));
            for (SlideController.Event e : log.c().events()) {
                if (e instanceof SlideController.WipedOut w) {
                    loggedWipeout = true;
                    impact = w.impactSpeed();
                }
            }
            for (SlideController.Event e : stone.c().events()) {
                if (e instanceof SlideController.WipedOut) stoneWipeout = true;
            }
        }
        check(loggedWipeout, "no wipeout into the log: " + log.describe());
        check(impact < 0.5, "the log hit should be below the wall speed: " + impact);
        check(!stoneWipeout, "wiped out into stone at the same speed: " + stone.describe());
        Vec3 at = course.relative(intoStone);
        check(at.z > 12, "the stone rider did not reach the wall: " + stone.describe());
        helper.succeed();
    }

    private static BlockState persistentLeaves() {
        return Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    }
}
