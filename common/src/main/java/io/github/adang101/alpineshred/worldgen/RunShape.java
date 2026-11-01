package io.github.adang101.alpineshred.worldgen;

import java.util.ArrayList;
import java.util.List;

/**
 * What a planned run does to each column (design doc section 12, {@code GroomedRunPiece}): within
 * half the width of the centerline the surface is cut or filled (by at most 2 blocks) to the
 * profile and groomed; over the next 3 blocks it fades into snow layers; slope markers stand on
 * both edges every 24 blocks, colored by difficulty. Pure, so the piece's per-chunk work and the
 * tests agree.
 */
public final class RunShape {
    /** How far the groomed surface may cut into or build up from the ground. */
    public static final int MAX_CUT = 2;
    public static final int MARKER_SPACING = 24;

    public enum Kind {NONE, GROOMED, FADE}

    /**
     * One column: what to do, the surface height to build to (the y of the first block above the
     * groomed snow), and how deep into the fade (0 at the groomed edge, 1 at the outside).
     */
    public record Column(Kind kind, int surface, double fade) {
        static final Column NONE = new Column(Kind.NONE, 0, 0);
    }

    private RunShape() {
    }

    /** The column at (x, z), given the ground's surface height there. */
    public static Column column(RunPlanner.Plan plan, int x, int z, int ground) {
        double cx = x + 0.5, cz = z + 0.5;
        List<double[]> pts = plan.points();
        double best = Double.MAX_VALUE, y = 0;
        for (int i = 0; i + 1 < pts.size(); i++) {
            double[] a = pts.get(i), b = pts.get(i + 1);
            double vx = b[0] - a[0], vz = b[1] - a[1];
            double len2 = vx * vx + vz * vz;
            double u = len2 < 1.0e-9 ? 0 : Math.max(0, Math.min(1, ((cx - a[0]) * vx + (cz - a[1]) * vz) / len2));
            double px = a[0] + vx * u, pz = a[1] + vz * u;
            double d = Math.hypot(cx - px, cz - pz);
            if (d < best) {
                best = d;
                y = a[2] + (b[2] - a[2]) * u;
            }
        }
        double half = plan.width() / 2.0;
        if (best > half + RunPlanner.EDGE_FADE) return Column.NONE;
        int target = (int) Math.round(y);
        target = Math.max(ground - MAX_CUT, Math.min(ground + MAX_CUT, target));
        if (best <= half) return new Column(Kind.GROOMED, target, 0);
        return new Column(Kind.FADE, ground, (best - half) / RunPlanner.EDGE_FADE);
    }

    /** Slope marker posts: both edges, every 24 blocks along the run (not on the run-out). */
    public static List<int[]> markers(RunPlanner.Plan plan) {
        List<int[]> out = new ArrayList<>();
        List<double[]> pts = plan.points();
        int every = MARKER_SPACING / RunPlanner.SAMPLE;
        double off = plan.width() / 2.0 + 1;
        for (int i = every / 2; i + 1 < Math.min(pts.size(), plan.runOutFrom()); i += every) {
            double[] a = pts.get(i), b = pts.get(i + 1);
            double vx = b[0] - a[0], vz = b[1] - a[1], len = Math.hypot(vx, vz);
            if (len < 1.0e-6) continue;
            double nx = -vz / len, nz = vx / len;
            for (int side = -1; side <= 1; side += 2) {
                out.add(new int[]{(int) Math.floor(a[0] + nx * off * side), (int) Math.floor(a[1] + nz * off * side)});
            }
        }
        return out;
    }

    /** The smallest box (minX, minZ, maxX, maxZ) holding every column the run touches. */
    public static int[] bounds(RunPlanner.Plan plan) {
        double r = plan.reach() + 1;
        double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (double[] p : plan.points()) {
            minX = Math.min(minX, p[0] - r);
            minZ = Math.min(minZ, p[1] - r);
            maxX = Math.max(maxX, p[0] + r);
            maxZ = Math.max(maxZ, p[1] + r);
        }
        return new int[]{(int) Math.floor(minX), (int) Math.floor(minZ), (int) Math.ceil(maxX), (int) Math.ceil(maxZ)};
    }
}
