package io.github.adang101.alpineshred.trick;

/**
 * One scored piece of a trick (design doc section 8, "Trick catalog"): a spin, a flip, a grab, a
 * grind or slide, air time or height. {@code id} names the trick type for the repeat rule and the
 * popup ({@code spin_360}, {@code grab_indy}, {@code grind_5050_switch}); {@code category} feeds
 * the variety multiplier, null for air time and height, which count toward no category.
 */
public record TrickPart(String id, Category category, double points) {
    public enum Category {SPIN, FLIP, GRAB, GRIND}

    public TrickPart {
        if (points < 0 || Double.isNaN(points)) throw new IllegalArgumentException("points " + points);
    }
}
