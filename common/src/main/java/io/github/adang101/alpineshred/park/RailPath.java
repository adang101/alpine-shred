package io.github.adang101.alpineshred.park;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A grind path: a polyline the rider's feet follow (design doc section 10). Distance along the path
 * is {@code s}, from 0 at end A to {@link #length()} at end B. Rail pieces have fixed local paths,
 * cached per (shape, facing) and moved into place with {@link #at}; vanilla fences and walls build
 * theirs on the fly.
 */
public final class RailPath {
    private static final int CURVE_POINTS = 6;
    private static final Map<RailShape, Map<Facing, RailPath>> LOCAL = new EnumMap<>(RailShape.class);

    static {
        for (RailShape shape : RailShape.values()) {
            Map<Facing, RailPath> byFacing = new EnumMap<>(Facing.class);
            List<V3> north = authored(shape);
            for (Facing facing : Facing.values()) {
                List<V3> pts = new ArrayList<>();
                for (V3 p : north) pts.add(facing.rotateLocal(p));
                byFacing.put(facing, new RailPath(pts));
            }
            LOCAL.put(shape, byFacing);
        }
    }

    private final V3[] points;
    private final double[] cumulative;

    public RailPath(List<V3> points) {
        if (points.size() < 2) throw new IllegalArgumentException("a path needs two points");
        this.points = points.toArray(new V3[0]);
        this.cumulative = new double[this.points.length];
        for (int i = 1; i < this.points.length; i++) {
            cumulative[i] = cumulative[i - 1] + this.points[i].distance(this.points[i - 1]);
        }
    }

    /** The path of a piece in block-local coordinates, shared. */
    public static RailPath local(RailShape shape, Facing facing) {
        return LOCAL.get(shape).get(facing);
    }

    /** The path of a piece placed with its block's minimum corner at (bx, by, bz). */
    public static RailPath at(RailShape shape, Facing facing, int bx, int by, int bz) {
        return local(shape, facing).translate(bx, by, bz);
    }

    /** A straight path between two points (vanilla fences and walls, coping). */
    public static RailPath straight(V3 a, V3 b) {
        return new RailPath(List.of(a, b));
    }

    /** Points of the north-facing piece (section 10 table). */
    static List<V3> authored(RailShape shape) {
        return switch (shape) {
            case FLAT -> List.of(new V3(0.5, 0.5, 1.0), new V3(0.5, 0.5, 0.0));
            case RISE_A -> List.of(new V3(0.5, 0.5, 1.0), new V3(0.5, 1.0, 0.0));
            case RISE_B -> List.of(new V3(0.5, 0.0, 1.0), new V3(0.5, 0.5, 0.0));
            case CURVE -> curve(0.5);
        };
    }

    /** Quarter arc about the block's south-east corner, south edge to east edge, at {@code height}. */
    public static List<V3> curve(double height) {
        List<V3> pts = new ArrayList<>();
        for (int i = 0; i < CURVE_POINTS; i++) {
            double phi = Math.toRadians(180 + 90.0 * i / (CURVE_POINTS - 1));
            double x = 1 + 0.5 * Math.cos(phi), z = 1 + 0.5 * Math.sin(phi);
            // Snap the ends exactly onto the edges so neighbours meet without rounding error.
            if (i == 0) x = 0.5;
            if (i == CURVE_POINTS - 1) z = 0.5;
            pts.add(new V3(Math.abs(x - 1) < 1e-12 ? 1 : x, height, Math.abs(z - 1) < 1e-12 ? 1 : z));
        }
        return pts;
    }

    public RailPath translate(double dx, double dy, double dz) {
        List<V3> pts = new ArrayList<>(points.length);
        for (V3 p : points) pts.add(p.add(dx, dy, dz));
        return new RailPath(pts);
    }

    public double length() {
        return cumulative[cumulative.length - 1];
    }

    public V3 endA() {
        return points[0];
    }

    public V3 endB() {
        return points[points.length - 1];
    }

    public int pointCount() {
        return points.length;
    }

    public V3 point(int i) {
        return points[i];
    }

    /** Position at distance {@code s}, clamped to the path. */
    public V3 pointAt(double s) {
        int i = segment(s);
        double segLen = cumulative[i + 1] - cumulative[i];
        double t = segLen < 1.0e-12 ? 0 : (clamp(s, 0, length()) - cumulative[i]) / segLen;
        return points[i].lerp(points[i + 1], t);
    }

    /** Unit tangent at {@code s}, pointing toward end B. */
    public V3 tangentAt(double s) {
        int i = segment(s);
        return points[i + 1].sub(points[i]).normalize();
    }

    /** Tangent at end A, pointing into the path. */
    public V3 tangentA() {
        return tangentAt(0);
    }

    /** Tangent at end B, pointing out of the path. */
    public V3 tangentB() {
        return tangentAt(length());
    }

    private int segment(double s) {
        if (s <= 0) return 0;
        for (int i = 0; i < points.length - 1; i++) {
            if (s <= cumulative[i + 1]) return i;
        }
        return points.length - 2;
    }

    /** Where a point is relative to the path: the closest point measured horizontally. */
    public record Closest(double s, V3 point, double horizontal, double above) {
    }

    /**
     * The point of the path horizontally closest to {@code p}, how far it is horizontally, and how
     * far {@code p} is above it (negative below).
     */
    public Closest closest(V3 p) {
        double bestS = 0, bestD = Double.MAX_VALUE;
        V3 best = points[0];
        for (int i = 0; i < points.length - 1; i++) {
            V3 a = points[i], b = points[i + 1];
            double ex = b.x() - a.x(), ez = b.z() - a.z();
            double len2 = ex * ex + ez * ez;
            double t = len2 < 1.0e-12 ? 0 : clamp(((p.x() - a.x()) * ex + (p.z() - a.z()) * ez) / len2, 0, 1);
            V3 q = a.lerp(b, t);
            double d = Math.hypot(p.x() - q.x(), p.z() - q.z());
            if (d < bestD - 1.0e-12) {
                bestD = d;
                best = q;
                bestS = cumulative[i] + t * (cumulative[i + 1] - cumulative[i]);
            }
        }
        return new Closest(bestS, best, bestD, p.y() - best.y());
    }

    static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
