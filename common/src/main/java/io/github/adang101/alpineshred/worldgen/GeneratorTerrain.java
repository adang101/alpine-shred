package io.github.adang101.alpineshred.worldgen;

import net.minecraft.core.QuartPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link RunPlanner.Terrain} over a structure's generation context, before anything is built.
 *
 * <p>Heights come from the chunk generator's noise on a lattice every {@link #LATTICE} blocks,
 * interpolated between: one noise column costs about a millisecond, and the planner looks at
 * hundreds of points. The run's profile is smoothed over 10 blocks and each column is still built
 * against its real ground (cut or fill of at most 2 blocks), so the lattice only steers. Water is a
 * surface at or below sea level (the generator's surface counts water as ground, and above sea
 * level there is next to none before features). The biome is the structure's tag at the surface,
 * cached per quart column.
 */
final class GeneratorTerrain implements RunPlanner.Terrain {
    static final int LATTICE = 8;
    private final Structure.GenerationContext context;
    private final int seaLevel;
    private final Map<Long, Integer> samples = new HashMap<>();
    private final Map<Long, Boolean> biomes = new HashMap<>();
    private int columns;

    GeneratorTerrain(Structure.GenerationContext context) {
        this.context = context;
        this.seaLevel = context.chunkGenerator().getSeaLevel();
    }

    private static long key(int a, int b) {
        return (long) a << 32 | (b & 0xFFFFFFFFL);
    }

    /** The real surface at lattice point (i, j), i.e. block (i * LATTICE, j * LATTICE). */
    private int sample(int i, int j) {
        return samples.computeIfAbsent(key(i, j), k -> {
            columns++;
            return context.chunkGenerator().getBaseHeight(i * LATTICE, j * LATTICE, Heightmap.Types.WORLD_SURFACE_WG,
                    context.heightAccessor(), context.randomState());
        });
    }

    @Override
    public int height(int x, int z) {
        int i = Math.floorDiv(x, LATTICE), j = Math.floorDiv(z, LATTICE);
        double fx = (x - i * LATTICE) / (double) LATTICE, fz = (z - j * LATTICE) / (double) LATTICE;
        if (fx == 0 && fz == 0) return sample(i, j);
        double north = sample(i, j) + (sample(i + 1, j) - sample(i, j)) * fx;
        double south = sample(i, j + 1) + (sample(i + 1, j + 1) - sample(i, j + 1)) * fx;
        return (int) Math.round(north + (south - north) * fz);
    }

    @Override
    public boolean water(int x, int z) {
        return height(x, z) <= seaLevel;
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
        int qx = QuartPos.fromBlock(x), qz = QuartPos.fromBlock(z);
        return biomes.computeIfAbsent(key(qx, qz), k -> context.validBiome().test(context.biomeSource().getNoiseBiome(qx,
                QuartPos.fromBlock(height(x, z)), qz, context.randomState().sampler())));
    }

    /** Noise columns sampled so far. */
    int columns() {
        return columns;
    }
}
