package io.github.adang101.alpineshred.gear;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Ski poles, held in the main hand. The push action (+0.10 speed, 12-tick cooldown, skis only)
 * belongs to the phase 2 slide controller; for now poles are durable, repairable items.
 */
public class SkiPolesItem extends Item {
    public SkiPolesItem(Properties properties) {
        super(properties);
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
