package io.github.adang101.alpineshred.movement;

import io.github.adang101.alpineshred.gear.GearKind;
import io.github.adang101.alpineshred.park.GrindMaterial;
import io.github.adang101.alpineshred.park.GrindPath;
import io.github.adang101.alpineshred.park.GrindPhysics;
import io.github.adang101.alpineshred.park.ParkView;
import io.github.adang101.alpineshred.park.PipeArc;
import io.github.adang101.alpineshred.park.PipeSection;
import io.github.adang101.alpineshred.park.V3;
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
 *
 * <h2>Park features (phase 4)</h2>
 * Within {@link Mode#RIDE}, three sub-modes take over from ground and air riding: grinding a rail
 * along its path ({@link GrindPhysics}, state {@code GRIND}); riding a quarter pipe's arc
 * analytically ({@link PipeSection}); and sliding a box, which is ground riding on a box surface
 * with reduced steering and no ground snap off its end (state {@code SLIDE}).
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

        /** Park features around the rider (rails, pipes, lips). */
        default ParkView park() {
            return ParkView.NONE;
        }

        /**
         * Puts the feet exactly at (x, y, z), standing (grinding and pipe riding follow paths, not
         * collisions).
         */
        default void placeAt(double x, double y, double z) {
            move(x - x(), y - y(), z - z());
        }
    }

    /** What {@link Body#move} actually did. */
    public record Moved(double dx, double dy, double dz, boolean onGround, boolean horizontalCollision) {
        public double horizontal() {
            return Math.hypot(dx, dz);
        }
    }

    public enum Mode {WALK, RIDE}

    /** Things that happened this tick, for packets, sounds and the validator. */
    public sealed interface Event permits Popped, Launched, Landed, WipedOut, Pushed, Caught {
    }

    /** Caught a rail (section 10, grind assist: "a spark and sound cue confirm the catch"). */
    public record Caught(GrindMaterial material) implements Event {
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

    private static final int TERRAIN_SAMPLES = 8;
    private static final double TERRAIN_WINDOW = 3.0;

    private final SlideModel.State motion = new SlideModel.State();
    private final double[] terrainX = new double[TERRAIN_SAMPLES];
    private final double[] terrainZ = new double[TERRAIN_SAMPLES];
    private final double[] terrainDist = new double[TERRAIN_SAMPLES];
    private final double[] terrainHeight = new double[TERRAIN_SAMPLES];
    private int terrainCount;
    private int terrainNext;
    /** Horizontal distance ridden on the ground since the last takeover or landing. */
    private double travelled;
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

    // ---- park sub-modes ----
    /** The rail being ground, or null. */
    private GrindPhysics.Grind grind;
    private int switchUpCooldown;
    private boolean strafeWasHeld;
    private boolean pressing;
    /** The quarter pipe being ridden, or null; with the angle up the arc and the speeds along and across it. */
    private PipeSection pipe;
    private double pipeTheta;
    private double pipeSpeed;
    private double pipeAcross;
    private double pipeAcrossSpeed;
    /** Ticks since a launch off a lip ramp, or -1. */
    private int lipTicks = -1;

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
        if (grind != null) {
            grindTick(body, in, ctx);
        } else if (pipe != null) {
            pipeTick(body, in, ctx);
        } else if (state != RideState.WIPEOUT && tryGrind(body, in, ctx)) {
            grindTick(body, in, ctx);
        } else if (state != RideState.WIPEOUT && tryPipe(body)) {
            pipeTick(body, in, ctx);
        } else if (grounded) {
            groundTick(body, in, ctx);
        } else {
            airTick(body, in, ctx);
        }
        strafeWasHeld = in.strafe() != 0;
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
        clearTerrain();
        travelled = 0;
        climb[0] = climb[1] = 0;
        chargeTicks = 0;
        jumpWasHeld = input.jump();
        surface = under;
        state = RideState.IDLE;
        return true;
    }

    /**
     * Snow, ice and fast-enough powder are always ridden; other ground only while sliding fast; a box
     * only when arriving moving (section 11: "previously airborne or gliding") at the slide speed.
     */
    private boolean canRide(SurfaceProfile under, double speed, RideContext ctx) {
        MovementTuning t = ctx.tuning();
        return switch (under.category()) {
            case SNOW, ICE -> true;
            case POWDER -> speed >= SlideModel.powderFloatSpeed(t, ctx.gear());
            case SCRAPE -> ctx.scrapeEnabled() && speed >= t.scrapeSlideMinSpeed;
            case BOX -> speed >= t.slideMinSpeed && state != RideState.IDLE;
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
        travelled += horizontal;
        double thisClimb = moved.dy() > 0 && horizontal > 1.0e-6 ? moved.dy() / horizontal : 0;
        double recentClimb = Math.max(Math.max(climb[0], climb[1]), thisClimb);
        recordClimb(thisClimb);

        if (moved.onGround()) {
            grounded = true;
            vy = 0;
        } else if (state == RideState.SLIDE) {
            // Section 11: sliding past the last box exits to AIR with the velocity kept, no snap.
            grounded = false;
            vy = 0;
            enterAir();
            return;
        } else {
            double gap = gapBelow(body, GroundContact.snapDistance(t, motion.speed) + CAST_MARGIN);
            double upGrade = gap > 1.0e-4 ? Math.max(recentClimb, terrainClimbGrade(body)) : recentClimb;
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
                    // The launch is decided up to a block and a half past the ramp's edge (the rider
                    // leaves the top between samples), so look back along the way it came.
                    boolean lip = false;
                    for (double back = 0.3; speedBefore >= ctx.tuning().lipAssistMinSpeed && back <= 1.9 && !lip; back += 0.4) {
                        lip = body.park().lipNear(new V3(body.x() - SlideModel.dirX(motion.travelYaw) * back, body.y() - 0.5,
                                body.z() - SlideModel.dirZ(motion.travelYaw) * back));
                    }
                    enterAir();
                    lipTicks = lip ? 0 : -1;
                    jumpWasHeld = in.jump();
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
        if (lipTicks >= 0) {
            // Section 11, lip assist: jump within a few ticks of leaving a lip adds 20% of a pop.
            lipTicks++;
            if (in.jump() && !jumpWasHeld && lipTicks <= t.lipAssistTicks && state != RideState.WIPEOUT) {
                double bonus = t.lipAssistPop * SlideModel.popVelocity(t, ctx.gear(), 0);
                vy += bonus;
                events.add(new Popped(bonus, 0));
                lipTicks = -1;
            } else if (lipTicks > t.lipAssistTicks) {
                lipTicks = -1;
            }
        }
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
            clearTerrain();
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
        lipTicks = -1;
        if (state != RideState.WIPEOUT) state = RideState.AIR;
    }

    private void stopRiding() {
        mode = Mode.WALK;
        state = RideState.IDLE;
        lean = 0;
        grind = null;
        pipe = null;
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
        double center = body.groundTop(x, z, fromY, toY);
        recordTerrain(x, z, center);
        SlopeMath.Slope raw = SlopeMath.fromHeights(yaw,
                center,
                body.groundTop(x + fx, z + fz, fromY, toY),
                body.groundTop(x - fx, z - fz, fromY, toY),
                body.groundTop(x - rx, z - rz, fromY, toY),
                body.groundTop(x + rx, z + rz, fromY, toY),
                1.0);
        slope = slopeFilter.update(raw, smoothing);
    }

    /**
     * The grade of the terrain leading up to the lip: the lip is the highest recent ground sample
     * (the terrain under the rider's center at the start of each grounded tick, within the last
     * {@value #TERRAIN_WINDOW} blocks), and the grade is its rise over the ground one and two blocks
     * further back, the steeper of the two.
     *
     * <p>Why not the rider's own climb: a ramp of snow layers climbs in steps the last ticks before
     * the lip may not cross. Why not the terrain behind the rider's last position: vanilla collision
     * checks the ground before moving forward, so at speed a rider can end a tick hovering past an
     * edge, and that position is not the top of a ramp. The highest sample is.
     */
    private double terrainClimbGrade(Body body) {
        if (terrainCount == 0) return 0;
        int newest = (terrainNext - 1 + TERRAIN_SAMPLES) % TERRAIN_SAMPLES;
        int lip = -1;
        for (int a = 0; a < terrainCount; a++) {
            int i = (terrainNext - 1 - a + TERRAIN_SAMPLES) % TERRAIN_SAMPLES;
            if (terrainDist[newest] - terrainDist[i] > TERRAIN_WINDOW) break;
            if (lip < 0 || terrainHeight[i] > terrainHeight[lip] + 1.0e-6) lip = i;
        }
        double backX = -SlideModel.dirX(motion.travelYaw), backZ = -SlideModel.dirZ(motion.travelYaw);
        double h = terrainHeight[lip];
        double fromY = h + SlopeMath.CAST_UP, toY = h - SlopeMath.CAST_DOWN;
        double best = 0;
        for (int d = 1; d <= 2; d++) {
            double behind = body.groundTop(terrainX[lip] + backX * d, terrainZ[lip] + backZ * d, fromY, toY);
            if (!Double.isNaN(behind)) best = Math.max(best, (h - behind) / d);
        }
        return best;
    }

    private void recordTerrain(double x, double z, double height) {
        if (Double.isNaN(height)) return;
        terrainX[terrainNext] = x;
        terrainZ[terrainNext] = z;
        terrainDist[terrainNext] = travelled;
        terrainHeight[terrainNext] = height;
        terrainNext = (terrainNext + 1) % TERRAIN_SAMPLES;
        terrainCount = Math.min(terrainCount + 1, TERRAIN_SAMPLES);
    }

    private void clearTerrain() {
        terrainCount = 0;
        terrainNext = 0;
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

    // ---- grinding (section 10) --------------------------------------------------------------------

    /** Switch-ups: at most one variant change per this many ticks (section 8). */
    private static final int SWITCH_UP_TICKS = 20;

    /** Steps 1 to 3: catch a rail around the feet, now or one tick of velocity ahead. */
    private boolean tryGrind(Body body, RideInput in, RideContext ctx) {
        double vx = SlideModel.dirX(motion.travelYaw) * motion.speed;
        double vz = SlideModel.dirZ(motion.travelYaw) * motion.speed;
        double vyNow = grounded ? 0 : vy;
        V3 velocity = new V3(vx, vyNow, vz);
        if (velocity.horizontalLength() < GrindPhysics.MIN_SPEED) return false;
        V3 feet = new V3(body.x(), body.y(), body.z());
        GrindPhysics.Lookup lookup = body.park().grinds();
        GrindPhysics.Grind caught = GrindPhysics.capture(lookup.near(feet), feet, velocity, motion.heading);
        if (caught == null) {
            V3 ahead = feet.add(velocity);
            caught = GrindPhysics.capture(lookup.near(ahead), ahead, velocity, motion.heading);
        }
        if (caught == null) return false;
        if (!grounded) events.add(new Landed(airTicks, vyNow));
        events.add(new Caught(caught.path().material()));
        grind = caught;
        grounded = true;
        vy = 0;
        chargeTicks = 0;
        switchUpCooldown = 0;
        airTicks = 0;
        lipTicks = -1;
        V3 p = caught.position();
        body.placeAt(p.x(), p.y(), p.z());
        state = RideState.GRIND;
        return true;
    }

    /**
     * Steps 4 to 6: ride along the line, onto the next piece at each end; jump pops off with the
     * rail's velocity, a tap of A or D is a switch-up, sneak presses.
     */
    private void grindTick(Body body, RideInput in, RideContext ctx) {
        MovementTuning t = ctx.tuning();
        GrindPhysics.Lookup lookup = body.park().grinds();
        GrindPhysics.Grind g = grind;
        // The piece may have been broken under the rider.
        GrindPath current = null;
        for (GrindPath p : lookup.near(g.position())) {
            if (p.id() == g.path().id()) current = p;
        }
        if (current == null) {
            leaveGrind(body, g.travel().scale(g.speed()), RideState.AIR);
            return;
        }
        pressing = in.sneak();
        if (switchUpCooldown > 0) switchUpCooldown--;
        if (in.strafe() != 0 && !strafeWasHeld && switchUpCooldown == 0) {
            double relative = SlideModel.wrapDegrees(g.relativeHeading() + 90 * Math.signum(in.strafe()));
            g = new GrindPhysics.Grind(g.path(), g.s(), g.direction(), g.speed(), relative, GrindPhysics.variant(relative));
            switchUpCooldown = SWITCH_UP_TICKS;
        }
        if (in.jump() && !jumpWasHeld) {
            // Section 10, step 6: a pop that inherits the rail's velocity.
            jumpWasHeld = true;
            double pop = SlideModel.popVelocity(t, ctx.gear(), 0);
            V3 v = g.travel().scale(g.speed());
            grind = null;
            motion.speed = v.horizontalLength();
            if (motion.speed > 1.0e-4) motion.travelYaw = SlideModel.yawOf(v.x(), v.z());
            motion.heading = g.heading();
            grounded = false;
            vy = Math.max(0, v.y()) + pop;
            events.add(new Popped(pop, 0));
            enterAir();
            body.move(SlideModel.dirX(motion.travelYaw) * motion.speed, vy, SlideModel.dirZ(motion.travelYaw) * motion.speed);
            vy = Ballistics.nextVerticalVelocity(vy);
            return;
        }
        jumpWasHeld = in.jump();
        double waxMul = ctx.waxed() ? t.waxFrictionMul : 1.0;
        GrindPhysics.Step step = GrindPhysics.advance(g.withSpeed(g.speed()), lookup, t, waxMul);
        grind = step.grind();
        travelled += step.distance();
        V3 p = step.position();
        body.placeAt(p.x(), p.y(), p.z());
        motion.heading = grind.heading();
        V3 v = step.velocity();
        motion.speed = Math.max(v.horizontalLength(), 0);
        if (motion.speed > 1.0e-4) motion.travelYaw = SlideModel.yawOf(v.x(), v.z());
        lean = 0;
        if (step.exit() == GrindPhysics.Exit.END) {
            leaveGrind(body, v, null);
            return;
        }
        if (grind.speed() <= 1.0e-3) {
            // Stalled on a rise: slide back down the way it came.
            grind = new GrindPhysics.Grind(grind.path(), grind.s(), -grind.direction(), 0.0, grind.relativeHeading(), grind.variant());
        }
        state = RideState.GRIND;
    }

    /** Leaves the rail with {@code velocity}: onto the ground below if there is some, otherwise into the air. */
    private void leaveGrind(Body body, V3 velocity, RideState forced) {
        grind = null;
        pressing = false;
        motion.speed = velocity.horizontalLength();
        if (motion.speed > 1.0e-4) motion.travelYaw = SlideModel.yawOf(velocity.x(), velocity.z());
        double gap = gapBelow(body, 0.2);
        if (forced == null && gap <= 0.1 && velocity.y() <= 0.05) {
            grounded = true;
            vy = 0;
            surface = body.surfaceUnderFeet();
            state = RideState.GLIDE;
        } else {
            grounded = false;
            vy = velocity.y();
            enterAir();
        }
    }

    // ---- quarter pipes (section 11) --------------------------------------------------------------

    /**
     * Enters the arc of a quarter pipe the rider's front edge has reached: from the ground at the
     * transition (moving toward the wall), or from the air coming down onto it.
     */
    private boolean tryPipe(Body body) {
        V3 feet = new V3(body.x(), body.y(), body.z());
        double vx = SlideModel.dirX(motion.travelYaw) * motion.speed, vz = SlideModel.dirZ(motion.travelYaw) * motion.speed;
        V3 next = grounded ? feet.add(vx, 0, vz) : feet.add(vx, vy, vz);
        PipeSection section = body.park().pipeNear(next);
        if (section == null) return false;
        double arc = section.surfaceUnder(next);
        if (Double.isNaN(arc)) return false;
        if (!section.coversAcross(section.across(next), 0.0)) return false;
        double along = vx * section.facing().dx + vz * section.facing().dz;
        double theta = PipeArc.theta(section.radius(), section.u(next));
        if (grounded) {
            if (next.y() > arc + 0.15 || along <= 0) return false;
        } else {
            if (next.y() > arc || vy > 0) return false;
        }
        V3 tangent = section.tangent(theta);
        double speedAlong = vx * tangent.x() + (grounded ? 0 : vy) * tangent.y() + vz * tangent.z();
        if (grounded && theta < 0.05 && speedAlong <= 0) return false;
        V3 l = section.lateral();
        pipe = section;
        pipeTheta = theta;
        pipeSpeed = speedAlong;
        pipeAcross = section.across(next);
        pipeAcrossSpeed = vx * l.x() + vz * l.z();
        if (!grounded) events.add(new Landed(airTicks, vy));
        grounded = true;
        vy = 0;
        airTicks = 0;
        chargeTicks = 0;
        lipTicks = -1;
        return true;
    }

    /**
     * One tick on the arc: gravity along it, friction and drag; out over the coping when it reaches
     * the vertical, back onto the flat when it comes down past the transition, off the side past the
     * last pipe block.
     */
    private void pipeTick(Body body, RideInput in, RideContext ctx) {
        MovementTuning t = ctx.tuning();
        PipeSection section = pipe;
        double radius = section.radius();
        double friction = (surface != null ? surface.friction() : 0.0008) * (ctx.waxed() ? t.waxFrictionMul : 1.0);
        pipeSpeed -= t.effectiveGravity() * Math.sin(pipeTheta);
        double loss = friction + t.dragCoeff * pipeSpeed * pipeSpeed;
        pipeSpeed = pipeSpeed > 0 ? Math.max(0, pipeSpeed - loss) : Math.min(0, pipeSpeed + loss);
        pipeTheta += pipeSpeed / radius;
        pipeAcross += pipeAcrossSpeed;
        pipeAcrossSpeed *= 1 - 0.02;
        jumpWasHeld = in.jump();
        lean = 0;
        V3 lateral = section.lateral();
        V3 facing = section.facing().vector();
        if (!section.coversAcross(pipeAcross, 0.0)) {
            // Off the side of the pipe.
            V3 v = section.tangent(Math.max(0, Math.min(Math.PI / 2, pipeTheta))).scale(pipeSpeed).add(lateral.scale(pipeAcrossSpeed));
            V3 p = section.feetAt(Math.max(0, Math.min(Math.PI / 2, pipeTheta)), pipeAcross);
            body.placeAt(p.x(), p.y(), p.z());
            pipe = null;
            setTravel(v.x(), v.z());
            grounded = false;
            vy = v.y();
            enterAir();
            return;
        }
        if (pipeTheta >= Math.PI / 2) {
            // Over the coping: straight up, with only the speed along the wall.
            V3 p = section.feetAt(Math.PI / 2, pipeAcross);
            body.placeAt(p.x(), p.y(), p.z());
            pipe = null;
            setTravel(lateral.x() * pipeAcrossSpeed, lateral.z() * pipeAcrossSpeed);
            grounded = false;
            vy = Math.max(0, pipeSpeed);
            events.add(new Launched(vy));
            enterAir();
            return;
        }
        if (pipeTheta <= 0) {
            // Back down on the flat, riding away from the wall.
            V3 p = section.feetAt(0, pipeAcross);
            body.placeAt(p.x(), p.y(), p.z());
            pipe = null;
            setTravel(facing.x() * pipeSpeed + lateral.x() * pipeAcrossSpeed, facing.z() * pipeSpeed + lateral.z() * pipeAcrossSpeed);
            grounded = true;
            vy = 0;
            state = RideState.GLIDE;
            return;
        }
        V3 p = section.feetAt(pipeTheta, pipeAcross);
        body.placeAt(p.x(), p.y(), p.z());
        V3 v = section.tangent(pipeTheta).scale(pipeSpeed).add(lateral.scale(pipeAcrossSpeed));
        setTravel(v.x(), v.z());
        if (state != RideState.WIPEOUT) state = RideState.GLIDE;
    }

    /** Sets the horizontal motion from a velocity, keeping the heading. */
    private void setTravel(double vx, double vz) {
        motion.speed = Math.hypot(vx, vz);
        if (motion.speed > 1.0e-4) motion.travelYaw = SlideModel.yawOf(vx, vz);
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

    /** The rail being ground, or null. */
    public GrindPhysics.Grind grind() {
        return grind;
    }

    /** True while sneaking on a rail (the press). */
    public boolean pressing() {
        return grind != null && pressing;
    }

    /** True while riding a quarter pipe's arc. */
    public boolean inPipe() {
        return pipe != null;
    }

    /** The snapshot this rider should sync, given its mode and stance. */
    public RideSnapshot snapshot(RideMode rideMode, boolean goofy) {
        return new RideSnapshot(state, rideMode, goofy, false, GrabType.NONE,
                0, RideSnapshot.leanToLevel(lean), RideSnapshot.speedToLevel(motion.speed),
                RideSnapshot.headingToStep(motion.heading));
    }
}
