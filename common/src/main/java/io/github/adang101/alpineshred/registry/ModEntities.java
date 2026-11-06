package io.github.adang101.alpineshred.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.lift.ChairEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Entity types: the lift chair (design doc section 13). */
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(AlpineShred.MOD_ID, Registries.ENTITY_TYPE);

    public static final RegistrySupplier<EntityType<ChairEntity>> LIFT_CHAIR = TYPES.register("lift_chair",
            () -> EntityType.Builder.<ChairEntity>of(ChairEntity::new, MobCategory.MISC)
                    .sized(1.2f, 0.5f).clientTrackingRange(10).updateInterval(20).fireImmune().build("lift_chair"));

    private ModEntities() {
    }

    public static void register() {
        TYPES.register();
    }
}
