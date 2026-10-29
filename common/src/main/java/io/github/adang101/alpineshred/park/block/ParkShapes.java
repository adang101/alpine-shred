package io.github.adang101.alpineshred.park.block;

import io.github.adang101.alpineshred.park.Facing;
import io.github.adang101.alpineshred.park.PipeArc;
import io.github.adang101.alpineshred.park.RailPath;
import io.github.adang101.alpineshred.park.RailShape;
import io.github.adang101.alpineshred.park.RampSlope;
import io.github.adang101.alpineshred.park.V3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;

/**
 * Collision and outline shapes of the park blocks, built once from the pure geometry and cached
 * (design doc sections 10 and 11). Pieces are authored facing north and turned like their models.
 */
public final class ParkShapes {
    private static final double PX = 1 / 16.0;
    private static final Map<String, VoxelShape> CACHE = new HashMap<>();

    private ParkShapes() {
    }

    private static synchronized VoxelShape cached(String key, java.util.function.Supplier<VoxelShape> build) {
        return CACHE.computeIfAbsent(key, k -> build.get());
    }

    /** An axis-aligned box given in north-facing local coordinates (blocks), turned to {@code facing}. */
    static VoxelShape box(Facing facing, double x0, double y0, double z0, double x1, double y1, double z1) {
        V3 a = facing.rotateLocal(new V3(x0, y0, z0));
        V3 b = facing.rotateLocal(new V3(x1, y1, z1));
        return Shapes.box(Math.min(a.x(), b.x()), Math.min(y0, y1), Math.min(a.z(), b.z()),
                Math.max(a.x(), b.x()), Math.max(y0, y1), Math.max(a.z(), b.z()));
    }

    // ---- rails ------------------------------------------------------------------------------------

    /**
     * Section 10, "Collision and selection": a thin bar at the path's height only, its top on the
     * path, so riders step and drop onto it and walkers step over it.
     */
    public static VoxelShape railBar(RailShape shape, Facing facing) {
        return cached("rail_bar/" + shape + "/" + facing, () -> bar(RailPath.local(shape, facing), 1.0 * PX, 2.0 * PX));
    }

    /** The outline: the bar one pixel larger all round, for easy wrench use. */
    public static VoxelShape railOutline(RailShape shape, Facing facing) {
        return cached("rail_outline/" + shape + "/" + facing, () -> bar(RailPath.local(shape, facing), 2.0 * PX, 4.0 * PX));
    }

    private static VoxelShape bar(RailPath path, double halfWidth, double height) {
        int segments = Math.max(1, (int) Math.ceil(path.length() / (2 * PX)));
        boolean straightLevel = path.pointCount() == 2 && Math.abs(path.endA().y() - path.endB().y()) < 1e-9;
        if (straightLevel) segments = 1;
        VoxelShape shape = Shapes.empty();
        for (int k = 0; k < segments; k++) {
            V3 p0 = path.pointAt(path.length() * k / segments);
            V3 p1 = path.pointAt(path.length() * (k + 1) / segments);
            double top = Math.max(p0.y(), p1.y());
            double x0 = Math.min(p0.x(), p1.x()), x1 = Math.max(p0.x(), p1.x());
            double z0 = Math.min(p0.z(), p1.z()), z1 = Math.max(p0.z(), p1.z());
            // Widen across the run only (straight runs), or both ways (curves).
            boolean alongZ = Math.abs(p1.z() - p0.z()) >= Math.abs(p1.x() - p0.x());
            if (alongZ || path.pointCount() > 2) {
                x0 -= halfWidth;
                x1 += halfWidth;
            }
            if (!alongZ || path.pointCount() > 2) {
                z0 -= halfWidth;
                z1 += halfWidth;
            }
            shape = Shapes.or(shape, Shapes.box(clamp(x0), Math.max(0, top - height), clamp(z0), clamp(x1), Math.min(1, Math.max(top, 1.0e-3)), clamp(z1)));
        }
        return shape.optimize();
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    /** A rail post: a vertical pole, four pixels square. */
    public static final VoxelShape POST = Shapes.box(6 * PX, 0, 6 * PX, 10 * PX, 1, 10 * PX);

    // ---- boxes and coping ---------------------------------------------------------------------------

    public static final VoxelShape BOX_LOW = Shapes.box(0, 0, 0, 1, 0.5, 1);
    public static final VoxelShape BOX_FULL = Shapes.block();

    // ---- ramps ------------------------------------------------------------------------------------

    /**
     * Section 11: eight 2-pixel steps per block, rising toward {@code facing}; 28 shapes in all
     * (seven slope and step pairs, four facings).
     */
    public static VoxelShape ramp(RampSlope slope, int step, Facing facing) {
        return cached("ramp/" + slope + "/" + step + "/" + facing, () -> {
            VoxelShape shape = Shapes.empty();
            for (int column = 0; column < RampSlope.STEPS; column++) {
                double top = slope.columnTop(step, column);
                double z1 = 1 - column / (double) RampSlope.STEPS;
                double z0 = 1 - (column + 1) / (double) RampSlope.STEPS;
                shape = Shapes.or(shape, box(facing, 0, 0, z0, 1, top, z1));
            }
            return shape.optimize();
        });
    }

    // ---- quarter pipes ----------------------------------------------------------------------------

    /**
     * Section 11, {@code PipeShapes.generate}: the arc sampled in sixteen 1-pixel columns per block,
     * unioned. The wall rises toward {@code facing}.
     */
    public static VoxelShape pipe(int radius, int index, Facing facing) {
        return cached("pipe/" + radius + "/" + index + "/" + facing, () -> {
            VoxelShape shape = Shapes.empty();
            for (int column = 0; column < PipeArc.COLUMNS; column++) {
                double top = PipeArc.columnTop(radius, index, column);
                if (top <= 0) continue;
                double z1 = 1 - column / (double) PipeArc.COLUMNS;
                double z0 = 1 - (column + 1) / (double) PipeArc.COLUMNS;
                shape = Shapes.or(shape, box(facing, 0, 0, z0, 1, top, z1));
            }
            return shape.isEmpty() ? Shapes.box(0, 0, 0, 1, 1.0e-3, 1) : shape.optimize();
        });
    }

    // ---- decor --------------------------------------------------------------------------------------

    /** A slope marker: a post with a sign plate across it (authored facing north). */
    public static VoxelShape marker(Facing facing) {
        return cached("marker/" + facing, () -> Shapes.or(Shapes.box(7 * PX, 0, 7 * PX, 9 * PX, 1, 9 * PX),
                box(facing, 4 * PX, 10 * PX, 7 * PX, 12 * PX, 15 * PX, 9 * PX)));
    }

    /** The park flag's pole. */
    public static VoxelShape flag(Facing facing) {
        return Shapes.box(7 * PX, 0, 7 * PX, 9 * PX, 1, 9 * PX);
    }

    /** The start gate: a frame across the block with a top bar. */
    public static VoxelShape gate(Facing facing) {
        return cached("gate/" + facing, () -> Shapes.or(box(facing, 0, 0, 7 * PX, 2 * PX, 1, 9 * PX),
                box(facing, 14 * PX, 0, 7 * PX, 1, 1, 9 * PX), box(facing, 0, 13 * PX, 7 * PX, 1, 1, 9 * PX)));
    }
}
