package io.github.adang101.alpineshred.fabric.datagen;

import io.github.adang101.alpineshred.registry.ModBlocks;
import io.github.adang101.alpineshred.registry.ModItems;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;

/** {@code en_us.json} (design doc section 18, "Language"). Keys follow section 4's conventions. */
public final class AlpineLangProvider extends FabricLanguageProvider {
    public AlpineLangProvider(FabricDataOutput output) {
        super(output);
    }

    @Override
    public void generateTranslations(TranslationBuilder t) {
        // ---- items and blocks ----
        t.add(ModItems.SNOWBOARD.get(), "Snowboard");
        t.add(ModItems.PARK_SNOWBOARD.get(), "Park Snowboard");
        t.add(ModItems.FREERIDE_SNOWBOARD.get(), "Freeride Snowboard");
        t.add(ModItems.PRO_SNOWBOARD.get(), "Pro Snowboard");
        t.add(ModItems.SKIS.get(), "Skis");
        t.add(ModItems.CARVING_SKIS.get(), "Carving Skis");
        t.add(ModItems.POWDER_SKIS.get(), "Powder Skis");
        t.add(ModItems.PARK_SKIS.get(), "Park Skis");
        t.add(ModItems.PRO_SKIS.get(), "Pro Skis");
        t.add(ModItems.SKI_BOOTS.get(), "Ski Boots");
        t.add(ModItems.SNOWBOARD_BOOTS.get(), "Snowboard Boots");
        t.add(ModItems.SKI_HELMET.get(), "Ski Helmet");
        t.add(ModItems.GOGGLES.get(), "Goggles");
        t.add(ModItems.MITTS.get(), "Mitts");
        t.add(ModItems.SKI_POLES.get(), "Ski Poles");
        t.add(ModItems.CARBON_SKI_POLES.get(), "Carbon Ski Poles");
        t.add(ModItems.BOARD_WAX.get(), "Board Wax");
        t.add(ModItems.EDGE_TUNER.get(), "Edge Tuner");
        t.add(ModItems.PARK_WRENCH.get(), "Park Wrench");
        t.add(ModItems.LIFT_LINKER.get(), "Lift Linker");
        t.add(ModItems.LIFT_PASS.get(), "Lift Pass");
        t.add(ModBlocks.GROOMED_SNOW.get(), "Groomed Snow");
        t.add(ModBlocks.PIPE_RAIL.get(), "Pipe Rail");
        t.add(ModBlocks.FLAT_RAIL.get(), "Flat Rail");
        String[] stages = {"", "Exposed ", "Weathered ", "Oxidized "};
        for (int i = 0; i < stages.length; i++) {
            t.add(ModBlocks.COPPER_RAILS.get(i).get(), stages[i] + "Copper Pipe Rail");
            t.add(ModBlocks.WAXED_COPPER_RAILS.get(i).get(), "Waxed " + stages[i] + "Copper Pipe Rail");
        }
        t.add(ModBlocks.RAIL_POST.get(), "Rail Post");
        t.add(ModBlocks.WOOD_BOX.get(), "Wood Box");
        t.add(ModBlocks.METAL_BOX.get(), "Metal Box");
        t.add(ModBlocks.PLASTIC_BOX.get(), "Plastic Box");
        t.add(ModBlocks.PARK_RAMP.get(), "Park Ramp");
        t.add(ModBlocks.QUARTER_PIPE.get(), "Quarter Pipe");
        t.add(ModBlocks.PIPE_COPING.get(), "Pipe Coping");
        t.add(ModBlocks.SLOPE_MARKER_GREEN.get(), "Green Slope Marker");
        t.add(ModBlocks.SLOPE_MARKER_BLUE.get(), "Blue Slope Marker");
        t.add(ModBlocks.SLOPE_MARKER_BLACK.get(), "Black Slope Marker");
        t.add(ModBlocks.PARK_FLAG.get(), "Park Flag");
        t.add(ModBlocks.START_GATE.get(), "Start Gate");
        t.add("itemGroup.alpineshred.main", "Alpine Shred");

        // ---- tooltips ----
        t.add("tooltip.alpineshred.gear.kind.board", "Snowboard");
        t.add("tooltip.alpineshred.gear.kind.skis", "Skis");
        t.add("tooltip.alpineshred.gear.stats", "Speed %s · Grip %s · Pop %s");
        t.add("tooltip.alpineshred.gear.stats2", "Powder %s · Landing %s");
        t.add("tooltip.alpineshred.gear.wax", "Waxed: %s blocks left");
        t.add("tooltip.alpineshred.gear.unwaxed", "Unwaxed");
        t.add("tooltip.alpineshred.gear.edge", "Edge: %s%%");
        t.add("tooltip.alpineshred.gear.equip_hint", "Wear in the snow gear slot, then press %s to strap in");
        t.add("tooltip.alpineshred.ski_boots", "Binds with skis");
        t.add("tooltip.alpineshred.snowboard_boots", "Binds with snowboards");
        t.add("tooltip.alpineshred.ski_helmet", "Cuts wipeout damage by 40%");
        t.add("tooltip.alpineshred.goggles.lens", "Lens: %s");
        t.add("tooltip.alpineshred.goggles", "Clears snow spray and whiteout. Craft with a dye to change the lens.");
        t.add("tooltip.alpineshred.mitts", "+10% pole push and grab score");
        t.add("tooltip.alpineshred.ski_poles", "Hold while skiing and use to push off");
        t.add("tooltip.alpineshred.board_wax", "Craft with a board or skis: +8% speed on snow for 3000 blocks");
        t.add("tooltip.alpineshred.edge_tuner", "Use with a board or skis in your off hand to sharpen the edges");
        t.add("tooltip.alpineshred.park_wrench", "Use on a rail to cycle its shape, sneak to turn it. Ramps: slope, sneak for the lip. Boxes: height, sneak to turn.");
        t.add("tooltip.alpineshred.quarter_pipe", "Places a whole radius 3 quarter pipe rising the way you face; sneak for radius 5");
        t.add("tooltip.alpineshred.rail", "Place end to end: rails connect, turn and follow the ground. Sneak to force a straight piece.");
        t.add("tooltip.alpineshred.lift_linker", "Links lift stations and tower heads");
        t.add("tooltip.alpineshred.lift_pass", "Lets you ride lifts that require a pass");
        t.add("lens.alpineshred.clear", "Clear");
        t.add("lens.alpineshred.amber", "Amber");
        t.add("lens.alpineshred.blue_mirror", "Blue Mirror");
        t.add("lens.alpineshred.black", "Black");

        // ---- messages ----
        t.add("message.alpineshred.strap.in", "Strapped in");
        t.add("message.alpineshred.strap.out", "Unstrapped");
        t.add("message.alpineshred.strap.disabled", "Riding is disabled on this server");
        t.add("message.alpineshred.strap.spectator", "Spectators can't strap in");
        t.add("message.alpineshred.strap.vehicle", "Can't strap in while riding something");
        t.add("message.alpineshred.strap.fluid", "Can't strap in while in water or lava");
        t.add("message.alpineshred.strap.elytra", "Can't strap in while gliding");
        t.add("message.alpineshred.strap.no_gear", "Put a snowboard or skis in your snow gear slot first");
        t.add("message.alpineshred.strap.boots_board", "Snowboards need snowboard boots");
        t.add("message.alpineshred.strap.boots_skis", "Skis need ski boots");
        t.add("message.alpineshred.unstrap.fluid", "Unstrapped: you hit the water");
        t.add("message.alpineshred.unstrap.vehicle", "Unstrapped to ride");
        t.add("message.alpineshred.unstrap.gear_removed", "Unstrapped: gear removed");
        t.add("message.alpineshred.unstrap.spectator", "Unstrapped");
        t.add("message.alpineshred.unstrap.elytra", "Unstrapped to glide");
        t.add("message.alpineshred.unstrap.disabled", "Riding was disabled on this server");
        t.add("message.alpineshred.stance.regular", "Stance: regular");
        t.add("message.alpineshred.stance.goofy", "Stance: goofy");
        t.add("message.alpineshred.hint.steer", "Look to steer, A or D to dig an edge, sneak to brake");
        t.add("message.alpineshred.tuner.no_gear", "Hold a board or skis in your off hand");
        t.add("message.alpineshred.tuner.sharp", "Edges are already sharp");
        t.add("message.alpineshred.tuner.done", "Edges sharpened");
        t.add("message.alpineshred.ride.corrected", "Position corrected by the server");
        t.add("message.alpineshred.pipe.no_room", "No room for a radius %s quarter pipe here");
        t.add("message.alpineshred.wrench.only_option", "This is the only piece that fits here");
        t.add("message.alpineshred.wrench.rail", "Rail: %s, %s");
        t.add("message.alpineshred.wrench.slope", "Ramp slope: %s");
        t.add("message.alpineshred.wrench.lip_on", "Ramp lip on");
        t.add("message.alpineshred.wrench.lip_off", "Ramp lip off");
        t.add("shape.alpineshred.flat", "flat");
        t.add("shape.alpineshred.rise_a", "rise (lower half)");
        t.add("shape.alpineshred.rise_b", "rise (upper half)");
        t.add("shape.alpineshred.curve", "curve");
        t.add("direction.alpineshred.north", "north");
        t.add("direction.alpineshred.east", "east");
        t.add("direction.alpineshred.south", "south");
        t.add("direction.alpineshred.west", "west");
        t.add("slope.alpineshred.quarter", "quarter (14°)");
        t.add("slope.alpineshred.half", "half (27°)");
        t.add("slope.alpineshred.full", "full (45°)");
        t.add("message.alpineshred.protocol_mismatch", "Alpine Shred on this server speaks protocol %s, this client %s: riding is off until versions match");

        // ---- tricks (section 8) ----
        t.add("trick.alpineshred.spin", "%s");
        t.add("trick.alpineshred.flip_front", "Front flip");
        t.add("trick.alpineshred.flip_back", "Back flip");
        t.add("trick.alpineshred.flip_front_n", "%sx front flip");
        t.add("trick.alpineshred.flip_back_n", "%sx back flip");
        t.add("trick.alpineshred.grab_indy", "Indy");
        t.add("trick.alpineshred.grab_mute", "Mute");
        t.add("trick.alpineshred.grab_nose", "Nose grab");
        t.add("trick.alpineshred.grab_tail", "Tail grab");
        t.add("trick.alpineshred.grab_method", "Method");
        t.add("trick.alpineshred.grind_5050", "50-50");
        t.add("trick.alpineshred.grind_nose", "Nose grind");
        t.add("trick.alpineshred.grind_tail", "Tail grind");
        t.add("trick.alpineshred.grind_boardslide", "Boardslide");
        t.add("trick.alpineshred.slide", "%s (box)");
        t.add("trick.alpineshred.switch", "Switch %s");
        t.add("trick.alpineshred.switch_up", "Switch-up");
        t.add("trick.alpineshred.spin_in", "Spin in");
        t.add("trick.alpineshred.spin_out", "Spin out");
        t.add("trick.alpineshred.air", "Air time");
        t.add("trick.alpineshred.height", "Height");
        t.add("trick.alpineshred.popup.bank", "%s banked");
        t.add("trick.alpineshred.popup.bail", "Bail!");
        t.add("trick.alpineshred.quality.perfect", "Perfect landing");
        t.add("trick.alpineshred.quality.clean", "Clean");
        t.add("trick.alpineshred.quality.sketchy", "Sketchy landing");
        t.add("trick.alpineshred.quality.bail", "Bail!");

        // ---- statistics ----
        t.add("stat.alpineshred.trick_score", "Trick Score");
        t.add("stat.alpineshred.tricks_landed", "Tricks Landed");
        t.add("stat.alpineshred.air_time", "Time in the Air");
        t.add("stat.alpineshred.grind_distance", "Distance Ground on Rails and Boxes");
        t.add("stat.alpineshred.lift_rides", "Lift Rides");
        t.add("stat.alpineshred.ride_distance", "Distance Ridden Strapped In");
        t.add("stat.alpineshred.best_combo", "Best Combo");

        // ---- advancements ----
        t.add("advancements.alpineshred.root.title", "Alpine Shred");
        t.add("advancements.alpineshred.root.description", "Get your hands on a snowboard or a pair of skis");
        t.add("advancements.alpineshred.first_run.title", "First Run");
        t.add("advancements.alpineshred.first_run.description", "Slide 100 blocks strapped in");
        t.add("advancements.alpineshred.waxed_up.title", "Waxed Up");
        t.add("advancements.alpineshred.waxed_up.description", "Wax a board or skis");
        t.add("advancements.alpineshred.first_grind.title", "Rail Rider");
        t.add("advancements.alpineshred.first_grind.description", "Grind 5 blocks of rail or box");
        t.add("advancements.alpineshred.first_air.title", "Catching Air");
        t.add("advancements.alpineshred.first_air.description", "Land a trick worth 100 points");
        t.add("advancements.alpineshred.spin_cycle.title", "Spin Cycle");
        t.add("advancements.alpineshred.spin_cycle.description", "Land a 360");

        // ---- commands ----
        t.add("commands.alpineshred.stats", "%s: best combo %s, total score %s, tricks landed %s, %s s in the air, %s blocks ground, %s blocks ridden, %s lift rides");
        t.add("commands.alpineshred.strap.done", "Changed strap state for %s player(s)");
        t.add("commands.alpineshred.strap.failed", "Could not strap in: %s");
        t.add("commands.alpineshred.config.reloaded", "Alpine Shred config reloaded (%s warning(s), see log)");
        t.add("commands.alpineshred.tuning.reloaded", "Alpine Shred tuning, surfaces and gear stats reloaded");
        t.add("commands.alpineshred.debug.ride.on", "Ride debug overlay on");
        t.add("commands.alpineshred.debug.ride.off", "Ride debug overlay off");

        // ---- HUD ----
        t.add("hud.alpineshred.gear", "Wax %s  Edge %s");

        // ---- subtitles ----
        t.add("subtitles.alpineshred.ride.glide", "Snow hisses");
        t.add("subtitles.alpineshred.ride.scrape", "Board scrapes");
        t.add("subtitles.alpineshred.ride.carve", "Edge carves");
        t.add("subtitles.alpineshred.ride.brake", "Rider skids");
        t.add("subtitles.alpineshred.ride.pop", "Rider pops");
        t.add("subtitles.alpineshred.ride.land", "Rider lands");
        t.add("subtitles.alpineshred.ride.spin", "Rider spins");
        t.add("subtitles.alpineshred.ride.wipeout", "Rider wipes out");
        t.add("subtitles.alpineshred.ride.pole_push", "Pole plants");
        t.add("subtitles.alpineshred.grind.rail_metal", "Rail grinds");
        t.add("subtitles.alpineshred.grind.rail_copper", "Copper rail grinds");
        t.add("subtitles.alpineshred.grind.box_wood", "Board slides on wood");
        t.add("subtitles.alpineshred.grind.box_metal", "Board slides on metal");
        t.add("subtitles.alpineshred.grind.box_plastic", "Board slides on plastic");
        t.add("subtitles.alpineshred.grind.catch", "Rail catches");
        t.add("subtitles.alpineshred.trick.combo_bank", "Combo banked");
        t.add("subtitles.alpineshred.trick.bail", "Rider bails");
        t.add("subtitles.alpineshred.gear.strap_in", "Bindings click");
        t.add("subtitles.alpineshred.gear.strap_out", "Bindings release");
        t.add("subtitles.alpineshred.gear.wax_apply", "Wax rubs on");
        t.add("subtitles.alpineshred.gear.tune", "Edge file scrapes");

        // ---- key bindings ----
        t.add("key.categories.alpineshred", "Alpine Shred");
        t.add("key.alpineshred.strap", "Strap In / Out");
        t.add("key.alpineshred.grab_indy", "Indy Grab");
        t.add("key.alpineshred.grab_mute", "Mute Grab");
        t.add("key.alpineshred.grab_nose", "Nose Grab");
        t.add("key.alpineshred.grab_tail", "Tail Grab");
        t.add("key.alpineshred.stance", "Toggle Stance");

        // ---- designs (section 22) ----
        t.add(ModBlocks.PAINT_BENCH.get(), "Paint Bench");
        t.add("container.alpineshred.paint_bench", "Paint Bench");
        t.add("gui.alpineshred.paint_bench.paint", "Paint");
        t.add("gui.alpineshred.paint_bench.gear", "Gear");
        t.add("gui.alpineshred.paint_bench.dye", "Dye");
        t.add("gui.alpineshred.design_editor", "Paint Editor");
        t.add("gui.alpineshred.design_editor.tool.pencil", "Pencil (B)");
        t.add("gui.alpineshred.design_editor.tool.eraser", "Eraser (E)");
        t.add("gui.alpineshred.design_editor.tool.fill", "Fill (F)");
        t.add("gui.alpineshred.design_editor.tool.line", "Line (L)");
        t.add("gui.alpineshred.design_editor.tool.rectangle", "Rectangle (R)");
        t.add("gui.alpineshred.design_editor.tool.gradient", "Gradient (G): drag from A to B");
        t.add("gui.alpineshred.design_editor.tool.pattern", "Pattern fill (P)");
        t.add("gui.alpineshred.design_editor.tool.eyedropper", "Eyedropper (I or middle click)");
        t.add("gui.alpineshred.design_editor.mirror_h", "Mirror left-right (M)");
        t.add("gui.alpineshred.design_editor.mirror_v", "Mirror top-bottom (Shift+M)");
        t.add("gui.alpineshred.design_editor.undo", "Undo (Ctrl+Z)");
        t.add("gui.alpineshred.design_editor.redo", "Redo (Ctrl+Y)");
        t.add("gui.alpineshred.design_editor.grid", "Grid (#)");
        t.add("gui.alpineshred.design_editor.zoom", "Bigger canvas");
        t.add("gui.alpineshred.design_editor.hex", "Hex color");
        t.add("gui.alpineshred.design_editor.name", "Design name");
        t.add("gui.alpineshred.design_editor.template_prev", "Previous template");
        t.add("gui.alpineshred.design_editor.template_next", "Next template");
        t.add("gui.alpineshred.design_editor.template_apply", "Apply this template (replaces both faces)");
        t.add("gui.alpineshred.design_editor.no_templates", "No templates");
        t.add("gui.alpineshred.design_editor.copy_code", "Code");
        t.add("gui.alpineshred.design_editor.copy_code.tip", "Copy a share code for this design");
        t.add("gui.alpineshred.design_editor.paste_code", "Paste");
        t.add("gui.alpineshred.design_editor.paste_code.tip", "Paste a share code");
        t.add("gui.alpineshred.design_editor.export", "Export");
        t.add("gui.alpineshred.design_editor.export.tip", "Save both faces as PNG files in config/alpineshred/exports");
        t.add("gui.alpineshred.design_editor.reset", "Reset");
        t.add("gui.alpineshred.design_editor.reset.tip", "Remove the design from the gear (free)");
        t.add("gui.alpineshred.design_editor.reset_sure", "Sure?");
        t.add("gui.alpineshred.design_editor.back", "Back");
        t.add("gui.alpineshred.design_editor.back.tip", "Back to the bench's slots");
        t.add("gui.alpineshred.design_editor.save", "Save");
        t.add("gui.alpineshred.design_editor.save.tip", "Save the design to the gear");
        t.add("gui.alpineshred.design_editor.brush_smaller", "Smaller brush ([)");
        t.add("gui.alpineshred.design_editor.brush", "Size %s");
        t.add("gui.alpineshred.design_editor.brush_bigger", "Bigger brush (])");
        t.add("gui.alpineshred.design_editor.filled", "Filled");
        t.add("gui.alpineshred.design_editor.filled.tip", "Rectangles filled or outlined");
        t.add("gui.alpineshred.design_editor.gradient_type", "Gradient shape");
        t.add("gui.alpineshred.design_editor.gradient.linear", "Linear");
        t.add("gui.alpineshred.design_editor.gradient.radial", "Radial");
        t.add("gui.alpineshred.design_editor.dither", "Dither");
        t.add("gui.alpineshred.design_editor.dither.tip", "Ordered dither instead of a smooth blend");
        t.add("gui.alpineshred.design_editor.middle", "Middle");
        t.add("gui.alpineshred.design_editor.middle.tip", "A middle color stop (taken from the primary color)");
        t.add("gui.alpineshred.design_editor.pattern", "Pattern type");
        t.add("gui.alpineshred.design_editor.pattern.checker", "Checker");
        t.add("gui.alpineshred.design_editor.pattern.stripes_horizontal", "Stripes");
        t.add("gui.alpineshred.design_editor.pattern.stripes_vertical", "Bars");
        t.add("gui.alpineshred.design_editor.pattern.stripes_diagonal", "Diagonal");
        t.add("gui.alpineshred.design_editor.pattern.dots", "Dots");
        t.add("gui.alpineshred.design_editor.pattern_smaller", "Smaller pattern");
        t.add("gui.alpineshred.design_editor.pattern_bigger", "Bigger pattern");
        t.add("gui.alpineshred.design_editor.lock", "Lock");
        t.add("gui.alpineshred.design_editor.lock.tip", "Lock transparent: gradients and patterns repaint only painted pixels");
        t.add("gui.alpineshred.design_editor.copy_face", "Copy face");
        t.add("gui.alpineshred.design_editor.copy_face.tip", "Copy this face to the other side");
        t.add("gui.alpineshred.design_editor.edge", "Edge");
        t.add("gui.alpineshred.design_editor.edge.tip", "Paint the edges the primary color");
        t.add("gui.alpineshred.design_editor.edge_label", "Edge");
        t.add("gui.alpineshred.design_editor.pair.tip", "Both skis the same, or the left one mirrored");
        t.add("gui.alpineshred.design_editor.pair.same", "Pair: same");
        t.add("gui.alpineshred.design_editor.pair.mirror", "Pair: mirror");
        t.add("gui.alpineshred.design_editor.saving", "Saving...");
        t.add("gui.alpineshred.design_editor.code_copied", "Share code copied");
        t.add("gui.alpineshred.design_editor.code_pasted", "Share code pasted");
        t.add("gui.alpineshred.design_editor.code_bad", "Not a valid code: %s");
        t.add("gui.alpineshred.design_editor.exported", "Exported to %s");
        t.add("gui.alpineshred.design_editor.export_failed", "Export failed (see the log)");
        t.add("gui.alpineshred.design_editor.front", "Front (top)");
        t.add("gui.alpineshred.design_editor.back_face", "Back (base)");
        t.add("gui.alpineshred.design_editor.hints", "B E F L R G P I tools, M mirror, X swap colors, [ ] brush, Tab face, Ctrl+Z undo");
        t.add("gui.alpineshred.design_editor.cost", "Saving costs %s dye (%s in the bench)");
        t.add("message.alpineshred.design.ok", "Design saved");
        t.add("message.alpineshred.design.too_large", "Too large to save");
        t.add("message.alpineshred.design.invalid", "Could not save: not a valid design");
        t.add("message.alpineshred.design.no_cost", "Put dye in the bench to pay for the save");
        t.add("message.alpineshred.design.rate_limited", "Wait a moment before saving again");
        t.add("message.alpineshred.design.disabled", "Designs are turned off on this server");
        t.add("message.alpineshred.design.forbidden", "You are not allowed to save designs here");
        t.add("tooltip.alpineshred.design", "Design: %s");
        t.add("tooltip.alpineshred.design.by", "Design: %s by %s");
        t.add("tooltip.alpineshred.design.untitled", "untitled");
        t.add("commands.alpineshred.design.none", "The held item has no design");
        t.add("commands.alpineshred.design.unreadable", "The held item's design could not be read (version %s, %s bytes)");
        t.add("commands.alpineshred.design.info", "Design \"%s\" by %s (%s, %s bytes, hash %s)");
        t.add("commands.alpineshred.design.cleared", "Cleared %s design(s)");
        t.add("advancements.alpineshred.fresh_coat.title", "Fresh Coat");
        t.add("advancements.alpineshred.fresh_coat.description", "Paint a design on a board or skis");
        t.add("design_template.alpineshred.solid", "Solid");
        t.add("design_template.alpineshred.stripes", "Stripes");
        t.add("design_template.alpineshred.pinstripe", "Pinstripe");
        t.add("design_template.alpineshred.checker", "Checker");
        t.add("design_template.alpineshred.diagonal_split", "Diagonal split");
        t.add("design_template.alpineshred.sunset", "Sunset");
        t.add("design_template.alpineshred.glow", "Glow");
        t.add("design_template.alpineshred.camo", "Camo");
        t.add("design_template.alpineshred.polka", "Polka dots");
        t.add("design_template.alpineshred.racing_stripe", "Racing stripe");
        t.add("design_template.alpineshred.two_tone", "Two tone");
        t.add("design_template.alpineshred.retro_fade", "Retro fade");

        // ---- accessory slot names (Curios and Trinkets) ----
        t.add("curios.identifier.snow_gear", "Snow Gear");
        t.add("curios.identifier.goggles", "Goggles");
        t.add("trinkets.slot.feet.snow_gear", "Snow Gear");
        t.add("trinkets.slot.head.face", "Face");
        t.add("trinkets.slot.hand.glove", "Glove");
    }
}
