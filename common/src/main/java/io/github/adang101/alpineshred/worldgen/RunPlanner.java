package io.github.adang101.alpineshred.worldgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Plans a groomed run down real terrain (design doc section 12, "Groomed run planner"), for
 * {@code groomed_run} and {@code ski_resort}. Pure: it reads heights through {@link Terrain}, so it
 * is tested on synthetic slopes and runs on the world generator's height sampler.
 *
 * <ol>
 *   <li>The start must be at least sea level plus 20 with a local grade between 0.15 and 0.60; the
 *       run heads down the steepest of eight directions.</li>
 *   <li>A difficulty (green, blue or black) comes from the seed, with target grades 0.20, 0.35 and
 *       0.55, and a length between 96 and 200 blocks.</li>
 *   <li>Every 8 blocks it samples 8 headings within 60 degrees of the current one and takes the
 *       cheapest: distance from the target grade, a turn penalty, and water or biome-edge penalties.</li>
 *   <li>It stops at the length, when the grade stays under 0.05 for two steps, or at water or a
 *       biome edge, then adds a 16-block flat run-out where there is room.</li>
 *   <li>The path is smoothed with a Catmull-Rom spline sampled every 2 blocks; the width is 9, 11
 *       or 13; the profile follows the terrain, smoothed and never climbing.</li>
 * </ol>
 * Every point stays within {@link #MAX_REACH} blocks of the structure's start, less half the width
 * and the edge fade, so the pieces stay within the 8 chunks vanilla tracks around a start. The run
 * itself begins uphill of the structure's start ({@link #uphillStart}), so it can use the reach on
 * both sides.
 */
public final class RunPlanner {
    public enum Difficulty {
        GREEN(0.20, "green"), BLUE(0.35, "blue"), BLACK(0.55, "black");

        public final double grade;
        public final String id;

        Difficulty(double grade, String id) {
            this.grade = grade;
            this.id = id;
        }

        public static Difficulty byId(String id) {
            for (Difficulty d : values()) {
                if (d.id.equals(id)) return d;
            }
            return BLUE;
        }
    }

    /** What the planner reads of the world. */
    public interface Terrain {
        /** The surface height at a column: the y of the first air (or fluid) block above the ground. */
        int height(int x, int z);

        /** True where the surface is water or another fluid. */
        boolean water(int x, int z);

        /** True where a run may go (a snowy biome from the structure's tag). */
        boolean allowed(int x, int z);
    }

    public static final int STEP = 8;
    public static final int MIN_LENGTH = 96;
    public static final int MAX_LENGTH = 200;
    /** Shorter than this (water or an edge came early) is not worth building. */
    public static final int MIN_BUILT_LENGTH = 48;
    public static final int RUN_OUT = 16;
    public static final int SAMPLE = 2;
    public static final int EDGE_FADE = 3;
    /** Furthest any built column may be from the start: 7 chunks. */
    public static final int MAX_REACH = 112;
    public static final double MIN_START_GRADE = 0.15;
    public static final double MAX_START_GRADE = 0.60;
    public static final double FLAT_GRADE = 0.05;
    private static final int HEADINGS = 8;
    private static final double MAX_TURN = Math.toRadians(60);
    /** Good starts tried, from the top of the uphill walk down, before a start chunk gives up. */
    public static final int START_TRIES = 3;

    /** Why the last plan on this thread came to nothing: for the game test's tally. */
    public enum Failure {NONE, NOT_NEAR_BIOME, LOW, WATER, BIOME, GENTLE, STEEP, NO_GOOD_START, SHORT_BLOCKED, SHORT_REACH, SHORT_FLAT}

    private static final ThreadLocal<Failure> LAST_FAILURE = ThreadLocal.withInitial(() -> Failure.NONE);

    public static Failure lastFailure() {
        return LAST_FAILURE.get();
    }

    static void noteFailure(Failure failure) {
        LAST_FAILURE.set(failure);
    }

    /**
     * A planned run: the smoothed centerline every 2 blocks as (x, z, y), where y is the groomed
     * surface's height (the first block above the snow); the width; and the index where the flat
     * run-out begins (equal to the size when there is none).
     */
    public record Plan(Difficulty difficulty, int width, List<double[]> points, int runOutFrom) {
        public double length() {
            double len = 0;
            for (int i = 1; i < points.size(); i++) {
                len += Math.hypot(points.get(i)[0] - points.get(i - 1)[0], points.get(i)[1] - points.get(i - 1)[1]);
            }
            return len;
        }

        /** Half the width plus the edge fade: how far from the centerline the run touches the world. */
        public double reach() {
            return width / 2.0 + EDGE_FADE;
        }
    }

    private RunPlanner() {
    }

    /** True if (x, z) is a start the planner accepts: high enough and on a slope it can ride down. */
    public static boolean goodStart(Terrain t, int seaLevel, int x, int z) {
        return startFailure(t, seaLevel, x, z) == Failure.NONE;
    }

    private static Failure startFailure(Terrain t, int seaLevel, int x, int z) {
        if (t.height(x, z) < seaLevel + 20) return Failure.LOW;
        if (t.water(x, z)) return Failure.WATER;
        if (!t.allowed(x, z)) return Failure.BIOME;
        double best = steepest(t, x, z)[1];
        if (best < MIN_START_GRADE) return Failure.GENTLE;
        if (best > MAX_START_GRADE) return Failure.STEEP;
        return Failure.NONE;
    }

    /** {angle, grade} of the steepest of eight directions over one step. */
    private static double[] steepest(Terrain t, int x, int z) {
        int h = t.height(x, z);
        double bestAngle = 0, bestGrade = -1;
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            int nx = (int) Math.round(x + Math.cos(a) * STEP), nz = (int) Math.round(z + Math.sin(a) * STEP);
            double g = (h - t.height(nx, nz)) / (double) STEP;
            if (g > bestGrade) {
                bestGrade = g;
                bestAngle = a;
            }
        }
        return new double[]{bestAngle, bestGrade};
    }

    /** Plans a run from (startX, startZ), keeping it within reach of the start itself. */
    public static Plan plan(Terrain t, int seaLevel, int startX, int startZ, long seed) {
        return plan(t, seaLevel, startX, startZ, startX, startZ, seed);
    }

    /**
     * Plans a run from (startX, startZ), keeping every column within {@link #MAX_REACH} of
     * (centerX, centerZ), the structure's start; returns null when the start is not good or the run
     * would be too short.
     */
    public static Plan plan(Terrain t, int seaLevel, int startX, int startZ, int centerX, int centerZ, long seed) {
        return plan(t, seaLevel, startX, startZ, centerX, centerZ, seed, MAX_REACH);
    }

    /** As above, within {@code maxReach} of the centre (a resort keeps room for its lodge). */
    public static Plan plan(Terrain t, int seaLevel, int startX, int startZ, int centerX, int centerZ, long seed, int maxReach) {
        Failure start = startFailure(t, seaLevel, startX, startZ);
        LAST_FAILURE.set(start);
        if (start != Failure.NONE) return null;
        Random random = new Random(seed);
        Difficulty difficulty = Difficulty.values()[random.nextInt(3)];
        int target = MIN_LENGTH + random.nextInt(MAX_LENGTH - MIN_LENGTH + 1);
        int width = 9 + 2 * random.nextInt(3);
        double limit = maxReach - width / 2.0 - EDGE_FADE - 1;

        double heading = steepest(t, startX, startZ)[0];
        List<double[]> control = new ArrayList<>();
        double x = startX, z = startZ;
        control.add(new double[]{x, z});
        double travelled = 0;
        int flat = 0;
        Failure stop = Failure.NONE;
        while (travelled < target) {
            double bestCost = Double.MAX_VALUE, bestHeading = heading, bestGrade = 0;
            boolean bestBlocked = true;
            int h = t.height((int) Math.round(x), (int) Math.round(z));
            for (int k = 0; k < HEADINGS; k++) {
                double turn = -MAX_TURN + k * (2 * MAX_TURN) / (HEADINGS - 1);
                double a = heading + turn;
                double nx = x + Math.cos(a) * STEP, nz = z + Math.sin(a) * STEP;
                if (Math.hypot(nx - centerX, nz - centerZ) > limit) continue;
                int ix = (int) Math.round(nx), iz = (int) Math.round(nz);
                double grade = (h - t.height(ix, iz)) / (double) STEP;
                boolean blocked = t.water(ix, iz) || !t.allowed(ix, iz);
                double cost = Math.abs(grade - difficulty.grade) * 4 + Math.abs(turn) / MAX_TURN * 0.3 + (blocked ? 10 : 0)
                        + (grade < 0 ? 2 : 0);
                if (cost < bestCost) {
                    bestCost = cost;
                    bestHeading = a;
                    bestGrade = grade;
                    bestBlocked = blocked;
                }
            }
            if (bestCost == Double.MAX_VALUE || bestBlocked) { // the edge of the reach, water or another biome
                stop = bestCost == Double.MAX_VALUE ? Failure.SHORT_REACH : Failure.SHORT_BLOCKED;
                break;
            }
            heading = bestHeading;
            x += Math.cos(heading) * STEP;
            z += Math.sin(heading) * STEP;
            control.add(new double[]{x, z});
            travelled += STEP;
            flat = bestGrade < FLAT_GRADE ? flat + 1 : 0;
            if (flat >= 2) {
                stop = Failure.SHORT_FLAT;
                break;
            }
        }
        if (travelled < MIN_BUILT_LENGTH) {
            LAST_FAILURE.set(stop);
            return null;
        }
        LAST_FAILURE.set(Failure.NONE);

        // The run-out: flat, straight on, where it fits and stays dry.
        int runOutSteps = 0;
        for (int s = 1; s <= RUN_OUT / STEP; s++) {
            double nx = x + Math.cos(heading) * STEP * s, nz = z + Math.sin(heading) * STEP * s;
            int ix = (int) Math.round(nx), iz = (int) Math.round(nz);
            if (Math.hypot(nx - centerX, nz - centerZ) > limit || t.water(ix, iz) || !t.allowed(ix, iz)) break;
            runOutSteps = s;
        }
        int slopeControls = control.size();
        for (int s = 1; s <= runOutSteps; s++) {
            control.add(new double[]{x + Math.cos(heading) * STEP * s, z + Math.sin(heading) * STEP * s});
        }

        // Smooth: Catmull-Rom through the control points, every 2 blocks (8 / 2 = 4 samples a segment).
        List<double[]> points = new ArrayList<>();
        int perSegment = STEP / SAMPLE;
        int runOutFrom = -1;
        for (int i = 0; i + 1 < control.size(); i++) {
            double[] p0 = control.get(Math.max(0, i - 1)), p1 = control.get(i), p2 = control.get(i + 1),
                    p3 = control.get(Math.min(control.size() - 1, i + 2));
            if (i == slopeControls - 1 && runOutFrom < 0) runOutFrom = points.size();
            for (int s = 0; s < perSegment; s++) {
                double u = s / (double) perSegment;
                points.add(new double[]{catmullRom(p0[0], p1[0], p2[0], p3[0], u), catmullRom(p0[1], p1[1], p2[1], p3[1], u), 0});
            }
        }
        double[] last = control.get(control.size() - 1);
        points.add(new double[]{last[0], last[1], 0});
        if (runOutFrom < 0) runOutFrom = points.size();

        // Profile: the terrain under each point, smoothed over 5 points, never climbing, flat on the run-out.
        int n = points.size();
        double[] ground = new double[n];
        for (int i = 0; i < n; i++) ground[i] = t.height((int) Math.round(points.get(i)[0]), (int) Math.round(points.get(i)[1]));
        double[] profile = new double[n];
        for (int i = 0; i < n; i++) {
            double sum = 0;
            int count = 0;
            for (int j = Math.max(0, i - 2); j <= Math.min(n - 1, i + 2); j++) {
                sum += ground[j];
                count++;
            }
            profile[i] = sum / count;
        }
        for (int i = 1; i < n; i++) {
            profile[i] = i >= runOutFrom ? profile[runOutFrom - 1] : Math.min(profile[i], profile[i - 1]);
        }
        for (int i = 0; i < n; i++) points.get(i)[2] = profile[i];
        return new Plan(difficulty, width, Collections.unmodifiableList(points), runOutFrom);
    }

    /**
     * A run through (centerX, centerZ), the structure's start: walks uphill from there
     * ({@link #uphillWalk}) and plans from the highest good starts along the walk, at most
     * {@link #START_TRIES} of them. A summit is often too steep or too flat to start on; a little
     * below it usually is not. Null when none gives a run.
     */
    public static Plan planThrough(Terrain t, int seaLevel, int centerX, int centerZ, int maxUphill, long seed, int maxReach) {
        List<int[]> walk = uphillWalk(t, centerX, centerZ, maxUphill);
        Failure first = null;
        int tries = 0;
        for (int i = walk.size() - 1; i >= 0 && tries < START_TRIES; i--) {
            int[] p = walk.get(i);
            Failure f = startFailure(t, seaLevel, p[0], p[1]);
            if (first == null) first = f;
            if (f != Failure.NONE) continue;
            tries++;
            Plan plan = plan(t, seaLevel, p[0], p[1], centerX, centerZ, seed, maxReach);
            if (plan != null) return plan;
            first = lastFailure();
        }
        LAST_FAILURE.set(tries == 0 ? (first == null ? Failure.NO_GOOD_START : first) : first);
        return null;
    }

    /**
     * The top of a run through (centerX, centerZ): walks uphill (the steepest rise, a step at a
     * time) up to {@code maxDistance} blocks, so a run from there can pass the structure's start
     * and use the reach on both sides of it. Returns {x, z}.
     */
    public static int[] uphillStart(Terrain t, int centerX, int centerZ, int maxDistance) {
        List<int[]> walk = uphillWalk(t, centerX, centerZ, maxDistance);
        return walk.get(walk.size() - 1);
    }

    /** The uphill walk from (centerX, centerZ): every point it stood on, the centre first and the top last. */
    public static List<int[]> uphillWalk(Terrain t, int centerX, int centerZ, int maxDistance) {
        List<int[]> walk = new ArrayList<>();
        int x = centerX, z = centerZ;
        walk.add(new int[]{x, z});
        for (int walked = 0; walked + STEP <= maxDistance; walked += STEP) {
            int h = t.height(x, z);
            int bx = x, bz = z, best = h;
            for (int k = 0; k < 8; k++) {
                double a = k * Math.PI / 4;
                int nx = (int) Math.round(x + Math.cos(a) * STEP), nz = (int) Math.round(z + Math.sin(a) * STEP);
                int nh = t.height(nx, nz);
                if (nh > best && !t.water(nx, nz) && t.allowed(nx, nz)) {
                    best = nh;
                    bx = nx;
                    bz = nz;
                }
            }
            if (best == h) break;
            x = bx;
            z = bz;
            walk.add(new int[]{x, z});
        }
        return walk;
    }

    private static double catmullRom(double p0, double p1, double p2, double p3, double u) {
        double u2 = u * u, u3 = u2 * u;
        return 0.5 * (2 * p1 + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u2 + (-p0 + 3 * p1 - 3 * p2 + p3) * u3);
    }
}
