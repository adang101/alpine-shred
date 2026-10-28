package io.github.adang101.alpineshred.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * {@link SlideController.Body} over a real {@link Player}: vanilla collision and step-up through
 * {@code Entity.move}, ground casts through {@code Level.clip} with the player's own collision
 * context (so floating on powder sees powder as solid), and block surfaces through
 * {@link SurfaceResolver}.
 */
public final class PlayerBody implements SlideController.Body {
    private final Player player;

    public PlayerBody(Player player) {
        this.player = player;
    }

    public Player player() {
        return player;
    }

    @Override
    public double x() {
        return player.getX();
    }

    @Override
    public double y() {
        return player.getY();
    }

    @Override
    public double z() {
        return player.getZ();
    }

    @Override
    public double velocityX() {
        return player.getDeltaMovement().x;
    }

    @Override
    public double velocityZ() {
        return player.getDeltaMovement().z;
    }

    @Override
    public boolean onGround() {
        return player.onGround();
    }

    @Override
    public SlideController.Moved move(double dx, double dy, double dz) {
        Vec3 before = player.position();
        player.move(MoverType.SELF, new Vec3(dx, dy, dz));
        Vec3 after = player.position();
        return new SlideController.Moved(after.x - before.x, after.y - before.y, after.z - before.z,
                player.onGround(), player.horizontalCollision);
    }

    @Override
    public void snapDown(double dy) {
        player.setPos(player.getX(), player.getY() - dy, player.getZ());
        player.setOnGround(true);
        player.resetFallDistance();
    }

    @Override
    public double groundTop(double x, double z, double fromY, double toY) {
        Level level = player.level();
        BlockHitResult hit = level.clip(new ClipContext(new Vec3(x, fromY, z), new Vec3(x, toY, z),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK || hit.isInside()) return Double.NaN;
        return hit.getLocation().y;
    }

    /**
     * In order: a surface block at foot level (a one-layer snow cover has no collision box, and a
     * rider sinking into powder stands inside it); the block below the feet; and, standing on an
     * edge with the center over air, the block vanilla says supports the player.
     */
    @Override
    @Nullable
    public SurfaceProfile surfaceUnderFeet() {
        Level level = player.level();
        double x = player.getX(), y = player.getY(), z = player.getZ();

        SurfaceProfile overlay = SurfaceResolver.match(level.getBlockState(BlockPos.containing(x, y + 0.01, z)));
        if (overlay != null) return overlay;

        BlockPos below = BlockPos.containing(x, y - 0.001, z);
        SurfaceProfile under = SurfaceResolver.forState(level, below, level.getBlockState(below));
        if (under != null) return under;

        if (player.onGround()) {
            Optional<BlockPos> support = player.mainSupportingBlockPos;
            if (support.isPresent()) {
                BlockState state = level.getBlockState(support.get());
                return SurfaceResolver.forState(level, support.get(), state);
            }
        }
        return null;
    }
}
