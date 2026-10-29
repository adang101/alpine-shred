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
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
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
 */
public final class ClientSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("alpineshred.smoke");
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

    private enum Stage {MENU, LOADING, BUILDING, RIDE_BOARD, GALLERY_BOARD, SWAP, RIDE_SKIS, GALLERY_SKIS, DONE}

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
                    onServer(mc, ClientSmoke::build);
                    next(Stage.BUILDING);
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
                access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
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
        GearAccess.current().set(player, GearAccess.Slot.BOARD, new ItemStack(ModItems.SNOWBOARD.get()));
        GearAccess.current().set(player, GearAccess.Slot.GOGGLES, GogglesItem.withLens(new ItemStack(ModItems.GOGGLES.get()), Lens.AMBER));
        GearAccess.current().set(player, GearAccess.Slot.MITTS, new ItemStack(ModItems.MITTS.get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.SNOWBOARD_BOOTS.get()));
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.SKI_HELMET.get()));
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        origin = base;
        toTop(player);
        StrapCheck.Result result = StrapController.tryStrap(player);
        note("built the run at " + base + "; strap " + result);
        if (!result.ok()) failures.add("strap failed: " + result);
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
