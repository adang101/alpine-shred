package io.github.adang101.alpineshred.gametest;

import io.github.adang101.alpineshred.lift.LiftDriveStationBlockEntity;
import io.github.adang101.alpineshred.lift.LiftLine;
import io.github.adang101.alpineshred.lift.LiftLinkerItem;
import io.github.adang101.alpineshred.registry.ModItems;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import io.github.adang101.alpineshred.lift.LiftManager;
import io.github.adang101.alpineshred.lift.LiftMotion;
import io.github.adang101.alpineshred.lift.LiftSavedData;
import io.github.adang101.alpineshred.lift.LiftNodeBlockEntity;
import io.github.adang101.alpineshred.lift.LiftReturnStationBlockEntity;
import io.github.adang101.alpineshred.lift.LiftTowerHeadBlockEntity;
import io.github.adang101.alpineshred.lift.PendingLine;
import io.github.adang101.alpineshred.config.AlpineConfig;
import io.github.adang101.alpineshred.lift.ChairEntity;
import io.github.adang101.alpineshred.lift.LiftChairs;
import io.github.adang101.alpineshred.lift.PassengerRule;
import io.github.adang101.alpineshred.park.V3;
import io.github.adang101.alpineshred.registry.ModBlocks;
import io.github.adang101.alpineshred.registry.ModEntities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.AABB;
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

    // ---- work package 3: the linker ----

    /** Uses the player's linker on the block at {@code pos}, as a right-click on its top does. */
    private static void useLinker(Player player, BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
        player.getItemInHand(InteractionHand.MAIN_HAND).useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    /** A drive, three heads and a return station, 10 blocks apart along +z, with a gentle sideways wander. */
    private static List<BlockPos> linkerNodes(GameTestHelper helper) {
        // The test worlds have real terrain (hills, snow), so stand well above the highest ground under the line.
        BlockPos flat = helper.absolutePos(new BlockPos(3, 2, 1));
        int top = Integer.MIN_VALUE;
        for (int dx = -8; dx <= 8; dx += 2) {
            for (int dz = -2; dz <= 42; dz += 2) {
                top = Math.max(top, helper.getLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, flat.getX() + dx, flat.getZ() + dz));
            }
        }
        BlockPos base = new BlockPos(flat.getX(), Math.max(flat.getY(), top) + 8, flat.getZ());
        return List.of(base, base.offset(1, 0, 10), base.offset(-1, 0, 20), base.offset(1, 0, 30), base.offset(0, 0, 40));
    }

    private static void placeNodes(ServerLevel level, List<BlockPos> nodes) {
        for (int i = 0; i < nodes.size(); i++) {
            var block = i == 0 ? ModBlocks.LIFT_DRIVE_STATION : i == nodes.size() - 1 ? ModBlocks.LIFT_RETURN_STATION : ModBlocks.LIFT_TOWER_HEAD;
            level.setBlock(nodes.get(i), block.get().defaultBlockState(), 3);
        }
    }

    /** What the validator says about a chain, with the blocks it blocked on, for failure messages. */
    private static String diagnose(ServerLevel level, List<BlockPos> chain) {
        List<io.github.adang101.alpineshred.park.V3> v = new ArrayList<>();
        for (BlockPos p : chain) v.add(LiftLine.node(p));
        var r = io.github.adang101.alpineshred.lift.LinkValidator.checkAdd(v, (x, y, z) -> {
            BlockPos p = new BlockPos(x, y, z);
            return !level.hasChunkAt(p) || !level.getBlockState(p).getCollisionShape(level, p).isEmpty();
        }, new io.github.adang101.alpineshred.lift.LinkValidator.Limits(64, 48, 1200, 64));
        return r + (r.ok() ? "" : " block " + level.getBlockState(new BlockPos(r.bx(), r.by(), r.bz())) + " loaded=" + level.hasChunkAt(new BlockPos(r.bx(), r.by(), r.bz())));
    }

    private static LiftNodeBlockEntity nodeBe(ServerLevel level, BlockPos pos) {
        return (LiftNodeBlockEntity) level.getBlockEntity(pos);
    }

    public static void linkerBuildsALine(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<BlockPos> nodes = linkerNodes(helper);
        placeNodes(level, nodes);
        Player player = helper.makeMockPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.LIFT_LINKER.get()));
        UUID id = null;
        try {
            int before = LiftManager.lines(level).size();
            for (int i = 0; i < nodes.size(); i++) {
                useLinker(player, nodes.get(i));
                ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
                if (i < nodes.size() - 1) {
                    check(LiftLinkerItem.chain(stack).equals(nodes.subList(0, i + 1)), "chain after click " + i + ": " + LiftLinkerItem.chain(stack)
                            + ", validator says " + diagnose(level, nodes.subList(0, i + 1)));
                    check(LiftManager.lines(level).size() == before, "a line exists before the return station");
                }
            }
            check(LiftLinkerItem.chain(player.getItemInHand(InteractionHand.MAIN_HAND)).isEmpty(), "the chain was not cleared");
            check(LiftManager.lines(level).size() == before + 1, "expected one new line, got " + (LiftManager.lines(level).size() - before));
            id = nodeBe(level, nodes.get(0)).lineId();
            check(id != null, "the drive has no line id");
            LiftLine line = LiftManager.get(level, id);
            check(line != null && line.nodePositions().equals(nodes), "the line's nodes: " + (line == null ? null : line.nodePositions()));
            check(!line.faulted, "the new line is faulted");
            for (int i = 0; i < nodes.size(); i++) {
                LiftNodeBlockEntity be = nodeBe(level, nodes.get(i));
                check(id.equals(be.lineId()), "node " + i + " holds the wrong line id");
                if (be instanceof LiftTowerHeadBlockEntity head) {
                    check(nodes.get(i - 1).equals(head.prev()), "head " + i + " prev " + head.prev());
                    check(nodes.get(i + 1).equals(head.next()), "head " + i + " next " + head.next());
                }
            }
            // The load check agrees with what the linker wrote.
            for (BlockPos p : nodes) nodeBe(level, p).loadCheck(level);
            check(!line.faulted, "a load check faulted the line: " + line.faultReason);

            // A healthy line is not linked over, but sneak-using a head unlinks it.
            player.setShiftKeyDown(true);
            useLinker(player, nodes.get(2));
            player.setShiftKeyDown(false);
            check(LiftManager.get(level, id) == null, "unlinking did not remove the line");
            for (BlockPos p : nodes) check(nodeBe(level, p).lineId() == null, "a node kept its line id after unlinking");
        } finally {
            if (id != null) LiftManager.remove(level, id);
            for (BlockPos p : nodes) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        }
        helper.succeed();
    }

    public static void linkerRejectsABlockedSpan(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<BlockPos> nodes = linkerNodes(helper);
        placeNodes(level, nodes);
        // A stone wall across the first span, at the height of the cable.
        List<BlockPos> wall = new ArrayList<>();
        for (int dx = -6; dx <= 6; dx++) {
            for (int dy = -1; dy <= 6; dy++) {
                for (int dz = 4; dz <= 6; dz++) wall.add(nodes.get(0).offset(dx, dy, dz));
            }
        }
        for (BlockPos p : wall) level.setBlock(p, Blocks.STONE.defaultBlockState(), 3);
        Player player = helper.makeMockPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.LIFT_LINKER.get()));
        try {
            int before = LiftManager.lines(level).size();
            useLinker(player, nodes.get(0));
            useLinker(player, nodes.get(1));
            check(LiftLinkerItem.chain(player.getItemInHand(InteractionHand.MAIN_HAND)).size() == 2, "the first head should be accepted until the next node fixes its direction");
            useLinker(player, nodes.get(2)); // checks the span through the wall
            List<BlockPos> chain = LiftLinkerItem.chain(player.getItemInHand(InteractionHand.MAIN_HAND));
            check(chain.size() == 2, "the second head should have been rejected, chain: " + chain);
            // Going straight to the return station is rejected too.
            useLinker(player, nodes.get(4));
            check(LiftManager.lines(level).size() == before, "a line was created through a wall");
            for (BlockPos p : nodes) check(nodeBe(level, p).lineId() == null, "a node was linked despite the wall");
        } finally {
            for (BlockPos p : wall) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            for (BlockPos p : nodes) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
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

    // ---- work package 4: chairs ----

    /** A short line near the test: drive, two heads and a return station, 16 blocks apart. */
    private static List<BlockPos> shortLine(GameTestHelper helper) {
        BlockPos base = helper.absolutePos(new BlockPos(1, 2, 1));
        return List.of(base.offset(0, 12, 0), base.offset(3, 10, 16), base.offset(0, 8, 32), base.offset(3, 6, 48));
    }

    private static void cleanUp(ServerLevel level, LiftLine line) {
        for (int i = 0; i < line.chairCount(); i++) {
            ChairEntity chair = LiftChairs.find(level, line.id, i);
            if (chair != null) chair.discard();
        }
        LiftManager.remove(level, line.id);
    }

    private static ChairEntity looseChair(ServerLevel level) {
        return new ChairEntity(ModEntities.LIFT_CHAIR.get(), level);
    }

    private static Cow cow(ServerLevel level, Vec3 at) {
        Cow cow = EntityType.COW.create(level);
        check(cow != null, "no cow");
        cow.setPos(at.x, at.y, at.z);
        return cow;
    }

    public static void chairPositionsAtFixedTicks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LiftLine line = LiftManager.create(level, shortLine(helper));
        check(line != null, "the line was not created");
        ServerPlayer player = RideGameTests.newServerPlayer(helper);
        try {
            LiftManager.setDrive(level, line.id, true, 2.5);
            BlockPos drive = line.drive;
            player.setPos(drive.getX(), drive.getY(), drive.getZ());
            int count = line.chairCount();
            check(count >= 3, "expected several chairs, got " + count);
            for (int i = 0; i < count; i++) {
                check(LiftChairs.spawn(level, line, i) != null, "chair " + i + " was not spawned");
            }
            for (int tick = 0; tick < 100; tick++) {
                LiftManager.tick(level);
                for (int i = 0; i < count; i++) {
                    ChairEntity chair = LiftChairs.find(level, line.id, i);
                    check(chair != null, "chair " + i + " vanished at tick " + tick);
                    chair.tick();
                }
            }
            check(line.travel > 0, "the line did not move");
            for (int i = 0; i < count; i++) {
                ChairEntity chair = LiftChairs.find(level, line.id, i);
                V3 want = line.path().chairPos(line.travel, i, count);
                check(Math.abs(chair.getX() - want.x()) < 1e-6 && Math.abs(chair.getY() - want.y()) < 1e-6
                        && Math.abs(chair.getZ() - want.z()) < 1e-6, "chair " + i + " at " + chair.position() + ", wanted " + want);
                V3 t = line.path().tangent(line.path().chairS(line.travel, i, count));
                double h = Math.sqrt(t.x() * t.x() + t.z() * t.z());
                double yaw = Math.toRadians(chair.getYRot());
                check(Math.abs(-Math.sin(yaw) * h - t.x()) < 1e-3 && Math.abs(Math.cos(yaw) * h - t.z()) < 1e-3,
                        "chair " + i + " yaw " + chair.getYRot() + " does not match the tangent " + t);
            }
        } finally {
            RideGameTests.removeServerPlayer(player);
            cleanUp(level, line);
        }
        helper.succeed();
    }

    public static void chairsSpawnNearPlayersAndDespawnFar(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LiftLine line = LiftManager.create(level, shortLine(helper));
        check(line != null, "the line was not created");
        ServerPlayer player = RideGameTests.newServerPlayer(helper);
        try {
            BlockPos drive = line.drive;
            player.setPos(drive.getX() + 2000, drive.getY(), drive.getZ());
            LiftChairs.update(level, LiftManager.lines(level));
            for (int i = 0; i < line.chairCount(); i++) check(LiftChairs.find(level, line.id, i) == null, "chair " + i + " spawned with nobody near");

            player.setPos(drive.getX(), drive.getY(), drive.getZ());
            LiftChairs.update(level, LiftManager.lines(level));
            LiftChairs.update(level, LiftManager.lines(level));
            int spawned = 0;
            for (int i = 0; i < line.chairCount(); i++) {
                V3 p = line.path().chairPos(line.travel, i, line.chairCount());
                boolean ticking = level.isPositionEntityTicking(BlockPos.containing(p.x(), p.y(), p.z()));
                ChairEntity chair = LiftChairs.find(level, line.id, i);
                check((chair != null) == ticking, "chair " + i + (ticking ? " was not spawned" : " was spawned in a chunk that does not tick"));
                if (chair != null) spawned++;
            }
            check(spawned > 0, "no chair was spawned");
            int present = level.getEntities(ModEntities.LIFT_CHAIR.get(), new AABB(drive).inflate(200), e -> line.id.equals(e.lineId())).size();
            check(present == spawned, present + " chair entities for " + spawned + " chairs: duplicates");

            player.setPos(drive.getX() + 2000, drive.getY(), drive.getZ());
            LiftChairs.update(level, LiftManager.lines(level));
            for (int i = 0; i < line.chairCount(); i++) check(LiftChairs.find(level, line.id, i) == null, "chair " + i + " stayed with nobody near");
        } finally {
            RideGameTests.removeServerPlayer(player);
            cleanUp(level, line);
        }
        helper.succeed();
    }

    public static void blockedMobCannotBoard(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChairEntity chair = looseChair(level);
        Vec3 at = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        for (EntityType<?> type : List.of(EntityType.WARDEN, EntityType.WITHER, EntityType.ENDER_DRAGON, EntityType.RAVAGER)) {
            Entity e = type.create(level);
            check(e != null, "no " + type);
            e.setPos(at.x, at.y, at.z);
            check(!PassengerRule.allows(e), type + " passes the rule");
            check(!e.startRiding(chair), type + " boarded");
            check(chair.getPassengers().isEmpty(), type + " is a passenger");
        }
        Cow cow = cow(level, at);
        check(cow.startRiding(chair), "a cow could not board");
        helper.succeed();
    }

    public static void mobBoardsAndIsPersistent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChairEntity chair = looseChair(level);
        Vec3 at = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        Cow plain = cow(level, at);
        Cow kept = cow(level, at);
        kept.setPersistenceRequired();
        check(!plain.isPersistenceRequired() && kept.isPersistenceRequired(), "unexpected starting flags");
        check(plain.startRiding(chair) && kept.startRiding(chair), "the cows could not board");
        check(plain.isPersistenceRequired() && kept.isPersistenceRequired(), "boarded mobs are not persistent");
        check(Boolean.FALSE.equals(chair.originalPersistence(plain.getUUID())), "the original flag was not kept");

        // The chair's save data carries the flags, so they survive a reload.
        CompoundTag saved = new CompoundTag();
        check(chair.save(saved), "the chair did not save");
        ChairEntity reloaded = looseChair(level);
        reloaded.load(saved);
        check(Boolean.FALSE.equals(reloaded.originalPersistence(plain.getUUID())), "the original flag was not saved");

        plain.stopRiding();
        kept.stopRiding();
        check(!plain.isPersistenceRequired(), "the flag was not restored on dismount");
        check(kept.isPersistenceRequired(), "a mob that was persistent before is no longer");
        check(chair.originalPersistence(plain.getUUID()) == null, "the saved flag stayed after dismount");
        helper.succeed();
    }

    public static void seatsAreLimited(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int seats = AlpineConfig.LIFT_SEATS.get();
        try {
            AlpineConfig.LIFT_SEATS.set(2);
            ChairEntity chair = looseChair(level);
            chair.setPos(100, 100, 100);
            chair.setYRot(0);
            Vec3 at = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
            Cow a = cow(level, at), b = cow(level, at), c = cow(level, at);
            check(a.startRiding(chair) && b.startRiding(chair), "two cows should fit");
            check(!c.startRiding(chair), "a third passenger boarded a two-seat chair");
            check(chair.getPassengers().size() == 2, "passenger count " + chair.getPassengers().size());
            a.rideTick();
            b.rideTick();
            check(Math.abs(a.position().distanceTo(b.position()) - PassengerRule.SEAT_SPACING) < 1e-6,
                    "seats " + a.position().distanceTo(b.position()) + " apart");
            check(Math.abs((a.getX() + b.getX()) / 2 - 100) < 1e-6 && Math.abs((a.getZ() + b.getZ()) / 2 - 100) < 1e-6, "seats are not centred");
            AlpineConfig.LIFT_SEATS.set(1);
            // With one seat configured, a chair already holding two refuses more, and a fresh one takes one.
            ChairEntity single = looseChair(level);
            Cow d = cow(level, at), e = cow(level, at);
            check(d.startRiding(single) && !e.startRiding(single), "one seat takes one passenger");
        } finally {
            AlpineConfig.LIFT_SEATS.set(seats);
        }
        helper.succeed();
    }

    public static void duplicateChairKeepsTheOneWithRiders(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LiftLine line = LiftManager.create(level, shortLine(helper));
        check(line != null, "the line was not created");
        try {
            ChairEntity first = LiftChairs.spawn(level, line, 0);
            check(first != null, "no chair");
            // A rider's saved vehicle comes back as a second chair for the same slot.
            ChairEntity second = looseChair(level);
            second.setLine(line.id, 0);
            second.place(line);
            Cow cow = cow(level, second.position());
            check(level.addFreshEntity(second) && level.addFreshEntity(cow), "could not add");
            check(cow.startRiding(second), "the cow could not board");
            second.tick();
            check(first.isRemoved() && !second.isRemoved(), "the chair with riders did not win");
            check(LiftChairs.find(level, line.id, 0) == second, "the survivor is not the tracked chair");
            // And the other way round: an empty latecomer loses.
            ChairEntity third = looseChair(level);
            third.setLine(line.id, 0);
            third.place(line);
            level.addFreshEntity(third);
            third.tick();
            check(third.isRemoved() && !second.isRemoved(), "an empty duplicate survived");
            cow.discard();
        } finally {
            cleanUp(level, line);
        }
        helper.succeed();
    }

    public static void staleChairEjectsItsRiders(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LiftLine line = LiftManager.create(level, shortLine(helper));
        check(line != null, "the line was not created");
        Cow cow = null;
        try {
            ChairEntity chair = LiftChairs.spawn(level, line, 0);
            check(chair != null, "no chair");
            cow = cow(level, chair.position());
            check(level.addFreshEntity(cow) && cow.startRiding(chair), "the cow could not board");
            LiftManager.remove(level, line.id);
            chair.tick();
            check(chair.isRemoved(), "the chair of a removed line stayed");
            check(!cow.isPassenger(), "the rider stayed aboard");
            check(cow.getY() > level.getMinBuildHeight() - 1, "the rider was put below the world");
        } finally {
            if (cow != null) cow.discard();
            cleanUp(level, line);
        }
        helper.succeed();
    }

    public static void groundSearchIsCapped(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos high = helper.absolutePos(new BlockPos(1, 2, 1)).above(200);
        check(LiftChairs.findGround(level, high) == null, "found ground more than 64 blocks down");
        helper.succeed();
    }
}
