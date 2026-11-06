package io.github.adang101.alpineshred.gametest;

import com.mojang.authlib.GameProfile;
import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.lift.Boarding;
import io.github.adang101.alpineshred.lift.DismountRule;
import io.github.adang101.alpineshred.movement.RideServer;
import io.github.adang101.alpineshred.movement.RideState;
import io.github.adang101.alpineshred.movement.Riders;
import io.github.adang101.alpineshred.movement.StrapController;
import io.github.adang101.alpineshred.registry.ModStats;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Wolf;
import io.github.adang101.alpineshred.lift.LiftDriveStationBlockEntity;
import io.github.adang101.alpineshred.lift.LiftLine;
import io.github.adang101.alpineshred.lift.LiftLinkerItem;
import io.github.adang101.alpineshred.registry.ModItems;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import io.github.adang101.alpineshred.client.lift.ClientLine;
import io.github.adang101.alpineshred.lift.LiftLineSync;
import io.github.adang101.alpineshred.lift.LiftSync;
import io.github.adang101.alpineshred.lift.LiftManager;
import io.github.adang101.alpineshred.lift.LiftMotion;
import io.github.adang101.alpineshred.lift.LiftSavedData;
import io.github.adang101.alpineshred.lift.LiftNodeBlockEntity;
import io.github.adang101.alpineshred.lift.LiftReturnStationBlockEntity;
import io.github.adang101.alpineshred.lift.LiftTowerHeadBlockEntity;
import io.github.adang101.alpineshred.lift.PendingLine;
import io.github.adang101.alpineshred.config.AlpineConfig;
import io.github.adang101.alpineshred.network.ModNetwork;
import io.github.adang101.alpineshred.network.SyncCodec;
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
import java.util.Locale;
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
            LiftChairs.update(level, List.of(line), List.of(player));
            for (int i = 0; i < line.chairCount(); i++) check(LiftChairs.find(level, line.id, i) == null, "chair " + i + " spawned with nobody near");

            player.setPos(drive.getX(), drive.getY(), drive.getZ());
            LiftChairs.update(level, List.of(line), List.of(player));
            LiftChairs.update(level, List.of(line), List.of(player));
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
            LiftChairs.update(level, List.of(line), List.of(player));
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
        // 100 blocks over the terrain of this column: where a test sits depends on the test order, and on
        // Forge the grid reaches hills, so a fixed offset from the test floor can come within 64 of the ground.
        BlockPos column = helper.absolutePos(new BlockPos(1, 2, 1));
        int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ());
        BlockPos high = new BlockPos(column.getX(), Math.min(top + 100, level.getMaxBuildHeight() - 3), column.getZ());
        BlockPos found = LiftChairs.findGround(level, high);
        check(found == null, "found ground more than 64 blocks down: " + found + " from " + high + ", block below " + (found == null ? null : level.getBlockState(found.below())));
        helper.succeed();
    }

    // ---- work package 7: drive, stop button, commands ----

    private static BlockHitResult topHit(BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
    }

    /** The block's own {@code use}, as vanilla calls it before the held item's {@code useOn}. */
    private static net.minecraft.world.InteractionResult useBlock(ServerLevel level, Player player, BlockPos pos) {
        return level.getBlockState(pos).use(level, player, InteractionHand.MAIN_HAND, topHit(pos));
    }

    public static void redstoneDrivesTheLine(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Built b = place(helper);
        LiftLine line = LiftManager.create(level, b.nodes());
        check(line != null, "the line was not created");
        BlockPos redstone = b.drive.east();
        try {
            link(level, b, line);
            check(!line.powered && !line.emergency, "an unpowered drive started the line");
            level.setBlock(redstone, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            check(line.powered && line.targetSpeed == 2.5, "redstone did not power the line at 2.5: " + line.powered + " " + line.targetSpeed);
            check(!line.emergency, "powering set an emergency");
            double loop = line.path().loopLength();
            double[] up = LiftMotion.advance(0, 0, 2.5, false, loop, 130);
            for (int i = 0; i < 130; i++) LiftManager.tick(level);
            check(Math.abs(line.speed - up[0]) < 1e-9 && Math.abs(line.travel - up[1]) < 1e-9, "ramp differs from LiftMotion: " + line.speed + " vs " + up[0]);
            check(line.speed == 2.5, "the line did not reach 2.5 b/s: " + line.speed);

            level.setBlock(redstone, Blocks.AIR.defaultBlockState(), 3);
            check(line.emergency, "losing redstone while running was not an emergency stop");
            check(!line.powered, "losing redstone left the line powered");
            double speed = line.speed, travel = line.travel;
            double[] down = LiftMotion.advance(speed, travel, 0, true, loop, 10);
            for (int i = 0; i < 10; i++) LiftManager.tick(level);
            check(Math.abs(line.speed - (speed - 10 * LiftMotion.EMERGENCY_DECELERATION / LiftMotion.TICKS_PER_SECOND)) < 1e-9,
                    "speed did not fall at 2.0 b/s^2: " + line.speed);
            check(Math.abs(line.speed - down[0]) < 1e-9 && Math.abs(line.travel - down[1]) < 1e-9, "stop differs from LiftMotion");

            // Redstone coming back clears the emergency and powers the line again.
            level.setBlock(redstone, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            check(!line.emergency && line.powered, "regained redstone did not clear the emergency and power the line");
        } finally {
            level.setBlock(redstone, Blocks.AIR.defaultBlockState(), 3);
            LiftManager.remove(level, line.id);
            b.clear(level);
        }
        helper.succeed();
    }

    public static void driveKeepsItsCommandOnLoad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Built b = place(helper);
        LiftLine line = LiftManager.create(level, b.nodes());
        check(line != null, "the line was not created");
        BlockPos redstone = b.drive.east();
        try {
            link(level, b, line);
            level.setBlock(redstone, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            for (int i = 0; i < 50; i++) LiftManager.tick(level);
            check(line.powered && line.speed > 0, "the line is not running");
            double target = line.targetSpeed, speed = line.speed;
            // The source vanishes with no neighbour update, as if it sat in a chunk that is not loaded yet.
            level.setBlock(redstone, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            check(!b.driveBe(level).enabled(), "the drive still sees a signal");
            b.driveBe(level).loadCheck(level);
            check(line.powered, "the load check unpowered the line");
            check(!line.emergency, "the load check stopped the line");
            check(line.targetSpeed == target && line.speed == speed, "the load check changed the command");
        } finally {
            level.setBlock(redstone, Blocks.AIR.defaultBlockState(), 3);
            LiftManager.remove(level, line.id);
            b.clear(level);
        }
        helper.succeed();
    }

    public static void stopButtonStopsTheNearestLine(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Built b = place(helper);
        LiftLine near = LiftManager.create(level, b.nodes());
        // A second line whose drive is 7 blocks from the button, the first line's 1 block away.
        LiftLine other = LiftManager.create(level, b.nodes().stream().map(p -> p.offset(6, 0, 0)).toList());
        check(near != null && other != null, "the lines were not created");
        BlockPos button = b.drive.west();
        BlockPos farButton = b.drive.above(30);
        Player player = helper.makeMockPlayer();
        try {
            level.setBlock(button, ModBlocks.LIFT_STOP_BUTTON.get().defaultBlockState(), 3);
            level.setBlock(farButton, ModBlocks.LIFT_STOP_BUTTON.get().defaultBlockState(), 3);
            check(io.github.adang101.alpineshred.lift.LiftStopButtonBlock.nearestLine(level, button) == near, "the nearest line is the wrong one");
            useBlock(level, player, button);
            check(near.emergency, "the button did not stop the nearest line");
            check(!other.emergency, "the button stopped a farther line too");

            near.emergency = false;
            useBlock(level, player, farButton);
            check(!near.emergency && !other.emergency, "a button with no station in range stopped a line");
        } finally {
            level.setBlock(button, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(farButton, Blocks.AIR.defaultBlockState(), 3);
            LiftManager.remove(level, near.id);
            LiftManager.remove(level, other.id);
        }
        helper.succeed();
    }

    public static void speedLevelsCycle(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Built b = place(helper);
        LiftLine line = LiftManager.create(level, b.nodes());
        check(line != null, "the line was not created");
        BlockPos redstone = b.drive.east();
        Player player = helper.makeMockPlayer();
        try {
            link(level, b, line);
            List<Double> levels = AlpineConfig.LIFT_SPEED_LEVELS.get();
            check(levels.equals(List.of(1.5, 2.5, 4.0)), "unexpected default speed levels " + levels);
            check(line.targetSpeed == 2.5, "a new line does not start at the normal level: " + line.targetSpeed);
            double[] expected = {4.0, 1.5, 2.5};
            for (double want : expected) {
                check(useBlock(level, player, b.drive).consumesAction(), "the drive did not take the right-click");
                check(line.targetSpeed == want, "target " + line.targetSpeed + ", expected " + want);
                check(b.driveBe(level).targetSpeed() == want, "drive speed " + b.driveBe(level).targetSpeed());
            }
            check(!line.powered, "cycling the speed powered a drive without redstone");

            // With redstone the speed change reaches a running line, and a click resets an emergency.
            level.setBlock(redstone, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            check(line.powered, "no power");
            useBlock(level, player, b.drive);
            check(line.powered && line.targetSpeed == 4.0, "a powered line did not take the new speed: " + line.targetSpeed);
            LiftManager.emergencyStop(level, line.id);
            useBlock(level, player, b.drive);
            check(!line.emergency, "right-clicking the drive did not clear the emergency");
            check(line.targetSpeed == 4.0 && b.driveBe(level).speedLevel() == 2, "clearing the emergency also changed the speed");
        } finally {
            level.setBlock(redstone, Blocks.AIR.defaultBlockState(), 3);
            LiftManager.remove(level, line.id);
            b.clear(level);
        }
        helper.succeed();
    }

    public static void linkerPassesThroughTheDrive(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Built b = place(helper);
        Player player = helper.makeMockPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.LIFT_LINKER.get()));
        try {
            int before = b.driveBe(level).speedLevel();
            // What vanilla does for a right-click on a block: the block's use, and if that passes, the item's useOn.
            var result = useBlock(level, player, b.drive);
            check(result == net.minecraft.world.InteractionResult.PASS, "the drive swallowed the linker click: " + result);
            check(b.driveBe(level).speedLevel() == before, "the speed level changed");
            useLinker(player, b.drive);
            check(LiftLinkerItem.chain(player.getItemInHand(InteractionHand.MAIN_HAND)).equals(List.of(b.drive)), "the linker did not record the drive");
            check(b.driveBe(level).speedLevel() == before, "the speed level changed");
        } finally {
            b.clear(level);
        }
        helper.succeed();
    }

    /** Collects what a command source is sent. */
    private static final class Sink implements net.minecraft.commands.CommandSource {
        final List<net.minecraft.network.chat.Component> messages = new ArrayList<>();

        @Override
        public void sendSystemMessage(net.minecraft.network.chat.Component message) {
            messages.add(message);
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }

        /** The translatable part of message {@code i}; failures arrive wrapped in an empty red component. */
        private net.minecraft.network.chat.contents.TranslatableContents translatable(int i) {
            net.minecraft.network.chat.Component c = messages.get(i);
            if (!(c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents) && !c.getSiblings().isEmpty()) {
                c = c.getSiblings().get(0);
            }
            return c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t ? t : null;
        }

        String key(int i) {
            var t = translatable(i);
            return t == null ? "?" : t.getKey();
        }

        Object[] args(int i) {
            return translatable(i).getArgs();
        }
    }

    private static Sink run(ServerLevel level, int permission, String command) {
        Sink sink = new Sink();
        var source = level.getServer().createCommandSourceStack().withSource(sink).withLevel(level).withPermission(permission);
        level.getServer().getCommands().performPrefixedCommand(source, command);
        return sink;
    }

    public static void liftCommands(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Built b = place(helper);
        UUID idA = UUID.fromString("deadbeef-0000-4000-8000-000000000001");
        UUID idB = UUID.fromString("deadbeef-1111-4000-8000-000000000002");
        LiftLine line = LiftManager.create(level, idA, b.nodes());
        LiftLine twin = LiftManager.create(level, idB, b.nodes().stream().map(p -> p.offset(100, 0, 0)).toList());
        check(line != null && twin != null, "the lines were not created");
        Cow cow = null;
        try {
            link(level, b, line);
            ChairEntity chair = LiftChairs.spawn(level, line, 0);
            check(chair != null, "no chair");
            cow = cow(level, chair.position());
            check(level.addFreshEntity(cow) && cow.startRiding(chair), "the cow could not board");

            // list and info
            Sink sink = run(level, 2, "alpineshred lift list");
            int entries = 0;
            for (int i = 0; i < sink.messages.size(); i++) {
                if (sink.key(i).equals("commands.alpineshred.lift.list.entry") && "deadbeef".equals(sink.args(i)[0])) {
                    entries++;
                    check(Integer.valueOf(3).equals(sink.args(i)[1]), "list entry args: " + java.util.Arrays.toString(sink.args(i)));
                }
            }
            check(entries == 2, "list shows " + entries + " of our two lines: " + sink.messages);
            sink = run(level, 2, "alpineshred lift info " + idA);
            check(sink.messages.size() == 1 && sink.key(0).equals("commands.alpineshred.lift.info"), "info output: " + sink.messages);
            check(idA.toString().equals(sink.args(0)[0]) && Integer.valueOf(1).equals(sink.args(0)[8]), "info args (id, riders): " + java.util.Arrays.toString(sink.args(0)));

            // ids: a shared prefix is ambiguous, a unique one works, a short or unknown one is refused
            sink = run(level, 2, "alpineshred lift stop deadbeef");
            check(sink.key(0).equals("commands.alpineshred.lift.ambiguous") && !line.emergency, "ambiguous prefix: " + sink.messages);
            sink = run(level, 2, "alpineshred lift stop dea");
            check(sink.key(0).equals("commands.alpineshred.lift.too_short") && !line.emergency, "short prefix: " + sink.messages);
            sink = run(level, 2, "alpineshred lift stop zzzzzz");
            check(sink.key(0).equals("commands.alpineshred.lift.no_line"), "unknown id: " + sink.messages);

            // permission
            run(level, 0, "alpineshred lift stop deadbeef-0");
            check(!line.emergency, "a level-0 source stopped the line");

            // stop and start
            run(level, 2, "alpineshred lift stop deadbeef-0");
            check(line.emergency && !twin.emergency, "stop");
            run(level, 2, "alpineshred lift start deadbeef-0");
            check(!line.emergency && line.powered && line.targetSpeed == b.driveBe(level).targetSpeed(), "start: " + line.emergency + " " + line.powered + " " + line.targetSpeed);

            // remove
            run(level, 2, "alpineshred lift remove deadbeef-0");
            check(LiftManager.get(level, idA) == null, "the line is still there");
            check(LiftManager.get(level, idB) == twin, "the other line went too");
            for (int i = 0; i < 3; i++) check(nodeBe(level, b.nodes().get(i)).lineId() == null, "node " + i + " kept its line id");
            check(chair.isRemoved(), "the chair is still there");
            check(!cow.isPassenger(), "the rider stayed aboard");
            check(cow.getY() > level.getMinBuildHeight(), "the rider was put below the world");
        } finally {
            if (cow != null) cow.discard();
            LiftManager.remove(level, idA);
            LiftManager.remove(level, idB);
            b.clear(level);
        }
        helper.succeed();
    }

    // ---- work package 5: syncing lines to clients ----

    private static void closeEnough(double got, double want, String what) {
        check(Math.abs(got - want) < 1e-6, what + " " + got + " vs " + want);
    }

    /**
     * Syncs {@code line} as the server would at its current state, lets a client copy catch up 137
     * ticks (by one {@code apply} and by 137 client ticks) while the manager runs 137 more ticks, and
     * compares travel and speed.
     */
    private static void catchUpCheck(ServerLevel level, LiftLine line, String label) {
        long t = level.getGameTime();
        LiftLineSync sync = LiftLineSync.of(line, t);
        ClientLine caught = new ClientLine(sync, t + 137);
        ClientLine stepped = new ClientLine(sync, t);
        check(caught.chairCount() == line.chairCount() && stepped.chairCount() == line.chairCount(),
                label + ": chair count " + caught.chairCount() + " vs " + line.chairCount());
        check(caught.path().loopLength() == line.path().loopLength(), label + ": loop length");
        for (int i = 0; i < 137; i++) stepped.tick();
        for (int i = 0; i < 137; i++) LiftManager.tick(level);
        closeEnough(caught.travel, line.travel, label + " travel (catch-up)");
        closeEnough(caught.speed, line.speed, label + " speed (catch-up)");
        closeEnough(stepped.travel, line.travel, label + " travel (ticked)");
        closeEnough(stepped.speed, line.speed, label + " speed (ticked)");
        // A sync from far in the future is taken as it is; one a few ticks ahead is rewound at its own speed.
        ClientLine early = new ClientLine(sync, t - 50);
        closeEnough(early.travel, sync.travel(), label + " early travel");
        closeEnough(early.speed, sync.speed(), label + " early speed");
        ClientLine slightlyEarly = new ClientLine(sync, t - 3);
        double loop = sync.speed() == 0 ? 0 : slightlyEarly.path().loopLength();
        double back = sync.travel() - sync.speed() / 20.0 * 3;
        if (loop > 0) back = ((back % loop) + loop) % loop;
        closeEnough(slightlyEarly.travel, sync.speed() == 0 ? sync.travel() : back, label + " travel rewound by 3 ticks");
        closeEnough(slightlyEarly.speed, sync.speed(), label + " speed after a rewind");
    }

    public static void clientCatchUpMatchesServer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LiftLine line = LiftManager.create(level, nodes(helper, 3));
        check(line != null, "the line was not created");
        try {
            // A sync of a line that has not started.
            LiftManager.setDrive(level, line.id, true, 2.5);
            catchUpCheck(level, line, "from standstill");
            // Mid-ramp: 137 ticks is 3.4 seconds, so the next sync is still short of 2.5.
            LiftManager.setDrive(level, line.id, false, 2.5);
            for (int i = 0; i < 400; i++) LiftManager.tick(level);
            check(line.speed == 0, "the line did not stop");
            LiftManager.setDrive(level, line.id, true, 2.5);
            for (int i = 0; i < 30; i++) LiftManager.tick(level);
            check(line.speed > 0 && line.speed < 2.5, "not mid-ramp: " + line.speed);
            catchUpCheck(level, line, "mid-ramp");
            // Steady running.
            for (int i = 0; i < 100; i++) LiftManager.tick(level);
            check(line.speed == 2.5, "not at speed: " + line.speed);
            catchUpCheck(level, line, "steady");
            // A faulted line: the client slows it at the emergency rate to a stop.
            LiftManager.fault(level, line.id, "test");
            for (int i = 0; i < 3; i++) LiftManager.tick(level);
            check(line.speed > 0, "stopped too soon");
            catchUpCheck(level, line, "faulted");
            check(line.speed == 0, "a faulted line did not stop");
            // An emergency stop that was cleared and a drive that was switched off.
            LiftSavedData.get(level).get(line.id).faulted = false;
            LiftManager.setDrive(level, line.id, true, 2.0);
            for (int i = 0; i < 20; i++) LiftManager.tick(level);
            LiftManager.emergencyStop(level, line.id);
            catchUpCheck(level, line, "emergency");
        } finally {
            LiftManager.remove(level, line.id);
        }
        helper.succeed();
    }

    public static void syncSetTracksPlayers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LiftLine line = LiftManager.create(level, shortLine(helper));
        check(line != null, "the line was not created");
        ServerPlayer near = RideGameTests.newServerPlayer(helper);
        ServerPlayer far = RideGameTests.newServerPlayer(helper);
        List<LiftLineSync> toNear = new ArrayList<>();
        List<LiftLineSync> toFar = new ArrayList<>();
        try {
            LiftSync.reset();
            LiftSync.setPlayers(l -> List.of(near, far));
            LiftSync.setSender((player, sync) -> (player == near ? toNear : toFar).add(sync));
            BlockPos drive = line.drive;
            near.setPos(drive.getX() + 20, drive.getY(), drive.getZ());
            far.setPos(drive.getX() + 5000, drive.getY(), drive.getZ());

            LiftSync.pass(level, 39);
            check(toNear.isEmpty() && LiftSync.synced(near.getUUID()).isEmpty(), "synced off the 40-tick beat");
            LiftSync.pass(level, 40);
            check(LiftSync.synced(near.getUUID()).contains(line.id), "the player in range was not synced");
            check(LiftSync.synced(far.getUUID()).isEmpty() && toFar.isEmpty(), "the player out of range was synced");
            check(toNear.size() == 1 && !toNear.get(0).removed() && toNear.get(0).id().equals(line.id)
                    && toNear.get(0).nodes().equals(line.nodePositions()) && toNear.get(0).gameTime() == 40
                    && toNear.get(0).spacing() == line.spacing(), "the full sync is wrong");

            LiftSync.pass(level, 80);
            check(toNear.size() == 1, "a line already synced was sent again at 80");
            LiftSync.pass(level, 100);
            check(toNear.size() == 2 && toFar.isEmpty(), "no 100-tick resend, or sent to the wrong player");

            // A change goes to everyone who has the line, and only them.
            LiftManager.setDrive(level, line.id, true, 2.5);
            check(toNear.size() == 3 && toNear.get(2).powered() && toNear.get(2).targetSpeed() == 2.5 && toFar.isEmpty(),
                    "the drive change was not sent to the player with the line");
            LiftManager.setDrive(level, line.id, true, 2.5);
            check(toNear.size() == 3, "an unchanged drive command was sent");
            LiftManager.emergencyStop(level, line.id);
            check(toNear.size() == 4 && toNear.get(3).emergency(), "the emergency stop was not sent");

            // A player who moves in range gets the line at the next scan.
            far.setPos(drive.getX(), drive.getY(), drive.getZ() + 200);
            LiftSync.pass(level, 120);
            check(LiftSync.synced(far.getUUID()).contains(line.id) && toFar.size() == 1, "the player who came in range was not synced");
            // A line that leaves the radius stays in the set.
            near.setPos(drive.getX() + 5000, drive.getY(), drive.getZ());
            LiftSync.pass(level, 160);
            check(LiftSync.synced(near.getUUID()).contains(line.id), "the line left the set when out of range");

            // A joining player gets the line at once, from an empty set, whatever the beat.
            LiftSync.forget(far.getUUID());
            int farJoin = toFar.size();
            LiftSync.join(far, level);
            check(toFar.size() == farJoin + 1 && !toFar.get(farJoin).removed() && toFar.get(farJoin).id().equals(line.id)
                    && LiftSync.synced(far.getUUID()).contains(line.id), "a joining player was not sent the line at once");
            LiftSync.join(far, level);
            check(toFar.size() == farJoin + 2, "a rejoin did not resend the line");

            // Removal reaches everyone with the line and empties their sets.
            int nearBefore = toNear.size(), farBefore = toFar.size();
            LiftManager.remove(level, line.id);
            check(toNear.size() == nearBefore + 1 && toNear.get(nearBefore).removed() && toNear.get(nearBefore).id().equals(line.id),
                    "removal was not sent to the first player");
            check(toFar.size() == farBefore + 1 && toFar.get(farBefore).removed(), "removal was not sent to the second player");
            check(LiftSync.synced(near.getUUID()).isEmpty() && LiftSync.synced(far.getUUID()).isEmpty(), "sets kept the removed line");

            // Logout and dimension change forget the set.
            LiftSync.forget(far.getUUID());
            check(LiftSync.synced(far.getUUID()).isEmpty(), "forget kept the set");
        } finally {
            LiftSync.reset();
            LiftManager.remove(level, line.id);
        }
        helper.succeed();
    }

    // ---- work package 6: boarding, riding, unloading ----

    /**
     * A climbing line about 150 blocks of cable long that stays within 50 blocks of the test: the drive
     * (top) first, the return station (bottom) last.
     */
    private static List<BlockPos> climbingLine(GameTestHelper helper) {
        BlockPos base = helper.absolutePos(new BlockPos(1, 2, 1));
        return List.of(base.offset(0, 20, 0), base.offset(36, 16, 12), base.offset(2, 12, 24), base.offset(38, 8, 36), base.offset(4, 4, 48));
    }

    /** Spawns chair 0 of {@code line} at loop distance {@code s} (chair 0's {@code s} is the line's travel). */
    private static ChairEntity chairAtS(ServerLevel level, LiftLine line, double s) {
        line.travel = s;
        ChairEntity chair = LiftChairs.spawn(level, line, 0);
        check(chair != null, "chair 0 was not spawned");
        chair.tick(); // claims its slot
        return chair;
    }

    /** Air in the chair's block column from {@code below} blocks under the seat to two above it. */
    private static void clearColumn(ServerLevel level, ChairEntity chair, int below) {
        BlockPos seat = chair.blockPosition();
        for (int dy = -below; dy <= 2; dy++) level.setBlock(seat.offset(0, dy, 0), Blocks.AIR.defaultBlockState(), 3);
    }

    /** A strapped survival player standing so that {@code chair} is at their side. */
    private static ServerPlayer riderBeside(GameTestHelper helper, ChairEntity chair) {
        return RideGameTests.serverRider(helper, new Vec3(chair.getX(), chair.getY() - 0.5, chair.getZ()));
    }

    private static void tickLine(ServerLevel level, ChairEntity chair, int ticks) {
        for (int i = 0; i < ticks; i++) {
            LiftManager.tick(level);
            chair.tick();
        }
    }

    public static void boardAndRideToTheTop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LiftLine line = LiftManager.create(level, climbingLine(helper));
        check(line != null, "the line was not created");
        ServerPlayer player = null;
        Cow cow = null;
        try {
            LiftManager.setDrive(level, line.id, true, 2.5);
            line.speed = 2.5;
            double boardS = 1.0;
            ChairEntity chair = chairAtS(level, line, boardS);
            player = riderBeside(helper, chair);
            int rides = ModStats.value(player, ModStats.LIFT_RIDES);
            check(Riders.state(player) == RideState.IDLE, "the rider should start strapped in IDLE, was " + Riders.state(player));

            Boarding.Result result = Boarding.tryBoard(player, chair, Boarding.MANUAL_REACH);
            check(result.ok(), "boarding was refused: " + result);
            check(player.getVehicle() == chair, "the player is not on the chair");
            check(Riders.state(player) == RideState.LIFT, "state after boarding " + Riders.state(player));
            check(!chair.barDown(), "the bar was down at once");
            cow = cow(level, chair.position());
            check(level.addFreshEntity(cow) && cow.startRiding(chair), "the cow could not take the second seat");

            tickLine(level, chair, 25);
            check(chair.barDown(), "the bar did not close 20 ticks after boarding");
            check(player.getVehicle() == chair, "the player left the chair on the way");

            double ejectS = line.stations().ejectS();
            check(ejectS - boardS >= 100, "the test line is too short for the advancement: " + (ejectS - boardS));
            check(line.stations().inUnload(ejectS), "the ejection point is not in the unload stretch");
            // Skip the middle of the ride; the ejection itself is crossed by ticks.
            line.travel = ejectS - 6;
            tickLine(level, chair, 1);
            check(!chair.barDown(), "the bar did not open in the unload stretch");
            check(player.getVehicle() == chair, "ejected early");
            int ticks = 0;
            while (player.isPassenger() && ticks < 200) {
                tickLine(level, chair, 1);
                ticks++;
            }
            check(!player.isPassenger(), "the player was never ejected");
            check(ticks > 20 && ticks < 120, "ejection after " + ticks + " ticks");
            check(!cow.isPassenger(), "the cow stayed aboard");
            check(cow.getDeltaMovement().lengthSqr() < 1.0e-9, "the cow was ejected with speed " + cow.getDeltaMovement());
            check(Riders.state(player) == RideState.GLIDE, "state after the top: " + Riders.state(player));
            check(Riders.isStrapped(player), "no longer strapped");
            V3 t = line.path().tangent(ejectS);
            Vec3 v = player.getDeltaMovement();
            check(Math.abs(v.x - t.x() * 0.35) < 0.02 && Math.abs(v.z - t.z() * 0.35) < 0.02,
                    "velocity " + v + ", tangent*0.35 is (" + t.x() * 0.35 + ", " + t.z() * 0.35 + ")");
            check(Math.hypot(v.x, v.z) > 0.1, "the ejected rider is not moving");
            check(ModStats.value(player, ModStats.LIFT_RIDES) == rides + 1, "lift_rides " + ModStats.value(player, ModStats.LIFT_RIDES));
            var advancement = level.getServer().getAdvancements().getAdvancement(new ResourceLocation("alpineshred", "bluebird_day"));
            check(advancement != null, "bluebird_day is not loaded");
            check(player.getAdvancements().getOrStartProgress(advancement).isDone(), "bluebird_day was not granted");
        } finally {
            if (cow != null) cow.discard();
            RideGameTests.removeServerPlayer(player);
            cleanUp(level, line);
        }
        helper.succeed();
    }

    public static void boardingRefusedTooFastFullOrNoPass(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LiftLine line = LiftManager.create(level, climbingLine(helper));
        check(line != null, "the line was not created");
        ServerPlayer player = null;
        Cow a = null, b = null;
        int seats = AlpineConfig.LIFT_SEATS.get();
        boolean requirePass = AlpineConfig.LIFT_REQUIRE_PASS.on();
        try {
            AlpineConfig.LIFT_SEATS.set(2);
            AlpineConfig.LIFT_REQUIRE_PASS.set(false);
            ChairEntity chair = chairAtS(level, line, 1.0);
            player = riderBeside(helper, chair);

            // Too fast.
            line.speed = AlpineConfig.LIFT_MAX_BOARD_SPEED.get() + 0.5;
            Boarding.Result fast = Boarding.tryBoard(player, chair, Boarding.MANUAL_REACH);
            check(fast == Boarding.Result.TOO_FAST && !player.isPassenger(), "too fast gave " + fast);
            line.speed = 2.5;

            // Too far.
            Boarding.Result far = Boarding.tryBoard(player, chair, 0.0);
            check(far == Boarding.Result.TOO_FAR && !player.isPassenger(), "reach 0 gave " + far);

            // Full.
            a = cow(level, chair.position());
            b = cow(level, chair.position());
            check(level.addFreshEntity(a) && level.addFreshEntity(b), "no cows");
            check(a.startRiding(chair) && b.startRiding(chair), "the cows could not board");
            Boarding.Result full = Boarding.tryBoard(player, chair, Boarding.MANUAL_REACH);
            check(full == Boarding.Result.FULL && !player.isPassenger(), "full gave " + full);
            a.stopRiding();
            b.stopRiding();

            // No pass: refused without one, boards with one, and the pass is kept.
            AlpineConfig.LIFT_REQUIRE_PASS.set(true);
            Boarding.Result noPass = Boarding.tryBoard(player, chair, Boarding.MANUAL_REACH);
            check(noPass == Boarding.Result.NO_PASS && !player.isPassenger(), "no pass gave " + noPass);
            player.getInventory().add(new ItemStack(ModItems.LIFT_PASS.get()));
            Boarding.Result withPass = Boarding.tryBoard(player, chair, Boarding.MANUAL_REACH);
            check(withPass.ok() && player.getVehicle() == chair, "with a pass gave " + withPass);
            check(Boarding.hasPass(player), "the pass was consumed");
        } finally {
            AlpineConfig.LIFT_SEATS.set(seats);
            AlpineConfig.LIFT_REQUIRE_PASS.set(requirePass);
            if (a != null) a.discard();
            if (b != null) b.discard();
            RideGameTests.removeServerPlayer(player);
            cleanUp(level, line);
        }
        helper.succeed();
    }

    /** Sneaks the player off the chair as the server does each tick; true if they are still aboard afterwards. */
    private static boolean sneakAndStay(ServerPlayer player) {
        player.setShiftKeyDown(true);
        player.rideTick();
        boolean aboard = player.isPassenger();
        player.setShiftKeyDown(false);
        return aboard;
    }

    /** What a client would read from the server's config_sync body: the receiver's decode path in {@code ClientNetwork}. */
    private static SyncCodec.Settings clientView() throws java.io.IOException {
        net.minecraft.network.FriendlyByteBuf buf = ModNetwork.configSyncBuffer();
        check(buf.readVarInt() == SyncCodec.PROTOCOL_VERSION, "protocol version not first in config_sync");
        return SyncCodec.readConfig(SyncCodec.inflate(buf.readByteArray(ModNetwork.MAX_SYNC_BYTES)));
    }

    /** Synchronous, so no other test sees the changed values. */
    public static void configSyncCarriesLiftKeys(GameTestHelper helper) {
        double speed = AlpineConfig.LIFT_MAX_BOARD_SPEED.get();
        boolean anywhere = AlpineConfig.LIFT_DISMOUNT_ANYWHERE.on();
        int seats = AlpineConfig.LIFT_SEATS.get();
        try {
            AlpineConfig.LIFT_MAX_BOARD_SPEED.set(1.5);
            AlpineConfig.LIFT_SEATS.set(3);
            AlpineConfig.LIFT_DISMOUNT_ANYWHERE.set(true);
            SyncCodec.Settings view = clientView();
            check(view.maxBoardSpeed() == 1.5, "client sees maxBoardSpeed " + view.maxBoardSpeed());
            check(view.dismountAnywhere(), "client does not see dismountAnywhere");
            check(view.liftSeats() == 3, "client sees lift.seats " + view.liftSeats());
            AlpineConfig.LIFT_MAX_BOARD_SPEED.set(6.0);
            AlpineConfig.LIFT_DISMOUNT_ANYWHERE.set(false);
            AlpineConfig.LIFT_SEATS.set(1);
            view = clientView();
            check(view.maxBoardSpeed() == 6.0 && !view.dismountAnywhere() && view.liftSeats() == 1, "client missed the second change: " + view);
        } catch (java.io.IOException e) {
            throw new AssertionError("config_sync did not decode", e);
        } finally {
            AlpineConfig.LIFT_MAX_BOARD_SPEED.set(speed);
            AlpineConfig.LIFT_DISMOUNT_ANYWHERE.set(anywhere);
            AlpineConfig.LIFT_SEATS.set(seats);
        }
        helper.succeed();
    }

    public static void sneakDismountRules(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LiftLine line = LiftManager.create(level, climbingLine(helper));
        check(line != null, "the line was not created");
        ServerPlayer player = null;
        boolean anywhere = AlpineConfig.LIFT_DISMOUNT_ANYWHERE.on();
        try {
            AlpineConfig.LIFT_DISMOUNT_ANYWHERE.set(false);
            ChairEntity chair = chairAtS(level, line, line.path().loadedEnd() - 10);
            clearColumn(level, chair, 12); // neighbouring tests may have built under the line
            player = riderBeside(helper, chair);
            check(chair.heightAboveGround() > DismountRule.MAX_HEIGHT, "the chair is only " + chair.heightAboveGround() + " above the ground");

            // Mid-span, bar closed, high up, line healthy: refused.
            check(player.startRiding(chair), "could not sit");
            chair.setBarDown(true);
            check(Riders.state(player) == RideState.LIFT, "not in LIFT");
            check(sneakAndStay(player), "sneaking got the rider off a closed bar mid-span");
            check(Riders.state(player) == RideState.LIFT, "state changed by a refused dismount");

            // Bar open: allowed, and the rider is back to IDLE.
            chair.setBarDown(false);
            check(!sneakAndStay(player), "an open bar did not let the rider off");
            check(Riders.state(player) == RideState.IDLE, "state after a sneak dismount " + Riders.state(player));

            // Faulted line: allowed.
            check(player.startRiding(chair, true), "could not sit again");
            chair.setBarDown(true);
            check(sneakAndStay(player), "refused again");
            line.faulted = true;
            check(!sneakAndStay(player), "a faulted line did not let the rider off");
            line.faulted = false;

            // dismountAnywhere: allowed.
            check(player.startRiding(chair, true), "could not sit the third time");
            check(sneakAndStay(player), "refused the third time");
            AlpineConfig.LIFT_DISMOUNT_ANYWHERE.set(true);
            check(!sneakAndStay(player), "dismountAnywhere did not let the rider off");
        } finally {
            AlpineConfig.LIFT_DISMOUNT_ANYWHERE.set(anywhere);
            RideGameTests.removeServerPlayer(player);
            cleanUp(level, line);
        }
        helper.succeed();
    }

    private static int countLeads(Player player) {
        int n = 0;
        for (ItemStack stack : player.getInventory().items) if (stack.is(Items.LEAD)) n += stack.getCount();
        return n;
    }

    public static void leashedMobsBoard(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LiftLine line = LiftManager.create(level, climbingLine(helper));
        check(line != null, "the line was not created");
        ServerPlayer player = null;
        List<Cow> cows = new ArrayList<>();
        int seats = AlpineConfig.LIFT_SEATS.get();
        boolean mobs = AlpineConfig.LIFT_MOBS_ALLOWED.on();
        try {
            AlpineConfig.LIFT_SEATS.set(2);
            ChairEntity chair = chairAtS(level, line, 1.0);
            player = riderBeside(helper, chair);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEAD));
            for (int i = 0; i < 3; i++) {
                Cow cow = cow(level, chair.position().add(i - 1, 0, 2));
                check(level.addFreshEntity(cow), "no cow");
                cow.setLeashedTo(player, true);
                cows.add(cow);
            }
            check(countLeads(player) == 1, "setup: " + countLeads(player) + " leads");
            chair.interact(player, InteractionHand.MAIN_HAND);
            long aboard = cows.stream().filter(c -> c.getVehicle() == chair).count();
            Player owner = player;
            long stillLeashed = cows.stream().filter(c -> c.isLeashed() && c.getLeashHolder() == owner).count();
            check(aboard == 2, aboard + " cows boarded a two-seat chair");
            check(stillLeashed == 1, stillLeashed + " cows still leashed, expected the one that did not fit");
            check(cows.stream().filter(c -> c.getVehicle() == chair).noneMatch(Cow::isLeashed), "a boarded cow kept its lead");
            check(countLeads(player) == 3, "the leads did not come back: " + countLeads(player));
            check(!player.isPassenger(), "the player boarded too");

            // With mobs not allowed nobody boards and the leads stay on.
            for (Cow cow : cows) cow.stopRiding();
            for (Cow cow : cows) cow.setLeashedTo(player, true);
            AlpineConfig.LIFT_MOBS_ALLOWED.set(false);
            chair.interact(player, InteractionHand.MAIN_HAND);
            check(cows.stream().noneMatch(Entity::isPassenger), "a mob boarded with mobsAllowed off");
            check(cows.stream().allMatch(Cow::isLeashed), "a lead was taken though nobody boarded");
        } finally {
            AlpineConfig.LIFT_SEATS.set(seats);
            AlpineConfig.LIFT_MOBS_ALLOWED.set(mobs);
            for (Cow cow : cows) cow.discard();
            RideGameTests.removeServerPlayer(player);
            cleanUp(level, line);
        }
        helper.succeed();
    }

    private static Wolf wolfOf(ServerLevel level, Player owner, Vec3 at) {
        Wolf wolf = EntityType.WOLF.create(level);
        check(wolf != null, "no wolf");
        wolf.setPos(at.x, at.y, at.z);
        wolf.tame(owner);
        check(level.addFreshEntity(wolf), "could not add the wolf");
        return wolf;
    }

    public static void petFollowsOwner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LiftLine line = LiftManager.create(level, climbingLine(helper));
        check(line != null, "the line was not created");
        ServerPlayer player = null;
        List<Wolf> wolves = new ArrayList<>();
        int seats = AlpineConfig.LIFT_SEATS.get();
        boolean pets = AlpineConfig.LIFT_PETS_AUTO_BOARD.on();
        boolean mobs = AlpineConfig.LIFT_MOBS_ALLOWED.on();
        try {
            AlpineConfig.LIFT_SEATS.set(2);
            ChairEntity chair = chairAtS(level, line, 1.0);
            player = riderBeside(helper, chair);
            Wolf near = wolfOf(level, player, chair.position().add(5, 0, 0));
            Wolf far = wolfOf(level, player, chair.position().add(12, 0, 0));
            Wolf second = wolfOf(level, player, chair.position().add(4, 0, 0));
            wolves.addAll(List.of(near, far, second));
            check(near.isTame() && player.getUUID().equals(near.getOwnerUUID()), "the wolf is not tamed");

            // Pets off: the owner boards alone.
            AlpineConfig.LIFT_PETS_AUTO_BOARD.set(false);
            check(Boarding.tryBoard(player, chair, Boarding.MANUAL_REACH).ok(), "the owner could not board");
            check(wolves.stream().noneMatch(Entity::isPassenger), "a pet boarded with petsAutoBoard off");
            chair.ejectPassengers();

            // Mobs not allowed at all: pets stay too.
            AlpineConfig.LIFT_PETS_AUTO_BOARD.set(true);
            AlpineConfig.LIFT_MOBS_ALLOWED.set(false);
            check(Boarding.tryBoard(player, chair, Boarding.MANUAL_REACH).ok(), "the owner could not board");
            check(wolves.stream().noneMatch(Entity::isPassenger), "a pet boarded with mobsAllowed off");
            chair.ejectPassengers();
            AlpineConfig.LIFT_MOBS_ALLOWED.set(true);

            // On: one pet within 8 blocks fits in the free seat; the far one and the one without a seat stay.
            check(Boarding.tryBoard(player, chair, Boarding.MANUAL_REACH).ok(), "the owner could not board");
            long aboard = wolves.stream().filter(w -> w.getVehicle() == chair).count();
            check(aboard == 1, aboard + " pets followed their owner onto a chair with one free seat");
            check(far.getVehicle() == null, "a pet 12 blocks away boarded");
            check(chair.getPassengers().size() == 2, "passengers " + chair.getPassengers().size());
        } finally {
            AlpineConfig.LIFT_SEATS.set(seats);
            AlpineConfig.LIFT_PETS_AUTO_BOARD.set(pets);
            AlpineConfig.LIFT_MOBS_ALLOWED.set(mobs);
            for (Wolf w : wolves) w.discard();
            RideGameTests.removeServerPlayer(player);
            cleanUp(level, line);
        }
        helper.succeed();
    }

    public static void riderSurvivesRelog(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LiftLine line = LiftManager.create(level, climbingLine(helper));
        check(line != null, "the line was not created");
        GameProfile profile = new GameProfile(UUID.randomUUID(), "alpine-relog");
        ServerPlayer first = null, second = null;
        try {
            line.travel = 40;
            ChairEntity chair = chairAtS(level, line, 40);
            first = RideGameTests.newServerPlayer(helper, profile);
            first.moveTo(chair.getX(), chair.getY() - 0.5, chair.getZ());
            check(first.startRiding(chair), "could not sit");
            chair.setBarDown(true);
            CompoundTag saved = new CompoundTag();
            first.saveWithoutId(saved);
            check(saved.contains("RootVehicle"), "the player's data has no root vehicle");
            CompoundTag vehicle = saved.getCompound("RootVehicle").getCompound("Entity");
            check(vehicle.hasUUID("Line") && vehicle.getUUID("Line").equals(line.id) && vehicle.getInt("Index") == 0,
                    "the saved chair lost its line or index: " + vehicle);

            // A real log out and in: the server saves the player, drops the chair with them and loads both back.
            level.getServer().getPlayerList().remove(first);
            check(chair.isRemoved(), "the chair outlived its only rider's logout");
            first = null;
            second = RideGameTests.newServerPlayer(helper, profile);
            check(second.getVehicle() instanceof ChairEntity, "the rider was not put back on a chair");
            ChairEntity back = (ChairEntity) second.getVehicle();
            check(back != chair, "the old chair came back");
            check(line.id.equals(back.lineId()) && back.chairIndex() == 0, "the chair's slot changed");
            check(back.barDown(), "the bar was not saved");
            back.tick();
            check(LiftChairs.find(level, line.id, 0) == back, "the reloaded chair did not claim its slot");
            check(back.getPassengers().contains(second), "the rider is not a passenger of the chair");
            V3 want = line.path().chairPos(line.travel, 0, line.chairCount());
            check(Math.abs(back.getX() - want.x()) < 1e-6 && Math.abs(back.getY() - want.y()) < 1e-6, "the chair is at " + back.position() + ", wanted " + want);
            // The line spawns the other chairs without making a second chair 0.
            LiftChairs.update(level, LiftManager.lines(level));
            int zero = level.getEntities(ModEntities.LIFT_CHAIR.get(), new AABB(line.drive).inflate(200),
                    e -> line.id.equals(e.lineId()) && e.chairIndex() == 0 && !e.isRemoved()).size();
            check(zero == 1, zero + " chairs for slot 0");
        } finally {
            RideGameTests.removeServerPlayer(first);
            RideGameTests.removeServerPlayer(second);
            cleanUp(level, line);
        }
        helper.succeed();
    }

    public static void riderEjectedWhenLineGone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LiftLine line = LiftManager.create(level, climbingLine(helper));
        check(line != null, "the line was not created");
        ServerPlayer player = null;
        try {
            ChairEntity chair = chairAtS(level, line, line.path().loadedEnd() - 10);
            // Ground for the rider to be put on: one stone block 8 blocks under the seat, air above it.
            clearColumn(level, chair, 12);
            BlockPos stone = chair.blockPosition().below(8);
            level.setBlock(stone, Blocks.STONE.defaultBlockState(), 3);
            player = riderBeside(helper, chair);
            check(player.startRiding(chair), "could not sit");
            check(Riders.state(player) == RideState.LIFT, "not in LIFT");
            double seatY = chair.getY();
            LiftManager.remove(level, line.id);
            chair.tick();
            check(chair.isRemoved(), "the chair of a removed line stayed");
            check(!player.isPassenger(), "the rider is still aboard");
            check(player.getY() < seatY - 1, "the rider was left floating at " + player.getY() + " (seat " + seatY + ")");
            check(player.blockPosition().equals(stone.above()), "the rider was put at " + player.blockPosition() + ", not on the stone at " + stone);
            check(Riders.state(player) == RideState.IDLE, "state after losing the line " + Riders.state(player));
            check(Riders.isStrapped(player), "unstrapped");
        } finally {
            RideGameTests.removeServerPlayer(player);
            cleanUp(level, line);
        }
        helper.succeed();
    }

    public static void staysStrappedOnChair(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LiftLine line = LiftManager.create(level, climbingLine(helper));
        check(line != null, "the line was not created");
        ServerPlayer player = null;
        Cow cow = null;
        try {
            ChairEntity chair = chairAtS(level, line, 1.0);
            player = riderBeside(helper, chair);
            check(Boarding.tryBoard(player, chair, Boarding.MANUAL_REACH).ok(), "could not board");
            check(Riders.isStrapped(player) && Riders.state(player) == RideState.LIFT, "state " + Riders.state(player));
            check(StrapController.autoUnstrapReason(player) == null, "auto-unstrap says " + StrapController.autoUnstrapReason(player));
            for (int i = 0; i < 40; i++) StrapController.serverTick(player);
            check(Riders.isStrapped(player) && Riders.state(player) == RideState.LIFT, "state after ticks " + Riders.state(player));
            check(RideServer.violations(player) == 0 && RideServer.corrections(player) == 0, "the validator flagged a rider on a chair");

            // Any other vehicle still unstraps.
            player.stopRiding();
            check(Riders.state(player) == RideState.IDLE, "state after getting off " + Riders.state(player));
            cow = cow(level, chair.position());
            check(level.addFreshEntity(cow) && player.startRiding(cow, true), "could not ride the cow");
            check(StrapController.autoUnstrapReason(player) == StrapController.UnstrapReason.VEHICLE, "a cow did not unstrap");
        } finally {
            if (cow != null) cow.discard();
            RideGameTests.removeServerPlayer(player);
            cleanUp(level, line);
        }
        helper.succeed();
    }

    // ---- work package 10: save and reload, budgets ----

    /**
     * A line and a rider survive a save and reload: the line comes back from its saved data (the way a restart
     * loads it) in the state it was saved in, the rider logs out (the server saves them with their chair) and
     * back in, and they are on a chair of the same slot, which follows the reloaded line. The full restart of a
     * dedicated server with riders is a hand check ({@code docs/playtest-phase8.md}); this is its data path.
     */
    public static void liftSurvivesSaveReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LiftLine line = LiftManager.create(level, climbingLine(helper));
        check(line != null, "the line was not created");
        GameProfile profile = new GameProfile(UUID.randomUUID(), "alpine-reload");
        ServerPlayer first = null, second = null;
        try {
            UUID id = line.id;
            LiftManager.setDrive(level, id, true, 2.5);
            for (int i = 0; i < 120; i++) LiftManager.tick(level);
            check(line.speed > 1.0 && line.travel > 0, "the line did not get going: speed " + line.speed + ", travel " + line.travel);
            // The 120 ticks above include spawn passes (every few ticks, on a level-wide counter) that measure to every
            // player of the shared level, so a concurrent test's player near the line may already have had chair 0 spawned.
            ChairEntity chair = LiftChairs.find(level, id, 0);
            if (chair == null) chair = LiftChairs.spawn(level, line, 0);
            check(chair != null, "chair 0 was not spawned");
            chair.tick();
            first = RideGameTests.newServerPlayer(helper, profile);
            first.moveTo(chair.getX(), chair.getY() - 0.5, chair.getZ());
            check(first.startRiding(chair), "could not sit");
            chair.setBarDown(true);
            double speed = line.speed, travel = line.travel;

            // The save: the level's lift data, and the rider with the chair (a log out saves both).
            CompoundTag savedLines = LiftSavedData.get(level).save(new CompoundTag());
            level.getServer().getPlayerList().remove(first);
            first = null;
            check(chair.isRemoved(), "the chair outlived its only rider's logout");

            // The reload: the line is replaced by the one loaded from the saved data.
            LiftManager.remove(level, id);
            LiftLine loaded = LiftSavedData.load(savedLines).get(id);
            check(loaded != null && loaded != line, "the line did not load from its saved data");
            check(LiftSavedData.get(level).add(loaded), "the loaded line could not be added");
            check(loaded.travel == travel && loaded.speed == speed, "loaded travel " + loaded.travel + " and speed " + loaded.speed + ", saved " + travel + " and " + speed);
            check(loaded.powered && loaded.targetSpeed == 2.5 && !loaded.faulted && !loaded.emergency, "the drive's command was lost");
            for (int i = 0; i < 60; i++) LiftManager.tick(level);
            check(loaded.travel > travel, "the reloaded line did not carry on: " + loaded.travel);

            second = RideGameTests.newServerPlayer(helper, profile);
            check(second.getVehicle() instanceof ChairEntity, "the rider was not put back on a chair");
            ChairEntity back = (ChairEntity) second.getVehicle();
            back.tick();
            check(id.equals(back.lineId()) && back.chairIndex() == 0 && back.barDown(), "the chair's slot or bar changed");
            check(LiftChairs.find(level, id, 0) == back && back.getPassengers().contains(second), "the reloaded chair is not the tracked one, or lost the rider");
            for (int i = 0; i < 100; i++) {
                LiftManager.tick(level);
                back.tick();
            }
            V3 want = loaded.path().chairPos(loaded.travel, 0, loaded.chairCount());
            check(Math.abs(back.getX() - want.x()) < 1e-6 && Math.abs(back.getY() - want.y()) < 1e-6 && Math.abs(back.getZ() - want.z()) < 1e-6,
                    "the chair is at " + back.position() + ", wanted " + want);
            check(second.getVehicle() == back, "the rider left the chair");
        } finally {
            RideGameTests.removeServerPlayer(first);
            RideGameTests.removeServerPlayer(second);
            cleanUp(level, line);
        }
        helper.succeed();
    }

    /** The doc's budgets (section 22): 0.3 ms a tick for five lines and 5 microseconds a chair; the test fails at three times each. */
    static final double MANAGER_BUDGET_MS = 0.3, CHAIR_BUDGET_US = 5.0;

    /**
     * Five lines of about fifty chairs each, run for 200 ticks after 40 of warm-up: the mean time of
     * {@link LiftManager#tick} and the mean time of a chair's tick. The chairs are loose entities (never added to
     * the level), so the number is the chair's own work, without the entity manager's bookkeeping when it moves.
     */
    public static void liftManagerBudget(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(1, 1, 1));
        List<LiftLine> lines = new ArrayList<>();
        List<ChairEntity> chairs = new ArrayList<>();
        try {
            for (int k = 0; k < 5; k++) {
                List<BlockPos> nodes = new ArrayList<>();
                for (int i = 0; i < 6; i++) nodes.add(base.offset(16 * k, 100 - 6 * i, 125 * i));
                LiftLine line = LiftManager.create(level, nodes);
                check(line != null, "line " + k + " was not created");
                lines.add(line);
                LiftManager.setDrive(level, line.id, true, 2.5);
                check(line.chairCount() >= 50, "line " + k + " has only " + line.chairCount() + " chairs");
                for (int i = 0; i < line.chairCount(); i++) {
                    ChairEntity chair = looseChair(level);
                    chair.setLine(line.id, i);
                    chair.tick(); // claims its slot, so the manager does not spawn one beside it
                    chairs.add(chair);
                }
            }
            int warmUp = 40, measured = 200;
            long managerNanos = 0, chairNanos = 0;
            for (int tick = 0; tick < warmUp + measured; tick++) {
                long start = System.nanoTime();
                LiftManager.tick(level);
                long mid = System.nanoTime();
                for (ChairEntity chair : chairs) chair.tick();
                long end = System.nanoTime();
                if (tick >= warmUp) {
                    managerNanos += mid - start;
                    chairNanos += end - mid;
                }
            }
            double managerMs = managerNanos / 1.0e6 / measured;
            double chairUs = chairNanos / 1.0e3 / measured / chairs.size();
            AlpineShred.LOGGER.info(String.format(Locale.ROOT,
                    "[lift budget] %d lines, %d chairs, %d ticks: manager %.4f ms a tick (budget %.1f), chair %.3f us a tick (budget %.1f)",
                    lines.size(), chairs.size(), measured, managerMs, MANAGER_BUDGET_MS, chairUs, CHAIR_BUDGET_US));
            check(managerMs < 3 * MANAGER_BUDGET_MS, String.format(Locale.ROOT, "the manager takes %.4f ms a tick, over 3x the %.1f ms budget", managerMs, MANAGER_BUDGET_MS));
            check(chairUs < 3 * CHAIR_BUDGET_US, String.format(Locale.ROOT, "a chair takes %.3f us a tick, over 3x the %.1f us budget", chairUs, CHAIR_BUDGET_US));
        } finally {
            for (LiftLine line : lines) cleanUp(level, line);
        }
        helper.succeed();
    }
}
