package io.github.adang101.alpineshred.worldgen;

import io.github.adang101.alpineshred.lift.LiftNodeBlockEntity;
import io.github.adang101.alpineshred.lift.LiftTowerHeadBlockEntity;
import io.github.adang101.alpineshred.lift.PendingLine;
import io.github.adang101.alpineshred.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A resort's lift line (design doc sections 12 and 13): the drive station and the return station on small
 * platforms, and a pole and head for each tower between. The nodes are absolute world positions, planned
 * by {@link ResortLiftPlanner} before anything is built.
 *
 * <p>Pieces cannot touch saved data while a chunk generates, so every node block entity gets the
 * {@link PendingLine} (a UUID derived from the structure start, so regenerating gives the same id) and
 * the first one to run its load check creates the line (phase 8 plan, decision 5). The drive command in the
 * pending line says whether the line starts running; otherwise a lever beside the drive station is left off.
 *
 * <p>Built a chunk at a time, only inside that chunk, and each block is a function of its own column and
 * the terrain below the structure (the run piece's {@code groundAt}), so any generation order gives the same result.
 */
public class LiftLinePiece extends StructurePiece {
    private static final int CLEAR_ABOVE = 5, MAX_FILL = 24;
    private final UUID id;
    private final List<BlockPos> nodes;
    private final boolean powered;
    private final double target;
    private final boolean lever;

    public LiftLinePiece(UUID id, List<BlockPos> nodes, boolean powered, double target, boolean lever) {
        super(ModStructures.LIFT_LINE_PIECE.get(), 0, box(nodes));
        this.id = id;
        this.nodes = List.copyOf(nodes);
        this.powered = powered;
        this.target = target;
        this.lever = lever;
        setOrientation(null);
    }

    public LiftLinePiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(ModStructures.LIFT_LINE_PIECE.get(), tag);
        this.id = tag.getUUID("id");
        List<BlockPos> list = new ArrayList<>();
        for (Tag t : tag.getList("nodes", Tag.TAG_COMPOUND)) list.add(NbtUtils.readBlockPos((CompoundTag) t));
        this.nodes = List.copyOf(list);
        this.powered = tag.getBoolean("powered");
        this.target = tag.getDouble("target");
        this.lever = tag.getBoolean("lever");
    }

    /** The nodes' footprint with a platform's width round it, from the lowest pole base to the clearing above the highest node. */
    static BoundingBox box(List<BlockPos> nodes) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : nodes) {
            minX = Math.min(minX, p.getX());
            maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY());
            maxY = Math.max(maxY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxZ = Math.max(maxZ, p.getZ());
        }
        int pad = ResortLiftPlanner.PLATFORM_HALF + 1;
        return new BoundingBox(minX - pad, minY - ResortLiftPlanner.MAX_POLE - 8, minZ - pad, maxX + pad, maxY + CLEAR_ABOVE + 1, maxZ + pad);
    }

    public UUID lineId() {
        return id;
    }

    public List<BlockPos> nodes() {
        return nodes;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putUUID("id", id);
        ListTag list = new ListTag();
        for (BlockPos p : nodes) list.add(NbtUtils.writeBlockPos(p));
        tag.put("nodes", list);
        tag.putBoolean("powered", powered);
        tag.putDouble("target", target);
        tag.putBoolean("lever", lever);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
                            BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        PendingLine pending = new PendingLine(id, nodes, powered, target);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int last = nodes.size() - 1;
        for (int i = 0; i <= last; i++) {
            BlockPos node = nodes.get(i);
            if (i == 0 || i == last) {
                BlockPos toward = nodes.get(i == 0 ? 1 : last - 1);
                station(level, chunkBox, pos, node, toward, i == 0, pending);
            } else if (chunkBox.isInside(node.getX(), chunkBox.minY(), node.getZ())) {
                tower(level, pos, node, nodes.get(i - 1), nodes.get(i + 1), pending);
            }
        }
    }

    private void tower(WorldGenLevel level, BlockPos.MutableBlockPos pos, BlockPos head, BlockPos prev, BlockPos next, PendingLine pending) {
        int base = Math.max(boundingBox.minY(), GroomedRunPiece.groundAt(level, pos, head.getX(), head.getZ()));
        BlockState pole = ModBlocks.LIFT_TOWER_POLE.get().defaultBlockState();
        for (int y = base; y < head.getY(); y++) level.setBlock(pos.set(head.getX(), y, head.getZ()), pole, Block.UPDATE_CLIENTS);
        level.setBlock(head, ModBlocks.LIFT_TOWER_HEAD.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        BlockEntity be = level.getBlockEntity(head);
        if (be instanceof LiftTowerHeadBlockEntity headBe) {
            headBe.setLink(id, prev, next);
            headBe.setPending(pending);
        }
    }

    /** A 7 by 7 platform (spruce rim, stone brick floor) with the station block on it, and the 5 blocks above it cleared. */
    private void station(WorldGenLevel level, BoundingBox chunkBox, BlockPos.MutableBlockPos pos, BlockPos station, BlockPos toward, boolean drive,
                         PendingLine pending) {
        int half = ResortLiftPlanner.PLATFORM_HALF, floor = station.getY() - 1;
        BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState(), planks = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = Math.max(chunkBox.minX(), station.getX() - half); x <= Math.min(chunkBox.maxX(), station.getX() + half); x++) {
            for (int z = Math.max(chunkBox.minZ(), station.getZ() - half); z <= Math.min(chunkBox.maxZ(), station.getZ() + half); z++) {
                int ground = GroomedRunPiece.groundAt(level, pos, x, z);
                for (int y = Math.max(ground, floor - MAX_FILL); y < floor; y++) level.setBlock(pos.set(x, y, z), bricks, Block.UPDATE_CLIENTS);
                boolean rim = Math.abs(x - station.getX()) == half || Math.abs(z - station.getZ()) == half;
                level.setBlock(pos.set(x, floor, z), rim ? planks : bricks, Block.UPDATE_CLIENTS);
                for (int y = station.getY(); y <= station.getY() + CLEAR_ABOVE; y++) {
                    pos.set(x, y, z);
                    if (!level.getBlockState(pos).isAir()) level.setBlock(pos, air, Block.UPDATE_CLIENTS);
                }
            }
        }
        if (chunkBox.isInside(station.getX(), chunkBox.minY(), station.getZ())) {
            BlockState state = (drive ? ModBlocks.LIFT_DRIVE_STATION : ModBlocks.LIFT_RETURN_STATION).get().defaultBlockState();
            level.setBlock(station, state, Block.UPDATE_CLIENTS);
            if (level.getBlockEntity(station) instanceof LiftNodeBlockEntity be) be.setPending(pending);
        }
        if (drive && lever) {
            // Behind the station, away from the line.
            int dx = toward.getX() - station.getX(), dz = toward.getZ() - station.getZ();
            Direction back = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.WEST : Direction.EAST) : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
            BlockPos at = station.relative(back);
            if (chunkBox.isInside(at.getX(), chunkBox.minY(), at.getZ())) {
                level.setBlock(at, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR)
                        .setValue(LeverBlock.FACING, back.getOpposite()).setValue(LeverBlock.POWERED, false), Block.UPDATE_CLIENTS);
            }
        }
    }
}
