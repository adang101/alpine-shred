package io.github.adang101.alpineshred.park.item;

import io.github.adang101.alpineshred.park.PipeArc;
import io.github.adang101.alpineshred.park.block.ParkStates;
import io.github.adang101.alpineshred.park.block.PipeBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Places a whole quarter pipe (design doc section 11): radius 3, or 5 while sneaking, with the
 * transition where the player clicked and the wall rising the way they face. Fails, placing
 * nothing, if any of its blocks is taken.
 */
public class PipeItem extends BlockItem {
    public PipeItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        if (!context.canPlace()) return InteractionResult.FAIL;
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos origin = context.getClickedPos();
        Direction facing = context.getHorizontalDirection();
        ParkStates.PipeRadius radius = player != null && player.isSecondaryUseActive() ? ParkStates.PipeRadius.R5 : ParkStates.PipeRadius.R3;
        var cells = PipeArc.cells(radius.radius);
        for (PipeArc.Cell c : cells) {
            BlockPos p = origin.relative(facing, c.i()).above(c.j());
            if (!level.getBlockState(p).canBeReplaced() || !level.isInWorldBounds(p)) {
                if (player != null && level.isClientSide) {
                    player.displayClientMessage(Component.translatable("message.alpineshred.pipe.no_room", radius.radius), true);
                }
                return InteractionResult.FAIL;
            }
        }
        if (!level.isClientSide) {
            for (int i = 0; i < cells.size(); i++) {
                PipeArc.Cell c = cells.get(i);
                BlockPos p = origin.relative(facing, c.i()).above(c.j());
                BlockState state = getBlock().defaultBlockState().setValue(PipeBlock.FACING, facing)
                        .setValue(ParkStates.PIPE_RADIUS, radius).setValue(ParkStates.PIPE_INDEX, i);
                level.setBlock(p, state, Block.UPDATE_ALL_IMMEDIATE);
            }
            var sound = getBlock().defaultBlockState().getSoundType();
            level.playSound(null, origin, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0f) / 2.0f, sound.getPitch() * 0.8f);
        }
        if (player == null || !player.getAbilities().instabuild) context.getItemInHand().shrink(1);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.alpineshred.quarter_pipe").withStyle(ChatFormatting.GRAY));
    }
}
