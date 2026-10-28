package io.github.adang101.alpineshred.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import dev.architectury.registry.ReloadListenerRegistry;
import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.gear.GearKind;
import io.github.adang101.alpineshred.gear.GearStats;
import io.github.adang101.alpineshred.gear.GearStatsRegistry;
import io.github.adang101.alpineshred.movement.MovementTuning;
import io.github.adang101.alpineshred.movement.SurfaceProfile;
import io.github.adang101.alpineshred.movement.TuningData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server data reload listeners (design doc section 18, "Data files"): {@code movement/tuning},
 * {@code surface_profiles} and {@code gear}. {@code box_materials} and {@code design_templates}
 * join in phases 4 and 6. Clients receive all three through {@code s2c/tuning_sync}
 * ({@code ModNetwork}) at login and after every reload: the client-side slide controller reads them.
 */
public final class AlpineDataLoaders {
    private static final Gson GSON = new GsonBuilder().create();

    public static final TuningLoader TUNING = new TuningLoader();
    public static final SurfaceLoader SURFACES = new SurfaceLoader();
    public static final GearLoader GEAR = new GearLoader();

    private AlpineDataLoaders() {
    }

    public static void register() {
        ReloadListenerRegistry.register(PackType.SERVER_DATA, TUNING, AlpineShred.id("movement/tuning"));
        ReloadListenerRegistry.register(PackType.SERVER_DATA, SURFACES, AlpineShred.id("surface_profiles"));
        ReloadListenerRegistry.register(PackType.SERVER_DATA, GEAR, AlpineShred.id("gear"));
    }

    /** {@code /alpineshred tuning reload}: re-reads only this mod's data, without a full /reload. */
    public static void reloadNow(ResourceManager resources) {
        TUNING.reloadNow(resources);
        SURFACES.reloadNow(resources);
        GEAR.reloadNow(resources);
    }

    private abstract static class Loader extends SimpleJsonResourceReloadListener {
        Loader(String directory) {
            super(GSON, directory);
        }

        void reloadNow(ResourceManager resources) {
            apply(prepare(resources, InactiveProfiler.INSTANCE), resources, InactiveProfiler.INSTANCE);
        }
    }

    public static final class TuningLoader extends Loader {
        TuningLoader() {
            super("movement");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller profiler) {
            JsonElement json = files.get(AlpineShred.id("tuning"));
            if (json == null || !json.isJsonObject()) {
                AlpineShred.LOGGER.warn("data/alpineshred/movement/tuning.json missing; using built-in tuning");
                TuningData.setTuning(MovementTuning.DEFAULT);
                return;
            }
            TuningData.setTuning(MovementTuning.fromJson(json.getAsJsonObject(), AlpineShred.LOGGER::warn));
            AlpineShred.LOGGER.debug("Loaded movement tuning");
        }
    }

    public static final class SurfaceLoader extends Loader {
        SurfaceLoader() {
            super("surface_profiles");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller profiler) {
            Map<String, SurfaceProfile> profiles = new LinkedHashMap<>(SurfaceProfile.DEFAULTS);
            files.forEach((id, json) -> {
                if (!json.isJsonObject()) return;
                String key = id.getNamespace().equals(AlpineShred.MOD_ID) ? id.getPath() : id.toString();
                SurfaceProfile profile = SurfaceProfile.fromJson(key, json.getAsJsonObject(), AlpineShred.LOGGER::warn);
                if (profile != null) profiles.put(key, profile);
            });
            TuningData.setSurfaces(new ArrayList<>(profiles.values()));
            AlpineShred.LOGGER.debug("Loaded {} surface profiles", profiles.size());
        }
    }

    public static final class GearLoader extends Loader {
        GearLoader() {
            super("gear");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller profiler) {
            Map<String, GearStats> stats = new LinkedHashMap<>();
            files.forEach((id, json) -> {
                if (!json.isJsonObject() || !id.getNamespace().equals(AlpineShred.MOD_ID)) return;
                GearStats fallback = GearStats.DEFAULTS.getOrDefault(id.getPath(), GearStats.neutral(GearKind.BOARD));
                stats.put(id.getPath(), GearStats.fromJson(id.getPath(), json.getAsJsonObject(), fallback, AlpineShred.LOGGER::warn));
            });
            GearStatsRegistry.replace(stats);
            AlpineShred.LOGGER.debug("Loaded {} gear stat files", stats.size());
        }
    }

    /** For game tests: ids the gear loader has seen. */
    public static List<String> loadedGearIds() {
        return List.copyOf(GearStatsRegistry.all().keySet());
    }
}
