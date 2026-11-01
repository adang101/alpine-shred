package io.github.adang101.alpineshred.worldgen;

import io.github.adang101.alpineshred.config.AlpineConfig;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * The {@code worldgen.*} switches (design doc section 12, "Config"): each structure type reads its
 * flag before placing, so they work without a datapack. {@code worldgen.frequencyMul} below 1
 * skips that share of starts; above 1 it has no effect (spacing is the structure set's).
 */
public enum WorldgenToggle {
    TERRAIN_PARKS, GROOMED_RUNS, RESORTS, SNOW_FEATURES;

    public boolean on() {
        return switch (this) {
            case TERRAIN_PARKS -> AlpineConfig.WORLDGEN_TERRAIN_PARKS.on();
            case GROOMED_RUNS -> AlpineConfig.WORLDGEN_GROOMED_RUNS.on();
            case RESORTS -> AlpineConfig.WORLDGEN_RESORTS.on();
            case SNOW_FEATURES -> AlpineConfig.WORLDGEN_SNOW_FEATURES.on();
        };
    }

    /** False when this start should not generate: switched off, or skipped by the frequency multiplier. */
    public boolean allows(Structure.GenerationContext context) {
        if (!on()) return false;
        double mul = AlpineConfig.WORLDGEN_FREQUENCY_MUL.value();
        if (mul >= 1.0) return true;
        // The context's random is seeded per start, so the same starts are kept every time.
        return context.random().nextDouble() < mul;
    }
}
