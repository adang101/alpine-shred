package io.github.adang101.alpineshred.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.gear.GogglesLensRecipe;
import io.github.adang101.alpineshred.gear.WaxApplyRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;

/** The two custom recipe serializers (design doc appendix). */
public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(AlpineShred.MOD_ID, Registries.RECIPE_SERIALIZER);

    public static final RegistrySupplier<SimpleCraftingRecipeSerializer<WaxApplyRecipe>> WAX_APPLY =
            SERIALIZERS.register("wax_apply", () -> new SimpleCraftingRecipeSerializer<>(WaxApplyRecipe::new));
    public static final RegistrySupplier<SimpleCraftingRecipeSerializer<GogglesLensRecipe>> GOGGLES_LENS =
            SERIALIZERS.register("goggles_lens", () -> new SimpleCraftingRecipeSerializer<>(GogglesLensRecipe::new));

    private ModRecipes() {
    }

    public static void register() {
        SERIALIZERS.register();
    }
}
