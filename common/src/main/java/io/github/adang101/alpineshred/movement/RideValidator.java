package io.github.adang101.alpineshred.movement;

import io.github.adang101.alpineshred.gear.GearStats;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Server-side plausibility checks for a client-simulated rider (design doc section 7, "Server
 * validation"). Vanilla movement is client-authoritative, so this checks results instead of
 * simulating: it is fed the position the server accepted each tick, plus the pops and launches the
 * client reported, and decides when a correction is due.
 *
 * <ol>
 *   <li>Speed: horizontal path length over the last {@link Limits#speedWindow} ticks, divided by
 *       the window, must stay under {@code maxSpeed * board.speed * wax * 1.15} plus a small
 *       tolerance. Averaging over a window absorbs two move packets landing in one server tick.</li>
 *   <li>Rise: while grounded the body may climb a step ({@code stepUp}); within
 *       {@link Limits#launchGraceTicks} of a reported pop or launch it may rise at the launch
 *       velocity; otherwise its rise is bounded by a ballistic budget that decays like vanilla gravity.</li>
 *   <li>State claims: {@code AIR} only while not standing ({@link #checkAirClaim}); the transition
 *       table itself is {@link RideTransitions}. Dropped packets also count ({@link #flag}).</li>
 *   <li>Three violations within {@link Limits#windowTicks} ticks call for a correction back to the
 *       last valid grounded position.</li>
 * </ol>
 * Pure Java; the game-side wiring is {@code RideServer}.
 */
public final class RideValidator {
    public static final double SPEED_TOLERANCE = 0.05;
    public static final double RISE_TOLERANCE = 0.05;
    public static final int AIR_CLAIM_GRACE_TICKS = 4;

    public record Limits(double speedCap, double groundRise, double maxLaunchVy, int launchGraceTicks,
                         int windowTicks, int violationsToCorrect, int speedWindow) {

        public static Limits of(MovementTuning t, GearStats gear, double speedCapMul) {
            double speedCap = SlideModel.validatorSpeedCap(t, gear, speedCapMul);
            double maxPop = t.popVelocity * gear.pop() * (1 + t.popChargeMax);
            double maxLaunch = GroundContact.launchVelocity(t, speedCap, GroundContact.MAX_LAUNCH_GRADE);
            return new Limits(speedCap, Math.max(t.stepUp, 0.6), Math.max(maxPop, maxLaunch), 3, 40, 3, 10);
        }

        /**
         * On a quarter pipe the rider climbs the arc on the ground at up to its speed, more than a
         * step, and leaves the top of the wall going straight up at what speed is left: the server
         * allows a rise, and a launch, of up to the speed cap per tick while one is in reach.
         */
        public Limits onPipe() {
            return new Limits(speedCap, Math.max(groundRise, speedCap), Math.max(maxLaunchVy, speedCap), launchGraceTicks, windowTicks,
                    violationsToCorrect, speedWindow);
        }
    }

    public enum Kind {OK, VIOLATION, CORRECT}

    /** @param reason short tag for logs; {@code x, y, z} the correction target for {@link Kind#CORRECT} */
    public record Verdict(Kind kind, String reason, double x, double y, double z) {
        static final Verdict OK = new Verdict(Kind.OK, "", 0, 0, 0);
    }

    private final Deque<double[]> recent = new ArrayDeque<>();
    private final Deque<Long> violations = new ArrayDeque<>();
    private double lastX, lastY, lastZ;
    private long lastTick = Long.MIN_VALUE / 2;
    private boolean hasLast;
    private boolean groundedLast;
    private long lastLaunchTick = Long.MIN_VALUE / 2;
    private double launchVelocity;
    private double airBudget;
    /** The air budget one and two ticks before this one (see {@link #CLOCK_JITTER_TICKS}). */
    private double airBudgetLag1, airBudgetLag2;
    private double validX, validY, validZ;
    private boolean hasValid;
    private int airClaimGroundTicks;

    /**
     * How far a reported launch may sit from the rise it starts, in rider ticks. An action packet's
     * place among the move packets is known only to within a tick or two on a real connection
     * (seen in the multiplayer smoke test: a kicker launch stamped ticks before its rise), so each
     * tick in the air may rise at the ballistic budget of up to two ticks earlier; with the launch
     * window that covers a stamp three ticks early. A launch gains at most two ticks of rise.
     */
    static final int CLOCK_JITTER_TICKS = 2;

    /** A pop ({@code c2s/ride_action}) the client reported; it rises in this same tick. */
    public void noteLaunch(long tick, double reportedVelocity, Limits limits) {
        double v = Math.max(0, Math.min(reportedVelocity, limits.maxLaunchVy()));
        lastLaunchTick = tick;
        launchVelocity = v;
        airBudget = Math.max(airBudget, v);
    }

    /**
     * A ramp launch the client reported. Unlike a pop, which rises in the tick it is reported, a
     * ramp launch is decided after that tick's move, so the rise at {@code reportedVelocity} begins
     * the tick after: the allowance window and the air budget start one tick later.
     */
    public void noteRampLaunch(long tick, double reportedVelocity, Limits limits) {
        double v = Math.max(0, Math.min(reportedVelocity, limits.maxLaunchVy()));
        lastLaunchTick = tick + 1;
        launchVelocity = v;
        airBudget = Math.max(airBudget, Ballistics.previousVerticalVelocity(v));
    }

    /** Forget history, e.g. after a teleport, respawn or strap-in. */
    public void reset() {
        recent.clear();
        violations.clear();
        hasLast = false;
        lastTick = Long.MIN_VALUE / 2;
        hasValid = false;
        airBudget = 0;
        airBudgetLag1 = 0;
        airBudgetLag2 = 0;
        groundedLast = false;
        airClaimGroundTicks = 0;
    }

    /**
     * Called once per server tick with the position the server now holds for the rider.
     *
     * @param tick the rider's clock: client ticks, counted by move packets (see {@code RideServer}),
     *             so packets that arrive bunched after a lag spike, or a server below 20 TPS, are
     *             judged by the client time they cover rather than the server time they arrived in.
     */
    public Verdict observe(long tick, double x, double y, double z, boolean onGround, Limits limits) {
        if (!hasLast) {
            remember(tick, x, y, z, onGround);
            markValid(x, y, z, onGround);
            recent.addLast(new double[]{tick, x, z});
            return Verdict.OK;
        }
        if (tick <= lastTick && x == lastX && y == lastY && z == lastZ) return Verdict.OK; // no packet, no movement
        long dt = Math.max(1, tick - lastTick);
        String reason = null;

        // Rule 1: horizontal path length over at least speedWindow ticks of rider time.
        recent.addLast(new double[]{tick, x, z});
        while (recent.size() > 2) {
            double[] first = recent.pollFirst();
            if (tick - recent.peekFirst()[0] < limits.speedWindow()) {
                recent.addFirst(first);
                break;
            }
        }
        double span = tick - recent.peekFirst()[0];
        if (span >= limits.speedWindow()) {
            double path = 0;
            double[] prev = null;
            for (double[] p : recent) {
                if (prev != null) path += Math.hypot(p[1] - prev[1], p[2] - prev[2]);
                prev = p;
            }
            double average = path / span;
            if (average > limits.speedCap() + SPEED_TOLERANCE) {
                reason = String.format(java.util.Locale.ROOT, "speed %.3f > %.3f", average, limits.speedCap());
            }
        }

        // Rule 2: rise, allowed per elapsed tick.
        double rise = y - lastY;
        double allowed = 0;
        double budget = airBudget, lag1 = airBudgetLag1, lag2 = airBudgetLag2;
        for (long i = 0; i < dt; i++) {
            long at = lastTick + 1 + i;
            boolean launchWindow = at - lastLaunchTick <= limits.launchGraceTicks();
            double ballistic = Math.max(budget, Math.max(lag1, lag2));
            if (groundedLast || onGround) {
                allowed += Math.max(limits.groundRise(), launchWindow ? launchVelocity : 0);
            } else {
                allowed += Math.max(0, launchWindow ? Math.max(launchVelocity, ballistic) : ballistic);
            }
            lag2 = lag1;
            lag1 = budget;
            budget = Ballistics.nextVerticalVelocity(Math.max(budget, 0));
        }
        if (rise > allowed + RISE_TOLERANCE && reason == null) {
            reason = String.format(java.util.Locale.ROOT, "rise %.3f > %.3f (ground %b, before %b, %d ticks; budget %.3f, last launch %d ticks ago at %.3f)",
                    rise, allowed, onGround, groundedLast, dt, airBudget, tick - lastLaunchTick, launchVelocity);
        }
        // Standing ends the air budget, except in a launch's own window: a pipe's top launches while
        // the server still counts the rider as on the arc.
        boolean launching = tick - lastLaunchTick <= limits.launchGraceTicks();
        boolean standing = onGround && !launching;
        airBudget = standing ? 0 : budget;
        airBudgetLag1 = standing ? 0 : lag1;
        airBudgetLag2 = standing ? 0 : lag2;

        remember(tick, x, y, z, onGround);
        if (reason == null) {
            markValid(x, y, z, onGround);
            return Verdict.OK;
        }
        return violation(tick, reason, limits);
    }

    /**
     * Counts a violation found outside {@link #observe}: a dropped (rate-limited) packet, or an
     * implausible state claim (section 15, "Rate limiting"; section 7, rule 3).
     */
    public Verdict flag(long tick, String reason, Limits limits) {
        return violation(tick, reason, limits);
    }

    /**
     * Rule 3 for {@code AIR}: a rider may claim to be airborne only while the server does not see it
     * standing on the ground. Allows {@value #AIR_CLAIM_GRACE_TICKS} ticks for the state packet to
     * catch up with the landing. Returns a reason when the claim has become implausible, else null.
     */
    public String checkAirClaim(boolean claimsAir, boolean onGround) {
        if (claimsAir && onGround) {
            if (++airClaimGroundTicks > AIR_CLAIM_GRACE_TICKS) {
                airClaimGroundTicks = 0;
                return "AIR claimed while standing";
            }
        } else {
            airClaimGroundTicks = 0;
        }
        return null;
    }

    private Verdict violation(long tick, String reason, Limits limits) {
        violations.addLast(tick);
        while (!violations.isEmpty() && tick - violations.peekFirst() > limits.windowTicks()) violations.removeFirst();
        if (violations.size() >= limits.violationsToCorrect()) {
            double cx = hasValid ? validX : lastX, cy = hasValid ? validY : lastY, cz = hasValid ? validZ : lastZ;
            violations.clear();
            recent.clear();
            recent.addLast(new double[]{tick, cx, cz});
            lastX = cx;
            lastY = cy;
            lastZ = cz;
            airBudget = 0;
            airClaimGroundTicks = 0;
            return new Verdict(Kind.CORRECT, reason, cx, cy, cz);
        }
        return new Verdict(Kind.VIOLATION, reason, 0, 0, 0);
    }

    private void remember(long tick, double x, double y, double z, boolean onGround) {
        lastTick = Math.max(lastTick, tick);
        lastX = x;
        lastY = y;
        lastZ = z;
        groundedLast = onGround;
        hasLast = true;
    }

    private void markValid(double x, double y, double z, boolean onGround) {
        if (onGround || !hasValid) {
            validX = x;
            validY = y;
            validZ = z;
            hasValid = true;
        }
    }

    public int recentViolations() {
        return violations.size();
    }
}
