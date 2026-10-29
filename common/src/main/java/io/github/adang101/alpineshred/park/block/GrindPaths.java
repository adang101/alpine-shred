package io.github.adang101.alpineshred.park.block;

import io.github.adang101.alpineshred.park.Facing;
import io.github.adang101.alpineshred.park.GrindMaterial;
import io.github.adang101.alpineshred.park.GrindPath;
import io.github.adang101.alpineshred.park.GrindPhysics;
import io.github.adang101.alpineshred.park.RailPath;
import io.github.adang101.alpineshred.park.RailPlacement;
import io.github.adang101.alpineshred.park.V3;
import io.github.adang101.alpineshred.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WallSide;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The {@code GrindPathProvider} registry (design doc section 10, "Vanilla blocks as rails"): maps
 * blocks to grind paths. Built in: every rail piece, pipe coping, and blocks in
 * {@code #alpineshred:grindable_vanilla} (fences and walls by default), whose connection properties
 * give a straight path (two opposite sides, or one side at the end of a line) or a curve (two
 * adjacent sides) at their collision top. Other mods can {@link #register} more.
 */
public final class GrindPaths {
    /** Maps one block to a grind path, or returns null. */
    @FunctionalInterface
    public interface Provider {
        @Nullable
        GrindPath path(BlockGetter level, BlockPos pos, BlockState state);
    }

    private static final List<Provider> PROVIDERS = new CopyOnWriteArrayList<>();

    static {
        PROVIDERS.add((level, pos, state) -> state.getBlock() instanceof RailBlock rail ? rail.grindPath(state, pos) : null);
        PROVIDERS.add((level, pos, state) -> state.getBlock() instanceof PipeCopingBlock ? PipeCopingBlock.grindPath(state, pos) : null);
        PROVIDERS.add(GrindPaths::vanilla);
    }

    private GrindPaths() {
    }

    public static void register(Provider provider) {
        PROVIDERS.add(provider);
    }

    @Nullable
    public static GrindPath at(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return null;
        for (Provider p : PROVIDERS) {
            GrindPath path = p.path(level, pos, state);
            if (path != null) return path;
        }
        return null;
    }

    /** Paths in the blocks around a point (3 by 3 by 3): at most 27 lookups, as section 10 budgets 9 a tick per probe. */
    public static List<GrindPath> around(BlockGetter level, V3 point) {
        List<GrindPath> out = new ArrayList<>();
        int bx = (int) Math.floor(point.x()), by = (int) Math.floor(point.y()), bz = (int) Math.floor(point.z());
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = bx - 1; x <= bx + 1; x++) {
            for (int y = by - 1; y <= by + 1; y++) {
                for (int z = bz - 1; z <= bz + 1; z++) {
                    m.set(x, y, z);
                    GrindPath p = at(level, m.immutable());
                    if (p != null) out.add(p);
                }
            }
        }
        return out;
    }

    public static GrindPhysics.Lookup lookup(BlockGetter level) {
        return p -> around(level, p);
    }

    // ---- fences and walls -------------------------------------------------------------------------

    @Nullable
    private static GrindPath vanilla(BlockGetter level, BlockPos pos, BlockState state) {
        if (!state.is(ModTags.Blocks.GRINDABLE_VANILLA)) return null;
        Set<Facing> sides = EnumSet.noneOf(Facing.class);
        for (Facing f : Facing.values()) {
            if (connects(state, ParkStates.direction(f))) sides.add(f);
        }
        var shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) return null;
        double top = Math.min(1.5, shape.max(Direction.Axis.Y));
        GrindMaterial material = state.is(BlockTags.WOODEN_FENCES) ? GrindMaterial.WOOD : GrindMaterial.STONE;
        double friction = BoxMaterials.friction(state, material);
        RailPath local = vanillaPath(sides, top);
        if (local == null) return null;
        return new GrindPath(pos.asLong(), local.translate(pos.getX(), pos.getY(), pos.getZ()), material, friction);
    }

    private static boolean connects(BlockState state, Direction d) {
        if (state.getBlock() instanceof WallBlock) {
            var property = switch (d) {
                case NORTH -> WallBlock.NORTH_WALL;
                case EAST -> WallBlock.EAST_WALL;
                case SOUTH -> WallBlock.SOUTH_WALL;
                default -> WallBlock.WEST_WALL;
            };
            return state.hasProperty(property) && state.getValue(property) != WallSide.NONE;
        }
        var property = switch (d) {
            case NORTH -> CrossCollisionBlock.NORTH;
            case EAST -> CrossCollisionBlock.EAST;
            case SOUTH -> CrossCollisionBlock.SOUTH;
            default -> CrossCollisionBlock.WEST;
        };
        return state.hasProperty(property) && state.getValue(property);
    }

    /**
     * The local path for a fence or wall connected on {@code sides}: straight through two opposite
     * sides (or along the one side of a line's last post, out to the far edge), curved between two
     * adjacent sides; none for a lone post or a junction without a straight run.
     */
    @Nullable
    static RailPath vanillaPath(Set<Facing> sides, double top) {
        boolean ns = sides.contains(Facing.NORTH) && sides.contains(Facing.SOUTH);
        boolean ew = sides.contains(Facing.EAST) && sides.contains(Facing.WEST);
        if (ns || (sides.size() == 1 && (sides.contains(Facing.NORTH) || sides.contains(Facing.SOUTH)))) {
            return RailPath.straight(new V3(0.5, top, 1.0), new V3(0.5, top, 0.0));
        }
        if (ew || (sides.size() == 1 && (sides.contains(Facing.EAST) || sides.contains(Facing.WEST)))) {
            return RailPath.straight(new V3(0.0, top, 0.5), new V3(1.0, top, 0.5));
        }
        if (sides.size() == 2) {
            Facing[] two = sides.toArray(new Facing[0]);
            Facing curve = RailPlacement.curveConnecting(two[0], two[1]);
            if (curve == null) return null;
            RailPath north = new RailPath(RailPath.curve(top));
            List<V3> pts = new ArrayList<>();
            for (int i = 0; i < north.pointCount(); i++) pts.add(curve.rotateLocal(north.point(i)));
            return new RailPath(pts);
        }
        return null;
    }
}
