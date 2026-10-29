package io.github.adang101.alpineshred.client.feel;

import io.github.adang101.alpineshred.client.RideDriver;
import io.github.adang101.alpineshred.feel.LeanRig;
import io.github.adang101.alpineshred.feel.ParticleRules;
import io.github.adang101.alpineshred.feel.RiderClips;
import io.github.adang101.alpineshred.movement.PlayerBody;
import io.github.adang101.alpineshred.movement.RideMode;
import io.github.adang101.alpineshred.movement.RideSnapshot;
import io.github.adang101.alpineshred.movement.RideState;
import io.github.adang101.alpineshred.movement.Riders;
import io.github.adang101.alpineshred.movement.SlideController;
import io.github.adang101.alpineshred.movement.SlideModel;
import io.github.adang101.alpineshred.movement.SurfaceProfile;
import io.github.adang101.alpineshred.movement.TuningData;
import io.github.adang101.alpineshred.park.GrindMaterial;
import io.github.adang101.alpineshred.park.GrindPath;
import io.github.adang101.alpineshred.park.GrindPhysics;
import io.github.adang101.alpineshred.park.RailPath;
import io.github.adang101.alpineshred.park.V3;
import io.github.adang101.alpineshred.park.block.BoxMaterials;
import io.github.adang101.alpineshred.park.block.GrindPaths;
import io.github.adang101.alpineshred.registry.ModTags;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * What one client knows about one rider this tick, the input to animation, sound and particles
 * (design doc sections 9 and 17). Remote riders are read from the synced state and their observed
 * motion; the local rider from its controller, which is ahead of the server echo. Either way the
 * same rules turn this into pictures and sound, so every rider looks and sounds alike everywhere.
 */
final class RiderView {
    final AbstractClientPlayer player;
    final boolean local;
    private final PlayerBody body;
    final RiderClips.Director director = new RiderClips.Director();
    final ParticleRules.Accumulator spray = new ParticleRules.Accumulator();

    RideState state = RideState.OFF;
    RideState previous = RideState.OFF;
    RideMode mode = RideMode.NONE;
    boolean goofy;
    boolean switchStance;
    boolean grounded;
    double speed;
    double lean;
    /** Board heading or ski direction, degrees. */
    double heading;
    double charge;
    double verticalVelocity;
    @Nullable
    SurfaceProfile surface;
    @Nullable
    BlockState blockUnder;
    String clip;
    LeanRig.Pose leanPose = LeanRig.Pose.NEUTRAL;
    boolean holdingPoles;

    /** Events this tick, from transitions (remote) or controller events (local). */
    boolean popped;
    double popCharge;
    double landedImpact = Double.NaN;
    boolean wipedOut;
    boolean polePushed;
    /** Caught a rail this tick (the catch cue). */
    boolean caught;
    /** What the rail being ground or the box being slid on is made of, or null. */
    @Nullable
    GrindMaterial material;
    /** Whether the rider is on a rail or a box this tick, and the board is pressed (sneak on a rail). */
    boolean pressing;
    private int materialAge;
    /** The grind or slide variant on a rail or box, else null. */
    @Nullable
    GrindPhysics.Variant variant;

    double bodyYaw;
    boolean bodyYawSet;
    int offTicks;

    private double lastX, lastY, lastZ;
    private boolean hasLast;
    private boolean wasSwinging;

    RiderView(AbstractClientPlayer player, boolean local) {
        this.player = player;
        this.local = local;
        this.body = new PlayerBody(player);
    }

    void observe() {
        RideSnapshot snap = Riders.get(player);
        double dx = hasLast ? player.getX() - lastX : 0;
        double dy = hasLast ? player.getY() - lastY : 0;
        double dz = hasLast ? player.getZ() - lastZ : 0;
        lastX = player.getX();
        lastY = player.getY();
        lastZ = player.getZ();
        hasLast = true;

        previous = state;
        popped = false;
        wipedOut = false;
        polePushed = false;
        caught = false;
        pressing = false;
        landedImpact = Double.NaN;
        double prevVy = verticalVelocity;
        verticalVelocity = dy;
        mode = snap.mode();
        goofy = snap.goofy();
        holdingPoles = player.getMainHandItem().is(ModTags.Items.POLES);

        SlideController c = local ? RideDriver.INSTANCE.controller() : null;
        if (c != null && snap.state().isStrapped()) {
            boolean riding = c.mode() == SlideController.Mode.RIDE;
            state = riding ? c.state() : RideState.IDLE;
            speed = riding ? c.speed() : Math.hypot(dx, dz);
            lean = c.lean();
            heading = riding ? c.heading() : player.getYRot();
            switchStance = riding && c.switchStance();
            grounded = !riding || c.grounded();
            charge = c.chargeTicks() / (double) Math.max(1, TuningData.tuning().popChargeTicks);
            if (riding) verticalVelocity = c.verticalVelocity();
            for (SlideController.Event e : RideDriver.INSTANCE.drainEvents()) {
                if (e instanceof SlideController.Popped pop) {
                    popped = true;
                    popCharge = pop.chargeTicks() / (double) Math.max(1, TuningData.tuning().popChargeTicks);
                } else if (e instanceof SlideController.Landed land) {
                    landedImpact = land.impactVelocity();
                } else if (e instanceof SlideController.WipedOut) {
                    wipedOut = true;
                } else if (e instanceof SlideController.Pushed) {
                    polePushed = true;
                } else if (e instanceof SlideController.Caught) {
                    caught = true;
                }
            }
            pressing = c.pressing();
        } else {
            state = snap.state();
            speed = Math.hypot(dx, dz);
            lean = snap.lean() / (double) RideSnapshot.MAX_LEAN;
            heading = state == RideState.IDLE ? player.getYRot() : snap.heading();
            grounded = player.onGround();
            charge = 0;
            // Remote riders: switch when travelling more than 90 degrees from the board heading.
            switchStance = speed > 0.05
                    && Math.abs(SlideModel.wrapDegrees(SlideModel.yawOf(dx, dz) - heading)) > 90;
            if (state != previous) {
                if (state == RideState.AIR && previous.isGroundRiding() && dy > 0.1) {
                    popped = true;
                    popCharge = 0.5;
                } else if (previous == RideState.AIR && state.isGroundRiding()) {
                    landedImpact = Math.min(prevVy, dy);
                } else if (state == RideState.WIPEOUT) {
                    wipedOut = true;
                }
                if (state == RideState.GRIND) caught = true;
            }
            pressing = state == RideState.GRIND && player.isCrouching();
            // A pole push swings the arm (the client that pushed sends the swing to everyone).
            boolean swinging = player.swinging;
            if (swinging && !wasSwinging && holdingPoles && mode == RideMode.SKIS && state.isGroundRiding()) polePushed = true;
            wasSwinging = swinging;
        }
        if (local && state != previous && state == RideState.WIPEOUT) wipedOut = true;

        surface = state.isStrapped() ? body.surfaceUnderFeet() : null;
        blockUnder = state.isStrapped() ? player.level().getBlockState(BlockPos.containing(player.getX(), player.getY() - 0.2, player.getZ())) : null;
        offTicks = state.isStrapped() ? 0 : offTicks + 1;
        material = materialNow(c);
        variant = variantNow(c, dx, dz);

        clip = director.tick(state, mode, goofy, speed, charge, variant);
        LeanRig.Pose target = state.isStrapped() && state != RideState.WIPEOUT
                ? LeanRig.pose(mode, goofy, switchStance, lean, speed, charge, grounded)
                : LeanRig.Pose.NEUTRAL;
        leanPose = LeanRig.Pose.lerp(leanPose, target, local ? 0.5 : 0.35);
    }

    /**
     * The rail's material while grinding (the local controller knows its path; for others, the
     * nearest path around the feet, looked up again every 10 ticks), the box's while sliding.
     */
    @Nullable
    private GrindMaterial materialNow(@Nullable SlideController c) {
        if (state == RideState.SLIDE) return blockUnder == null ? null : BoxMaterials.material(blockUnder);
        if (state != RideState.GRIND) {
            materialAge = 0;
            return null;
        }
        if (c != null && c.grind() != null) return c.grind().path().material();
        if (material != null && previous == RideState.GRIND && ++materialAge % 10 != 0) return material;
        V3 feet = new V3(player.getX(), player.getY(), player.getZ());
        GrindPath best = null;
        double bestDistance = Double.MAX_VALUE;
        for (GrindPath p : GrindPaths.around(player.level(), feet)) {
            RailPath.Closest closest = p.path().closest(feet);
            double d = closest.horizontal() + Math.abs(closest.above());
            if (d < bestDistance) {
                bestDistance = d;
                best = p;
            }
        }
        return best == null ? GrindMaterial.METAL : best.material();
    }

    /**
     * The variant: the local controller's own on a rail; otherwise, from the board heading against
     * the direction of travel, the same angle bands (section 8), so remote riders and box slides
     * pick the same clip.
     */
    @Nullable
    private GrindPhysics.Variant variantNow(@Nullable SlideController c, double dx, double dz) {
        if (!state.isOnFeature()) return null;
        if (c != null && c.grind() != null) return c.grind().variant();
        double travel = Math.hypot(dx, dz) > 1.0e-3 ? SlideModel.yawOf(dx, dz) : heading;
        if (c != null && c.speed() > 1.0e-3) travel = c.travelYaw();
        return GrindPhysics.variant(heading - travel);
    }

    /** Body yaw: snowboarders face sideways to the board (stance picks the side), skiers face along it. */
    double targetBodyYaw() {
        if (mode == RideMode.BOARD) return heading + (goofy ? -90 : 90);
        return heading;
    }
}
