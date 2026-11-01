package io.github.adang101.alpineshred.gametest;

import io.github.adang101.alpineshred.lift.LiftDriveStationBlockEntity;
import io.github.adang101.alpineshred.lift.LiftLine;
import io.github.adang101.alpineshred.lift.LiftManager;
import io.github.adang101.alpineshred.lift.LiftMotion;
import io.github.adang101.alpineshred.lift.LiftSavedData;
import io.github.adang101.alpineshred.lift.LiftNodeBlockEntity;
import io.github.adang101.alpineshred.lift.LiftReturnStationBlockEntity;
import io.github.adang101.alpineshred.lift.LiftTowerHeadBlockEntity;
import io.github.adang101.alpineshred.lift.PendingLine;
import io.github.adang101.alpineshred.registry.ModBlocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.github.adang101.alpineshred.gametest.RideGameTests.check;

/**
 * Phase 8 game tests, work package 1 (design doc section 22): lift lines survive the NBT round trip of
 * their saved data, a pending line is adopted once, and the manager moves a line exactly as
 * {@link LiftMotion#advance} predicts. Each test removes the lines it makes, since the level's saved
 * data outlives the test.
 */
public final class LiftGameTests {
    private LiftGameTests() {
    }

    /** Drive, {@code heads} heads and a return station, 40 blocks apart and stepping down. */
    private static List<BlockPos> nodes(GameTestHelper helper, int heads) {
        BlockPos base = helper.absolutePos(new BlockPos(1, 1, 1));
        List<BlockPos> list = new ArrayList<>();
        for (int i = 0; i < heads + 2; i++) list.add(base.offset(i % 2 == 0 ? 0 : 7, 100 - 6 * i, 40 * i));
        return list;
    }

    public static void savedDataRoundTrip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LiftLine line = LiftManager.create(level, nodes(helper, 4));
        check(line != null, "the line was not created");
        try {
            line.travel = 123.456789012345;
            line.speed = 1.3;
            line.targetSpeed = 2.5;
            line.powered = true;
            line.faulted = true;
            line.emergency = true;
            CompoundTag saved = LiftSavedData.get(level).save(new CompoundTag());
            LiftSavedData fresh = LiftSavedData.load(saved);
            LiftLine back = fresh.get(line.id);
            check(back != null, "the line did not come back");
            check(back.id.equals(line.id), "id");
            check(back.drive.equals(line.drive), "drive");
            check(back.returnStation.equals(line.returnStation), "return station");
            check(back.heads.equals(line.heads), "heads or their order: " + back.heads + " vs " + line.heads);
            check(back.heads.size() == 4, "head count");
            check(back.travel == line.travel, "travel");
            check(back.speed == line.speed, "speed");
            check(back.targetSpeed == line.targetSpeed, "target speed");
            check(back.powered && back.faulted && back.emergency, "flags");
            check(back.path().loopLength() == line.path().loopLength(), "rebuilt path");
            check(back.chairCount() == line.chairCount() && back.chairCount() > 1, "chair count");

            // A pending line is adopted once, however often its blocks load.
            UUID id = UUID.randomUUID();
            List<BlockPos> pendingNodes = nodes(helper, 2).stream().map(p -> p.offset(500, 0, 0)).toList();
            PendingLine pending = PendingLine.read(new PendingLine(id, pendingNodes).write());
            check(pending.id().equals(id) && pending.nodes().equals(pendingNodes), "pending line round trip");
            int before = LiftManager.lines(level).size();
            LiftLine first = LiftManager.adoptPending(level, pending);
            LiftLine second = LiftManager.adoptPending(level, pending);
            check(first != null && first == second, "the second adoption made another line");
            check(LiftManager.lines(level).size() == before + 1, "exactly one line for the pending one");
            check(first.id.equals(id), "the pending UUID is the line's id");
            LiftManager.remove(level, id);
        } finally {
            LiftManager.remove(level, line.id);
        }
        helper.succeed();
    }

    public static void travelAtFixedTicks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LiftLine line = LiftManager.create(level, nodes(helper, 3));
        check(line != null, "the line was not created");
        try {
            LiftManager.setDrive(level, line.id, true, 2.5);
            double loop = line.path().loopLength();
            double[] expected = LiftMotion.advance(0, 0, 2.5, false, loop, 200);
            for (int i = 0; i < 200; i++) LiftManager.tick(level);
            check(line.age() == 200, "stepped " + line.age() + " times");
            check(Math.abs(line.speed - expected[0]) < 1e-6, "speed " + line.speed + " vs " + expected[0]);
            check(Math.abs(line.travel - expected[1]) < 1e-6, "travel " + line.travel + " vs " + expected[1]);
            check(line.speed > 0 && line.travel > 0, "the line did not move");

            // A fault stops it at the emergency rate, whatever the drive says.
            LiftManager.fault(level, line.id, "test");
            double[] stopping = LiftMotion.advance(line.speed, line.travel, 0, true, loop, 10);
            for (int i = 0; i < 10; i++) LiftManager.tick(level);
            check(Math.abs(line.speed - stopping[0]) < 1e-6 && Math.abs(line.travel - stopping[1]) < 1e-6, "emergency slowing");
        } finally {
            LiftManager.remove(level, line.id);
        }
        helper.succeed();
    }

    // ---- work package 2: blocks and block entities ----

    /** A drive, one head and a return station six blocks apart, placed with their block entities. */
    private record Built(BlockPos drive, BlockPos head, BlockPos ret) {
        List<BlockPos> nodes() {
            return List.of(drive, head, ret);
        }

        LiftDriveStationBlockEntity driveBe(ServerLevel level) {
            return (LiftDriveStationBlockEntity) level.getBlockEntity(drive);
        }

        LiftTowerHeadBlockEntity headBe(ServerLevel level) {
            return (LiftTowerHeadBlockEntity) level.getBlockEntity(head);
        }

        LiftReturnStationBlockEntity returnBe(ServerLevel level) {
            return (LiftReturnStationBlockEntity) level.getBlockEntity(ret);
        }

        void clear(ServerLevel level) {
            for (BlockPos p : nodes()) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static Built place(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(1, 2, 1));
        Built b = new Built(base, base.offset(0, 1, 6), base.offset(0, 0, 12));
        level.setBlock(b.drive, ModBlocks.LIFT_DRIVE_STATION.get().defaultBlockState(), 3);
        level.setBlock(b.head, ModBlocks.LIFT_TOWER_HEAD.get().defaultBlockState(), 3);
        level.setBlock(b.ret, ModBlocks.LIFT_RETURN_STATION.get().defaultBlockState(), 3);
        check(b.driveBe(level) != null && b.headBe(level) != null && b.returnBe(level) != null, "block entities were not created");
        return b;
    }

    /** Writes the data a linker would: the line id on every block entity, and the head's neighbours. */
    private static void link(ServerLevel level, Built b, LiftLine line) {
        b.driveBe(level).setLineId(line.id);
        b.headBe(level).setLink(line.id, b.drive, b.ret);
        b.returnBe(level).setLineId(line.id);
    }

    public static void breakingAHeadFaultsTheLine(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Built b = place(helper);
        LiftLine line = LiftManager.create(level, b.nodes());
        check(line != null, "the line was not created");
        try {
            link(level, b, line);
            LiftManager.setDrive(level, line.id, true, 2.5);
            for (int i = 0; i < 100; i++) LiftManager.tick(level);
            check(line.speed > 1.0, "the line did not get up to speed: " + line.speed);
            check(!line.faulted, "faulted before anything broke");
            double speed = line.speed, travel = line.travel;

            check(level.destroyBlock(b.head, false), "the head did not break");
            check(line.faulted, "breaking the head did not fault the line");
            check(line.faultReason.contains("head broken at"), "fault reason: " + line.faultReason);

            double[] expected = LiftMotion.advance(speed, travel, 0, true, line.path().loopLength(), 10);
            for (int i = 0; i < 10; i++) LiftManager.tick(level);
            check(Math.abs(line.speed - (speed - 10 * LiftMotion.EMERGENCY_DECELERATION / LiftMotion.TICKS_PER_SECOND)) < 1e-9,
                    "speed fell to " + line.speed + " from " + speed + ", not at 2.0 b/s^2");
            check(Math.abs(line.speed - expected[0]) < 1e-9 && Math.abs(line.travel - expected[1]) < 1e-9, "motion differs from LiftMotion");
        } finally {
            LiftManager.remove(level, line.id);
            b.clear(level);
        }
        helper.succeed();
    }

    public static void mismatchedBlockEntityFaultsTheLine(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Built b = place(helper);
        LiftLine line = LiftManager.create(level, b.nodes());
        check(line != null, "the line was not created");
        try {
            link(level, b, line);
            // Data that agrees with the line passes, and a line id the manager has never heard of is dropped.
            b.driveBe(level).loadCheck(level);
            b.headBe(level).loadCheck(level);
            b.returnBe(level).loadCheck(level);
            check(!line.faulted, "consistent block entities faulted the line: " + line.faultReason);
            b.returnBe(level).setLineId(UUID.randomUUID());
            b.returnBe(level).loadCheck(level);
            check(b.returnBe(level).lineId() == null, "a stale line id was kept");
            check(!line.faulted, "a stale line id faulted another line");

            // The head thinks its next node is somewhere else.
            b.headBe(level).setLink(line.id, b.drive, b.ret.offset(5, 0, 5));
            b.headBe(level).loadCheck(level);
            check(line.faulted, "a mismatched next node did not fault the line");
            check(line.faultReason.contains("head"), "fault reason: " + line.faultReason);
        } finally {
            LiftManager.remove(level, line.id);
            b.clear(level);
        }
        helper.succeed();
    }

    public static void pendingLineAdoptedOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Built b = place(helper);
        UUID id = UUID.randomUUID();
        PendingLine pending = new PendingLine(id, b.nodes());
        try {
            int before = LiftManager.lines(level).size();
            b.driveBe(level).setPending(pending);
            b.headBe(level).setPending(pending);
            b.returnBe(level).setPending(pending);
            // The save and reload round trip of a block entity keeps the pending line.
            CompoundTag saved = b.headBe(level).saveWithFullMetadata();
            check(saved.contains("Pending"), "the pending line was not saved");

            for (LiftNodeBlockEntity be : List.<LiftNodeBlockEntity>of(b.headBe(level), b.returnBe(level), b.driveBe(level))) {
                be.loadCheck(level);
            }
            check(LiftManager.lines(level).size() == before + 1, "expected exactly one new line, got " + (LiftManager.lines(level).size() - before));
            LiftLine line = LiftManager.get(level, id);
            check(line != null && line.nodePositions().equals(b.nodes()), "the adopted line's nodes");
            check(!line.faulted, "the adopted line is faulted: " + line.faultReason);
            for (LiftNodeBlockEntity be : List.<LiftNodeBlockEntity>of(b.driveBe(level), b.headBe(level), b.returnBe(level))) {
                check(id.equals(be.lineId()), "a block entity does not hold the line id");
                check(be.pending() == null, "the pending line was not cleared");
            }
            check(b.drive.equals(b.headBe(level).prev()) && b.ret.equals(b.headBe(level).next()), "the head's neighbours were not filled in");
            // Loading again changes nothing.
            b.headBe(level).loadCheck(level);
            check(LiftManager.lines(level).size() == before + 1 && !line.faulted, "a second load check changed the line");
        } finally {
            LiftManager.remove(level, id);
            b.clear(level);
        }
        helper.succeed();
    }

    public static void everyLiftBlockDropsItself(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        check(ModBlocks.liftBlocks().size() == 6, "expected six lift blocks");
        for (var supplier : ModBlocks.liftBlocks()) {
            Block block = supplier.get();
            LootParams.Builder params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))))
                    .withParameter(LootContextParams.TOOL, new ItemStack(Items.IRON_PICKAXE));
            List<ItemStack> drops = block.defaultBlockState().getDrops(params);
            String name = supplier.getId().getPath();
            check(drops.size() == 1, name + " dropped " + drops.size() + " stacks");
            check(drops.get(0).is(block.asItem()) && drops.get(0).getCount() == 1, name + " dropped " + drops.get(0));
        }
        helper.succeed();
    }
}
