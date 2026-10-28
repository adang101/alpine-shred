package io.github.adang101.alpineshred.gear;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Mitts, worn in the Curios {@code hands} or Trinkets {@code hand/glove} slot. Dyeable through
 * vanilla's armor-dye recipe, which accepts any {@link DyeableLeatherItem}. Effects (+10% pole
 * push, +10% grab score) apply once poles and tricks ship.
 */
public class MittsItem extends Item implements DyeableLeatherItem {
    public static final int DEFAULT_COLOR = 0xB8322E;

    public MittsItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getColor(ItemStack stack) {
        return hasCustomColor(stack) ? DyeableLeatherItem.super.getColor(stack) : DEFAULT_COLOR;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.alpineshred.mitts").withStyle(ChatFormatting.GRAY));
    }
}
