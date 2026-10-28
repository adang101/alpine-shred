package io.github.adang101.alpineshred.gear;

import io.github.adang101.alpineshred.registry.ModItems;
import io.github.adang101.alpineshred.registry.ModRecipes;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code alpineshred:wax_apply}: one board or pair of skis plus one {@code board_wax} sets
 * {@code Wax} to 3000 blocks (design doc section 18). Every other tag on the stack is kept.
 */
public class WaxApplyRecipe extends CustomRecipe {
    public WaxApplyRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        return !result(contents(container)).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        return result(contents(container));
    }

    /** Pure matching logic, shared with game tests: returns the waxed gear or EMPTY. */
    public static ItemStack result(List<ItemStack> stacks) {
        ItemStack gear = ItemStack.EMPTY;
        int wax = 0;
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof SnowGearItem) {
                if (!gear.isEmpty()) return ItemStack.EMPTY;
                gear = stack;
            } else if (stack.is(ModItems.BOARD_WAX.get())) {
                wax++;
            } else {
                return ItemStack.EMPTY;
            }
        }
        if (gear.isEmpty() || wax != 1) return ItemStack.EMPTY;
        ItemStack out = gear.copyWithCount(1);
        GearNbt.setWax(out, GearWear.WAX_BLOCKS);
        return out;
    }

    private static List<ItemStack> contents(CraftingContainer container) {
        List<ItemStack> stacks = new ArrayList<>(container.getContainerSize());
        for (int i = 0; i < container.getContainerSize(); i++) stacks.add(container.getItem(i));
        return stacks;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer container) {
        return NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.WAX_APPLY.get();
    }
}
