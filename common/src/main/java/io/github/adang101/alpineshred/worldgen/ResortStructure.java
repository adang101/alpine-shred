package io.github.adang101.alpineshred.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.List;
import java.util.Optional;

/**
 * {@code alpineshred:ski_resort} (design doc section 12, "Resort composition"): a groomed run and,
 * at its bottom, the lodge, turned to face up the run. The lift line between them comes with phase
 * 8 (section 13). The run keeps 28 blocks of its reach free for the lodge.
 */
public class ResortStructure extends Structure {
    public static final Codec<ResortStructure> CODEC = simpleCodec(ResortStructure::new);
    static final int LODGE_ROOM = 28;
    /** The lodge template's footprint (x by z), its door in the middle of the north (z = 0) wall. */
    static final int LODGE_W = 24, LODGE_D = 18;

    public ResortStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!WorldgenToggle.RESORTS.allows(context)) return Optional.empty();
        ChunkPos chunk = context.chunkPos();
        int cx = chunk.getMiddleBlockX(), cz = chunk.getMiddleBlockZ();
        GeneratorTerrain terrain = new GeneratorTerrain(context);
        if (!terrain.allowedNear(cx, cz)) {
            RunPlanner.noteFailure(RunPlanner.Failure.NOT_NEAR_BIOME);
            return Optional.empty();
        }
        RunPlanner.Plan plan = RunPlanner.planThrough(terrain, context.chunkGenerator().getSeaLevel(), cx, cz,
                GroomedRunStructure.UPHILL - LODGE_ROOM, context.random().nextLong(), RunPlanner.MAX_REACH - LODGE_ROOM);
        if (plan == null) return Optional.empty();
        List<double[]> pts = plan.points();
        double[] end = pts.get(pts.size() - 1), before = pts.get(Math.max(0, pts.size() - 3));
        double dx = end[0] - before[0], dz = end[1] - before[1], len = Math.max(1.0e-6, Math.hypot(dx, dz));
        dx /= len;
        dz /= len;
        // The lodge stands past the end of the run, its door (north wall) toward the run.
        Rotation rotation = facingUphill(dx, dz);
        double lx = end[0] + dx * (LODGE_D / 2.0 + 4), lz = end[1] + dz * (LODGE_D / 2.0 + 4);
        // Its floor flush with the end of the run: the template's foundation row two below the groomed surface.
        BlockPos corner = cornerFor(rotation, (int) Math.floor(lx), (int) Math.round(end[2]) - 2, (int) Math.floor(lz));
        double[] first = pts.get(0);
        BlockPos at = new BlockPos((int) Math.floor(first[0]), (int) Math.round(first[2]), (int) Math.floor(first[1]));
        return Optional.of(new GenerationStub(at, builder -> {
            builder.addPiece(new GroomedRunPiece(plan));
            builder.addPiece(new LodgePiece(context.structureTemplateManager(), corner, rotation));
        }));
    }

    /** The rotation that turns the template's north wall (its door) against the run's direction (dx, dz). */
    static Rotation facingUphill(double dx, double dz) {
        // The run arrives travelling (dx, dz); the door must face back along -(dx, dz).
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? Rotation.COUNTERCLOCKWISE_90 : Rotation.CLOCKWISE_90;
        return dz > 0 ? Rotation.NONE : Rotation.CLOCKWISE_180;
    }

    /** The template origin that centres the rotated footprint on (x, z). */
    static BlockPos cornerFor(Rotation rotation, int x, int y, int z) {
        int w = LODGE_W, d = LODGE_D;
        return switch (rotation) {
            case NONE -> new BlockPos(x - w / 2, y, z - d / 2);
            case CLOCKWISE_90 -> new BlockPos(x + d / 2, y, z - w / 2);
            case CLOCKWISE_180 -> new BlockPos(x + w / 2, y, z + d / 2);
            case COUNTERCLOCKWISE_90 -> new BlockPos(x - d / 2, y, z + w / 2);
        };
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.SKI_RESORT.get();
    }
}
