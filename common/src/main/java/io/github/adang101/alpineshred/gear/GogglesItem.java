package io.github.adang101.alpineshred.gear;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Goggles, worn in the Curios {@code goggles} or Trinkets {@code head/face} slot. The lens is stack
 * NBT {@code Lens}; goggles remove snow-spray and whiteout screen effects (section 17, phase 3).
 */
public class GogglesItem extends Item {
    public static final String LENS = "Lens";

    public GogglesItem(Properties properties) {
        super(properties);
    }

    public static Lens lens(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(LENS, Tag.TAG_STRING) ? Lens.byId(tag.getString(LENS)) : Lens.CLEAR;
    }

    public static ItemStack withLens(ItemStack stack, Lens lens) {
        if (lens == Lens.CLEAR) {
            CompoundTag tag = stack.getTag();
            if (tag != null) {
                tag.remove(LENS);
                if (tag.isEmpty()) stack.setTag(null);
            }
        } else {
            stack.getOrCreateTag().putString(LENS, lens.id());
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.alpineshred.goggles.lens",
                Component.translatable("lens.alpineshred." + lens(stack).id())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.alpineshred.goggles").withStyle(ChatFormatting.DARK_GRAY));
    }
}
