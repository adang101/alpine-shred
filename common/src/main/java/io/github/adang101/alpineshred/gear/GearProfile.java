package io.github.adang101.alpineshred.gear;

import io.github.adang101.alpineshred.registry.ModTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Everything the movement code needs to know about a rider's gear, combined into one record
 * (design doc section 6, "Abstraction"). Built by {@link #build} and cached by {@link GearProfiles}.
 *
 * @param kind        BOARD or SKIS, or null when no rideable gear is equipped
 * @param bootsMatch  feet armor is tagged for this kind ({@code alpineshred:binding/board} or {@code binding/ski})
 */
public record GearProfile(
        ItemStack board,
        @Nullable GearKind kind,
        GearStats stats,
        int wax,
        int edge,
        boolean bootsMatch,
        boolean helmet,
        boolean goggles,
        boolean mitts,
        boolean poles) {

    public static final GearProfile NONE = new GearProfile(ItemStack.EMPTY, null, GearStats.NEUTRAL_BOARD,
            0, GearWear.MAX_EDGE, false, false, false, false, false);

    public boolean hasGear() {
        return kind != null;
    }

    public boolean canRide() {
        return kind != null && bootsMatch;
    }

    public static GearProfile build(Player player) {
        GearAccess access = GearAccess.current();
        ItemStack board = access.board(player).orElse(ItemStack.EMPTY);
        GearKind kind = kindOf(board);
        ItemStack feet = player.getItemBySlot(EquipmentSlot.FEET);
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        boolean bootsMatch = kind != null && bootsMatch(kind, feet);
        boolean helmet = head.getItem() instanceof ArmorItem armor && armor.getType() == ArmorItem.Type.HELMET;
        return new GearProfile(
                board,
                kind,
                kind == null ? GearStats.NEUTRAL_BOARD : statsOf(board, kind),
                kind == null ? 0 : GearNbt.wax(board),
                kind == null ? GearWear.MAX_EDGE : GearNbt.edge(board),
                bootsMatch,
                helmet,
                access.goggles(player).isPresent(),
                access.mitts(player).isPresent(),
                player.getMainHandItem().is(ModTags.Items.POLES));
    }

    /** BOARD for {@code #gear/boards}, SKIS for {@code #gear/skis}, otherwise null. */
    @Nullable
    public static GearKind kindOf(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (stack.is(ModTags.Items.BOARDS)) return GearKind.BOARD;
        if (stack.is(ModTags.Items.SKIS)) return GearKind.SKIS;
        return null;
    }

    public static boolean bootsMatch(GearKind kind, ItemStack feet) {
        return feet.is(kind == GearKind.BOARD ? ModTags.Items.BINDING_BOARD : ModTags.Items.BINDING_SKI);
    }

    private static GearStats statsOf(ItemStack stack, GearKind kind) {
        if (stack.getItem() instanceof SnowGearItem gear) return gear.stats();
        // A datapack-tagged item that is not one of ours rides with neutral stats.
        return GearStats.neutral(kind);
    }
}
