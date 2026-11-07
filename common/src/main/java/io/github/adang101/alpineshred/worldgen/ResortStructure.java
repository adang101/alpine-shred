package io.github.adang101.alpineshred.worldgen;

import com.mojang.serialization.Codec;
import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.config.AlpineConfig;
import io.github.adang101.alpineshred.lift.LinkValidator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code alpineshred:ski_resort} (design doc section 12, "Resort composition"): a groomed run and,
 * at its bottom, the lodge, turned to face up the run. The lift line between them comes with phase
 * 8 (section 13). The lift is planned by {@link ResortLiftPlanner} and built by {@link LiftLinePiece}: a drive
 * station beside the top of the run, a return station beside the lodge and towers between; it is skipped (and
 * logged at debug level) whenever no valid line can be planned. The run keeps 28 blocks of its reach free for the lodge.
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
        LodgePiece lodge = new LodgePiece(context.structureTemplateManager(), corner, rotation);
        LiftLinePiece lift = planLift(context, terrain, plan, lodge.getBoundingBox());
        return Optional.of(new GenerationStub(at, builder -> {
            builder.addPiece(new GroomedRunPiece(plan));
            builder.addPiece(lodge);
            if (lift != null) builder.addPiece(lift);
        }));
    }

    /** The terrain as the lift planner's ground, passing on its prefetching. */
    private static ResortLiftPlanner.Ground ground(GeneratorTerrain terrain) {
        return new ResortLiftPlanner.Ground() {
            @Override
            public int height(int x, int z) {
                return terrain.height(x, z);
            }

            @Override
            public void prefetch(int[] xs, int[] zs, int n) {
                terrain.prefetch(xs, zs, n);
            }
        };
    }

    /** The resort's lift line, or null (logged at debug level) when no valid one can be planned. */
    private static LiftLinePiece planLift(GenerationContext context, GeneratorTerrain terrain, RunPlanner.Plan plan, BoundingBox lodge) {
        ChunkPos chunk = context.chunkPos();
        LinkValidator.Limits limits = new LinkValidator.Limits(AlpineConfig.LIFT_MAX_SPAN.get(), AlpineConfig.LIFT_MAX_HEADS.get(),
                AlpineConfig.LIFT_MAX_LENGTH.get(), AlpineConfig.LIFT_MAX_LINES_PER_LEVEL.get());
        ResortLiftPlanner.Outcome outcome = ResortLiftPlanner.plan(plan.points(), plan.reach(), ground(terrain),
                context.chunkGenerator().getSeaLevel(), limits, context.seed() ^ chunk.toLong());
        if (outcome.plan() == null) {
            AlpineShred.LOGGER.debug("Ski resort at {} gets no lift: {}", chunk, outcome.failure());
            return null;
        }
        List<BlockPos> nodes = new ArrayList<>();
        for (ResortLiftPlanner.Node n : outcome.plan().nodes()) nodes.add(new BlockPos(n.x(), n.y(), n.z()));
        UUID id = UUID.nameUUIDFromBytes(("alpineshred:resort_lift/" + context.seed() + "/" + chunk.x + "/" + chunk.z)
                .getBytes(StandardCharsets.UTF_8));
        boolean needsRedstone = AlpineConfig.LIFT_DRIVE_NEEDS_REDSTONE.get();
        List<Double> levels = AlpineConfig.LIFT_SPEED_LEVELS.get();
        LiftLinePiece piece = new LiftLinePiece(id, nodes, !needsRedstone, levels.get(Math.min(1, levels.size() - 1)), needsRedstone);
        BoundingBox box = piece.getBoundingBox();
        int reachBlocks = 7 * 16;
        if (box.minX() < chunk.getMinBlockX() - reachBlocks || box.maxX() > chunk.getMaxBlockX() + reachBlocks
                || box.minZ() < chunk.getMinBlockZ() - reachBlocks || box.maxZ() > chunk.getMaxBlockZ() + reachBlocks) {
            AlpineShred.LOGGER.debug("Ski resort at {} gets no lift: it would reach beyond 7 chunks of the start", chunk);
            return null;
        }
        if (box.minY() < context.heightAccessor().getMinBuildHeight() || box.maxY() >= context.heightAccessor().getMaxBuildHeight()) {
            AlpineShred.LOGGER.debug("Ski resort at {} gets no lift: it would leave the world's height", chunk);
            return null;
        }
        int half = ResortLiftPlanner.PLATFORM_HALF;
        for (BlockPos station : List.of(nodes.get(0), nodes.get(nodes.size() - 1))) {
            if (lodge.intersects(station.getX() - half, station.getZ() - half, station.getX() + half, station.getZ() + half)) {
                AlpineShred.LOGGER.debug("Ski resort at {} gets no lift: a station would stand on the lodge", chunk);
                return null;
            }
        }
        return piece;
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
