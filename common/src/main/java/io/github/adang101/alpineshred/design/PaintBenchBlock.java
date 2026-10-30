package io.github.adang101.alpineshred.design;

import io.github.adang101.alpineshred.config.AlpineConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * {@code paint_bench} (design doc section 22, "Bench and flow"): a crafting table plus red, blue
 * and yellow dye. Using it opens the {@link PaintBenchMenu}; with {@code design.enabled} off it
 * says so instead.
 */
public class PaintBenchBlock extends Block {
    private static final Component TITLE = Component.translatable("container.alpineshred.paint_bench");

    public PaintBenchBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!AlpineConfig.DESIGN_ENABLED.on()) {
            player.displayClientMessage(Component.translatable(DesignStatus.DISABLED.translationKey()), true);
            return InteractionResult.CONSUME;
        }
        player.openMenu(state.getMenuProvider(level, pos));
        return InteractionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider((id, inventory, player) -> new PaintBenchMenu(id, inventory, ContainerLevelAccess.create(level, pos)), TITLE);
    }
}
