package io.github.adang101.alpineshred.trick.advancement;

import com.google.gson.JsonObject;
import io.github.adang101.alpineshred.AlpineShred;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;

/**
 * {@code alpineshred:grind_completed} (design doc section 8): fires when a scored grind or box
 * slide ends; {@code min_blocks} is the distance ridden on it, and {@code box} (optional) picks rails
 * ({@code false}) or boxes ({@code true}).
 */
public final class GrindCompletedTrigger extends SimpleCriterionTrigger<GrindCompletedTrigger.Instance> {
    public static final ResourceLocation ID = AlpineShred.id("grind_completed");

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    protected Instance createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext context) {
        Boolean box = json.has("box") ? GsonHelper.getAsBoolean(json, "box") : null;
        return new Instance(player, GsonHelper.getAsFloat(json, "min_blocks", 0), box);
    }

    public void trigger(ServerPlayer player, double blocks, boolean box) {
        trigger(player, i -> blocks >= i.minBlocks && (i.box == null || i.box == box));
    }

    public static final class Instance extends AbstractCriterionTriggerInstance {
        private final float minBlocks;
        private final Boolean box;

        Instance(ContextAwarePredicate player, float minBlocks, Boolean box) {
            super(ID, player);
            this.minBlocks = minBlocks;
            this.box = box;
        }

        @Override
        public JsonObject serializeToJson(SerializationContext context) {
            JsonObject json = super.serializeToJson(context);
            if (minBlocks > 0) json.addProperty("min_blocks", minBlocks);
            if (box != null) json.addProperty("box", box);
            return json;
        }
    }
}
