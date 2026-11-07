package io.github.adang101.alpineshred.gametest;

import io.github.adang101.alpineshred.compat.create.CreateBridge;
import io.github.adang101.alpineshred.compat.create.CreateCompat;
import io.github.adang101.alpineshred.compat.create.WrenchActions;
import io.github.adang101.alpineshred.lift.LiftLine;
import io.github.adang101.alpineshred.lift.LiftManager;
import io.github.adang101.alpineshred.lift.LiftNodeBlockEntity;
import io.github.adang101.alpineshred.park.RailPlacement;
import io.github.adang101.alpineshred.park.block.BoxBlock;
import io.github.adang101.alpineshred.park.block.LevelRails;
import io.github.adang101.alpineshred.park.block.ParkStates;
import io.github.adang101.alpineshred.park.block.RailBlock;
import io.github.adang101.alpineshred.platform.PlatformHooks;
import io.github.adang101.alpineshred.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Create compatibility tests (phase 9 plan, decision 4). The CI "with Create" column runs the game tests with
 * Create on the runtime classpath and {@value #COLUMN_PROPERTY} set; the ordinary column leaves both out. Each
 * test asserts what its column declares, so a column cannot silently run without (or with) Create: kinetic
 * tests of later work packages start with {@link #inCreateColumn()} and return early outside it.
 * Nothing here names a Create type.
 */
public final class CreateGameTests {
    /** Set by the loader modules' {@code -PwithCreate} run configurations. */
    public static final String COLUMN_PROPERTY = "alpineshred.create";

    private CreateGameTests() {
    }

    public static boolean inCreateColumn() {
        return Boolean.getBoolean(COLUMN_PROPERTY);
    }

    /** With Create: the loader found it and the bridge is installed and live. Without: no bridge, so no Create class was loaded. */
    public static void createBridgeMatchesColumn(GameTestHelper helper) {
        boolean expected = inCreateColumn();
        check(PlatformHooks.isCreatePresent() == expected, "Create present = " + PlatformHooks.isCreatePresent() + " but the column expects " + expected);
        CreateBridge bridge = CreateCompat.bridge();
        if (expected) {
            check(bridge != CreateCompat.ABSENT, "Create is present but no bridge was installed");
            check(bridge.live(), "the Create bridge is not live");
            check(!bridge.version().isEmpty(), "the Create bridge reports no version");
        } else {
            check(bridge == CreateCompat.ABSENT, "a bridge is installed without Create");
            check(!bridge.live(), "the absent bridge claims to be live");
        }
        helper.succeed();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    // ---- wrench support (phase 9, WP4; README 80) ----

    /** A survival player holding Create's wrench, found by id so no Create class is named. */
    private static Player wrenchHolder(GameTestHelper helper, boolean sneak) {
        Item wrench = BuiltInRegistries.ITEM.get(new ResourceLocation("create", "wrench"));
        check(wrench != Items.AIR, "Create's wrench is not registered");
        Player player = helper.makeMockSurvivalPlayer();
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(wrench));
        player.setShiftKeyDown(sneak);
        return player;
    }

    private static InteractionResult click(GameTestHelper helper, Player player, BlockPos abs) {
        return WrenchActions.onClick(helper.getLevel(), abs, player, player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND));
    }

    /** The wrench check matches Create's wrench and nothing of ours; an empty hand is left alone. */
    public static void wrenchIsMatchedById(GameTestHelper helper) {
        if (!inCreateColumn()) {
            helper.succeed();
            return;
        }
        Player player = wrenchHolder(helper, false);
        check(WrenchActions.isWrench(player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND)), "create:wrench is not recognised as the wrench");
        check(!WrenchActions.isWrench(new ItemStack(Items.STICK)), "a stick counts as a wrench");
        BlockPos abs = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.getLevel().setBlock(abs, ModBlocks.LIFT_TOWER_POLE.get().defaultBlockState(), 3);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        check(click(helper, player, abs) == InteractionResult.PASS, "an empty hand was handled");
        check(helper.getLevel().getBlockState(abs).is(ModBlocks.LIFT_TOWER_POLE.get()), "an empty hand changed the pole");
        helper.getLevel().setBlock(abs, Blocks.AIR.defaultBlockState(), 3);
        helper.succeed();
    }

    /** Park pieces: a click runs the park wrench's code; sneak-click picks the piece up. */
    public static void wrenchOnParkPieces(GameTestHelper helper) {
        if (!inCreateColumn()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos rail = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos box = helper.absolutePos(new BlockPos(2, 2, 5));
        level.setBlock(rail.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(box.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(rail, ModBlocks.PIPE_RAIL.get().defaultBlockState(), 3);
        level.setBlock(box, ModBlocks.WOOD_BOX.get().defaultBlockState(), 3);

        RailPlacement.Piece before = RailBlock.piece(level.getBlockState(rail));
        List<RailPlacement.Piece> options = RailPlacement.alternatives(new LevelRails(level), LevelRails.cell(rail), before);
        check(options.size() > 1, "a lone rail should have alternatives: " + options);
        Player player = wrenchHolder(helper, false);
        check(click(helper, player, rail).consumesAction(), "the click was not handled on a rail");
        check(!RailBlock.piece(level.getBlockState(rail)).equals(before), "Create's wrench did not cycle the rail");
        ParkStates.BoxHalf half = level.getBlockState(box).getValue(ParkStates.BOX_HALF);
        check(click(helper, player, box).consumesAction(), "the click was not handled on a box");
        check(level.getBlockState(box).getValue(ParkStates.BOX_HALF) != half, "Create's wrench did not switch the box");

        Player sneaker = wrenchHolder(helper, true);
        check(click(helper, sneaker, rail).consumesAction(), "the sneak-click was not handled on a rail");
        check(level.getBlockState(rail).isAir(), "sneak-click did not pick up the rail");
        check(sneaker.getInventory().countItem(ModBlocks.PIPE_RAIL.get().asItem()) == 1, "the rail did not reach the inventory");
        check(click(helper, sneaker, box).consumesAction(), "the sneak-click was not handled on a box");
        check(level.getBlockState(box).isAir(), "sneak-click did not pick up the box");
        check(sneaker.getInventory().countItem(ModBlocks.WOOD_BOX.get().asItem()) == 1, "the box did not reach the inventory");

        BlockPos other = helper.absolutePos(new BlockPos(2, 2, 7));
        level.setBlock(other, Blocks.STONE.defaultBlockState(), 3);
        check(click(helper, player, other) == InteractionResult.PASS, "a stone block was handled");
        level.setBlock(other, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(rail.below(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(box.below(), Blocks.AIR.defaultBlockState(), 3);
        helper.succeed();
    }

    /** Lift blocks: a plain click changes nothing; sneak-click picks up a pole, a head or a station. */
    public static void wrenchOnLiftBlocks(GameTestHelper helper) {
        if (!inCreateColumn()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        List<net.minecraft.world.level.block.Block> blocks = List.of(ModBlocks.LIFT_TOWER_POLE.get(), ModBlocks.LIFT_TOWER_HEAD.get(),
                ModBlocks.LIFT_DRIVE_STATION.get(), ModBlocks.LIFT_RETURN_STATION.get(),
                ModBlocks.LIFT_LOADING_GATE.get(), ModBlocks.LIFT_STOP_BUTTON.get());
        Player plain = wrenchHolder(helper, false);
        Player sneaker = wrenchHolder(helper, true);
        for (int i = 0; i < blocks.size(); i++) {
            net.minecraft.world.level.block.Block block = blocks.get(i);
            BlockPos abs = helper.absolutePos(new BlockPos(1 + i, 2, 1));
            String name = BuiltInRegistries.BLOCK.getKey(block).getPath();
            level.setBlock(abs, block.defaultBlockState(), 3);
            BlockState state = level.getBlockState(abs);
            check(click(helper, plain, abs).consumesAction(), "the plain click was not handled on " + name);
            check(level.getBlockState(abs) == state, "a plain click changed " + name);
            check(click(helper, sneaker, abs).consumesAction(), "the sneak-click was not handled on " + name);
            check(level.getBlockState(abs).isAir(), "sneak-click did not pick up " + name);
            check(sneaker.getInventory().countItem(block.asItem()) == 1, name + " did not reach the inventory");
        }
        helper.succeed();
    }

    /**
     * Picking a node up with the wrench faults the line with the very reason breaking it by hand gives: the same
     * line is built twice, once broken by hand and once picked up, for the head and the drive station.
     */
    public static void wrenchPickupFaultsLikeBreaking(GameTestHelper helper) {
        if (!inCreateColumn()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(1, 2, 1));
        List<BlockPos> nodes = List.of(base, base.offset(0, 1, 6), base.offset(0, 0, 12));
        Player sneaker = wrenchHolder(helper, true);
        for (BlockPos broken : nodes.subList(0, 2)) {
            String byHand = faultAfter(helper, nodes, broken, null);
            String byWrench = faultAfter(helper, nodes, broken, sneaker);
            check(byHand != null && byHand.contains("broken at"), "breaking by hand gave: " + byHand);
            check(byHand.equals(byWrench), "by hand: " + byHand + ", by wrench: " + byWrench);
        }
        helper.succeed();
    }

    /** Builds a linked line, removes {@code broken} by hand ({@code picker} null) or with the wrench, and returns the fault reason. */
    private static String faultAfter(GameTestHelper helper, List<BlockPos> nodes, BlockPos broken, Player picker) {
        ServerLevel level = helper.getLevel();
        level.setBlock(nodes.get(0), ModBlocks.LIFT_DRIVE_STATION.get().defaultBlockState(), 3);
        level.setBlock(nodes.get(1), ModBlocks.LIFT_TOWER_HEAD.get().defaultBlockState(), 3);
        level.setBlock(nodes.get(2), ModBlocks.LIFT_RETURN_STATION.get().defaultBlockState(), 3);
        LiftLine line = LiftManager.create(level, nodes);
        check(line != null, "the line was not created");
        try {
            for (BlockPos p : nodes) ((LiftNodeBlockEntity) level.getBlockEntity(p)).setLineId(line.id);
            check(!line.faulted, "faulted before anything broke");
            if (picker == null) {
                check(level.destroyBlock(broken, false), "the block did not break");
            } else {
                check(click(helper, picker, broken).consumesAction(), "the sneak-click was not handled");
            }
            check(level.getBlockState(broken).isAir(), "the block is still there");
            check(line.faulted, "removing the block did not fault the line");
            return line.faultReason;
        } finally {
            LiftManager.remove(level, line.id);
            for (BlockPos p : nodes) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        }
    }
}
