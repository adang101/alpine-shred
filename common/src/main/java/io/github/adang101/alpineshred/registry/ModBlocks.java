package io.github.adang101.alpineshred.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.adang101.alpineshred.AlpineShred;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Blocks. Phase 1 ships only {@code groomed_snow}, which the surface profiles and generated runs
 * depend on; park, lift and bench blocks arrive with their phases (design doc section 20).
 */
public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(AlpineShred.MOD_ID, Registries.BLOCK);

    /** Flattened, packed snow: the fastest, grippiest snow surface (section 7). */
    public static final RegistrySupplier<Block> GROOMED_SNOW = BLOCKS.register("groomed_snow",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.SNOW_BLOCK)));

    private ModBlocks() {
    }

    public static void register() {
        BLOCKS.register();
    }
}
