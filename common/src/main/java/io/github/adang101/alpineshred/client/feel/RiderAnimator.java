package io.github.adang101.alpineshred.client.feel;

import dev.kosmx.playerAnim.api.layered.AnimationStack;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.feel.RiderClips;
import io.github.adang101.alpineshred.movement.RideMode;
import net.minecraft.client.player.AbstractClientPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * {@code RiderAnimator} (design doc section 9): the only class that talks to PlayerAnimator, so the
 * library can be swapped for the 1.21 port. Every client animates every tracked rider from the
 * synced state; there are no animation packets.
 *
 * <p>Layers, low to high priority: the base clip ({@link RiderClips}), cross-faded over
 * {@value #FADE_TICKS} ticks; the procedural {@link LeanLayer}; the hands layer for poles. The trick
 * layer (grabs, flips) arrives in phase 5. Layers are attached lazily when a player first straps in
 * and detached when they leave tracking range or the world.
 */
final class RiderAnimator {
    static final int BASE_PRIORITY = 1000;
    static final int LEAN_PRIORITY = 1010;
    static final int HANDS_PRIORITY = 1030;
    static final int FADE_TICKS = 4;

    private static final Map<AbstractClientPlayer, Rig> RIGS = new WeakHashMap<>();
    private static final Set<String> MISSING_LOGGED = new HashSet<>();
    private static boolean libraryBroken;

    private RiderAnimator() {
    }

    private static final class Rig {
        final ModifierLayer<IAnimation> base = new ModifierLayer<>();
        final ModifierLayer<IAnimation> hands = new ModifierLayer<>();
        final LeanLayer lean;
        String baseClip;
        String handsClip;
        int handsOneShot;
        boolean leftNext;

        Rig(AbstractClientPlayer player) {
            lean = new LeanLayer(player, base);
        }
    }

    static void update(RiderView view) {
        if (libraryBroken) return;
        Rig rig = RIGS.get(view.player);
        if (rig == null) {
            if (!view.state.isStrapped()) return;
            rig = attach(view.player);
            if (rig == null) return;
        }
        if (!Objects.equals(view.clip, rig.baseClip)) {
            rig.baseClip = view.clip;
            play(rig.base, view.clip);
        }
        rig.lean.update(view.leanPose, view.state.isStrapped());

        String hands = null;
        if (rig.handsOneShot > 0) {
            rig.handsOneShot--;
            hands = rig.handsClip;
        } else if (view.mode == RideMode.SKIS && view.holdingPoles && view.state.isGroundRiding()) {
            hands = RiderClips.POLE_CARRY;
        }
        if (view.polePushed && view.mode == RideMode.SKIS) {
            hands = rig.leftNext ? RiderClips.POLE_PUSH_LEFT : RiderClips.POLE_PUSH_RIGHT;
            rig.leftNext = !rig.leftNext;
            rig.handsOneShot = RiderClips.POLE_PUSH_TICKS;
            rig.handsClip = null; // force a restart even if the same side repeats
        }
        if (!Objects.equals(hands, rig.handsClip)) {
            rig.handsClip = hands;
            play(rig.hands, hands);
        }
    }

    @Nullable
    private static Rig attach(AbstractClientPlayer player) {
        try {
            AnimationStack stack = PlayerAnimationAccess.getPlayerAnimLayer(player);
            Rig rig = new Rig(player);
            stack.addAnimLayer(BASE_PRIORITY, rig.base);
            stack.addAnimLayer(LEAN_PRIORITY, rig.lean);
            stack.addAnimLayer(HANDS_PRIORITY, rig.hands);
            RIGS.put(player, rig);
            return rig;
        } catch (RuntimeException e) {
            libraryBroken = true;
            AlpineShred.LOGGER.error("PlayerAnimator is not working; rider animation is off", e);
            return null;
        }
    }

    static void detach(AbstractClientPlayer player) {
        Rig rig = RIGS.remove(player);
        if (rig == null) return;
        try {
            AnimationStack stack = PlayerAnimationAccess.getPlayerAnimLayer(player);
            stack.removeLayer(rig.base);
            stack.removeLayer(rig.lean);
            stack.removeLayer(rig.hands);
        } catch (RuntimeException ignored) {
            // The player object is gone with its stack.
        }
    }

    static void clear() {
        RIGS.clear();
    }

    private static void play(ModifierLayer<IAnimation> layer, @Nullable String clip) {
        IAnimation next = clip == null ? null : player(clip);
        if (next != null) FeelStats.clipsPlayed++;
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(FADE_TICKS, Ease.INOUTSINE), next);
    }

    @Nullable
    private static IAnimation player(String clip) {
        KeyframeAnimation animation = PlayerAnimationRegistry.getAnimation(AlpineShred.id(clip));
        if (animation == null) {
            if (MISSING_LOGGED.add(clip)) {
                AlpineShred.LOGGER.warn("Rider clip {} is missing; using {}", clip, RiderClips.FALLBACK);
            }
            animation = PlayerAnimationRegistry.getAnimation(AlpineShred.id(RiderClips.FALLBACK));
        }
        return animation == null ? null : new KeyframeAnimationPlayer(animation);
    }
}
