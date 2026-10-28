package io.github.adang101.alpineshred.gear;

import io.github.adang101.alpineshred.registry.ModItems;
import io.github.adang101.alpineshred.registry.ModRecipes;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** {@code alpineshred:goggles_lens}: goggles plus one dye picks the lens tint ({@link Lens#fromDye}). */
public class GogglesLensRecipe extends CustomRecipe {
    public GogglesLensRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        return !assemble(container, null).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        ItemStack goggles = ItemStack.EMPTY;
        DyeItem dye = null;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.is(ModItems.GOGGLES.get())) {
                if (!goggles.isEmpty()) return ItemStack.EMPTY;
                goggles = stack;
            } else if (stack.getItem() instanceof DyeItem d) {
                if (dye != null) return ItemStack.EMPTY;
                dye = d;
            } else {
                return ItemStack.EMPTY;
            }
        }
        if (goggles.isEmpty() || dye == null) return ItemStack.EMPTY;
        Lens lens = Lens.fromDye(dye.getDyeColor());
        if (lens == GogglesItem.lens(goggles)) return ItemStack.EMPTY;
        return GogglesItem.withLens(goggles.copyWithCount(1), lens);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.GOGGLES_LENS.get();
    }
}
