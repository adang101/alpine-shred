package io.github.adang101.alpineshred.client.feel;

import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import io.github.adang101.alpineshred.movement.RideState;
import io.github.adang101.alpineshred.movement.Riders;
import io.github.adang101.alpineshred.movement.SlideModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Drives everything a rider looks and sounds like, once per client tick for every player within
 * {@value #RANGE} blocks of the camera (design doc sections 9 and 17): reads each rider into a
 * {@link RiderView}, then animation, body facing, sounds and particles; then the local rider's
 * camera and HUD effects.
 */
public final class RiderTracker {
    public static final double RANGE = 48;
    private static final int RELEASE_AFTER_OFF_TICKS = 40;

    private static final Map<AbstractClientPlayer, RiderView> VIEWS = new WeakHashMap<>();
    @Nullable
    private static ClientLevel trackedLevel;
    @Nullable
    private static String previewClip;

    private RiderTracker() {
    }

    public static void register() {
        ClientTickEvent.CLIENT_POST.register(RiderTracker::tick);
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> clear());
        ClientPlayerEvent.CLIENT_PLAYER_RESPAWN.register((oldPlayer, newPlayer) -> clear());
    }

    /**
     * Plays {@code clip} on the local rider in place of the one the ride state picks, or goes back
     * to the ride state with null. For the smoke test's pose gallery; clip ids are {@link
     * io.github.adang101.alpineshred.feel.RiderClips} names.
     */
    public static void previewClip(@Nullable String clip) {
        previewClip = clip;
    }

    /** The clip this client plays for {@code player} now, or "-" (the multiplayer smoke test logs it). */
    public static String clipOf(net.minecraft.world.entity.player.Player player) {
        RiderView view = player instanceof AbstractClientPlayer p ? VIEWS.get(p) : null;
        return view == null || view.clip == null ? "-" : view.clip;
    }

    @Nullable
    static RiderView localView() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? null : VIEWS.get(player);
    }

    private static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        LocalPlayer local = mc.player;
        if (level != trackedLevel) {
            clear();
            trackedLevel = level;
        }
        if (level == null || local == null) return;
        if (mc.isPaused()) return;
        Entity camera = mc.getCameraEntity() != null ? mc.getCameraEntity() : local;

        for (AbstractClientPlayer player : level.players()) {
            double distanceSq = player.distanceToSqr(camera);
            boolean inRange = player == local || distanceSq <= RANGE * RANGE;
            RiderView view = VIEWS.get(player);
            if (view == null) {
                if (!inRange || !Riders.state(player).isStrapped()) continue;
                view = new RiderView(player, player == local);
                VIEWS.put(player, view);
            } else if (!inRange) {
                release(view);
                VIEWS.remove(player);
                continue;
            }
            view.observe();
            if (player == local && previewClip != null) view.clip = previewClip;
            RiderAnimator.update(view);
            faceBody(view);
            RiderSoundManager.update(view, distanceSq);
            RiderParticles.update(view);
            if (view.offTicks > RELEASE_AFTER_OFF_TICKS && player != local) {
                release(view);
                VIEWS.remove(player);
            }
        }
        List<AbstractClientPlayer> gone = new ArrayList<>();
        for (AbstractClientPlayer player : VIEWS.keySet()) {
            if (player.isRemoved() || player.level() != level) gone.add(player);
        }
        for (AbstractClientPlayer player : gone) release(VIEWS.remove(player));

        RideEffects.tick(local, VIEWS.get(local));
    }

    /**
     * Turns the rendered body to the board (sideways by stance) or skis, smoothing remote riders'
     * coarse synced heading. Runs after the entity tick, so it has the last word on the body yaw;
     * vanilla's own interpolation between ticks keeps it smooth.
     */
    private static void faceBody(RiderView view) {
        if (!view.state.isStrapped()) {
            view.bodyYawSet = false;
            return;
        }
        double target = view.targetBodyYaw();
        if (!view.bodyYawSet || view.local) {
            view.bodyYaw = target;
            view.bodyYawSet = true;
        } else {
            view.bodyYaw += SlideModel.wrapDegrees(target - view.bodyYaw) * 0.5;
        }
        view.player.yBodyRot = (float) view.bodyYaw;
    }

    private static void release(@Nullable RiderView view) {
        if (view == null) return;
        RiderAnimator.detach(view.player);
        RiderSoundManager.stopAll(view.player);
    }

    private static void clear() {
        for (RiderView view : new ArrayList<>(VIEWS.values())) release(view);
        VIEWS.clear();
        RiderSoundManager.clear();
        RiderAnimator.clear();
        RideEffects.reset();
    }

    /** True for state the camera and HUD treat as riding. */
    static boolean riding(@Nullable RiderView view) {
        return view != null && view.state.isControllerDriven() && view.state != RideState.IDLE;
    }
}
