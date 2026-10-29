package io.github.adang101.alpineshred.trick;

/**
 * The server's bounds on trick reports (design doc section 8, "Server validation"). A report that
 * breaks one is rejected outright: no points, and the reason counts as a ride violation. Air time
 * is the server's own count of the session, never the client's.
 */
public final class TrickValidator {
    /** Extra ticks of air the server allows over its own count, for packet timing at both ends. */
    public static final int AIR_TICK_SLACK = 4;
    /** Extra apex over what the server saw. */
    public static final double APEX_SLACK = 0.75;

    private TrickValidator() {
    }

    /**
     * @param observedAirTicks the air session's length as the server counted it
     * @param observedApex     the highest the server saw the rider above the landing point
     * @param spinMax          degrees per tick, 24 on a board, 28 on skis
     * @param flipMax          degrees per tick
     * @return null if the report is plausible, else the reason
     */
    public static String check(AirReport r, int observedAirTicks, double observedApex, double spinMax, double flipMax) {
        int air = Math.min(r.airTicks(), observedAirTicks + AIR_TICK_SLACK);
        if (r.airTicks() > observedAirTicks + AIR_TICK_SLACK) {
            return "air report: " + r.airTicks() + " air ticks, server saw " + observedAirTicks;
        }
        // Rule 1: spin within the rate the gear allows over the air time.
        if (Math.abs(r.yawAccum()) > air * spinMax * 1.1 + 30) {
            return String.format(java.util.Locale.ROOT, "air report: spin %.0f in %d ticks", r.yawAccum(), air);
        }
        // Flips at the flip rate, and (rule 2) whole flips need 16 ticks each.
        if (Math.abs(r.pitchAccum()) > air * flipMax * 1.1 + 30 || TrickAngles.flips(r.pitchAccum()) > air / 16) {
            return String.format(java.util.Locale.ROOT, "air report: flip %.0f in %d ticks", r.pitchAccum(), air);
        }
        // Rule 3: no grab longer than the air.
        for (GrabType type : GrabType.values()) {
            if (r.grab(type) < 0 || r.grab(type) > air) return "air report: " + type + " held " + r.grab(type) + " of " + air + " ticks";
        }
        // Rule 4: the landing heading agrees with the spin, modulo 180.
        double expected = TrickAngles.headingError(r.takeoffRelative(), r.yawAccum());
        if (Math.abs(expected - r.headingError()) > 20) {
            return String.format(java.util.Locale.ROOT, "air report: heading error %.0f, spin says %.0f", r.headingError(), expected);
        }
        double expectedFlip = TrickAngles.residual(r.pitchAccum(), 360);
        if (Math.abs(expectedFlip - r.flipResidual()) > 20) {
            return String.format(java.util.Locale.ROOT, "air report: flip residual %.0f, flip says %.0f", r.flipResidual(), expectedFlip);
        }
        if (r.apex() > observedApex + APEX_SLACK) {
            return String.format(java.util.Locale.ROOT, "air report: apex %.1f, server saw %.1f", r.apex(), observedApex);
        }
        if (r.headingError() < 0 || r.headingError() > 90 || r.flipResidual() < 0 || r.flipResidual() > 180 || Double.isNaN(r.yawAccum())
                || Double.isNaN(r.pitchAccum()) || Double.isNaN(r.apex())) {
            return "air report: values out of range";
        }
        return null;
    }

    /**
     * A grind report: the rail or box exists (the caller checks the world), the time on it is no
     * more than the server saw, and the length agrees with the speed cap over that time.
     */
    public static String check(GrindReport g, int observedTicks, double speedCap) {
        if (g.durationTicks() <= 0 || g.durationTicks() > observedTicks + AIR_TICK_SLACK) {
            return "grind report: " + g.durationTicks() + " ticks, server saw " + observedTicks;
        }
        if (g.lengthBlocks() < 0 || g.lengthBlocks() > g.durationTicks() * speedCap + 1.0) {
            return String.format(java.util.Locale.ROOT, "grind report: %.1f blocks in %d ticks", g.lengthBlocks(), g.durationTicks());
        }
        if (g.variant() < 0 || g.variant() > 3 || g.switchUps() < 0 || g.switchUps() > g.durationTicks() / 20 + 1
                || g.entryAxis() < 0 || g.entryAxis() > 90) {
            return "grind report: values out of range";
        }
        return null;
    }
}
