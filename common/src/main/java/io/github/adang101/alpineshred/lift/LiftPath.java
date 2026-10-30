package io.github.adang101.alpineshred.lift;

import io.github.adang101.alpineshred.park.V3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A lift's cable loop (design doc section 13, "Path model"): pure math, the same on the server and
 * on every client, so a chair's place is a function of one number, {@code s}, its distance along the
 * loop.
 *
 * <p>Nodes run from the drive station (the top, first) through the tower heads in link order to the
 * return station (the bottom, last). At each node the horizontal direction {@code d} runs from the
 * previous node to the next and {@code n = (-d.z, d.x)} is its left; the loaded cable passes
 * {@link #HEAD_OFFSET} to the left of a head and the return cable as far to the right, both
 * {@link #CABLE_HEIGHT} above it. At a station the cables pass {@link #WHEEL_RADIUS} to either side,
 * and a half circle of that radius round the far side of the bullwheel joins them. Between nodes
 * the cable sags as a parabola, {@code y(t) = lerp(y0, y1, t) - sag * 4t(1 - t)} with
 * {@code sag = min(4, L^2 / 600)} for a span {@code L} blocks long (horizontally).
 *
 * <p>The loop starts ({@code s = 0}) where the loaded cable leaves the return station's wheel,
 * climbs to the drive station ({@link #loadedEnd}), turns round its wheel, comes back down the
 * return cable ({@link #returnStart} to {@link #returnEnd}) and turns round the return wheel to the
 * start. It is sampled every {@link #STEP} blocks or less; positions between samples are
 * interpolated, found by binary search over the cumulative distance. Every number is a double,
 * computed in a fixed order, so the server and clients agree to the last bit.
 */
public final class LiftPath {
    public static final double STEP = 0.25;
    public static final double HEAD_OFFSET = 1.5;
    public static final double WHEEL_RADIUS = 2.0;
    public static final double CABLE_HEIGHT = 2.5;
    public static final double MAX_SAG = 4.0;
    public static final double SAG_DIVISOR = 600.0;
    /** How far below the cable a chair's seat hangs. */
    public static final double CHAIR_HANG = 1.6;

    private final List<V3> nodes;
    private final double[] xs, ys, zs, dist;
    private final double loopLength, loadedEnd, returnStart, returnEnd;

    private LiftPath(List<V3> nodes, double[] xs, double[] ys, double[] zs, double[] dist, double loadedEnd, double returnStart,
                     double returnEnd) {
        this.nodes = nodes;
        this.xs = xs;
        this.ys = ys;
        this.zs = zs;
        this.dist = dist;
        this.loopLength = dist[dist.length - 1];
        this.loadedEnd = loadedEnd;
        this.returnStart = returnStart;
        this.returnEnd = returnEnd;
    }

    /**
     * The loop through {@code nodes}: the drive station first, the return station last, at least
     * two in all, no two at the same horizontal position.
     */
    public static LiftPath of(List<V3> nodes) {
        if (nodes.size() < 2) throw new IllegalArgumentException("a lift needs two stations");
        int count = nodes.size();
        V3[] dirs = new V3[count], lefts = new V3[count];
        for (int i = 0; i < count; i++) {
            V3 prev = nodes.get(Math.max(0, i - 1)), next = nodes.get(Math.min(count - 1, i + 1));
            double dx = next.x() - prev.x(), dz = next.z() - prev.z(), len = Math.sqrt(dx * dx + dz * dz);
            if (len < 1.0e-9) throw new IllegalArgumentException("nodes " + (i - 1) + " and " + (i + 1) + " stand at the same place");
            dirs[i] = new V3(dx / len, 0, dz / len);
            lefts[i] = new V3(-dirs[i].z(), 0, dirs[i].x());
        }
        V3[] loaded = new V3[count], back = new V3[count];
        for (int i = 0; i < count; i++) {
            double off = i == 0 || i == count - 1 ? WHEEL_RADIUS : HEAD_OFFSET;
            V3 top = nodes.get(i).add(0, CABLE_HEIGHT, 0);
            loaded[i] = top.add(lefts[i].scale(off));
            back[i] = top.add(lefts[i].scale(-off));
        }

        Sampler out = new Sampler();
        // Loaded side, from the return station (last) up to the drive (first).
        out.add(loaded[count - 1]);
        for (int i = count - 1; i > 0; i--) out.span(loaded[i], loaded[i - 1]);
        double loadedEnd = out.length();
        // Round the drive wheel's far side (away from the line, -d).
        out.arc(nodes.get(0).add(0, CABLE_HEIGHT, 0), lefts[0], dirs[0].scale(-1));
        double returnStart = out.length();
        // Return side, down to the return station.
        for (int i = 0; i + 1 < count; i++) out.span(back[i], back[i + 1]);
        double returnEnd = out.length();
        // Round the return wheel's far side (+d), back to the start.
        out.arc(nodes.get(count - 1).add(0, CABLE_HEIGHT, 0), lefts[count - 1].scale(-1), dirs[count - 1]);
        return new LiftPath(List.copyOf(nodes), out.xs(), out.ys(), out.zs(), out.dist(), loadedEnd, returnStart, returnEnd);
    }

    /** The sag at the middle of a span {@code length} blocks long. */
    public static double sag(double length) {
        return Math.min(MAX_SAG, length * length / SAG_DIVISOR);
    }

    public List<V3> nodes() {
        return nodes;
    }

    public double loopLength() {
        return loopLength;
    }

    /** Where the loaded cable reaches the drive station's wheel. */
    public double loadedEnd() {
        return loadedEnd;
    }

    /** Where the return cable leaves the drive station's wheel. */
    public double returnStart() {
        return returnStart;
    }

    /** Where the return cable reaches the return station's wheel. */
    public double returnEnd() {
        return returnEnd;
    }

    /** Samples in the loop, the closing point (the start again) included. */
    public int samples() {
        return dist.length;
    }

    /** {@code s} folded into [0, loopLength). */
    public double wrap(double s) {
        double r = s % loopLength;
        return r < 0 ? r + loopLength : r;
    }

    /** The sample index {@code i} with {@code dist[i] <= s < dist[i + 1]}, for a wrapped {@code s}. */
    private int index(double s) {
        int i = Arrays.binarySearch(dist, s);
        if (i >= 0) return Math.min(i, dist.length - 2);
        return Math.max(0, Math.min(dist.length - 2, -i - 2));
    }

    /** The cable point at {@code s}. */
    public V3 pos(double s) {
        double w = wrap(s);
        int i = index(w);
        double span = dist[i + 1] - dist[i];
        double t = span <= 0 ? 0 : (w - dist[i]) / span;
        return new V3(xs[i] + (xs[i + 1] - xs[i]) * t, ys[i] + (ys[i + 1] - ys[i]) * t, zs[i] + (zs[i + 1] - zs[i]) * t);
    }

    /** The cable's direction of travel at {@code s}, a unit vector. */
    public V3 tangent(double s) {
        int i = index(wrap(s));
        double dx = xs[i + 1] - xs[i], dy = ys[i + 1] - ys[i], dz = zs[i + 1] - zs[i];
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return len < 1.0e-12 ? new V3(1, 0, 0) : new V3(dx / len, dy / len, dz / len);
    }

    /** Where chair {@code index} of {@code count} hangs (its seat) when the loop has run {@code travel} blocks. */
    public V3 chairPos(double travel, int index, int count) {
        return pos(chairS(travel, index, count)).add(0, -CHAIR_HANG, 0);
    }

    /** The loop distance of chair {@code index} of {@code count}: {@code travel + index * spacing}, wrapped. */
    public double chairS(double travel, int index, int count) {
        return wrap(travel + index * (loopLength / count));
    }

    /** {@code floor(loopLength / spacingTarget)}, at least one: chairs are then spaced evenly. */
    public static int chairCount(double loopLength, double spacingTarget) {
        return Math.max(1, (int) Math.floor(loopLength / spacingTarget));
    }

    /** The loop sampled as it is built: points every {@link #STEP} or less, with cumulative distance. */
    private static final class Sampler {
        private final List<double[]> points = new ArrayList<>();
        private final List<Double> distances = new ArrayList<>();

        void add(V3 p) {
            if (!points.isEmpty()) {
                double[] last = points.get(points.size() - 1);
                double dx = p.x() - last[0], dy = p.y() - last[1], dz = p.z() - last[2];
                double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (d < 1.0e-9) return;
                distances.add(distances.get(distances.size() - 1) + d);
            } else {
                distances.add(0.0);
            }
            points.add(new double[]{p.x(), p.y(), p.z()});
        }

        double length() {
            return distances.get(distances.size() - 1);
        }

        /** The sagging span from a to b, a already added. */
        void span(V3 a, V3 b) {
            double h = Math.sqrt((b.x() - a.x()) * (b.x() - a.x()) + (b.z() - a.z()) * (b.z() - a.z()));
            double sag = sag(h);
            double approx = Math.sqrt(h * h + (b.y() - a.y()) * (b.y() - a.y())) + 2 * sag;
            int n = Math.max(1, (int) Math.ceil(approx / STEP));
            for (int k = 1; k <= n; k++) {
                double t = k / (double) n;
                add(new V3(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t - sag * 4 * t * (1 - t), a.z() + (b.z() - a.z()) * t));
            }
        }

        /** Half a circle of {@link #WHEEL_RADIUS} about {@code centre}, from {@code centre + from * r} through {@code centre + via * r}. */
        void arc(V3 centre, V3 from, V3 via) {
            int n = Math.max(2, (int) Math.ceil(Math.PI * WHEEL_RADIUS / STEP));
            for (int k = 1; k <= n; k++) {
                double a = Math.PI * k / n;
                double c = Math.cos(a), s = Math.sin(a);
                add(new V3(centre.x() + (from.x() * c + via.x() * s) * WHEEL_RADIUS, centre.y(),
                        centre.z() + (from.z() * c + via.z() * s) * WHEEL_RADIUS));
            }
        }

        double[] xs() {
            return points.stream().mapToDouble(p -> p[0]).toArray();
        }

        double[] ys() {
            return points.stream().mapToDouble(p -> p[1]).toArray();
        }

        double[] zs() {
            return points.stream().mapToDouble(p -> p[2]).toArray();
        }

        double[] dist() {
            return distances.stream().mapToDouble(Double::doubleValue).toArray();
        }
    }
}
