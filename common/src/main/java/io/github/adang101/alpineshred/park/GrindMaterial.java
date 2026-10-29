package io.github.adang101.alpineshred.park;

/** What a grind or slide surface is made of: picks its friction, sound and particles (section 17). */
public enum GrindMaterial {
    METAL, COPPER, WOOD, PLASTIC, STONE;

    public String serializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
