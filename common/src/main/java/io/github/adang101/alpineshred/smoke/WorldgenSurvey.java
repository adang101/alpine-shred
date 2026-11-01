package io.github.adang101.alpineshred.smoke;

import com.mojang.datafixers.util.Pair;
import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.lift.LiftLine;
import io.github.adang101.alpineshred.lift.LiftManager;
import io.github.adang101.alpineshred.lift.LiftNodeBlockEntity;
import io.github.adang101.alpineshred.park.block.BoxBlock;
import io.github.adang101.alpineshred.park.block.RailBlock;
import io.github.adang101.alpineshred.registry.ModBlocks;
import io.github.adang101.alpineshred.worldgen.GroomedRunPiece;
import io.github.adang101.alpineshred.worldgen.LiftLinePiece;
import io.github.adang101.alpineshred.worldgen.LodgePiece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The server half of the world generation smoke test (phase 7, the visual review): in a default
 * world with structures on, finds the nearest of each structure to the snowy slopes the mountain
 * ride uses, generates every chunk it touches, counts what was built there against what it should
 * hold, and picks a viewpoint above it for the client's picture.
 */
public final class WorldgenSurvey {
    /** Where the camera goes for one structure's picture, and a point inside its first piece. */
    public record Stop(String name, double x, double y, double z, float yaw, float pitch, BlockPos inside) {
    }

    public record Result(List<Stop> stops, List<String> notes, List<String> failures) {
    }

    /** Structure, search radius in chunks. */
    private static final Object[][] SURVEY = {{"ski_resort", 200}, {"terrain_park", 100}, {"groomed_run", 120}, {"snow_feature", 50}};
    private static final int MAX_CHUNKS_FROM_START = 7;

    private WorldgenSurvey() {
    }

    public static Result run(ServerLevel level) {
        List<Stop> stops = new ArrayList<>();
        List<String> notes = new ArrayList<>(), failures = new ArrayList<>();
        Pair<BlockPos, Holder<Biome>> slopes = level.findClosestBiome3d(b -> b.is(Biomes.SNOWY_SLOPES), new BlockPos(0, 100, 0), 6400, 32, 64);
        if (slopes == null) {
            failures.add("no snowy slopes within 6400 blocks");
            return new Result(stops, notes, failures);
        }
        BlockPos from = slopes.getFirst();
        notes.add("surveying from the snowy slopes at " + from);
        for (Object[] s : SURVEY) {
            String name = (String) s[0];
            int radius = (Integer) s[1];
            long t0 = System.nanoTime();
            Holder<Structure> holder = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                    .getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE, AlpineShred.id(name)));
            Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
                    .findNearestMapStructure(level, HolderSet.direct(holder), from, radius, false);
            long located = System.nanoTime();
            if (found == null) {
                failures.add("no " + name + " within " + radius + " chunks of " + from);
                continue;
            }
            ChunkPos chunk = new ChunkPos(found.getFirst());
            StructureStart start = level.getChunk(chunk.x, chunk.z, ChunkStatus.STRUCTURE_STARTS).getStartForStructure(holder.value());
            if (start == null || !start.isValid()) {
                failures.add(name + ": located at chunk " + chunk + " but no start there");
                continue;
            }
            BoundingBox box = start.getBoundingBox();
            int far = 0;
            int runs = 0, lodges = 0, liftPieces = 0;
            for (StructurePiece piece : start.getPieces()) {
                BoundingBox b = piece.getBoundingBox();
                far = Math.max(far, Math.max(Math.max(Math.abs((b.minX() >> 4) - chunk.x), Math.abs((b.maxX() >> 4) - chunk.x)),
                        Math.max(Math.abs((b.minZ() >> 4) - chunk.z), Math.abs((b.maxZ() >> 4) - chunk.z))));
                if (piece instanceof GroomedRunPiece) runs++;
                if (piece instanceof LodgePiece) lodges++;
                if (piece instanceof LiftLinePiece) liftPieces++;
            }
            if (far > MAX_CHUNKS_FROM_START) failures.add(name + ": a piece " + far + " chunks from its start");
            // Build everything it touches (and a chunk round it, so the edges are decorated too).
            int chunks = 0;
            for (int cx = (box.minX() >> 4) - 1; cx <= (box.maxX() >> 4) + 1; cx++) {
                for (int cz = (box.minZ() >> 4) - 1; cz <= (box.maxZ() >> 4) + 1; cz++) {
                    level.getChunk(cx, cz);
                    chunks++;
                }
            }
            long built = System.nanoTime();
            Map<String, Integer> counts = count(level, box);
            if (name.equals("ski_resort")) checkLift(level, box, liftPieces, counts, notes, failures);
            notes.add(String.format(Locale.ROOT, "%s at chunk %s: box %d..%d, %d..%d, %d..%d; %d pieces (%d runs, %d lodges), farthest %d chunks; "
                            + "located in %d ms, %d chunks built in %d ms; %s", name, chunk, box.minX(), box.maxX(), box.minY(), box.maxY(),
                    box.minZ(), box.maxZ(), start.getPieces().size(), runs, lodges, far, (located - t0) / 1_000_000, chunks,
                    (built - located) / 1_000_000, counts));
            int groomed = counts.getOrDefault("groomed_snow", 0), markers = counts.getOrDefault("slope_markers", 0);
            switch (name) {
                case "ski_resort" -> {
                    check(failures, runs == 1 && lodges == 1, name + ": " + runs + " runs and " + lodges + " lodges");
                    check(failures, groomed >= 200, name + ": only " + groomed + " groomed snow");
                    check(failures, markers >= 2, name + ": " + markers + " slope markers");
                    check(failures, counts.getOrDefault("lodge_chests", 0) == 1, name + ": " + counts.getOrDefault("lodge_chests", 0) + " lodge chests");
                    check(failures, counts.getOrDefault("spruce_planks", 0) >= 200, name + ": the lodge has "
                            + counts.getOrDefault("spruce_planks", 0) + " planks");
                }
                case "groomed_run" -> {
                    check(failures, groomed >= 200, name + ": only " + groomed + " groomed snow");
                    check(failures, markers >= 2, name + ": " + markers + " slope markers");
                }
                case "terrain_park" -> {
                    check(failures, counts.getOrDefault("start_gates", 0) >= 1, name + ": no start gate");
                    int features = counts.getOrDefault("rails", 0) + counts.getOrDefault("boxes", 0) + counts.getOrDefault("ramps", 0)
                            + counts.getOrDefault("pipes", 0);
                    check(failures, features >= 8, name + ": only " + features + " park blocks");
                }
                default -> check(failures, counts.getOrDefault("snow_blocks", 0) >= 20, name + ": only "
                        + counts.getOrDefault("snow_blocks", 0) + " snow blocks");
            }
            BoundingBox first = start.getPieces().get(0).getBoundingBox();
            BlockPos inside = new BlockPos((first.minX() + first.maxX()) / 2, Math.min(first.maxY(), first.minY() + 3), (first.minZ() + first.maxZ()) / 2);
            stops.add(viewpoint(level, name, box, inside));
        }
        return new Result(stops, notes, failures);
    }

    /**
     * A resort's lift: the pieces and the blocks agree (a drive, a return station, a head and a pole under each head),
     * and once every node's load check has run (the chunks here are built but not ticked, so it is run by hand,
     * as the first tick does) the one line exists and is not faulted. A resort may legitimately have no lift
     * (its plan was skipped); that is a note, not a failure.
     */
    private static void checkLift(ServerLevel level, BoundingBox box, int liftPieces, Map<String, Integer> counts, List<String> notes, List<String> failures) {
        int drives = counts.getOrDefault("lift_drives", 0), returns = counts.getOrDefault("lift_returns", 0);
        int heads = counts.getOrDefault("lift_heads", 0), poles = counts.getOrDefault("lift_poles", 0);
        if (liftPieces == 0) {
            notes.add("ski_resort: no lift planned (resort lifts: 0)");
            check(failures, drives + returns + heads + poles == 0, "ski_resort: lift blocks without a lift piece");
            return;
        }
        check(failures, drives == 1 && returns == 1, "ski_resort: " + drives + " drive stations and " + returns + " return stations");
        check(failures, heads >= 1 && poles >= heads, "ski_resort: " + heads + " heads on " + poles + " poles");
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int y = box.minY(); y <= box.maxY(); y++) {
                    if (level.getBlockState(pos.set(x, y, z)).hasBlockEntity() && level.getBlockEntity(pos) instanceof LiftNodeBlockEntity be) {
                        be.loadCheck(level);
                    }
                }
            }
        }
        List<LiftLine> lines = new ArrayList<>();
        for (LiftLine line : LiftManager.lines(level)) {
            if (box.isInside(line.drive)) lines.add(line);
        }
        check(failures, lines.size() == 1, "ski_resort: " + lines.size() + " lift lines in its bounds");
        for (LiftLine line : lines) {
            check(failures, !line.faulted, "ski_resort: generated lift " + line.id + " is faulted: " + line.faultReason);
            check(failures, line.heads.size() == heads, "ski_resort: the line has " + line.heads.size() + " heads, the world " + heads);
            notes.add(String.format(Locale.ROOT, "ski_resort lift: resort lifts: 1, %d heads, loop %.0f blocks, faulted %b, powered %b", line.heads.size(),
                    line.path().loopLength(), line.faulted, line.powered));
        }
    }

    private static void check(List<String> failures, boolean ok, String message) {
        if (!ok) failures.add(message);
    }

    /** Above and south of the structure, looking down at its middle, far enough back to see it whole. */
    private static Stop viewpoint(ServerLevel level, String name, BoundingBox box, BlockPos inside) {
        double cx = (box.minX() + box.maxX() + 1) / 2.0, cz = (box.minZ() + box.maxZ() + 1) / 2.0;
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(cx), (int) Math.floor(cz));
        double extent = Math.max(box.getXSpan(), box.getZSpan());
        double up = Math.max(18, Math.min(110, extent * 0.55)), back = up * 0.6;
        double ex = cx, ey = Math.max(ground, box.maxY()) + up, ez = cz + back;
        double dx = cx - ex, dy = ground - ey, dz = cz - ez;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.hypot(dx, dz)));
        return new Stop(name, ex, ey, ez, yaw, pitch, inside);
    }

    /** What stands inside the box, by kind. */
    private static Map<String, Integer> count(ServerLevel level, BoundingBox box) {
        Map<String, Integer> counts = new TreeMap<>();
        Block marker1 = ModBlocks.SLOPE_MARKER_GREEN.get(), marker2 = ModBlocks.SLOPE_MARKER_BLUE.get(), marker3 = ModBlocks.SLOPE_MARKER_BLACK.get();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minY = Math.max(box.minY(), level.getMinBuildHeight()), maxY = Math.min(box.maxY(), level.getMaxBuildHeight() - 1);
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockState s = level.getBlockState(pos.set(x, y, z));
                    if (s.isAir()) continue;
                    Block b = s.getBlock();
                    String key = null;
                    if (b == ModBlocks.GROOMED_SNOW.get()) key = "groomed_snow";
                    else if (b == marker1 || b == marker2 || b == marker3) key = "slope_markers";
                    else if (b instanceof RailBlock) key = "rails";
                    else if (b instanceof BoxBlock) key = "boxes";
                    else if (b == ModBlocks.PARK_RAMP.get()) key = "ramps";
                    else if (b == ModBlocks.QUARTER_PIPE.get()) key = "pipes";
                    else if (b == ModBlocks.START_GATE.get()) key = "start_gates";
                    else if (b == ModBlocks.LIFT_TOWER_HEAD.get()) key = "lift_heads";
                    else if (b == ModBlocks.LIFT_TOWER_POLE.get()) key = "lift_poles";
                    else if (b == ModBlocks.LIFT_DRIVE_STATION.get()) key = "lift_drives";
                    else if (b == ModBlocks.LIFT_RETURN_STATION.get()) key = "lift_returns";
                    else if (b == Blocks.SNOW_BLOCK) key = "snow_blocks";
                    else if (b == Blocks.SPRUCE_PLANKS) key = "spruce_planks";
                    else if (b == Blocks.CHEST) {
                        BlockEntity be = level.getBlockEntity(pos);
                        if (be != null && LodgePiece.LOOT.toString().equals(be.saveWithoutMetadata().getString("LootTable"))) key = "lodge_chests";
                    }
                    if (key != null) counts.merge(key, 1, Integer::sum);
                }
            }
        }
        return counts;
    }
}
