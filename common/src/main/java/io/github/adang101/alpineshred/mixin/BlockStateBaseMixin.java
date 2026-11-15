package io.github.adang101.alpineshred.mixin;

import io.github.adang101.alpineshred.movement.Riders;
import io.github.adang101.alpineshred.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Riding through leaves (README clarification 97): a block in {@code #alpineshred:ride_through} has no
 * collision for a player whose ride a controller drives. The client's movement and the server's replay
 * of it both ask for collision shapes through here, so vanilla's "moved wrongly" check agrees with the
 * client and no validator change is needed.
 *
 * <p>This is among the hottest methods in the game (every entity, every collision query), so the checks
 * run cheapest first and nothing is allocated: the context type, the entity type, the block's tag (a
 * set lookup), and last the synced ride state. An unstrapped player, or any other entity, sees the
 * block's own shape.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {
    @Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("HEAD"), cancellable = true)
    private void alpineshred$ridersPassThrough(BlockGetter level, BlockPos pos, CollisionContext context,
                                               CallbackInfoReturnable<VoxelShape> cir) {
        if (context instanceof EntityCollisionContext entityContext
                && entityContext.getEntity() instanceof Player player
                && ((BlockState) (Object) this).is(ModTags.Blocks.RIDE_THROUGH)
                && Riders.state(player).isControllerDriven()) {
            cir.setReturnValue(Shapes.empty());
        }
    }
}
