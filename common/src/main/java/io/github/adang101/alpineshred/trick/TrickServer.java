package io.github.adang101.alpineshred.trick;

import io.github.adang101.alpineshred.config.AlpineConfig;
import io.github.adang101.alpineshred.gear.GearKind;
import io.github.adang101.alpineshred.gear.GearProfile;
import io.github.adang101.alpineshred.gear.GearProfiles;
import io.github.adang101.alpineshred.movement.MovementTuning;
import io.github.adang101.alpineshred.movement.PlayerBody;
import io.github.adang101.alpineshred.movement.RideServer;
import io.github.adang101.alpineshred.movement.RideState;
import io.github.adang101.alpineshred.movement.Riders;
import io.github.adang101.alpineshred.movement.SlideModel;
import io.github.adang101.alpineshred.movement.TuningData;
import io.github.adang101.alpineshred.network.ModNetwork;
import io.github.adang101.alpineshred.park.V3;
import io.github.adang101.alpineshred.park.block.GrindPaths;
import io.github.adang101.alpineshred.registry.ModStats;
import io.github.adang101.alpineshred.registry.ModTags;
import io.github.adang101.alpineshred.trick.advancement.ModTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The server's side of tricks (design doc section 8). It watches each rider's accepted states to
 * know, independently of the client, how long every air session and every rail or box lasted and
 * how high the air went; checks the client's {@code air_report} and {@code grind_report} against
 * that ({@link TrickValidator}); scores what passes ({@link TrickScorer}) into the rider's
 * {@link Combo}; and banks the combo when it runs out. Every landing, grind entry, bank and bail
 * goes back as a {@code trick_result} (to the rider, or as {@code trick.broadcast} says), fires
 * {@link TrickEvents#LANDED} and the advancement triggers, and adds to the statistics.
 */
public final class TrickServer {
    /** How long a finished air session or grind waits for its report, in ticks. */
    static final int REPORT_WINDOW_TICKS = 40;

    private static final class Rider {
        @Nullable
        Combo combo;
        // the air session in progress, as the server sees it
        boolean inAir;
        int airTicks;
        double airMaxY;
        double prevX, prevY, prevZ;
        double lastDy, lastHorizontal;
        // the last finished one, waiting for its report
        int doneAirTicks = -1;
        double doneApex, doneDescent, doneLandX, doneLandY, doneLandZ;
        int doneAirAge;
        // rails and boxes
        boolean onFeature;
        int featureTicks;
        int doneFeatureTicks = -1;
        int doneFeatureAge;
        @Nullable
        TrickResult lastResult;
        int lastBanked;
    }

    private static final Map<ServerPlayer, Rider> RIDERS = new WeakHashMap<>();

    private TrickServer() {
    }

    private static synchronized Rider rider(ServerPlayer player) {
        return RIDERS.computeIfAbsent(player, p -> new Rider());
    }

    // ---- watching -------------------------------------------------------------------------------------

    /** Once per server tick for a strapped rider, after the ride checks. */
    public static void tick(ServerPlayer player, RideState state) {
        Rider r = rider(player);
        double x = player.getX(), y = player.getY(), z = player.getZ();
        if (state == RideState.AIR) {
            if (!r.inAir) {
                r.inAir = true;
                r.airTicks = 0;
                r.airMaxY = y;
            }
            r.airTicks++;
            r.airMaxY = Math.max(r.airMaxY, y);
            r.lastDy = y - r.prevY;
            r.lastHorizontal = Math.hypot(x - r.prevX, z - r.prevZ);
        } else if (r.inAir) {
            r.inAir = false;
            r.doneAirTicks = r.airTicks;
            r.doneApex = Math.max(0, r.airMaxY - y);
            r.doneDescent = Math.toDegrees(Math.atan2(Math.max(0, -r.lastDy), Math.max(1.0e-6, r.lastHorizontal)));
            r.doneLandX = x;
            r.doneLandY = y;
            r.doneLandZ = z;
            r.doneAirAge = 0;
        }
        if (r.doneAirTicks >= 0 && ++r.doneAirAge > REPORT_WINDOW_TICKS) r.doneAirTicks = -1;

        boolean feature = state == RideState.GRIND || state == RideState.SLIDE;
        if (feature) {
            if (!r.onFeature) {
                r.onFeature = true;
                r.featureTicks = 0;
            }
            r.featureTicks++;
        } else if (r.onFeature) {
            r.onFeature = false;
            r.doneFeatureTicks = r.featureTicks;
            r.doneFeatureAge = 0;
        }
        if (r.doneFeatureTicks >= 0 && ++r.doneFeatureAge > REPORT_WINDOW_TICKS) r.doneFeatureTicks = -1;

        if (r.combo != null) {
            if (state == RideState.WIPEOUT) {
                lose(player, r);
            } else if (state.isGroundRiding() && r.combo.tickGround(Riders.get(player).speed())) {
                bank(player, r);
            }
        }
        r.prevX = x;
        r.prevY = y;
        r.prevZ = z;
    }

    /** Unstrapped, logged out, or riding switched off: the combo in progress banks. */
    public static void end(ServerPlayer player) {
        Rider r = rider(player);
        if (r.combo != null) bank(player, r);
        r.inAir = r.onFeature = false;
        r.doneAirTicks = r.doneFeatureTicks = -1;
    }

    // ---- reports ----------------------------------------------------------------------------------------

    /** {@code c2s/air_report}. */
    public static void handleAir(ServerPlayer player, AirReport report) {
        if (!AlpineConfig.TRICK_ENABLED.on() || !Riders.isStrapped(player)) return;
        Rider r = rider(player);
        int observedTicks;
        double observedApex, descent, landY;
        if (r.doneAirTicks >= 0) {
            observedTicks = r.doneAirTicks;
            observedApex = r.doneApex;
            descent = r.doneDescent;
            landY = r.doneLandY;
        } else if (r.inAir) {
            // The report beat the state change here: judge it on the air so far.
            observedTicks = r.airTicks;
            observedApex = Math.max(0, r.airMaxY - player.getY());
            descent = Math.toDegrees(Math.atan2(Math.max(0, -r.lastDy), Math.max(1.0e-6, r.lastHorizontal)));
            landY = player.getY();
        } else {
            RideServer.flagReport(player, "air report with no air session");
            return;
        }
        GearProfile gear = GearProfiles.get(player);
        MovementTuning t = TuningData.tuning();
        double spinMax = gear.kind() == GearKind.SKIS ? t.airSpinMaxSkis : t.airSpinMaxBoard;
        String reason = TrickValidator.check(report, observedTicks, observedApex, spinMax, t.airFlipMax);
        if (reason != null) {
            RideServer.flagReport(player, reason);
            return;
        }
        r.doneAirTicks = -1;
        r.inAir = false;
        ModStats.award(player, ModStats.AIR_TIME, report.airTicks());

        LandingQuality quality;
        if (report.endsOnRail()) {
            quality = LandingQuality.CLEAN;
        } else {
            // Rule 5: the slope is the world's, not the client's.
            double slopeError = Math.abs(descent - downhill(player, landY));
            quality = LandingQuality.evaluate(report.headingError(), report.flipResidual(), slopeError, gear.stats().landingForgiveness());
        }
        if (quality == LandingQuality.BAIL) {
            lose(player, r);
            return;
        }
        List<TrickPart> parts = TrickScorer.air(report, gear.mitts());
        if (parts.isEmpty()) {
            if (r.combo != null) r.combo.tickGroundReset();
            return;
        }
        TrickResult result = land(player, r, parts, quality, report.switchLanding() && !report.endsOnRail());
        Set<GrabType> grabs = EnumSet.noneOf(GrabType.class);
        for (GrabType g : GrabType.values()) {
            if (g != GrabType.NONE && report.grab(g) >= TrickScorer.MIN_GRAB_TICKS) grabs.add(g);
        }
        ModTriggers.TRICK_LANDED.trigger(player, result.points(), TrickAngles.halfTurns(report.yawAccum()) * 180,
                TrickAngles.flips(report.pitchAccum()), grabs, quality);
    }

    /** {@code c2s/grind_report}. */
    public static void handleGrind(ServerPlayer player, GrindReport report) {
        if (!AlpineConfig.TRICK_ENABLED.on() || !Riders.isStrapped(player)) return;
        Rider r = rider(player);
        int observed = r.doneFeatureTicks >= 0 ? r.doneFeatureTicks : r.onFeature ? r.featureTicks : -1;
        if (observed < 0) {
            RideServer.flagReport(player, "grind report with no grind");
            return;
        }
        BlockPos pos = new BlockPos(report.x(), report.y(), report.z());
        if (player.blockPosition().distManhattan(pos) > 48 || !featureAt(player, pos, report.box())) {
            RideServer.flagReport(player, "grind report: no " + (report.box() ? "box" : "rail") + " at " + pos.toShortString());
            return;
        }
        String reason = TrickValidator.check(report, observed, RideServer.limits(player).speedCap());
        if (reason != null) {
            RideServer.flagReport(player, reason);
            return;
        }
        r.doneFeatureTicks = -1;
        ModStats.award(player, ModStats.GRIND_DISTANCE, (int) Math.round(report.lengthBlocks() * 100));
        List<TrickPart> parts = TrickScorer.grind(report);
        if (parts.isEmpty()) return;
        land(player, r, parts, TrickScorer.grindEntry(report.variant(), report.entryAxis()), false);
        ModTriggers.GRIND_COMPLETED.trigger(player, report.lengthBlocks(), report.box());
    }

    private static boolean featureAt(ServerPlayer player, BlockPos pos, boolean box) {
        var level = player.level();
        if (box) {
            for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
                if (level.getBlockState(p).is(ModTags.Blocks.SLIDE_BOXES)) return true;
            }
            return false;
        }
        return !GrindPaths.around(level, new V3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)).isEmpty();
    }

    /** The slope under the landing point along the rider's heading, degrees downhill. */
    private static double downhill(ServerPlayer player, double landY) {
        PlayerBody body = new PlayerBody(player);
        double yaw = Riders.get(player).heading();
        double dx = SlideModel.dirX(yaw) * 0.5, dz = SlideModel.dirZ(yaw) * 0.5;
        double behind = body.groundTop(player.getX() - dx, player.getZ() - dz, landY + 1.0, landY - 2.0);
        double ahead = body.groundTop(player.getX() + dx, player.getZ() + dz, landY + 1.0, landY - 2.0);
        if (Double.isNaN(behind) || Double.isNaN(ahead)) return 0;
        return Math.toDegrees(Math.atan2(behind - ahead, 1.0));
    }

    // ---- the combo --------------------------------------------------------------------------------------

    private static TrickResult land(ServerPlayer player, Rider r, List<TrickPart> parts, LandingQuality quality, boolean switchLanding) {
        if (r.combo == null) r.combo = new Combo();
        double added = r.combo.land(parts, quality, switchLanding);
        Combo.Entry entry = r.combo.entries().get(r.combo.entries().size() - 1);
        List<TrickResult.Line> lines = new ArrayList<>();
        for (TrickPart p : entry.parts()) lines.add(new TrickResult.Line(p.id(), (int) Math.round(p.points())));
        TrickResult result = new TrickResult((int) Math.round(added), lines, quality, switchLanding, r.combo.multiplier(),
                r.combo.total(cap()), r.combo.entries().size(), false, false);
        ModStats.award(player, ModStats.TRICKS_LANDED, 1);
        send(player, r, result);
        return result;
    }

    private static void bank(ServerPlayer player, Rider r) {
        Combo combo = r.combo;
        r.combo = null;
        if (combo == null || combo.isEmpty()) return;
        int total = combo.total(cap());
        r.lastBanked = total;
        ModStats.award(player, ModStats.TRICK_SCORE, total);
        ModStats.best(player, ModStats.BEST_COMBO, total);
        send(player, r, TrickResult.bank(total, combo.multiplier(), combo.entries().size()));
    }

    /** A bail (or any wipeout) loses the combo in progress; banked combos stay (section 8). */
    private static void lose(ServerPlayer player, Rider r) {
        r.combo = null;
        send(player, r, TrickResult.bailed());
    }

    private static int cap() {
        return AlpineConfig.TRICK_MAX_COMBO_SCORE.value();
    }

    private static void send(ServerPlayer player, Rider r, TrickResult result) {
        r.lastResult = result;
        TrickEvents.LANDED.invoker().landed(player, result);
        switch (AlpineConfig.TRICK_BROADCAST.get()) {
            case "none" -> {
            }
            case "nearby" -> {
                for (ServerPlayer other : player.serverLevel().players()) {
                    if (other == player || other.distanceToSqr(player) <= 64 * 64) ModNetwork.sendTrickResult(other, player, result);
                }
            }
            case "all" -> {
                for (ServerPlayer other : player.server.getPlayerList().getPlayers()) ModNetwork.sendTrickResult(other, player, result);
            }
            default -> ModNetwork.sendTrickResult(player, player, result);
        }
    }

    // ---- read-outs (tests, the smoke test, /alpineshred stats) --------------------------------------

    @Nullable
    public static synchronized TrickResult lastResult(ServerPlayer player) {
        Rider r = RIDERS.get(player);
        return r == null ? null : r.lastResult;
    }

    /** The last banked combo's total. */
    public static synchronized int lastBanked(ServerPlayer player) {
        Rider r = RIDERS.get(player);
        return r == null ? 0 : r.lastBanked;
    }

    /** The combo in progress, or null. */
    @Nullable
    public static synchronized Combo combo(ServerPlayer player) {
        Rider r = RIDERS.get(player);
        return r == null ? null : r.combo;
    }
}
