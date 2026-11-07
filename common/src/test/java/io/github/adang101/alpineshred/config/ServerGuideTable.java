package io.github.adang101.alpineshred.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders the config tables of {@code docs/server-guide.md} from {@link AlpineConfig}, so the key names, defaults and
 * ranges cannot drift from the code. The one-line notes (what a key does and what it costs) are written here, and
 * {@link ServerGuideTest} fails when a key has no note or a note names no key.
 */
final class ServerGuideTable {
    static final String COMMON_START = "<!-- config-table:common:start -->", COMMON_END = "<!-- config-table:common:end -->";
    static final String CLIENT_START = "<!-- config-table:client:start -->", CLIENT_END = "<!-- config-table:client:end -->";

    static final Map<String, String> NOTES = new LinkedHashMap<>();

    static {
        // movement
        note("movement.enabled", "Master switch for riding. Off: nobody can strap in (clients learn it through `config_sync`). No cost.");
        note("movement.maxSpeedMul", "Scales the rider speed cap, on the server's validator and on clients (synced). No cost.");
        note("movement.scrapeEnabled", "Whether riders slide over scrape surfaces (bare ground) above the minimum slide speed; off, they walk there (synced). No cost.");
        note("movement.validation.strict", "`auto` enforces on a dedicated server and only logs \"Would correct\" in singleplayer. `true` and `false` force it. Enforced: three violations in 40 ticks put the rider back at the last valid position. No cost.");
        note("movement.disableWithMods", "Mod ids; riding is off while any of them is loaded (synced as `movement.enabled`). No cost.");
        // trick
        note("trick.enabled", "Off: no tricks are scored for strapped riders. No cost.");
        note("trick.maxComboScore", "Cap on one combo's total score. No cost.");
        note("trick.broadcast", "Who sees a landed trick's result: `none`, `self`, `nearby` (players within 64 blocks in the level) or `all`. `nearby` and `all` send one small packet per trick to each other player.");
        // gear, rail
        note("gear.durabilityMul", "Multiplies board and ski durability wear and bail damage; 0 turns durability wear off (edge and wax still wear). No cost.");
        note("rail.oxidation", "Off: copper rails stop weathering (random ticks do nothing). Waxing still works. Saves a few random ticks.");
        // worldgen
        note("worldgen.terrainParks", "Terrain parks generate in new chunks. Off skips them. About 3 ms a start when on.");
        note("worldgen.groomedRuns", "Groomed runs generate in new chunks. Off skips them. Run planning samples terrain noise: the most expensive start type besides resorts.");
        note("worldgen.resorts", "Ski resorts (lodge, groomed run and lift) generate in new chunks. About 133 to 138 ms a start on CI's two cores (budget 150 ms).");
        note("worldgen.snowFeatures", "Snow features (small park pieces) generate in new chunks. About 3 ms a start when on.");
        note("worldgen.frequencyMul", "Keeps this share of starts of every type and skips the rest, by each start's own seed; 1.0 keeps all. It cannot add starts: spacing belongs to the structure sets (a datapack may change them).");
        // lift
        note("lift.maxLinesPerLevel", "Lift lines per dimension; the linker refuses more. Cost grows with line count (the manager measured 0.003 to 0.005 ms a tick for five lines).");
        note("lift.maxHeads", "Most tower heads on one line (the linker refuses more).");
        note("lift.maxSpan", "Longest horizontal gap between two consecutive nodes of a line, in blocks (the linker refuses more; a resort's generated lift obeys it too).");
        note("lift.maxLength", "Longest loop of one line, in blocks. Chair count is the loop length over `lift.spacing`, so this bounds a line's chairs.");
        note("lift.seats", "Seats on each chair (synced). Read live, so a reload changes every chair.");
        note("lift.spacing", "Blocks between chairs. More chairs on a line cost more per tick (5 microseconds a chair budget). Read when a line is created or loaded, so lines already in memory keep the old spacing until a restart.");
        note("lift.speedLevels", "Speeds, in blocks per second, that a drive station cycles through when right-clicked. The second one is the default.");
        note("lift.maxBoardSpeed", "A chair is refused when its line runs faster than this, in blocks per second (synced; the client's board prompt predicts it).");
        note("lift.runUnloaded", "On: a line keeps moving while none of its nodes is in a loaded chunk. Off: it freezes until a node's chunk loads. See the lifts section.");
        note("lift.requirePass", "On: boarding needs a lift pass anywhere in the player's inventory or offhand.");
        note("lift.dismountAnywhere", "Off: a rider may sneak off only with the bar up, within 4 blocks of the ground, or on a faulted line. On: any time (synced).");
        note("lift.mobsAllowed", "Whether mobs may ride (they must also fit the size limits and not be in `#alpineshred:lift_blocked`). Players always may.");
        note("lift.maxPassengerWidth", "Widest mob bounding box that may ride, in blocks.");
        note("lift.maxPassengerHeight", "Tallest mob bounding box that may ride, in blocks.");
        note("lift.petsAutoBoard", "Tamed pets within 8 blocks of a boarding owner board the same chair, while seats are free. Needs `lift.mobsAllowed`.");
        note("lift.syncRadius", "A line is sent to a player when any of its nodes is within this many blocks horizontally. Larger radius means more lines resent (every 100 ticks) to each player.");
        note("lift.kineticMaxSpeed", "Create only: the cap on a kinetic drive's line speed, in blocks per second (rpm / 64 up to this).");
        note("lift.kineticStress", "Create only: stress units per rpm the kinetic drive takes from the network (4 gives 1024 su at 256 rpm).");
        note("lift.driveNeedsRedstone", "On: a drive station runs only with a redstone signal. Off: it always runs. A kinetic drive ignores it.");
        // design
        note("design.enabled", "Off: the Paint Bench refuses every save and says so.");
        note("design.maxCompressedBytes", "Largest design a save or share-code import may be, in bytes, both deflated faces together. Bigger designs cost item NBT and sync bandwidth.");
        note("design.saveCostCount", "Dye items a save consumes (any dye in `#alpineshred:design_cost`); 0 makes saves free. Creative players never pay.");
        note("design.minPermissionLevel", "Permission level a player needs to save a design: 0 everyone, 2 operators.");
        note("design.saveCooldownTicks", "Ticks a player must wait between saves. No cost.");
        // client
        note("client.steerMode", "`ride`: steer by looking and leaning; `keys`: steer with the movement keys.");
        note("client.stance", "Which foot leads on a board: `regular` or `goofy`.");
        note("client.fovEffect", "Strength of the speed field-of-view effect: 0 off, 1 full.");
        note("client.reduceMotion", "Accessibility option: reduces camera and screen motion effects.");
        note("client.flashSafe", "Accessibility option: tones down flashing screen effects.");
        note("client.hudSpeed", "Shows a speed read-out in blocks per second while strapped in.");
        note("client.carveTrails", "Snow trail particles behind a carving rider.");
        note("client.gogglesOverlay", "Tints the first-person view with the worn goggles' lens.");
        note("client.gearRenderDistance", "Other players' board, ski, goggle, mitt and pole models are drawn within this many blocks. A lower value saves frame time in crowds.");
        note("client.autoBoard", "Board a chair automatically when riding up to one.");
        note("client.hudScale", "Scale of the riding HUD.");
        note("client.showCustomDesigns", "Draw other players' painted designs; off draws the default look for others.");
        note("client.designCacheSize", "Most design textures kept in memory (least recently used go first).");
        note("client.designGrid", "The paint editor starts with its grid on.");
        note("client.hints", "Show control hints.");
        note("client.hintsSeen", "Hint ids already shown. The mod writes this; leave it alone.");
    }

    private static void note(String key, String text) {
        if (NOTES.put(key, text) != null) throw new IllegalStateException("duplicate note " + key);
    }

    static String table(ConfigSpec spec) {
        StringBuilder out = new StringBuilder("| Key | Default | Allowed | What it does, and what it costs |\n| --- | --- | --- | --- |\n");
        for (ConfigSpec.Entry<?> entry : spec.entries()) {
            String note = NOTES.get(entry.path);
            out.append("| `").append(entry.path).append("` | `").append(show(entry.defaultValue)).append("` | ")
                    .append(allowed(entry)).append(" | ").append(note == null ? "(no note)" : note).append(" |\n");
        }
        return out.toString();
    }

    private static String allowed(ConfigSpec.Entry<?> entry) {
        if (entry instanceof ConfigSpec.BoolEntry) return "`true`, `false`";
        if (entry instanceof ConfigSpec.IntEntry i) return i.min + " to " + i.max;
        if (entry instanceof ConfigSpec.DoubleEntry d) return show(d.min) + " to " + show(d.max);
        if (entry instanceof ConfigSpec.ChoiceEntry c) return String.join(", ", c.allowed.stream().map(s -> "`" + s + "`").toList());
        if (entry instanceof ConfigSpec.DoubleListEntry l) {
            return l.minSize + " to " + l.maxSize + " values, each " + show(l.min) + " to " + show(l.max);
        }
        if (entry instanceof ConfigSpec.StringListEntry) return "list of strings";
        throw new IllegalStateException("unknown entry type " + entry.path);
    }

    private static String show(Object value) {
        if (value instanceof List<?> list) return list.toString();
        return String.valueOf(value);
    }
}
