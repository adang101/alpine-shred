package io.github.adang101.alpineshred.movement;

import io.github.adang101.alpineshred.gear.GearKind;
import io.github.adang101.alpineshred.gear.GearStats;
import io.github.adang101.alpineshred.gear.GearWear;

/**
 * The arcade sliding model (design doc section 7, "Per-tick algorithm (ground)", steps 3 to 5),
 * free of Minecraft types so it can be unit-tested against the section 19 feel targets and reused
 * by the client controller, the server validator and game tests.
 *
 * <h2>Conventions</h2>
 * <ul>
 *   <li>Yaw is Minecraft yaw in degrees: 0 faces +Z (south), 90 faces -X (west); turning right
 *       (D) increases yaw. Direction vector for yaw {@code a} is {@code (-sin a, cos a)}.</li>
 *   <li>{@code speed} is horizontal speed in blocks per tick. Vertical motion is vanilla gravity
 *       plus ground snap, handled by {@link GroundContact} and {@link Ballistics}.</li>
 *   <li>{@link State#heading} is the board's nose. When the rider travels tail-first (after a
 *       180 or a switch landing) the leading end is {@code heading + 180}; steering, grip blend
 *       and slip use that leading end so switch riding is stable. The doc's step 4 formula assumes
 *       nose-first travel; this is the switch-aware reading of it.</li>
 * </ul>
 */
public final class SlideModel {
    /** Vanilla gravity in blocks per tick squared (32 blocks per second squared). */
    public static final double VANILLA_GRAVITY = 0.08;
    /** Vanilla vertical velocity multiplier applied after gravity each tick. */
    public static final double VANILLA_VERTICAL_DRAG = 0.98;
    /** Below this speed the rider counts as still ({@link RideState#IDLE}). */
    public static final double IDLE_SPEED = 0.02;
    /** Travel direction is only meaningful above this speed for switch detection. */
    public static final double SWITCH_MIN_SPEED = 0.05;
    /** Stance flips only past 90 degrees plus this margin, so hard drifts do not chatter. */
    public static final double SWITCH_HYSTERESIS = 10.0;
    /**
     * Looking further than this behind the leading end is looking over the shoulder, not steering.
     * Without it a camera pointing straight back gives a +-180 target that flips every tick.
     */
    public static final double LOOK_BEHIND_ANGLE = 135.0;
    /** Degrees of turn per tick above which a rider counts as carving without an edge key. */
    public static final double CARVE_TURN_THRESHOLD = 1.0;
    /** Minimum velocity-to-heading blend from the doc's clamp in step 4. */
    public static final double MIN_BLEND = 0.02;

    private SlideModel() {
    }

    public enum SteerMode {
        /** Default: the body follows the camera, A and D dig edges. */
        RIDE,
        /** A and D steer; the camera is free. */
        KEYS
    }

    /**
     * One tick of rider input.
     *
     * @param edge    edge input in [-1, 1]: D positive, A negative
     * @param lookYaw camera yaw in degrees
     */
    public record Input(double edge, double lookYaw, boolean tuck, boolean flare, boolean brake) {
        public static Input coast(double lookYaw) {
            return new Input(0, lookYaw, false, false, false);
        }
    }

    /**
     * What the rider is on and riding with this tick.
     *
     * @param edgeSharpness  stack {@code Edge}, 0 to 100
     * @param waxed          stack has {@code Wax} left
     * @param slopeAngle     slope angle in radians (from {@link SlopeMath})
     * @param downhillYaw    yaw pointing straight downhill, degrees
     * @param speedCapMul    server {@code movement.maxSpeedMul}
     */
    public record Conditions(GearStats gear, int edgeSharpness, boolean waxed, SurfaceProfile surface,
                             double slopeAngle, double downhillYaw, double speedCapMul) {
        public static Conditions flat(GearStats gear, SurfaceProfile surface) {
            return new Conditions(gear, GearWear.MAX_EDGE, false, surface, 0, 0, 1.0);
        }

        public static Conditions slope(GearStats gear, SurfaceProfile surface, double degrees, double downhillYaw) {
            return new Conditions(gear, GearWear.MAX_EDGE, false, surface, Math.toRadians(degrees), downhillYaw, 1.0);
        }
    }

    /** Mutable kinematic state of one rider on the ground. */
    public static final class State {
        /** Board nose yaw, degrees. */
        public double heading;
        /** Velocity direction yaw, degrees. */
        public double travelYaw;
        /** Horizontal speed, blocks per tick. */
        public double speed;

        // Outputs of the last step, read by state classification, sounds and particles.
        public double lastTurn;
        public double lastSlip;
        public boolean switchStance;
        public boolean edgeEngaged;

        public State() {
        }

        public State(double heading, double speed) {
            this.heading = heading;
            this.travelYaw = heading;
            this.speed = speed;
        }

        /** Yaw of the end of the board that leads travel. */
        public double leadingYaw() {
            return switchStance ? wrapDegrees(heading + 180) : heading;
        }
    }

    // ---- per-tick ground step ---------------------------------------------------------------

    /** Advances the rider one tick on the ground: turn, grip blend, slip loss, then speed. */
    public static void stepGround(State s, Input in, Conditions c, MovementTuning t, SteerMode mode) {
        GearStats gear = c.gear();
        GearKind kind = gear.kind();
        SurfaceProfile surface = c.surface();
        double edgeMul = GearWear.edgeGripMultiplier(c.edgeSharpness());
        double edgeIn = clamp(in.edge(), -1, 1);
        double speed = s.speed;

        // Which end leads. Below SWITCH_MIN_SPEED keep the previous stance to avoid flicker.
        if (speed > SWITCH_MIN_SPEED) {
            double offset = Math.abs(wrapDegrees(s.heading - s.travelYaw));
            if (!s.switchStance && offset > 90 + SWITCH_HYSTERESIS) s.switchStance = true;
            else if (s.switchStance && offset < 90 - SWITCH_HYSTERESIS) s.switchStance = false;
        }
        double leading = s.leadingYaw();

        // Step 3: turn.
        double maxTurn = turnRate(t, kind, speed) * gear.grip() * edgeMul;
        if (in.brake() && kind == GearKind.SKIS) maxTurn *= t.skiBrakeSteerMul;
        if (surface.category() == SurfaceProfile.Category.BOX) maxTurn *= t.slideSteerMul; // line up, not carve off
        boolean pivoting = speed < t.pivotSpeed;
        double counterGripMul = 1.0;
        double turn;
        if (mode == SteerMode.RIDE) {
            double want = wrapDegrees(in.lookYaw() - leading);
            if (Math.abs(want) < t.lookDeadzone || Math.abs(want) > LOOK_BEHIND_ANGLE) want = 0;
            double limit = maxTurn;
            if (!pivoting && edgeIn != 0 && want != 0) {
                if (Math.signum(edgeIn) == Math.signum(want)) {
                    limit *= 1 + t.edgeTurnBoost * Math.abs(edgeIn);
                } else {
                    counterGripMul = t.counterEdgeGrip;
                }
            }
            turn = clamp(want, -limit, limit);
            if (pivoting) turn += t.pivotRate * edgeIn;
        } else {
            turn = pivoting ? t.pivotRate * edgeIn : edgeIn * maxTurn;
        }
        leading = wrapDegrees(leading + turn);
        s.heading = s.switchStance ? wrapDegrees(leading - 180) : leading;
        s.edgeEngaged = !pivoting && edgeIn != 0;
        if (s.edgeEngaged) speed -= t.edgeScrub * Math.abs(edgeIn);

        // Step 4: blend velocity direction toward the leading end; sideways slip costs speed.
        double k = clamp(t.gripBlend * gear.grip() * surface.grip() * edgeMul * counterGripMul, MIN_BLEND, 1.0);
        double lag = wrapDegrees(leading - s.travelYaw);
        double slip = 1 - Math.cos(Math.toRadians(lag));
        s.travelYaw = wrapDegrees(s.travelYaw + k * lag);
        speed -= t.slipLoss * speed * slip;

        // Step 5: integrate speed along the travel direction.
        double accel = slopeAcceleration(t, c.slopeAngle(), s.travelYaw, c.downhillYaw());
        double friction = friction(t, c, in.brake());
        double drag = drag(t, c, in, speed);
        double next = speed + accel - friction - drag;
        if (next < 0) {
            // Stopped. If the slope pulls harder than friction holds, slide back down tail-first.
            double pull = -slopeAcceleration(t, c.slopeAngle(), s.travelYaw, c.downhillYaw());
            if (pull > friction) {
                // Reversing direction swaps which end leads; flip the stance now rather than
                // waiting for SWITCH_MIN_SPEED, or the grip blend would pull velocity back uphill.
                s.travelYaw = wrapDegrees(s.travelYaw + 180);
                s.switchStance = !s.switchStance;
                next = pull - friction;
            } else {
                next = 0;
            }
        }
        s.speed = Math.min(next, speedCap(t, c));
        s.lastTurn = turn;
        s.lastSlip = slip;
    }

    /** Ground sub-state for the synced rider state, from the step just taken. */
    public static RideState classifyGround(State s, Input in, SurfaceProfile surface) {
        if (surface.category() == SurfaceProfile.Category.POWDER) return RideState.POWDER;
        if (surface.category() == SurfaceProfile.Category.BOX && s.speed > IDLE_SPEED) return RideState.SLIDE;
        if (in.brake() && s.speed > IDLE_SPEED) return RideState.BRAKE;
        if (s.speed <= IDLE_SPEED) return RideState.IDLE;
        if (s.edgeEngaged || Math.abs(s.lastTurn) > CARVE_TURN_THRESHOLD) return RideState.CARVE;
        return RideState.GLIDE;
    }

    // ---- formula pieces (public for the validator, HUD and tests) ---------------------------

    /** {@code turnRate(s) = baseTurnRate / (1 + s / turnSpeedRef)}, times the board or ski multiplier. */
    public static double turnRate(MovementTuning t, GearKind kind, double speed) {
        double mul = kind == GearKind.SKIS ? t.skiTurnMul : t.boardTurnMul;
        return t.baseTurnRate / (1 + Math.max(0, speed) / t.turnSpeedRef) * mul;
    }

    /** {@code gEff * sin(theta) * cos(angle between travel and downhill)}. */
    public static double slopeAcceleration(MovementTuning t, double slopeAngle, double travelYaw, double downhillYaw) {
        if (slopeAngle == 0) return 0;
        double cosToDownhill = Math.cos(Math.toRadians(wrapDegrees(travelYaw - downhillYaw)));
        return t.effectiveGravity() * Math.sin(slopeAngle) * cosToDownhill;
    }

    public static double friction(MovementTuning t, Conditions c, boolean braking) {
        double mul = 1.0;
        if (c.waxed() && c.surface().isSnow()) mul *= t.waxFrictionMul;
        if (braking) mul *= c.gear().kind() == GearKind.SKIS ? t.skiBrakeFrictionMul : t.brakeFrictionMul;
        return c.surface().friction() * mul;
    }

    /**
     * Drag {@code dragCoeff * s^2 * tuckOrFlare}, divided by the square of the effective speed stat.
     * The doc says board speed "scales top speed and glide": dividing drag this way makes terminal
     * speed scale linearly with the stat, while neutral gear (1.0) matches the doc formula exactly.
     */
    public static double drag(MovementTuning t, Conditions c, Input in, double speed) {
        double mul = 1.0;
        if (in.tuck() && !in.flare()) mul = t.tuckDragMul;
        else if (in.flare() && !in.tuck()) mul = t.flareDragMul;
        double stat = effectiveSpeedStat(t, c);
        return t.dragCoeff * mul * speed * speed / (stat * stat);
    }

    /** Board speed stat, times the wax bonus on snow. */
    public static double effectiveSpeedStat(MovementTuning t, Conditions c) {
        double stat = c.gear().speed();
        if (c.waxed() && c.surface().isSnow()) stat *= t.waxSpeedMul;
        return stat;
    }

    /** {@code maxSpeed * board.speed}, with wax and the server multiplier. */
    public static double speedCap(MovementTuning t, Conditions c) {
        return t.maxSpeed * effectiveSpeedStat(t, c) * c.speedCapMul();
    }

    /** Server validator bound: {@code maxSpeed * board.speed * 1.15} (section 7, rule 1). */
    public static double validatorSpeedCap(MovementTuning t, GearStats gear, double speedCapMul) {
        return t.maxSpeed * gear.speed() * t.waxSpeedMul * speedCapMul * 1.15;
    }

    /** {@code 0.42 * board.pop * (1 + 0.6 * charge)}, charge from 0 to {@code popChargeTicks}. */
    public static double popVelocity(MovementTuning t, GearStats gear, int chargeTicks) {
        double charge = clamp(chargeTicks, 0, t.popChargeTicks) / (double) t.popChargeTicks;
        return t.popVelocity * gear.pop() * (1 + t.popChargeMax * charge);
    }

    /** Minimum speed to float on powder: {@code 0.25 / board.powderFloat}. */
    public static double powderFloatSpeed(MovementTuning t, GearStats gear) {
        return t.powderFloatSpeed / gear.powderFloat();
    }

    /** Wipeout damage {@code (impactSpeed - wallHitSpeed) * 8}, reduced 40% by a helmet. */
    public static double wipeoutDamage(MovementTuning t, double impactSpeed, boolean helmet) {
        double damage = Math.max(0, impactSpeed - t.wallHitSpeed) * t.wipeoutDamagePerSpeed;
        return helmet ? damage * (1 - t.helmetWipeoutReduction) : damage;
    }

    /**
     * One tick along a rail or box path (section 10, grind step 4): slope acceleration along the
     * path tangent minus friction, plus the same drag as on snow.
     *
     * @param sinSlope sine of the path's downhill angle along travel (negative going up)
     */
    public static double alongPathStep(MovementTuning t, double speed, double sinSlope, double friction) {
        double next = speed + t.effectiveGravity() * sinSlope - friction - t.dragCoeff * speed * speed;
        return Math.max(0, Math.min(next, t.maxSpeed * 1.5));
    }

    /** Pole push: +{@code poleImpulse}, only below {@code poleMaxSpeed}. Returns the new speed. */
    public static double polePush(MovementTuning t, double speed) {
        return speed < t.poleMaxSpeed ? Math.min(t.poleMaxSpeed, speed + t.poleImpulse) : speed;
    }

    // ---- angle helpers ----------------------------------------------------------------------

    /** Wraps degrees to [-180, 180). */
    public static double wrapDegrees(double degrees) {
        double d = degrees % 360.0;
        if (d >= 180.0) d -= 360.0;
        if (d < -180.0) d += 360.0;
        return d;
    }

    public static double dirX(double yawDegrees) {
        return -Math.sin(Math.toRadians(yawDegrees));
    }

    public static double dirZ(double yawDegrees) {
        return Math.cos(Math.toRadians(yawDegrees));
    }

    /** Yaw of a horizontal vector, inverse of {@link #dirX}/{@link #dirZ}. */
    public static double yawOf(double x, double z) {
        return wrapDegrees(Math.toDegrees(Math.atan2(-x, z)));
    }

    static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
