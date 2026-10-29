package io.github.adang101.alpineshred.park.item;

import io.github.adang101.alpineshred.park.RailPlacement;
import io.github.adang101.alpineshred.park.block.BoxBlock;
import io.github.adang101.alpineshred.park.block.LevelRails;
import io.github.adang101.alpineshred.park.block.ParkDecorBlock;
import io.github.adang101.alpineshred.park.block.ParkStates;
import io.github.adang101.alpineshred.park.block.PipeCopingBlock;
import io.github.adang101.alpineshred.park.block.RailBlock;
import io.github.adang101.alpineshred.park.block.RampBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * {@code park_wrench} (design doc section 10, "Placement and tools"): only overrides what placement
 * chose. On a rail, right-click cycles the valid alternatives (pieces that keep the line continuous
 * with its neighbours) and sneak-right-click turns it; with one valid option it says so. On a ramp
 * it cycles the slope, or (sneaking) marks the lip. On a box it switches low and full, or
 * (sneaking) turns the axis. Coping and decor turn.
 */
public class ParkWrenchItem extends Item {
    public ParkWrenchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = context.getPlayer();
        boolean sneak = player != null && player.isSecondaryUseActive();
        BlockState changed;
        Component message;
        if (state.getBlock() instanceof RailBlock) {
            RailPlacement.Piece current = RailBlock.piece(state);
            RailPlacement.Piece next;
            if (sneak) {
                next = new RailPlacement.Piece(current.shape(), current.facing().clockwise());
            } else {
                List<RailPlacement.Piece> options = RailPlacement.alternatives(new LevelRails(level), LevelRails.cell(pos), current);
                if (options.size() <= 1) {
                    say(player, level, Component.translatable("message.alpineshred.wrench.only_option"));
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
                // A fixed order, whichever piece is current, so repeated clicks walk every option.
                List<RailPlacement.Piece> sorted = new java.util.ArrayList<>(options);
                sorted.sort(java.util.Comparator.<RailPlacement.Piece>comparingInt(q -> q.shape().ordinal())
                        .thenComparingInt(q -> q.facing().ordinal()));
                next = sorted.get((sorted.indexOf(current) + 1) % sorted.size());
            }
            changed = refreshRail(level, pos, state, next);
            message = Component.translatable("message.alpineshred.wrench.rail",
                    Component.translatable("shape.alpineshred." + next.shape().serializedName()),
                    Component.translatable("direction.alpineshred." + next.facing().serializedName()));
        } else if (state.getBlock() instanceof RampBlock) {
            if (sneak) {
                changed = state.cycle(ParkStates.LIP);
                message = Component.translatable(changed.getValue(ParkStates.LIP) ? "message.alpineshred.wrench.lip_on" : "message.alpineshred.wrench.lip_off");
            } else {
                changed = RampBlock.nextSlope(state);
                message = Component.translatable("message.alpineshred.wrench.slope",
                        Component.translatable("slope.alpineshred." + changed.getValue(ParkStates.RAMP_SLOPE).getSerializedName()));
            }
        } else if (state.getBlock() instanceof BoxBlock) {
            changed = sneak ? state.cycle(BoxBlock.AXIS) : state.cycle(ParkStates.BOX_HALF);
            message = null;
        } else if (state.getBlock() instanceof PipeCopingBlock || state.getBlock() instanceof ParkDecorBlock) {
            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            changed = state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing.getClockWise());
            message = null;
        } else {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            level.setBlock(pos, changed, Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8f, 1.2f);
        }
        if (message != null) say(player, level, message);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static BlockState refreshRail(Level level, BlockPos pos, BlockState state, RailPlacement.Piece piece) {
        BlockState s = state.setValue(RailBlock.FACING, ParkStates.direction(piece.facing()))
                .setValue(ParkStates.RAIL_SHAPE, ParkStates.RailShapeProp.of(piece.shape()));
        boolean[] ends = RailPlacement.connections(new LevelRails(level), LevelRails.cell(pos), piece);
        return s.setValue(ParkStates.CONNECTED_A, ends[0]).setValue(ParkStates.CONNECTED_B, ends[1]);
    }

    private static void say(@Nullable Player player, Level level, Component message) {
        if (player != null && level.isClientSide) player.displayClientMessage(message, true);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.alpineshred.park_wrench").withStyle(ChatFormatting.GRAY));
    }
}
