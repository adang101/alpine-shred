package io.github.adang101.alpineshred.gear;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Restores edge sharpness to 100 on the board or skis held in the off hand (design doc section 5).
 * 64 uses.
 */
public class EdgeTunerItem extends Item {
    public EdgeTunerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack tuner = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(tuner);
        ItemStack gear = player.getOffhandItem();
        if (!(gear.getItem() instanceof SnowGearItem)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.alpineshred.tuner.no_gear"), true);
            }
            return InteractionResultHolder.fail(tuner);
        }
        if (GearNbt.edge(gear) >= GearWear.MAX_EDGE) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.alpineshred.tuner.sharp"), true);
            }
            return InteractionResultHolder.fail(tuner);
        }
        if (!level.isClientSide) {
            GearNbt.setEdge(gear, GearWear.MAX_EDGE);
            tuner.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 0.6f, 1.4f);
            player.displayClientMessage(Component.translatable("message.alpineshred.tuner.done"), true);
        }
        player.getCooldowns().addCooldown(this, 10);
        return InteractionResultHolder.sidedSuccess(tuner, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.alpineshred.edge_tuner").withStyle(ChatFormatting.GRAY));
    }
}
