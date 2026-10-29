package io.github.adang101.alpineshred.park;

/** What a grind or slide surface is made of: picks its friction, sound and particles (section 17). */
public enum GrindMaterial {
    METAL, COPPER, WOOD, PLASTIC, STONE, CONCRETE;

    public static GrindMaterial byName(String name) {
        for (GrindMaterial m : values()) {
            if (m.serializedName().equals(name)) return m;
        }
        return null;
    }

    public String serializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
