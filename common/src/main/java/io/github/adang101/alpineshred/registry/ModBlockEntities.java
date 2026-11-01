package io.github.adang101.alpineshred.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.lift.LiftDriveStationBlockEntity;
import io.github.adang101.alpineshred.lift.LiftReturnStationBlockEntity;
import io.github.adang101.alpineshred.lift.LiftTowerHeadBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Block entity types: the lift nodes (design doc section 13). */
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(AlpineShred.MOD_ID, Registries.BLOCK_ENTITY_TYPE);

    public static final RegistrySupplier<BlockEntityType<LiftTowerHeadBlockEntity>> LIFT_TOWER_HEAD = TYPES.register("lift_tower_head",
            () -> BlockEntityType.Builder.of(LiftTowerHeadBlockEntity::new, ModBlocks.LIFT_TOWER_HEAD.get()).build(null));
    public static final RegistrySupplier<BlockEntityType<LiftDriveStationBlockEntity>> LIFT_DRIVE_STATION = TYPES.register("lift_drive_station",
            () -> BlockEntityType.Builder.of(LiftDriveStationBlockEntity::new, ModBlocks.LIFT_DRIVE_STATION.get()).build(null));
    public static final RegistrySupplier<BlockEntityType<LiftReturnStationBlockEntity>> LIFT_RETURN_STATION = TYPES.register("lift_return_station",
            () -> BlockEntityType.Builder.of(LiftReturnStationBlockEntity::new, ModBlocks.LIFT_RETURN_STATION.get()).build(null));

    private ModBlockEntities() {
    }

    public static void register() {
        TYPES.register();
    }
}
