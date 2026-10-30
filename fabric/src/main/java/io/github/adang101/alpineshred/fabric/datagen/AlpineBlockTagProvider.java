package io.github.adang101.alpineshred.fabric.datagen;

import io.github.adang101.alpineshred.registry.ModBlocks;
import io.github.adang101.alpineshred.registry.ModTags;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.concurrent.CompletableFuture;

/** Block tags (design doc sections 11 and 18). */
public final class AlpineBlockTagProvider extends FabricTagProvider.BlockTagProvider {
    public AlpineBlockTagProvider(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        getOrCreateTagBuilder(ModTags.Blocks.SLIDE_SNOW).add(ModBlocks.GROOMED_SNOW.get(), Blocks.SNOW_BLOCK, Blocks.SNOW);
        getOrCreateTagBuilder(ModTags.Blocks.SLIDE_ICE).add(Blocks.ICE, Blocks.PACKED_ICE, Blocks.BLUE_ICE, Blocks.FROSTED_ICE);
        getOrCreateTagBuilder(ModTags.Blocks.SLIDE_POWDER).add(Blocks.POWDER_SNOW);

        // Slabs in the tag work as low boxes (section 11).
        getOrCreateTagBuilder(ModTags.Blocks.SLIDE_BOXES)
                .add(ModBlocks.WOOD_BOX.get(), ModBlocks.METAL_BOX.get(), ModBlocks.PLASTIC_BOX.get())
                .forceAddTag(BlockTags.PLANKS)
                .forceAddTag(BlockTags.WOODEN_SLABS)
                .add(Blocks.SMOOTH_STONE, Blocks.SMOOTH_STONE_SLAB,
                        Blocks.QUARTZ_BLOCK, Blocks.QUARTZ_SLAB, Blocks.SMOOTH_QUARTZ, Blocks.SMOOTH_QUARTZ_SLAB,
                        Blocks.IRON_BLOCK,
                        Blocks.WAXED_COPPER_BLOCK, Blocks.WAXED_EXPOSED_COPPER, Blocks.WAXED_WEATHERED_COPPER, Blocks.WAXED_OXIDIZED_COPPER,
                        Blocks.WAXED_CUT_COPPER, Blocks.WAXED_EXPOSED_CUT_COPPER, Blocks.WAXED_WEATHERED_CUT_COPPER, Blocks.WAXED_OXIDIZED_CUT_COPPER,
                        Blocks.WHITE_CONCRETE, Blocks.ORANGE_CONCRETE, Blocks.MAGENTA_CONCRETE, Blocks.LIGHT_BLUE_CONCRETE,
                        Blocks.YELLOW_CONCRETE, Blocks.LIME_CONCRETE, Blocks.PINK_CONCRETE, Blocks.GRAY_CONCRETE,
                        Blocks.LIGHT_GRAY_CONCRETE, Blocks.CYAN_CONCRETE, Blocks.PURPLE_CONCRETE, Blocks.BLUE_CONCRETE,
                        Blocks.BROWN_CONCRETE, Blocks.GREEN_CONCRETE, Blocks.RED_CONCRETE, Blocks.BLACK_CONCRETE);

        // Section 18: all rail blocks and rail_post; section 11: the pipe coping too.
        var grindable = getOrCreateTagBuilder(ModTags.Blocks.GRINDABLE);
        rails().forEach(grindable::add);
        grindable.add(ModBlocks.RAIL_POST.get(), ModBlocks.PIPE_COPING.get());
        getOrCreateTagBuilder(ModTags.Blocks.GRINDABLE_VANILLA).forceAddTag(BlockTags.FENCES).forceAddTag(BlockTags.WALLS);

        getOrCreateTagBuilder(BlockTags.MINEABLE_WITH_SHOVEL).add(ModBlocks.GROOMED_SNOW.get());
        getOrCreateTagBuilder(BlockTags.MINEABLE_WITH_AXE).add(ModBlocks.PAINT_BENCH.get());
        var pickaxe = getOrCreateTagBuilder(BlockTags.MINEABLE_WITH_PICKAXE);
        rails().forEach(pickaxe::add);
        pickaxe.add(ModBlocks.RAIL_POST.get(), ModBlocks.METAL_BOX.get(), ModBlocks.PLASTIC_BOX.get(), ModBlocks.PIPE_COPING.get());
        getOrCreateTagBuilder(BlockTags.MINEABLE_WITH_AXE).add(ModBlocks.WOOD_BOX.get(), ModBlocks.PARK_RAMP.get(),
                ModBlocks.QUARTER_PIPE.get(), ModBlocks.SLOPE_MARKER_GREEN.get(), ModBlocks.SLOPE_MARKER_BLUE.get(),
                ModBlocks.SLOPE_MARKER_BLACK.get(), ModBlocks.PARK_FLAG.get(), ModBlocks.START_GATE.get());
    }

    private static java.util.List<Block> rails() {
        java.util.List<Block> out = new java.util.ArrayList<>();
        out.add(ModBlocks.PIPE_RAIL.get());
        out.add(ModBlocks.FLAT_RAIL.get());
        ModBlocks.COPPER_RAILS.forEach(r -> out.add(r.get()));
        ModBlocks.WAXED_COPPER_RAILS.forEach(r -> out.add(r.get()));
        return out;
    }
}
