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
 * {@code alpineshred:lift_ridden} (design doc section 8): fires when a player is unloaded at the top of a
 * lift. Condition, optional: {@code min_length}, the loaded cable length ridden in blocks. The length is
 * measured from the loop distance where the player boarded to the ejection point.
 */
public final class LiftRiddenTrigger extends SimpleCriterionTrigger<LiftRiddenTrigger.Instance> {
    public static final ResourceLocation ID = AlpineShred.id("lift_ridden");

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    protected Instance createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext context) {
        return new Instance(player, GsonHelper.getAsInt(json, "min_length", 0));
    }

    public void trigger(ServerPlayer player, int lengthBlocks) {
        trigger(player, i -> i.matches(lengthBlocks));
    }

    public static final class Instance extends AbstractCriterionTriggerInstance {
        private final int minLength;

        Instance(ContextAwarePredicate player, int minLength) {
            super(ID, player);
            this.minLength = minLength;
        }

        boolean matches(int length) {
            return length >= minLength;
        }

        @Override
        public JsonObject serializeToJson(SerializationContext context) {
            JsonObject json = super.serializeToJson(context);
            if (minLength > 0) json.addProperty("min_length", minLength);
            return json;
        }
    }
}
