package io.github.adang101.alpineshred.lift;

/** Why a boarding attempt is refused, decided over plain numbers (design doc section 13, "Boarding"). */
public final class BoardingRule {
    public enum Refusal {
        NONE, TOO_FAR, FULL, TOO_FAST, NO_PASS
    }

    private BoardingRule() {
    }

    /** The first reason that applies, in the order distance, seat, speed, pass. */
    public static Refusal check(double distance, double maxDistance, boolean freeSeat, double speed, double maxSpeed,
                                boolean requirePass, boolean hasPass) {
        if (distance > maxDistance) return Refusal.TOO_FAR;
        if (!freeSeat) return Refusal.FULL;
        if (speed > maxSpeed) return Refusal.TOO_FAST;
        if (requirePass && !hasPass) return Refusal.NO_PASS;
        return Refusal.NONE;
    }
}
