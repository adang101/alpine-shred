package io.github.adang101.alpineshred.trick;

import java.util.List;

/**
 * What the server made of a landing, grind entry or bank ({@code s2c/trick_result}, sections 8 and
 * 15). The client's popup shows exactly these numbers; it never scores anything itself.
 *
 * @param points     what this landing added, before the combo's multipliers
 * @param lines      the tricks in it, with the points each scored (repeats already halved)
 * @param multiplier the combo's variety times combo multiplier so far
 * @param comboTotal the combo's score so far, rounded and capped (the banked total when {@code banked})
 * @param landings   landings and grind entries in the combo so far
 * @param banked     the combo has ended and its total is banked
 * @param bail       the landing was a bail: the combo in progress is lost
 */
public record TrickResult(int points, List<Line> lines, LandingQuality quality, boolean switchLanding, double multiplier,
                          int comboTotal, int landings, boolean banked, boolean bail) {
    public record Line(String id, int points) {
    }

    public TrickResult {
        lines = List.copyOf(lines);
    }

    public static TrickResult bank(int total, double multiplier, int landings) {
        return new TrickResult(0, List.of(), LandingQuality.CLEAN, false, multiplier, total, landings, true, false);
    }

    public static TrickResult bailed() {
        return new TrickResult(0, List.of(), LandingQuality.BAIL, false, 1, 0, 0, false, true);
    }
}
