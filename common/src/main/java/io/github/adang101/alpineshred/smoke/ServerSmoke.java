package io.github.adang101.alpineshred.smoke;

import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.movement.RideServer;
import io.github.adang101.alpineshred.movement.RideState;
import io.github.adang101.alpineshred.movement.Riders;
import io.github.adang101.alpineshred.movement.StrapCheck;
import io.github.adang101.alpineshred.movement.StrapController;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The server half of the multiplayer smoke test (phase 2's exit gate: three clients see correct
 * state), run in CI with {@code -Dalpineshred.smoke.server=true} on a dedicated server
 * ({@code ./gradlew :forge:runSmokeServer}). Inert otherwise.
 *
 * <p>It builds the smoke run beside spawn. The player named {@code rider} is dressed for the
 * board, put at the top and strapped in; {@code watcher1} and {@code watcher2} hover beside the
 * slope and the kicker. The clients do the rest ({@code ClientSmoke}, scenario {@code multi}).
 * Once everyone has left, it writes {@code alpineshred-server-smoke.txt} (the states the server
 * accepted, the validator's verdict) and stops the server.
 */
public final class ServerSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("alpineshred.smoke.server");
    private static final int TIMEOUT_TICKS = 20 * 60 * 8;

    private static BlockPos base;
    private static int ticks;
    private static int joined;
    private static boolean riderSeen;
    private static int violations;
    private static String lastReason = "";
    private static RideState lastState = RideState.OFF;
    private static final Set<RideState> states = EnumSet.noneOf(RideState.class);
    private static final List<String> lines = Collections.synchronizedList(new ArrayList<>());
    private static boolean done;

    private ServerSmoke() {
    }

    public static void register() {
        if (!ENABLED) return;
        AlpineShred.LOGGER.info("[smoke] multiplayer server smoke test armed");
        LifecycleEvent.SERVER_STARTED.register(ServerSmoke::started);
        PlayerEvent.PLAYER_JOIN.register(ServerSmoke::joined);
        TickEvent.SERVER_POST.register(ServerSmoke::tick);
    }

    private static void started(MinecraftServer server) {
        ServerLevel level = server.overworld();
        level.setDayTime(6000);
        level.setWeatherParameters(20 * 60 * 30, 0, false, false);
        BlockPos spawn = level.getSharedSpawnPos();
        int x = spawn.getX() + 6, z = spawn.getZ() - 8;
        base = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
        SmokeCourse.build(level, base);
        note("built the run at " + base);
    }

    private static void joined(ServerPlayer player) {
        if (base == null) return;
        String name = player.getName().getString();
        joined++;
        note("joined: " + name);
        switch (name) {
            case "rider" -> {
                riderSeen = true;
                SmokeCourse.dressForBoard(player);
                SmokeCourse.toTop(player, base);
                StrapCheck.Result result = StrapController.tryStrap(player);
                note("rider strapped: " + result);
            }
            // Hovering beside the slope and beside the kicker, both well inside tracking range.
            case "watcher1" -> hover(player, base.getX() + 9.5, base.getY() + SmokeCourse.TOP, base.getZ() + 22.5, 90f);
            case "watcher2" -> hover(player, base.getX() - 8.5, base.getY() + 5, base.getZ() + SmokeCourse.KICKER + 4.5, -90f);
            default -> note("unexpected player " + name);
        }
    }

    private static void hover(ServerPlayer player, double x, double y, double z, float yaw) {
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.teleportTo(player.serverLevel(), x, y, z, yaw, 20f);
    }

    private static void tick(MinecraftServer server) {
        if (done || base == null) return;
        ticks++;
        ServerPlayer rider = server.getPlayerList().getPlayerByName("rider");
        if (rider != null) {
            RideState state = Riders.state(rider);
            states.add(state);
            if (state != lastState) {
                note(String.format(Locale.ROOT, "t%d gt%d %s at z %.1f", ticks, server.overworld().getGameTime(), state, rider.getZ() - base.getZ()));
                lastState = state;
            }
            int now = RideServer.violations(rider);
            if (now > violations) {
                note(String.format(Locale.ROOT, "t%d gt%d violation %d at z %.1f y %.2f: %s", ticks, server.overworld().getGameTime(), now,
                        rider.getZ() - base.getZ(), rider.getY() - base.getY(), RideServer.lastReason(rider)));
            }
            violations = now;
            lastReason = RideServer.lastReason(rider);
        }
        boolean everyoneLeft = riderSeen && server.getPlayerList().getPlayerCount() == 0;
        if (!everyoneLeft && ticks < TIMEOUT_TICKS) return;
        done = true;
        List<String> failures = new ArrayList<>();
        if (!riderSeen) failures.add("the rider never joined");
        if (joined < 3) failures.add("only " + joined + " players joined");
        if (!states.containsAll(EnumSet.of(RideState.GLIDE, RideState.CARVE, RideState.AIR))) failures.add("server saw states " + states);
        if (violations > 0) failures.add("validator flagged the rider " + violations + " times: " + lastReason);
        if (ticks >= TIMEOUT_TICKS) failures.add("timed out with " + server.getPlayerList().getPlayerCount() + " players online");
        note("server states " + states + ", violations " + violations);
        failures.forEach(f -> note("FAIL " + f));
        note(failures.isEmpty() ? "RESULT PASS" : "RESULT FAIL");
        try {
            Files.write(server.getServerDirectory().toPath().resolve("alpineshred-server-smoke.txt"), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            AlpineShred.LOGGER.error("[smoke] could not write the server result", e);
        }
        server.halt(false);
    }

    private static void note(String line) {
        lines.add(line);
        AlpineShred.LOGGER.info("[smoke] {}", line);
    }
}
