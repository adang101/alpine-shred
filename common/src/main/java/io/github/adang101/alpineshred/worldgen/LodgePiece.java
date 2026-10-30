package io.github.adang101.alpineshred.worldgen;

import io.github.adang101.alpineshred.AlpineShred;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * The resort's lodge (design doc section 12, "Resort composition"): the template
 * {@code alpineshred:resort/lodge} at the bottom of the run. Its data marker {@code lodge_chest}
 * becomes a chest with the {@code alpineshred:chests/lodge} loot table, seeded per lodge.
 */
public class LodgePiece extends TemplateStructurePiece {
    public static final ResourceLocation TEMPLATE = AlpineShred.id("resort/lodge");
    public static final ResourceLocation LOOT = AlpineShred.id("chests/lodge");

    public LodgePiece(StructureTemplateManager templates, BlockPos pos, Rotation rotation) {
        super(ModStructures.LODGE_PIECE.get(), 0, templates, TEMPLATE, TEMPLATE.toString(), settings(rotation), pos);
    }

    public LodgePiece(StructureTemplateManager templates, CompoundTag tag) {
        super(ModStructures.LODGE_PIECE.get(), tag, templates, id -> settings(Rotation.valueOf(tag.getString("Rot"))));
    }

    private static StructurePlaceSettings settings(Rotation rotation) {
        return new StructurePlaceSettings().setRotation(rotation).setMirror(Mirror.NONE)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putString("Rot", placeSettings.getRotation().name());
    }

    /** The template, then a foundation down to the ground under its footprint (no terrain adaptation: the run shares the structure). */
    @Override
    public void postProcess(net.minecraft.world.level.WorldGenLevel level, net.minecraft.world.level.StructureManager structures,
                            net.minecraft.world.level.chunk.ChunkGenerator generator, RandomSource random, BoundingBox chunkBox,
                            net.minecraft.world.level.ChunkPos chunkPos, BlockPos pivot) {
        super.postProcess(level, structures, generator, random, chunkBox, chunkPos, pivot);
        BoundingBox box = getBoundingBox();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Math.max(box.minX(), chunkBox.minX()); x <= Math.min(box.maxX(), chunkBox.maxX()); x++) {
            for (int z = Math.max(box.minZ(), chunkBox.minZ()); z <= Math.min(box.maxZ(), chunkBox.maxZ()); z++) {
                for (int y = box.minY() - 1; y > box.minY() - 24 && y > level.getMinBuildHeight(); y--) {
                    pos.set(x, y, z);
                    net.minecraft.world.level.block.state.BlockState s = level.getBlockState(pos);
                    if (s.isSolid() && level.getFluidState(pos).isEmpty()) break;
                    level.setBlock(pos, Blocks.COBBLESTONE.defaultBlockState(), 2);
                }
            }
        }
    }

    @Override
    protected void handleDataMarker(String marker, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {
        if (!"lodge_chest".equals(marker)) return;
        level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, placeSettings.getRotation().rotate(net.minecraft.core.Direction.NORTH)), 2);
        if (level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity chest) chest.setLootTable(LOOT, random.nextLong());
    }
}
