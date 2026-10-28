package io.github.adang101.alpineshred.gear;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * Stack NBT for boards and skis (design doc section 5, "Item state"). {@code Wax} and
 * {@code Edge} are top-level ints so other keys ({@code display}, {@code Design}) are never touched.
 */
public final class GearNbt {
    public static final String WAX = "Wax";
    public static final String EDGE = "Edge";

    private GearNbt() {
    }

    /** Remaining blocks of waxed sliding; 0 when unwaxed. */
    public static int wax(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(WAX, Tag.TAG_ANY_NUMERIC) ? Math.max(0, tag.getInt(WAX)) : 0;
    }

    public static void setWax(ItemStack stack, int blocks) {
        if (blocks <= 0) {
            CompoundTag tag = stack.getTag();
            if (tag != null) {
                tag.remove(WAX);
                if (tag.isEmpty()) stack.setTag(null);
            }
        } else {
            stack.getOrCreateTag().putInt(WAX, Math.min(blocks, GearWear.WAX_BLOCKS * 4));
        }
    }

    /** Edge sharpness 0..100; new gear is sharp (missing key = 100). */
    public static int edge(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(EDGE, Tag.TAG_ANY_NUMERIC)) return GearWear.MAX_EDGE;
        return Math.max(0, Math.min(GearWear.MAX_EDGE, tag.getInt(EDGE)));
    }

    public static void setEdge(ItemStack stack, int edge) {
        int clamped = Math.max(0, Math.min(GearWear.MAX_EDGE, edge));
        if (clamped == GearWear.MAX_EDGE) {
            CompoundTag tag = stack.getTag();
            if (tag != null) {
                tag.remove(EDGE);
                if (tag.isEmpty()) stack.setTag(null);
            }
        } else {
            stack.getOrCreateTag().putInt(EDGE, clamped);
        }
    }
}
