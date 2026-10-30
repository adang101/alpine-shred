package io.github.adang101.alpineshred.fabric.datagen;

import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.registry.ModBlocks;
import io.github.adang101.alpineshred.registry.ModItems;
import io.github.adang101.alpineshred.registry.ModRecipes;
import io.github.adang101.alpineshred.registry.ModTags;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.recipes.SmithingTransformRecipeBuilder;
import net.minecraft.data.recipes.SpecialRecipeBuilder;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

/**
 * Recipes (design doc section 5, "Core ingredients", and section 18). Patterns are set here; the
 * doc fixes only the ingredients. The survival audience (section 21) is why upgrades consume the
 * base gear and the pro tier needs netherite.
 */
public final class AlpineRecipeProvider extends FabricRecipeProvider {
    public AlpineRecipeProvider(FabricDataOutput output) {
        super(output);
    }

    @Override
    public void buildRecipes(Consumer<FinishedRecipe> out) {
        // ---- boards: a diagonal deck of planks with a binding core ----
        board(out, ModItems.SNOWBOARD.get(), Items.SPRUCE_PLANKS, Items.LEATHER, Items.IRON_NUGGET);
        board(out, ModItems.PARK_SNOWBOARD.get(), Items.BIRCH_PLANKS, Items.SLIME_BALL, Items.COPPER_INGOT);
        board(out, ModItems.FREERIDE_SNOWBOARD.get(), Items.SPRUCE_PLANKS, Items.PHANTOM_MEMBRANE, Items.PACKED_ICE);
        SmithingTransformRecipeBuilder.smithing(
                        Ingredient.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                        Ingredient.of(ModItems.PARK_SNOWBOARD.get(), ModItems.FREERIDE_SNOWBOARD.get()),
                        Ingredient.of(Items.NETHERITE_INGOT),
                        RecipeCategory.TOOLS, ModItems.PRO_SNOWBOARD.get())
                .unlocks(getHasName(Items.NETHERITE_INGOT), has(Items.NETHERITE_INGOT))
                .save(out, AlpineShred.id("pro_snowboard_smithing"));

        // ---- skis: two plank runners with leather straps; upgrades start from basic skis ----
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ModItems.SKIS.get())
                .pattern("P P")
                .pattern("PLP")
                .pattern("N N")
                .define('P', Items.BIRCH_PLANKS)
                .define('L', Items.LEATHER)
                .define('N', Items.IRON_NUGGET)
                .unlockedBy(getHasName(Items.LEATHER), has(Items.LEATHER))
                .save(out);
        skiUpgrade(out, ModItems.CARVING_SKIS.get(), Items.IRON_INGOT, Items.PACKED_ICE);
        skiUpgrade(out, ModItems.POWDER_SKIS.get(), Items.PHANTOM_MEMBRANE, Items.FEATHER);
        skiUpgrade(out, ModItems.PARK_SKIS.get(), Items.SLIME_BALL, Items.COPPER_INGOT);
        SmithingTransformRecipeBuilder.smithing(
                        Ingredient.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                        Ingredient.of(ModItems.CARVING_SKIS.get(), ModItems.PARK_SKIS.get()),
                        Ingredient.of(Items.NETHERITE_INGOT),
                        RecipeCategory.TOOLS, ModItems.PRO_SKIS.get())
                .unlocks(getHasName(Items.NETHERITE_INGOT), has(Items.NETHERITE_INGOT))
                .save(out, AlpineShred.id("pro_skis_smithing"));

        // ---- wearables ----
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, ModItems.SKI_BOOTS.get())
                .pattern("LNL")
                .pattern("W W")
                .define('L', Items.LEATHER)
                .define('N', Items.IRON_NUGGET)
                .define('W', ItemTags.WOOL)
                .unlockedBy(getHasName(Items.LEATHER), has(Items.LEATHER))
                .save(out);
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, ModItems.SNOWBOARD_BOOTS.get())
                .pattern("LSL")
                .pattern("W W")
                .define('L', Items.LEATHER)
                .define('S', Items.STRING)
                .define('W', ItemTags.WOOL)
                .unlockedBy(getHasName(Items.LEATHER), has(Items.LEATHER))
                .save(out);
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, ModItems.SKI_HELMET.get())
                .pattern("LIL")
                .pattern("W W")
                .define('L', Items.LEATHER)
                .define('I', Items.IRON_INGOT)
                .define('W', ItemTags.WOOL)
                .unlockedBy(getHasName(Items.LEATHER), has(Items.LEATHER))
                .save(out);
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ModItems.GOGGLES.get())
                .pattern("LLL")
                .pattern("GDG")
                .define('L', Items.LEATHER)
                .define('G', Items.GLASS_PANE)
                .define('D', ModTags.Items.DESIGN_COST)
                .unlockedBy(getHasName(Items.GLASS_PANE), has(Items.GLASS_PANE))
                .save(out);
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ModItems.MITTS.get())
                .pattern("L L")
                .pattern("W W")
                .define('L', Items.LEATHER)
                .define('W', ItemTags.WOOL)
                .unlockedBy(getHasName(Items.LEATHER), has(Items.LEATHER))
                .save(out);

        // ---- tools ----
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ModItems.SKI_POLES.get())
                .pattern("I I")
                .pattern("I I")
                .pattern("S S")
                .define('I', Items.IRON_INGOT)
                .define('S', Items.STRING)
                .unlockedBy(getHasName(Items.IRON_INGOT), has(Items.IRON_INGOT))
                .save(out);
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ModItems.CARBON_SKI_POLES.get())
                .pattern("A A")
                .pattern("I I")
                .pattern("S S")
                .define('A', Items.AMETHYST_SHARD)
                .define('I', Items.IRON_INGOT)
                .define('S', Items.STRING)
                .unlockedBy(getHasName(Items.AMETHYST_SHARD), has(Items.AMETHYST_SHARD))
                .save(out);
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.BOARD_WAX.get(), 2)
                .requires(Items.HONEYCOMB)
                .requires(Items.SNOWBALL)
                .unlockedBy(getHasName(Items.HONEYCOMB), has(Items.HONEYCOMB))
                .save(out);
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ModItems.EDGE_TUNER.get())
                .pattern("F")
                .pattern("I")
                .pattern("S")
                .define('F', Items.FLINT)
                .define('I', Items.IRON_INGOT)
                .define('S', Items.STICK)
                .unlockedBy(getHasName(Items.FLINT), has(Items.FLINT))
                .save(out);
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ModItems.PARK_WRENCH.get())
                .pattern("C C")
                .pattern(" I ")
                .pattern(" I ")
                .define('C', Items.COPPER_INGOT)
                .define('I', Items.IRON_INGOT)
                .unlockedBy(getHasName(Items.COPPER_INGOT), has(Items.COPPER_INGOT))
                .save(out);
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ModItems.LIFT_LINKER.get())
                .pattern("SRS")
                .pattern(" I ")
                .define('S', Items.STRING)
                .define('R', Items.REDSTONE)
                .define('I', Items.IRON_INGOT)
                .unlockedBy(getHasName(Items.REDSTONE), has(Items.REDSTONE))
                .save(out);
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.LIFT_PASS.get())
                .requires(Items.PAPER)
                .requires(Items.GOLD_NUGGET)
                .unlockedBy(getHasName(Items.PAPER), has(Items.PAPER))
                .save(out);

        // ---- blocks ----
        // Section 22: a crafting table plus red, blue and yellow dye.
        ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, ModItems.PAINT_BENCH.get())
                .requires(Items.CRAFTING_TABLE)
                .requires(Items.RED_DYE)
                .requires(Items.BLUE_DYE)
                .requires(Items.YELLOW_DYE)
                .unlockedBy(getHasName(Items.CRAFTING_TABLE), has(Items.CRAFTING_TABLE))
                .save(out);
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModItems.GROOMED_SNOW.get(), 4)
                .pattern("SS")
                .pattern("SS")
                .define('S', Items.SNOW_BLOCK)
                .unlockedBy(getHasName(Items.SNOW_BLOCK), has(Items.SNOW_BLOCK))
                .save(out);

        // ---- park (sections 10 and 11) ----
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.PIPE_RAIL.get(), 6)
                .pattern("III")
                .pattern("N N")
                .define('I', Items.IRON_INGOT)
                .define('N', Items.IRON_NUGGET)
                .unlockedBy(getHasName(Items.IRON_INGOT), has(Items.IRON_INGOT))
                .save(out);
        ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, ModBlocks.FLAT_RAIL.get())
                .requires(ModBlocks.PIPE_RAIL.get())
                .unlockedBy(getHasName(ModBlocks.PIPE_RAIL.get()), has(ModBlocks.PIPE_RAIL.get()))
                .save(out);
        ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, ModBlocks.PIPE_RAIL.get())
                .requires(ModBlocks.FLAT_RAIL.get())
                .unlockedBy(getHasName(ModBlocks.FLAT_RAIL.get()), has(ModBlocks.FLAT_RAIL.get()))
                .save(out, AlpineShred.id("pipe_rail_from_flat_rail"));
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.COPPER_RAILS.get(0).get(), 6)
                .pattern("CCC")
                .pattern("N N")
                .define('C', Items.COPPER_INGOT)
                .define('N', Items.IRON_NUGGET)
                .unlockedBy(getHasName(Items.COPPER_INGOT), has(Items.COPPER_INGOT))
                .save(out);
        for (int stage = 0; stage < ModBlocks.COPPER_RAILS.size(); stage++) {
            Block plain = ModBlocks.COPPER_RAILS.get(stage).get();
            ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, ModBlocks.WAXED_COPPER_RAILS.get(stage).get())
                    .requires(plain)
                    .requires(Items.HONEYCOMB)
                    .unlockedBy(getHasName(plain), has(plain))
                    .save(out, AlpineShred.id(BuiltInRegistries.BLOCK.getKey(ModBlocks.WAXED_COPPER_RAILS.get(stage).get()).getPath() + "_from_honeycomb"));
        }
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.RAIL_POST.get(), 4)
                .pattern("B")
                .pattern("B")
                .pattern("I")
                .define('B', Items.IRON_BARS)
                .define('I', Items.IRON_INGOT)
                .unlockedBy(getHasName(Items.IRON_BARS), has(Items.IRON_BARS))
                .save(out);
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.WOOD_BOX.get(), 4)
                .pattern("SSS")
                .pattern("PPP")
                .define('S', ItemTags.WOODEN_SLABS)
                .define('P', ItemTags.PLANKS)
                .unlockedBy("has_planks", has(ItemTags.PLANKS))
                .save(out);
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.METAL_BOX.get(), 4)
                .pattern("III")
                .pattern("PPP")
                .define('I', Items.IRON_INGOT)
                .define('P', ItemTags.PLANKS)
                .unlockedBy(getHasName(Items.IRON_INGOT), has(Items.IRON_INGOT))
                .save(out);
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.PLASTIC_BOX.get(), 4)
                .pattern("SSS")
                .pattern("C C")
                .pattern("CCC")
                .define('S', Items.SLIME_BALL)
                .define('C', Items.WHITE_CONCRETE)
                .unlockedBy(getHasName(Items.SLIME_BALL), has(Items.SLIME_BALL))
                .save(out);
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.PARK_RAMP.get(), 6)
                .pattern("  P")
                .pattern(" PP")
                .pattern("SSS")
                .define('P', ItemTags.PLANKS)
                .define('S', Items.SNOW_BLOCK)
                .unlockedBy("has_planks", has(ItemTags.PLANKS))
                .save(out);
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.QUARTER_PIPE.get())
                .pattern("P  ")
                .pattern("PP ")
                .pattern("SPP")
                .define('P', ItemTags.PLANKS)
                .define('S', Items.SNOW_BLOCK)
                .unlockedBy("has_planks", has(ItemTags.PLANKS))
                .save(out);
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.PIPE_COPING.get(), 4)
                .pattern("III")
                .pattern("SSS")
                .define('I', Items.IRON_INGOT)
                .define('S', Items.SMOOTH_STONE)
                .unlockedBy(getHasName(Items.IRON_INGOT), has(Items.IRON_INGOT))
                .save(out);
        marker(out, ModBlocks.SLOPE_MARKER_GREEN.get(), Items.GREEN_DYE);
        marker(out, ModBlocks.SLOPE_MARKER_BLUE.get(), Items.BLUE_DYE);
        marker(out, ModBlocks.SLOPE_MARKER_BLACK.get(), Items.BLACK_DYE);
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, ModBlocks.PARK_FLAG.get(), 2)
                .pattern("SW")
                .pattern("S ")
                .pattern("S ")
                .define('S', Items.STICK)
                .define('W', Items.RED_WOOL)
                .unlockedBy(getHasName(Items.RED_WOOL), has(Items.RED_WOOL))
                .save(out);
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, ModBlocks.START_GATE.get())
                .pattern("SSS")
                .pattern("S S")
                .pattern("S S")
                .define('S', Items.STICK)
                .unlockedBy(getHasName(Items.STICK), has(Items.STICK))
                .save(out);

        // ---- special recipes (section 18) ----
        SpecialRecipeBuilder.special(ModRecipes.WAX_APPLY.get()).save(out, AlpineShred.id("wax_apply").toString());
        SpecialRecipeBuilder.special(ModRecipes.GOGGLES_LENS.get()).save(out, AlpineShred.id("goggles_lens").toString());
    }

    private static void marker(Consumer<FinishedRecipe> out, Block marker, Item dye) {
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, marker, 3)
                .pattern("D")
                .pattern("S")
                .pattern("S")
                .define('D', dye)
                .define('S', Items.STICK)
                .unlockedBy(getHasName(dye), has(dye))
                .save(out);
    }

    private static void board(Consumer<FinishedRecipe> out, Item board, Item planks, Item core, Item hardware) {
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, board)
                .pattern(" PH")
                .pattern("PCP")
                .pattern("HP ")
                .define('P', planks)
                .define('C', core)
                .define('H', hardware)
                .unlockedBy(getHasName(core), has(core))
                .save(out);
    }

    private static void skiUpgrade(Consumer<FinishedRecipe> out, Item result, Item first, Item second) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, result)
                .requires(ModItems.SKIS.get())
                .requires(first)
                .requires(second)
                .requires(second)
                .unlockedBy(getHasName(ModItems.SKIS.get()), has(ModItems.SKIS.get()))
                .save(out);
    }
}
