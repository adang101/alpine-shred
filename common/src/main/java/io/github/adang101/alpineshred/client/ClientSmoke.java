package io.github.adang101.alpineshred.client;

import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.client.feel.FeelStats;
import io.github.adang101.alpineshred.client.feel.RiderTracker;
import io.github.adang101.alpineshred.feel.RiderClips;
import io.github.adang101.alpineshred.gear.GearAccess;
import io.github.adang101.alpineshred.gear.GogglesItem;
import io.github.adang101.alpineshred.gear.Lens;
import io.github.adang101.alpineshred.movement.RideMode;
import io.github.adang101.alpineshred.movement.RideServer;
import io.github.adang101.alpineshred.movement.RideSnapshot;
import io.github.adang101.alpineshred.movement.RideState;
import io.github.adang101.alpineshred.movement.Riders;
import io.github.adang101.alpineshred.movement.SlideController;
import io.github.adang101.alpineshred.movement.StrapCheck;
import io.github.adang101.alpineshred.movement.StrapController;
import io.github.adang101.alpineshred.registry.ModBlocks;
import io.github.adang101.alpineshred.registry.ModItems;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The client smoke test, run in CI with {@code -Dalpineshred.smoke=true} on a virtual display
 * ({@code ./gradlew :forge:runSmokeClient}). Inert otherwise.
 *
 * <p>It creates a flat world and builds a snow run (a staircase slope, a flat with a pop, a kicker,
 * a powder patch). Then, on a snowboard and again on skis with poles, it straps in and rides the run
 * with simulated keys: skate (and pole push), carve both ways, pop, launch, brake. After each run
 * it holds still and plays a gallery of rider clips. Screenshots from behind, from the side (through
 * {@link CameraOrbit}), from the front and in first person go to {@code screenshots/}; what it saw
 * goes to {@code alpineshred-smoke.txt}; then it quits. A client mixin that fails to apply, a crash
 * in the feel systems, or a rider that never slides, launches or makes sound fails it.
 *
 * <p>With {@code -Dalpineshred.smoke.scenario=mountain} ({@code ./gradlew :forge:runMountainClient})
 * it rides real terrain instead (risk R4): a default world, the nearest snowy slopes, and a minute
 * steering down the fall line around whatever is in the way. Any server validator violation fails
 * it; air time, wipeouts and surfaces go in the transcript.
 */
public final class ClientSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("alpineshred.smoke");
    private static final boolean MOUNTAIN = "mountain".equals(System.getProperty("alpineshred.smoke.scenario"));
    private static final int MOUNTAIN_RIDE_TICKS = 20 * 60;
    private static final int TIMEOUT_TICKS = 20 * 300;
    /** Course layout, in blocks along +z from the start. */
    private static final int SLOPE_START = 3, SLOPE_END = 48, KICKER = 60, POWDER_FROM = 84, POWDER_TO = 92, LENGTH = 130;
    private static final int TOP = 16;
    /**
     * Orbit yaw that looks at a regular snowboarder's chest (their body faces 90 degrees right of
     * the travel direction) and at a skier's left side.
     */
    private static final float SIDE = -90f;
    private static final float OFFSIDE = 90f;

    private enum Stage {MENU, LOADING, BUILDING, RIDE_BOARD, GALLERY_BOARD, SWAP, RIDE_SKIS, GALLERY_SKIS,
        FINDING, RIDE_MOUNTAIN, DONE}

    private record Shot(String name, int frame) {
    }

    /** One gallery picture: play {@code clip}, wait {@code delay} ticks, shoot from {@code yaw}. */
    private record Pose(String name, String clip, int delay, float yaw) {
    }

    private static Stage stage = Stage.MENU;
    private static int ticks;
    private static int stageTicks;
    private static int frames;
    private static BlockPos origin;
    private static UUID playerId;
    @Nullable
    private static volatile ServerPlayer serverPlayer;
    private static final List<String> lines = new ArrayList<>();
    private static final List<String> failures = Collections.synchronizedList(new ArrayList<>());
    private static final Set<String> shots = new HashSet<>();
    @Nullable
    private static Shot pending;

    // ---- per run ----
    private static final Set<RideState> states = EnumSet.noneOf(RideState.class);
    private static int rideTicks;
    private static double maxSpeed;
    private static boolean wasAir;
    private static int airTicks;
    private static int landings;
    private static int landedAt = -1;

    // ---- mountain ----
    private static double distance;
    private static double lastX = Double.NaN, lastZ;
    private static int stuckTicks;
    private static int wipeouts;
    private static RideState lastState = RideState.OFF;
    private static final Map<Integer, Integer> airEpisodes = new TreeMap<>();
    private static final Set<String> surfaces = new java.util.TreeSet<>();
    /** The last client ticks, dumped to the transcript when the server counts a violation. */
    private static final java.util.ArrayDeque<String> recentTicks = new java.util.ArrayDeque<>();
    private static int violationsSeen;
    private static int dumps;
    private static double lastY = Double.NaN;
    /** High snowy points near the slopes, highest first: the start, then restarts after getting stuck. */
    private static final List<BlockPos> mountainStarts = Collections.synchronizedList(new ArrayList<>());
    private static int restarts;

    // ---- gallery ----
    private static List<Pose> gallery = List.of();
    private static int galleryIndex;
    private static int galleryTicks;

    private ClientSmoke() {
    }

    public static void register() {
        if (!ENABLED) return;
        AlpineShred.LOGGER.info("[smoke] client smoke test armed");
        ClientTickEvent.CLIENT_POST.register(ClientSmoke::tick);
        ClientGuiEvent.RENDER_HUD.register((graphics, tickDelta) -> frames++);
    }

    private static void tick(Minecraft mc) {
        ticks++;
        stageTicks++;
        if (stage == Stage.DONE) return;
        takePending(mc);
        if (ticks > TIMEOUT_TICKS) {
            failures.add("timed out in stage " + stage);
            finish(mc);
            return;
        }
        switch (stage) {
            case MENU -> {
                if (mc.getOverlay() == null && mc.level == null && stageTicks > 40) {
                    createWorld(mc);
                    next(Stage.LOADING);
                }
            }
            case LOADING -> {
                MinecraftServer server = mc.getSingleplayerServer();
                if (mc.player != null && mc.level != null && server != null && mc.player.tickCount > 60) {
                    mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                    RideDebugOverlay.setEnabled(true);
                    playerId = mc.player.getUUID();
                    if (MOUNTAIN) {
                        onServer(mc, ClientSmoke::findMountain);
                        next(Stage.FINDING);
                    } else {
                        onServer(mc, ClientSmoke::build);
                        next(Stage.BUILDING);
                    }
                }
            }
            case BUILDING -> {
                if (mc.player != null && origin != null && Riders.isStrapped(mc.player) && stageTicks > 20) {
                    note("strapped in at " + mc.player.blockPosition());
                    startRun();
                    next(Stage.RIDE_BOARD);
                } else if (stageTicks > 400) {
                    failures.add("never strapped in");
                    finish(mc);
                }
            }
            case RIDE_BOARD -> {
                if (ride(mc, false)) {
                    startGallery(boardGallery());
                    next(Stage.GALLERY_BOARD);
                }
            }
            case GALLERY_BOARD -> {
                if (gallery(mc)) next(Stage.SWAP);
            }
            case SWAP -> swap(mc);
            case RIDE_SKIS -> {
                if (ride(mc, true)) {
                    startGallery(skiGallery());
                    next(Stage.GALLERY_SKIS);
                }
            }
            case GALLERY_SKIS -> {
                if (gallery(mc)) finish(mc);
            }
            case FINDING -> {
                if (mc.player != null && origin != null && Riders.isStrapped(mc.player) && stageTicks > 40) {
                    note("strapped in at " + mc.player.blockPosition());
                    startRun();
                    next(Stage.RIDE_MOUNTAIN);
                } else if (stageTicks > 20 * 180) {
                    failures.add("never reached the mountain");
                    finish(mc);
                }
            }
            case RIDE_MOUNTAIN -> {
                if (rideMountain(mc)) finish(mc);
            }
            default -> {
            }
        }
    }

    private static void next(Stage s) {
        stage = s;
        stageTicks = 0;
        note("stage " + s);
    }

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> task) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            failures.add("no integrated server");
            return;
        }
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                failures.add("no server player");
                return;
            }
            task.accept(player);
        });
    }

    // ---- world -------------------------------------------------------------------------------------

    private static void createWorld(Minecraft mc) {
        LevelSettings settings = new LevelSettings("Alpine Shred smoke test", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                true, new GameRules(), WorldDataConfiguration.DEFAULT);
        WorldOptions options = new WorldOptions(20261029L, false, false);
        mc.createWorldOpenFlows().createFreshLevel("alpineshred-smoke-" + System.currentTimeMillis(), settings, options,
                access -> access.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(MOUNTAIN ? WorldPresets.NORMAL : WorldPresets.FLAT).value().createWorldDimensions());
    }

    /** Server thread: build the run, dress the player for snowboarding, put them at the top and strap in. */
    private static void build(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        level.setDayTime(6000);
        level.setWeatherParameters(6000, 0, false, false);
        BlockPos base = player.blockPosition();
        Block groomed = ModBlocks.GROOMED_SNOW.get();
        for (int dz = 0; dz < LENGTH; dz++) {
            double h = height(dz);
            for (int dx = -4; dx <= 4; dx++) {
                for (int dy = 0; dy < (int) Math.floor(h); dy++) {
                    level.setBlock(base.offset(dx, dy, dz), groomed.defaultBlockState(), 2);
                }
                double frac = h - Math.floor(h);
                if (frac > 1.0e-6) {
                    int layers = (int) Math.round(frac * 8) + 1;
                    level.setBlock(base.offset(dx, (int) Math.floor(h), dz),
                            Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, Math.min(8, layers)), 2);
                }
                if (dz >= POWDER_FROM && dz < POWDER_TO) {
                    level.setBlock(base.offset(dx, (int) Math.floor(h) - 1, dz), Blocks.POWDER_SNOW.defaultBlockState(), 2);
                }
            }
        }
        dressForBoard(player);
        origin = base;
        toTop(player);
        StrapCheck.Result result = StrapController.tryStrap(player);
        note("built the run at " + base + "; strap " + result);
        if (!result.ok()) failures.add("strap failed: " + result);
    }

    private static void dressForBoard(ServerPlayer player) {
        GearAccess.current().set(player, GearAccess.Slot.BOARD, new ItemStack(ModItems.SNOWBOARD.get()));
        GearAccess.current().set(player, GearAccess.Slot.GOGGLES, GogglesItem.withLens(new ItemStack(ModItems.GOGGLES.get()), Lens.AMBER));
        GearAccess.current().set(player, GearAccess.Slot.MITTS, new ItemStack(ModItems.MITTS.get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.SNOWBOARD_BOOTS.get()));
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.SKI_HELMET.get()));
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        RideServer.setDebug(player, true);
        serverPlayer = player;
    }

    private static void toTop(ServerPlayer player) {
        player.teleportTo(player.serverLevel(), origin.getX() + 0.5, origin.getY() + TOP, origin.getZ() + 1.5, 0f, 15f);
    }

    /** Column height of the run at {@code dz}: platform, 1-in-3 staircase, flat, kicker, flat. */
    private static double height(int dz) {
        if (dz < SLOPE_START) return TOP;
        if (dz <= SLOPE_END) return Math.max(1, TOP - Math.floor((dz - SLOPE_START) / 3.0) - 1);
        if (dz >= KICKER && dz < KICKER + 4) return 1 + 0.375 * (dz - KICKER + 1);
        return 1;
    }

    /** Off the board, onto skis with poles in hand, back to the top, strapped in again. */
    private static void swap(Minecraft mc) {
        if (stageTicks == 1) {
            onServer(mc, player -> {
                StrapController.unstrap(player, StrapController.UnstrapReason.COMMAND);
                GearAccess.current().set(player, GearAccess.Slot.BOARD, new ItemStack(ModItems.SKIS.get()));
                player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.SKI_BOOTS.get()));
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SKI_POLES.get()));
                toTop(player);
            });
        } else if (stageTicks == 30) {
            onServer(mc, player -> {
                StrapCheck.Result result = StrapController.tryStrap(player);
                note("skis: strap " + result);
                if (!result.ok()) failures.add("strap onto skis failed: " + result);
            });
        } else if (stageTicks > 50 && mc.player != null) {
            RideSnapshot ride = Riders.get(mc.player);
            if (ride.state().isStrapped() && ride.mode() == RideMode.SKIS) {
                startRun();
                next(Stage.RIDE_SKIS);
            } else if (stageTicks > 400) {
                failures.add("never strapped onto skis: " + ride);
                finish(mc);
            }
        }
    }

    // ---- mountain (risk R4) ------------------------------------------------------------------------

    /** Server thread: find snowy slopes, generate around them, start at a high snowy point facing downhill. */
    private static void findMountain(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        level.setDayTime(6000);
        level.setWeatherParameters(6000, 0, false, false);
        long started = System.currentTimeMillis();
        // From a fixed point, not the player's randomized spawn, so every run rides the same slope.
        Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(b -> b.is(Biomes.SNOWY_SLOPES), new BlockPos(0, 100, 0),
                6400, 32, 64);
        if (found == null) {
            failures.add("no snowy slopes within 6400 blocks");
            return;
        }
        BlockPos at = found.getFirst();
        int cx = at.getX() >> 4, cz = at.getZ() >> 4;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) level.getChunk(cx + dx, cz + dz);
        }
        List<BlockPos> starts = new ArrayList<>();
        for (int dx = -24; dx <= 24; dx += 3) {
            for (int dz = -24; dz <= 24; dz += 3) {
                int x = at.getX() + dx, z = at.getZ() + dz;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (snowy(level, x, y, z)) starts.add(new BlockPos(x, y, z));
            }
        }
        starts.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
        mountainStarts.addAll(starts);
        BlockPos start = starts.isEmpty() ? null : starts.get(0);
        if (start == null) {
            failures.add("no snow near the snowy slopes at " + at);
            return;
        }
        float yaw = steepestDescent(level, start.getX() + 0.5, start.getZ() + 0.5, 12);
        note(String.format(Locale.ROOT, "snowy slopes at %s; start %s facing %.0f (%d ms to find and generate)",
                at, start, yaw, System.currentTimeMillis() - started));
        dressForBoard(player);
        player.teleportTo(level, start.getX() + 0.5, start.getY() + 0.1, start.getZ() + 0.5, yaw, 20f);
        origin = start;
        StrapCheck.Result result = StrapController.tryStrap(player);
        note("strap " + result);
        if (!result.ok()) failures.add("strap failed: " + result);
    }

    private static boolean snowy(net.minecraft.world.level.LevelReader level, int x, int surfaceY, int z) {
        BlockState below = level.getBlockState(new BlockPos(x, surfaceY - 1, z));
        BlockState at = level.getBlockState(new BlockPos(x, surfaceY, z));
        return below.is(BlockTags.SNOW) || at.is(Blocks.SNOW);
    }

    /** The yaw, in 10 degree steps, whose ground {@code reach} blocks away is lowest. */
    private static float steepestDescent(net.minecraft.world.level.LevelReader level, double x, double z, int reach) {
        float best = 0;
        int lowest = Integer.MAX_VALUE;
        for (int yaw = 0; yaw < 360; yaw += 10) {
            double r = Math.toRadians(yaw);
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x - Math.sin(r) * reach), Mth.floor(z + Math.cos(r) * reach));
            if (h < lowest) {
                lowest = h;
                best = yaw;
            }
        }
        return best;
    }

    /**
     * Steering for real terrain: of the directions within 60 degrees of the current one, the one
     * whose ground 3, 6 and 10 blocks ahead is lowest, with a small cost for turning; trees and
     * rocks stand high in the heightmap, so the line goes round them.
     */
    private static float steer(net.minecraft.world.level.LevelReader level, LocalPlayer player) {
        float yaw = player.getYRot();
        float best = yaw;
        double bestScore = Double.MAX_VALUE;
        for (int a = -60; a <= 60; a += 10) {
            double r = Math.toRadians(yaw + a);
            double score = Math.abs(a) * 0.03;
            for (int d : new int[]{3, 6, 10}) {
                score += level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(player.getX() - Math.sin(r) * d),
                        Mth.floor(player.getZ() + Math.cos(r) * d));
            }
            if (score < bestScore) {
                bestScore = score;
                best = yaw + a;
            }
        }
        return best;
    }

    /** One tick of the mountain ride; true when it is over and checked. */
    private static boolean rideMountain(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            failures.add("player vanished");
            return true;
        }
        rideTicks++;
        SlideController c = RideDriver.INSTANCE.controller();
        RideState state = RideDriver.INSTANCE.localState(player);
        states.add(state);
        double speed = c == null ? 0 : c.speed();
        maxSpeed = Math.max(maxSpeed, speed);
        if (c != null && c.surface() != null) surfaces.add(c.surface().id().toString());
        if (!Double.isNaN(lastX)) distance += Math.hypot(player.getX() - lastX, player.getZ() - lastZ);
        lastX = player.getX();
        lastZ = player.getZ();

        float turn = Mth.clamp(Mth.wrapDegrees(steer(mc.level, player) - player.getYRot()), -5f, 5f);
        player.setYRot(player.getYRot() + turn);
        hold(mc.options.keyUp, speed < 0.08);

        boolean air = state == RideState.AIR;
        if (air) {
            airTicks++;
        } else if (airTicks > 0) {
            airEpisodes.merge(airTicks, 1, Integer::sum);
            airTicks = 0;
        }
        if (state == RideState.WIPEOUT && lastState != RideState.WIPEOUT) {
            wipeouts++;
            note(String.format(Locale.ROOT, "wipeout at %s, speed %.3f", player.blockPosition(), speed));
        }
        lastState = state;
        stuckTicks = speed < 0.03 ? stuckTicks + 1 : 0;

        ServerPlayer sp = serverPlayer;
        int violations = sp == null ? 0 : RideServer.violations(sp);
        BlockPos feet = player.blockPosition();
        recentTicks.addLast(String.format(Locale.ROOT,
                "  t%d y%.3f dy%+.3f ground %b/%s %s %s speed %.3f feet %s below %s floats %b powder %b hcol %b fall %.2f",
                rideTicks, player.getY(), Double.isNaN(lastY) ? 0 : player.getY() - lastY, player.onGround(),
                c == null ? "-" : c.grounded(), c == null ? "-" : c.mode(), state, speed,
                mc.level.getBlockState(feet).getBlock().getName().getString(),
                mc.level.getBlockState(feet.below()).getBlock().getName().getString(),
                io.github.adang101.alpineshred.movement.RideHooks.floatsOnPowder(player), player.isInPowderSnow,
                player.horizontalCollision, player.fallDistance));
        if (recentTicks.size() > 12) recentTicks.removeFirst();
        lastY = player.getY();
        if (violations > violationsSeen) {
            if (dumps++ < 6) {
                note("server violation " + violations + " (" + (sp == null ? "" : RideServer.lastReason(sp)) + "), last client ticks:");
                recentTicks.forEach(ClientSmoke::note);
            }
            violationsSeen = violations;
        }
        if (rideTicks % 20 == 0) {
            note(String.format(Locale.ROOT, "t%d %s %s speed %.3f grade %.1f surface %s distance %.0f violations %d",
                    rideTicks, player.blockPosition(), state, speed, c == null ? 0 : c.slope().degrees(),
                    c == null || c.surface() == null ? "none" : c.surface().id(), distance, violations));
        }
        if (rideTicks % 200 == 60) back(mc, "mountain_" + rideTicks);
        if (rideTicks > 300 && rideTicks < 400) orbit(mc, "mountain_side", SIDE);
        if (rideTicks > 500 && rideTicks < 600) view(mc, "mountain_first_person", CameraType.FIRST_PERSON);

        boolean stuck = rideTicks > 100 && stuckTicks > 100;
        if (stuck && restarts < 3 && rideTicks < MOUNTAIN_RIDE_TICKS - 200) {
            // Stuck (deep powder sinks a slow rider, as in vanilla): start again from the next high point.
            restarts++;
            BlockPos next = mountainStarts.get(Math.min(mountainStarts.size() - 1, restarts * Math.max(1, mountainStarts.size() / 4)));
            note("stuck at " + player.blockPosition() + "; restarting at " + next);
            onServer(mc, sp2 -> {
                float yaw = steepestDescent(sp2.serverLevel(), next.getX() + 0.5, next.getZ() + 0.5, 12);
                sp2.teleportTo(sp2.serverLevel(), next.getX() + 0.5, next.getY() + 0.1, next.getZ() + 0.5, yaw, 20f);
            });
            stuckTicks = 0;
            lastX = Double.NaN;
            return false;
        }
        if (!stuck && rideTicks < MOUNTAIN_RIDE_TICKS) return false;

        hold(mc.options.keyUp, false);
        if (stuck) note("stopped: no speed for 5 seconds at " + player.blockPosition());
        note(String.format(Locale.ROOT, "mountain: %d ticks, %.0f blocks, max speed %.3f, states %s, wipeouts %d, restarts %d",
                rideTicks, distance, maxSpeed, states, wipeouts, restarts));
        note("air episodes (ticks=count): " + airEpisodes);
        note("surfaces: " + surfaces);
        if (sp != null) note("server violations " + violations + ", last reason: " + RideServer.lastReason(sp));
        check(violations == 0, "server validator flagged real-terrain riding " + violations + " times: "
                + (sp == null ? "" : RideServer.lastReason(sp)));
        check(distance > 60, String.format(Locale.ROOT, "rode only %.0f blocks", distance));
        check(states.contains(RideState.GLIDE) || states.contains(RideState.CARVE), "never glided: " + states);
        return true;
    }

    // ---- riding ------------------------------------------------------------------------------------

    private static void startRun() {
        states.clear();
        rideTicks = 0;
        maxSpeed = 0;
        wasAir = false;
        airTicks = 0;
        landings = 0;
        landedAt = -1;
    }

    /** One tick of a run; true when the run is over and checked. */
    private static boolean ride(Minecraft mc, boolean skis) {
        LocalPlayer player = mc.player;
        if (player == null) {
            failures.add("player vanished");
            finish(mc);
            return false;
        }
        rideTicks++;
        SlideController c = RideDriver.INSTANCE.controller();
        RideState state = RideDriver.INSTANCE.localState(player);
        states.add(state);
        double speed = c == null ? 0 : c.speed();
        maxSpeed = Math.max(maxSpeed, speed);
        double z = player.getZ() - origin.getZ();

        boolean skate = rideTicks < 40;
        boolean right = z > 12 && z < 22;
        boolean left = z > 26 && z < 36;
        boolean jump = z > 50 && z < 52.5;
        boolean brake = z > 104;
        hold(mc.options.keyUp, skate);
        hold(mc.options.keyRight, right);
        hold(mc.options.keyLeft, left);
        hold(mc.options.keyJump, jump);
        hold(mc.options.keyShift, brake);
        if (skis && (rideTicks == 6 || rideTicks == 18 || rideTicks == 30)) {
            KeyMapping.click(mc.options.keyUse.getDefaultKey()); // pole push
        }

        boolean air = state == RideState.AIR;
        airTicks = air ? airTicks + 1 : 0;
        if (air && !wasAir) note("airborne at z " + String.format(Locale.ROOT, "%.1f", z));
        if (!air && wasAir) {
            landings++;
            landedAt = rideTicks;
        }
        wasAir = air;

        if (rideTicks % 10 == 0) {
            note(String.format(Locale.ROOT, "t%d z%.1f y%.2f %s speed %.3f lean %.2f frames %d %s", rideTicks, z,
                    player.getY() - origin.getY(), state, speed, c == null ? 0 : c.lean(), frames, FeelStats.summary()));
        }

        // Screenshots: one at a time, so later windows wait for earlier ones.
        String p = skis ? "skis_" : "board_";
        if (skis && rideTicks >= 10 && rideTicks < 16) orbit(mc, p + "pole_push_side", SIDE);
        if (right && z > 15.5) back(mc, p + "carve_right");
        if (right && z > 18) orbit(mc, p + "carve_right_side", SIDE);
        if (left && z > 29.5) back(mc, p + "carve_left");
        if (left && z > 32) orbit(mc, p + "carve_left_side", SIDE);
        if (z > 39 && z < 43) view(mc, p + "front", CameraType.THIRD_PERSON_FRONT);
        if (z > 44 && z < 49.5) orbit(mc, p + "glide_offside", OFFSIDE);
        if (jump && z > 50.7) orbit(mc, p + "charge_side", SIDE);
        if (air && z < 60 && airTicks >= 2) orbit(mc, p + "pop_air_side", SIDE);
        if (landings > 0 && rideTicks >= landedAt + 2 && rideTicks <= landedAt + 8) back(mc, p + "landing_" + landings);
        if (air && z > 62 && airTicks >= 3) back(mc, p + "kicker_air");
        if (air && z > 62 && airTicks >= 6) orbit(mc, p + "kicker_air_side", SIDE);
        if (z > 77 && z < 84) view(mc, p + "first_person", CameraType.FIRST_PERSON);
        if (z > POWDER_FROM + 2 && z < POWDER_TO) back(mc, p + "powder");
        if (z > POWDER_FROM + 4.5 && z < POWDER_TO) orbit(mc, p + "powder_side", SIDE);
        if (brake && z > 108) back(mc, p + "brake");
        if (brake && z > 111) orbit(mc, p + "brake_side", SIDE);

        boolean stopped = brake && speed < 0.05;
        if (!stopped && z <= LENGTH - 6 && rideTicks <= 20 * 60) return false;

        hold(mc.options.keyShift, false);
        String run = skis ? "skis: " : "board: ";
        check(maxSpeed > (skis ? 0.4 : 0.5), run + String.format(Locale.ROOT, "max speed %.3f", maxSpeed));
        check(states.contains(RideState.GLIDE) || states.contains(RideState.CARVE), run + "never glided: " + states);
        check(states.contains(RideState.CARVE), run + "never carved: " + states);
        check(!states.contains(RideState.WIPEOUT), run + "wiped out on a clean run: " + states);
        if (skis) {
            check(FeelStats.polePushes > 0, run + "no pole push");
        } else {
            check(states.contains(RideState.AIR), run + "never airborne: " + states);
            check(FeelStats.clipsPlayed > 3, run + "animation clips played: " + FeelStats.clipsPlayed);
            check(FeelStats.loopsStarted > 0, run + "no sound loops started");
            check(FeelStats.oneShotsPlayed > 0, run + "no pop or landing sounds");
            check(FeelStats.particlesSpawned > 0, run + "no particles");
            check(FeelStats.maxFovAdded > 1, run + "no speed field of view: " + FeelStats.maxFovAdded);
            check(FeelStats.maxRoll > 0.5, run + "no camera roll: " + FeelStats.maxRoll);
        }
        note(run + "states " + states + ", max speed " + String.format(Locale.ROOT, "%.3f", maxSpeed) + ", landings "
                + landings + ", " + FeelStats.summary());
        return true;
    }

    private static void hold(KeyMapping key, boolean down) {
        KeyMapping.set(key.getDefaultKey(), down);
        key.setDown(down);
    }

    // ---- pose gallery ------------------------------------------------------------------------------

    private static List<Pose> boardGallery() {
        List<Pose> out = new ArrayList<>();
        for (String[] clip : new String[][]{{RiderClips.IDLE_BOARD, "8"}, {RiderClips.GLIDE_BOARD_REGULAR, "8"},
                {RiderClips.TUCK_BOARD, "8"}, {RiderClips.BRAKE_BOARD, "8"}, {RiderClips.POP_CHARGE, "6"},
                {RiderClips.POP_LAUNCH, "2"}, {RiderClips.AIR_BOARD, "8"}, {RiderClips.LAND_SKETCHY, "3"},
                {RiderClips.WIPEOUT_FRONT, "12"}, {RiderClips.WIPEOUT_BACK, "12"}}) {
            int delay = Integer.parseInt(clip[1]);
            out.add(new Pose("pose_board_" + clip[0] + "_side", clip[0], delay, SIDE));
            out.add(new Pose("pose_board_" + clip[0] + "_quarter", clip[0], delay, -35f));
        }
        return out;
    }

    private static List<Pose> skiGallery() {
        List<Pose> out = new ArrayList<>();
        for (String[] clip : new String[][]{{RiderClips.IDLE_SKIS, "8"}, {RiderClips.GLIDE_SKIS, "8"},
                {RiderClips.TUCK_SKIS, "8"}, {RiderClips.SNOWPLOW_SKIS, "8"}, {RiderClips.AIR_SKIS, "8"},
                {RiderClips.WIPEOUT_FRONT, "12"}}) {
            int delay = Integer.parseInt(clip[1]);
            out.add(new Pose("pose_skis_" + clip[0] + "_side", clip[0], delay, SIDE));
            out.add(new Pose("pose_skis_" + clip[0] + "_front", clip[0], delay, 150f));
        }
        return out;
    }

    private static void startGallery(List<Pose> poses) {
        gallery = poses;
        galleryIndex = 0;
        galleryTicks = 0;
    }

    /**
     * Plays each clip fresh (back to the ride state's own clip for a tick first, so a clip shot
     * from two angles restarts), waits its delay, takes the picture. True when done.
     */
    private static boolean gallery(Minecraft mc) {
        if (galleryIndex >= gallery.size()) {
            RiderTracker.previewClip(null);
            return true;
        }
        Pose pose = gallery.get(galleryIndex);
        String name = pose.name();
        galleryTicks++;
        if (galleryTicks == 1) {
            RiderTracker.previewClip(null);
        } else if (galleryTicks == 2) {
            RiderTracker.previewClip(pose.clip());
        } else if (shots.contains(name) || galleryTicks > 120) {
            if (!shots.contains(name)) note("gallery: no picture of " + name);
            galleryIndex++;
            galleryTicks = 0;
        } else if (galleryTicks >= 2 + pose.delay()) {
            request(mc, name, CameraType.THIRD_PERSON_BACK, pose.yaw(), 8f, 3.8f);
        }
        return false;
    }

    // ---- screenshots -------------------------------------------------------------------------------

    private static void back(Minecraft mc, String name) {
        request(mc, name, CameraType.THIRD_PERSON_BACK, Float.NaN);
    }

    private static void orbit(Minecraft mc, String name, float yaw) {
        request(mc, name, CameraType.THIRD_PERSON_BACK, yaw, 12f, 3.2f);
    }

    private static void view(Minecraft mc, String name, CameraType type) {
        request(mc, name, type, Float.NaN);
    }

    private static void request(Minecraft mc, String name, CameraType type, float orbitYaw) {
        request(mc, name, type, orbitYaw, 0f, 0f);
    }

    /**
     * Points the camera for a picture; the picture is taken once a frame has rendered from there
     * (software rendering on the CI display can be slower than the tick rate).
     */
    private static void request(Minecraft mc, String name, CameraType type, float orbitYaw, float pitch, float distance) {
        if (pending != null || shots.contains(name)) return;
        mc.options.setCameraType(type);
        if (Float.isNaN(orbitYaw)) {
            CameraOrbit.clear();
        } else {
            CameraOrbit.set(orbitYaw, pitch, distance);
        }
        pending = new Shot(name, frames);
    }

    private static void takePending(Minecraft mc) {
        Shot shot = pending;
        if (shot == null || frames <= shot.frame()) return;
        Screenshot.grab(mc.gameDirectory, "smoke_" + shot.name() + ".png", mc.getMainRenderTarget(), message -> {
        });
        shots.add(shot.name());
        LocalPlayer player = mc.player;
        String where = player == null || origin == null ? "" : String.format(Locale.ROOT, " at z %.1f, %s",
                player.getZ() - origin.getZ(), RideDriver.INSTANCE.localState(player));
        note("screenshot " + shot.name() + where);
        pending = null;
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        CameraOrbit.clear();
    }

    // ---- results -----------------------------------------------------------------------------------

    private static void check(boolean ok, String message) {
        if (!ok) failures.add(message);
    }

    private static void note(String line) {
        synchronized (lines) {
            lines.add(line);
        }
        AlpineShred.LOGGER.info("[smoke] {}", line);
    }

    private static void finish(Minecraft mc) {
        stage = Stage.DONE;
        RiderTracker.previewClip(null);
        CameraOrbit.clear();
        for (KeyMapping key : new KeyMapping[]{mc.options.keyUp, mc.options.keyLeft, mc.options.keyRight,
                mc.options.keyJump, mc.options.keyShift}) {
            key.setDown(false);
        }
        List<String> out;
        synchronized (lines) {
            out = new ArrayList<>(lines);
        }
        out.add("screenshots: " + shots.size());
        List<String> failed;
        synchronized (failures) {
            failed = new ArrayList<>(failures);
        }
        failed.forEach(f -> out.add("FAIL " + f));
        out.add(failed.isEmpty() ? "RESULT PASS" : "RESULT FAIL");
        try {
            Files.write(mc.gameDirectory.toPath().resolve("alpineshred-smoke.txt"), out, StandardCharsets.UTF_8);
        } catch (IOException e) {
            AlpineShred.LOGGER.error("[smoke] could not write the result file", e);
        }
        AlpineShred.LOGGER.info("[smoke] {}", failed.isEmpty() ? "PASS" : "FAIL " + failed);
        mc.stop();
    }
}
