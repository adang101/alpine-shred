package io.github.adang101.alpineshred.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.gear.AlpineArmorItem;
import io.github.adang101.alpineshred.gear.DescribedItem;
import io.github.adang101.alpineshred.gear.EdgeTunerItem;
import io.github.adang101.alpineshred.gear.GearKind;
import io.github.adang101.alpineshred.gear.GearStats;
import io.github.adang101.alpineshred.gear.GogglesItem;
import io.github.adang101.alpineshred.gear.MittsItem;
import io.github.adang101.alpineshred.gear.SkiPolesItem;
import io.github.adang101.alpineshred.gear.SnowGearItem;
import io.github.adang101.alpineshred.park.item.ParkWrenchItem;
import io.github.adang101.alpineshred.park.item.PipeItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** All 21 items from the design doc appendix, plus block items. */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(AlpineShred.MOD_ID, Registries.ITEM);

    private static final List<RegistrySupplier<? extends Item>> BOARDS_AND_SKIS = new ArrayList<>();

    // ---- boards ------------------------------------------------------------------------------
    public static final RegistrySupplier<SnowGearItem> SNOWBOARD = gear("snowboard", GearKind.BOARD, Rarity.COMMON, false);
    public static final RegistrySupplier<SnowGearItem> PARK_SNOWBOARD = gear("park_snowboard", GearKind.BOARD, Rarity.COMMON, false);
    public static final RegistrySupplier<SnowGearItem> FREERIDE_SNOWBOARD = gear("freeride_snowboard", GearKind.BOARD, Rarity.COMMON, false);
    public static final RegistrySupplier<SnowGearItem> PRO_SNOWBOARD = gear("pro_snowboard", GearKind.BOARD, Rarity.RARE, true);

    // ---- skis --------------------------------------------------------------------------------
    public static final RegistrySupplier<SnowGearItem> SKIS = gear("skis", GearKind.SKIS, Rarity.COMMON, false);
    public static final RegistrySupplier<SnowGearItem> CARVING_SKIS = gear("carving_skis", GearKind.SKIS, Rarity.COMMON, false);
    public static final RegistrySupplier<SnowGearItem> POWDER_SKIS = gear("powder_skis", GearKind.SKIS, Rarity.COMMON, false);
    public static final RegistrySupplier<SnowGearItem> PARK_SKIS = gear("park_skis", GearKind.SKIS, Rarity.COMMON, false);
    public static final RegistrySupplier<SnowGearItem> PRO_SKIS = gear("pro_skis", GearKind.SKIS, Rarity.RARE, true);

    // ---- wearables ---------------------------------------------------------------------------
    public static final RegistrySupplier<AlpineArmorItem> SKI_BOOTS = ITEMS.register("ski_boots",
            () -> new AlpineArmorItem(ArmorItem.Type.BOOTS, 0x2F5D8C, "tooltip.alpineshred.ski_boots", new Item.Properties()));
    public static final RegistrySupplier<AlpineArmorItem> SNOWBOARD_BOOTS = ITEMS.register("snowboard_boots",
            () -> new AlpineArmorItem(ArmorItem.Type.BOOTS, 0x3B3B3B, "tooltip.alpineshred.snowboard_boots", new Item.Properties()));
    public static final RegistrySupplier<AlpineArmorItem> SKI_HELMET = ITEMS.register("ski_helmet",
            () -> new AlpineArmorItem(ArmorItem.Type.HELMET, 0xE8E8E8, "tooltip.alpineshred.ski_helmet", new Item.Properties()));
    public static final RegistrySupplier<GogglesItem> GOGGLES = ITEMS.register("goggles",
            () -> new GogglesItem(new Item.Properties().stacksTo(1)));
    public static final RegistrySupplier<MittsItem> MITTS = ITEMS.register("mitts",
            () -> new MittsItem(new Item.Properties().stacksTo(1)));

    // ---- tools -------------------------------------------------------------------------------
    public static final RegistrySupplier<SkiPolesItem> SKI_POLES = ITEMS.register("ski_poles",
            () -> new SkiPolesItem(new Item.Properties().durability(250)));
    public static final RegistrySupplier<SkiPolesItem> CARBON_SKI_POLES = ITEMS.register("carbon_ski_poles",
            () -> new SkiPolesItem(new Item.Properties().durability(500)));
    public static final RegistrySupplier<DescribedItem> BOARD_WAX = ITEMS.register("board_wax",
            () -> new DescribedItem("tooltip.alpineshred.board_wax", new Item.Properties()));
    public static final RegistrySupplier<EdgeTunerItem> EDGE_TUNER = ITEMS.register("edge_tuner",
            () -> new EdgeTunerItem(new Item.Properties().durability(64)));
    public static final RegistrySupplier<ParkWrenchItem> PARK_WRENCH = ITEMS.register("park_wrench",
            () -> new ParkWrenchItem(new Item.Properties().stacksTo(1)));
    public static final RegistrySupplier<DescribedItem> LIFT_LINKER = ITEMS.register("lift_linker",
            () -> new DescribedItem("tooltip.alpineshred.lift_linker", new Item.Properties().stacksTo(1)));
    public static final RegistrySupplier<DescribedItem> LIFT_PASS = ITEMS.register("lift_pass",
            () -> new DescribedItem("tooltip.alpineshred.lift_pass", new Item.Properties()));

    // ---- block items -------------------------------------------------------------------------
    public static final RegistrySupplier<BlockItem> GROOMED_SNOW = ITEMS.register("groomed_snow",
            () -> new BlockItem(ModBlocks.GROOMED_SNOW.get(), new Item.Properties()));

    /** The park blocks' items (section 10, 11), in block registration order; the pipe's places a whole pipe. */
    private static final List<RegistrySupplier<? extends Item>> PARK = registerParkItems();

    private ModItems() {
    }

    private static List<RegistrySupplier<? extends Item>> registerParkItems() {
        List<RegistrySupplier<? extends Item>> out = new ArrayList<>();
        for (RegistrySupplier<? extends Block> block : ModBlocks.parkBlocks()) {
            String id = block.getId().getPath();
            if (block == ModBlocks.QUARTER_PIPE) {
                out.add(ITEMS.register(id, () -> new PipeItem(block.get(), new Item.Properties())));
            } else {
                out.add(ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties())));
            }
        }
        return Collections.unmodifiableList(out);
    }

    public static List<RegistrySupplier<? extends Item>> parkItems() {
        return PARK;
    }

    /** The park block item registered as {@code id}. */
    public static RegistrySupplier<? extends Item> parkItem(String id) {
        for (RegistrySupplier<? extends Item> item : PARK) {
            if (item.getId().getPath().equals(id)) return item;
        }
        throw new IllegalArgumentException("no park item " + id);
    }

    private static RegistrySupplier<SnowGearItem> gear(String id, GearKind kind, Rarity rarity, boolean fireResistant) {
        int durability = GearStats.DEFAULT_DURABILITY.get(id);
        RegistrySupplier<SnowGearItem> supplier = ITEMS.register(id, () -> {
            Item.Properties properties = new Item.Properties().durability(durability).rarity(rarity);
            if (fireResistant) properties.fireResistant();
            return new SnowGearItem(kind, properties);
        });
        BOARDS_AND_SKIS.add(supplier);
        return supplier;
    }

    public static List<RegistrySupplier<? extends Item>> boardsAndSkis() {
        return Collections.unmodifiableList(BOARDS_AND_SKIS);
    }

    public static void register() {
        ITEMS.register();
    }
}
