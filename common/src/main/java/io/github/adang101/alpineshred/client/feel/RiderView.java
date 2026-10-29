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
                }
            }
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
            }
            // A pole push swings the arm (the client that pushed sends the swing to everyone).
            boolean swinging = player.swinging;
            if (swinging && !wasSwinging && holdingPoles && mode == RideMode.SKIS && state.isGroundRiding()) polePushed = true;
            wasSwinging = swinging;
        }
        if (local && state != previous && state == RideState.WIPEOUT) wipedOut = true;

        surface = state.isStrapped() ? body.surfaceUnderFeet() : null;
        blockUnder = state.isStrapped() ? player.level().getBlockState(BlockPos.containing(player.getX(), player.getY() - 0.2, player.getZ())) : null;
        offTicks = state.isStrapped() ? 0 : offTicks + 1;

        clip = director.tick(state, mode, goofy, speed, charge);
        LeanRig.Pose target = state.isStrapped() && state != RideState.WIPEOUT
                ? LeanRig.pose(mode, goofy, switchStance, lean, speed, charge, grounded)
                : LeanRig.Pose.NEUTRAL;
        leanPose = LeanRig.Pose.lerp(leanPose, target, local ? 0.5 : 0.35);
    }

    /** Body yaw: snowboarders face sideways to the board (stance picks the side), skiers face along it. */
    double targetBodyYaw() {
        if (mode == RideMode.BOARD) return heading + (goofy ? -90 : 90);
        return heading;
    }
}
