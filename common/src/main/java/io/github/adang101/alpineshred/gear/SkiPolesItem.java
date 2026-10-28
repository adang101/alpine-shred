package io.github.adang101.alpineshred.gear;

import io.github.adang101.alpineshred.movement.RideHooks;
import io.github.adang101.alpineshred.movement.RideMode;
import io.github.adang101.alpineshred.movement.RideSnapshot;
import io.github.adang101.alpineshred.movement.Riders;
import io.github.adang101.alpineshred.movement.TuningData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Ski poles, held in the main hand. Using them while riding skis on the ground pushes (+0.10 speed,
 * 12-tick cooldown, below {@code poleMaxSpeed}; design doc section 7). The push happens in the
 * client's slide controller and is reported with {@code c2s/ride_action}; the server checks the
 * cooldown and wears the poles there ({@code RideServer}).
 */
public class SkiPolesItem extends Item {
    public SkiPolesItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND || !level.isClientSide) return InteractionResultHolder.pass(stack);
        RideSnapshot ride = Riders.get(player);
        if (ride.mode() != RideMode.SKIS || !ride.state().isGroundRiding()) return InteractionResultHolder.pass(stack);
        if (!RideHooks.polePushLocal(player)) return InteractionResultHolder.pass(stack);
        player.getCooldowns().addCooldown(this, TuningData.tuning().poleCooldownTicks);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return repair.is(Items.IRON_INGOT) || super.isValidRepairItem(stack, repair);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.alpineshred.ski_poles").withStyle(ChatFormatting.GRAY));
    }
}
