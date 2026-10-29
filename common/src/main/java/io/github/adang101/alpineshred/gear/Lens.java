package io.github.adang101.alpineshred.gear;

import net.minecraft.world.item.DyeColor;

/** Goggles lens tints (design doc section 5). {@link #modelValue} drives the item model override. */
public enum Lens {
    CLEAR("clear", 0.0f, 0x000000, 0),
    AMBER("amber", 0.25f, 0xFFA020, 30),
    BLUE_MIRROR("blue_mirror", 0.5f, 0x3070FF, 26),
    BLACK("black", 0.75f, 0x101018, 70);

    private final String id;
    private final float modelValue;
    private final int tint;
    private final int tintAlpha;

    /**
     * @param tint      first-person overlay color (RGB)
     * @param tintAlpha overlay opacity out of 255; 0 draws no tint (section 17, "Goggle lens")
     */
    Lens(String id, float modelValue, int tint, int tintAlpha) {
        this.id = id;
        this.modelValue = modelValue;
        this.tint = tint;
        this.tintAlpha = tintAlpha;
    }

    /** ARGB of the first-person lens tint. */
    public int overlayArgb() {
        return (tintAlpha << 24) | tint;
    }

    public String id() {
        return id;
    }

    public float modelValue() {
        return modelValue;
    }

    public static Lens byId(String id) {
        for (Lens lens : values()) {
            if (lens.id.equals(id)) return lens;
        }
        return CLEAR;
    }

    /** The lens a dye produces in the {@code goggles_lens} recipe. */
    public static Lens fromDye(DyeColor dye) {
        return switch (dye) {
            case ORANGE, YELLOW, BROWN, RED -> AMBER;
            case BLUE, LIGHT_BLUE, CYAN, PURPLE, MAGENTA -> BLUE_MIRROR;
            case BLACK, GRAY -> BLACK;
            default -> CLEAR;
        };
    }
}
