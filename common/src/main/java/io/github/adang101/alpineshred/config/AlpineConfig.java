package io.github.adang101.alpineshred.config;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Both config files (design doc section 18): {@code config/alpineshred-common.json} is
 * server-authoritative and synced to clients at login; {@code config/alpineshred-client.json} is
 * local. Every key from the doc is declared here even when its feature ships in a later phase, so
 * the file layout is stable from the first release.
 */
public final class AlpineConfig {
    public static final int SCHEMA_VERSION = 1;

    // =============================== common (server) ===============================
    public static final ConfigSpec COMMON = new ConfigSpec("alpineshred-common.json", SCHEMA_VERSION);

    public static final ConfigSpec.BoolEntry MOVEMENT_ENABLED = COMMON.bool("movement.enabled", true);
    public static final ConfigSpec.DoubleEntry MOVEMENT_MAX_SPEED_MUL = COMMON.decimal("movement.maxSpeedMul", 1.0, 0.1, 3.0);
    public static final ConfigSpec.BoolEntry MOVEMENT_SCRAPE_ENABLED = COMMON.bool("movement.scrapeEnabled", true);
    /** {@code auto} = strict on dedicated servers, relaxed in singleplayer. */
    public static final ConfigSpec.ChoiceEntry MOVEMENT_VALIDATION_STRICT = COMMON.choice("movement.validation.strict", "auto", "auto", "true", "false");
    public static final ConfigSpec.StringListEntry MOVEMENT_DISABLE_WITH_MODS = COMMON.strings("movement.disableWithMods", List.of());

    public static final ConfigSpec.BoolEntry TRICK_ENABLED = COMMON.bool("trick.enabled", true);
    public static final ConfigSpec.IntEntry TRICK_MAX_COMBO_SCORE = COMMON.integer("trick.maxComboScore", 50000, 0, 10_000_000);
    public static final ConfigSpec.ChoiceEntry TRICK_BROADCAST = COMMON.choice("trick.broadcast", "self", "none", "self", "nearby", "all");

    public static final ConfigSpec.DoubleEntry GEAR_DURABILITY_MUL = COMMON.decimal("gear.durabilityMul", 1.0, 0.0, 10.0);
    public static final ConfigSpec.BoolEntry RAIL_OXIDATION = COMMON.bool("rail.oxidation", true);

    public static final ConfigSpec.BoolEntry WORLDGEN_TERRAIN_PARKS = COMMON.bool("worldgen.terrainParks", true);
    public static final ConfigSpec.BoolEntry WORLDGEN_GROOMED_RUNS = COMMON.bool("worldgen.groomedRuns", true);
    public static final ConfigSpec.BoolEntry WORLDGEN_RESORTS = COMMON.bool("worldgen.resorts", true);
    public static final ConfigSpec.BoolEntry WORLDGEN_SNOW_FEATURES = COMMON.bool("worldgen.snowFeatures", true);
    public static final ConfigSpec.DoubleEntry WORLDGEN_FREQUENCY_MUL = COMMON.decimal("worldgen.frequencyMul", 1.0, 0.1, 10.0);

    public static final ConfigSpec.IntEntry LIFT_MAX_LINES_PER_LEVEL = COMMON.integer("lift.maxLinesPerLevel", 64, 1, 1024);
    public static final ConfigSpec.IntEntry LIFT_MAX_HEADS = COMMON.integer("lift.maxHeads", 48, 1, 256);
    public static final ConfigSpec.IntEntry LIFT_MAX_SPAN = COMMON.integer("lift.maxSpan", 64, 8, 256);
    public static final ConfigSpec.IntEntry LIFT_MAX_LENGTH = COMMON.integer("lift.maxLength", 1200, 32, 8192);
    public static final ConfigSpec.IntEntry LIFT_SEATS = COMMON.integer("lift.seats", 2, 1, 4);
    public static final ConfigSpec.IntEntry LIFT_SPACING = COMMON.integer("lift.spacing", 24, 12, 48);
    public static final ConfigSpec.DoubleListEntry LIFT_SPEED_LEVELS = COMMON.decimals("lift.speedLevels", List.of(1.5, 2.5, 4.0), 0.1, 10.0, 1, 8);
    public static final ConfigSpec.DoubleEntry LIFT_MAX_BOARD_SPEED = COMMON.decimal("lift.maxBoardSpeed", 3.5, 0.0, 10.0);
    public static final ConfigSpec.BoolEntry LIFT_RUN_UNLOADED = COMMON.bool("lift.runUnloaded", true);
    public static final ConfigSpec.BoolEntry LIFT_REQUIRE_PASS = COMMON.bool("lift.requirePass", false);
    public static final ConfigSpec.BoolEntry LIFT_DISMOUNT_ANYWHERE = COMMON.bool("lift.dismountAnywhere", false);
    public static final ConfigSpec.BoolEntry LIFT_MOBS_ALLOWED = COMMON.bool("lift.mobsAllowed", true);
    public static final ConfigSpec.DoubleEntry LIFT_MAX_PASSENGER_WIDTH = COMMON.decimal("lift.maxPassengerWidth", 1.2, 0.1, 4.0);
    public static final ConfigSpec.DoubleEntry LIFT_MAX_PASSENGER_HEIGHT = COMMON.decimal("lift.maxPassengerHeight", 2.4, 0.1, 6.0);
    public static final ConfigSpec.BoolEntry LIFT_PETS_AUTO_BOARD = COMMON.bool("lift.petsAutoBoard", true);
    public static final ConfigSpec.IntEntry LIFT_SYNC_RADIUS = COMMON.integer("lift.syncRadius", 256, 32, 1024);
    public static final ConfigSpec.DoubleEntry LIFT_KINETIC_MAX_SPEED = COMMON.decimal("lift.kineticMaxSpeed", 4.0, 0.1, 10.0);
    /** Section 13 mentions {@code drive.needsRedstone} without a default; on by default here. */
    public static final ConfigSpec.BoolEntry LIFT_DRIVE_NEEDS_REDSTONE = COMMON.bool("lift.driveNeedsRedstone", true);

    public static final ConfigSpec.BoolEntry DESIGN_ENABLED = COMMON.bool("design.enabled", true);
    public static final ConfigSpec.IntEntry DESIGN_MAX_COMPRESSED_BYTES = COMMON.integer("design.maxCompressedBytes", 8192, 512, 16384);
    public static final ConfigSpec.IntEntry DESIGN_SAVE_COST_COUNT = COMMON.integer("design.saveCostCount", 1, 0, 64);
    public static final ConfigSpec.IntEntry DESIGN_MIN_PERMISSION_LEVEL = COMMON.integer("design.minPermissionLevel", 0, 0, 4);
    public static final ConfigSpec.IntEntry DESIGN_SAVE_COOLDOWN_TICKS = COMMON.integer("design.saveCooldownTicks", 40, 0, 1200);

    // ================================== client ==================================
    public static final ConfigSpec CLIENT = new ConfigSpec("alpineshred-client.json", SCHEMA_VERSION);

    public static final ConfigSpec.ChoiceEntry CLIENT_STEER_MODE = CLIENT.choice("client.steerMode", "ride", "ride", "keys");
    public static final ConfigSpec.ChoiceEntry CLIENT_STANCE = CLIENT.choice("client.stance", "regular", "regular", "goofy");
    public static final ConfigSpec.DoubleEntry CLIENT_FOV_EFFECT = CLIENT.decimal("client.fovEffect", 1.0, 0.0, 1.0);
    public static final ConfigSpec.BoolEntry CLIENT_REDUCE_MOTION = CLIENT.bool("client.reduceMotion", false);
    public static final ConfigSpec.BoolEntry CLIENT_FLASH_SAFE = CLIENT.bool("client.flashSafe", false);
    public static final ConfigSpec.BoolEntry CLIENT_HUD_SPEED = CLIENT.bool("client.hudSpeed", false);
    public static final ConfigSpec.BoolEntry CLIENT_CARVE_TRAILS = CLIENT.bool("client.carveTrails", true);
    public static final ConfigSpec.BoolEntry CLIENT_GOGGLES_OVERLAY = CLIENT.bool("client.gogglesOverlay", true);
    public static final ConfigSpec.IntEntry CLIENT_GEAR_RENDER_DISTANCE = CLIENT.integer("client.gearRenderDistance", 48, 8, 256);
    public static final ConfigSpec.BoolEntry CLIENT_AUTO_BOARD = CLIENT.bool("client.autoBoard", true);
    public static final ConfigSpec.DoubleEntry CLIENT_HUD_SCALE = CLIENT.decimal("client.hudScale", 1.0, 0.5, 3.0);
    public static final ConfigSpec.BoolEntry CLIENT_SHOW_CUSTOM_DESIGNS = CLIENT.bool("client.showCustomDesigns", true);
    public static final ConfigSpec.IntEntry CLIENT_DESIGN_CACHE_SIZE = CLIENT.integer("client.designCacheSize", 64, 8, 512);
    public static final ConfigSpec.BoolEntry CLIENT_DESIGN_GRID = CLIENT.bool("client.designGrid", true);
    public static final ConfigSpec.BoolEntry CLIENT_HINTS = CLIENT.bool("client.hints", true);
    /** Hint ids already shown on this client, so each shows once (section 7, "Teaching the controls"). */
    public static final ConfigSpec.StringListEntry CLIENT_HINTS_SEEN = CLIENT.strings("client.hintsSeen", List.of());

    private AlpineConfig() {
    }

    public static void loadCommon(Path configDir, Consumer<String> warn) {
        COMMON.load(configDir, warn);
    }

    public static void loadClient(Path configDir, Consumer<String> warn) {
        CLIENT.load(configDir, warn);
    }

    /** Resolves {@code movement.validation.strict}: {@code auto} is strict only on dedicated servers. */
    public static boolean strictValidation(boolean dedicatedServer) {
        return switch (MOVEMENT_VALIDATION_STRICT.get()) {
            case "true" -> true;
            case "false" -> false;
            default -> dedicatedServer;
        };
    }
}
