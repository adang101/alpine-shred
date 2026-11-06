package io.github.adang101.alpineshred.fabric.datagen;

import io.github.adang101.alpineshred.registry.ModTags;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.entity.EntityType;

import java.util.concurrent.CompletableFuture;

/** Entity type tags: who may not ride a lift chair, and who may whatever their size (design doc section 13). */
public final class AlpineEntityTypeTagProvider extends FabricTagProvider.EntityTypeTagProvider {
    public AlpineEntityTypeTagProvider(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        getOrCreateTagBuilder(ModTags.EntityTypes.LIFT_BLOCKED).add(EntityType.ENDER_DRAGON, EntityType.WITHER, EntityType.WARDEN);
        getOrCreateTagBuilder(ModTags.EntityTypes.LIFT_PASSENGERS);
    }
}
