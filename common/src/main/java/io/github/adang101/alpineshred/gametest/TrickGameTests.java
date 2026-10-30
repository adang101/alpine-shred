package io.github.adang101.alpineshred.gametest;

import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.movement.RideServer;
import io.github.adang101.alpineshred.movement.RideState;
import io.github.adang101.alpineshred.movement.Riders;
import io.github.adang101.alpineshred.movement.StrapController;
import io.github.adang101.alpineshred.registry.ModBlocks;
import io.github.adang101.alpineshred.registry.ModStats;
import io.github.adang101.alpineshred.trick.AirReport;
import io.github.adang101.alpineshred.trick.Combo;
import io.github.adang101.alpineshred.trick.GrabType;
import io.github.adang101.alpineshred.trick.GrindReport;
import io.github.adang101.alpineshred.trick.LandingQuality;
import io.github.adang101.alpineshred.trick.TrickAngles;
import io.github.adang101.alpineshred.trick.TrickResult;
import io.github.adang101.alpineshred.trick.TrickScorer;
import io.github.adang101.alpineshred.trick.TrickServer;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import static io.github.adang101.alpineshred.gametest.RideGameTests.check;

/**
 * Phase 5 game tests (design doc section 8; exit gate: forged reports are rejected, and the
 * server's scores are the ones the client's popup shows). A logged-in server player claims ride
 * states and reports through the real server code, tick by tick, so the server's own view of each
 * air session is what the reports are checked against.
 */
public final class TrickGameTests {
    private TrickGameTests() {
    }

    /** A strapped server rider on the floor, its claimed speed 0.5 (enough to keep a combo going). */
    private static ServerPlayer rider(GameTestHelper helper, Course course) {
        course.flat(0, 20, 2, Blocks.SNOW_BLOCK);
        ServerPlayer player = RideGameTests.serverRider(helper, course.at(Course.CENTER_X, 2, 2.5));
        check(RideServer.handleState(player, Riders.get(player).withState(RideState.GLIDE).withMotion(0.5, 0, 0).pack()), "GLIDE refused");
        StrapController.serverTick(player);
        return player;
    }

    /** {@code ticks} of air as the server sees it (claimed AIR, not standing), then a landing. */
    private static void airSession(ServerPlayer player, int ticks) {
        takeOff(player);
        clientTicks(player, ticks);
        land(player);
    }

    private static void takeOff(ServerPlayer player) {
        player.setOnGround(false);
        check(RideServer.handleState(player, Riders.get(player).withState(RideState.AIR).pack()), "AIR refused");
    }

    private static void land(ServerPlayer player) {
        player.setOnGround(true);
        check(RideServer.handleState(player, Riders.get(player).withState(RideState.GLIDE).pack()), "landing GLIDE refused");
        clientTicks(player, 1);
    }

    /**
     * {@code n} ticks of a client: a move packet each (the rider clock trick sessions are timed on
     * counts them), then the server's tick.
     */
    private static void clientTicks(ServerPlayer player, int n) {
        for (int i = 0; i < n; i++) {
            player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.PosRot(player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot(), player.onGround()));
            StrapController.serverTick(player);
        }
    }

    private static AirReport spin(double yaw, int ticks, double headingError) {
        int[] grabs = new int[GrabType.values().length];
        return new AirReport(yaw, 0, grabs, 0, ticks, 0, headingError, 0, false, false, false);
    }

    /**
     * Exit gate, part one: a report claiming more than the air allowed is rejected and counted; the
     * honest one after it scores exactly what the catalog says.
     */
    public static void forgedAirReportsAreRejected(GameTestHelper helper) {
        ServerPlayer player = null;
        // Three forged reports in a row are enough for a correction; on a strict (dedicated) server
        // that would wipe the rider out and the honest report after them would rightly be ignored.
        String strictBefore = io.github.adang101.alpineshred.config.AlpineConfig.MOVEMENT_VALIDATION_STRICT.get();
        try {
            io.github.adang101.alpineshred.config.AlpineConfig.MOVEMENT_VALIDATION_STRICT.set("false");
            player = rider(helper, new Course(helper));
            airSession(player, 20);
            int before = RideServer.violations(player);
            TrickServer.handleAir(player, spin(1080, 20, 0));          // rule 1: 1080 in 20 ticks
            check(TrickServer.lastResult(player) == null, "a forged 1080 scored: " + TrickServer.lastResult(player));
            check(RideServer.violations(player) == before + 1, "the forged report was not counted");
            TrickServer.handleAir(player, spin(360, 60, 0));           // more air than the server saw
            check(TrickServer.lastResult(player) == null, "60 ticks of air claimed after 20 scored");
            TrickServer.handleAir(player, spin(270, 20, 5));           // rule 4: a clean landing after a 270
            check(TrickServer.lastResult(player) == null, "a 270 claimed as clean scored");
            check(RideServer.violations(player) == before + 3, "violations " + RideServer.violations(player));

            TrickServer.handleAir(player, spin(360, 20, 0));
            TrickResult result = TrickServer.lastResult(player);
            check(result != null && !result.bail() && !result.banked(), "the honest 360 did not score: " + result);
            // Flat ground, no rise: descent and slope agree, so PERFECT; the air time adds its points.
            double expected = (TrickScorer.spinPoints(2) + TrickScorer.airTimePoints(20)) * LandingQuality.PERFECT.factor;
            check(result.quality() == LandingQuality.PERFECT, "quality " + result.quality());
            check(result.points() == (int) Math.round(expected), "points " + result.points() + ", expected " + expected);
            check(result.comboTotal() == (int) Math.round(expected), "combo total " + result.comboTotal());
            check(result.lines().stream().anyMatch(l -> l.id().equals("spin_360") && l.points() == 300), "lines " + result.lines());
            int afterHonest = RideServer.violations(player);
            TrickServer.handleAir(player, spin(360, 20, 0));
            check(TrickServer.lastResult(player) == result && RideServer.violations(player) == afterHonest + 1,
                    "the same air was scored twice");
            helper.succeed();
        } finally {
            io.github.adang101.alpineshred.config.AlpineConfig.MOVEMENT_VALIDATION_STRICT.set(strictBefore);
            RideGameTests.removeServerPlayer(player);
        }
    }

    /** Combos: a second landing chains with the multiplier, the combo banks after 60 ground ticks into the stats. */
    public static void combosChainBankAndCount(GameTestHelper helper) {
        ServerPlayer player = null;
        try {
            player = rider(helper, new Course(helper));
            int scoreBefore = ModStats.value(player, ModStats.TRICK_SCORE);
            airSession(player, 20);
            TrickServer.handleAir(player, spin(360, 20, 0));
            for (int i = 0; i < 20; i++) StrapController.serverTick(player);
            airSession(player, 24);
            int[] grabs = new int[GrabType.values().length];
            grabs[GrabType.INDY.ordinal()] = 20;
            TrickServer.handleAir(player, new AirReport(0, 0, grabs, 0, 24, 0, 0, 0, false, false, false));
            Combo combo = TrickServer.combo(player);
            check(combo != null && combo.entries().size() == 2, "the second landing did not chain: " + combo);
            double base = (TrickScorer.spinPoints(2) + TrickScorer.airTimePoints(20)) * 1.25
                    + (TrickScorer.grabPoints(GrabType.INDY, 20, false) + TrickScorer.airTimePoints(24)) * 1.25;
            int expected = (int) Math.round(base * (1 + 0.2) * (1 + 0.25)); // spin + grab categories; one chained landing
            check(TrickServer.lastResult(player).comboTotal() == expected,
                    "running total " + TrickServer.lastResult(player).comboTotal() + ", expected " + expected);
            for (int i = 0; i <= Combo.WINDOW_TICKS; i++) StrapController.serverTick(player);
            TrickResult bank = TrickServer.lastResult(player);
            check(bank.banked() && bank.comboTotal() == expected, "bank " + bank);
            check(TrickServer.combo(player) == null, "the combo is still open after banking");
            check(ModStats.value(player, ModStats.TRICK_SCORE) == scoreBefore + expected, "trick_score stat");
            check(ModStats.value(player, ModStats.BEST_COMBO) >= expected, "best_combo stat");
            check(ModStats.value(player, ModStats.TRICKS_LANDED) >= 2, "tricks_landed stat");
            helper.succeed();
        } finally {
            RideGameTests.removeServerPlayer(player);
        }
    }

    /** A bail (heading 70 degrees off) loses the combo in progress; one banked earlier stays. */
    public static void aBailLosesTheCombo(GameTestHelper helper) {
        ServerPlayer player = null;
        try {
            player = rider(helper, new Course(helper));
            airSession(player, 20);
            TrickServer.handleAir(player, spin(360, 20, 0));
            check(TrickServer.combo(player) != null, "no combo");
            airSession(player, 20);
            TrickServer.handleAir(player, spin(250, 20, TrickAngles.headingError(0, 250)));
            check(TrickServer.combo(player) == null, "the bail kept the combo");
            check(TrickServer.lastResult(player).bail(), "no bail result: " + TrickServer.lastResult(player));
            helper.succeed();
        } finally {
            RideGameTests.removeServerPlayer(player);
        }
    }

    /** Grind reports need a rail where they say, and no more time on it than the server saw. */
    public static void grindReportsNeedARail(GameTestHelper helper) {
        ServerPlayer player = null;
        try {
            Course course = new Course(helper);
            player = rider(helper, course);
            helper.setBlock(new BlockPos(4, 2, 8), ModBlocks.PIPE_RAIL.get());
            BlockPos rail = helper.absolutePos(new BlockPos(4, 2, 8));
            BlockPos nothing = helper.absolutePos(new BlockPos(4, 2, 16));
            Vec3 on = course.at(Course.CENTER_X, 2.5, 8.5);
            player.setPos(on.x, on.y, on.z);
            check(RideServer.handleState(player, Riders.get(player).withState(RideState.GRIND).pack()), "GRIND refused on a rail");
            clientTicks(player, 20);
            check(RideServer.handleState(player, Riders.get(player).withState(RideState.AIR).pack()), "AIR refused");
            clientTicks(player, 1);
            int before = RideServer.violations(player);
            TrickServer.handleGrind(player, new GrindReport(nothing.getX(), nothing.getY(), nothing.getZ(), false, 0, 5, false, 6, 20, 0));
            check(TrickServer.lastResult(player) == null, "a grind with no rail scored");
            TrickServer.handleGrind(player, new GrindReport(rail.getX(), rail.getY(), rail.getZ(), false, 0, 5, false, 6, 200, 0));
            check(TrickServer.lastResult(player) == null, "200 ticks claimed on a 20 tick grind");
            check(RideServer.violations(player) == before + 2, "violations " + RideServer.violations(player));
            TrickServer.handleGrind(player, new GrindReport(rail.getX(), rail.getY(), rail.getZ(), false, 0, 5, false, 6, 20, 0));
            TrickResult result = TrickServer.lastResult(player);
            check(result != null && result.quality() == LandingQuality.PERFECT, "50-50 entered 5 degrees off: " + result);
            check(result.points() == (int) Math.round(40 * 1.0 * 1.25), "points " + result.points());
            helper.succeed();
        } finally {
            RideGameTests.removeServerPlayer(player);
        }
    }

    /**
     * The order a real client sends things in: the report from the landing tick comes before the
     * landing's state claim, and a one-tick hop just before the air does not stand in for it.
     */
    public static void aReportBeforeItsLandingIsJudgedOnThatAir(GameTestHelper helper) {
        ServerPlayer player = null;
        try {
            player = rider(helper, new Course(helper));
            airSession(player, 1);                                     // a bump: one tick of air, no report
            clientTicks(player, 3);
            takeOff(player);
            clientTicks(player, 12);
            int before = RideServer.violations(player);
            TrickServer.handleAir(player, spin(180, 12, 0));           // before the landing claim
            land(player);
            TrickResult result = TrickServer.lastResult(player);
            check(RideServer.violations(player) == before, "an honest 180 was flagged: " + RideServer.violations(player));
            check(result != null && result.lines().stream().anyMatch(l -> l.id().equals("spin_180")), "the 180 did not score: " + result);
            helper.succeed();
        } finally {
            RideGameTests.removeServerPlayer(player);
        }
    }

    /** Section 18: the first five trick advancements fire from real landings and grinds. */
    public static void trickAdvancementsFire(GameTestHelper helper) {
        ServerPlayer player = null;
        try {
            player = rider(helper, new Course(helper));
            airSession(player, 20);
            TrickServer.handleAir(player, spin(360, 20, 0));
            check(done(player, "first_air"), "first_air not awarded");
            check(done(player, "spin_cycle"), "spin_cycle not awarded");
            helper.succeed();
        } finally {
            RideGameTests.removeServerPlayer(player);
        }
    }

    private static boolean done(ServerPlayer player, String id) {
        Advancement advancement = player.server.getAdvancements().getAdvancement(AlpineShred.id(id));
        check(advancement != null, "no advancement " + id);
        return player.getAdvancements().getOrStartProgress(advancement).isDone();
    }
}
