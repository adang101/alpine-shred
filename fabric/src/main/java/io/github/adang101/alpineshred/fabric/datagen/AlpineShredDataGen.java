package io.github.adang101.alpineshred.fabric.datagen;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

/**
 * Data generation (design doc section 4): runs from the Fabric module with
 * {@code ./gradlew :fabric:runDatagen} and writes into {@code common/src/main/generated}, which
 * both loaders package. Generates the language file, recipes, tags and loot tables. Item models
 * with custom geometry and display transforms are hand-written in {@code common/src/main/resources}.
 */
public final class AlpineShredDataGen implements DataGeneratorEntrypoint {
    @Override
    public void onInitializeDataGenerator(FabricDataGenerator generator) {
        FabricDataGenerator.Pack pack = generator.createPack();
        pack.addProvider(AlpineLangProvider::new);
        pack.addProvider(AlpineRecipeProvider::new);
        pack.addProvider(AlpineItemTagProvider::new);
        pack.addProvider(AlpineBlockTagProvider::new);
        pack.addProvider(AlpineLootProvider::new);
    }
}
