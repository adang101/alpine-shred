package io.github.adang101.alpineshred.lift;

/**
 * How a lift line moves (design doc section 13, "State and persistence"): each tick its speed ramps
 * toward the drive's target by 0.5 blocks per second squared, or 2.0 in an emergency stop, then
 * {@code travel} advances by {@code speed / 20}. Pure, so the server's line and a client's copy of
 * it step the same way; a client that receives a line's state catches up with {@link #advance}.
 */
public final class LiftMotion {
    public static final double ACCELERATION = 0.5;
    public static final double EMERGENCY_DECELERATION = 2.0;
    public static final int TICKS_PER_SECOND = 20;

    private LiftMotion() {
    }

    /** The speed one tick later, in blocks per second. */
    public static double nextSpeed(double speed, double target, boolean emergency) {
        double rate = (emergency ? EMERGENCY_DECELERATION : ACCELERATION) / TICKS_PER_SECOND;
        if (speed < target) return Math.min(target, speed + rate);
        if (speed > target) return Math.max(target, speed - rate);
        return speed;
    }

    /** One tick of a line: {speed, travel} after it, with {@code travel} folded into the loop. */
    public static double[] step(double speed, double travel, double target, boolean emergency, double loopLength) {
        double s = nextSpeed(speed, target, emergency);
        double t = travel + s / TICKS_PER_SECOND;
        if (loopLength > 0) {
            t %= loopLength;
            if (t < 0) t += loopLength;
        }
        return new double[]{s, t};
    }

    /** {@code ticks} ticks of a line, tick by tick, as the server runs them: {speed, travel}. */
    public static double[] advance(double speed, double travel, double target, boolean emergency, double loopLength, long ticks) {
        double[] state = {speed, travel};
        for (long i = 0; i < ticks; i++) {
            // Once at its target the line moves at a constant speed: the rest is one multiplication,
            // then the same fold the stepping does.
            if (state[0] == target) {
                double t = state[1] + state[0] / TICKS_PER_SECOND * (ticks - i);
                if (loopLength > 0) {
                    t %= loopLength;
                    if (t < 0) t += loopLength;
                }
                return new double[]{state[0], t};
            }
            state = step(state[0], state[1], target, emergency, loopLength);
        }
        return state;
    }
}
