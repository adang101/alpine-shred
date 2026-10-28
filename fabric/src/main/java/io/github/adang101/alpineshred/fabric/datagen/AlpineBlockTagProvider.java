package io.github.adang101.alpineshred.fabric.datagen;

import io.github.adang101.alpineshred.registry.ModBlocks;
import io.github.adang101.alpineshred.registry.ModTags;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.tags.BlockTags;
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

        // Custom box blocks join in phase 4; slabs in the tag work as low boxes.
        getOrCreateTagBuilder(ModTags.Blocks.SLIDE_BOXES)
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

        // Rail blocks join in phase 4; the tag exists now so datapacks can extend it.
        getOrCreateTagBuilder(ModTags.Blocks.GRINDABLE);
        getOrCreateTagBuilder(ModTags.Blocks.GRINDABLE_VANILLA).forceAddTag(BlockTags.FENCES).forceAddTag(BlockTags.WALLS);

        getOrCreateTagBuilder(BlockTags.MINEABLE_WITH_SHOVEL).add(ModBlocks.GROOMED_SNOW.get());
    }
}
