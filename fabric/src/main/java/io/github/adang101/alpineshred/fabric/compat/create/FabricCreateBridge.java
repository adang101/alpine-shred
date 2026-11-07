package io.github.adang101.alpineshred.fabric.compat.create;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.architectury.platform.Platform;
import io.github.adang101.alpineshred.compat.create.CreateBridge;
import io.github.adang101.alpineshred.lift.LiftDriveSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.function.Consumer;

/**
 * Fabric's {@link CreateBridge}. Names Create types; it is loaded solely through
 * {@link FabricCreateCompat#init()}, which the entry point calls behind {@code PlatformHooks.isCreatePresent()}.
 */
final class FabricCreateBridge implements CreateBridge {
    /** Create's kinetic base class: loaded here so the Create column proves the dependency resolves; the kinetic drive extends it. */
    private static final Class<?> KINETIC_BASE = KineticBlockEntity.class;
    private static final ResourceLocation SHAFT = new ResourceLocation("create", "shaft");
    /** Fixed order, so two drives beside one station always resolve to the same one. */
    private static final Direction[] SIDES = Direction.values();

    /** Live once Create has registered its content, so a listed-but-broken Create jar does not pass. */
    @Override
    public boolean live() {
        return KINETIC_BASE != null && BuiltInRegistries.BLOCK.containsKey(SHAFT);
    }

    @Override
    public String version() {
        return Platform.getMod("create").getVersion();
    }

    @Override
    public LiftDriveSource kineticDriveNear(Level level, BlockPos stationPos) {
        for (Direction side : SIDES) {
            BlockPos pos = stationPos.relative(side);
            // A neighbour in an unloaded chunk is not asked: that would load it.
            if (!level.hasChunkAt(pos)) continue;
            if (level.getBlockEntity(pos) instanceof KineticLiftDriveBE drive) return drive;
        }
        return null;
    }

    @Override
    public void addCreativeTabItems(Consumer<ItemLike> out) {
        out.accept(FabricKineticDrive.ITEM.get());
    }

    @Override
    public BlockState testKineticDriveState(Direction.Axis axis) {
        return FabricKineticDrive.BLOCK.get().defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, axis);
    }

    @Override
    public void testPlaceMotor(ServerLevel level, BlockPos pos, Direction facing, int rpm) {
        BlockState state = AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, facing);
        if (level.getBlockState(pos) != state) level.setBlock(pos, state, 3);
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof CreativeMotorBlockEntity motor)) throw new IllegalStateException("no creative motor at " + pos);
        motor.generatedSpeed.setValue(rpm);
    }

    @Override
    public void testSetOverstressed(ServerLevel level, BlockPos pos, boolean overstressed) {
        if (!(level.getBlockEntity(pos) instanceof KineticBlockEntity kinetic)) throw new IllegalStateException("no kinetic block at " + pos);
        kinetic.updateFromNetwork(overstressed ? 0f : 1_000_000f, overstressed ? 1f : 0f, 1);
    }
}
