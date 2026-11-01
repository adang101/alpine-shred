package io.github.adang101.alpineshred.lift;

/**
 * The parts of a line's loaded cable where riders get on and off (phase 8 plan, decision 4). Pure
 * (no Minecraft types), so it unit-tests.
 *
 * <ul>
 *   <li>The boarding stretch is {@code s in [0, 4]}: the first 4 blocks of loaded cable after the
 *       return wheel (shorter if the loaded cable is).
 *   <li>The unload stretch is the last part of the loaded cable before {@code loadedEnd} where the seat
 *       (cable point minus {@link LiftPath#CHAIR_HANG}) is within 3 blocks of the drive station's
 *       platform Y. It ends at {@code loadedEnd}. (The plan says "at most 3 blocks above"; read
 *       literally that is the whole climbing cable, since the drive is its highest point, so the band
 *       is two-sided: a seat far below the platform cannot unload either.)
 *   <li>The ejection point is 1 block before {@code loadedEnd}, clamped to the start of the loop.
 * </ul>
 */
public final class LiftStations {
    public static final double BOARDING_LENGTH = 4.0;
    public static final double UNLOAD_HEIGHT = 3.0;
    public static final double EJECT_BEFORE_END = 1.0;

    private LiftStations() {
    }

    /** The intervals, as loop distances {@code s}. */
    public record Result(double boardStart, double boardEnd, double unloadStart, double unloadEnd, double ejectS) {
        public boolean inBoarding(double s) {
            return s >= boardStart && s <= boardEnd;
        }

        public boolean inUnload(double s) {
            return s >= unloadStart && s <= unloadEnd;
        }
    }

    /** Computes the intervals for {@code path}, with {@code platformY} the drive station block's Y. */
    public static Result compute(LiftPath path, double platformY) {
        double end = path.loadedEnd();
        // Walk back from the end while the seat is low enough; refine the boundary by bisection.
        double start = end;
        if (low(path, end, platformY)) {
            double ok = end;
            for (int k = 1; ; k++) {
                double s = Math.max(0, end - k * LiftPath.STEP);
                if (low(path, s, platformY)) {
                    ok = s;
                    if (s == 0) break;
                    continue;
                }
                double bad = s;
                for (int i = 0; i < 40; i++) {
                    double mid = (ok + bad) / 2;
                    if (low(path, mid, platformY)) ok = mid;
                    else bad = mid;
                }
                break;
            }
            start = ok;
        }
        double eject = Math.max(0, end - EJECT_BEFORE_END);
        start = Math.max(0, Math.min(start, eject));
        return new Result(0, Math.min(BOARDING_LENGTH, end), start, end, eject);
    }

    /** The seat at {@code s} is within {@link #UNLOAD_HEIGHT} blocks of the platform's Y, above or below. */
    private static boolean low(LiftPath path, double s, double platformY) {
        return Math.abs(seatY(path, s) - platformY) <= UNLOAD_HEIGHT;
    }

    private static double seatY(LiftPath path, double s) {
        return path.pos(s).y() - LiftPath.CHAIR_HANG;
    }
}
