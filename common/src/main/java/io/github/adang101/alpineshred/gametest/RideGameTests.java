package io.github.adang101.alpineshred.gametest;

import io.github.adang101.alpineshred.config.AlpineConfig;
import io.github.adang101.alpineshred.gear.GearAccess;
import io.github.adang101.alpineshred.gear.GearNbt;
import io.github.adang101.alpineshred.gear.GearProfiles;
import io.github.adang101.alpineshred.mixin.ServerGamePacketListenerAccessor;
import io.github.adang101.alpineshred.movement.MovementTuning;
import io.github.adang101.alpineshred.movement.PlayerBody;
import io.github.adang101.alpineshred.movement.RideContext;
import io.github.adang101.alpineshred.movement.RideInput;
import io.github.adang101.alpineshred.movement.RideMode;
import io.github.adang101.alpineshred.movement.RideRunner;
import io.github.adang101.alpineshred.movement.RideServer;
import io.github.adang101.alpineshred.movement.RideSnapshot;
import io.github.adang101.alpineshred.movement.RideState;
import io.github.adang101.alpineshred.movement.RideStateHolder;
import io.github.adang101.alpineshred.movement.Riders;
import io.github.adang101.alpineshred.movement.SlideController;
import io.github.adang101.alpineshred.movement.SlideModel;
import io.github.adang101.alpineshred.movement.StateSendThrottle;
import io.github.adang101.alpineshred.movement.StrapController;
import io.github.adang101.alpineshred.movement.SurfaceProfile;
import io.github.adang101.alpineshred.movement.SurfaceResolver;
import io.github.adang101.alpineshred.movement.TuningData;
import io.github.adang101.alpineshred.network.RideAction;
import io.github.adang101.alpineshred.network.SyncCodec;
import io.github.adang101.alpineshred.registry.ModBlocks;
import io.github.adang101.alpineshred.registry.ModItems;
import io.github.adang101.alpineshred.trick.GrabType;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Phase 2 game tests (design doc section 19): the slide controller on real collision, the mixins,
 * the server side of riding, and risk R2. The controller runs on the client in play, so these call
 * its step function through {@link RideRunner} with scripted input on test players, a tick at a
 * time, inside one test method. Courses are built with {@link Course} in the {@value #LARGE}
 * template.
 */
public final class RideGameTests {
    public static final String LARGE = "empty_large";

    private RideGameTests() {
    }

    // ---- surfaces and hooks -----------------------------------------------------------------------

    public static void surfaceProfilesMatchBlocksAndTags(GameTestHelper helper) {
        expectProfile("groomed", ModBlocks.GROOMED_SNOW.get().defaultBlockState());
        expectProfile("snow_block", Blocks.SNOW_BLOCK.defaultBlockState());
        expectProfile("snow_layer", Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 3));
        expectProfile("blue_ice", Blocks.BLUE_ICE.defaultBlockState());
        expectProfile("packed_ice", Blocks.PACKED_ICE.defaultBlockState());
        expectProfile("powder", Blocks.POWDER_SNOW.defaultBlockState());
        expectProfile("ice", Blocks.FROSTED_ICE.defaultBlockState()); // only through #alpineshred:slide/ice

        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var level = helper.getLevel();
        check(SurfaceResolver.forState(level, pos, Blocks.STONE.defaultBlockState()) == SurfaceProfile.SCRAPE, "stone should scrape");
        check(SurfaceResolver.forState(level, pos, Blocks.OAK_SLAB.defaultBlockState()) == SurfaceProfile.SCRAPE, "slabs should scrape");
        check(SurfaceResolver.forState(level, pos, Blocks.AIR.defaultBlockState()) == null, "air is no surface");
        check(SurfaceResolver.forState(level, pos, Blocks.WATER.defaultBlockState()) == null, "water is no surface");
        check(SurfaceResolver.forState(level, pos, Blocks.GRASS.defaultBlockState()) == null, "plants are no surface");
        helper.succeed();
    }

    public static void bodyFindsTheSurfaceUnderfoot(GameTestHelper helper) {
        Course course = new Course(helper);
        Player player = helper.makeMockSurvivalPlayer();
        PlayerBody body = new PlayerBody(player);

        // A one-layer snow cover has no collision box: the rider stands on the grass under it.
        course.set(2, 1, 2, Blocks.GRASS_BLOCK.defaultBlockState()).set(2, 2, 2, Blocks.SNOW.defaultBlockState());
        standAt(player, course.at(2.5, 2.0, 2.5));
        expectSurface("snow_layer", body.surfaceUnderFeet());

        course.set(4, 1, 2, Blocks.SNOW_BLOCK.defaultBlockState());
        standAt(player, course.at(4.5, 2.0, 2.5));
        expectSurface("snow_block", body.surfaceUnderFeet());

        course.set(6, 1, 2, Blocks.SNOW_BLOCK.defaultBlockState()).set(6, 2, 2, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 4));
        standAt(player, course.at(6.5, 2.375, 2.5));
        expectSurface("snow_layer", body.surfaceUnderFeet());

        // Center over air, box still on the block: the supporting block counts. One block above
        // the structure's floor, so there is air under the center.
        course.set(2, 2, 6, Blocks.BLUE_ICE.defaultBlockState());
        Vec3 edge = course.at(3.2, 3.0, 6.5);
        player.setPos(edge.x, edge.y, edge.z);
        player.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(0, -0.1, 0));
        check(player.onGround(), "rider on the ice edge should be on the ground");
        expectSurface("blue_ice", body.surfaceUnderFeet());

        standAt(player, course.at(4.5, 6.0, 8.5));
        player.setOnGround(false);
        check(body.surfaceUnderFeet() == null, "nothing under a rider in the air");
        helper.succeed();
    }

    public static void stepHeightAndPowderHooksFollowTheController(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        RideStateHolder holder = (RideStateHolder) player;
        float vanilla = player.maxUpStep();
        check(Math.abs(vanilla - 0.6f) < 1e-4, "vanilla step height " + vanilla);
        holder.alpineshred$setRideStep(1.0f);
        check(Math.abs(player.maxUpStep() - vanilla) < 1e-4, "step raised without a controller");
        holder.alpineshred$setControlled(true);
        check(Math.abs(player.maxUpStep() - 1.0f) < 1e-4, "controller step height not applied: " + player.maxUpStep());
        holder.alpineshred$setControlled(false);
        check(Math.abs(player.maxUpStep() - vanilla) < 1e-4, "step height not restored");

        check(!PowderSnowBlock.canEntityWalkOnPowderSnow(player), "powder walkable without a controller");
        holder.alpineshred$setFloatsOnPowder(true);
        check(PowderSnowBlock.canEntityWalkOnPowderSnow(player), "fast rider does not float on powder");
        player.setShiftKeyDown(true);
        check(!player.isDescending(), "braking makes a floating rider sink");
        holder.alpineshred$setFloatsOnPowder(false);
        check(player.isDescending(), "sneaking off powder should still descend");
        helper.succeed();
    }

    // ---- riding on real collision -------------------------------------------------------------------

    public static void staircaseDescentNeverFlickersToAir(GameTestHelper helper) {
        Course course = new Course(helper);
        // A 1-in-3 voxel staircase (18 degrees) of snow blocks, 19 steps down.
        for (int z = 0; z <= 63; z++) course.column(z, 22 - Math.floor(z / 3.0), Blocks.SNOW_BLOCK);
        Player rider = Course.boarder(helper);
        course.place(rider, 22, 1.5, 0.3);
        Ride ride = new Ride(rider);

        double startSpeed = 0.3;
        for (int tick = 0; tick < 200 && course.relative(rider).z < 60; tick++) {
            ride.tick(RideInput.coast(0));
            check(ride.mode() == SlideController.Mode.RIDE, "stopped riding at tick " + tick + " " + ride.describe());
            check(ride.c().state() != RideState.AIR, "AIR flicker at tick " + tick + " " + ride.describe());
            check(ride.c().state() != RideState.WIPEOUT, "wiped out at tick " + tick + " " + ride.describe());
        }
        check(course.relative(rider).z >= 60, "did not reach the bottom: " + ride.describe());
        check(ride.c().speed() > startSpeed + 0.3, "no speed gained down the slope: " + ride.describe());
        helper.succeed();
    }

    public static void kickerLaunchesAndLands(GameTestHelper helper) {
        Course course = new Course(helper);
        course.flat(0, 70, 2, ModBlocks.GROOMED_SNOW.get());
        // Rising 3/8 of a block per block: grade 0.375, above launchGrade (0.35).
        course.column(20, 2.375, ModBlocks.GROOMED_SNOW.get())
                .column(21, 2.75, ModBlocks.GROOMED_SNOW.get())
                .column(22, 3.125, ModBlocks.GROOMED_SNOW.get())
                .column(23, 3.5, ModBlocks.GROOMED_SNOW.get());
        Player rider = Course.boarder(helper);
        course.place(rider, 2, 4.5, 0.8);
        Ride ride = new Ride(rider);

        int airTicks = 0;
        boolean launched = false, landed = false;
        for (int tick = 0; tick < 120 && !landed; tick++) {
            ride.tick(RideInput.coast(0));
            for (SlideController.Event e : ride.c().events()) {
                if (e instanceof SlideController.Launched) launched = true;
                if (e instanceof SlideController.Landed) landed = launched;
                if (e instanceof SlideController.WipedOut) throw new GameTestAssertException("wiped out on the kicker " + ride.describe());
            }
            if (ride.c().state() == RideState.AIR) airTicks++;
        }
        check(launched, "kicker did not launch: " + ride.describe());
        check(landed, "never landed: " + ride.describe());
        check(airTicks >= 6, "only " + airTicks + " ticks in the air");
        check(ride.c().state().isGroundRiding(), "after landing: " + ride.describe());
        helper.succeed();
    }

    public static void wallHitWipesOutAndLocksInput(GameTestHelper helper) {
        Course course = new Course(helper);
        course.flat(0, 30, 2, Blocks.SNOW_BLOCK).wall(14, 2, 4, Blocks.STONE);
        Player rider = Course.boarder(helper);
        course.place(rider, 2, 4.5, 0.8);
        Ride ride = new Ride(rider);
        MovementTuning t = TuningData.tuning();

        int wipeoutAt = -1;
        for (int tick = 0; tick < 40 && wipeoutAt < 0; tick++) {
            ride.tick(RideInput.coast(0));
            if (ride.c().state() == RideState.WIPEOUT) wipeoutAt = tick;
        }
        check(wipeoutAt >= 0, "no wipeout at the wall: " + ride.describe());
        check(ride.c().speed() < 0.05, "still moving after the wall: " + ride.describe());
        // The hit tick counts as the first of wipeoutTicks; input pushing forward must do nothing.
        RideInput pushing = new RideInput(1, 1, 30, false, false, GrabType.NONE);
        for (int tick = 1; tick < t.wipeoutTicks - 1; tick++) {
            ride.tick(pushing);
            check(ride.c().state() == RideState.WIPEOUT, "wipeout ended early at " + tick);
            check(ride.c().speed() < 0.01, "input moved a wiped-out rider at " + tick + " " + ride.describe());
        }
        ride.tick(pushing);
        ride.tick(pushing);
        check(ride.c().state() != RideState.WIPEOUT, "wipeout did not end after " + t.wipeoutTicks + " ticks");
        helper.succeed();
    }

    public static void ridersStepUpOneBlockAtSpeedOnly(GameTestHelper helper) {
        Course course = new Course(helper);
        course.flat(0, 70, 2, Blocks.SNOW_BLOCK).flat(12, 70, 3, Blocks.SNOW_BLOCK);
        Player fast = Course.boarder(helper);
        course.place(fast, 2, 4.5, 0.5);
        Ride ride = new Ride(fast);
        for (int tick = 0; tick < 30; tick++) ride.tick(RideInput.coast(0));
        Vec3 at = course.relative(fast);
        check(at.z > 14 && Math.abs(at.y - 3) < 0.01, "fast rider did not climb the step: " + ride.describe());
        check(ride.c().state() != RideState.WIPEOUT, "fast rider wiped out on a one-block step");

        Player slow = Course.boarder(helper);
        course.place(slow, 2, 11.3, 0.05);
        Ride crawl = new Ride(slow);
        for (int tick = 0; tick < 30; tick++) crawl.tick(RideInput.coast(0));
        at = course.relative(slow);
        check(at.y < 2.01 && at.z < 11.71, "a rider at walking pace climbed a full block: " + crawl.describe());
        helper.succeed();
    }

    public static void powderCarriesFastRidersOnly(GameTestHelper helper) {
        Course course = new Course(helper);
        course.flat(0, 70, 1, Blocks.STONE);
        for (int x = Course.LANE_MIN_X; x <= Course.LANE_MAX_X; x++) {
            for (int z = 0; z <= 70; z++) course.set(x, 1, z, Blocks.POWDER_SNOW.defaultBlockState());
        }
        Player fast = Course.boarder(helper);
        course.place(fast, 2, 3.5, 0.6);
        Ride ride = new Ride(fast);
        for (int tick = 0; tick < 25; tick++) {
            ride.tick(RideInput.coast(0));
            check(ride.mode() == SlideController.Mode.RIDE, "fast rider stopped riding at " + tick + " " + ride.describe());
            check(course.relative(fast).y > 1.99, "fast rider sank into powder at " + tick + " " + ride.describe());
        }
        check(ride.c().state() == RideState.POWDER, "state on powder: " + ride.c().state());
        check(PowderSnowBlock.canEntityWalkOnPowderSnow(fast), "fast rider not floating");

        Player slow = Course.boarder(helper);
        course.place(slow, 2, 3.5, 0.1);
        Ride crawl = new Ride(slow);
        crawl.tick(RideInput.coast(0));
        check(crawl.mode() == SlideController.Mode.WALK, "slow rider should sink and walk: " + crawl.describe());
        check(!PowderSnowBlock.canEntityWalkOnPowderSnow(slow), "slow rider floats");
        helper.succeed();
    }

    // ---- server side ---------------------------------------------------------------------------------

    public static void rideStateClaimsFollowTheTransitionTable(GameTestHelper helper) {
        Player rider = Course.boarder(helper);
        RideSnapshot glide = RideSnapshot.OFF.withState(RideState.GLIDE).withMode(RideMode.SKIS).withMotion(0.9, 90, 0.5);
        check(RideServer.handleState(rider, glide.pack()), "GLIDE refused");
        RideSnapshot now = Riders.get(rider);
        check(now.state() == RideState.GLIDE, "state " + now.state());
        check(now.mode() == RideMode.BOARD, "client changed the mode to " + now.mode());
        check(Math.abs(now.speed() - glide.speed()) < 1e-9, "speed not taken from the claim");

        int before = RideServer.violations(rider);
        check(!RideServer.handleState(rider, glide.withState(RideState.GRIND).pack()), "GRIND accepted without a rail");
        check(Riders.state(rider) == RideState.GLIDE, "refused claim changed the state");
        check(RideServer.violations(rider) == before + 1, "refused claim not counted");

        StrapController.unstrap(rider, StrapController.UnstrapReason.USER);
        check(!RideServer.handleState(rider, glide.pack()), "claim accepted after unstrapping");
        check(Riders.state(rider) == RideState.OFF, "claim strapped the rider back in");
        helper.succeed();
    }

    public static void wipeoutDamageUsesObservedSpeedAndHelmets(GameTestHelper helper) {
        Course course = new Course(helper);
        course.flat(0, 70, 2, Blocks.SNOW_BLOCK);
        float bare = wipeoutAfterRun(helper, course, false, 1.2);
        float helmet = wipeoutAfterRun(helper, course, true, 1.2);
        float expected = RideServer.wipeoutDamage(1.2, false);
        check(Math.abs(bare - expected) < 0.05, "damage " + bare + ", expected " + expected);
        check(Math.abs(helmet - expected * 0.6f) < 0.05, "helmet damage " + helmet + ", expected " + expected * 0.6f);
        float slow = wipeoutAfterRun(helper, course, false, 0.4);
        check(slow == 0, "a wipeout below wallHitSpeed hurt for " + slow);
        helper.succeed();
    }

    private static float wipeoutAfterRun(GameTestHelper helper, Course course, boolean helmet, double speed) {
        Player rider = Course.boarder(helper);
        if (helmet) {
            rider.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.SKI_HELMET.get()));
            GearProfiles.invalidate(rider);
        }
        course.place(rider, 2, 2.5, 0);
        RideServer.handleState(rider, Riders.get(rider).withState(RideState.GLIDE).pack());
        for (int tick = 0; tick < 6; tick++) {
            rider.setPos(rider.getX(), rider.getY(), rider.getZ() + speed);
            rider.setOnGround(true);
            StrapController.serverTick(rider);
        }
        float health = rider.getHealth();
        RideServer.handleState(rider, Riders.get(rider).withState(RideState.WIPEOUT).pack());
        return health - rider.getHealth();
    }

    public static void serverWearsWaxEdgesAndBoards(GameTestHelper helper) {
        Course course = new Course(helper);
        course.flat(0, 59, 2, Blocks.SNOW_BLOCK).flat(60, 71, 2, Blocks.STONE);
        Player rider = Course.boarder(helper);
        ItemStack board = GearAccess.current().board(rider).orElseThrow();
        GearNbt.setWax(board, 100);
        course.place(rider, 2, 0.5, 0);
        RideServer.handleState(rider, Riders.get(rider).withState(RideState.CARVE).pack());
        for (int tick = 0; tick <= 50; tick++) {
            rider.setPos(rider.getX(), rider.getY(), rider.getZ() + 1.0);
            rider.setOnGround(true);
            StrapController.serverTick(rider);
        }
        board = GearAccess.current().board(rider).orElseThrow();
        check(GearNbt.wax(board) == 50, "wax after 50 blocks of snow: " + GearNbt.wax(board));
        check(GearNbt.edge(board) == 99, "edge after 50 blocks of carving: " + GearNbt.edge(board));
        check(board.getDamageValue() == 0, "snow should not have cost durability yet: " + board.getDamageValue());

        Vec3 scrape = course.at(Course.CENTER_X, 2, 60.5);
        rider.setPos(scrape.x, scrape.y, scrape.z);
        RideServer.reset(rider);
        for (int tick = 0; tick <= 8; tick++) {
            rider.setPos(rider.getX(), rider.getY(), rider.getZ() + 1.0);
            rider.setOnGround(true);
            StrapController.serverTick(rider);
        }
        board = GearAccess.current().board(rider).orElseThrow();
        check(board.getDamageValue() == 2, "8 blocks of scraping should cost 2 durability, cost " + board.getDamageValue());
        check(GearNbt.wax(board) == 50, "scraping used wax");
        helper.succeed();
    }

    /**
     * Validator rule 5 end to end: a client claiming 3 blocks per tick passes vanilla's checks but not
     * ours, and in strict mode is sent back to its last valid position in WIPEOUT.
     */
    public static void validatorCorrectsAnImpossibleRide(GameTestHelper helper) {
        Course course = new Course(helper);
        course.flat(0, 71, 2, Blocks.SNOW_BLOCK);
        String strictBefore = AlpineConfig.MOVEMENT_VALIDATION_STRICT.get();
        ServerPlayer player = null;
        try {
            AlpineConfig.MOVEMENT_VALIDATION_STRICT.set("true");
            player = serverRider(helper, course.at(Course.CENTER_X, 2, 1.5));
            var listener = (ServerGamePacketListenerAccessor) player.connection;
            Vec3 start = player.position();
            int sent = 0;
            while (RideServer.corrections(player) == 0 && sent < 40) {
                player.connection.resetPosition();
                player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.PosRot(
                        start.x, start.y, start.z + 3.0 * (sent + 1), 0, 0, true));
                sent++;
                check(listener.alpineshred$awaitingPosition() == null || RideServer.corrections(player) > 0,
                        "vanilla rejected the move itself at packet " + sent);
                StrapController.serverTick(player);
            }
            check(RideServer.corrections(player) == 1, "no correction after " + sent + " packets at 3 b/t; last: " + RideServer.lastReason(player));
            check(Riders.state(player) == RideState.WIPEOUT, "corrected rider not in WIPEOUT: " + Riders.state(player));
            check(listener.alpineshred$awaitingPosition() != null, "correction did not teleport");
            double backZ = listener.alpineshred$awaitingPosition().z - start.z;
            check(backZ < 3.0 * sent - 6, "corrected only to z+" + backZ + " after " + sent + " packets");
            check(RideServer.lastReason(player).startsWith("speed"), "reason " + RideServer.lastReason(player));
            helper.succeed();
        } finally {
            AlpineConfig.MOVEMENT_VALIDATION_STRICT.set(strictBefore);
            removeServerPlayer(player);
        }
    }

    /**
     * Risk R2, part one: the vanilla move-packet checks of a dedicated server must accept a rider at
     * the speed cap through a pop, a one-block step-up taken at speed, a voxel staircase, and braking
     * (sneaking) off a ledge. See {@link #replayOnServer} for how the client is simulated.
     */
    public static void vanillaAcceptsARideAtTheSpeedCap(GameTestHelper helper) {
        Course course = new Course(helper);
        Block snow = ModBlocks.GROOMED_SNOW.get();
        course.flat(0, 25, 5, snow)                    // in-run; the pop lands here
                .flat(26, 33, 6, snow)                 // one-block step up, at speed
                .flat(34, 36, 5, snow).flat(37, 39, 4, snow) // staircase down
                .flat(40, 59, 3, snow)                 // braking from z 45 on
                .flat(60, 71, 2, snow);                // ledge, taken while braking
        Replay run = replayOnServer(helper, course, 5, true, z -> new RideInput(0, 0, 0, z >= 45, z >= 2.5 && z < 4.5, GrabType.NONE));
        double cap = TuningData.tuning().maxSpeed * run.gearSpeed;
        check(run.maxSpeed >= cap - 1.0e-6, String.format(Locale.ROOT, "never reached the cap %.3f (max %.3f)", cap, run.maxSpeed));
        check(run.popped, "the pop never fired");
        check(run.climbed, "never took the step up");
        helper.succeed();
    }

    /** Risk R2, part two: a kicker launch at speed with a long air phase (floating kick, air budget). */
    public static void vanillaAcceptsKickerAir(GameTestHelper helper) {
        Course course = new Course(helper);
        Block snow = ModBlocks.GROOMED_SNOW.get();
        course.flat(0, 7, 3, snow)
                .column(8, 3.375, snow).column(9, 3.75, snow).column(10, 4.125, snow).column(11, 4.5, snow)
                .flat(12, 71, 3, snow);
        Replay run = replayOnServer(helper, course, 3, false, z -> RideInput.coast(0));
        check(run.launchVy > 0, "the kicker never launched");
        check(run.airTicks >= 14, "only " + run.airTicks + " ticks in the air");
        helper.succeed();
    }

    /** What a replayed run did, for the per-course checks. */
    private static final class Replay {
        double maxSpeed, launchVy, gearSpeed;
        int airTicks;
        boolean popped, climbed;
    }

    /**
     * Rides {@code course} from z = 1.5 at the speed cap with the real controller on a test player
     * (the "client"), and sends each tick's position through the real {@code handleMovePlayer} of a
     * strapped server player, as {@code LocalPlayer.sendPosition} would, followed by the client's
     * {@code ride_action} and {@code ride_state} claims and the server's per-tick ride checks. Fails
     * on any rubber-band (a pending teleport, or the server holding a different position), on a
     * floating kick, on a validator violation, or on a wipeout. With {@code negativeControl}, ends by
     * proving the harness sees rubber-banding at all.
     */
    private static Replay replayOnServer(GameTestHelper helper, Course course, double startY, boolean negativeControl,
                                         java.util.function.DoubleFunction<RideInput> script) {
        ServerPlayer server = null;
        try {
            server = serverRider(helper, course.at(Course.CENTER_X, startY, 1.5));
            var listener = (ServerGamePacketListenerAccessor) server.connection;

            Player client = helper.makeMockSurvivalPlayer();
            Course.equipBoard(client);
            course.place(client, startY, 1.5, 1.45); // just above the cap: the controller holds it there
            Ride ride = new Ride(client);
            StateSendThrottle throttle = new StateSendThrottle();
            Replay run = new Replay();
            run.gearSpeed = GearProfiles.get(client).stats().speed();

            int floating = 0, maxFloating = 0;
            List<String> trace = new ArrayList<>();
            for (int tick = 0; tick < 200 && course.relative(client).z < 70; tick++) {
                RideInput input = script.apply(course.relative(client).z);
                server.setShiftKeyDown(input.sneak()); // what the client's sneak command tells the server
                double yBefore = client.getY();
                ride.tick(input);
                SlideController c = ride.c();
                run.maxSpeed = Math.max(run.maxSpeed, c.speed());
                if (c.state() == RideState.AIR) run.airTicks++;
                if (c.grounded() && client.getY() - yBefore > 0.9) run.climbed = true;
                Vec3 rel = course.relative(client);
                trace.add(String.format(Locale.ROOT, "t%d z%.2f y%.3f %s v%.3f vy%.3f ground %s", tick, rel.z, rel.y,
                        c.state(), c.speed(), c.verticalVelocity(), client.onGround()));

                // The move packet. resetPosition() is what the start of each server tick does.
                server.connection.resetPosition();
                server.connection.handleMovePlayer(new ServerboundMovePlayerPacket.PosRot(
                        client.getX(), client.getY(), client.getZ(), client.getYRot(), client.getXRot(), client.onGround()));
                if (listener.alpineshred$awaitingPosition() != null || server.position().distanceTo(client.position()) > 1.0e-6) {
                    throw new GameTestAssertException("rubber-banded at tick " + tick + ": client " + client.position()
                            + " server " + server.position() + "\n" + String.join("\n", tail(trace)));
                }
                floating = listener.alpineshred$clientIsFloating() ? floating + 1 : 0;
                maxFloating = Math.max(maxFloating, floating);

                // ride_action and ride_state.
                for (SlideController.Event e : c.events()) {
                    if (e instanceof SlideController.Popped pop) {
                        run.popped = true;
                        RideServer.handleAction(server, RideAction.POP, pop.chargeTicks());
                    } else if (e instanceof SlideController.Launched launch) {
                        run.launchVy = launch.velocity();
                        RideServer.handleAction(server, RideAction.LAUNCH, (int) Math.round(launch.velocity() * 1000));
                    } else if (e instanceof SlideController.WipedOut) {
                        throw new GameTestAssertException("wiped out at tick " + tick + "\n" + String.join("\n", tail(trace)));
                    }
                }
                int bits = c.snapshot(RideMode.BOARD, false).pack();
                if (throttle.shouldSend(tick, bits)) RideServer.handleState(server, bits);

                StrapController.serverTick(server); // PLAYER_POST: auto-unstrap checks and the ride validator
                check(Riders.isStrapped(server), "server unstrapped the rider at tick " + tick);
                check(RideServer.violations(server) == 0, "ride validator flagged a legal ride at tick " + tick + ": "
                        + RideServer.lastReason(server) + "\n" + String.join("\n", tail(trace)));
            }
            check(course.relative(client).z >= 70, "rider did not finish the course\n" + String.join("\n", tail(trace)));
            check(maxFloating < 80, "vanilla would kick for floating: " + maxFloating + " ticks");

            if (negativeControl) {
                // The harness must see a rubber-band at all: claim a move through a wall.
                Vec3 rel = course.relative(client);
                course.wall((int) Math.floor(rel.z) + 1, (int) Math.floor(rel.y), (int) Math.floor(rel.y) + 2, Blocks.STONE);
                server.connection.resetPosition();
                server.connection.handleMovePlayer(new ServerboundMovePlayerPacket.PosRot(
                        client.getX(), client.getY(), client.getZ() + 2.5, 0, 0, true));
                check(listener.alpineshred$awaitingPosition() != null,
                        "a move through a wall was accepted: the vanilla checks are not active in this harness");
            }
            return run;
        } finally {
            removeServerPlayer(server);
        }
    }

    // ---- helpers ------------------------------------------------------------------------------------

    /**
     * A logged-in server player on an in-memory connection. Vanilla's
     * {@code makeMockServerPlayerInLevel} gives its connection no channel, which Forge's
     * {@code placeNewPlayer} cannot handle (it installs packet filters on the channel pipeline);
     * an {@link EmbeddedChannel} works on both loaders and simply collects what the server sends.
     */
    private static ServerPlayer newServerPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ServerPlayer player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "alpine-rider"));
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection); // registers and activates the connection on the channel
        connection.setProtocol(ConnectionProtocol.PLAY);
        server.getPlayerList().placeNewPlayer(connection, player);
        return player;
    }

    /** A strapped survival server player at {@code pos}, with every pending teleport answered. */
    private static ServerPlayer serverRider(GameTestHelper helper, Vec3 pos) {
        ServerPlayer player = newServerPlayer(helper);
        player.setGameMode(GameType.SURVIVAL);
        acceptTeleport(player);
        player.connection.teleport(pos.x, pos.y, pos.z, 0, 0);
        acceptTeleport(player);
        player.setOnGround(true);
        Course.equipBoard(player);
        return player;
    }

    private static void acceptTeleport(ServerPlayer player) {
        var listener = (ServerGamePacketListenerAccessor) player.connection;
        if (listener.alpineshred$awaitingPosition() != null) {
            player.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(listener.alpineshred$awaitingTeleport()));
        }
        check(listener.alpineshred$awaitingPosition() == null, "teleport not accepted");
    }

    private static void removeServerPlayer(ServerPlayer player) {
        if (player == null) return;
        player.getServer().getPlayerList().remove(player);
    }

    private static List<String> tail(List<String> trace) {
        return trace.subList(Math.max(0, trace.size() - 12), trace.size());
    }

    private static void standAt(Player player, Vec3 pos) {
        player.setPos(pos.x, pos.y, pos.z);
        player.setOnGround(true);
    }

    private static void expectProfile(String id, BlockState state) {
        SurfaceProfile profile = SurfaceResolver.match(state);
        check(profile != null && profile.id().equals(id), state + " matched " + (profile == null ? "nothing" : profile.id()) + ", expected " + id);
    }

    private static void expectSurface(String id, SurfaceProfile profile) {
        check(profile != null && profile.id().equals(id), "surface " + (profile == null ? "none" : profile.id()) + ", expected " + id);
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    /** A rider run by the real {@link RideRunner} with the rider's own gear and default server settings. */
    private static final class Ride {
        private final RideRunner runner;
        private final RideContext ctx;
        private SlideController.Mode mode = SlideController.Mode.WALK;

        Ride(Player player) {
            runner = new RideRunner(player);
            ctx = RideRunner.context(GearProfiles.get(player), SyncCodec.Settings.DEFAULTS, SlideModel.SteerMode.RIDE);
        }

        void tick(RideInput input) {
            mode = runner.tick(input, ctx) ? SlideController.Mode.RIDE : SlideController.Mode.WALK;
        }

        SlideController c() {
            return runner.controller();
        }

        SlideController.Mode mode() {
            return mode;
        }

        String describe() {
            SlideController c = c();
            Player p = runner.player();
            return String.format(Locale.ROOT, "[%s %s speed %.3f vy %.3f pos %.2f %.2f %.2f ground %s surface %s]",
                    mode, c.state(), c.speed(), c.verticalVelocity(), p.getX(), p.getY(), p.getZ(), p.onGround(),
                    c.surface() == null ? "none" : c.surface().id());
        }
    }
}
