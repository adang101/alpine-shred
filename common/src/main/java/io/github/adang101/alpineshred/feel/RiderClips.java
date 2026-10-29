package io.github.adang101.alpineshred.feel;

import io.github.adang101.alpineshred.movement.RideMode;
import io.github.adang101.alpineshred.movement.RideState;
import io.github.adang101.alpineshred.park.GrindPhysics;
import io.github.adang101.alpineshred.trick.GrabType;
import io.github.adang101.alpineshred.trick.LandingQuality;

import java.util.List;

/**
 * Which base clip a rider plays (design doc section 9, "Clip list"), as a pure function of the
 * synced state plus what every client can observe: speed, whether the rider is walking, and the
 * state it was in before. Clip files live in {@code assets/alpineshred/player_animation/}; the
 * animator falls back to {@link #FALLBACK} if one is missing.
 *
 * <p>Grind and slide clips arrived with phase 4, grab and graded landing clips with phase 5; lift
 * clips join with phase 8.
 */
public final class RiderClips {
    public static final String IDLE_BOARD = "idle_board";
    public static final String IDLE_SKIS = "idle_skis";
    public static final String WALK_STRAPPED = "walk_strapped";
    public static final String GLIDE_BOARD_REGULAR = "glide_board_regular";
    public static final String GLIDE_BOARD_GOOFY = "glide_board_goofy";
    public static final String GLIDE_SKIS = "glide_skis";
    public static final String TUCK_BOARD = "tuck_board";
    public static final String TUCK_SKIS = "tuck_skis";
    public static final String BRAKE_BOARD = "brake_board";
    public static final String SNOWPLOW_SKIS = "snowplow_skis";
    public static final String POP_CHARGE = "pop_charge";
    public static final String POP_LAUNCH = "pop_launch";
    public static final String AIR_BOARD = "air_board";
    public static final String AIR_SKIS = "air_skis";
    public static final String LAND_PERFECT = "land_perfect";
    public static final String LAND_CLEAN = "land_clean";
    public static final String LAND_SKETCHY = "land_sketchy";
    public static final String WIPEOUT_FRONT = "wipeout_front";
    public static final String WIPEOUT_BACK = "wipeout_back";
    public static final String POLE_PUSH_LEFT = "pole_push_left";
    public static final String POLE_PUSH_RIGHT = "pole_push_right";
    public static final String POLE_CARRY = "pole_carry";
    public static final String GRIND_5050 = "grind_5050";
    public static final String GRIND_NOSE = "grind_nose";
    public static final String GRIND_TAIL = "grind_tail";
    public static final String BOARDSLIDE = "boardslide";
    public static final String SLIDE_SKIS = "slide_skis";

    public static final String FALLBACK = GLIDE_BOARD_REGULAR;

    /** Every clip this phase plays: the files that must exist. */
    public static final List<String> ALL = List.of(IDLE_BOARD, IDLE_SKIS, WALK_STRAPPED, GLIDE_BOARD_REGULAR,
            GLIDE_BOARD_GOOFY, GLIDE_SKIS, TUCK_BOARD, TUCK_SKIS, BRAKE_BOARD, SNOWPLOW_SKIS, POP_CHARGE, POP_LAUNCH,
            AIR_BOARD, AIR_SKIS, LAND_PERFECT, LAND_CLEAN, LAND_SKETCHY, WIPEOUT_FRONT, WIPEOUT_BACK,
            POLE_PUSH_LEFT, POLE_PUSH_RIGHT, POLE_CARRY, GRIND_5050, GRIND_NOSE, GRIND_TAIL, BOARDSLIDE, SLIDE_SKIS,
            "grab_indy", "grab_mute", "grab_nose", "grab_tail", "grab_method",
            "grab_indy_skis", "grab_mute_skis", "grab_nose_skis", "grab_tail_skis", "grab_method_skis");

    /** Section 9's grab clips, one set each for board and skis: {@code grab_indy}, {@code grab_indy_skis}. */
    public static String grab(GrabType grab, boolean skis) {
        return "grab_" + grab.name().toLowerCase(java.util.Locale.ROOT) + (skis ? "_skis" : "");
    }

    /** The landing clip for a graded landing (section 9: quality picks the clip). */
    public static String landing(LandingQuality quality) {
        return switch (quality) {
            case PERFECT -> LAND_PERFECT;
            case SKETCHY -> LAND_SKETCHY;
            default -> LAND_CLEAN;
        };
    }

    /** One-shot lengths in ticks; the clip files are this long. */
    public static final int POP_LAUNCH_TICKS = 5;
    public static final int LAND_TICKS = 10;
    public static final int POLE_PUSH_TICKS = 10;

    /** Above this speed (blocks per tick) gliding riders tuck. */
    public static final double TUCK_SPEED = 1.1;
    /** Below this, an idle rider who is moving is walking. */
    public static final double WALK_SPEED = 0.03;

    private RiderClips() {
    }

    /** Hysteresis on the speed thresholds, so a rider near one does not flicker between clips. */
    public static final double TUCK_RELEASE = TUCK_SPEED - 0.1;
    public static final double WALK_RELEASE = WALK_SPEED / 2;

    /** The looping clip for a state, or null for no animation (not strapped). */
    public static String base(RideState state, RideMode mode, boolean goofy, double speed) {
        return base(state, mode, goofy, speed > WALK_SPEED, speed >= TUCK_SPEED);
    }

    public static String base(RideState state, RideMode mode, boolean goofy, boolean walking, boolean tucked) {
        return base(state, mode, goofy, walking, tucked, null);
    }

    /**
     * @param variant the grind or slide variant while on a rail or box (section 8's angle bands),
     *                or null to glide
     */
    public static String base(RideState state, RideMode mode, boolean goofy, boolean walking, boolean tucked, GrindPhysics.Variant variant) {
        boolean skis = mode == RideMode.SKIS;
        if ((state == RideState.GRIND || state == RideState.SLIDE) && variant != null) return grind(variant, skis, goofy);
        return switch (state) {
            case OFF -> null;
            case IDLE -> walking ? WALK_STRAPPED : skis ? IDLE_SKIS : IDLE_BOARD;
            case GLIDE, CARVE, POWDER, GRIND, SLIDE -> tucked
                    ? (skis ? TUCK_SKIS : TUCK_BOARD)
                    : skis ? GLIDE_SKIS : goofy ? GLIDE_BOARD_GOOFY : GLIDE_BOARD_REGULAR;
            case BRAKE -> skis ? SNOWPLOW_SKIS : BRAKE_BOARD;
            case AIR -> skis ? AIR_SKIS : AIR_BOARD;
            case WIPEOUT -> WIPEOUT_FRONT;
            case LIFT -> skis ? IDLE_SKIS : IDLE_BOARD;
        };
    }

    /**
     * Section 9's grind clips. The clips are authored for a regular stance; a goofy rider's nose is
     * on the other side, so nose and tail swap. Skis cross the rail on a boardslide.
     */
    public static String grind(GrindPhysics.Variant variant, boolean skis, boolean goofy) {
        return switch (variant) {
            case FIFTY_FIFTY -> GRIND_5050;
            case NOSE -> goofy ? GRIND_TAIL : GRIND_NOSE;
            case TAIL -> goofy ? GRIND_NOSE : GRIND_TAIL;
            case BOARDSLIDE -> skis ? SLIDE_SKIS : BOARDSLIDE;
        };
    }

    /**
     * Base clip with one-shots layered in time: a pop launch after leaving the ground, a landing
     * after touching down, and the pop charge while the local rider holds jump. One per rider.
     */
    public static final class Director {
        private RideState last = RideState.OFF;
        private String oneShot;
        private int oneShotTicks;
        private boolean walking;
        private boolean tucked;

        /** @param charge 0 to 1 while the local rider charges a pop; always 0 for other riders */
        public String tick(RideState state, RideMode mode, boolean goofy, double speed, double charge) {
            return tick(state, mode, goofy, speed, charge, null);
        }

        /** @param variant the grind or slide variant on a rail or box, else null */
        public String tick(RideState state, RideMode mode, boolean goofy, double speed, double charge, GrindPhysics.Variant variant) {
            return tick(state, mode, goofy, speed, charge, variant, GrabType.NONE);
        }

        /**
         * A graded landing (the local rider knows its own): the landing clip just started becomes the
         * one for that quality. A bail is a wipeout, which the state already plays.
         */
        public void landed(LandingQuality quality) {
            if (quality != LandingQuality.BAIL && oneShotTicks > 0 && LAND_CLEAN.equals(oneShot)) {
                oneShot = landing(quality);
                oneShotTicks = quality == LandingQuality.SKETCHY ? LAND_TICKS + 2 : LAND_TICKS;
            }
        }

        /** @param grab the grab held in the air, else NONE */
        public String tick(RideState state, RideMode mode, boolean goofy, double speed, double charge, GrindPhysics.Variant variant,
                           GrabType grab) {
            if (state != last) {
                if (state == RideState.AIR && (last.isGroundRiding() || last.isOnFeature())) {
                    start(POP_LAUNCH, POP_LAUNCH_TICKS);
                } else if (last == RideState.AIR && state.isGroundRiding()) {
                    start(LAND_CLEAN, LAND_TICKS);
                } else if (state == RideState.WIPEOUT || !state.isStrapped()) {
                    oneShot = null;
                    oneShotTicks = 0;
                }
                last = state;
            }
            walking = walking ? speed > WALK_RELEASE : speed > WALK_SPEED;
            tucked = tucked ? speed >= TUCK_RELEASE : speed >= TUCK_SPEED;
            if (oneShotTicks > 0) {
                oneShotTicks--;
                return oneShot;
            }
            if (charge > 0 && state.isGroundRiding()) return POP_CHARGE;
            if (state == RideState.AIR && grab != null && grab != GrabType.NONE) return RiderClips.grab(grab, mode == RideMode.SKIS);
            return base(state, mode, goofy, walking, tucked, variant);
        }

        private void start(String clip, int ticks) {
            oneShot = clip;
            oneShotTicks = ticks;
        }
    }
}
