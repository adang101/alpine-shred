package io.github.adang101.alpineshred.gear;

import net.minecraft.world.item.DyeColor;

/** Goggles lens tints (design doc section 5). {@link #modelValue} drives the item model override. */
public enum Lens {
    CLEAR("clear", 0.0f),
    AMBER("amber", 0.25f),
    BLUE_MIRROR("blue_mirror", 0.5f),
    BLACK("black", 0.75f);

    private final String id;
    private final float modelValue;

    Lens(String id, float modelValue) {
        this.id = id;
        this.modelValue = modelValue;
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
