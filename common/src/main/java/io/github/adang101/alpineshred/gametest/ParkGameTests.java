package io.github.adang101.alpineshred.gametest;

import io.github.adang101.alpineshred.gametest.RideGameTests.Replay;
import io.github.adang101.alpineshred.gametest.RideGameTests.ReplayRun;
import io.github.adang101.alpineshred.gametest.RideGameTests.Ride;
import io.github.adang101.alpineshred.movement.RideInput;
import io.github.adang101.alpineshred.movement.RideServer;
import io.github.adang101.alpineshred.movement.RideState;
import io.github.adang101.alpineshred.movement.Riders;
import io.github.adang101.alpineshred.movement.SlideController;
import io.github.adang101.alpineshred.movement.SurfaceProfile;
import io.github.adang101.alpineshred.movement.TuningData;
import io.github.adang101.alpineshred.park.GrindMaterial;
import io.github.adang101.alpineshred.park.GrindPath;
import io.github.adang101.alpineshred.park.GrindPhysics;
import io.github.adang101.alpineshred.park.PipeArc;
import io.github.adang101.alpineshred.park.RailPlacement;
import io.github.adang101.alpineshred.park.RailShape;
import io.github.adang101.alpineshred.park.block.BoxBlock;
import io.github.adang101.alpineshred.park.block.CopperRailBlock;
import io.github.adang101.alpineshred.park.block.GrindPaths;
import io.github.adang101.alpineshred.park.block.LevelRails;
import io.github.adang101.alpineshred.park.block.ParkStates;
import io.github.adang101.alpineshred.park.block.PipeBlock;
import io.github.adang101.alpineshred.park.block.RailBlock;
import io.github.adang101.alpineshred.park.block.RampBlock;
import io.github.adang101.alpineshred.registry.ModBlocks;
import io.github.adang101.alpineshred.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static io.github.adang101.alpineshred.gametest.RideGameTests.check;

/**
 * Phase 4 game tests (design doc sections 10, 11 and 19; the phase exit gate is a 10-piece rail
 * chain, a box slide and a pipe ride). Park pieces are placed the way a player places them, by
 * using the item on the ground, so placement, connection and shapes are tested with the ride.
 * Courses run along +z in the {@value RideGameTests#LARGE} template, riders facing south.
 */
public final class ParkGameTests {
    /** Rail and lane center: block x 4. */
    private static final int X = 4;

    private ParkGameTests() {
    }

    // ---- building, the way a player does -----------------------------------------------------------

    /** Uses {@code item} on the top face of the test-relative block {@code ground}, facing south. */
    private static InteractionResult place(GameTestHelper helper, Player builder, Item item, BlockPos ground, boolean sneak) {
        BlockPos abs = helper.absolutePos(ground);
        builder.setPos(abs.getX() + 0.5, abs.getY() + 1, abs.getZ() - 1.5);
        builder.setYRot(0); // south
        builder.setXRot(30);
        builder.setShiftKeyDown(sneak);
        builder.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false);
        InteractionResult result = builder.getItemInHand(InteractionHand.MAIN_HAND).useOn(new UseOnContext(builder, InteractionHand.MAIN_HAND, hit));
        builder.setShiftKeyDown(false);
        return result;
    }

    private static BlockState at(GameTestHelper helper, int x, int y, int z) {
        return helper.getBlockState(new BlockPos(x, y, z));
    }

    /**
     * The 10-piece line used by the rail tests, placed piece by piece on the lane: four flat pieces
     * on the floor (top y 2), a rise where the ground steps up a block at z 15, then four flat
     * pieces on the higher ground. Placement picks the rise itself.
     */
    private static void buildRailLine(GameTestHelper helper, Course course, Item rail) {
        course.flat(0, 9, 2.5, Blocks.SNOW_BLOCK);    // run-in, level with the rail
        course.flat(10, 14, 2, Blocks.STONE);         // under the first five pieces
        course.flat(15, 19, 3, Blocks.STONE);         // the step up
        course.flat(20, 40, 3, Blocks.SNOW_BLOCK);    // landing
        Player builder = helper.makeMockSurvivalPlayer();
        for (int z = 10; z <= 14; z++) check(place(helper, builder, rail, new BlockPos(X, 1, z), false).consumesAction(), "rail not placed at z " + z);
        for (int z = 15; z <= 19; z++) check(place(helper, builder, rail, new BlockPos(X, 2, z), false).consumesAction(), "rail not placed at z " + z);
    }

    // ---- rails (section 10) --------------------------------------------------------------------------

    /** Placement: shapes follow the ground, ends connect, legs stand on the floor. */
    public static void railsConnectAndFollowTheGround(GameTestHelper helper) {
        Course course = new Course(helper);
        buildRailLine(helper, course, ModBlocks.PIPE_RAIL.get().asItem());
        List<String> pieces = new ArrayList<>();
        for (int z = 10; z <= 19; z++) {
            int y = z < 15 ? 2 : 3;
            BlockState s = at(helper, X, y, z);
            check(s.getBlock() instanceof RailBlock, "no rail at y " + y + " z " + z + ": " + s);
            pieces.add(RailBlock.shape(s) + "/" + RailBlock.facing(s));
        }
        BlockState first = at(helper, X, 2, 10);
        check(first.getValue(ParkStates.CONNECTED_A) != first.getValue(ParkStates.CONNECTED_B), "first piece should have one open end: " + first);
        check(RailBlock.shape(at(helper, X, 2, 14)) == RailShape.RISE_A, "no rise before the step: " + pieces);
        check(RailBlock.shape(at(helper, X, 3, 15)) == RailShape.RISE_B, "second half of the rise missing: " + pieces);
        for (int z : new int[]{10, 11, 12, 13, 16, 17, 18, 19}) {
            int y = z < 15 ? 2 : 3;
            check(RailBlock.shape(at(helper, X, y, z)) == RailShape.FLAT, "piece at z " + z + " is not flat: " + pieces);
        }
        for (int z = 11; z <= 18; z++) {
            BlockState s = at(helper, X, z < 15 ? 2 : 3, z);
            check(s.getValue(ParkStates.CONNECTED_A) && s.getValue(ParkStates.CONNECTED_B), "piece at z " + z + " has an open end: " + s);
        }
        BlockState last = at(helper, X, 3, 19);
        check(last.getValue(ParkStates.CONNECTED_A) != last.getValue(ParkStates.CONNECTED_B), "last piece should have one open end: " + last);
        check(at(helper, X, 2, 11).getValue(ParkStates.LEGS), "a rail on the floor should stand on legs");

        // Breaking a piece opens its neighbours' ends.
        helper.setBlock(new BlockPos(X, 2, 12), Blocks.AIR);
        check(!at(helper, X, 2, 11).getValue(ParkStates.CONNECTED_A) || !at(helper, X, 2, 11).getValue(ParkStates.CONNECTED_B),
                "neighbour still connected after a break");
        helper.succeed();
    }

    /**
     * Exit gate: a rider glides off the run-in onto a 10-piece line with a rise in it and grinds
     * every piece to the end, catching the next piece at each joint, then leaves the end into the air.
     */
    public static void tenPieceRailChainGrindsEndToEnd(GameTestHelper helper) {
        Course course = new Course(helper);
        buildRailLine(helper, course, ModBlocks.PIPE_RAIL.get().asItem());
        Player rider = Course.boarder(helper);
        course.place(rider, 2.5, 3.5, 0.6);
        Ride ride = new Ride(rider);

        int caughtAt = -1, leftAt = -1;
        double caughtZ = Double.NaN, leftZ = Double.NaN;
        java.util.Set<Long> pieces = new java.util.HashSet<>();
        List<String> trace = new ArrayList<>();
        for (int tick = 0; tick < 120 && leftAt < 0; tick++) {
            ride.tick(RideInput.coast(0));
            SlideController c = ride.c();
            Vec3 rel = course.relative(rider);
            trace.add(String.format(Locale.ROOT, "t%d z%.2f y%.3f %s v%.3f", tick, rel.z, rel.y, c.state(), c.speed()));
            check(c.state() != RideState.WIPEOUT, "wiped out\n" + String.join("\n", RideGameTests.tail(trace)));
            if (c.state() == RideState.GRIND) {
                if (caughtAt < 0) {
                    caughtAt = tick;
                    caughtZ = rel.z;
                }
                pieces.add(c.grind().path().id());
                check(c.grind().path().material() == GrindMaterial.METAL, "pipe rail ground as " + c.grind().path().material());
            } else if (caughtAt >= 0) {
                leftAt = tick;
                leftZ = rel.z;
            }
        }
        String log = "\n" + String.join("\n", RideGameTests.tail(trace));
        check(caughtAt >= 0, "never caught the rail" + log);
        check(caughtZ <= 10.6, String.format(Locale.ROOT, "caught late, at z %.2f", caughtZ) + log);
        check(leftAt > 0, "never left the rail" + log);
        check(leftZ >= 19.8, String.format(Locale.ROOT, "fell off at z %.2f, before the end of the line", leftZ) + log);
        check(pieces.size() == 10, "ground " + pieces.size() + " pieces of 10" + log);
        check(ride.c().state() == RideState.AIR || ride.c().state().isGroundRiding(), "after the rail: " + ride.describe());
        check(ride.c().speed() > 0.2, "stalled on the rise: " + ride.describe());
        helper.succeed();
    }

    /** The same line, replayed through a real server connection: no rubber-band, no validator flag. */
    public static void serverAcceptsARailGrind(GameTestHelper helper) {
        Course course = new Course(helper);
        buildRailLine(helper, course, ModBlocks.PIPE_RAIL.get().asItem());
        Replay run = RideGameTests.replayOnServer(helper, course, new ReplayRun(2.5, 3.5, 0.6, 26, 120), false, z -> RideInput.coast(0));
        check(run.grindTicks >= 12, "only " + run.grindTicks + " ticks grinding: " + run.states);
        helper.succeed();
    }

    /** Waxed rails are faster, oxidized copper slower; honeycomb and axe follow vanilla copper. */
    public static void waxAndOxidationChangeRailFriction(GameTestHelper helper) {
        Player builder = helper.makeMockSurvivalPlayer();
        BlockPos pos = new BlockPos(X, 2, 4);
        helper.setBlock(pos, ModBlocks.COPPER_RAILS.get(0).get());
        BlockPos abs = helper.absolutePos(pos);
        RailBlock fresh = (RailBlock) at(helper, X, 2, 4).getBlock();
        double freshFriction = fresh.friction(at(helper, X, 2, 4));

        use(helper, builder, abs, new ItemStack(Items.HONEYCOMB));
        BlockState waxed = helper.getBlockState(pos);
        check(waxed.getBlock() == ModBlocks.WAXED_COPPER_RAILS.get(0).get(), "honeycomb did not wax: " + waxed);
        check(waxed.getValue(ParkStates.WAXED), "waxed rail without the waxed flag");
        check(((RailBlock) waxed.getBlock()).friction(waxed) < freshFriction, "waxed rail is not slicker");
        GrindPath path = GrindPaths.at(helper.getLevel(), abs);
        check(path != null && path.material() == GrindMaterial.COPPER, "copper rail path " + path);

        use(helper, builder, abs, new ItemStack(Items.IRON_AXE));
        check(helper.getBlockState(pos).getBlock() == ModBlocks.COPPER_RAILS.get(0).get(), "axe did not take the wax off: " + helper.getBlockState(pos));

        helper.setBlock(pos, ModBlocks.COPPER_RAILS.get(3).get());
        BlockState oxidized = helper.getBlockState(pos);
        check(((RailBlock) oxidized.getBlock()).friction(oxidized) > freshFriction, "oxidized copper is not rougher");
        check(((CopperRailBlock) oxidized.getBlock()).getNext(oxidized).isEmpty(), "oxidized copper should not weather further");
        check(((CopperRailBlock) fresh).getNext(fresh.defaultBlockState()).map(BlockState::getBlock).orElse(null)
                == ModBlocks.COPPER_RAILS.get(1).get(), "fresh copper should weather to exposed");
        use(helper, builder, abs, new ItemStack(Items.IRON_AXE));
        check(helper.getBlockState(pos).getBlock() == ModBlocks.COPPER_RAILS.get(2).get(), "axe did not scrape a stage: " + helper.getBlockState(pos));
        helper.succeed();
    }

    /** Recomputes a block's connections from its neighbours, as placing it by hand would. */
    private static void settle(GameTestHelper helper, BlockPos relative) {
        BlockPos abs = helper.absolutePos(relative);
        var level = helper.getLevel();
        level.setBlock(abs, Block.updateFromNeighbourShapes(level.getBlockState(abs), level, abs), Block.UPDATE_ALL);
    }

    private static void use(GameTestHelper helper, Player player, BlockPos abs, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        BlockState state = helper.getLevel().getBlockState(abs);
        state.use(helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
    }

    /** Fences carry rails (no legs) and grind as wood; walls as stone (section 10, vanilla blocks as rails). */
    public static void fencesAndWallsGrind(GameTestHelper helper) {
        Course course = new Course(helper);
        course.flat(0, 30, 2, Blocks.SNOW_BLOCK);
        for (int z = 6; z <= 12; z++) course.set(X, 2, z, Blocks.OAK_FENCE.defaultBlockState());
        for (int z = 14; z <= 18; z++) course.set(X, 2, z, Blocks.COBBLESTONE_WALL.defaultBlockState());
        for (int z = 6; z <= 18; z++) settle(helper, new BlockPos(X, 2, z)); // as if placed by hand
        // The fence posts joined up through their neighbour updates.
        GrindPath fence = GrindPaths.at(helper.getLevel(), helper.absolutePos(new BlockPos(X, 2, 8)));
        check(fence != null && fence.material() == GrindMaterial.WOOD, "fence path " + fence);
        check(Math.abs(fence.path().endA().y() - (helper.absolutePos(BlockPos.ZERO).getY() + 3.5)) < 1.0e-6, "fence path height " + fence.path().endA());
        GrindPath wall = GrindPaths.at(helper.getLevel(), helper.absolutePos(new BlockPos(X, 2, 16)));
        check(wall != null && wall.material() == GrindMaterial.STONE, "wall path " + wall);
        GrindPath lone = GrindPaths.at(helper.getLevel(), helper.absolutePos(new BlockPos(X, 2, 2)));
        check(lone == null, "a snow block has a path");

        Player builder = helper.makeMockSurvivalPlayer();
        place(helper, builder, ModBlocks.PIPE_RAIL.get().asItem(), new BlockPos(X, 2, 10), false);
        BlockState onFence = at(helper, X, 3, 10);
        check(onFence.getBlock() instanceof RailBlock && !onFence.getValue(ParkStates.LEGS), "a rail on a fence post should have no legs: " + onFence);
        helper.succeed();
    }

    // ---- boxes (section 11) --------------------------------------------------------------------------

    /**
     * Exit gate: a rider glides up onto a row of low metal boxes, slides it with box friction and
     * reduced steering, and leaves the end into the air.
     */
    public static void boxSlideRunsTheBox(GameTestHelper helper) {
        Course course = new Course(helper);
        course.flat(0, 40, 2, Blocks.SNOW_BLOCK);
        Player builder = helper.makeMockSurvivalPlayer();
        for (int z = 10; z <= 19; z++) {
            check(place(helper, builder, ModBlocks.METAL_BOX.get().asItem(), new BlockPos(X, 1, z), true).consumesAction(), "box not placed at " + z);
        }
        for (int z = 10; z <= 19; z++) {
            BlockState s = at(helper, X, 2, z);
            check(s.getBlock() instanceof BoxBlock && s.getValue(ParkStates.BOX_HALF) == ParkStates.BoxHalf.LOW, "not a low box at z " + z + ": " + s);
            check(s.getValue(BoxBlock.AXIS) == Direction.Axis.Z, "box at z " + z + " runs " + s.getValue(BoxBlock.AXIS));
            check(s.getValue(ParkStates.END_A) == (z == 10) && s.getValue(ParkStates.END_B) == (z == 19), "box ends at z " + z + ": " + s);
        }
        // Only the box is under the lane center; snow either side would let the rider glide off.
        Player rider = Course.boarder(helper);
        course.place(rider, 2, 3.5, 0.55);
        Ride ride = new Ride(rider);
        int slideTicks = 0;
        double onAt = Double.NaN, offAt = Double.NaN, speedOn = 0, speedOff = 0;
        List<String> trace = new ArrayList<>();
        for (int tick = 0; tick < 80 && Double.isNaN(offAt); tick++) {
            ride.tick(RideInput.coast(0));
            SlideController c = ride.c();
            Vec3 rel = course.relative(rider);
            trace.add(String.format(Locale.ROOT, "t%d z%.2f y%.3f %s v%.3f %s", tick, rel.z, rel.y, c.state(), c.speed(),
                    c.surface() == null ? "-" : c.surface().id()));
            check(c.state() != RideState.WIPEOUT, "wiped out\n" + String.join("\n", RideGameTests.tail(trace)));
            if (c.state() == RideState.SLIDE) {
                slideTicks++;
                if (Double.isNaN(onAt)) {
                    onAt = rel.z;
                    speedOn = c.speed();
                }
                check(c.surface() != null && c.surface().category() == SurfaceProfile.Category.BOX && c.surface().id().equals("box_metal"),
                        "sliding on " + (c.surface() == null ? "nothing" : c.surface().id()));
                speedOff = c.speed();
            } else if (!Double.isNaN(onAt)) {
                offAt = rel.z;
            }
        }
        String log = "\n" + String.join("\n", RideGameTests.tail(trace));
        check(!Double.isNaN(onAt), "never slid the box" + log);
        check(onAt <= 11, String.format(Locale.ROOT, "got on the box late, at z %.2f", onAt) + log);
        check(!Double.isNaN(offAt) && offAt >= 19.5, String.format(Locale.ROOT, "left the box at z %.2f", offAt) + log);
        check(slideTicks >= 12, "slid only " + slideTicks + " ticks" + log);
        check(speedOff < speedOn && speedOff > speedOn - 0.15, String.format(Locale.ROOT, "speed %.3f on, %.3f off", speedOn, speedOff) + log);
        helper.succeed();
    }

    /** The box slide replayed through a real server connection. */
    public static void serverAcceptsABoxSlide(GameTestHelper helper) {
        Course course = new Course(helper);
        course.flat(0, 40, 2, Blocks.SNOW_BLOCK);
        Player builder = helper.makeMockSurvivalPlayer();
        for (int z = 10; z <= 19; z++) place(helper, builder, ModBlocks.WOOD_BOX.get().asItem(), new BlockPos(X, 1, z), true);
        Replay run = RideGameTests.replayOnServer(helper, course, new ReplayRun(2, 3.5, 0.55, 28, 120), false, z -> RideInput.coast(0));
        check(run.slideTicks >= 12, "only " + run.slideTicks + " ticks sliding: " + run.states);
        helper.succeed();
    }

    // ---- ramps and pipes (section 11) ---------------------------------------------------------------

    /** Ramps placed in a row continue each other's slope; the wrench changes slope and lip. */
    public static void rampsStackIntoRuns(GameTestHelper helper) {
        Course course = new Course(helper);
        course.flat(0, 20, 2, Blocks.STONE);
        Player builder = helper.makeMockSurvivalPlayer();
        for (int z = 6; z <= 7; z++) place(helper, builder, ModBlocks.PARK_RAMP.get().asItem(), new BlockPos(X, 1, z), false);
        BlockState first = at(helper, X, 2, 6), second = at(helper, X, 2, 7);
        check(first.getBlock() instanceof RampBlock && RampBlock.slope(first).blocks == 2, "default ramp slope: " + first);
        check(RampBlock.step(first) == 0 && RampBlock.step(second) == 1, "ramp steps " + RampBlock.step(first) + ", " + RampBlock.step(second));
        check(first.getValue(RampBlock.FACING) == Direction.SOUTH, "ramp rises " + first.getValue(RampBlock.FACING));

        BlockPos abs = helper.absolutePos(new BlockPos(X, 2, 7));
        wrench(helper, builder, abs, false);
        check(at(helper, X, 2, 7).getValue(ParkStates.RAMP_SLOPE) != ParkStates.RampSlopeProp.HALF, "wrench did not change the slope");
        wrench(helper, builder, abs, true);
        check(at(helper, X, 2, 7).getValue(ParkStates.LIP), "sneak-wrench did not set the lip");
        helper.succeed();
    }

    private static void wrench(GameTestHelper helper, Player player, BlockPos abs, boolean sneak) {
        player.setShiftKeyDown(sneak);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.PARK_WRENCH.get()));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        player.getItemInHand(InteractionHand.MAIN_HAND).useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        player.setShiftKeyDown(false);
    }

    /** The wrench on a rail: the only option in a straight line says so; sneak turns the piece. */
    public static void wrenchCyclesAndTurnsRails(GameTestHelper helper) {
        Course course = new Course(helper);
        course.flat(0, 20, 2, Blocks.STONE);
        Player builder = helper.makeMockSurvivalPlayer();
        place(helper, builder, ModBlocks.PIPE_RAIL.get().asItem(), new BlockPos(X, 1, 8), false);
        BlockPos abs = helper.absolutePos(new BlockPos(X, 2, 8));
        RailPlacement.Piece before = RailBlock.piece(helper.getBlockState(new BlockPos(X, 2, 8)));
        List<RailPlacement.Piece> options = RailPlacement.alternatives(new LevelRails(helper.getLevel()), LevelRails.cell(abs), before);
        check(options.size() > 1, "a lone piece should have alternatives: " + options);
        wrench(helper, builder, abs, false);
        RailPlacement.Piece cycled = RailBlock.piece(helper.getBlockState(new BlockPos(X, 2, 8)));
        check(!cycled.equals(before), "wrench did not change a lone piece");
        wrench(helper, builder, abs, true);
        RailPlacement.Piece turned = RailBlock.piece(helper.getBlockState(new BlockPos(X, 2, 8)));
        check(turned.shape() == cycled.shape() && turned.facing() == cycled.facing().clockwise(), "sneak-wrench did not turn: " + cycled + " -> " + turned);
        helper.succeed();
    }

    /** One item places the whole pipe; breaking one block removes it all. */
    public static void quarterPipePlacesWhole(GameTestHelper helper) {
        Course course = new Course(helper);
        course.flat(0, 30, 2, Blocks.STONE);
        Player builder = helper.makeMockSurvivalPlayer();
        check(place(helper, builder, ModItems.parkItem("quarter_pipe").get(), new BlockPos(X, 1, 10), false).consumesAction(), "pipe not placed");
        List<PipeArc.Cell> cells = PipeArc.cells(3);
        for (int k = 0; k < cells.size(); k++) {
            PipeArc.Cell c = cells.get(k);
            BlockState s = at(helper, X, 2 + c.j(), 10 + c.i());
            check(s.getBlock() instanceof PipeBlock && PipeBlock.index(s) == k && PipeBlock.radius(s) == 3, "pipe block " + k + ": " + s);
        }
        check(place(helper, builder, ModItems.parkItem("quarter_pipe").get(), new BlockPos(X, 1, 11), true) == InteractionResult.FAIL,
                "a radius 5 pipe overlapping the first was placed");
        BlockPos top = helper.absolutePos(new BlockPos(X, 4, 12));
        BlockState topState = helper.getLevel().getBlockState(top);
        topState.getBlock().playerWillDestroy(helper.getLevel(), top, topState, builder);
        helper.getLevel().removeBlock(top, false);
        for (PipeArc.Cell c : cells) {
            check(at(helper, X, 2 + c.j(), 10 + c.i()).isAir(), "pipe block left at " + c);
        }
        helper.succeed();
    }

    /** A pipe of {@code radius} rising south from z 15 on the floor (top y 2), with a run-in. */
    private static void buildPipe(GameTestHelper helper, Course course, int radius) {
        course.flat(0, 30, 2, Blocks.SNOW_BLOCK);
        Player builder = helper.makeMockSurvivalPlayer();
        check(place(helper, builder, ModItems.parkItem("quarter_pipe").get(), new BlockPos(X, 1, 15), radius == 5).consumesAction(),
                "pipe not placed");
        // Widen it: pipes side by side ride as one section.
        for (int x : new int[]{X - 1, X + 1}) {
            BlockPos abs = helper.absolutePos(new BlockPos(x, 1, 15));
            builder.setPos(abs.getX() + 0.5, abs.getY() + 1, abs.getZ() - 1.5);
            builder.setShiftKeyDown(radius == 5);
            builder.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.parkItem("quarter_pipe").get()));
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false);
            builder.getItemInHand(InteractionHand.MAIN_HAND).useOn(new UseOnContext(builder, InteractionHand.MAIN_HAND, hit));
            builder.setShiftKeyDown(false);
        }
    }

    /**
     * Exit gate: a rider rides up a radius 3 quarter pipe, stops below the coping, rides back down
     * and out onto the flat going the other way.
     */
    public static void pipeRideGoesUpAndComesBack(GameTestHelper helper) {
        Course course = new Course(helper);
        buildPipe(helper, course, 3);
        Player rider = Course.boarder(helper);
        // Slopes and arcs share the riding gravity (slopeAccelScale), so a slow entry rises a block or two.
        course.place(rider, 2, 10.5, 0.33);
        Ride ride = new Ride(rider);
        double maxY = 0;
        boolean entered = false, back = false;
        List<String> trace = new ArrayList<>();
        for (int tick = 0; tick < 120 && !back; tick++) {
            ride.tick(RideInput.coast(0));
            SlideController c = ride.c();
            Vec3 rel = course.relative(rider);
            trace.add(String.format(Locale.ROOT, "t%d z%.2f y%.3f %s v%.3f pipe %b", tick, rel.z, rel.y, c.state(), c.speed(), c.inPipe()));
            check(c.state() != RideState.WIPEOUT, "wiped out\n" + String.join("\n", RideGameTests.tail(trace)));
            check(c.state() != RideState.AIR, "left the pipe into the air\n" + String.join("\n", RideGameTests.tail(trace)));
            if (c.inPipe()) entered = true;
            maxY = Math.max(maxY, rel.y);
            if (entered && !c.inPipe() && rel.z < 15) back = Math.abs(io.github.adang101.alpineshred.movement.SlideModel.wrapDegrees(c.travelYaw() - 180)) < 20;
        }
        String log = "\n" + String.join("\n", RideGameTests.tail(trace));
        check(entered, "never rode onto the pipe" + log);
        check(maxY - 2 > 0.6, String.format(Locale.ROOT, "rose only %.2f blocks", maxY - 2) + log);
        check(maxY - 2 < 3, String.format(Locale.ROOT, "rose %.2f blocks, over the coping", maxY - 2) + log);
        check(back, "never rode back out of the pipe" + log);
        helper.succeed();
    }

    /** Fast enough, the rider leaves the top of the wall straight up, falls back in and rides out. */
    public static void pipeTopLaunchesAndReenters(GameTestHelper helper) {
        Course course = new Course(helper);
        buildPipe(helper, course, 3);
        Player rider = Course.boarder(helper);
        course.place(rider, 2, 2.5, 0.95);
        Ride ride = new Ride(rider);
        boolean launched = false, reentered = false, back = false;
        int airTicks = 0;
        List<String> trace = new ArrayList<>();
        for (int tick = 0; tick < 160 && !back; tick++) {
            ride.tick(RideInput.coast(0));
            SlideController c = ride.c();
            Vec3 rel = course.relative(rider);
            trace.add(String.format(Locale.ROOT, "t%d z%.2f y%.3f %s v%.3f vy%.3f pipe %b", tick, rel.z, rel.y, c.state(), c.speed(),
                    c.verticalVelocity(), c.inPipe()));
            check(c.state() != RideState.WIPEOUT, "wiped out\n" + String.join("\n", RideGameTests.tail(trace)));
            for (SlideController.Event e : c.events()) {
                if (e instanceof SlideController.Launched) launched = true;
            }
            if (c.state() == RideState.AIR) airTicks++;
            if (launched && c.inPipe()) reentered = true;
            if (reentered && !c.inPipe() && rel.z < 15) back = true;
        }
        String log = "\n" + String.join("\n", RideGameTests.tail(trace));
        check(launched, "never launched off the coping" + log);
        check(airTicks >= 5, "only " + airTicks + " ticks in the air" + log);
        check(reentered, "never came back down into the pipe" + log);
        check(back, "never rode back out" + log);
        helper.succeed();
    }

    /** The pipe ride replayed through a real server connection: the arc's rise is allowed. */
    public static void serverAcceptsAPipeRide(GameTestHelper helper) {
        Course course = new Course(helper);
        buildPipe(helper, course, 5);
        Replay run = RideGameTests.replayOnServer(helper, course, new ReplayRun(2, 4.5, 0.8, Double.NaN, 70), false, z -> RideInput.coast(0));
        check(run.pipeTicks >= 10, "only " + run.pipeTicks + " ticks on the pipe: " + run.states);
        helper.succeed();
    }

    // ---- server checks -----------------------------------------------------------------------------

    /** GRIND needs a rail in the capture zone and SLIDE a box underfoot (section 7, rule 3). */
    public static void parkClaimsNeedTheFeature(GameTestHelper helper) {
        Course course = new Course(helper);
        course.flat(0, 20, 2, Blocks.STONE);
        Player builder = helper.makeMockSurvivalPlayer();
        place(helper, builder, ModBlocks.PIPE_RAIL.get().asItem(), new BlockPos(X, 1, 8), false);
        place(helper, builder, ModBlocks.WOOD_BOX.get().asItem(), new BlockPos(X, 1, 14), false);
        Player rider = Course.boarder(helper);
        course.place(rider, 2, 3.5, 0);
        check(RideServer.handleState(rider, Riders.get(rider).withState(RideState.GLIDE).pack()), "GLIDE refused");
        int before = RideServer.violations(rider);
        check(!RideServer.handleState(rider, Riders.get(rider).withState(RideState.GRIND).pack()), "GRIND accepted on bare ground");
        check(!RideServer.handleState(rider, Riders.get(rider).withState(RideState.SLIDE).pack()), "SLIDE accepted on bare ground");
        check(RideServer.violations(rider) == before + 2, "refused claims not counted");

        Vec3 onRail = course.at(Course.CENTER_X, 2.5, 8.5);
        rider.setPos(onRail.x, onRail.y, onRail.z);
        check(RideServer.handleState(rider, Riders.get(rider).withState(RideState.GRIND).pack()), "GRIND refused on a rail");
        check(RideServer.handleState(rider, Riders.get(rider).withState(RideState.AIR).pack()), "AIR off the rail refused");
        Vec3 onBox = course.at(Course.CENTER_X, 3.0, 14.5);
        rider.setPos(onBox.x, onBox.y, onBox.z);
        check(RideServer.handleState(rider, Riders.get(rider).withState(RideState.SLIDE).pack()), "SLIDE refused on a box");
        check(RideServer.violations(rider) == before + 2, "legal park claims flagged: " + RideServer.lastReason(rider));
        double friction = TuningData.tuning().railFriction;
        check(friction > 0 && GrindPhysics.CAPTURE_RADIUS > 0, "tuning");
        helper.succeed();
    }

}
