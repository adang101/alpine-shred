package io.github.adang101.alpineshred.fabric.datagen;

import dev.architectury.registry.registries.RegistrySupplier;
import io.github.adang101.alpineshred.park.block.PlasticBoxBlock;
import io.github.adang101.alpineshred.registry.ModBlocks;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootTableProvider;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.CopyBlockState;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

/**
 * Block drops are self-drops (design doc section 18; section 10: "breaking a piece drops it").
 * A plastic box keeps its color. The quarter pipe has no table: breaking any block of it removes
 * the whole pipe and drops one item ({@code PipeBlock.playerWillDestroy}).
 */
public final class AlpineLootProvider extends FabricBlockLootTableProvider {
    public AlpineLootProvider(FabricDataOutput output) {
        super(output);
    }

    @Override
    public void generate() {
        dropSelf(ModBlocks.GROOMED_SNOW.get());
        for (RegistrySupplier<? extends Block> supplier : ModBlocks.parkBlocks()) {
            Block block = supplier.get();
            if (block == ModBlocks.QUARTER_PIPE.get()) continue;
            if (block == ModBlocks.PLASTIC_BOX.get()) {
                add(block, LootTable.lootTable().withPool(applyExplosionCondition(block, LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1))
                        .add(LootItem.lootTableItem(block).apply(CopyBlockState.copyState(block).copy(PlasticBoxBlock.COLOR))))));
                continue;
            }
            dropSelf(block);
        }
    }
}
