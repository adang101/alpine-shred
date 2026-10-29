package io.github.adang101.alpineshred.client;

import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import io.github.adang101.alpineshred.config.AlpineConfig;
import io.github.adang101.alpineshred.gear.GearProfiles;
import io.github.adang101.alpineshred.movement.RideContext;
import io.github.adang101.alpineshred.movement.RideHooks;
import io.github.adang101.alpineshred.movement.RideInput;
import io.github.adang101.alpineshred.movement.RideMode;
import io.github.adang101.alpineshred.movement.RideRunner;
import io.github.adang101.alpineshred.movement.RideSnapshot;
import io.github.adang101.alpineshred.movement.RideState;
import io.github.adang101.alpineshred.movement.Riders;
import io.github.adang101.alpineshred.movement.SlideController;
import io.github.adang101.alpineshred.movement.SlideModel;
import io.github.adang101.alpineshred.movement.StateSendThrottle;
import io.github.adang101.alpineshred.movement.StrapController;
import io.github.adang101.alpineshred.movement.TuningData;
import io.github.adang101.alpineshred.network.ModNetwork;
import io.github.adang101.alpineshred.network.RideAction;
import io.github.adang101.alpineshred.network.SyncCodec;
import io.github.adang101.alpineshred.trick.GrabType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives the local player's {@link SlideController} (design doc section 7): reads input in
 * {@code Player.travel}, runs the controller through a {@link RideRunner}, reports pops and launches
 * with {@code c2s/ride_action}, and sends the resulting state with {@code c2s/ride_state}.
 *
 * <p>The client never writes its own synced ride state. The server accepts the claim and echoes it
 * through synced data to everyone, this client included; {@link #localState} answers from the
 * controller so local code does not wait for the echo.
 */
public final class RideDriver implements RideHooks.ClientDriver {
    public static final RideDriver INSTANCE = new RideDriver();

    private final StateSendThrottle throttle = new StateSendThrottle();
    private final List<SlideController.Event> pendingEvents = new ArrayList<>();
    @Nullable
    private RideRunner runner;
    @Nullable
    private RideContext lastContext;
    private SyncCodec.Settings server = SyncCodec.Settings.DEFAULTS;
    private boolean protocolOk = true;
    private boolean pendingCorrection;
    private long ticks;

    private RideDriver() {
    }

    public void register() {
        RideHooks.setClientDriver(this);
        ClientTickEvent.CLIENT_POST.register(minecraft -> postTick());
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> disconnect());
        ClientPlayerEvent.CLIENT_PLAYER_RESPAWN.register((oldPlayer, newPlayer) -> release());
    }

    // ---- RideHooks.ClientDriver ---------------------------------------------------------------------

    @Override
    public boolean isLocal(Player player) {
        return player instanceof LocalPlayer && player == Minecraft.getInstance().player;
    }

    @Override
    public boolean travel(Player player) {
        LocalPlayer local = (LocalPlayer) player;
        if (!mayRide(local)) {
            release();
            return false;
        }
        RideRunner r = runnerFor(local);
        if (pendingCorrection) {
            r.controller().correct(TuningData.tuning());
            pendingCorrection = false;
        }
        RideContext ctx = RideRunner.context(GearProfiles.get(local), server, steerMode());
        lastContext = ctx;
        boolean moved = r.tick(input(local), ctx);
        if (moved) reportEvents(r.controller());
        pendingEvents.addAll(r.controller().events());
        // The -35% strapped modifier only matters for walking; while sliding, drop it locally so it
        // does not narrow the field of view (the server keeps it, and resyncs only when it changes).
        StrapController.applyWalkModifier(local, !moved);
        return moved;
    }

    @Override
    public boolean polePush(Player player) {
        RideRunner r = runner;
        if (r == null || r.player() != player || lastContext == null) return false;
        if (!r.controller().polePush(lastContext)) return false;
        ModNetwork.sendRideAction(RideAction.POLE_PUSH, 0);
        return true;
    }

    // ---- per tick ----------------------------------------------------------------------------------

    private boolean mayRide(LocalPlayer player) {
        RideState synced = Riders.state(player);
        return synced.isStrapped() && synced != RideState.LIFT
                && protocolOk && server.ridingEnabled()
                && !player.getAbilities().flying && !player.isPassenger() && !player.isSpectator()
                && !player.isFallFlying() && !player.isInWater() && !player.isInLava();
    }

    private RideRunner runnerFor(LocalPlayer player) {
        if (runner == null || runner.player() != player) {
            release();
            runner = new RideRunner(player);
        }
        return runner;
    }

    private static SlideModel.SteerMode steerMode() {
        return AlpineConfig.CLIENT_STEER_MODE.is("keys") ? SlideModel.SteerMode.KEYS : SlideModel.SteerMode.RIDE;
    }

    /** A is +leftImpulse in vanilla; the controller's strafe is positive toward D. */
    private static RideInput input(LocalPlayer player) {
        Input in = player.input;
        double strafe = in.leftImpulse > 0 ? -1 : in.leftImpulse < 0 ? 1 : 0;
        int forward = in.forwardImpulse > 0 ? 1 : in.forwardImpulse < 0 ? -1 : 0;
        return new RideInput(strafe, forward, player.getYRot(), in.shiftKeyDown, in.jumping, GrabType.NONE);
    }

    private static void reportEvents(SlideController controller) {
        for (SlideController.Event event : controller.events()) {
            if (event instanceof SlideController.Popped pop) {
                ModNetwork.sendRideAction(RideAction.POP, pop.chargeTicks());
            } else if (event instanceof SlideController.Launched launch) {
                ModNetwork.sendRideAction(RideAction.LAUNCH, (int) Math.round(launch.velocity() * 1000));
            }
        }
    }

    private void postTick() {
        ticks++;
        LocalPlayer player = Minecraft.getInstance().player;
        RideRunner r = runner;
        if (player == null || r == null) return;
        if (r.player() != player || !mayRide(player)) {
            release();
            return;
        }
        RideMode mode = Riders.get(player).mode();
        boolean goofy = mode == RideMode.BOARD && AlpineConfig.CLIENT_STANCE.is("goofy");
        int bits = r.controller().snapshot(mode, goofy).pack();
        if (throttle.shouldSend(ticks, bits)) ModNetwork.sendRideState(bits);
    }

    /** Controller events since the last call, for sounds and particles. */
    public List<SlideController.Event> drainEvents() {
        if (pendingEvents.isEmpty()) return List.of();
        List<SlideController.Event> out = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return out;
    }

    /** Stops driving the local player, back to vanilla movement. */
    public void release() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (runner != null && player != null && runner.player() == player && Riders.isStrapped(player)) {
            StrapController.applyWalkModifier(player, true);
        }
        pendingEvents.clear();
        if (runner != null) runner.release();
        runner = null;
        lastContext = null;
        throttle.reset();
    }

    private void disconnect() {
        release();
        server = SyncCodec.Settings.DEFAULTS;
        protocolOk = true;
        pendingCorrection = false;
        RideDebugOverlay.reset();
    }

    // ---- from the server -----------------------------------------------------------------------------

    void onConfigSync(int protocol, SyncCodec.Settings settings) {
        server = settings;
        boolean ok = protocol == SyncCodec.PROTOCOL_VERSION;
        if (!ok && protocolOk) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.alpineshred.protocol_mismatch",
                        protocol, SyncCodec.PROTOCOL_VERSION), false);
            }
        }
        protocolOk = ok;
    }

    void onCorrection() {
        pendingCorrection = true;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.translatable("message.alpineshred.ride.corrected"), true);
    }

    // ---- read-outs -----------------------------------------------------------------------------------

    /** The local rider's state from the controller, or the synced state when it is not driving. */
    public RideState localState(LocalPlayer player) {
        RideRunner r = runner;
        if (r != null && r.player() == player && Riders.isStrapped(player)) return r.controller().state();
        return Riders.state(player);
    }

    /** True while the controller (not vanilla walking) moves the local player. */
    public boolean sliding() {
        RideRunner r = runner;
        return r != null && r.controller().mode() == SlideController.Mode.RIDE;
    }

    @Nullable
    public SlideController controller() {
        return runner == null ? null : runner.controller();
    }

    public SyncCodec.Settings serverSettings() {
        return server;
    }

    public boolean protocolOk() {
        return protocolOk;
    }

    /** Snapshot the controller would send now, for the debug overlay. */
    @Nullable
    public RideSnapshot pendingSnapshot(LocalPlayer player) {
        RideRunner r = runner;
        if (r == null || r.player() != player) return null;
        RideMode mode = Riders.get(player).mode();
        return r.controller().snapshot(mode, mode == RideMode.BOARD && AlpineConfig.CLIENT_STANCE.is("goofy"));
    }
}
