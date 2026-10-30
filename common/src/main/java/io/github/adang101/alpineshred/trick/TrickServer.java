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
import io.github.adang101.alpineshred.park.PipeArc;
import io.github.adang101.alpineshred.park.PipeSection;
import io.github.adang101.alpineshred.park.V3;
import io.github.adang101.alpineshred.park.block.LevelParkView;
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
        // the air session in progress, as the server sees it: its start on the rider clock (the
        // client's own ticks, counted in move packets), its highest point, the last movement in it
        boolean inAir;
        long airStart;
        double airMaxY;
        double prevX, prevY, prevZ;
        long prevClock;
        boolean hasPrev;
        double lastDx, lastDy, lastDz;
        // the last finished one, waiting for its report
        int doneAirTicks = -1;
        double doneApex, doneDx, doneDy, doneDz, doneX, doneY, doneZ;
        int doneAirAge;
        // rails and boxes
        boolean onFeature;
        long featureStart;
        int doneFeatureTicks = -1;
        int doneFeatureAge;
        @Nullable
        TrickResult lastResult;
        int lastBanked;
    }

    /** What the server saw of an air session, for a report to be checked against. */
    private record Seen(int ticks, double apex, double dx, double dy, double dz, double x, double y, double z) {
    }

    private static final Map<ServerPlayer, Rider> RIDERS = new WeakHashMap<>();

    private TrickServer() {
    }

    private static synchronized Rider rider(ServerPlayer player) {
        return RIDERS.computeIfAbsent(player, p -> new Rider());
    }

    // ---- watching -------------------------------------------------------------------------------------

    /**
     * A ride state claim the server accepted ({@code c2s/ride_state}). Sessions start and end here,
     * in packet order, stamped with the rider clock: a report always arrives before the claim of
     * the landing it reports, and several claims can arrive between two server ticks.
     */
    public static void claimed(ServerPlayer player, RideState from, RideState to) {
        if (from == to) return;
        Rider r = rider(player);
        long now = RideServer.packetClock(player);
        observe(r, player, now);
        follow(player, r, to, now);
    }

    /** Once per server tick for a strapped rider, after the ride checks. */
    public static void tick(ServerPlayer player, RideState state) {
        Rider r = rider(player);
        long now = RideServer.packetClock(player);
        observe(r, player, now);
        // States the claims did not carry: a correction's wipeout, an unstrap.
        follow(player, r, state, now);
        if (r.doneAirTicks >= 0 && ++r.doneAirAge > REPORT_WINDOW_TICKS) r.doneAirTicks = -1;
        if (r.doneFeatureTicks >= 0 && ++r.doneFeatureAge > REPORT_WINDOW_TICKS) r.doneFeatureTicks = -1;

        if (r.combo != null) {
            if (state == RideState.WIPEOUT) {
                lose(player, r);
            } else if (state.isGroundRiding() && r.combo.tickGround(Riders.get(player).speed())) {
                bank(player, r);
            }
        }
    }

    private static void follow(ServerPlayer player, Rider r, RideState state, long now) {
        if (state == RideState.AIR && !r.inAir) {
            r.inAir = true;
            r.airStart = now;
            r.airMaxY = player.getY();
            r.doneAirTicks = -1;
        } else if (state != RideState.AIR && r.inAir) {
            r.inAir = false;
            r.doneAirTicks = (int) Math.max(0, now - r.airStart);
            r.doneApex = Math.max(0, r.airMaxY - player.getY());
            r.doneDx = r.lastDx;
            r.doneDy = r.lastDy;
            r.doneDz = r.lastDz;
            r.doneX = player.getX();
            r.doneY = player.getY();
            r.doneZ = player.getZ();
            r.doneAirAge = 0;
            // Any landing keeps a combo going: the ground clock starts again (section 8).
            if (r.combo != null && state != RideState.WIPEOUT) r.combo.tickGroundReset();
        }
        boolean feature = state == RideState.GRIND || state == RideState.SLIDE;
        if (feature && !r.onFeature) {
            r.onFeature = true;
            r.featureStart = now;
            r.doneFeatureTicks = -1;
        } else if (!feature && r.onFeature) {
            r.onFeature = false;
            r.doneFeatureTicks = (int) Math.max(0, now - r.featureStart);
            r.doneFeatureAge = 0;
        }
    }

    /** The rider's movement since the last look, per rider tick, and the air's highest point. */
    private static void observe(Rider r, ServerPlayer player, long now) {
        double x = player.getX(), y = player.getY(), z = player.getZ();
        if (r.hasPrev && now > r.prevClock) {
            double dx = x - r.prevX, dy = y - r.prevY, dz = z - r.prevZ;
            if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > 1.0e-6) {
                double n = now - r.prevClock;
                r.lastDx = dx / n;
                r.lastDy = dy / n;
                r.lastDz = dz / n;
            }
        }
        if (!r.hasPrev || now > r.prevClock || Math.abs(x - r.prevX) + Math.abs(y - r.prevY) + Math.abs(z - r.prevZ) > 1.0e-6) {
            r.prevX = x;
            r.prevY = y;
            r.prevZ = z;
            r.prevClock = now;
            r.hasPrev = true;
        }
        if (r.inAir) r.airMaxY = Math.max(r.airMaxY, y);
    }

    /** Unstrapped, logged out, or riding switched off: the combo in progress banks. */
    public static void end(ServerPlayer player) {
        Rider r = rider(player);
        if (r.combo != null) bank(player, r);
        r.inAir = r.onFeature = false;
        r.doneAirTicks = r.doneFeatureTicks = -1;
        r.hasPrev = false;
    }

    // ---- reports ----------------------------------------------------------------------------------------

    /** {@code c2s/air_report}. */
    public static void handleAir(ServerPlayer player, AirReport report) {
        if (!AlpineConfig.TRICK_ENABLED.on() || !Riders.isStrapped(player)) return;
        if (Riders.state(player) == RideState.WIPEOUT) return; // a wipeout already ended the session
        Rider r = rider(player);
        long now = RideServer.packetClock(player);
        observe(r, player, now);
        Seen seen;
        if (r.inAir) {
            // The usual case: the report comes just before the landing's state claim, from the tick
            // before the landing, so the landing tick's own drop is allowed on the apex.
            seen = new Seen((int) Math.max(0, now - r.airStart), Math.max(0, r.airMaxY - player.getY()) + Math.max(0, -r.lastDy),
                    r.lastDx, r.lastDy, r.lastDz, player.getX(), player.getY() + Math.min(0, r.lastDy), player.getZ());
        } else if (r.doneAirTicks >= 0) {
            seen = new Seen(r.doneAirTicks, r.doneApex, r.doneDx, r.doneDy, r.doneDz, r.doneX, r.doneY, r.doneZ);
        } else {
            RideServer.flagReport(player, "air report with no air session");
            return;
        }
        GearProfile gear = GearProfiles.get(player);
        MovementTuning t = TuningData.tuning();
        double spinMax = gear.kind() == GearKind.SKIS ? t.airSpinMaxSkis : t.airSpinMaxBoard;
        String reason = TrickValidator.check(report, seen.ticks(), seen.apex(), spinMax, t.airFlipMax);
        if (reason != null) {
            RideServer.flagReport(player, reason);
            return;
        }
        // The session is reported: what follows it (when the landing's claim has not come yet, a
        // second air straight after) is a new one.
        if (r.inAir) {
            r.airStart = now;
            r.airMaxY = player.getY();
        } else {
            r.doneAirTicks = -1;
        }
        ModStats.award(player, ModStats.AIR_TIME, report.airTicks());

        LandingQuality quality;
        if (report.endsOnRail()) {
            quality = LandingQuality.CLEAN;
        } else {
            // Rule 5: the slope is the world's, not the client's.
            quality = LandingQuality.evaluate(report.headingError(), report.flipResidual(), slopeError(player, seen),
                    gear.stats().landingForgiveness());
        }
        if (quality == LandingQuality.BAIL) {
            lose(player, r);
            return;
        }
        List<TrickPart> parts = TrickScorer.air(report, gear.mitts());
        if (parts.isEmpty()) return;
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
        long now = RideServer.packetClock(player);
        int observed = r.onFeature ? (int) Math.max(0, now - r.featureStart) : r.doneFeatureTicks;
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
        // Reported: straight off a box onto a rail, the server's one feature session goes on as the next.
        if (r.onFeature) r.featureStart = now;
        else r.doneFeatureTicks = -1;
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

    /**
     * Degrees between the rider's last movement in the air and the surface under the landing point
     * (section 8, rule 5): a quarter pipe's arc there, or the ground along the way of travel.
     */
    private static double slopeError(ServerPlayer player, Seen seen) {
        V3 feet = new V3(seen.x(), seen.y(), seen.z());
        PipeSection pipe = new LevelParkView(player.level()).pipeNear(feet);
        if (pipe != null && !Double.isNaN(pipe.surfaceUnder(feet)) && pipe.coversAcross(pipe.across(feet), PipeSection.HALF_WIDTH)) {
            double theta = PipeArc.theta(pipe.radius(), pipe.u(feet));
            double along = seen.dx() * pipe.facing().dx + seen.dz() * pipe.facing().dz;
            return TrickAngles.pipeSlopeError(along, seen.dy(), theta);
        }
        double horizontal = Math.hypot(seen.dx(), seen.dz());
        double descent = Math.toDegrees(Math.atan2(Math.max(0, -seen.dy()), Math.max(1.0e-6, horizontal)));
        double yaw = horizontal > 1.0e-4 ? SlideModel.yawOf(seen.dx(), seen.dz()) : Riders.get(player).heading();
        PlayerBody body = new PlayerBody(player);
        double dx = SlideModel.dirX(yaw) * 0.5, dz = SlideModel.dirZ(yaw) * 0.5;
        double behind = body.groundTop(seen.x() - dx, seen.z() - dz, seen.y() + 1.0, seen.y() - 2.0);
        double ahead = body.groundTop(seen.x() + dx, seen.z() + dz, seen.y() + 1.0, seen.y() - 2.0);
        double downhill = Double.isNaN(behind) || Double.isNaN(ahead) ? 0 : Math.toDegrees(Math.atan2(behind - ahead, 1.0));
        return Math.abs(descent - downhill);
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
