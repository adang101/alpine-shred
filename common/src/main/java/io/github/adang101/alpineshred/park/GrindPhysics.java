package io.github.adang101.alpineshred.park;

import io.github.adang101.alpineshred.movement.MovementTuning;
import io.github.adang101.alpineshred.movement.SlideModel;

/**
 * Grinding (design doc section 10, "Grind detection", steps 2 to 5), as pure functions over
 * {@link GrindPath}s: catching a rail, riding along it, and passing from piece to piece. The
 * controller supplies the paths around the rider through {@link Lookup}.
 */
public final class GrindPhysics {
    /** Horizontal capture distance from the path. */
    public static final double CAPTURE_RADIUS = 0.45;
    /** Grind assist: the wider capture when the approach is nearly along the rail. */
    public static final double ASSIST_RADIUS = 0.65;
    public static final double ASSIST_ANGLE = 20;
    /** The feet may be this far below or above the path. */
    public static final double BELOW = 0.25;
    public static final double ABOVE = 0.45;
    public static final double MIN_SPEED = 0.08;
    public static final double ENTRY_LOSS = 0.05;
    public static final double MIN_ENTRY_SPEED = 0.05;
    /** Continuing onto the next piece: its end within this distance, its tangent within {@link #JOIN_ANGLE}. */
    public static final double JOIN_DISTANCE = 0.15;
    public static final double JOIN_ANGLE = 60;
    /** Grind variants by the board's angle to the rail axis (section 8). */
    public static final double FIFTY_FIFTY_MAX = 25;
    public static final double NOSE_TAIL_MAX = 65;
    /** At most this many pieces are crossed in one tick. */
    private static final int MAX_JOINS = 8;

    private GrindPhysics() {
    }

    /** Paths around a point: the pieces in and next to the blocks it touches. */
    public interface Lookup {
        Iterable<GrindPath> near(V3 point);
    }

    public enum Variant {
        FIFTY_FIFTY, NOSE, TAIL, BOARDSLIDE;

        public String serializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * A rider on a path.
     *
     * @param direction       +1 riding toward end B, -1 toward end A
     * @param relativeHeading board heading minus the travel direction's yaw, kept as the rail turns
     */
    public record Grind(GrindPath path, double s, int direction, double speed, double relativeHeading, Variant variant) {
        public V3 position() {
            return path.path().pointAt(s);
        }

        /** Unit direction of travel. */
        public V3 travel() {
            return path.path().tangentAt(s).scale(direction);
        }

        public double travelYaw() {
            V3 t = travel();
            return SlideModel.yawOf(t.x(), t.z());
        }

        public double heading() {
            return SlideModel.wrapDegrees(travelYaw() + relativeHeading);
        }

        public Grind withSpeed(double newSpeed) {
            return new Grind(path, s, direction, newSpeed, relativeHeading, variant);
        }

        public Grind withVariant(Variant v) {
            return new Grind(path, s, direction, speed, relativeHeading, v);
        }
    }

    /**
     * Step 2 and 3: the best path to catch, or null. The feet must be within the capture distance
     * horizontally (wider when approaching nearly along the rail), between {@link #BELOW} below and
     * {@link #ABOVE} above the path, moving at {@link #MIN_SPEED} or more and not straight across
     * it; the speed carried onto the rail is the velocity along it, less 5%.
     *
     * @param headingYaw the board's heading, which picks the variant (section 8)
     */

    public static Grind capture(Iterable<GrindPath> candidates, V3 feet, V3 velocity, double headingYaw) {
        double horizontalSpeed = velocity.horizontalLength();
        if (horizontalSpeed < MIN_SPEED) return null;
        Grind best = null;
        double bestDistance = Double.MAX_VALUE;
        for (GrindPath candidate : candidates) {
            RailPath.Closest c = candidate.path().closest(feet);
            if (c.above() < -BELOW || c.above() > ABOVE) continue;
            V3 tangent = candidate.path().tangentAt(c.s());
            double tangentHorizontal = tangent.horizontalLength();
            if (tangentHorizontal < 1.0e-6) continue;
            double cos = (velocity.x() * tangent.x() + velocity.z() * tangent.z()) / (horizontalSpeed * tangentHorizontal);
            double approach = Math.toDegrees(Math.acos(Math.min(1, Math.abs(cos))));
            if (approach >= 89.999) continue;
            double radius = approach <= ASSIST_ANGLE ? ASSIST_RADIUS : CAPTURE_RADIUS;
            if (c.horizontal() > radius) continue;
            int direction = cos > 0 ? 1 : -1;
            double along = Math.abs(velocity.dot(tangent)) * (1 - ENTRY_LOSS);
            if (along < MIN_ENTRY_SPEED) continue;
            if (c.horizontal() < bestDistance) {
                bestDistance = c.horizontal();
                V3 travel = tangent.scale(direction);
                double travelYaw = SlideModel.yawOf(travel.x(), travel.z());
                double relative = SlideModel.wrapDegrees(headingYaw - travelYaw);
                best = new Grind(candidate, c.s(), direction, along, relative, variant(relative));
            }
        }
        return best;
    }

    /**
     * The variant for a board at {@code relative} degrees from the travel direction: the angle to
     * the rail axis, either way along it, picks 50-50, nose or tail (by the sign), or boardslide.
     */
    public static Variant variant(double relative) {
        double a = Math.abs(SlideModel.wrapDegrees(relative));
        double axis = a > 90 ? 180 - a : a;
        if (axis <= FIFTY_FIFTY_MAX) return Variant.FIFTY_FIFTY;
        if (axis <= NOSE_TAIL_MAX) return SlideModel.wrapDegrees(relative) >= 0 ? Variant.NOSE : Variant.TAIL;
        return Variant.BOARDSLIDE;
    }

    /** How a tick on the rail ended. */
    public enum Exit {
        /** Still grinding. */
        NONE,
        /** Ran off the end of the line: no piece continues it. */
        END
    }

    /**
     * The result of one tick: the grind (still the last one at an {@link Exit#END}), where the feet
     * are, the velocity, and how many blocks were covered.
     */
    public record Step(Grind grind, V3 position, V3 velocity, Exit exit, double distance) {
    }

    /**
     * Steps 4 and 5: speed changes by the slope along the path, the path's friction (times
     * {@code frictionMul}, wax on the board) and drag; then the rider moves that far along the path,
     * onto the next piece at each end where one continues the line.
     */
    public static Step advance(Grind g, Lookup lookup, MovementTuning t, double frictionMul) {
        V3 travel = g.travel();
        double sinSlope = -travel.y(); // travel is a unit vector: its y is the sine of the climb
        double speed = SlideModel.alongPathStep(t, g.speed(), sinSlope, g.path().friction() * frictionMul);
        Grind current = g.withSpeed(speed);
        double remaining = speed;
        double covered = 0;
        for (int joins = 0; joins <= MAX_JOINS; joins++) {
            RailPath path = current.path().path();
            double target = current.s() + current.direction() * remaining;
            if (target >= 0 && target <= path.length()) {
                covered += remaining;
                current = new Grind(current.path(), target, current.direction(), speed, current.relativeHeading(), current.variant());
                return new Step(current, current.position(), current.travel().scale(speed), Exit.NONE, covered);
            }
            double toEnd = current.direction() > 0 ? path.length() - current.s() : current.s();
            covered += toEnd;
            remaining -= toEnd;
            V3 end = current.direction() > 0 ? path.endB() : path.endA();
            V3 out = current.direction() > 0 ? path.tangentB() : path.tangentA().scale(-1);
            Grind next = join(current, end, out, remaining, lookup, speed);
            if (next == null) {
                Grind atEnd = new Grind(current.path(), current.direction() > 0 ? path.length() : 0, current.direction(), speed,
                        current.relativeHeading(), current.variant());
                return new Step(atEnd, end, out.scale(speed), Exit.END, covered);
            }
            current = next; // starts at the joined end; the next pass moves the rest of the distance along it
        }
        return new Step(current, current.position(), current.travel().scale(speed), Exit.NONE, covered);
    }

    /**
     * The piece that continues a line past {@code end} heading {@code out}: one with an end within
     * {@link #JOIN_DISTANCE} whose direction differs by less than {@link #JOIN_ANGLE}. The board keeps
     * its angle to the new piece.
     */

    static Grind join(Grind from, V3 end, V3 out, double remaining, Lookup lookup, double speed) {
        double cosLimit = Math.cos(Math.toRadians(JOIN_ANGLE));
        Grind best = null;
        double bestDistance = Double.MAX_VALUE;
        for (GrindPath p : lookup.near(end)) {
            if (p.id() == from.path().id()) continue;
            RailPath path = p.path();
            double dA = path.endA().distance(end);
            if (dA <= JOIN_DISTANCE && path.tangentA().dot(out) > cosLimit && dA < bestDistance) {
                bestDistance = dA;
                best = new Grind(p, 0, 1, speed, from.relativeHeading(), from.variant());
            }
            double dB = path.endB().distance(end);
            if (dB <= JOIN_DISTANCE && path.tangentB().scale(-1).dot(out) > cosLimit && dB < bestDistance) {
                bestDistance = dB;
                best = new Grind(p, path.length(), -1, speed, from.relativeHeading(), from.variant());
            }
        }
        return best;
    }
}
