package io.github.adang101.alpineshred.smoke;

import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.lift.Boarding;
import io.github.adang101.alpineshred.lift.ChairEntity;
import io.github.adang101.alpineshred.lift.LiftChairs;
import io.github.adang101.alpineshred.lift.LiftLine;
import io.github.adang101.alpineshred.lift.LiftManager;
import io.github.adang101.alpineshred.park.V3;
import io.github.adang101.alpineshred.movement.RideServer;
import io.github.adang101.alpineshred.movement.RideState;
import io.github.adang101.alpineshred.movement.Riders;
import io.github.adang101.alpineshred.movement.StrapCheck;
import io.github.adang101.alpineshred.movement.StrapController;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The server half of the multiplayer smoke test (phase 2's exit gate: three clients see correct
 * state), run in CI with {@code -Dalpineshred.smoke.server=true} on a dedicated server
 * ({@code ./gradlew :forge:runSmokeServer}). Inert otherwise.
 *
 * <p>It builds the smoke run beside spawn. The player named {@code rider} is dressed for the
 * board, put at the top and strapped in; {@code watcher1} and {@code watcher2} hover beside the
 * slope and the kicker. The clients do the rest ({@code ClientSmoke}, scenario {@code multi}).
 *
 * <p>Phase 8 adds a lift stage after the run (see {@link #startLift}): the server builds a lift with a loop of
 * over 400 blocks, stages the three players onto it one after another, logs where every chair is every ten
 * ticks (the clients log what they see, and {@code multiplayer-check.py} compares), kicks {@code watcher1}
 * mid-ride and checks that it comes back on the same chair, then breaks a head with a command near the top
 * and checks that the line slows at 2.0 blocks per second squared and the riders can dismount. When that is
 * done, or on a timeout, it writes {@code alpineshred-server-smoke.txt} (the states the server accepted, the
 * validator's verdict, the lift's numbers) and stops the server.
 */
public final class ServerSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("alpineshred.smoke.server");
    private static final int TIMEOUT_TICKS = 20 * 60 * 16;

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

    // ---- the lift stage (phase 8) ----
    /** Ticks after the lift is built that the whole stage may take. */
    private static final int LIFT_LIMIT_TICKS = 20 * 60 * 9;
    private static final String[] BOARDING_ORDER = {"rider", "watcher1", "watcher2"};
    /** Ticks between one rider boarding and the next being placed (so each sits alone on a chair), and before strapping in. */
    private static final int BOARD_GAP = 80, STRAP_DELAY = 40;
    /** Ticks a strapped rider gets to be auto-boarded by their client before the server boards them itself. */
    private static final int BOARD_WAIT = 20 * 30;
    /** Ticks watcher1 rides before it is kicked, and the most it gets to come back. */
    private static final int RELOG_AFTER = 100, RELOG_WAIT = 20 * 90;
    /** The head is broken when the rider is this many blocks of cable short of the ejection point. */
    private static final double FAULT_BEFORE_TOP = 40;
    private static final int DISMOUNT_WAIT = 20 * 40, SETTLE_TICKS = 60;
    private static boolean liftStarted, liftDone;
    private static int restTicks, liftTicks, brakeSeenAt = -1;
    private static Vec3 lastRiderPos;
    private static UUID liftId;
    private static BlockPos liftBottom;
    private static Vec3 boardSpot = Vec3.ZERO;
    private static float boardYaw;
    private static final List<String> liftFailures = new ArrayList<>();
    private static int nextToBoard, placedAt = -1, lastBoardedTick;
    private static boolean fellBack;
    private static final Map<String, Integer> boardedTick = new HashMap<>();
    private static ServerPlayer kicked;
    private static boolean relogKicked, relogResolved;
    private static int relogChair = -1, relogKickTick, rejoinTick = -1;
    private static int faultTick = -1, settleTick = -1;
    private static final List<Double> faultSpeeds = new ArrayList<>();
    private static final Set<String> aboardAtFault = new HashSet<>();
    private static final Set<String> dismounted = new HashSet<>();

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
        if (liftStarted) {
            // A relog during the lift stage: the lift code checks where the player ended up.
            return;
        }
        switch (name) {
            case "rider" -> {
                riderSeen = true;
                SmokeCourse.dressForBoard(player);
                SmokeCourse.toTop(player, base);
                StrapCheck.Result result = StrapController.tryStrap(player);
                note("rider strapped: " + result);
            }
            // Hovering beside the slope and beside the kicker, both well inside tracking range.
            case "watcher1" -> {
                hover(player, base.getX() + 9.5, base.getY() + SmokeCourse.TOP, base.getZ() + 22.5, 90f);
                // Section 22's budget: 20 different designs in view, a few blocks in front of each
                // watcher toward the top of the run, where they look while the rider waits (items
                // are drawn only within about 16 blocks).
                showcase(player.serverLevel(), base.getX() + 7.5, base.getY() + SmokeCourse.TOP + 1, base.getZ() + 17.5);
            }
            case "watcher2" -> {
                hover(player, base.getX() - 8.5, base.getY() + 5, base.getZ() + SmokeCourse.KICKER + 4.5, -90f);
                showcase(player.serverLevel(), base.getX() - 7.5, base.getY() + 6, base.getZ() + SmokeCourse.KICKER - 1.5);
            }
            default -> note("unexpected player " + name);
        }
    }

    /** Twenty boards and skis, each with its own design, floating in a 5 by 4 grid centred on (x, y, z). */
    private static void showcase(ServerLevel level, double x, double y, double z) {
        var templates = io.github.adang101.alpineshred.design.paint.DesignTemplates.all();
        if (templates.isEmpty()) {
            note("no design templates for the showcase");
            return;
        }
        for (int i = 0; i < 20; i++) {
            boolean skis = i % 3 == 2;
            var kind = skis ? io.github.adang101.alpineshred.design.DesignKind.SKI : io.github.adang101.alpineshred.design.DesignKind.BOARD;
            var t = templates.get(i % templates.size());
            var d = t.design(kind, 0x101010 * (i % 15 + 1));
            if (i >= templates.size()) d = new io.github.adang101.alpineshred.design.Design(kind, d.base(), d.top(), d.edge(), d.pair(), "Swap " + i, "", null);
            var stack = new net.minecraft.world.item.ItemStack(skis ? io.github.adang101.alpineshred.registry.ModItems.SKIS.get()
                    : io.github.adang101.alpineshred.registry.ModItems.SNOWBOARD.get());
            io.github.adang101.alpineshred.design.DesignNbt.write(stack, d);
            var item = new net.minecraft.world.entity.item.ItemEntity(level, x + (i % 5 - 2) * 0.8, y + (i / 5) * 0.6, z, stack, 0, 0, 0);
            item.setNoGravity(true);
            item.setNeverPickUp();
            item.setUnlimitedLifetime();
            level.addFreshEntity(item);
        }
        note("showcase of 20 designs at " + String.format(Locale.ROOT, "%.1f %.1f %.1f", x, y, z));
    }

    private static void hover(ServerPlayer player, double x, double y, double z, float yaw) {
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.teleportTo(player.serverLevel(), x, y, z, yaw, 20f);
    }

    private static long lastTickNanos;

    private static void tick(MinecraftServer server) {
        if (done || base == null) return;
        ticks++;
        // Server stalls, for the multiplayer check: while the server stands still the watchers see
        // the rider stand still, whatever the rider's own client does.
        long nanos = System.nanoTime();
        if (lastTickNanos != 0 && nanos - lastTickNanos > 150_000_000L) {
            note(String.format(Locale.ROOT, "stall gt%d %d ms", server.overworld().getGameTime(), (nanos - lastTickNanos) / 1_000_000));
        }
        lastTickNanos = nanos;
        ServerPlayer rider = server.getPlayerList().getPlayerByName("rider");
        if (rider != null) {
            RideState state = Riders.state(rider);
            states.add(state);
            if (state != lastState) {
                note(String.format(Locale.ROOT, "t%d gt%d %s at z %.1f", ticks, server.overworld().getGameTime(), state, rider.getZ() - base.getZ()));
                lastState = state;
            }
            // Once the lift's head is broken the riders drop off the chairs; what the validator makes of a fall is not the lift's.
            int now = faultTick >= 0 ? violations : RideServer.violations(rider);
            if (now > violations) {
                note(String.format(Locale.ROOT, "t%d gt%d violation %d at z %.1f y %.2f: %s", ticks, server.overworld().getGameTime(), now,
                        rider.getZ() - base.getZ(), rider.getY() - base.getY(), RideServer.lastReason(rider)));
            }
            violations = now;
            lastReason = RideServer.lastReason(rider);
        }
        if (!liftStarted) watchRun(server, rider);
        else liftTick(server);
        boolean everyoneLeft = riderSeen && server.getPlayerList().getPlayerCount() == 0;
        if (!liftDone && !everyoneLeft && ticks < TIMEOUT_TICKS) return;
        done = true;
        List<String> failures = new ArrayList<>();
        if (!riderSeen) failures.add("the rider never joined");
        if (joined < 3) failures.add("only " + joined + " players joined");
        if (!states.containsAll(EnumSet.of(RideState.GLIDE, RideState.CARVE, RideState.AIR))) failures.add("server saw states " + states);
        if (violations > 0) failures.add("validator flagged the rider " + violations + " times: " + lastReason);
        failures.addAll(liftFailures);
        if (!liftDone) failures.add("the lift stage did not finish (started " + liftStarted + ", boarded " + boardedTick.keySet() + ")");
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

    // ---- the lift stage (phase 8) ----------------------------------------------------------------------

    private static ServerPlayer player(MinecraftServer server, String name) {
        return server.getPlayerList().getPlayerByName(name);
    }

    private static void liftFail(String message) {
        liftFailures.add("lift: " + message);
        note("lift problem: " + message);
    }

    /** Before the lift: waits for the rider to brake to a stop at the end of the run, then starts the lift stage. */
    private static void watchRun(MinecraftServer server, ServerPlayer rider) {
        if (rider == null || !states.contains(RideState.BRAKE)) {
            restTicks = 0;
            return;
        }
        if (brakeSeenAt < 0) brakeSeenAt = ticks;
        Vec3 pos = rider.position();
        restTicks = lastRiderPos != null && pos.distanceToSqr(lastRiderPos) < 0.0025 ? restTicks + 1 : 0;
        lastRiderPos = pos;
        // At rest for five seconds, or half a minute after the brake began, whichever is first.
        if (restTicks >= 100 || ticks - brakeSeenAt > 600) startLift(server);
    }

    /** Builds the lift 30 blocks east of the run and starts the boarding. */
    private static void startLift(MinecraftServer server) {
        liftStarted = true;
        ServerLevel level = server.overworld();
        List<BlockPos> nodes = SmokeCourse.longLift(new BlockPos(base.getX() + 30, base.getY(), base.getZ()));
        LiftLine line = SmokeCourse.buildLift(level, nodes);
        if (line == null) {
            liftFail("the line could not be built");
            liftDone = true;
            return;
        }
        liftId = line.id;
        liftBottom = line.returnStation;
        V3 spot = line.path().pos(2.0);
        Float yaw = ChairEntity.yawOf(line.path().tangent(2.0));
        boardSpot = new Vec3(spot.x(), liftBottom.getY(), spot.z());
        boardYaw = yaw == null ? 0f : yaw;
        note(String.format(Locale.ROOT, "lift: built at %s, loop %.0f blocks, %d chairs, powered %b target %.1f, boarding stretch to %.1f, eject at %.1f",
                liftBottom, line.path().loopLength(), line.chairCount(), line.powered, line.targetSpeed, line.stations().boardEnd(), line.stations().ejectS()));
        if (line.path().loopLength() < 400) liftFail("the loop is only " + line.path().loopLength() + " blocks");
        if (!line.powered || line.targetSpeed <= 0) liftFail("the drive did not power the line");
    }

    private static void liftTick(MinecraftServer server) {
        if (liftDone) return;
        ServerLevel level = server.overworld();
        LiftLine line = LiftManager.get(level, liftId);
        if (line == null) {
            liftFail("the line vanished");
            liftDone = true;
            return;
        }
        liftTicks++;
        long gt = level.getGameTime();
        board(server, level, line);
        if (!boardedTick.isEmpty() && gt % 10 == 0) sampleChairs(level, line, gt);
        relog(server, line, gt);
        fault(server, level, line, gt);
        if (!liftDone && liftTicks > LIFT_LIMIT_TICKS) {
            liftFail("timed out after " + liftTicks + " ticks (boarded " + boardedTick.keySet() + ", relog " + relogResolved + ", fault tick " + faultTick + ")");
            liftDone = true;
        }
    }

    /**
     * Puts the players on the lift one at a time, each at the bottom station, strapped in; their clients auto-board
     * the next chair that comes. Spacing them out keeps each alone on a two-seat chair, which a relog needs.
     */
    private static void board(MinecraftServer server, ServerLevel level, LiftLine line) {
        if (nextToBoard >= BOARDING_ORDER.length) return;
        String name = BOARDING_ORDER[nextToBoard];
        ServerPlayer p = player(server, name);
        if (p == null) return;
        if (placedAt < 0) {
            if (liftTicks < 40 || (nextToBoard > 0 && liftTicks - lastBoardedTick < BOARD_GAP)) return;
            StrapController.unstrap(p, StrapController.UnstrapReason.COMMAND);
            p.getAbilities().flying = false;
            p.teleportTo(level, boardSpot.x, boardSpot.y, boardSpot.z, boardYaw, 0f);
            SmokeCourse.dressForBoard(p);
            placedAt = liftTicks;
            fellBack = false;
            note("lift: placed " + name + " at the bottom station, gt" + level.getGameTime());
            return;
        }
        if (liftTicks - placedAt == STRAP_DELAY) {
            StrapCheck.Result result = StrapController.tryStrap(p);
            note("lift: " + name + " strap " + result);
            if (!result.ok()) liftFail(name + " could not strap in: " + result);
        }
        if (p.getVehicle() instanceof ChairEntity chair) {
            boardedTick.put(name, liftTicks);
            lastBoardedTick = liftTicks;
            note(String.format(Locale.ROOT, "lift: %s boarded chair %d%s at gt%d, line speed %.2f", name, chair.chairIndex(),
                    fellBack ? " by the server's fallback" : " by auto-board", level.getGameTime(), line.speed));
            nextToBoard++;
            placedAt = -1;
        } else if (!fellBack && liftTicks - placedAt > STRAP_DELAY + BOARD_WAIT) {
            fellBack = true;
            liftFail("auto-board did not board " + name + " within " + BOARD_WAIT / 20 + " seconds");
            ChairEntity best = null;
            for (int i = 0; i < line.chairCount(); i++) {
                ChairEntity c = LiftChairs.find(level, line.id, i);
                if (c != null && (best == null || Boarding.distance(p, c) < Boarding.distance(p, best))) best = c;
            }
            note("lift: fallback boarding " + (best == null ? "has no chair" : Boarding.tryBoard(p, best, Double.MAX_VALUE)));
        }
    }

    /** One line per chair that exists, for {@code multiplayer-check.py} to hold the clients' chairs against. */
    private static void sampleChairs(ServerLevel level, LiftLine line, long gt) {
        for (int i = 0; i < line.chairCount(); i++) {
            ChairEntity chair = LiftChairs.find(level, line.id, i);
            if (chair != null) note(String.format(Locale.ROOT, "chair gt%d %d %.3f %.3f %.3f", gt, i, chair.getX(), chair.getY(), chair.getZ()));
        }
    }

    /** Kicks watcher1 mid-ride, then checks that it comes back on the chair it left. */
    private static void relog(MinecraftServer server, LiftLine line, long gt) {
        if (relogResolved) return;
        ServerPlayer w1 = player(server, "watcher1");
        if (!relogKicked) {
            Integer since = boardedTick.get("watcher1");
            if (since != null && w1 != null && w1.getVehicle() instanceof ChairEntity chair && liftTicks - since >= RELOG_AFTER) {
                relogChair = chair.chairIndex();
                relogKicked = true;
                relogKickTick = liftTicks;
                kicked = w1;
                note(String.format(Locale.ROOT, "lift: watcher1 logs out on chair %d at gt%d, line travel %.1f", relogChair, gt, line.travel));
                w1.connection.disconnect(Component.literal("lift smoke relog"));
            }
            return;
        }
        if (w1 != null && w1 != kicked && rejoinTick < 0) {
            rejoinTick = liftTicks;
            note("lift: watcher1 is back after " + (liftTicks - relogKickTick) + " ticks, gt" + gt);
        }
        if (rejoinTick >= 0 && liftTicks - rejoinTick >= 20) {
            boolean same = w1 != null && w1.getVehicle() instanceof ChairEntity c && liftId.equals(c.lineId()) && c.chairIndex() == relogChair;
            note("lift: watcher1 " + (same ? "re-attached to chair " + relogChair : "is NOT on chair " + relogChair + " (vehicle " + (w1 == null ? "none, offline" : w1.getVehicle()) + ")"));
            if (!same) liftFail("watcher1 did not re-attach to its chair after a relog");
            relogResolved = true;
        } else if (rejoinTick < 0 && liftTicks - relogKickTick > RELOG_WAIT) {
            liftFail("watcher1 did not come back within " + RELOG_WAIT / 20 + " seconds");
            relogResolved = true;
        }
    }

    /**
     * Near the top, breaks the topmost head with a command: the line must fault, slow at the emergency rate
     * (2.0 blocks per second squared) and let its riders dismount (their clients sneak). Then ends the stage.
     */
    private static void fault(MinecraftServer server, ServerLevel level, LiftLine line, long gt) {
        if (faultTick < 0) {
            Integer since = boardedTick.get("rider");
            if (since == null) return;
            ServerPlayer lead = player(server, "rider");
            boolean riding = lead != null && lead.getVehicle() instanceof ChairEntity;
            boolean near = false;
            if (riding) {
                ChairEntity chair = (ChairEntity) lead.getVehicle();
                near = line.path().chairS(line.travel, chair.chairIndex(), line.chairCount()) >= line.stations().ejectS() - FAULT_BEFORE_TOP;
            }
            if (riding && near && (relogResolved || !relogKicked && liftTicks - since > 20 * 150)) {
                breakHead(server, level, line, gt);
            } else if (!riding && liftTicks - since > 40) {
                liftFail("the rider left the chair before the head was broken");
                breakHead(server, level, line, gt);
            }
            return;
        }
        faultSpeeds.add(line.speed);
        for (String name : aboardAtFault) {
            ServerPlayer p = player(server, name);
            if (!dismounted.contains(name) && p != null && !p.isPassenger()) {
                dismounted.add(name);
                note(String.format(Locale.ROOT, "lift: %s dismounted %d ticks after the fault, line speed %.2f", name, liftTicks - faultTick, line.speed));
            }
        }
        boolean settled = line.speed == 0 && dismounted.containsAll(aboardAtFault);
        if (settled && settleTick < 0) settleTick = liftTicks;
        if (settled && liftTicks - settleTick >= SETTLE_TICKS || liftTicks - faultTick > DISMOUNT_WAIT) {
            finishFault(server, line);
        }
    }

    private static void breakHead(MinecraftServer server, ServerLevel level, LiftLine line, long gt) {
        BlockPos head = line.heads.get(0);
        faultTick = liftTicks;
        faultSpeeds.add(line.speed);
        for (String name : BOARDING_ORDER) {
            ServerPlayer p = player(server, name);
            if (p != null && p.getVehicle() instanceof ChairEntity) aboardAtFault.add(name);
        }
        note(String.format(Locale.ROOT, "lift: breaking the head at %s with a command at gt%d, line speed %.2f, travel %.1f, aboard %s", head.toShortString(), gt,
                line.speed, line.travel, aboardAtFault));
        var source = server.createCommandSourceStack().withLevel(level).withPermission(4);
        server.getCommands().performPrefixedCommand(source, "setblock " + head.getX() + " " + head.getY() + " " + head.getZ() + " minecraft:air");
        if (!line.faulted) liftFail("breaking the head did not fault the line");
        else note("lift: line faulted: " + line.faultReason);
    }

    private static void finishFault(MinecraftServer server, LiftLine line) {
        // The deceleration: the drop in speed over every tick that started and ended above zero.
        double total = 0;
        int pairs = 0;
        for (int i = 1; i < faultSpeeds.size(); i++) {
            double before = faultSpeeds.get(i - 1), after = faultSpeeds.get(i);
            if (before > 0 && after > 0) {
                total += before - after;
                pairs++;
            }
        }
        double decel = pairs == 0 ? Double.NaN : total / pairs * 20;
        note(String.format(Locale.ROOT, "lift: faulted line slowed at %.3f blocks per second squared over %d ticks (start %.2f, end %.2f, stopped %b)",
                decel, pairs, faultSpeeds.get(0), line.speed, line.speed == 0));
        if (pairs < 3 || Math.abs(decel - 2.0) > 0.01) liftFail("the faulted line slowed at " + decel + " b/s^2 over " + pairs + " ticks, not 2.0");
        if (line.speed != 0) liftFail("the faulted line was still moving at " + line.speed);
        for (String name : aboardAtFault) {
            if (dismounted.contains(name)) continue;
            liftFail(name + " could not dismount from the faulted line");
            ServerPlayer p = player(server, name);
            if (p != null) p.stopRiding();
        }
        if (aboardAtFault.isEmpty()) liftFail("nobody was aboard when the head broke");
        liftDone = true;
    }

    private static void note(String line) {
        lines.add(line);
        AlpineShred.LOGGER.info("[smoke] {}", line);
    }
}
