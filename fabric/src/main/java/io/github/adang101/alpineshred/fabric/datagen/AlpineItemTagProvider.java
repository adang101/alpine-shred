package io.github.adang101.alpineshred.fabric.datagen;

import io.github.adang101.alpineshred.registry.ModItems;
import io.github.adang101.alpineshred.registry.ModTags;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;

import java.util.concurrent.CompletableFuture;

/** Item tags (design doc section 18, "Tags"). Curios and Trinkets slot tags live in each loader module. */
public final class AlpineItemTagProvider extends FabricTagProvider.ItemTagProvider {
    public AlpineItemTagProvider(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        getOrCreateTagBuilder(ModTags.Items.BOARDS).add(
                ModItems.SNOWBOARD.get(), ModItems.PARK_SNOWBOARD.get(), ModItems.FREERIDE_SNOWBOARD.get(), ModItems.PRO_SNOWBOARD.get());
        getOrCreateTagBuilder(ModTags.Items.SKIS).add(
                ModItems.SKIS.get(), ModItems.CARVING_SKIS.get(), ModItems.POWDER_SKIS.get(), ModItems.PARK_SKIS.get(), ModItems.PRO_SKIS.get());
        getOrCreateTagBuilder(ModTags.Items.POLES).add(ModItems.SKI_POLES.get(), ModItems.CARBON_SKI_POLES.get());
        getOrCreateTagBuilder(ModTags.Items.BINDING_BOARD).add(ModItems.SNOWBOARD_BOOTS.get());
        getOrCreateTagBuilder(ModTags.Items.BINDING_SKI).add(ModItems.SKI_BOOTS.get());
        getOrCreateTagBuilder(ModTags.Items.REPAIR_SOFT_GEAR).addTag(ItemTags.WOOL).add(Items.LEATHER);
        getOrCreateTagBuilder(ModTags.Items.DESIGN_COST).add(
                Items.WHITE_DYE, Items.ORANGE_DYE, Items.MAGENTA_DYE, Items.LIGHT_BLUE_DYE,
                Items.YELLOW_DYE, Items.LIME_DYE, Items.PINK_DYE, Items.GRAY_DYE,
                Items.LIGHT_GRAY_DYE, Items.CYAN_DYE, Items.PURPLE_DYE, Items.BLUE_DYE,
                Items.BROWN_DYE, Items.GREEN_DYE, Items.RED_DYE, Items.BLACK_DYE);
        getOrCreateTagBuilder(ItemTags.FREEZE_IMMUNE_WEARABLES).add(
                ModItems.SKI_BOOTS.get(), ModItems.SNOWBOARD_BOOTS.get(), ModItems.SKI_HELMET.get());
    }
}
