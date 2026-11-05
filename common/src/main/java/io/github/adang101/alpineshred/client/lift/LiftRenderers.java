package io.github.adang101.alpineshred.client.lift;

import dev.architectury.event.events.client.ClientLifecycleEvent;
import dev.architectury.registry.client.level.entity.EntityRendererRegistry;
import dev.architectury.registry.client.rendering.BlockEntityRendererRegistry;
import io.github.adang101.alpineshred.lift.LiftNodeBlockEntity;
import io.github.adang101.alpineshred.registry.ModBlockEntities;
import io.github.adang101.alpineshred.registry.ModEntities;

/** Registers the lift renderers (design doc section 16): the chair, the tower heads and both stations' bullwheels. */
public final class LiftRenderers {
    private LiftRenderers() {
    }

    public static void register() {
        EntityRendererRegistry.register(ModEntities.LIFT_CHAIR, ChairEntityRenderer::new);
        // The block entity types exist once registries are filled, which is client setup on both loaders.
        ClientLifecycleEvent.CLIENT_SETUP.register(minecraft -> {
            BlockEntityRendererRegistry.register(ModBlockEntities.LIFT_TOWER_HEAD.get(), TowerHeadBER::new);
            BlockEntityRendererRegistry.<LiftNodeBlockEntity>register(ModBlockEntities.LIFT_DRIVE_STATION.get(), BullwheelBER::new);
            BlockEntityRendererRegistry.<LiftNodeBlockEntity>register(ModBlockEntities.LIFT_RETURN_STATION.get(), BullwheelBER::new);
        });
    }
}
