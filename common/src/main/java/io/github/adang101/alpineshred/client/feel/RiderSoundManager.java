package io.github.adang101.alpineshred.client.feel;

import dev.architectury.registry.registries.RegistrySupplier;
import io.github.adang101.alpineshred.feel.RideSoundRules;
import io.github.adang101.alpineshred.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * {@code RiderSoundManager} (design doc section 17): one looping sound per audible loop per rider
 * within {@value #RANGE} blocks, volume and pitch set every tick from {@link RideSoundRules}, surface
 * changes cross-faded over {@value RideSoundRules#FADE_TICKS} ticks, and the pop, landing, wipeout,
 * pole and rail-catch one-shots. Grinds and box slides loop by the rail's or box's
 * material. All of it comes from the synced rider state, so remote riders sound right without any
 * sound packets.
 */
final class RiderSoundManager {
    static final double RANGE = 32;

    private static final Map<AbstractClientPlayer, EnumMap<RideSoundRules.Loop, LoopSound>> LOOPS = new WeakHashMap<>();
    private static final RideSoundRules.Mix MIX = new RideSoundRules.Mix();

    private RiderSoundManager() {
    }

    static void update(RiderView view, double distanceSq) {
        boolean audible = distanceSq <= RANGE * RANGE;
        if (audible) {
            RideSoundRules.targets(view.state, view.surface == null ? null : view.surface.category(), view.material, view.speed, view.lean, MIX);
        } else {
            java.util.Arrays.fill(MIX.volume, 0);
        }
        EnumMap<RideSoundRules.Loop, LoopSound> loops = LOOPS.computeIfAbsent(view.player, p -> new EnumMap<>(RideSoundRules.Loop.class));
        for (RideSoundRules.Loop loop : RideSoundRules.Loop.all()) {
            LoopSound sound = loops.get(loop);
            if (sound != null && sound.isStopped()) {
                loops.remove(loop);
                sound = null;
            }
            double target = MIX.volume(loop);
            if (sound == null) {
                if (target <= 0) continue;
                sound = new LoopSound(view.player, event(loop));
                loops.put(loop, sound);
                Minecraft.getInstance().getSoundManager().play(sound);
                FeelStats.loopsStarted++;
            }
            sound.target = target;
            sound.targetPitch = MIX.pitch(loop);
        }
        if (!audible) return;

        if (view.popped) oneShot(view, ModSounds.POP, 0.8f, (float) RideSoundRules.popPitch(view.popCharge));
        if (view.spinStarted) oneShot(view, ModSounds.SPIN_WHOOSH, 0.5f, 0.9f + view.player.getRandom().nextFloat() * 0.2f);
        if (!Double.isNaN(view.landedImpact)) {
            // Section 17: soft for perfect and clean landings, hard for sketchy (or a hard impact).
            boolean hard = RideSoundRules.hardLanding(view.landedImpact)
                    || view.landedQuality == io.github.adang101.alpineshred.trick.LandingQuality.SKETCHY;
            oneShot(view, hard ? ModSounds.LAND_HARD : ModSounds.LAND_SOFT, hard ? 0.9f : 0.6f, 0.95f + view.player.getRandom().nextFloat() * 0.1f);
        }
        if (view.wipedOut) oneShot(view, ModSounds.WIPEOUT, 1.0f, 0.95f);
        if (view.caught) oneShot(view, ModSounds.GRIND_CATCH, 0.7f, 0.95f + view.player.getRandom().nextFloat() * 0.1f);
        if (view.polePushed) oneShot(view, ModSounds.POLE_PUSH, 0.6f, 0.9f + view.player.getRandom().nextFloat() * 0.2f);
    }

    static void stopAll(AbstractClientPlayer player) {
        EnumMap<RideSoundRules.Loop, LoopSound> loops = LOOPS.remove(player);
        if (loops != null) loops.values().forEach(s -> s.target = 0);
    }

    static void clear() {
        LOOPS.values().forEach(m -> m.values().forEach(s -> s.target = 0));
        LOOPS.clear();
    }

    private static void oneShot(RiderView view, RegistrySupplier<SoundEvent> sound, float volume, float pitch) {
        FeelStats.oneShotsPlayed++;
        view.player.level().playLocalSound(view.player.getX(), view.player.getY(), view.player.getZ(), sound.get(),
                SoundSource.PLAYERS, volume, pitch, false);
    }

    private static SoundEvent event(RideSoundRules.Loop loop) {
        return switch (loop) {
            case GLIDE_SNOW -> ModSounds.GLIDE_SNOW.get();
            case GLIDE_ICE -> ModSounds.GLIDE_ICE.get();
            case GLIDE_POWDER -> ModSounds.GLIDE_POWDER.get();
            case SCRAPE -> ModSounds.SCRAPE.get();
            case CARVE -> ModSounds.CARVE.get();
            case BRAKE -> ModSounds.BRAKE.get();
            case GRIND_METAL -> ModSounds.GRIND_RAIL_METAL.get();
            case GRIND_COPPER -> ModSounds.GRIND_RAIL_COPPER.get();
            case BOX_WOOD -> ModSounds.GRIND_BOX_WOOD.get();
            case BOX_METAL -> ModSounds.GRIND_BOX_METAL.get();
            case BOX_PLASTIC -> ModSounds.GRIND_BOX_PLASTIC.get();
        };
    }

    /** A loop that follows its rider and fades toward the target the manager sets each tick. */
    private static final class LoopSound extends AbstractTickableSoundInstance {
        private final AbstractClientPlayer rider;
        double target;
        double targetPitch = 1;
        private int silentTicks;

        LoopSound(AbstractClientPlayer rider, SoundEvent event) {
            super(event, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.rider = rider;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.0f;
            this.pitch = 1.0f;
            this.x = rider.getX();
            this.y = rider.getY();
            this.z = rider.getZ();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            if (rider.isRemoved()) {
                stop();
                return;
            }
            volume = (float) RideSoundRules.fadeStep(volume, target);
            pitch += (float) ((targetPitch - pitch) * 0.3);
            x = rider.getX();
            y = rider.getY();
            z = rider.getZ();
            silentTicks = volume <= 0.001f && target <= 0 ? silentTicks + 1 : 0;
            if (silentTicks > 2) stop();
        }
    }
}
