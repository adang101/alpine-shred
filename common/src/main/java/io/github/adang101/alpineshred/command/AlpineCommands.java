package io.github.adang101.alpineshred.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.platform.Platform;
import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.config.AlpineConfig;
import io.github.adang101.alpineshred.data.AlpineDataLoaders;
import io.github.adang101.alpineshred.movement.RideServer;
import io.github.adang101.alpineshred.movement.Riders;
import io.github.adang101.alpineshred.movement.StrapCheck;
import io.github.adang101.alpineshred.movement.StrapController;
import io.github.adang101.alpineshred.network.ModNetwork;
import io.github.adang101.alpineshred.registry.ModStats;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * {@code /alpineshred} (design doc section 18, "Commands"): {@code strap}, {@code config reload},
 * {@code tuning reload} (both resync clients), {@code debug ride on|off} and {@code stats [player]}.
 * {@code lift}, the other {@code debug} targets and {@code design} arrive with their systems.
 */
public final class AlpineCommands {
    private AlpineCommands() {
    }

    public static void register() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(AlpineShred.MOD_ID)
                .then(Commands.literal("strap")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> strap(ctx, Mode.TOGGLE))
                                .then(Commands.literal("on").executes(ctx -> strap(ctx, Mode.ON)))
                                .then(Commands.literal("off").executes(ctx -> strap(ctx, Mode.OFF)))))
                .then(Commands.literal("stats")
                        .executes(ctx -> stats(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> stats(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("design")
                        .then(Commands.literal("info").executes(AlpineCommands::designInfo))
                        .then(Commands.literal("clear")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("players", EntityArgument.players())
                                        .executes(ctx -> designClear(ctx, EntityArgument.getPlayers(ctx, "players"))))))
                .then(Commands.literal("config")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("reload").executes(AlpineCommands::reloadConfig)))
                .then(Commands.literal("tuning")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("reload").executes(AlpineCommands::reloadTuning)))
                .then(Commands.literal("debug")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("ride")
                                .then(Commands.literal("on").executes(ctx -> debugRide(ctx, true)))
                                .then(Commands.literal("off").executes(ctx -> debugRide(ctx, false))))));
    }

    /** {@code /alpineshred design info}: the held item's design name, author and size (section 22). */
    private static int designInfo(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        net.minecraft.world.item.ItemStack stack = player.getMainHandItem();
        net.minecraft.nbt.CompoundTag tag = io.github.adang101.alpineshred.design.DesignNbt.tag(stack);
        io.github.adang101.alpineshred.design.Design design = io.github.adang101.alpineshred.design.DesignNbt.read(stack);
        if (tag == null) {
            ctx.getSource().sendFailure(Component.translatable("commands.alpineshred.design.none"));
            return 0;
        }
        int bytes = tag.getByteArray("top").length + tag.getByteArray("base").length;
        if (design == null) {
            ctx.getSource().sendSuccess(() -> Component.translatable("commands.alpineshred.design.unreadable", tag.getInt("v"), bytes), false);
            return 1;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.alpineshred.design.info", design.name(),
                design.author().isEmpty() ? "?" : design.author(), design.kind().id, bytes,
                String.format(java.util.Locale.ROOT, "%08x", design.hash())), false);
        return 1;
    }

    /** {@code /alpineshred design clear <players>}: strips the design from each player's main-hand item. */
    private static int designClear(CommandContext<CommandSourceStack> ctx, java.util.Collection<ServerPlayer> players) {
        int cleared = 0;
        for (ServerPlayer player : players) {
            net.minecraft.world.item.ItemStack stack = player.getMainHandItem();
            if (io.github.adang101.alpineshred.design.DesignNbt.has(stack)) {
                io.github.adang101.alpineshred.design.DesignNbt.clear(stack);
                cleared++;
            }
        }
        int n = cleared;
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.alpineshred.design.cleared", n), true);
        return n;
    }

    /** {@code /alpineshred stats [player]}: best combo, total score, and the totals (section 18). */
    private static int stats(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        int best = ModStats.value(player, ModStats.BEST_COMBO);
        int total = ModStats.value(player, ModStats.TRICK_SCORE);
        int landed = ModStats.value(player, ModStats.TRICKS_LANDED);
        String air = String.format(java.util.Locale.ROOT, "%.1f", ModStats.value(player, ModStats.AIR_TIME) / 20.0);
        String grind = String.format(java.util.Locale.ROOT, "%.0f", ModStats.value(player, ModStats.GRIND_DISTANCE) / 100.0);
        String ridden = String.format(java.util.Locale.ROOT, "%.0f", ModStats.value(player, ModStats.RIDE_DISTANCE) / 100.0);
        int lifts = ModStats.value(player, ModStats.LIFT_RIDES);
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.alpineshred.stats",
                player.getDisplayName(), best, total, landed, air, grind, ridden, lifts), false);
        return 1;
    }

    /** Client overlay of state, speed, grade and surface, plus this server's validator read-out. */
    private static int debugRide(CommandContext<CommandSourceStack> ctx, boolean on) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        RideServer.setDebug(player, on);
        ModNetwork.sendDebugToggle(player, on);
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.alpineshred.debug.ride." + (on ? "on" : "off")), false);
        return 1;
    }

    private enum Mode {ON, OFF, TOGGLE}

    private static int strap(CommandContext<CommandSourceStack> ctx, Mode mode) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        int changed = 0;
        List<String> failures = new ArrayList<>();
        for (ServerPlayer player : targets) {
            boolean strapped = Riders.isStrapped(player);
            boolean want = switch (mode) {
                case ON -> true;
                case OFF -> false;
                case TOGGLE -> !strapped;
            };
            if (want == strapped) continue;
            if (want) {
                StrapCheck.Result result = StrapController.tryStrap(player);
                if (result.ok()) changed++;
                else failures.add(player.getGameProfile().getName() + " (" + result.name().toLowerCase(java.util.Locale.ROOT) + ")");
            } else {
                StrapController.unstrap(player, StrapController.UnstrapReason.COMMAND);
                changed++;
            }
        }
        int done = changed;
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.alpineshred.strap.done", done), true);
        if (!failures.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("commands.alpineshred.strap.failed", String.join(", ", failures)));
        }
        return changed;
    }

    private static int reloadConfig(CommandContext<CommandSourceStack> ctx) {
        List<String> warnings = new ArrayList<>();
        AlpineConfig.loadCommon(Platform.getConfigFolder(), warning -> {
            warnings.add(warning);
            AlpineShred.LOGGER.warn(warning);
        });
        ModNetwork.resyncAll(ctx.getSource().getServer(), true, false);
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.alpineshred.config.reloaded", warnings.size()), true);
        return 1;
    }

    private static int reloadTuning(CommandContext<CommandSourceStack> ctx) {
        AlpineDataLoaders.reloadNow(ctx.getSource().getServer().getResourceManager());
        ModNetwork.resyncAll(ctx.getSource().getServer(), false, true);
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.alpineshred.tuning.reloaded"), true);
        return 1;
    }
}
