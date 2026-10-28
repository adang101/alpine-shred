package io.github.adang101.alpineshred.gametest;

import io.github.adang101.alpineshred.gear.GearAccess;
import io.github.adang101.alpineshred.gear.GearNbt;
import io.github.adang101.alpineshred.gear.GearProfile;
import io.github.adang101.alpineshred.gear.GearProfiles;
import io.github.adang101.alpineshred.gear.GearStats;
import io.github.adang101.alpineshred.gear.GearStatsRegistry;
import io.github.adang101.alpineshred.gear.GearWear;
import io.github.adang101.alpineshred.gear.GogglesItem;
import io.github.adang101.alpineshred.gear.Lens;
import io.github.adang101.alpineshred.gear.WaxApplyRecipe;
import io.github.adang101.alpineshred.movement.MovementTuning;
import io.github.adang101.alpineshred.movement.RideMode;
import io.github.adang101.alpineshred.movement.RideSnapshot;
import io.github.adang101.alpineshred.movement.RideState;
import io.github.adang101.alpineshred.movement.Riders;
import io.github.adang101.alpineshred.movement.StrapCheck;
import io.github.adang101.alpineshred.movement.StrapController;
import io.github.adang101.alpineshred.movement.TuningData;
import io.github.adang101.alpineshred.registry.ModItems;
import io.github.adang101.alpineshred.registry.ModTags;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Game-test logic shared by both loaders (design doc section 19). Each loader has a thin holder
 * class that registers these with its own template conventions; all tests use the
 * {@code alpineshred:empty} template. They run on the headless test server in CI, so they prove
 * on every loader that the mixins apply, the registries and data load, the Curios/Trinkets slots
 * exist, and strapping in follows the section 6 rules.
 */
public final class AlpineGameTests {
    public static final String TEMPLATE = "empty";

    private AlpineGameTests() {
    }

    // ---- data and registries ------------------------------------------------------------------

    public static void dataFilesLoad(GameTestHelper helper) {
        check(TuningData.loadedFromData(), "movement/tuning.json was not loaded");
        check(TuningData.tuning().values().equals(MovementTuning.DEFAULT.values()), "shipped tuning differs from built-in defaults");
        check(TuningData.surfaces().size() >= 8, "surface profiles missing: " + TuningData.surfaces().size());
        check(GearStatsRegistry.loadedFromData(), "gear stats were not loaded from data");
        for (var entry : GearStats.DEFAULTS.entrySet()) {
            GearStats loaded = GearStatsRegistry.all().get(entry.getKey());
            check(entry.getValue().equals(loaded), "gear/" + entry.getKey() + " loaded as " + loaded);
        }
        helper.succeed();
    }

    public static void tagsAreWired(GameTestHelper helper) {
        check(new ItemStack(ModItems.PRO_SNOWBOARD.get()).is(ModTags.Items.BOARDS), "pro_snowboard not in #gear/boards");
        check(new ItemStack(ModItems.POWDER_SKIS.get()).is(ModTags.Items.SKIS), "powder_skis not in #gear/skis");
        check(new ItemStack(ModItems.SNOWBOARD_BOOTS.get()).is(ModTags.Items.BINDING_BOARD), "snowboard_boots not in #binding/board");
        check(new ItemStack(ModItems.SKI_BOOTS.get()).is(ModTags.Items.BINDING_SKI), "ski_boots not in #binding/ski");
        check(new ItemStack(ModItems.CARBON_SKI_POLES.get()).is(ModTags.Items.POLES), "carbon poles not in #gear/poles");
        check(new ItemStack(Items.WHITE_WOOL).is(ModTags.Items.REPAIR_SOFT_GEAR), "wool cannot repair soft gear");
        helper.succeed();
    }

    // ---- accessory slots ----------------------------------------------------------------------

    public static void accessorySlotsExist(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        GearAccess access = GearAccess.current();
        ItemStack board = new ItemStack(ModItems.SNOWBOARD.get());
        ItemStack goggles = GogglesItem.withLens(new ItemStack(ModItems.GOGGLES.get()), Lens.AMBER);
        ItemStack mitts = new ItemStack(ModItems.MITTS.get());
        check(access.set(player, GearAccess.Slot.BOARD, board), "no snow gear slot on players");
        check(access.set(player, GearAccess.Slot.GOGGLES, goggles), "no goggles slot on players");
        check(access.set(player, GearAccess.Slot.MITTS, mitts), "no mitts slot on players");
        check(access.board(player).map(s -> s.is(ModItems.SNOWBOARD.get())).orElse(false), "board not read back");
        check(access.goggles(player).map(s -> GogglesItem.lens(s) == Lens.AMBER).orElse(false), "goggles not read back");
        check(access.mitts(player).isPresent(), "mitts not read back");
        check(access.set(player, GearAccess.Slot.BOARD, ItemStack.EMPTY), "could not clear the board slot");
        check(access.board(player).isEmpty(), "board slot not cleared");
        helper.succeed();
    }

    // ---- strapping in -------------------------------------------------------------------------

    public static void strapNeedsGearAndMatchingBoots(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        expect(StrapCheck.Result.NO_GEAR, StrapController.tryStrap(player));

        GearAccess.current().set(player, GearAccess.Slot.BOARD, new ItemStack(ModItems.SNOWBOARD.get()));
        expect(StrapCheck.Result.BOOTS_BOARD, StrapController.tryStrap(player));

        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.SKI_BOOTS.get()));
        expect(StrapCheck.Result.BOOTS_BOARD, StrapController.tryStrap(player));

        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.SNOWBOARD_BOOTS.get()));
        expect(StrapCheck.Result.OK, StrapController.tryStrap(player));

        RideSnapshot ride = Riders.get(player);
        check(ride.state() == RideState.IDLE, "state after strap-in: " + ride.state());
        check(ride.mode() == RideMode.BOARD, "mode after strap-in: " + ride.mode());
        helper.succeed();
    }

    public static void skisNeedSkiBoots(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        GearAccess.current().set(player, GearAccess.Slot.BOARD, new ItemStack(ModItems.CARVING_SKIS.get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.SNOWBOARD_BOOTS.get()));
        expect(StrapCheck.Result.BOOTS_SKIS, StrapController.tryStrap(player));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.SKI_BOOTS.get()));
        expect(StrapCheck.Result.OK, StrapController.tryStrap(player));
        check(Riders.get(player).mode() == RideMode.SKIS, "mode should be skis");
        helper.succeed();
    }

    public static void strappedRidersWalkSlowerAndCannotSprint(GameTestHelper helper) {
        Player player = strappedBoarder(helper);
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        check(speed != null && speed.getModifier(StrapController.WALK_MODIFIER_ID) != null, "walk modifier missing");
        double base = speed.getBaseValue();
        double expected = base * (1 + TuningData.tuning().strappedWalkMod);
        check(Math.abs(speed.getValue() - expected) < 1e-6, "walk speed " + speed.getValue() + ", expected " + expected);
        player.setSprinting(true);
        check(!player.isSprinting(), "strapped rider started sprinting");

        StrapController.unstrap(player, StrapController.UnstrapReason.USER);
        check(speed.getModifier(StrapController.WALK_MODIFIER_ID) == null, "walk modifier left after unstrap");
        player.setSprinting(true);
        check(player.isSprinting(), "unstrapped player cannot sprint");
        helper.succeed();
    }

    public static void removingGearUnstraps(GameTestHelper helper) {
        Player player = strappedBoarder(helper);
        GearAccess.current().set(player, GearAccess.Slot.BOARD, ItemStack.EMPTY);
        GearProfiles.invalidate(player);
        check(StrapController.autoUnstrapReason(player) == StrapController.UnstrapReason.GEAR_REMOVED,
                "expected GEAR_REMOVED, got " + StrapController.autoUnstrapReason(player));
        StrapController.serverTick(player);
        check(!Riders.isStrapped(player), "still strapped after removing the board");
        helper.succeed();
    }

    public static void swappingBootsUnstraps(GameTestHelper helper) {
        Player player = strappedBoarder(helper);
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.SKI_BOOTS.get()));
        GearProfiles.invalidate(player);
        StrapController.serverTick(player);
        check(!Riders.isStrapped(player), "still strapped in ski boots on a board");
        helper.succeed();
    }

    public static void rideStateSurvivesTheSyncedAccessor(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        check(Riders.get(player).equals(RideSnapshot.OFF), "new players must start OFF");
        RideSnapshot snapshot = RideSnapshot.OFF.withState(RideState.CARVE).withMode(RideMode.SKIS).withMotion(0.8, 135, -0.66);
        Riders.set(player, snapshot);
        check(Riders.get(player).equals(snapshot), "synced value did not round-trip: " + Riders.get(player));
        helper.succeed();
    }

    // ---- items ----------------------------------------------------------------------------------

    public static void waxRecipeSetsWaxAndKeepsOtherTags(GameTestHelper helper) {
        ItemStack board = new ItemStack(ModItems.PARK_SNOWBOARD.get());
        GearNbt.setEdge(board, 42);
        ItemStack waxed = WaxApplyRecipe.result(List.of(board, new ItemStack(ModItems.BOARD_WAX.get()), ItemStack.EMPTY));
        check(!waxed.isEmpty(), "recipe did not match");
        check(GearNbt.wax(waxed) == GearWear.WAX_BLOCKS, "wax = " + GearNbt.wax(waxed));
        check(GearNbt.edge(waxed) == 42, "edge was not preserved");
        check(WaxApplyRecipe.result(List.of(board, new ItemStack(ModItems.BOARD_WAX.get(), 1), new ItemStack(ModItems.BOARD_WAX.get()))).isEmpty(),
                "two wax should not match");
        check(WaxApplyRecipe.result(List.of(new ItemStack(ModItems.BOARD_WAX.get()))).isEmpty(), "wax alone should not match");
        helper.succeed();
    }

    public static void gearProfileReadsEverything(GameTestHelper helper) {
        Player player = strappedBoarder(helper);
        GearAccess.current().set(player, GearAccess.Slot.GOGGLES, new ItemStack(ModItems.GOGGLES.get()));
        GearAccess.current().set(player, GearAccess.Slot.MITTS, new ItemStack(ModItems.MITTS.get()));
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.SKI_HELMET.get()));
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.SKI_POLES.get()));
        GearProfile profile = GearProfile.build(player);
        check(profile.canRide() && profile.helmet() && profile.goggles() && profile.mitts() && profile.poles(),
                "incomplete profile: " + profile);
        check(profile.stats().equals(GearStats.DEFAULTS.get("snowboard")), "wrong stats: " + profile.stats());
        helper.succeed();
    }

    // ---- helpers --------------------------------------------------------------------------------

    private static Player strappedBoarder(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        GearAccess.current().set(player, GearAccess.Slot.BOARD, new ItemStack(ModItems.SNOWBOARD.get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.SNOWBOARD_BOOTS.get()));
        expect(StrapCheck.Result.OK, StrapController.tryStrap(player));
        return player;
    }

    private static void expect(StrapCheck.Result expected, StrapCheck.Result actual) {
        check(expected == actual, "expected " + expected + " but strap check gave " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
