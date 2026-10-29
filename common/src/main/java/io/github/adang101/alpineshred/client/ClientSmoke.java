package io.github.adang101.alpineshred.client;

import dev.architectury.event.events.client.ClientTickEvent;
import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.client.feel.FeelStats;
import io.github.adang101.alpineshred.gear.GearAccess;
import io.github.adang101.alpineshred.gear.GogglesItem;
import io.github.adang101.alpineshred.gear.Lens;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The client smoke test, run in CI with {@code -Dalpineshred.smoke=true} on a virtual display
 * ({@code ./gradlew :fabric:runSmokeClient}). Inert otherwise.
 *
 * <p>It creates a flat world, builds a snow run (a staircase slope, a flat with a pop, a kicker, a
 * powder patch), straps the player in, and rides it with simulated keys: skate, carve both ways,
 * pop, launch, brake. It takes screenshots from behind, from the front and in first person, writes
 * what it saw to {@code alpineshred-smoke.txt}, and quits. A client mixin that fails to apply, a
 * crash in the feel systems, or a rider that never slides, launches or makes sound fails it.
 */
public final class ClientSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("alpineshred.smoke");
    private static final int TIMEOUT_TICKS = 20 * 300;
    /** Course layout, in blocks along +z from the start. */
    private static final int SLOPE_START = 3, SLOPE_END = 48, KICKER = 60, POWDER_FROM = 84, POWDER_TO = 92, LENGTH = 130;
    private static final int TOP = 16;

    private enum Stage {MENU, LOADING, BUILDING, RIDING, DONE}

    private static Stage stage = Stage.MENU;
    private static int ticks;
    private static int rideTicks;
    private static int stageTicks;
    private static BlockPos origin;
    private static final List<String> lines = new ArrayList<>();
    private static final List<String> failures = new ArrayList<>();
    private static final Set<RideState> states = EnumSet.noneOf(RideState.class);
    private static final Set<String> shots = new java.util.HashSet<>();
    private static double maxSpeed;
    private static boolean wasAir;
    private static int landedAt = -1;

    private ClientSmoke() {
    }

    public static void register() {
        if (!ENABLED) return;
        AlpineShred.LOGGER.info("[smoke] client smoke test armed");
        ClientTickEvent.CLIENT_POST.register(ClientSmoke::tick);
    }

    private static void tick(Minecraft mc) {
        ticks++;
        stageTicks++;
        if (stage == Stage.DONE) return;
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
                    java.util.UUID id = mc.player.getUUID();
                    server.execute(() -> build(server, id));
                    next(Stage.BUILDING);
                }
            }
            case BUILDING -> {
                if (mc.player != null && origin != null && Riders.isStrapped(mc.player) && stageTicks > 20) {
                    note("strapped in at " + mc.player.blockPosition());
                    next(Stage.RIDING);
                } else if (stageTicks > 400) {
                    failures.add("never strapped in");
                    finish(mc);
                }
            }
            case RIDING -> ride(mc);
            default -> {
            }
        }
    }

    private static void next(Stage s) {
        stage = s;
        stageTicks = 0;
        note("stage " + s);
    }

    private static void createWorld(Minecraft mc) {
        LevelSettings settings = new LevelSettings("Alpine Shred smoke test", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                true, new GameRules(), WorldDataConfiguration.DEFAULT);
        WorldOptions options = new WorldOptions(20261029L, false, false);
        mc.createWorldOpenFlows().createFreshLevel("alpineshred-smoke-" + System.currentTimeMillis(), settings, options,
                access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
    }

    /** Server thread: build the run, dress the player, put them at the top and strap them in. */
    private static void build(MinecraftServer server, java.util.UUID id) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player == null) {
            failures.add("no server player");
            return;
        }
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
        player.teleportTo(level, base.getX() + 0.5, base.getY() + TOP, base.getZ() + 1.5, 0f, 15f);
        origin = base;
        StrapCheck.Result result = StrapController.tryStrap(player);
        note("built the run at " + base + "; strap " + result);
        if (!result.ok()) failures.add("strap failed: " + result);
    }

    /** Column height of the run at {@code dz}: platform, 1-in-3 staircase, flat, kicker, flat. */
    private static double height(int dz) {
        if (dz < SLOPE_START) return TOP;
        if (dz <= SLOPE_END) return Math.max(1, TOP - Math.floor((dz - SLOPE_START) / 3.0) - 1);
        if (dz >= KICKER && dz < KICKER + 4) return 1 + 0.375 * (dz - KICKER + 1);
        return 1;
    }

    private static void ride(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) {
            failures.add("player vanished");
            finish(mc);
            return;
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

        if (rideTicks % 10 == 0) {
            note(String.format(Locale.ROOT, "t%d z%.1f y%.2f %s speed %.3f lean %.2f %s", rideTicks, z,
                    player.getY() - origin.getY(), state, speed, c == null ? 0 : c.lean(), FeelStats.summary()));
        }
        if (right && z > 18) shot(mc, "carve_right", CameraType.THIRD_PERSON_BACK);
        if (left && z > 32) shot(mc, "carve_left", CameraType.THIRD_PERSON_BACK);
        if (z > 40) shot(mc, "front", CameraType.THIRD_PERSON_FRONT);
        if (z > 44) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        if (jump) shot(mc, "charge", CameraType.THIRD_PERSON_BACK);
        boolean air = state == RideState.AIR;
        if (air && !wasAir) note("airborne at z " + String.format(Locale.ROOT, "%.1f", z));
        if (air && z > 62) shot(mc, "kicker_air", CameraType.THIRD_PERSON_BACK);
        if (!air && wasAir) landedAt = rideTicks;
        wasAir = air;
        if (landedAt > 0 && rideTicks == landedAt + 3) shot(mc, "landing_" + landedAt, CameraType.THIRD_PERSON_BACK);
        if (z > 72 && z < 80) shot(mc, "first_person", CameraType.FIRST_PERSON);
        if (z > 80) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        if (z > POWDER_FROM + 2 && z < POWDER_TO) shot(mc, "powder", CameraType.THIRD_PERSON_BACK);
        if (brake && z > 108) shot(mc, "brake", CameraType.THIRD_PERSON_BACK);

        boolean stopped = brake && speed < 0.05;
        if (stopped || z > LENGTH - 6 || rideTicks > 20 * 60) {
            hold(mc.options.keyShift, false);
            check(maxSpeed > 0.5, String.format(Locale.ROOT, "max speed %.3f, expected the slope to get over 0.5", maxSpeed));
            check(states.contains(RideState.GLIDE) || states.contains(RideState.CARVE), "never glided: " + states);
            check(states.contains(RideState.CARVE), "never carved: " + states);
            check(states.contains(RideState.AIR), "never airborne: " + states);
            check(!states.contains(RideState.WIPEOUT), "wiped out on a clean run: " + states);
            check(FeelStats.clipsPlayed > 3, "animation clips played: " + FeelStats.clipsPlayed);
            check(FeelStats.loopsStarted > 0, "no sound loops started");
            check(FeelStats.oneShotsPlayed > 0, "no pop or landing sounds");
            check(FeelStats.particlesSpawned > 0, "no particles");
            check(FeelStats.maxFovAdded > 1, "no speed field of view: " + FeelStats.maxFovAdded);
            check(FeelStats.maxRoll > 0.5, "no camera roll: " + FeelStats.maxRoll);
            note("states " + states + ", max speed " + String.format(Locale.ROOT, "%.3f", maxSpeed) + ", " + FeelStats.summary());
            finish(mc);
        }
    }

    private static void hold(KeyMapping key, boolean down) {
        KeyMapping.set(key.getDefaultKey(), down);
        key.setDown(down);
    }

    private static void shot(Minecraft mc, String name, CameraType view) {
        if (mc.options.getCameraType() != view) {
            mc.options.setCameraType(view);
            return; // let a frame render from the new view first
        }
        if (!shots.add(name)) return;
        Screenshot.grab(mc.gameDirectory, "smoke_" + name + ".png", mc.getMainRenderTarget(), message -> {
        });
        note("screenshot " + name);
    }

    private static void check(boolean ok, String message) {
        if (!ok) failures.add(message);
    }

    private static void note(String line) {
        lines.add(line);
        AlpineShred.LOGGER.info("[smoke] {}", line);
    }

    private static void finish(Minecraft mc) {
        stage = Stage.DONE;
        for (KeyMapping key : new KeyMapping[]{mc.options.keyUp, mc.options.keyLeft, mc.options.keyRight,
                mc.options.keyJump, mc.options.keyShift}) {
            key.setDown(false);
        }
        List<String> out = new ArrayList<>(lines);
        failures.forEach(f -> out.add("FAIL " + f));
        out.add(failures.isEmpty() ? "RESULT PASS" : "RESULT FAIL");
        try {
            Files.write(mc.gameDirectory.toPath().resolve("alpineshred-smoke.txt"), out, StandardCharsets.UTF_8);
        } catch (IOException e) {
            AlpineShred.LOGGER.error("[smoke] could not write the result file", e);
        }
        AlpineShred.LOGGER.info("[smoke] {}", failures.isEmpty() ? "PASS" : "FAIL " + failures);
        mc.stop();
    }
}
