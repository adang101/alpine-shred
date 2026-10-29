package io.github.adang101.alpineshred.park;

import java.util.ArrayList;
import java.util.List;

/**
 * Smart rail placement (design doc section 10): one pure function, shared by the server's block
 * placement and the client's ghost preview, so what the preview shows is what gets placed.
 *
 * <ul>
 *   <li>Sneaking forces {@code FLAT} along the player's facing.</li>
 *   <li>A piece placed where an open rail end touches the cell continues that line: {@code FLAT};
 *       {@code RISE_A} when the ground ahead steps up a block; the falling half of a rise
 *       ({@code RISE_B} facing back) when it drops a block; and the second half of a rise or fall
 *       when the end was left by the first half.</li>
 *   <li>A piece placed beside the end of a line, not in line with it, turns the end piece into a
 *       {@code CURVE} toward the new piece, as vanilla rails do.</li>
 *   <li>With no rail end nearby, {@code FLAT} along the player's facing.</li>
 * </ul>
 */
public final class RailPlacement {
    private static final double TOUCH = 0.15;
    private static final double JOIN_COS = Math.cos(Math.toRadians(GrindPhysics.JOIN_ANGLE));

    private RailPlacement() {
    }

    public record Piece(RailShape shape, Facing facing) {
        public RailPath pathAt(Cell c) {
            return RailPath.at(shape, facing, c.x(), c.y(), c.z());
        }
    }

    public record Cell(int x, int y, int z) {
        public Cell offset(Facing f) {
            return new Cell(x + f.dx, y, z + f.dz);
        }

        public Cell up(int n) {
            return new Cell(x, y + n, z);
        }

        /** True if {@code p} lies in or on the boundary of this block. */
        boolean touches(V3 p) {
            double e = 1.0e-6;
            return p.x() >= x - e && p.x() <= x + 1 + e && p.y() >= y - e && p.y() <= y + 1 + e && p.z() >= z - e && p.z() <= z + 1 + e;
        }
    }

    /** What placement needs to know about the world around the target. */
    public interface World {
        /** The rail piece in a cell, or null. */
        Piece railAt(Cell c);

        /** True for ground a rail would rest on or run into (a full collision block). */
        boolean solid(Cell c);
    }

    /**
     * What to place, and optionally a neighbour to reshape (into a curve toward the new piece).
     */
    public record Choice(Piece piece, Cell reshapeAt, Piece reshapeTo) {
        static Choice of(Piece piece) {
            return new Choice(piece, null, null);
        }
    }

    /** An open end of a neighbouring piece: where it is and which way the line leaves it. */
    record OpenEnd(Cell owner, V3 point, V3 out) {
    }

    public static Choice choose(World world, Cell target, Facing playerFacing, boolean sneak) {
        if (sneak) return Choice.of(new Piece(RailShape.FLAT, playerFacing));

        List<OpenEnd> ends = openEndsTouching(world, target);
        if (!ends.isEmpty()) {
            Piece best = null;
            int bestMatches = 0;
            int bestRank = Integer.MAX_VALUE;
            for (RailShape shape : RailShape.values()) {
                for (Facing facing : Facing.values()) {
                    Piece candidate = new Piece(shape, facing);
                    int matches = matches(candidate.pathAt(target), ends);
                    if (matches == 0) continue;
                    int rank = rank(world, target, candidate, ends);
                    if (matches > bestMatches || (matches == bestMatches && rank < bestRank)) {
                        best = candidate;
                        bestMatches = matches;
                        bestRank = rank;
                    }
                }
            }
            if (best != null) return Choice.of(best);
        }

        Choice corner = corner(world, target);
        if (corner != null) return corner;
        return Choice.of(new Piece(RailShape.FLAT, playerFacing));
    }

    /** Open ends of neighbouring pieces that lie on the target cell's boundary. */
    static List<OpenEnd> openEndsTouching(World world, Cell target) {
        List<OpenEnd> ends = new ArrayList<>();
        for (int dy = -1; dy <= 1; dy++) {
            for (Facing f : Facing.values()) {
                addOpenEnds(world, target.offset(f).up(dy), target, ends);
            }
            if (dy != 0) addOpenEnds(world, target.up(dy), target, ends);
        }
        return ends;
    }

    private static void addOpenEnds(World world, Cell owner, Cell target, List<OpenEnd> out) {
        Piece piece = world.railAt(owner);
        if (piece == null) return;
        RailPath path = piece.pathAt(owner);
        V3[] points = {path.endA(), path.endB()};
        V3[] outs = {path.tangentA().scale(-1), path.tangentB()};
        for (int i = 0; i < 2; i++) {
            if (!target.touches(points[i])) continue;
            if (connectedElsewhere(world, owner, target, points[i])) continue;
            out.add(new OpenEnd(owner, points[i], outs[i]));
        }
    }

    /** True when another piece (not in the target cell) already continues the line at {@code point}. */
    private static boolean connectedElsewhere(World world, Cell owner, Cell target, V3 point) {
        int bx = (int) Math.floor(point.x()), by = (int) Math.floor(point.y()), bz = (int) Math.floor(point.z());
        for (int x = bx - 1; x <= bx; x++) {
            for (int y = by - 1; y <= by; y++) {
                for (int z = bz - 1; z <= bz; z++) {
                    Cell c = new Cell(x, y, z);
                    if (c.equals(owner) || c.equals(target)) continue;
                    Piece p = world.railAt(c);
                    if (p == null) continue;
                    RailPath path = p.pathAt(c);
                    if (path.endA().distance(point) <= TOUCH || path.endB().distance(point) <= TOUCH) return true;
                }
            }
        }
        return false;
    }

    /** How many of the open ends the candidate continues smoothly (each end of it used once). */
    static int matches(RailPath candidate, List<OpenEnd> ends) {
        boolean usedA = false, usedB = false;
        int count = 0;
        for (OpenEnd end : ends) {
            if (!usedA && candidate.endA().distance(end.point()) <= TOUCH && candidate.tangentA().dot(end.out()) > JOIN_COS) {
                usedA = true;
                count++;
            } else if (!usedB && candidate.endB().distance(end.point()) <= TOUCH && candidate.tangentB().scale(-1).dot(end.out()) > JOIN_COS) {
                usedB = true;
                count++;
            }
        }
        return count;
    }

    /**
     * Preference among pieces that continue a line equally well: follow the ground (a rise where it
     * steps up, a fall where it drops, level otherwise); curves only when nothing straight fits.
     */
    private static int rank(World world, Cell target, Piece candidate, List<OpenEnd> ends) {
        if (candidate.shape() == RailShape.CURVE) return 100;
        OpenEnd end = ends.get(0);
        Facing travel = horizontalFacing(end.out());
        Cell ahead = target.offset(travel);
        boolean stepsUp = world.solid(ahead) && !world.solid(ahead.up(1));
        boolean drops = !world.solid(ahead.up(-1)) && world.solid(ahead.up(-2));
        Piece climb = new Piece(RailShape.RISE_A, travel);
        Piece fall = new Piece(RailShape.RISE_B, travel.opposite());
        Piece level = new Piece(RailShape.FLAT, travel);
        if (stepsUp && candidate.equals(climb)) return 0;
        if (drops && candidate.equals(fall)) return 0;
        if (candidate.equals(level) || candidate.equals(new Piece(RailShape.FLAT, travel.opposite()))) return 1;
        return 2;
    }

    /**
     * Vanilla-style corners: a piece placed beside the last piece of a line (a flat piece with one
     * end joined and the other open, in line with neither) turns that piece into a curve from its
     * joined end toward the new piece, which runs on straight.
     */
    private static Choice corner(World world, Cell target) {
        for (Facing side : Facing.values()) {
            Cell n = target.offset(side.opposite()); // the neighbour, with the target on its `side`
            Piece piece = world.railAt(n);
            if (piece == null || piece.shape() != RailShape.FLAT) continue;
            Facing axis = piece.facing();
            if (side == axis || side == axis.opposite()) continue; // in line: handled by the ends above
            RailPath path = piece.pathAt(n);
            boolean aJoined = connectedElsewhere(world, n, target, path.endA());
            boolean bJoined = connectedElsewhere(world, n, target, path.endB());
            if (aJoined == bJoined) continue; // isolated, or already part of a through line
            // The joined end's edge: end A sits on the edge opposite the piece's facing, end B on it.
            Facing joinedEdge = aJoined ? axis.opposite() : axis;
            Facing curveFacing = curveConnecting(joinedEdge, side);
            if (curveFacing == null) continue;
            return new Choice(new Piece(RailShape.FLAT, side), n, new Piece(RailShape.CURVE, curveFacing));
        }
        return null;
    }

    /** The curve facing whose path joins the two (adjacent) block edges, or null. */
    public static Facing curveConnecting(Facing edge1, Facing edge2) {
        for (Facing f : Facing.values()) {
            RailPath p = RailPath.local(RailShape.CURVE, f);
            Facing a = edgeOf(p.endA()), b = edgeOf(p.endB());
            if ((a == edge1 && b == edge2) || (a == edge2 && b == edge1)) return f;
        }
        return null;
    }

    /** The block edge a local end point lies on. */
    static Facing edgeOf(V3 local) {
        if (Math.abs(local.z()) < 1e-6) return Facing.NORTH;
        if (Math.abs(local.z() - 1) < 1e-6) return Facing.SOUTH;
        if (Math.abs(local.x()) < 1e-6) return Facing.WEST;
        return Facing.EAST;
    }

    static Facing horizontalFacing(V3 v) {
        if (Math.abs(v.x()) > Math.abs(v.z())) return v.x() > 0 ? Facing.EAST : Facing.WEST;
        return v.z() > 0 ? Facing.SOUTH : Facing.NORTH;
    }

    // ---- connections (block state upkeep and the wrench) ------------------------------------------

    /** Whether each end of the piece in {@code cell} meets another piece's end: [A, B]. */
    public static boolean[] connections(World world, Cell cell, Piece piece) {
        RailPath path = piece.pathAt(cell);
        return new boolean[]{joined(world, cell, path.endA()), joined(world, cell, path.endB())};
    }

    private static boolean joined(World world, Cell self, V3 point) {
        int bx = (int) Math.floor(point.x()), by = (int) Math.floor(point.y()), bz = (int) Math.floor(point.z());
        for (int x = bx - 1; x <= bx; x++) {
            for (int y = by - 1; y <= by; y++) {
                for (int z = bz - 1; z <= bz; z++) {
                    Cell c = new Cell(x, y, z);
                    if (c.equals(self)) continue;
                    Piece p = world.railAt(c);
                    if (p == null) continue;
                    RailPath path = p.pathAt(c);
                    if (path.endA().distance(point) <= TOUCH || path.endB().distance(point) <= TOUCH) return true;
                }
            }
        }
        return false;
    }

    /**
     * The wrench's choices for a cell: every piece that still meets each neighbour the current piece
     * meets (both ends' neighbours when it has two), smoothly. The current piece is always among them.
     */
    public static List<Piece> alternatives(World world, Cell cell, Piece current) {
        RailPath path = current.pathAt(cell);
        List<OpenEnd> neighbours = new ArrayList<>();
        for (V3[] end : new V3[][]{{path.endA(), path.tangentA().scale(-1)}, {path.endB(), path.tangentB()}}) {
            V3 joinedOut = neighbourOut(world, cell, end[0]);
            if (joinedOut != null) neighbours.add(new OpenEnd(cell, end[0], joinedOut));
        }
        List<Piece> out = new ArrayList<>();
        out.add(current);
        for (RailShape shape : RailShape.values()) {
            for (Facing facing : Facing.values()) {
                Piece p = new Piece(shape, facing);
                if (p.equals(current) || matches(p.pathAt(cell), neighbours) != neighbours.size()) continue;
                // A flat piece facing north is the one facing south run backward: offer the line once.
                if (out.stream().anyMatch(q -> sameLine(q, p))) continue;
                out.add(p);
            }
        }
        return out;
    }

    /** True if the two pieces lay the same path, either way round. */
    static boolean sameLine(Piece a, Piece b) {
        RailPath pa = RailPath.local(a.shape(), a.facing()), pb = RailPath.local(b.shape(), b.facing());
        if (pa.pointCount() != pb.pointCount()) return false;
        boolean forward = true, backward = true;
        int n = pa.pointCount();
        for (int i = 0; i < n; i++) {
            if (pa.point(i).distance(pb.point(i)) > 1e-9) forward = false;
            if (pa.point(i).distance(pb.point(n - 1 - i)) > 1e-9) backward = false;
        }
        return forward || backward;
    }

    /** The direction a neighbour's line arrives at {@code point}, pointing into this cell, or null. */
    private static V3 neighbourOut(World world, Cell self, V3 point) {
        int bx = (int) Math.floor(point.x()), by = (int) Math.floor(point.y()), bz = (int) Math.floor(point.z());
        for (int x = bx - 1; x <= bx; x++) {
            for (int y = by - 1; y <= by; y++) {
                for (int z = bz - 1; z <= bz; z++) {
                    Cell c = new Cell(x, y, z);
                    if (c.equals(self)) continue;
                    Piece p = world.railAt(c);
                    if (p == null) continue;
                    RailPath path = p.pathAt(c);
                    if (path.endA().distance(point) <= TOUCH) return path.tangentA().scale(-1);
                    if (path.endB().distance(point) <= TOUCH) return path.tangentB();
                }
            }
        }
        return null;
    }
}
