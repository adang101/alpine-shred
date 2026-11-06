package io.github.adang101.alpineshred.lift;

/**
 * When a strapped player standing at the bottom station boards a passing chair on their own (design doc
 * section 13, "Boarding"). Pure, so it unit-tests.
 *
 * <p><b>"Standing in the boarding stretch"</b> is defined through the chair: the player must be within
 * {@link #HORIZONTAL} blocks horizontally and {@link #VERTICAL} blocks vertically of the chair's seat, and
 * that chair's loop distance {@code s} must lie in the line's boarding stretch
 * ({@link LiftStations.Result#inBoarding}, the first 4 blocks of loaded cable). The stretch is a stretch
 * of cable, not of floor, so a player near the cable points of the stretch is exactly a player a chair
 * in the stretch can reach; no extra floor test is needed.
 *
 * <p>The server re-checks with {@link #SERVER_SLACK} blocks of extra room, since the client's chair is a
 * few ticks behind or ahead of the server's.
 */
public final class AutoBoard {
    public static final double HORIZONTAL = 1.2;
    public static final double VERTICAL = 2.5;
    /** Extra reach the server allows on top of the client's tolerances, for latency. */
    public static final double SERVER_SLACK = 1.5;

    private AutoBoard() {
    }

    /**
     * @param strapped               the player is strapped in
     * @param riding                 the player already rides something
     * @param horizontal             horizontal distance from the player to the chair's seat
     * @param vertical               the seat's height above the player's feet (negative when below)
     * @param freeSeat               the chair has a free seat
     * @param chairInBoardingStretch the chair's {@code s} lies inside the boarding stretch
     */
    public static boolean shouldBoard(boolean strapped, boolean riding, double horizontal, double vertical,
                                      boolean freeSeat, boolean chairInBoardingStretch) {
        return strapped && !riding && freeSeat && chairInBoardingStretch && withinReach(horizontal, vertical);
    }

    /** Whether a chair at these offsets is close enough for the client to try. */
    public static boolean withinReach(double horizontal, double vertical) {
        return horizontal <= HORIZONTAL && Math.abs(vertical) <= VERTICAL;
    }

    /** Whether a chair at these offsets is close enough for the server to accept an auto-board packet. */
    public static boolean serverAccepts(double horizontal, double vertical) {
        return horizontal <= HORIZONTAL + SERVER_SLACK && Math.abs(vertical) <= VERTICAL + SERVER_SLACK;
    }
}
