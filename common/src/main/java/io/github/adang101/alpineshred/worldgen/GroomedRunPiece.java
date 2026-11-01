package io.github.adang101.alpineshred.worldgen;

import io.github.adang101.alpineshred.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.util.ArrayList;
import java.util.List;

/**
 * One groomed run (design doc section 12, {@code GroomedRunPiece}). Built a chunk at a time, only
 * inside that chunk: each column near the path is cut or filled (by at most 2 blocks) to the
 * smoothed profile and topped with groomed snow over two snow blocks, with logs, leaves and plants
 * cleared for 10 blocks above; the edges fade into snow layers over 3 blocks; slope markers in the
 * difficulty's color stand on both edges every 24 blocks.
 */
public class GroomedRunPiece extends StructurePiece {
    private static final int CLEAR_ABOVE = 10;
    private final RunPlanner.Plan plan;

    public GroomedRunPiece(RunPlanner.Plan plan) {
        super(ModStructures.GROOMED_RUN_PIECE.get(), 0, box(plan));
        this.plan = plan;
        setOrientation(null);
    }

    public GroomedRunPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(ModStructures.GROOMED_RUN_PIECE.get(), tag);
        List<double[]> points = new ArrayList<>();
        ListTag list = tag.getList("points", Tag.TAG_FLOAT);
        for (int i = 0; i + 2 < list.size(); i += 3) {
            points.add(new double[]{list.getFloat(i), list.getFloat(i + 1), list.getFloat(i + 2)});
        }
        this.plan = new RunPlanner.Plan(RunPlanner.Difficulty.byId(tag.getString("difficulty")), tag.getInt("width"),
                List.copyOf(points), tag.getInt("run_out"));
    }

    private static BoundingBox box(RunPlanner.Plan plan) {
        int[] b = RunShape.bounds(plan);
        double low = Double.MAX_VALUE, high = -Double.MAX_VALUE;
        for (double[] p : plan.points()) {
            low = Math.min(low, p[2]);
            high = Math.max(high, p[2]);
        }
        return new BoundingBox(b[0], (int) Math.floor(low) - 4 - RunShape.MAX_CUT, b[1], b[2], (int) Math.ceil(high) + CLEAR_ABOVE + 2, b[3]);
    }

    public RunPlanner.Plan plan() {
        return plan;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        ListTag list = new ListTag();
        for (double[] p : plan.points()) {
            list.add(FloatTag.valueOf((float) p[0]));
            list.add(FloatTag.valueOf((float) p[1]));
            list.add(FloatTag.valueOf((float) p[2]));
        }
        tag.put("points", list);
        tag.putString("difficulty", plan.difficulty().id);
        tag.putInt("width", plan.width());
        tag.putInt("run_out", plan.runOutFrom());
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
                            BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minX = Math.max(chunkBox.minX(), boundingBox.minX()), maxX = Math.min(chunkBox.maxX(), boundingBox.maxX());
        int minZ = Math.max(chunkBox.minZ(), boundingBox.minZ()), maxZ = Math.min(chunkBox.maxZ(), boundingBox.maxZ());
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int ground = groundAt(level, pos, x, z);
                if (!level.getFluidState(pos.set(x, ground, z)).isEmpty()) continue; // never into water
                RunShape.Column c = RunShape.column(plan, x, z, ground);
                switch (c.kind()) {
                    case GROOMED -> groom(level, pos, x, z, ground, c.surface());
                    case FADE -> fade(level, pos, x, z, ground, c.fade());
                    default -> {
                    }
                }
            }
        }
        BlockState marker = markerBlock();
        for (int[] m : RunShape.markers(plan)) {
            if (!chunkBox.isInside(m[0], chunkBox.minY(), m[1])) continue;
            int y = groundAt(level, pos, m[0], m[1]);
            pos.set(m[0], y, m[1]);
            if (level.getBlockState(pos).canBeReplaced() && level.getFluidState(pos).isEmpty()) {
                level.setBlock(pos, marker, Block.UPDATE_CLIENTS);
            }
        }
    }

    /**
     * The first block above the ground at (x, z): below any tree that a neighbouring chunk's
     * decoration has already grown into this one, and below plants and snow layers.
     */
    private static int groundAt(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z) {
        int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
        int floor = Math.max(level.getMinBuildHeight(), y - 40);
        while (y > floor) {
            BlockState below = level.getBlockState(pos.set(x, y - 1, z));
            if (!(below.is(BlockTags.LOGS) || below.is(BlockTags.LEAVES) || below.is(Blocks.SNOW)
                    || (below.canBeReplaced() && below.getFluidState().isEmpty()))) break;
            y--;
        }
        return y;
    }

    private void groom(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int ground, int surface) {
        // Cut: everything from the new surface up to the old one goes.
        for (int y = surface; y < ground; y++) level.setBlock(pos.set(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        // Fill and top: two snow blocks under the groomed snow.
        for (int y = Math.min(ground, surface - 3); y < surface - 1; y++) {
            level.setBlock(pos.set(x, y, z), Blocks.SNOW_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        level.setBlock(pos.set(x, surface - 1, z), ModBlocks.GROOMED_SNOW.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        // Clear the air above of trees, plants and snow layers.
        for (int y = surface; y < surface + CLEAR_ABOVE; y++) {
            BlockState s = level.getBlockState(pos.set(x, y, z));
            if (s.isAir()) continue;
            if (s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.canBeReplaced() || s.is(Blocks.SNOW) || s.is(BlockTags.SAPLINGS)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Snow layers thinning out from the groomed edge: 7 at the edge to 1 at the outside. */
    private void fade(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int ground, double fade) {
        int layers = Math.max(1, Math.min(7, (int) Math.round(7 * (1 - fade))));
        pos.set(x, ground, z);
        BlockState here = level.getBlockState(pos);
        if (!(here.isAir() || here.is(Blocks.SNOW))) return;
        BlockState snow = Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers);
        if (snow.canSurvive(level, pos)) level.setBlock(pos, snow, Block.UPDATE_CLIENTS);
    }

    private BlockState markerBlock() {
        Block b = switch (plan.difficulty()) {
            case GREEN -> ModBlocks.SLOPE_MARKER_GREEN.get();
            case BLUE -> ModBlocks.SLOPE_MARKER_BLUE.get();
            case BLACK -> ModBlocks.SLOPE_MARKER_BLACK.get();
        };
        return b.defaultBlockState().setValue(io.github.adang101.alpineshred.park.block.ParkDecorBlock.FACING, Direction.NORTH);
    }
}
