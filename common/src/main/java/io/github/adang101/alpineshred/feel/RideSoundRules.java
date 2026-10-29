package io.github.adang101.alpineshred.feel;

import io.github.adang101.alpineshred.movement.RideState;
import io.github.adang101.alpineshred.movement.SurfaceProfile;
import io.github.adang101.alpineshred.park.GrindMaterial;

/**
 * Volume and pitch of a rider's sound loops (design doc section 17), as a pure function of the
 * rider's state, surface, speed and lean, so the local rider and remote riders sound the same.
 * The client's {@code RiderSoundManager} plays one loop per entry of {@link Loop} that is audible,
 * and moves each loop's volume toward its target by {@link #fadeStep} every tick.
 */
public final class RideSoundRules {
    /** Cross-fade length for surface changes, in ticks. */
    public static final int FADE_TICKS = 4;
    /** Loudest any loop gets, so the fade step is a fixed fraction of it. */
    public static final double MAX_VOLUME = 0.8;

    public enum Loop {
        GLIDE_SNOW, GLIDE_ICE, GLIDE_POWDER, SCRAPE, CARVE, BRAKE,
        /** Grinds and box slides (phase 4), by what the rail or box is made of. */
        GRIND_METAL, GRIND_COPPER, BOX_WOOD, BOX_METAL, BOX_PLASTIC;

        private static final Loop[] VALUES = values();

        public static Loop[] all() {
            return VALUES;
        }
    }

    /** Per-loop targets, indexed by {@link Loop#ordinal()}. */
    public static final class Mix {
        public final double[] volume = new double[Loop.VALUES.length];
        public final double[] pitch = new double[Loop.VALUES.length];

        public double volume(Loop loop) {
            return volume[loop.ordinal()];
        }

        public double pitch(Loop loop) {
            return pitch[loop.ordinal()];
        }

        void set(Loop loop, double v, double p) {
            volume[loop.ordinal()] = clamp(v, 0, MAX_VOLUME);
            pitch[loop.ordinal()] = clamp(p, 0.5, 2.0);
        }

        void clear() {
            java.util.Arrays.fill(volume, 0);
            java.util.Arrays.fill(pitch, 1);
        }
    }

    private RideSoundRules() {
    }

    /**
     * @param surface the surface under the rider, or null when there is none
     * @param speed   horizontal speed in blocks per tick
     * @param lean    -1 to 1, positive toward a right turn
     */
    public static void targets(RideState state, SurfaceProfile.Category surface, double speed, double lean, Mix out) {
        targets(state, surface, null, speed, lean, out);
    }

    /**
     * @param material what the rail (while grinding) or box (while sliding) is made of, or null
     */
    public static void targets(RideState state, SurfaceProfile.Category surface, GrindMaterial material, double speed, double lean, Mix out) {
        out.clear();
        if (state == RideState.GRIND || state == RideState.SLIDE) {
            // Section 17: grind and slide loops, pitch by speed.
            if (speed <= 0.01) return;
            Loop loop = grindLoop(material == null ? GrindMaterial.METAL : material);
            out.set(loop, clamp(speed / 0.6, 0, 1) * 0.7, 0.8 + speed * 0.4);
            return;
        }
        boolean sliding = switch (state) {
            case IDLE, GLIDE, CARVE, BRAKE, POWDER -> true;
            default -> false; // OFF, AIR, WIPEOUT, LIFT
        };
        if (!sliding || surface == null || speed <= 0.01) return;
        double glide = clamp(speed / 0.8, 0, 1) * 0.6;
        double glidePitch = 0.8 + speed * 0.35;
        boolean braking = state == RideState.BRAKE;
        double glideShare = braking ? 0.5 : 1.0;
        switch (surface) {
            case SNOW -> out.set(Loop.GLIDE_SNOW, glide * glideShare, glidePitch);
            case ICE -> out.set(Loop.GLIDE_ICE, glide * 0.9 * glideShare, glidePitch + 0.1);
            case POWDER -> out.set(Loop.GLIDE_POWDER, glide * 0.85 * glideShare, glidePitch - 0.1);
            case SCRAPE, BOX -> out.set(Loop.SCRAPE, clamp(speed / 0.6, 0, 1) * 0.75, 0.75 + speed * 0.3);
        }
        if (surface != SurfaceProfile.Category.SCRAPE && surface != SurfaceProfile.Category.BOX) {
            if (state == RideState.CARVE) out.set(Loop.CARVE, Math.abs(lean) * speed * 1.5, 0.9 + speed * 0.2);
            if (braking) out.set(Loop.BRAKE, clamp(speed / 0.5, 0, 1) * 0.7, 0.9 + speed * 0.3);
        }
    }

    /** The loop for grinding or sliding on a material. */
    public static Loop grindLoop(GrindMaterial material) {
        return switch (material) {
            case METAL -> Loop.GRIND_METAL;
            case COPPER -> Loop.GRIND_COPPER;
            case WOOD -> Loop.BOX_WOOD;
            case PLASTIC -> Loop.BOX_PLASTIC;
            case STONE, CONCRETE -> Loop.BOX_METAL;
        };
    }

    /** One tick of the cross-fade: at most {@code MAX_VOLUME / FADE_TICKS} toward the target. */
    public static double fadeStep(double current, double target) {
        double step = MAX_VOLUME / FADE_TICKS;
        return current + clamp(target - current, -step, step);
    }

    /** Pop pitch rises with charge (0 to 1). */
    public static double popPitch(double charge) {
        return 0.9 + 0.35 * clamp(charge, 0, 1);
    }

    /** Hard landings: falling faster than about 3.5 blocks of drop. Phase 5 grades landings properly. */
    public static boolean hardLanding(double impactVelocity) {
        return impactVelocity < -0.75;
    }

    static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
