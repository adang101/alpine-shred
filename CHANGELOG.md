# Changelog

Built from the README's "What phase N delivers" sections. The release workflow publishes the section whose
heading matches the tag (`v1.0.0` publishes `## [1.0.0]`). Edit the heading's date when the tag is cut.

## [1.0.0] - unreleased

First release, for Minecraft 1.20.1 on Fabric and Forge (the Forge jar also runs on NeoForge 1.20.1). Create is
optional.

### Playtest feedback: goggles, leaves and the strap key
- Without goggles, snow builds up on your lens as a frosted edge, fastest in powder and after powder landings
  and wipeouts; goggles keep it off completely.
- Leaves over your head darken the view, less with goggles.
- Strap in now defaults to `H`: Curios uses `G` for its inventory. Saved keybinds are kept, so rebind it in
  Controls on an existing install.

### Create compatibility and polish (phase 9)
- The kinetic lift drive: a Create gearbox beside a drive station runs the lift. Speed follows rpm (64 rpm is
  1 block a second, capped at 4), the stress is a flat 4 su per rpm, and losing rotation or overstress is an
  emergency stop.
- Create's wrench works on park pieces, lift blocks and the other blocks of the mod.
- Tags and recipes, a balance pass, a server guide, and optimization passes on lifts and rendering.
- Release pipeline: both jars are built from the tag, with Create declared optional on both loaders.

### Ski lifts (phase 8)
- Lift lines: a drive station, tower heads and a return station joined by a cable loop, linked with the lift
  linker. Chairs ride the cable on both sides and you board and unload with a right-click.
- Six blocks, the lift linker and lift pass, a loading gate and a stop button, with emergency stops on lost
  redstone.
- Synced to clients on change, with cable, sheaves, bullwheels and sound rendered client side.
- Every ski resort generates with a lift of about 200 blocks.

### World generation (phase 7)
- Four structures in snowy biomes, groomed runs, ski resorts with a lodge, terrain parks with an entry gate and
  lines, and natural snow features (kickers, rollers, cornices).

### Board and ski customization (phase 6)
- The Paint Bench and an in-game pixel editor for boards and skis, with twelve built-in templates, share codes,
  server-side checks and moderation settings.

### Tricks and scoring (phase 5)
- Spins, flips and grabs in the air, landing grades, combo scoring with a popup, server-side validation of every
  air session, statistics and advancements, and ten grab animations for boards and skis.

### Terrain park (phase 4)
- Rails, boxes in 16 colors, ramps, quarter pipes, the park wrench, grinding, waxing with honeycomb, park decor.

### Feel (phase 3)
- Rider animation (22 clips), camera roll and speed field of view, sound events, snow particles and a goggle
  HUD.

### Riding (phase 2)
- Slope riding with carving and jumps, server validation of speed and rise, wear and damage on gear, and a
  debug overlay.

### Gear (phase 1)
- 21 items: boards, skis, boots, helmets, goggles, mitts and tools, with recipes, tooltips, dyeing, the `alpine`
  armor material, gear slots on Curios (Forge) and Trinkets (Fabric), strapping in with `G`, and config files
  with every key.
