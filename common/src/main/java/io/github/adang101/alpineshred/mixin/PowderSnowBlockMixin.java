package io.github.adang101.alpineshred.mixin;

import io.github.adang101.alpineshred.movement.RideHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Riders above the float speed are carried by powder snow (design doc section 7, "Powder"). */
@Mixin(PowderSnowBlock.class)
public abstract class PowderSnowBlockMixin {
    @Inject(method = "canEntityWalkOnPowderSnow", at = @At("HEAD"), cancellable = true)
    private static void alpineshred$floatAtSpeed(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Player player && RideHooks.floatsOnPowder(player)) cir.setReturnValue(true);
    }

    /**
     * A floating rider meets powder as a full solid block (README clarification 102). Vanilla makes powder
     * solid only when the entity's feet are already above the block, but a step-up onto the next powder block
     * (a bowl wall) tests the block against the feet's current height, so it found no collision and the rider
     * ran into the powder and sank. A rider falling fast keeps vanilla's shorter shape.
     */
    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
    private void alpineshred$solidToFloatingRiders(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
                                                   CallbackInfoReturnable<VoxelShape> cir) {
        if (context instanceof EntityCollisionContext entityContext
                && entityContext.getEntity() instanceof Player player
                && player.fallDistance <= 2.5F
                && RideHooks.floatsOnPowder(player)) {
            cir.setReturnValue(Shapes.block());
        }
    }
}
