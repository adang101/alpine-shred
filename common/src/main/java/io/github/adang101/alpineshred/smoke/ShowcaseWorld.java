package io.github.adang101.alpineshred.smoke;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.lift.LiftLine;
import io.github.adang101.alpineshred.lift.LiftManager;
import io.github.adang101.alpineshred.lift.LiftNodeBlockEntity;
import io.github.adang101.alpineshred.lift.LiftTowerHeadBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Dev tool, inert unless run with {@code -Dalpineshred.showcase=true} ({@code ./gradlew :forge:runShowcaseServer}):
 * builds a test world on a dedicated server from a generated data pack, then stops.
 *
 * <p>The data pack ({@code world/datapacks/showcase}, written by {@code tools/showcase/gen.py}) does the building
 * with plain commands: {@code showcase:forceload}, then {@code showcase:build}, then {@code showcase:finish}.
 * Lifts cannot be linked by a command, so their nodes come from {@code showcase-lifts.json} in the run directory
 * (each lift: drive, heads in order, return station; the blocks are already placed by the build function) and are
 * linked here, the way {@link SmokeCourse#buildLift} does. Then the world is saved and the server halts.
 */
public final class ShowcaseWorld {
    private static final boolean ENABLED = Boolean.getBoolean("alpineshred.showcase");
    private static final int LOAD_TIMEOUT = 20 * 120;

    private static MinecraftServer server;
    private static int ticks, stage, waitFrom;
    private static int[] bounds; // min chunk x, min chunk z, max chunk x, max chunk z
    private static final List<List<BlockPos>> lifts = new ArrayList<>();

    private ShowcaseWorld() {
    }

    public static void register() {
        if (!ENABLED) return;
        AlpineShred.LOGGER.info("[showcase] showcase world builder armed");
        LifecycleEvent.SERVER_STARTED.register(s -> {
            server = s;
            read(Path.of("showcase-lifts.json"));
        });
        TickEvent.SERVER_POST.register(ShowcaseWorld::tick);
    }

    private static void read(Path file) {
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray b = root.getAsJsonArray("chunkBounds");
            bounds = new int[]{b.get(0).getAsInt(), b.get(1).getAsInt(), b.get(2).getAsInt(), b.get(3).getAsInt()};
            for (JsonElement lift : root.getAsJsonArray("lifts")) {
                List<BlockPos> nodes = new ArrayList<>();
                for (JsonElement n : lift.getAsJsonArray()) {
                    JsonArray p = n.getAsJsonArray();
                    nodes.add(new BlockPos(p.get(0).getAsInt(), p.get(1).getAsInt(), p.get(2).getAsInt()));
                }
                lifts.add(nodes);
            }
        } catch (Exception e) {
            AlpineShred.LOGGER.error("[showcase] cannot read {}", file.toAbsolutePath(), e);
            server.halt(false);
        }
    }

    private static void run(String command) {
        AlpineShred.LOGGER.info("[showcase] /{}", command);
        int result = server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
        AlpineShred.LOGGER.info("[showcase]   -> {}", result);
    }

    private static void tick(MinecraftServer s) {
        if (server == null || s != server) return;
        ticks++;
        ServerLevel level = server.overworld();
        switch (stage) {
            case 0 -> {
                if (ticks < 20) return;
                run("gamerule maxCommandChainLength 100000000");
                run("function showcase:forceload");
                waitFrom = ticks;
                stage = 1;
            }
            case 1 -> {
                if (!allLoaded(level) && ticks - waitFrom < LOAD_TIMEOUT) return;
                AlpineShred.LOGGER.info("[showcase] chunks loaded: {} after {} ticks", allLoaded(level), ticks - waitFrom);
                long t0 = System.currentTimeMillis();
                run("function showcase:build");
                AlpineShred.LOGGER.info("[showcase] build took {} ms", System.currentTimeMillis() - t0);
                stage = 2;
                waitFrom = ticks;
            }
            case 2 -> {
                if (ticks - waitFrom < 40) return; // block entities settle
                int i = 0;
                for (List<BlockPos> nodes : lifts) link(level, nodes, i++);
                run("function showcase:finish");
                waitFrom = ticks;
                stage = 3;
            }
            case 3 -> {
                if (ticks - waitFrom < 100) return;
                run("alpineshred lift list");
                run("forceload remove all");
                run("save-all flush");
                AlpineShred.LOGGER.info("[showcase] done; stopping");
                stage = 4;
                server.halt(false);
            }
            default -> {
            }
        }
    }

    private static boolean allLoaded(ServerLevel level) {
        for (int cx = bounds[0]; cx <= bounds[2]; cx++) {
            for (int cz = bounds[1]; cz <= bounds[3]; cz++) {
                if (!level.getChunkSource().hasChunk(cx, cz)) return false;
            }
        }
        return true;
    }

    private static void link(ServerLevel level, List<BlockPos> nodes, int index) {
        LiftLine line = LiftManager.create(level, nodes);
        if (line == null) {
            AlpineShred.LOGGER.error("[showcase] lift {} was not created: {}", index, nodes);
            return;
        }
        for (int i = 0; i < nodes.size(); i++) {
            if (!(level.getBlockEntity(nodes.get(i)) instanceof LiftNodeBlockEntity node)) {
                AlpineShred.LOGGER.error("[showcase] lift {}: no block entity at node {} ({})", index, i, nodes.get(i));
                return;
            }
            if (node instanceof LiftTowerHeadBlockEntity head) head.setLink(line.id, nodes.get(i - 1), nodes.get(i + 1));
            else node.setLineId(line.id); // the drive reads its redstone here and powers the line
        }
        AlpineShred.LOGGER.info("[showcase] lift {} linked: {} nodes, id {}", index, nodes.size(), line.id);
    }
}
