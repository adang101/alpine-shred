package io.github.adang101.alpineshred.gear;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A plain item with a one-line description: wax, the lift pass, and the park wrench and lift linker
 * until their blocks ship (sections 10, 11 and 13).
 */
public class DescribedItem extends Item {
    private final String descriptionKey;

    public DescribedItem(String descriptionKey, Properties properties) {
        super(properties);
        this.descriptionKey = descriptionKey;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(descriptionKey).withStyle(ChatFormatting.GRAY));
    }
}
