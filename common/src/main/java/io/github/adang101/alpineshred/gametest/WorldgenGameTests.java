package io.github.adang101.alpineshred.gametest;

import com.mojang.datafixers.util.Pair;
import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.park.PipeArc;
import io.github.adang101.alpineshred.park.RailPlacement;
import io.github.adang101.alpineshred.park.block.LevelRails;
import io.github.adang101.alpineshred.park.block.ParkStates;
import io.github.adang101.alpineshred.park.block.PipeBlock;
import io.github.adang101.alpineshred.park.block.RailBlock;
import io.github.adang101.alpineshred.registry.ModBlocks;
import io.github.adang101.alpineshred.worldgen.GroomedRunPiece;
import io.github.adang101.alpineshred.worldgen.LodgePiece;
import io.github.adang101.alpineshred.worldgen.RunPlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static io.github.adang101.alpineshred.gametest.RideGameTests.check;

/**
 * Phase 7 game tests (design doc sections 12 and 19, "structure placement inside bounds"): every
 * template is whole and its jigsaws and rail connections line up; and the four structures,
 * generated over real overworld terrain for three seeds (the noise generator alone, so no chunks
 * are built), give at least 1000 starts, every piece within 7 chunks of its start and inside the
 * world's height.
 */
public final class WorldgenGameTests {
    static final String[] PARK = {"entry_gate", "rail_flat", "rail_kinked", "rail_curved", "box_line", "kicker_line", "pipe_pair",
            "jib_line", "end_bench", "end_flags"};
    static final String[] SNOW = {"kicker", "roller", "cornice"};
    static final long[] SEEDS = {20261029L, 1850301L, -4172144997902289642L};
    /** Structure and its set. */
    private static final String[][] KINDS = {{"ski_resort", "ski_resorts"}, {"groomed_run", "groomed_runs"},
            {"terrain_park", "terrain_parks"}, {"snow_feature", "snow_features"}};
    /** Starts wanted from each seed, and candidate chunks tried per seed before giving up. */
    private static final int[] WANTED = {10, 40, 200, 200}, MAX_TRIES = {1500, 1500, 6000, 6000};
    /** At least this many of each over all seeds, and 1000 in all (section 20, phase 7's gate). */
    private static final int[] REQUIRED = {6, 25, 150, 150};
    /**
     * Mean time to generate one start, in ms, over all seeds, on the CI machine. Jigsaws keep
     * section 19's 50 ms. Runs and resorts sample about a hundred noise columns each and measure
     * about 230 and 190 ms there (see the README's clarifications); these limits catch a regression.
     */
    private static final double[] MAX_MEAN_MS = {400, 400, 50, 50};
    private static final int MAX_CHUNKS_FROM_START = 7;

    private WorldgenGameTests() {
    }

    public static void templatesAreWhole(GameTestHelper helper) {
        StructureTemplateManager manager = helper.getLevel().getStructureManager();
        StructurePlaceSettings settings = new StructurePlaceSettings();
        List<Block> railBlocks = BuiltInRegistries.BLOCK.stream().filter(b -> b instanceof RailBlock).toList();
        for (String name : PARK) {
            ResourceLocation id = AlpineShred.id("park/" + name);
            StructureTemplate t = manager.get(id).orElse(null);
            check(t != null, "no template " + id);
            Vec3i size = t.getSize();
            check(size.getX() == 9, id + " is " + size.getX() + " wide, not 9");
            int in = 0, out = 0;
            for (StructureTemplate.StructureBlockInfo j : t.filterBlocks(BlockPos.ZERO, settings, Blocks.JIGSAW)) {
                CompoundTag nbt = j.nbt() == null ? new CompoundTag() : j.nbt();
                String jigsaw = nbt.getString("name");
                Direction front = j.state().getValue(JigsawBlock.ORIENTATION).front();
                if (jigsaw.equals("alpineshred:park_in")) {
                    in++;
                    check(j.pos().equals(new BlockPos(4, 1, 0)) && front == Direction.NORTH, id + ": in-jigsaw at " + j.pos() + " facing " + front);
                } else if (jigsaw.equals("alpineshred:park_out")) {
                    out++;
                    check(j.pos().equals(new BlockPos(4, 0, size.getZ() - 1)) && front == Direction.SOUTH,
                            id + ": out-jigsaw at " + j.pos() + " facing " + front);
                    check("alpineshred:park_in".equals(nbt.getString("target")) && "alpineshred:park/lines".equals(nbt.getString("pool")),
                            id + ": out-jigsaw targets " + nbt.getString("target") + " in " + nbt.getString("pool"));
                } else {
                    check(false, id + ": unexpected jigsaw '" + jigsaw + "' at " + j.pos());
                }
            }
            check(in == (name.equals("entry_gate") ? 0 : 1), id + ": " + in + " in-jigsaws");
            check(out == (name.startsWith("end_") ? 0 : 1), id + ": " + out + " out-jigsaws");

            // Rail connection flags as the rails' own placement rules would set them.
            Map<BlockPos, BlockState> rails = new HashMap<>();
            for (Block b : railBlocks) {
                for (StructureTemplate.StructureBlockInfo info : t.filterBlocks(BlockPos.ZERO, settings, b)) rails.put(info.pos(), info.state());
            }
            RailPlacement.World world = new RailPlacement.World() {
                @Override
                public RailPlacement.Piece railAt(RailPlacement.Cell c) {
                    BlockState s = rails.get(LevelRails.pos(c));
                    return s == null ? null : RailBlock.piece(s);
                }

                @Override
                public boolean solid(RailPlacement.Cell c) {
                    return false;
                }
            };
            for (Map.Entry<BlockPos, BlockState> e : rails.entrySet()) {
                boolean[] joined = RailPlacement.connections(world, LevelRails.cell(e.getKey()), RailBlock.piece(e.getValue()));
                boolean a = e.getValue().getValue(ParkStates.CONNECTED_A), b = e.getValue().getValue(ParkStates.CONNECTED_B);
                check(joined[0] == a && joined[1] == b, String.format(Locale.ROOT, "%s: rail at %s says connected %b/%b, its neighbours make it %b/%b",
                        id, e.getKey(), a, b, joined[0], joined[1]));
            }
            if (name.startsWith("rail_")) check(rails.size() >= 8, id + " has " + rails.size() + " rail pieces");
            for (StructureTemplate.StructureBlockInfo info : t.filterBlocks(BlockPos.ZERO, settings, ModBlocks.QUARTER_PIPE.get())) {
                int index = info.state().getValue(ParkStates.PIPE_INDEX);
                check(index < PipeArc.cellCount(PipeBlock.radius(info.state())), id + ": pipe index " + index + " at " + info.pos());
            }
        }
        for (String name : SNOW) {
            ResourceLocation id = AlpineShred.id("snow/" + name);
            StructureTemplate t = manager.get(id).orElse(null);
            check(t != null, "no template " + id);
            check(t.getSize().getX() <= 20 && t.getSize().getZ() <= 20, id + " is " + t.getSize());
            check(t.filterBlocks(BlockPos.ZERO, settings, Blocks.SNOW_BLOCK).size() > 20, id + " has almost no snow");
        }
        StructureTemplate lodge = manager.get(LodgePiece.TEMPLATE).orElse(null);
        check(lodge != null, "no lodge template");
        check(lodge.getSize().getX() == 24 && lodge.getSize().getZ() == 18, "the lodge is " + lodge.getSize());
        List<StructureTemplate.StructureBlockInfo> markers = lodge.filterBlocks(BlockPos.ZERO, settings, Blocks.STRUCTURE_BLOCK);
        check(markers.size() == 1 && markers.get(0).nbt() != null && "lodge_chest".equals(markers.get(0).nbt().getString("metadata"))
                && "DATA".equals(markers.get(0).nbt().getString("mode")), "the lodge's data markers: " + markers);
        helper.succeed();
    }

    public static void structuresGenerateWithinBounds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RegistryAccess access = level.registryAccess();
        Holder<NoiseGeneratorSettings> noise = access.registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        Holder<MultiNoiseBiomeSourceParameterList> params = access.registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getHolderOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        BiomeSource biomes = MultiNoiseBiomeSource.createFromPreset(params);
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(biomes, noise);
        Registry<Structure> structures = access.registryOrThrow(Registries.STRUCTURE);
        Registry<StructureSet> sets = access.registryOrThrow(Registries.STRUCTURE_SET);
        StructureTemplateManager templates = level.getStructureManager();

        int total = 0;
        int[] found = new int[KINDS.length], tried = new int[KINDS.length];
        long[] nanos = new long[KINDS.length], validNanos = new long[KINDS.length];
        long[] worstNanos = new long[KINDS.length];
        int farthest = 0;
        List<String> problems = new ArrayList<>();
        Map<RunPlanner.Failure, Integer> runFailures = new java.util.EnumMap<>(RunPlanner.Failure.class);
        for (long seed : SEEDS) {
            RandomState random = RandomState.create(noise.value(), access.lookupOrThrow(Registries.NOISE), seed);
            Pair<BlockPos, Holder<Biome>> slopes = biomes.findClosestBiome3d(new BlockPos(0, 100, 0), 6400, 32, 64,
                    b -> b.is(Biomes.SNOWY_SLOPES), random.sampler(), level);
            check(slopes != null, "seed " + seed + ": no snowy slopes within 6400 blocks");
            ChunkPos centre = new ChunkPos(slopes.getFirst());
            for (int k = 0; k < KINDS.length; k++) {
                Structure structure = structures.getOrThrow(ResourceKey.create(Registries.STRUCTURE, AlpineShred.id(KINDS[k][0])));
                StructureSet set = sets.getOrThrow(ResourceKey.create(Registries.STRUCTURE_SET, AlpineShred.id(KINDS[k][1])));
                RandomSpreadStructurePlacement placement = (RandomSpreadStructurePlacement) set.placement();
                int spacing = placement.spacing();
                int rx0 = Math.floorDiv(centre.x, spacing), rz0 = Math.floorDiv(centre.z, spacing);
                int here = 0, triedHere = 0;
                for (int ring = 0; here < WANTED[k] && triedHere < MAX_TRIES[k]; ring++) {
                    for (int dx = -ring; dx <= ring && here < WANTED[k] && triedHere < MAX_TRIES[k]; dx++) {
                        for (int dz = -ring; dz <= ring && here < WANTED[k] && triedHere < MAX_TRIES[k]; dz++) {
                            if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                            triedHere++;
                            ChunkPos chunk = placement.getPotentialStructureChunk(seed, (rx0 + dx) * spacing, (rz0 + dz) * spacing);
                            long t0 = System.nanoTime();
                            StructureStart start;
                            try {
                                start = structure.generate(access, generator, biomes, random, templates, seed, chunk, 0, level,
                                        structure.biomes()::contains);
                            } catch (RuntimeException e) {
                                AlpineShred.LOGGER.error("[worldgen test] {} at {} threw", KINDS[k][0], chunk, e);
                                throw new AssertionError(KINDS[k][0] + " at chunk " + chunk + " (seed " + seed + ") threw " + e, e);
                            }
                            long took = System.nanoTime() - t0;
                            tried[k]++;
                            nanos[k] += took;
                            if (k <= 1 && !start.isValid()) runFailures.merge(RunPlanner.lastFailure(), 1, Integer::sum);
                            if (!start.isValid()) continue;
                            here++;
                            found[k]++;
                            validNanos[k] += took;
                            worstNanos[k] = Math.max(worstNanos[k], took);
                            farthest = Math.max(farthest, checkStart(KINDS[k][0], seed, chunk, start, level, problems));
                        }
                    }
                }
                total += here;
            }
        }
        StringBuilder summary = new StringBuilder();
        for (int k = 0; k < KINDS.length; k++) {
            summary.append(String.format(Locale.ROOT, "%s %d of %d tried (%.1f ms a try, %.1f ms a start, worst %.0f ms); ", KINDS[k][0],
                    found[k], tried[k], nanos[k] / 1.0e6 / Math.max(1, tried[k]), validNanos[k] / 1.0e6 / Math.max(1, found[k]),
                    worstNanos[k] / 1.0e6));
        }
        AlpineShred.LOGGER.info("[worldgen test] {} starts over {} seeds: {}farthest piece {} chunks from its start; runs and resorts "
                + "not started: {}", total, SEEDS.length, summary, farthest, runFailures);
        problems.stream().limit(20).forEach(p -> AlpineShred.LOGGER.error("[worldgen test] {}", p));
        check(problems.isEmpty(), problems.size() + " out-of-bounds starts, the first: " + (problems.isEmpty() ? "" : problems.get(0)));
        check(total >= 1000, "only " + total + " starts: " + summary);
        for (int k = 0; k < KINDS.length; k++) {
            check(found[k] >= REQUIRED[k], "only " + found[k] + " " + KINDS[k][0] + " starts: " + summary);
            double mean = validNanos[k] / 1.0e6 / Math.max(1, found[k]);
            check(mean <= MAX_MEAN_MS[k], String.format(Locale.ROOT, "%s takes %.0f ms a start (limit %.0f): %s", KINDS[k][0], mean,
                    MAX_MEAN_MS[k], summary));
        }
        helper.succeed();
    }

    /** Problems with one start go to {@code problems}; returns its farthest piece's distance in chunks. */
    private static int checkStart(String kind, long seed, ChunkPos chunk, StructureStart start, ServerLevel level, List<String> problems) {
        String where = kind + " at chunk " + chunk + " (seed " + seed + ")";
        int far = 0;
        boolean run = false, lodge = false;
        for (StructurePiece piece : start.getPieces()) {
            BoundingBox b = piece.getBoundingBox();
            far = Math.max(far, Math.max(Math.max(Math.abs((b.minX() >> 4) - chunk.x), Math.abs((b.maxX() >> 4) - chunk.x)),
                    Math.max(Math.abs((b.minZ() >> 4) - chunk.z), Math.abs((b.maxZ() >> 4) - chunk.z))));
            if (b.minY() < level.getMinBuildHeight() || b.maxY() >= level.getMaxBuildHeight()) {
                problems.add(where + ": a piece's box " + b + " leaves the world's height");
            }
            if (piece instanceof GroomedRunPiece g) {
                run = true;
                RunPlanner.Plan plan = g.plan();
                if (plan.length() < RunPlanner.MIN_BUILT_LENGTH - 8) problems.add(where + ": a run only " + plan.length() + " long");
            }
            if (piece instanceof LodgePiece) lodge = true;
        }
        if (far > MAX_CHUNKS_FROM_START) problems.add(where + ": a piece " + far + " chunks from the start");
        if (kind.equals("ski_resort") && !(run && lodge)) problems.add(where + ": run " + run + ", lodge " + lodge);
        if (kind.equals("groomed_run") && !run) problems.add(where + ": no run piece");
        return far;
    }
}
