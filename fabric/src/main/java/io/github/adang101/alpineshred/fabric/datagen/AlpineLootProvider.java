package io.github.adang101.alpineshred.fabric.datagen;

import io.github.adang101.alpineshred.registry.ModBlocks;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootTableProvider;

/** Block drops are self-drops (design doc section 18). */
public final class AlpineLootProvider extends FabricBlockLootTableProvider {
    public AlpineLootProvider(FabricDataOutput output) {
        super(output);
    }

    @Override
    public void generate() {
        dropSelf(ModBlocks.GROOMED_SNOW.get());
    }
}
