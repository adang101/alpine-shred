package io.github.adang101.alpineshred.park.block;

import io.github.adang101.alpineshred.movement.RideState;
import io.github.adang101.alpineshred.movement.RideStateHolder;
import io.github.adang101.alpineshred.movement.Riders;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * How park pieces collide with riders. The slide controller rides rails and pipes along their
 * paths itself, so their small collision boxes must not snag a rider it moves, on its own client or
 * in the server's replay of the move (vanilla resolves vertical movement first, and a rider moving
 * along a rising rail or arc is always a little below the next box's top).
 */
public final class ParkCollision {
    private ParkCollision() {
    }

    /**
     * True for a rider the controller moves: where the controller runs (the rider's own client, a
     * game test) its flag says so at once; elsewhere (the server, other clients) the synced state
     * does, for any ridden state but standing still.
     */
    public static boolean isRider(Entity entity) {
        if (!(entity instanceof Player player)) return false;
        if (((RideStateHolder) player).alpineshred$isControlled()) return true;
        RideState state = Riders.state(player);
        return state.isControllerDriven() && state != RideState.IDLE;
    }

    /**
     * A rail bar for riders: solid only from above, so a rider can land on it and stand on its top,
     * but never runs into it from the side or from below.
     */
    public static VoxelShape fromAbove(VoxelShape shape, BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext ec && isRider(ec.getEntity())) {
            return context.isAbove(shape, pos, false) ? shape : Shapes.empty();
        }
        return shape;
    }
}
