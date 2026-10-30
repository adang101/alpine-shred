package io.github.adang101.alpineshred.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * {@code alpineshred:groomed_run} (design doc section 12): a run planned down the terrain from just
 * uphill of the start chunk's centre ({@link RunPlanner}), built by one {@link GroomedRunPiece}.
 */
public class GroomedRunStructure extends Structure {
    public static final Codec<GroomedRunStructure> CODEC = simpleCodec(GroomedRunStructure::new);
    /** How far uphill of the start the run may begin. */
    static final int UPHILL = 88;

    public GroomedRunStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!WorldgenToggle.GROOMED_RUNS.allows(context)) return Optional.empty();
        RunPlanner.Plan plan = plan(context);
        if (plan == null) return Optional.empty();
        double[] top = plan.points().get(0);
        BlockPos at = new BlockPos((int) Math.floor(top[0]), (int) Math.round(top[2]), (int) Math.floor(top[1]));
        return Optional.of(new GenerationStub(at, builder -> builder.addPiece(new GroomedRunPiece(plan))));
    }

    /** The run for this start, or null where none fits. */
    static RunPlanner.Plan plan(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        int cx = chunk.getMiddleBlockX(), cz = chunk.getMiddleBlockZ();
        GeneratorTerrain terrain = new GeneratorTerrain(context);
        if (!terrain.allowedNear(cx, cz)) return null;
        int sea = context.chunkGenerator().getSeaLevel();
        int[] top = RunPlanner.uphillStart(terrain, cx, cz, UPHILL);
        return RunPlanner.plan(terrain, sea, top[0], top[1], cx, cz, context.random().nextLong());
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.GROOMED_RUN.get();
    }
}
