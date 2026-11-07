package io.github.adang101.alpineshred.gametest;

import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.compat.create.CreateBridge;
import io.github.adang101.alpineshred.compat.create.CreateCompat;
import io.github.adang101.alpineshred.lift.LiftDriveSource;
import io.github.adang101.alpineshred.lift.LiftDriveStationBlockEntity;
import io.github.adang101.alpineshred.lift.LiftLine;
import io.github.adang101.alpineshred.lift.LiftManager;
import io.github.adang101.alpineshred.lift.LiftMotion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

/**
 * Game tests of the kinetic lift drive (phase 9 plan, WP3; README 78). They need Create's real kinetic network, so
 * they run only in the "with Create" column and succeed at once elsewhere ({@link CreateGameTests#inCreateColumn()}).
 * The rig is a drive station, a kinetic drive against its east face with its shafts along X, and Create's creative
 * motor beyond it facing back at it; the motor's rpm is the knob. The tests read the saved line the way the lift
 * code does, and wait on real ticks because Create propagates rotation from its own block entity ticks.
 */
public final class KineticDriveGameTests {
    /** The kinetic drive's budget, README 78 (phase 9 decision 6): ms a tick per drive. */
    static final double DRIVE_BUDGET_MS = 0.02;

    private KineticDriveGameTests() {
    }

    private record Rig(ServerLevel level, LiftGameTests.Built built, LiftLine line, BlockPos kinetic, BlockPos motor) {
        LiftDriveStationBlockEntity station() {
            return built.driveBe(level);
        }

        void tune(int rpm) {
            CreateCompat.bridge().testPlaceMotor(level, motor, Direction.WEST, rpm);
        }

        void clear() {
            LiftManager.remove(level, line.id);
            built.clear(level);
            level.setBlock(kinetic, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(motor, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    /** A linked line with a kinetic drive and motor attached (the motor turns at {@code rpm}, or is left out when 0). */
    private static Rig rig(GameTestHelper helper, int rpm) {
        ServerLevel level = helper.getLevel();
        LiftGameTests.Built b = LiftGameTests.place(helper);
        LiftLine line = LiftManager.create(level, b.nodes());
        check(line != null, "the line was not created");
        LiftGameTests.link(level, b, line);
        CreateBridge bridge = CreateCompat.bridge();
        BlockPos kinetic = b.drive().east();
        BlockPos motor = kinetic.east();
        level.setBlock(kinetic, bridge.testKineticDriveState(Direction.Axis.X), 3);
        Rig rig = new Rig(level, b, line, kinetic, motor);
        if (rpm > 0) rig.tune(rpm);
        return rig;
    }

    private static boolean runningAt(LiftLine line, double speed) {
        return line.powered && !line.emergency && Math.abs(line.targetSpeed - speed) < 1e-9;
    }

    /** Rotation drives the line, and its speed follows the rpm through LiftMotion's ramp. */
    public static void kineticRotationRunsTheLine(GameTestHelper helper) {
        if (!CreateGameTests.inCreateColumn()) {
            helper.succeed();
            return;
        }
        Rig rig = rig(helper, 64);
        double[] before = new double[2];
        helper.startSequence()
                .thenWaitUntil(() -> {
                    check(rig.station().kineticSource() != null, "the station does not see the kinetic drive");
                    check(runningAt(rig.line, 1.0), "64 rpm should be 1.0 b/s: powered " + rig.line.powered + ", emergency " + rig.line.emergency + ", target " + rig.line.targetSpeed);
                })
                .thenWaitUntil(() -> check(rig.line.speed >= 1.0 - 1e-9, "speed " + rig.line.speed + " has not reached 1.0"))
                .thenExecute(() -> rig.tune(128))
                .thenWaitUntil(() -> check(runningAt(rig.line, 2.0), "128 rpm should be 2.0 b/s, target is " + rig.line.targetSpeed))
                .thenExecute(() -> {
                    before[0] = rig.line.speed;
                    before[1] = rig.line.age();
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    long ticks = rig.line.age() - (long) before[1];
                    double expected = before[0] + ticks * LiftMotion.ACCELERATION / LiftMotion.TICKS_PER_SECOND;
                    check(ticks >= 20 && expected < 2.0, "test setup: " + ticks + " ticks, expected " + expected);
                    check(Math.abs(rig.line.speed - expected) < 1e-9, "speed " + rig.line.speed + " is not on the ramp (" + expected + " after " + ticks + " ticks)");
                })
                .thenExecute(() -> rig.tune(256))
                .thenWaitUntil(() -> check(runningAt(rig.line, 4.0), "256 rpm should be the 4.0 b/s cap, target is " + rig.line.targetSpeed))
                .thenExecute(rig::clear)
                .thenSucceed();
    }

    /** Losing rotation is an emergency stop at LiftMotion's emergency rate; rotation back clears it, as redstone does. */
    public static void kineticLostRotationIsAnEmergencyStop(GameTestHelper helper) {
        if (!CreateGameTests.inCreateColumn()) {
            helper.succeed();
            return;
        }
        Rig rig = rig(helper, 64);
        double[] before = new double[2];
        helper.startSequence()
                .thenWaitUntil(() -> check(runningAt(rig.line, 1.0) && rig.line.speed >= 1.0 - 1e-9, "not up to speed: " + rig.line.speed))
                .thenExecute(() -> rig.level.setBlock(rig.motor, Blocks.AIR.defaultBlockState(), 3))
                .thenWaitUntil(() -> {
                    check(rig.line.emergency, "losing rotation did not stop the line");
                    check(!rig.line.powered, "the line is still powered");
                })
                .thenExecute(() -> {
                    before[0] = rig.line.speed;
                    before[1] = rig.line.age();
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    long ticks = rig.line.age() - (long) before[1];
                    double expected = before[0] - ticks * LiftMotion.EMERGENCY_DECELERATION / LiftMotion.TICKS_PER_SECOND;
                    check(ticks >= 5 && expected > 0, "test setup: " + ticks + " ticks, expected " + expected);
                    check(Math.abs(rig.line.speed - expected) < 1e-9, "speed " + rig.line.speed + " is not slowing at 2.0 b/s^2 (" + expected + ")");
                })
                .thenExecute(() -> rig.tune(64))
                .thenWaitUntil(() -> check(runningAt(rig.line, 1.0), "rotation back did not restart the line: emergency " + rig.line.emergency + ", powered " + rig.line.powered))
                .thenExecute(rig::clear)
                .thenSucceed();
    }

    /** Overstress is an emergency stop too, and relief clears it. */
    public static void kineticOverstressStopsTheLine(GameTestHelper helper) {
        if (!CreateGameTests.inCreateColumn()) {
            helper.succeed();
            return;
        }
        Rig rig = rig(helper, 64);
        CreateBridge bridge = CreateCompat.bridge();
        helper.startSequence()
                .thenWaitUntil(() -> check(runningAt(rig.line, 1.0) && rig.line.speed >= 1.0 - 1e-9, "not up to speed: " + rig.line.speed))
                .thenExecute(() -> bridge.testSetOverstressed(rig.level, rig.kinetic, true))
                .thenWaitUntil(() -> check(rig.line.emergency && !rig.line.powered, "overstress did not stop the line"))
                .thenExecute(() -> bridge.testSetOverstressed(rig.level, rig.kinetic, false))
                .thenWaitUntil(() -> check(runningAt(rig.line, 1.0), "relief did not restart the line: emergency " + rig.line.emergency + ", powered " + rig.line.powered))
                .thenExecute(rig::clear)
                .thenSucceed();
    }

    /** While a kinetic drive is attached redstone is ignored; remove it and redstone takes over again. */
    public static void kineticDriveOverridesRedstone(GameTestHelper helper) {
        if (!CreateGameTests.inCreateColumn()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        LiftGameTests.Built b = LiftGameTests.place(helper);
        LiftLine line = LiftManager.create(level, b.nodes());
        check(line != null, "the line was not created");
        BlockPos kinetic = b.drive().east();
        try {
            LiftGameTests.link(level, b, line);
            level.setBlock(b.drive().west(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            check(b.driveBe(level).kineticSource() == null && b.driveBe(level).enabled(), "redstone should run a station with no kinetic drive");
            check(line.powered, "redstone did not power the line");

            // A kinetic drive that is not turning: the signal next to the station no longer counts.
            level.setBlock(kinetic, CreateCompat.bridge().testKineticDriveState(Direction.Axis.X), 3);
            check(b.driveBe(level).kineticSource() != null, "the station does not see the kinetic drive");
            check(!b.driveBe(level).enabled(), "redstone still enables a station with a kinetic drive");
            check(!line.powered, "the line is still powered by redstone beside a kinetic drive");

            level.setBlock(kinetic, Blocks.AIR.defaultBlockState(), 3);
            check(b.driveBe(level).kineticSource() == null && b.driveBe(level).enabled(), "redstone did not take over again");
            check(line.powered, "the line was not powered again by redstone");
        } finally {
            LiftManager.remove(level, line.id);
            b.clear(level);
            level.setBlock(kinetic, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(b.drive().west(), Blocks.AIR.defaultBlockState(), 3);
        }
        helper.succeed();
    }

    /** What the drive writes to disk reads back as the same command, and reading it back is not a change. */
    public static void kineticDriveSurvivesSaveAndReload(GameTestHelper helper) {
        if (!CreateGameTests.inCreateColumn()) {
            helper.succeed();
            return;
        }
        Rig rig = rig(helper, 128);
        helper.startSequence()
                .thenWaitUntil(() -> check(runningAt(rig.line, 2.0), "not running at 2.0 b/s: target " + rig.line.targetSpeed))
                .thenExecute(() -> {
                    BlockEntity original = rig.level.getBlockEntity(rig.kinetic);
                    check(original instanceof LiftDriveSource, "no kinetic drive block entity");
                    CompoundTag saved = original.saveWithFullMetadata();
                    BlockEntity copy = BlockEntity.loadStatic(rig.kinetic, rig.level.getBlockState(rig.kinetic), saved);
                    check(copy instanceof LiftDriveSource, "the saved drive did not load");
                    LiftDriveSource a = (LiftDriveSource) original, b = (LiftDriveSource) copy;
                    check(b.enabled() && a.enabled(), "the reloaded drive is not running: " + b.enabled());
                    check(b.targetSpeed() == a.targetSpeed() && b.targetSpeed() == 2.0, "reloaded target " + b.targetSpeed() + " vs " + a.targetSpeed());
                    check(!rig.line.emergency && rig.line.powered, "reading the drive back disturbed the line");
                })
                .thenExecute(rig::clear)
                .thenSucceed();
    }

    /** The drive's own block entity tick, over a running drive, stays under 3x {@link #DRIVE_BUDGET_MS} (decision 6). */
    @SuppressWarnings("unchecked")
    public static void kineticDriveBudget(GameTestHelper helper) {
        if (!CreateGameTests.inCreateColumn()) {
            helper.succeed();
            return;
        }
        Rig rig = rig(helper, 256);
        helper.startSequence()
                .thenWaitUntil(() -> check(runningAt(rig.line, 4.0), "not running at 4.0 b/s: target " + rig.line.targetSpeed))
                .thenExecute(() -> {
                    BlockState state = rig.level.getBlockState(rig.kinetic);
                    BlockEntity be = rig.level.getBlockEntity(rig.kinetic);
                    BlockEntityTicker<BlockEntity> ticker = (BlockEntityTicker<BlockEntity>) ((EntityBlock) state.getBlock())
                            .getTicker(rig.level, state, (BlockEntityType<BlockEntity>) be.getType());
                    check(ticker != null, "the kinetic drive has no ticker");
                    int warmUp = 500, measured = 4000;
                    long nanos = 0;
                    for (int i = 0; i < warmUp + measured; i++) {
                        long start = System.nanoTime();
                        ticker.tick(rig.level, rig.kinetic, state, be);
                        if (i >= warmUp) nanos += System.nanoTime() - start;
                    }
                    double ms = nanos / 1.0e6 / measured;
                    AlpineShred.LOGGER.info(String.format(Locale.ROOT, "[kinetic budget] %d ticks: %.5f ms a tick per drive (budget %.2f)", measured, ms, DRIVE_BUDGET_MS));
                    check(ms < 3 * DRIVE_BUDGET_MS, String.format(Locale.ROOT, "a kinetic drive takes %.5f ms a tick, over 3x the %.2f ms budget", ms, DRIVE_BUDGET_MS));
                    check(runningAt(rig.line, 4.0), "measuring disturbed the line");
                })
                .thenExecute(rig::clear)
                .thenSucceed();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
