package io.github.adang101.alpineshred.movement;

import io.github.adang101.alpineshred.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
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
    /** How far past the box a trunk still counts as the thing the rider hit, in blocks. */
    private static final double CONTACT_REACH = 0.1;
    /** Shrinks a box's edges so a block it only touches is not overlapped. */
    private static final double EDGE = 1.0e-7;

    private final Player player;
    private final BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();
    private io.github.adang101.alpineshred.park.ParkView park;

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
    public boolean overlapsRideThrough() {
        return touches(player.getBoundingBox(), ModTags.Blocks.RIDE_THROUGH);
    }

    /** A trunk within {@link #CONTACT_REACH} of the box on the side of the move: what the rider ran into. */
    @Override
    public boolean hitTrunk(double dx, double dz) {
        AABB reach = player.getBoundingBox().expandTowards(Math.signum(dx) * CONTACT_REACH, 0, Math.signum(dz) * CONTACT_REACH);
        return touches(reach, ModTags.Blocks.TREE_TRUNKS);
    }

    /**
     * Whether any block cell the box overlaps is in {@code tag}. Cells whose face only touches the box
     * do not count. Reads at most a few cells (the box is 0.6 by 1.8 by 0.6) and allocates nothing.
     */
    private boolean touches(AABB box, TagKey<Block> tag) {
        return firstTagged(player.level(), box, tag, cell) != null;
    }

    /**
     * The first block in {@code tag} among the cells {@code box} overlaps, or null. {@code cell} is the
     * caller's scratch position. Public so the client can name the leaf block a rider entered (it asks
     * once per entry, on the event tick, with a throwaway position).
     */
    @Nullable
    public static BlockState firstTagged(Level level, AABB box, TagKey<Block> tag, BlockPos.MutableBlockPos cell) {
        int x1 = Mth.floor(box.minX + EDGE), x2 = Mth.floor(box.maxX - EDGE);
        int y1 = Mth.floor(box.minY + EDGE), y2 = Mth.floor(box.maxY - EDGE);
        int z1 = Mth.floor(box.minZ + EDGE), z2 = Mth.floor(box.maxZ - EDGE);
        for (int y = y1; y <= y2; y++) {
            for (int x = x1; x <= x2; x++) {
                for (int z = z1; z <= z2; z++) {
                    BlockState state = level.getBlockState(cell.set(x, y, z));
                    if (state.is(tag)) return state;
                }
            }
        }
        return null;
    }

    @Override
    public io.github.adang101.alpineshred.park.ParkView park() {
        if (park == null) park = new io.github.adang101.alpineshred.park.block.LevelParkView(player.level());
        return park;
    }

    /** Grinds and pipe rides put the feet on a path: no collision, standing, no fall damage building up. */
    @Override
    public void placeAt(double x, double y, double z) {
        player.setPos(x, y, z);
        player.setOnGround(true);
        player.resetFallDistance();
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
