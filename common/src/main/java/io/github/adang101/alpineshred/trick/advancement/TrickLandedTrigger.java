package io.github.adang101.alpineshred.trick.advancement;

import com.google.gson.JsonObject;
import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.trick.GrabType;
import io.github.adang101.alpineshred.trick.LandingQuality;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;

import java.util.Locale;
import java.util.Set;

/**
 * {@code alpineshred:trick_landed} (design doc section 8): fires on every scored landing. Conditions,
 * all optional: {@code min_score} (the landing's points), {@code min_spin} (degrees, whole
 * half-turns), {@code min_flips}, {@code grab} (a grab held in it: indy, mute, nose, tail, method)
 * and {@code quality} (at least this good: perfect, clean, sketchy).
 */
public final class TrickLandedTrigger extends SimpleCriterionTrigger<TrickLandedTrigger.Instance> {
    public static final ResourceLocation ID = AlpineShred.id("trick_landed");

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    protected Instance createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext context) {
        String grab = GsonHelper.getAsString(json, "grab", "");
        String quality = GsonHelper.getAsString(json, "quality", "");
        return new Instance(player, GsonHelper.getAsInt(json, "min_score", 0), GsonHelper.getAsInt(json, "min_spin", 0),
                GsonHelper.getAsInt(json, "min_flips", 0), grab.isEmpty() ? null : GrabType.valueOf(grab.toUpperCase(Locale.ROOT)),
                quality.isEmpty() ? null : LandingQuality.valueOf(quality.toUpperCase(Locale.ROOT)));
    }

    public void trigger(ServerPlayer player, int score, int spinDegrees, int flips, Set<GrabType> grabs, LandingQuality quality) {
        trigger(player, i -> i.matches(score, spinDegrees, flips, grabs, quality));
    }

    public static final class Instance extends AbstractCriterionTriggerInstance {
        private final int minScore, minSpin, minFlips;
        private final GrabType grab;
        private final LandingQuality quality;

        Instance(ContextAwarePredicate player, int minScore, int minSpin, int minFlips, GrabType grab, LandingQuality quality) {
            super(ID, player);
            this.minScore = minScore;
            this.minSpin = minSpin;
            this.minFlips = minFlips;
            this.grab = grab;
            this.quality = quality;
        }

        boolean matches(int score, int spin, int flips, Set<GrabType> grabs, LandingQuality landed) {
            return score >= minScore && spin >= minSpin && flips >= minFlips && (grab == null || grabs.contains(grab))
                    && (quality == null || landed.ordinal() <= quality.ordinal());
        }

        @Override
        public JsonObject serializeToJson(SerializationContext context) {
            JsonObject json = super.serializeToJson(context);
            if (minScore > 0) json.addProperty("min_score", minScore);
            if (minSpin > 0) json.addProperty("min_spin", minSpin);
            if (minFlips > 0) json.addProperty("min_flips", minFlips);
            if (grab != null) json.addProperty("grab", grab.name().toLowerCase(Locale.ROOT));
            if (quality != null) json.addProperty("quality", quality.serializedName());
            return json;
        }
    }
}
