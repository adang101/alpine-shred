package io.github.adang101.alpineshred.movement;

import java.util.Comparator;
import java.util.List;

/**
 * Current movement tuning and surface profiles, replaced atomically on data reload. The world-side
 * surface matcher (phase 2) walks {@link #surfaces()} in priority order.
 */
public final class TuningData {
    private static volatile MovementTuning tuning = MovementTuning.DEFAULT;
    private static volatile List<SurfaceProfile> surfaces = sorted(List.copyOf(SurfaceProfile.DEFAULTS.values()));
    private static volatile boolean loadedFromData;

    private TuningData() {
    }

    public static MovementTuning tuning() {
        return tuning;
    }

    /** Highest priority first. */
    public static List<SurfaceProfile> surfaces() {
        return surfaces;
    }

    public static void setTuning(MovementTuning value) {
        tuning = value;
        loadedFromData = true;
    }

    public static void setSurfaces(List<SurfaceProfile> value) {
        surfaces = sorted(value);
    }

    public static boolean loadedFromData() {
        return loadedFromData;
    }

    private static List<SurfaceProfile> sorted(List<SurfaceProfile> list) {
        return list.stream()
                .sorted(Comparator.comparingInt(SurfaceProfile::priority).reversed().thenComparing(SurfaceProfile::id))
                .toList();
    }
}
