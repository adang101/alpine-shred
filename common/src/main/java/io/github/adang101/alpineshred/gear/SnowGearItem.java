package io.github.adang101.alpineshred.gear;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * A snowboard or a pair of skis. Stats come from {@link GearStatsRegistry} (data-driven); wax and
 * edge live in stack NBT ({@link GearNbt}). The item goes in the snow gear slot (Curios
 * {@code snow_gear}, Trinkets {@code feet/snow_gear}) and the player straps in with {@code G}.
 */
public class SnowGearItem extends Item {
    private final GearKind kind;

    public SnowGearItem(GearKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public GearKind kind() {
        return kind;
    }

    /** Stats for this stack's item, from the current data. */
    public GearStats stats() {
        return GearStatsRegistry.get(BuiltInRegistries.ITEM.getKey(this).getPath(), kind);
    }

    public static GearStats statsOf(ItemStack stack) {
        if (stack.getItem() instanceof SnowGearItem gear) return gear.stats();
        return GearStats.NEUTRAL_BOARD;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        GearStats s = stats();
        tooltip.add(Component.translatable("tooltip.alpineshred.gear.kind." + kind.serializedName())
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.alpineshred.gear.stats",
                fmt(s.speed()), fmt(s.grip()), fmt(s.pop())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.alpineshred.gear.stats2",
                fmt(s.powderFloat()), fmt(s.landingForgiveness())).withStyle(ChatFormatting.GRAY));

        int wax = GearNbt.wax(stack);
        tooltip.add(wax > 0
                ? Component.translatable("tooltip.alpineshred.gear.wax", wax).withStyle(ChatFormatting.GOLD)
                : Component.translatable("tooltip.alpineshred.gear.unwaxed").withStyle(ChatFormatting.DARK_GRAY));
        int edge = GearNbt.edge(stack);
        tooltip.add(Component.translatable("tooltip.alpineshred.gear.edge", edge)
                .withStyle(edge >= 60 ? ChatFormatting.GRAY : ChatFormatting.RED));

        // Section 22: the design's name and author (cosmetic; stats never change).
        io.github.adang101.alpineshred.design.Design design = io.github.adang101.alpineshred.design.DesignNbt.read(stack);
        if (design != null) {
            Component name = design.name().isEmpty() ? Component.translatable("tooltip.alpineshred.design.untitled") : Component.literal(design.name());
            tooltip.add(design.author().isEmpty()
                    ? Component.translatable("tooltip.alpineshred.design", name).withStyle(ChatFormatting.LIGHT_PURPLE)
                    : Component.translatable("tooltip.alpineshred.design.by", name, design.author()).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        tooltip.add(Component.translatable("tooltip.alpineshred.gear.equip_hint",
                Component.keybind("key.alpineshred.strap")).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static String fmt(double multiplier) {
        return String.format(Locale.ROOT, "%.2f", multiplier);
    }
}
