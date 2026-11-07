package io.github.adang101.alpineshred.forge.compat.create;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.adang101.alpineshred.AlpineShred;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * The kinetic lift drive's own registries (phase 9 plan, decision 2): separate Architectury registers, filled
 * only when {@link ForgeCreateCompat#init()} runs behind {@code PlatformHooks.isCreatePresent()}. Without Create
 * this class is never loaded, so the block does not exist and its data files (recipe conditional on Create,
 * optional tag entries) are inert.
 */
final class ForgeKineticDrive {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(AlpineShred.MOD_ID, Registries.BLOCK);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(AlpineShred.MOD_ID, Registries.BLOCK_ENTITY_TYPE);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(AlpineShred.MOD_ID, Registries.ITEM);

    static final String ID = "kinetic_lift_drive";

    static final RegistrySupplier<KineticLiftDriveBlock> BLOCK = BLOCKS.register(ID, () -> new KineticLiftDriveBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f, 6.0f).sound(SoundType.METAL)));
    static final RegistrySupplier<BlockEntityType<KineticLiftDriveBE>> BLOCK_ENTITY = BLOCK_ENTITIES.register(ID,
            () -> BlockEntityType.Builder.of(KineticLiftDriveBE::new, BLOCK.get()).build(null));
    static final RegistrySupplier<BlockItem> ITEM = ITEMS.register(ID, () -> new BlockItem(BLOCK.get(), new Item.Properties()));

    private ForgeKineticDrive() {
    }

    static void register() {
        BLOCKS.register();
        BLOCK_ENTITIES.register();
        ITEMS.register();
    }
}
