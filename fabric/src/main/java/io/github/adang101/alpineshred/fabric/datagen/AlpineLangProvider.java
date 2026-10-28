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
        t.add("tooltip.alpineshred.park_wrench", "Rotates and reshapes rails and boxes");
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
        t.add("message.alpineshred.protocol_mismatch", "Alpine Shred on this server speaks protocol %s, this client %s: riding is off until versions match");

        // ---- commands ----
        t.add("commands.alpineshred.strap.done", "Changed strap state for %s player(s)");
        t.add("commands.alpineshred.strap.failed", "Could not strap in: %s");
        t.add("commands.alpineshred.config.reloaded", "Alpine Shred config reloaded (%s warning(s), see log)");
        t.add("commands.alpineshred.tuning.reloaded", "Alpine Shred tuning, surfaces and gear stats reloaded");
        t.add("commands.alpineshred.debug.ride.on", "Ride debug overlay on");
        t.add("commands.alpineshred.debug.ride.off", "Ride debug overlay off");

        // ---- key bindings ----
        t.add("key.categories.alpineshred", "Alpine Shred");
        t.add("key.alpineshred.strap", "Strap In / Out");
        t.add("key.alpineshred.grab_indy", "Indy Grab");
        t.add("key.alpineshred.grab_mute", "Mute Grab");
        t.add("key.alpineshred.grab_nose", "Nose Grab");
        t.add("key.alpineshred.grab_tail", "Tail Grab");
        t.add("key.alpineshred.stance", "Toggle Stance");

        // ---- accessory slot names (Curios and Trinkets) ----
        t.add("curios.identifier.snow_gear", "Snow Gear");
        t.add("curios.identifier.goggles", "Goggles");
        t.add("trinkets.slot.feet.snow_gear", "Snow Gear");
        t.add("trinkets.slot.head.face", "Face");
        t.add("trinkets.slot.hand.glove", "Glove");
    }
}
