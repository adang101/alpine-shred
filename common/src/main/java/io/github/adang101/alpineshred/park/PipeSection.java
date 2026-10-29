package io.github.adang101.alpineshred.park;

/**
 * A quarter pipe as the slide controller rides it (design doc section 11): its facing (the way the
 * wall rises), radius, the transition's back edge ({@code origin}: distance along the facing and the
 * base height), and the run of pipe blocks along the wall ({@code lateralMin} to {@code lateralMax},
 * along {@link #lateral()}).
 */
public record PipeSection(Facing facing, int radius, double originAlong, double originY, double lateralMin, double lateralMax) {
    /** Half the rider's box width: the box's front edge, not its center, rides the arc. */
    public static final double HALF_WIDTH = 0.3;

    /** The horizontal axis along the wall: the facing turned clockwise. */
    public V3 lateral() {
        return facing.clockwise().vector();
    }

    /** Coordinate of a point along the facing. */
    public double along(V3 p) {
        return p.x() * facing.dx + p.z() * facing.dz;
    }

    public double across(V3 p) {
        V3 l = lateral();
        return p.x() * l.x() + p.z() * l.z();
    }

    /** Distance along the arc's floor from the transition to the rider's front edge. */
    public double u(V3 feet) {
        return along(feet) + HALF_WIDTH - originAlong;
    }

    public boolean coversAcross(double across, double margin) {
        return across >= lateralMin - margin && across <= lateralMax + margin;
    }

    /** Feet position for a rider on the arc at angle {@code theta}, {@code across} along the wall. */
    public V3 feetAt(double theta, double across) {
        double along = originAlong + PipeArc.u(radius, theta) - HALF_WIDTH;
        double y = originY + PipeArc.heightAt(radius, theta);
        V3 l = lateral();
        return new V3(facing.dx * along + l.x() * across, y, facing.dz * along + l.z() * across);
    }

    /** Height of the arc under the rider's front edge, or NaN outside the pipe along its facing. */
    public double surfaceUnder(V3 feet) {
        double u = u(feet);
        if (u < 0 || u > radius) return Double.NaN;
        return originY + PipeArc.height(radius, u);
    }

    /** Unit tangent of the arc at {@code theta}, pointing up the wall. */
    public V3 tangent(double theta) {
        return new V3(facing.dx * Math.cos(theta), Math.sin(theta), facing.dz * Math.cos(theta));
    }
}
