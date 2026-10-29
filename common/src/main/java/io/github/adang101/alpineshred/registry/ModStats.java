package io.github.adang101.alpineshred.registry;

import dev.architectury.registry.registries.DeferredRegister;
import io.github.adang101.alpineshred.AlpineShred;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatFormatter;
import net.minecraft.stats.Stats;

/**
 * Custom statistics (design doc section 8, "Persistence and hooks"): vanilla custom stats, so they
 * show in the Statistics screen and work with scoreboards ({@code minecraft.custom:alpineshred.trick_score}).
 * Distances are in centimetres and times in ticks, as vanilla's are. {@code ride_distance} (for the
 * {@code first_run} advancement) and {@code best_combo} are additions to the doc's list.
 */
public final class ModStats {
    public static final DeferredRegister<ResourceLocation> STATS = DeferredRegister.create(AlpineShred.MOD_ID, Registries.CUSTOM_STAT);

    public static final ResourceLocation TRICK_SCORE = stat("trick_score");
    public static final ResourceLocation TRICKS_LANDED = stat("tricks_landed");
    public static final ResourceLocation AIR_TIME = stat("air_time");
    public static final ResourceLocation GRIND_DISTANCE = stat("grind_distance");
    public static final ResourceLocation LIFT_RIDES = stat("lift_rides");
    public static final ResourceLocation RIDE_DISTANCE = stat("ride_distance");
    public static final ResourceLocation BEST_COMBO = stat("best_combo");

    private ModStats() {
    }

    private static ResourceLocation stat(String path) {
        ResourceLocation id = AlpineShred.id(path);
        STATS.register(path, () -> id);
        return id;
    }

    public static void register() {
        STATS.register();
    }

    /** The formatter each stat is shown with: the first {@code Stats.CUSTOM.get} for an id decides it. */
    public static StatFormatter formatter(ResourceLocation id) {
        if (id.equals(AIR_TIME)) return StatFormatter.TIME;
        if (id.equals(GRIND_DISTANCE) || id.equals(RIDE_DISTANCE)) return StatFormatter.DISTANCE;
        return StatFormatter.DEFAULT;
    }

    /** Creates every stat with its formatter (common setup, and the client before its stats screen). */
    public static void bindFormatters() {
        for (ResourceLocation id : new ResourceLocation[]{TRICK_SCORE, TRICKS_LANDED, AIR_TIME, GRIND_DISTANCE, LIFT_RIDES,
                RIDE_DISTANCE, BEST_COMBO}) {
            stat(id);
        }
    }

    public static Stat<ResourceLocation> stat(ResourceLocation id) {
        return Stats.CUSTOM.get(id, formatter(id));
    }

    public static void award(ServerPlayer player, ResourceLocation id, int amount) {
        if (amount > 0) player.awardStat(stat(id), amount);
    }

    public static int value(ServerPlayer player, ResourceLocation id) {
        return player.getStats().getValue(stat(id));
    }

    /** Raises a "best" stat to {@code value} if it is higher. */
    public static void best(ServerPlayer player, ResourceLocation id, int value) {
        if (value > value(player, id)) player.getStats().setValue(player, stat(id), value);
    }
}
