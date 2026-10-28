package io.github.adang101.alpineshred.gear;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeableArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Ski boots, snowboard boots and the ski helmet: dyeable alpine armor. Vanilla's armor layer tints
 * the base texture with {@link #getColor} and draws the {@code _overlay} texture untinted.
 */
public class AlpineArmorItem extends DyeableArmorItem {
    private final int defaultColor;
    private final String tooltipKey;

    public AlpineArmorItem(Type type, int defaultColor, String tooltipKey, Properties properties) {
        super(AlpineArmorMaterial.ALPINE, type, properties);
        this.defaultColor = defaultColor;
        this.tooltipKey = tooltipKey;
    }

    @Override
    public int getColor(ItemStack stack) {
        return hasCustomColor(stack) ? super.getColor(stack) : defaultColor;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable(tooltipKey).withStyle(ChatFormatting.GRAY));
    }
}
