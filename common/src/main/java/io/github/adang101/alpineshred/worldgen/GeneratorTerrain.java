package io.github.adang101.alpineshred.worldgen;

import net.minecraft.core.QuartPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link RunPlanner.Terrain} over a structure's generation context: heights from the chunk
 * generator (before anything is placed), water where the ocean floor is below the surface, and the
 * structure's biome tag at the surface. Heights are cached: the planner asks for the same columns
 * more than once.
 */
final class GeneratorTerrain implements RunPlanner.Terrain {
    private final Structure.GenerationContext context;
    private final Map<Long, int[]> columns = new HashMap<>();

    GeneratorTerrain(Structure.GenerationContext context) {
        this.context = context;
    }

    /** {surface, ocean floor} at (x, z). */
    private int[] column(int x, int z) {
        return columns.computeIfAbsent((long) x << 32 | (z & 0xFFFFFFFFL), k -> new int[]{
                context.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState()),
                context.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState())});
    }

    @Override
    public int height(int x, int z) {
        return column(x, z)[0];
    }

    @Override
    public boolean water(int x, int z) {
        int[] c = column(x, z);
        return c[1] < c[0];
    }

    /**
     * A quick look before planning, since every candidate chunk of the structure set is asked,
     * snowy or not: the structure's biome at (x, z) or 48 blocks away along either axis.
     */
    boolean allowedNear(int x, int z) {
        if (allowed(x, z)) return true;
        for (int[] d : new int[][]{{48, 0}, {-48, 0}, {0, 48}, {0, -48}}) {
            if (allowed(x + d[0], z + d[1])) return true;
        }
        return false;
    }

    @Override
    public boolean allowed(int x, int z) {
        int y = height(x, z);
        return context.validBiome().test(context.biomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z),
                context.randomState().sampler()));
    }
}
