package io.github.adang101.alpineshred.gametest;

import dev.architectury.event.EventResult;
import io.github.adang101.alpineshred.design.Design;
import io.github.adang101.alpineshred.design.DesignCodec;
import io.github.adang101.alpineshred.design.DesignEvents;
import io.github.adang101.alpineshred.design.DesignKind;
import io.github.adang101.alpineshred.design.DesignNbt;
import io.github.adang101.alpineshred.design.DesignServer;
import io.github.adang101.alpineshred.design.DesignStatus;
import io.github.adang101.alpineshred.design.PaintBenchMenu;
import io.github.adang101.alpineshred.registry.ModBlocks;
import io.github.adang101.alpineshred.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;

import static io.github.adang101.alpineshred.gametest.RideGameTests.check;

/**
 * Phase 6 game tests (design doc section 22, "Tests"): saves through the Paint Bench's menu, with
 * and without the dye; rejected saves charge nothing; the design survives smithing and a save and
 * load of the block holding it.
 */
public final class DesignGameTests {
    private DesignGameTests() {
    }

    /** A design with a few colors, as the editor would make. */
    static Design sample(DesignKind kind) {
        int[] top = new int[kind.pixels()], base = new int[kind.pixels()];
        for (int i = 0; i < top.length; i++) {
            int x = i % kind.width;
            top[i] = x < kind.width / 2 ? 0xFFE03030 : 0xFF3050E0;
            base[i] = (x / 8) % 2 == 0 ? 0xFFF0F0F0 : 0;
        }
        return new Design(kind, top, base, 0x202020, Design.Pair.SAME, "Test Paint", "", null);
    }

    private static DesignServer.SaveRequest request(PaintBenchMenu menu, Design d) {
        return new DesignServer.SaveRequest(menu.containerId, false, d.kind(), DesignCodec.topZ(d), DesignCodec.baseZ(d),
                d.edge(), d.pair(), d.name());
    }

    /** A survival player at an open Paint Bench with {@code gear} and {@code dye} in its slots. */
    private static ServerPlayer atBench(GameTestHelper helper, ItemStack gear, ItemStack dye) {
        new Course(helper).flat(0, 6, 2, Blocks.STONE);
        BlockPos rel = new BlockPos(4, 2, 4);
        helper.setBlock(rel, ModBlocks.PAINT_BENCH.get());
        BlockPos pos = helper.absolutePos(rel);
        ServerPlayer player = RideGameTests.serverRider(helper, new Course(helper).at(Course.CENTER_X, 2, 2.5));
        player.setGameMode(GameType.SURVIVAL);
        BlockState state = helper.getLevel().getBlockState(pos);
        player.openMenu(state.getMenuProvider(helper.getLevel(), pos));
        check(player.containerMenu instanceof PaintBenchMenu, "the bench did not open: " + player.containerMenu);
        PaintBenchMenu menu = (PaintBenchMenu) player.containerMenu;
        menu.getSlot(PaintBenchMenu.GEAR_SLOT).set(gear);
        menu.getSlot(PaintBenchMenu.DYE_SLOT).set(dye);
        return player;
    }

    private static PaintBenchMenu menu(ServerPlayer player) {
        return (PaintBenchMenu) player.containerMenu;
    }

    /** A save writes the design with the server's author and takes one dye; in creative it is free. */
    public static void aSaveWritesTheDesignAndTakesTheDye(GameTestHelper helper) {
        ServerPlayer player = null;
        try {
            player = atBench(helper, new ItemStack(ModItems.SNOWBOARD.get()), new ItemStack(Items.RED_DYE, 3));
            PaintBenchMenu menu = menu(player);
            Design d = sample(DesignKind.BOARD);
            check(DesignServer.handle(player, request(menu, d)) == DesignStatus.OK, "save refused");
            Design saved = DesignNbt.read(menu.gear());
            check(saved != null && saved.sameArt(d), "the gear does not carry the design: " + saved);
            check(saved.author().equals(player.getGameProfile().getName()) && player.getUUID().equals(saved.authorId()),
                    "the server did not write the author: " + saved);
            check(menu.dye().getCount() == 2, "dye left " + menu.dye().getCount());
            check(menu.gear().getTag() != null && menu.gear().getTag().getCompound(DesignNbt.KEY).getInt("hash") == d.hash(), "hash");

            // Creative: free, even with no dye at all.
            ServerPlayer creative = atBench(helper, new ItemStack(ModItems.SKIS.get()), ItemStack.EMPTY);
            try {
                creative.setGameMode(GameType.CREATIVE);
                check(DesignServer.handle(creative, request(menu(creative), sample(DesignKind.SKI))) == DesignStatus.OK, "creative save refused");
                check(DesignNbt.has(menu(creative).gear()), "creative save not written");
            } finally {
                RideGameTests.removeServerPlayer(creative);
            }
            helper.succeed();
        } finally {
            RideGameTests.removeServerPlayer(player);
        }
    }

    /** No dye, bad data, the wrong kind, too soon, or a refusing listener: nothing is written and no dye is taken. */
    public static void rejectedSavesChargeNothing(GameTestHelper helper) {
        ServerPlayer player = null;
        DesignEvents.BeforeSave refuse = (p, gear, design) -> design.name().equals("refuse me") ? EventResult.interruptFalse() : EventResult.pass();
        DesignEvents.BEFORE_SAVE.register(refuse);
        try {
            player = atBench(helper, new ItemStack(ModItems.SNOWBOARD.get()), ItemStack.EMPTY);
            PaintBenchMenu menu = menu(player);
            Design d = sample(DesignKind.BOARD);
            check(DesignServer.handle(player, request(menu, d)) == DesignStatus.NO_COST, "saved with no dye");
            check(!DesignNbt.has(menu.gear()), "written with no dye");

            menu.getSlot(PaintBenchMenu.DYE_SLOT).set(new ItemStack(Items.BLUE_DYE, 4));
            // Partial alpha (rule 3).
            byte[] raw = DesignCodec.raw(d.top());
            raw[3] = (byte) 100;
            DesignServer.SaveRequest bad = new DesignServer.SaveRequest(menu.containerId, false, DesignKind.BOARD,
                    DesignCodec.deflate(raw), DesignCodec.baseZ(d), d.edge(), d.pair(), d.name());
            check(DesignServer.handle(player, bad) == DesignStatus.INVALID, "partial alpha accepted");
            // A ski design on a board (rule 1).
            check(DesignServer.handle(player, request(menu, sample(DesignKind.SKI))) == DesignStatus.INVALID, "ski design on a board");
            // The wrong container id.
            Design ok = sample(DesignKind.BOARD);
            DesignServer.SaveRequest stale = new DesignServer.SaveRequest(menu.containerId + 1, false, DesignKind.BOARD,
                    DesignCodec.topZ(ok), DesignCodec.baseZ(ok), ok.edge(), ok.pair(), ok.name());
            check(DesignServer.handle(player, stale) == DesignStatus.INVALID, "saved through a closed bench");
            // A listener refusing (DesignEvents.BEFORE_SAVE).
            check(DesignServer.handle(player, request(menu, ok.withName("refuse me"))) == DesignStatus.FORBIDDEN, "listener ignored");
            check(menu.dye().getCount() == 4 && !DesignNbt.has(menu.gear()), "a rejected save charged or wrote: " + menu.dye().getCount());
            // One save, then a second at once: rate limited (rule 6), charged once.
            check(DesignServer.handle(player, request(menu, ok)) == DesignStatus.OK, "the good save was refused");
            check(DesignServer.handle(player, request(menu, ok.withName("again"))) == DesignStatus.RATE_LIMITED, "no cooldown");
            check(menu.dye().getCount() == 3, "dye left " + menu.dye().getCount());
            check(DesignNbt.read(menu.gear()).name().equals("Test Paint"), "the rate limited save was written");
            helper.succeed();
        } finally {
            DesignEvents.BEFORE_SAVE.unregister(refuse);
            RideGameTests.removeServerPlayer(player);
        }
    }

    /** Smithing a park board into a pro board carries the design (the transform copies the tag). */
    public static void smithingCarriesTheDesign(GameTestHelper helper) {
        ItemStack park = new ItemStack(ModItems.PARK_SNOWBOARD.get());
        Design d = sample(DesignKind.BOARD).withAuthor("Alex", java.util.UUID.randomUUID());
        DesignNbt.write(park, d);
        SimpleContainer table = new SimpleContainer(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), park,
                new ItemStack(Items.NETHERITE_INGOT));
        var level = helper.getLevel();
        var recipe = level.getRecipeManager().getRecipeFor(RecipeType.SMITHING, table, level);
        check(recipe.isPresent(), "no smithing recipe for a park board");
        ItemStack pro = recipe.get().assemble(table, level.registryAccess());
        check(pro.is(ModItems.PRO_SNOWBOARD.get()), "made " + pro);
        Design carried = DesignNbt.read(pro);
        check(carried != null && carried.sameArt(d) && carried.author().equals("Alex"), "the design was lost: " + carried);
        helper.succeed();
    }

    /** A designed board in a chest survives the chest being saved and loaded (as a chunk save does). */
    public static void aDesignSurvivesSaveAndLoad(GameTestHelper helper) {
        BlockPos rel = new BlockPos(2, 2, 2);
        helper.setBlock(rel, Blocks.CHEST);
        BlockPos pos = helper.absolutePos(rel);
        BlockEntity be = helper.getLevel().getBlockEntity(pos);
        check(be instanceof ChestBlockEntity, "no chest");
        ItemStack skis = new ItemStack(ModItems.POWDER_SKIS.get());
        Design d = new Design(DesignKind.SKI, sample(DesignKind.SKI).top(), sample(DesignKind.SKI).base(), 0xABCDEF,
                Design.Pair.MIRROR, "Mirror pair", "Steve", null);
        DesignNbt.write(skis, d);
        ((ChestBlockEntity) be).setItem(0, skis);
        CompoundTag saved = be.saveWithFullMetadata();
        BlockEntity loaded = BlockEntity.loadStatic(pos, helper.getLevel().getBlockState(pos), saved);
        check(loaded instanceof ChestBlockEntity, "did not load");
        ItemStack back = ((ChestBlockEntity) loaded).getItem(0);
        Design again = DesignNbt.read(back);
        check(again != null && again.sameArt(d) && again.pair() == Design.Pair.MIRROR && again.author().equals("Steve"),
                "the design did not survive: " + again);
        check(Arrays.equals(again.top(), d.top()), "pixels changed");
        helper.succeed();
    }
}
