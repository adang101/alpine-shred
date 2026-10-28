package io.github.adang101.alpineshred.movement;

import io.github.adang101.alpineshred.gear.GearProfile;
import io.github.adang101.alpineshred.network.SyncCodec;
import net.minecraft.world.entity.player.Player;

/**
 * Runs a {@link SlideController} on a real player for one tick at a time: sets the per-player flags
 * the mixins read (controlled, step height, powder float), ticks the controller over a
 * {@link PlayerBody}, and leaves the result in the player's position and delta movement.
 *
 * <p>The client driver uses this for the local player, and the game tests use it with scripted
 * input on test players, so both exercise the same code against real collision (section 19).
 */
public final class RideRunner {
    private final PlayerBody body;
    private SlideController controller = new SlideController();

    public RideRunner(Player player) {
        this.body = new PlayerBody(player);
    }

    public Player player() {
        return body.player();
    }

    public PlayerBody body() {
        return body;
    }

    public SlideController controller() {
        return controller;
    }

    /** The controller context for this rider's gear and the server's settings. */
    public static RideContext context(GearProfile gear, SyncCodec.Settings server, SlideModel.SteerMode steer) {
        return new RideContext(TuningData.tuning(), gear.stats(), gear.edge(), gear.wax() > 0,
                server.maxSpeedMul(), steer, server.scrapeEnabled());
    }

    /**
     * One tick. Returns true when the controller moved the player, in which case vanilla travel
     * must not run this tick; false means walking, and vanilla moves the player.
     */
    public boolean tick(RideInput input, RideContext ctx) {
        Player player = body.player();
        RideStateHolder holder = (RideStateHolder) player;
        MovementTuning t = ctx.tuning();

        boolean riding = controller.mode() == SlideController.Mode.RIDE;
        double speed = riding ? controller.speed() : Math.hypot(player.getDeltaMovement().x, player.getDeltaMovement().z);
        SurfaceProfile surface = riding && controller.surface() != null ? controller.surface() : body.surfaceUnderFeet();
        double floatSpeed = SlideModel.powderFloatSpeed(t, ctx.gear());

        holder.alpineshred$setControlled(true);
        holder.alpineshred$setRideStep((float) GroundContact.stepHeight(t, surface != null && surface.isSnow(), speed));
        holder.alpineshred$setFloatsOnPowder(speed >= floatSpeed);

        SlideController.Mode mode = controller.tick(body, input, ctx);
        if (mode == SlideController.Mode.WALK) {
            clearFlags();
            return false;
        }
        holder.alpineshred$setFloatsOnPowder(controller.speed() >= floatSpeed);
        player.setDeltaMovement(controller.velocityX(), controller.verticalVelocity(), controller.velocityZ());
        player.walkAnimation.update(0.0f, 0.4f);
        return true;
    }

    /** Stops driving: flags off, controller back to walking. */
    public void release() {
        clearFlags();
        controller = new SlideController();
    }

    private void clearFlags() {
        RideStateHolder holder = (RideStateHolder) body.player();
        holder.alpineshred$setControlled(false);
        holder.alpineshred$setRideStep(0);
        holder.alpineshred$setFloatsOnPowder(false);
    }
}
