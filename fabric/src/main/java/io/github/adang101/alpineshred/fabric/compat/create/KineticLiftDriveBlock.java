package io.github.adang101.alpineshred.fabric.compat.create;

import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import io.github.adang101.alpineshred.compat.create.KineticDriveMath;
import io.github.adang101.alpineshred.config.AlpineConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;

import java.util.List;

/**
 * {@code kinetic_lift_drive}: a gearbox with a cable wheel on every side and a shaft through both ends, so it
 * connects to a Create network the way a shaft does. Placed against a drive station it runs the line from the
 * rotation (phase 9 plan, decision 2; README 78). Lives in {@code compat.create}, registered only when Create is
 * present, so nothing outside this package names a Create type.
 *
 * <p>It drops itself whatever the tool: the drop is code rather than a loot table because datagen runs without
 * Create and a loot file naming this item would be a load error in a world without Create.
 */
public class KineticLiftDriveBlock extends RotatedPillarKineticBlock implements IBE<KineticLiftDriveBE> {
    /** rpm at which the tooltip quotes the stress: Create's top speed, where the line hits its default cap. */
    private static final double TOOLTIP_RPM = 256.0;

    public KineticLiftDriveBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(AXIS);
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == state.getValue(AXIS);
    }

    @Override
    public Class<KineticLiftDriveBE> getBlockEntityClass() {
        return KineticLiftDriveBE.class;
    }

    @Override
    public BlockEntityType<? extends KineticLiftDriveBE> getBlockEntityType() {
        return FabricKineticDrive.BLOCK_ENTITY.get();
    }

    @Override
    @SuppressWarnings("deprecation")
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of(new ItemStack(this));
    }

    @Override
    public void appendHoverText(ItemStack stack, BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.alpineshred.kinetic_lift_drive").withStyle(ChatFormatting.GRAY));
        double su = KineticDriveMath.stressUnits(TOOLTIP_RPM, AlpineConfig.LIFT_KINETIC_STRESS.value());
        tooltip.add(Component.translatable("tooltip.alpineshred.kinetic_lift_drive.stress", String.valueOf((int) Math.round(su))).withStyle(ChatFormatting.GOLD));
    }
}
