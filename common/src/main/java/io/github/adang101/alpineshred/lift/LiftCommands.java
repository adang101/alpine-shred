package io.github.adang101.alpineshred.lift;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import io.github.adang101.alpineshred.config.AlpineConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * {@code /alpineshred lift list | info <id> | stop <id> | start <id> | remove <id>} (permission level 2).
 * {@code <id>} is a full UUID or a unique prefix of at least {@link #MIN_PREFIX} characters; the commands
 * act on the lines of the level the source is in.
 */
public final class LiftCommands {
    public static final int MIN_PREFIX = 4;

    private static final DynamicCommandExceptionType NO_LINE = new DynamicCommandExceptionType(
            id -> Component.translatable("commands.alpineshred.lift.no_line", id));
    private static final DynamicCommandExceptionType AMBIGUOUS = new DynamicCommandExceptionType(
            id -> Component.translatable("commands.alpineshred.lift.ambiguous", id));
    private static final DynamicCommandExceptionType SHORT = new DynamicCommandExceptionType(
            id -> Component.translatable("commands.alpineshred.lift.too_short", id, MIN_PREFIX));

    private LiftCommands() {
    }

    /** The {@code lift} subtree, to be added under {@code /alpineshred}. */
    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("lift")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list").executes(LiftCommands::list))
                .then(idCommand("info", LiftCommands::info))
                .then(idCommand("stop", LiftCommands::stop))
                .then(idCommand("start", LiftCommands::start))
                .then(idCommand("remove", LiftCommands::remove));
    }

    private interface Action {
        int run(CommandSourceStack source, ServerLevel level, LiftLine line) throws CommandSyntaxException;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> idCommand(String name, Action action) {
        return Commands.literal(name).then(Commands.argument("id", StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                        LiftManager.lines(ctx.getSource().getLevel()).stream().map(l -> shortId(l.id)), builder))
                .executes(ctx -> {
                    ServerLevel level = ctx.getSource().getLevel();
                    LiftLine line = resolve(level, StringArgumentType.getString(ctx, "id"));
                    return action.run(ctx.getSource(), level, line);
                }));
    }

    /** The first 8 characters of the id. */
    public static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    /** The line whose id equals or starts with {@code text}; errors if none, several, or the prefix is too short. */
    public static LiftLine resolve(ServerLevel level, String text) throws CommandSyntaxException {
        String wanted = text.toLowerCase(Locale.ROOT);
        LiftLine found = null;
        for (LiftLine line : LiftManager.lines(level)) {
            String id = line.id.toString();
            if (id.equals(wanted)) return line;
            if (wanted.length() >= MIN_PREFIX && id.startsWith(wanted)) {
                if (found != null) throw AMBIGUOUS.create(text);
                found = line;
            }
        }
        if (found != null) return found;
        if (wanted.length() < MIN_PREFIX) throw SHORT.create(text);
        throw NO_LINE.create(text);
    }

    private static String flags(LiftLine line) {
        List<String> flags = new ArrayList<>();
        if (line.powered) flags.add("powered");
        if (line.emergency) flags.add("emergency");
        if (line.faulted) flags.add("faulted");
        return flags.isEmpty() ? "-" : String.join(",", flags);
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        List<LiftLine> lines = LiftManager.snapshot(level);
        if (lines.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("commands.alpineshred.lift.list.none"), false);
            return 0;
        }
        for (LiftLine line : lines) {
            ctx.getSource().sendSuccess(() -> Component.translatable("commands.alpineshred.lift.list.entry", shortId(line.id),
                    line.nodePositions().size(), String.format("%.0f", line.path().loopLength()),
                    String.format("%.2f", line.speed), flags(line)), false);
        }
        return lines.size();
    }

    private static int info(CommandSourceStack source, ServerLevel level, LiftLine line) {
        StringBuilder nodes = new StringBuilder();
        for (BlockPos p : line.nodePositions()) {
            if (nodes.length() > 0) nodes.append(" > ");
            nodes.append(p.getX()).append(' ').append(p.getY()).append(' ').append(p.getZ());
        }
        int riders = riders(level, line);
        source.sendSuccess(() -> Component.translatable("commands.alpineshred.lift.info", line.id.toString(), line.nodePositions().size(),
                nodes.toString(), String.format("%.1f", line.path().loopLength()), line.chairCount(), String.format("%.2f", line.speed),
                String.format("%.2f", line.targetSpeed), flags(line), riders), false);
        return 1;
    }

    /** Passengers on the line's loaded chairs. */
    private static int riders(ServerLevel level, LiftLine line) {
        int riders = 0;
        for (int i = 0; i < line.chairCount(); i++) {
            ChairEntity chair = LiftChairs.find(level, line.id, i);
            if (chair != null) riders += chair.getPassengers().size();
        }
        return riders;
    }

    private static int stop(CommandSourceStack source, ServerLevel level, LiftLine line) {
        LiftManager.emergencyStop(level, line.id);
        source.sendSuccess(() -> Component.translatable("commands.alpineshred.lift.stop", shortId(line.id)), true);
        return 1;
    }

    /**
     * Clears the emergency and powers the line, at the drive's target if the drive is loaded (through
     * {@link LiftDriveSource}), else at the default speed level.
     */
    private static int start(CommandSourceStack source, ServerLevel level, LiftLine line) {
        double target;
        if (level.hasChunkAt(line.drive) && level.getBlockEntity(line.drive) instanceof LiftDriveSource drive) {
            target = drive.targetSpeed();
        } else {
            List<Double> levels = AlpineConfig.LIFT_SPEED_LEVELS.get();
            target = levels.get(Math.min(LiftDriveStationBlockEntity.DEFAULT_SPEED_LEVEL, levels.size() - 1));
        }
        LiftManager.clearEmergency(level, line.id);
        LiftManager.setDrive(level, line.id, true, target);
        source.sendSuccess(() -> Component.translatable("commands.alpineshred.lift.start", shortId(line.id), String.format("%.1f", target)), true);
        return 1;
    }

    /** Ejects riders onto the ground, discards the chairs, clears loaded nodes, then deletes the line. */
    private static int remove(CommandSourceStack source, ServerLevel level, LiftLine line) {
        for (int i = 0; i < line.chairCount(); i++) {
            ChairEntity chair = LiftChairs.find(level, line.id, i);
            if (chair == null) continue;
            List<Entity> riders = new ArrayList<>(chair.getPassengers());
            BlockPos from = chair.blockPosition();
            chair.ejectPassengers();
            for (Entity rider : riders) LiftChairs.putOnGround(level, rider, from, line.drive);
            chair.discard();
        }
        for (BlockPos p : line.nodePositions()) {
            if (level.hasChunkAt(p) && level.getBlockEntity(p) instanceof LiftNodeBlockEntity node) node.clearLine();
        }
        LiftManager.remove(level, line.id);
        source.sendSuccess(() -> Component.translatable("commands.alpineshred.lift.remove", shortId(line.id)), true);
        return 1;
    }
}
