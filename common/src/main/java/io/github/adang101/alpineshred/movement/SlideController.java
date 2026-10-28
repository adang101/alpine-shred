package io.github.adang101.alpineshred.movement;

import io.github.adang101.alpineshred.gear.GearKind;
import io.github.adang101.alpineshred.trick.GrabType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The riding controller (design doc section 7): replaces vanilla ground movement while strapped in.
 * It runs on the local player's client from the {@code Player.travel} mixin; the server validates
 * results with {@link RideValidator} instead of simulating.
 *
 * <p>It is written against {@link Body} rather than Minecraft types, so the same code drives the
 * real player (through {@code PlayerBody}), the game tests, and the unit tests' voxel world.
 *
 * <h2>Walking and riding</h2>
 * A strapped rider is in one of two modes. In {@link Mode#RIDE} this controller moves the body. In
 * {@link Mode#WALK} vanilla movement runs with the -35% strapped modifier (section 6): this is the
 * case on non-snow ground below {@code scrapeSlideMinSpeed}, when scraping is disabled, and when
 * sinking into powder below the float speed. Landing on, or walking onto, snow switches to riding.
 */
public final class SlideController {
    /** Vertical push applied while grounded so collision keeps the body on the ground. */
    public static final double GROUND_STICK = 0.08;
    /** Extra reach of the ground cast below the snap distance. */
    private static final double CAST_MARGIN = 0.1;

    /** The rider in its world. */
    public interface Body {
        double x();

        double y();

        double z();

        /** Current horizontal velocity, used when taking over from walking. */
        double velocityX();

        double velocityZ();

        boolean onGround();

        /** Moves with collisions and step-up, like {@code Entity.move}. */
        Moved move(double dx, double dy, double dz);

        /** Moves straight down by {@code dy} (ground snap); the controller has already checked the gap is clear. */
        void snapDown(double dy);

        /**
         * Highest collision top at column (x, z) between {@code toY} and {@code fromY}, or NaN if there is
         * none or the cast starts inside a solid block.
         */
        double groundTop(double x, double z, double fromY, double toY);

        /** Surface of the block the rider stands on or floats over, or null when nothing is underfoot. */
        SurfaceProfile surfaceUnderFeet();
    }

    /** What {@link Body#move} actually did. */
    public record Moved(double dx, double dy, double dz, boolean onGround, boolean horizontalCollision) {
        public double horizontal() {
            return Math.hypot(dx, dz);
        }
    }

    public enum Mode {WALK, RIDE}

    /** Things that happened this tick, for packets, sounds and the validator. */
    public sealed interface Event permits Popped, Launched, Landed, WipedOut, Pushed {
    }

    public record Popped(double velocity, int chargeTicks) implements Event {
    }

    public record Launched(double velocity) implements Event {
    }

    public record Landed(int airTicks, double impactVelocity) implements Event {
    }

    public record WipedOut(double impactSpeed) implements Event {
    }

    public record Pushed() implements Event {
    }

    private final SlideModel.State motion = new SlideModel.State();
    private final SlopeMath.Filter slopeFilter = new SlopeMath.Filter();
    private final double[] climb = new double[2];
    private final List<Event> events = new ArrayList<>();

    private Mode mode = Mode.WALK;
    private RideState state = RideState.IDLE;
    private SlopeMath.Slope slope = SlopeMath.Slope.FLAT;
    private SurfaceProfile surface;
    private boolean grounded;
    private double vy;
    private int chargeTicks;
    private boolean jumpWasHeld;
    private int wipeoutTicks;
    private int airTicks;
    private double lean;

    // ---- per tick ---------------------------------------------------------------------------------

    /**
     * Advances one tick. Returns the mode the body is in afterwards: in {@link Mode#WALK} the caller
     * lets vanilla movement run this tick.
     */
    public Mode tick(Body body, RideInput input, RideContext ctx) {
        events.clear();
        if (mode == Mode.WALK && !tryStartRiding(body, input, ctx)) {
            state = RideState.IDLE;
            lean = 0;
            return Mode.WALK;
        }
        RideInput in = state == RideState.WIPEOUT ? input.locked() : input;
        if (grounded) {
            groundTick(body, in, ctx);
        } else {
            airTick(body, in, ctx);
        }
        if (state == RideState.WIPEOUT && mode == Mode.RIDE) {
            motion.speed *= 1 - ctx.tuning().wipeoutSpeedBleed;
            if (--wipeoutTicks <= 0) {
                state = grounded ? groundState(in) : RideState.AIR;
            }
        }
        return mode;
    }

    private boolean tryStartRiding(Body body, RideInput input, RideContext ctx) {
        if (!body.onGround()) return false;
        SurfaceProfile under = body.surfaceUnderFeet();
        if (under == null) return false;
        double speed = Math.hypot(body.velocityX(), body.velocityZ());
        if (!canRide(under, speed, ctx)) return false;
        mode = Mode.RIDE;
        grounded = true;
        vy = 0;
        motion.speed = speed;
        motion.heading = SlideModel.wrapDegrees(input.lookYaw());
        motion.travelYaw = speed > 0.01 ? SlideModel.yawOf(body.velocityX(), body.velocityZ()) : motion.heading;
        motion.switchStance = false;
        slopeFilter.reset();
        climb[0] = climb[1] = 0;
        chargeTicks = 0;
        jumpWasHeld = input.jump();
        surface = under;
        state = RideState.IDLE;
        return true;
    }

    /** Snow, ice and fast-enough powder are always ridden; other ground only while sliding fast. */
    private static boolean canRide(SurfaceProfile under, double speed, RideContext ctx) {
        MovementTuning t = ctx.tuning();
        return switch (under.category()) {
            case SNOW, ICE -> true;
            case POWDER -> speed >= SlideModel.powderFloatSpeed(t, ctx.gear());
            case SCRAPE -> ctx.scrapeEnabled() && speed >= t.scrapeSlideMinSpeed;
        };
    }

    private void groundTick(Body body, RideInput in, RideContext ctx) {
        MovementTuning t = ctx.tuning();
        surface = body.surfaceUnderFeet();
        if (surface == null) {
            // Ground vanished from under us (block broken, edge): fall.
            grounded = false;
            vy = 0;
            enterAir();
            airTick(body, in, ctx);
            return;
        }
        if (state != RideState.WIPEOUT && !canRide(surface, motion.speed, ctx)) {
            stopRiding();
            return;
        }

        sampleSlope(body, t.gradeSmoothing);
        if (in.forward() > 0 && surface.category() != SurfaceProfile.Category.SCRAPE && motion.speed < t.skateMaxSpeed) {
            motion.speed = Math.min(t.skateMaxSpeed, motion.speed + t.skateAccel);
        }
        SlideModel.stepGround(motion, in.groundInput(), ctx.conditions(surface, slope), t, ctx.steerMode());
        lean = state == RideState.WIPEOUT ? 0 : Math.max(-1, Math.min(1, in.strafe() != 0 ? in.strafe() : motion.lastTurn / 4.0));

        // Pop charges while jump is held and fires on release (section 7, "Jumps").
        boolean popped = false;
        double popVelocity = 0;
        if (state != RideState.WIPEOUT) {
            if (in.jump()) {
                chargeTicks = Math.min(chargeTicks + 1, t.popChargeTicks);
            } else if (jumpWasHeld) {
                popped = true;
                popVelocity = SlideModel.popVelocity(t, ctx.gear(), chargeTicks);
                events.add(new Popped(popVelocity, chargeTicks));
                chargeTicks = 0;
            }
        }
        jumpWasHeld = in.jump();

        double speedBefore = motion.speed;
        Moved moved = body.move(SlideModel.dirX(motion.travelYaw) * speedBefore,
                popped ? popVelocity : -GROUND_STICK,
                SlideModel.dirZ(motion.travelYaw) * speedBefore);
        handleWall(moved, speedBefore, t);

        if (popped) {
            grounded = false;
            vy = Ballistics.nextVerticalVelocity(popVelocity);
            enterAir();
            return;
        }
        double horizontal = moved.horizontal();
        double thisClimb = moved.dy() > 0 && horizontal > 1.0e-6 ? moved.dy() / horizontal : 0;
        double recentClimb = Math.max(Math.max(climb[0], climb[1]), thisClimb);
        recordClimb(thisClimb);

        if (moved.onGround()) {
            grounded = true;
            vy = 0;
        } else {
            double gap = gapBelow(body, GroundContact.snapDistance(t, motion.speed) + CAST_MARGIN);
            double upGrade = gap > 1.0e-4 ? Math.max(recentClimb, gradeBehindLip(body, moved)) : recentClimb;
            GroundContact.Result contact = GroundContact.resolve(t, true, false, moved.dy(), gap, upGrade, motion.speed);
            switch (contact.kind()) {
                case GROUNDED -> grounded = true;
                case SNAP -> {
                    body.snapDown(contact.snapDistance());
                    grounded = true;
                }
                case LAUNCH -> {
                    grounded = false;
                    vy = contact.launchVy();
                    events.add(new Launched(contact.launchVy()));
                    enterAir();
                    return;
                }
                case AIRBORNE -> {
                    grounded = false;
                    vy = 0;
                    enterAir();
                    return;
                }
            }
        }
        if (state != RideState.WIPEOUT) state = groundState(in);
    }

    private void airTick(Body body, RideInput in, RideContext ctx) {
        MovementTuning t = ctx.tuning();
        airTicks++;
        lean = 0;
        double speedBefore = motion.speed;
        double vyBefore = vy;
        Moved moved = body.move(SlideModel.dirX(motion.travelYaw) * speedBefore, vy,
                SlideModel.dirZ(motion.travelYaw) * speedBefore);
        handleWall(moved, speedBefore, t);
        jumpWasHeld = in.jump();
        if (moved.onGround() && vyBefore <= 0) {
            grounded = true;
            vy = 0;
            chargeTicks = 0;
            climb[0] = climb[1] = 0;
            slopeFilter.reset();
            surface = body.surfaceUnderFeet();
            events.add(new Landed(airTicks, vyBefore));
            airTicks = 0;
            if (surface != null && !canRide(surface, motion.speed, ctx) && state != RideState.WIPEOUT) {
                stopRiding();
                return;
            }
            if (state != RideState.WIPEOUT) state = surface == null ? RideState.AIR : groundState(in);
            return;
        }
        if (vyBefore > 0 && moved.dy() < vyBefore - 1.0e-6) {
            vy = 0; // bumped a ceiling
        }
        vy = Ballistics.nextVerticalVelocity(vy);
        if (state != RideState.WIPEOUT) state = RideState.AIR;
    }

    /**
     * A wall stops the velocity components it blocks (collision clips per axis). The impact speed
     * is the stopped part of the velocity, not the distance lost this tick: a head-on hit at 0.8 is
     * an 0.8 impact even when the rider was 0.7 blocks short of the wall when the tick began, and
     * glancing along a wall at a shallow angle is not a crash.
     */
    private void handleWall(Moved moved, double speedBefore, MovementTuning t) {
        if (!moved.horizontalCollision()) return;
        double ix = SlideModel.dirX(motion.travelYaw) * speedBefore;
        double iz = SlideModel.dirZ(motion.travelYaw) * speedBefore;
        boolean stopX = Math.abs(moved.dx()) < Math.abs(ix) - 1.0e-6;
        boolean stopZ = Math.abs(moved.dz()) < Math.abs(iz) - 1.0e-6;
        double impact = Math.hypot(stopX ? ix : 0, stopZ ? iz : 0);
        if (impact >= t.wallHitSpeed && state != RideState.WIPEOUT) {
            startWipeout(impact, t);
        }
        double vx = stopX ? 0 : ix, vz = stopZ ? 0 : iz;
        motion.speed = Math.hypot(vx, vz);
        if (motion.speed > 1.0e-4) motion.travelYaw = SlideModel.yawOf(vx, vz);
    }

    private void startWipeout(double impact, MovementTuning t) {
        state = RideState.WIPEOUT;
        wipeoutTicks = t.wipeoutTicks;
        chargeTicks = 0;
        events.add(new WipedOut(impact));
    }

    private void enterAir() {
        airTicks = 0;
        chargeTicks = 0;
        if (state != RideState.WIPEOUT) state = RideState.AIR;
    }

    private void stopRiding() {
        mode = Mode.WALK;
        state = RideState.IDLE;
        lean = 0;
    }

    private RideState groundState(RideInput in) {
        return surface == null ? RideState.GLIDE : SlideModel.classifyGround(motion, in.groundInput(), surface);
    }

    private void sampleSlope(Body body, double smoothing) {
        double x = body.x(), y = body.y(), z = body.z();
        double fromY = y + SlopeMath.CAST_UP, toY = y - SlopeMath.CAST_DOWN;
        double yaw = motion.travelYaw;
        double fx = SlideModel.dirX(yaw), fz = SlideModel.dirZ(yaw);
        double rx = SlideModel.dirX(yaw + 90), rz = SlideModel.dirZ(yaw + 90);
        SlopeMath.Slope raw = SlopeMath.fromHeights(yaw,
                body.groundTop(x, z, fromY, toY),
                body.groundTop(x + fx, z + fz, fromY, toY),
                body.groundTop(x - fx, z - fz, fromY, toY),
                body.groundTop(x - rx, z - rz, fromY, toY),
                body.groundTop(x + rx, z + rz, fromY, toY),
                1.0);
        slope = slopeFilter.update(raw, smoothing);
    }

    /**
     * The terrain grade leading up to the point where the rider left the ground this tick: rise over
     * run from one and from two blocks back to the lip, the steeper of the two. Unlike the per-tick
     * climb history, this does not depend on where the tick boundaries fall: a ramp of snow layers
     * climbs in steps, and the last tick or two before the lip may have crossed no step at all.
     */
    private double gradeBehindLip(Body body, Moved moved) {
        double lipX = body.x() - moved.dx(), lipY = body.y() - moved.dy(), lipZ = body.z() - moved.dz();
        double backX = -SlideModel.dirX(motion.travelYaw), backZ = -SlideModel.dirZ(motion.travelYaw);
        double fromY = lipY + SlopeMath.CAST_UP, toY = lipY - SlopeMath.CAST_DOWN;
        double best = 0;
        for (int d = 1; d <= 2; d++) {
            double h = body.groundTop(lipX + backX * d, lipZ + backZ * d, fromY, toY);
            if (!Double.isNaN(h)) best = Math.max(best, (lipY - h) / d);
        }
        return best;
    }

    private double gapBelow(Body body, double reach) {
        double y = body.y();
        double top = body.groundTop(body.x(), body.z(), y + 0.05, y - reach);
        return Double.isNaN(top) ? Double.POSITIVE_INFINITY : Math.max(0, y - top);
    }

    private void recordClimb(double grade) {
        climb[0] = climb[1];
        climb[1] = grade;
    }

    // ---- actions from outside -------------------------------------------------------------------------

    /** Pole push (section 7): skis only, on the ground, below {@code poleMaxSpeed}. Returns true if it pushed. */
    public boolean polePush(RideContext ctx) {
        if (mode != Mode.RIDE || !grounded || state == RideState.WIPEOUT || ctx.kind() != GearKind.SKIS) return false;
        if (motion.speed >= ctx.tuning().poleMaxSpeed) return false;
        motion.speed = SlideModel.polePush(ctx.tuning(), motion.speed);
        events.add(new Pushed());
        return true;
    }

    /** Server correction (section 7, validator rule 5): stop dead and wipe out where the server put us. */
    public void correct(MovementTuning t) {
        mode = Mode.RIDE;
        motion.speed = 0;
        vy = 0;
        grounded = false;
        state = RideState.WIPEOUT;
        wipeoutTicks = t.wipeoutTicks;
        chargeTicks = 0;
        jumpWasHeld = false;
    }

    // ---- read-outs -------------------------------------------------------------------------------------

    public Mode mode() {
        return mode;
    }

    public RideState state() {
        return state;
    }

    public List<Event> events() {
        return Collections.unmodifiableList(events);
    }

    public double speed() {
        return motion.speed;
    }

    public double heading() {
        return motion.heading;
    }

    public double travelYaw() {
        return motion.travelYaw;
    }

    public boolean switchStance() {
        return motion.switchStance;
    }

    /** -1..1, for the synced lean and camera roll. */
    public double lean() {
        return lean;
    }

    public boolean grounded() {
        return grounded;
    }

    public double verticalVelocity() {
        return vy;
    }

    public double velocityX() {
        return SlideModel.dirX(motion.travelYaw) * motion.speed;
    }

    public double velocityZ() {
        return SlideModel.dirZ(motion.travelYaw) * motion.speed;
    }

    public SlopeMath.Slope slope() {
        return slope;
    }

    public SurfaceProfile surface() {
        return surface;
    }

    public int chargeTicks() {
        return chargeTicks;
    }

    public int wipeoutTicks() {
        return wipeoutTicks;
    }

    /** The snapshot this rider should sync, given its mode and stance. */
    public RideSnapshot snapshot(RideMode rideMode, boolean goofy) {
        return new RideSnapshot(state, rideMode, goofy, false, GrabType.NONE,
                0, RideSnapshot.leanToLevel(lean), RideSnapshot.speedToLevel(motion.speed),
                RideSnapshot.headingToStep(motion.heading));
    }
}
