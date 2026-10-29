package io.github.adang101.alpineshred.movement;

/**
 * Vanilla vertical motion for an airborne rider: each tick the entity moves by its velocity,
 * then {@code vy = (vy - 0.08) * 0.98}. Airborne horizontal velocity is preserved
 * ({@code airControl = 0}, section 7).
 */
public final class Ballistics {
    private Ballistics() {
    }

    public static double nextVerticalVelocity(double vy) {
        return (vy - SlideModel.VANILLA_GRAVITY) * SlideModel.VANILLA_VERTICAL_DRAG;
    }

    /** The velocity one tick earlier: the inverse of {@link #nextVerticalVelocity}. */
    public static double previousVerticalVelocity(double vy) {
        return vy / SlideModel.VANILLA_VERTICAL_DRAG + SlideModel.VANILLA_GRAVITY;
    }

    /** Height gained before the apex for a launch at {@code vy0}. */
    public static double apexHeight(double vy0) {
        double y = 0;
        double vy = vy0;
        double best = 0;
        for (int i = 0; i < 400 && vy > 0; i++) {
            y += vy;
            best = Math.max(best, y);
            vy = nextVerticalVelocity(vy);
        }
        return best;
    }

    /** Ticks in the air until the rider is back at or below {@code landingDrop} under the launch height. */
    public static int airTicks(double vy0, double landingDrop) {
        double y = 0;
        double vy = vy0;
        for (int tick = 1; tick <= 2000; tick++) {
            y += vy;
            vy = nextVerticalVelocity(vy);
            if (y <= -landingDrop) return tick;
        }
        return 2000;
    }
}
