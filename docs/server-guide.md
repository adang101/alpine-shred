# Alpine Shred server guide

For people who run a server with Alpine Shred (Minecraft 1.20.1, Forge, NeoForge via the Forge jar, or Fabric).
Everything here was read from the code; where something has not been tried by hand it says "not yet verified".
Sources: `AlpineConfig`, `AlpineCommands`, `LiftCommands`, the README's "Design clarifications" (cited as "README n")
and [the phase 9 plan](phase9-plan.md).

## 1. What to install

| | Version | Note |
| --- | --- | --- |
| Minecraft | 1.20.1 | |
| Forge | 47 or later | The jar's `mods.toml` asks for `[47,)`. CI runs 47.4.28. |
| Fabric Loader / Fabric API | 0.15.0 or later / any | CI runs 0.16.14 and Fabric API 0.92.6+1.20.1. |
| Architectury API | 9.2.14 or later | Both loaders. |
| PlayerAnimator | any | Both loaders. |
| Curios (Forge) / Trinkets (Fabric) | 5.2.0 or later / 3.7.0 or later | **Required**, not optional (section 8). CI runs 5.14.1 and 3.7.2. |
| Create | 6.0.0 or later | Optional (section 7). CI runs Create 6.0.8-291 (Forge) and Create Fabric 6.0.8.1. |

- Install the mod on the server and on every client. A client on a different `config_sync` protocol (now 2) ignores
  the server's settings and has riding switched off, with a message saying why (README "What phase 2 delivers").
- One jar per loader. The Forge jar is the one for NeoForge too (section 11).

## 2. Configuration files

| File | Who reads it |
| --- | --- |
| `config/alpineshred-common.json` | The server. Section 3. |
| `config/alpineshred-client.json` | Each client, for itself. Section 3, second table. A server owner can ignore it. |

How the files behave (from `ConfigSpec`):
- Keys are nested by their dotted path: `lift.seats` is `{"lift": {"seats": 2}}`. Both files are created on first
  start with every key.
- Missing keys take their default. A value out of range is clamped, and a bad value takes the default; each is
  logged as a warning with the file name and key. An unknown key is warned about and ignored.
- On load the file is rewritten in normal form (so comments cannot survive). A file whose `schemaVersion` is newer
  than the mod's is read but left untouched. An unreadable file is moved to `.bak` and replaced with defaults.
- Changes apply on restart or with `/alpineshred config reload` (section 4). Reload re-reads the common file only
  and resyncs clients. The exceptions are in the "does" column: `lift.spacing` applies to a line when it is created
  or loaded, and the worldgen keys apply to chunks generated afterwards.
- `config_sync` (server to client, at login and after a config or tuning reload) carries only the settings the
  client predicts: `movement.enabled` (false also while a mod in `movement.disableWithMods` is loaded),
  `movement.maxSpeedMul`, `movement.scrapeEnabled`, `lift.maxBoardSpeed`, `lift.dismountAnywhere` and `lift.seats`.
  Everything else is server-only. In particular `lift.kineticStress` is not synced: only the server's value matters
  to the drive, and the item tooltip shows the client's own copy (README 78).

## 3. Config reference

Generated from `AlpineConfig` by `ServerGuideTest`: the test fails with the expected text when a key, default or
range changes, so replace the block with it. "Allowed" is the range the loader clamps to.

### Server file: `alpineshred-common.json`

<!-- config-table:common:start -->
| Key | Default | Allowed | What it does, and what it costs |
| --- | --- | --- | --- |
| `movement.enabled` | `true` | `true`, `false` | Master switch for riding. Off: nobody can strap in (clients learn it through `config_sync`). No cost. |
| `movement.maxSpeedMul` | `1.0` | 0.1 to 3.0 | Scales the rider speed cap, on the server's validator and on clients (synced). No cost. |
| `movement.scrapeEnabled` | `true` | `true`, `false` | Whether riders slide over scrape surfaces (bare ground) above the minimum slide speed; off, they walk there (synced). No cost. |
| `movement.validation.strict` | `auto` | `auto`, `true`, `false` | `auto` enforces on a dedicated server and only logs "Would correct" in singleplayer. `true` and `false` force it. Enforced: three violations in 40 ticks put the rider back at the last valid position. No cost. |
| `movement.disableWithMods` | `[]` | list of strings | Mod ids; riding is off while any of them is loaded (synced as `movement.enabled`). No cost. |
| `trick.enabled` | `true` | `true`, `false` | Off: no tricks are scored for strapped riders. No cost. |
| `trick.maxComboScore` | `50000` | 0 to 10000000 | Cap on one combo's total score. No cost. |
| `trick.broadcast` | `self` | `none`, `self`, `nearby`, `all` | Who sees a landed trick's result: `none`, `self`, `nearby` (players within 64 blocks in the level) or `all`. `nearby` and `all` send one small packet per trick to each other player. |
| `gear.durabilityMul` | `1.0` | 0.0 to 10.0 | Multiplies board and ski durability wear and bail damage; 0 turns durability wear off (edge and wax still wear). No cost. |
| `rail.oxidation` | `true` | `true`, `false` | Off: copper rails stop weathering (random ticks do nothing). Waxing still works. Saves a few random ticks. |
| `worldgen.terrainParks` | `true` | `true`, `false` | Terrain parks generate in new chunks. Off skips them. About 3 ms a start when on. |
| `worldgen.groomedRuns` | `true` | `true`, `false` | Groomed runs generate in new chunks. Off skips them. Run planning samples terrain noise: the most expensive start type besides resorts. |
| `worldgen.resorts` | `true` | `true`, `false` | Ski resorts (lodge, groomed run and lift) generate in new chunks. About 133 to 138 ms a start on CI's two cores (budget 150 ms). |
| `worldgen.snowFeatures` | `true` | `true`, `false` | Snow features (small park pieces) generate in new chunks. About 3 ms a start when on. |
| `worldgen.frequencyMul` | `1.0` | 0.1 to 1.0 | Keeps this share of starts of every type and skips the rest, by each start's own seed; 1.0 keeps all. It cannot add starts: spacing belongs to the structure sets (a datapack may change them). |
| `lift.maxLinesPerLevel` | `64` | 1 to 1024 | Lift lines per dimension; the linker refuses more. Cost grows with line count (the manager measured 0.003 to 0.005 ms a tick for five lines). |
| `lift.maxHeads` | `48` | 1 to 256 | Most tower heads on one line (the linker refuses more). |
| `lift.maxSpan` | `64` | 8 to 256 | Longest horizontal gap between two consecutive nodes of a line, in blocks (the linker refuses more; a resort's generated lift obeys it too). |
| `lift.maxLength` | `1200` | 32 to 8192 | Longest loop of one line, in blocks. Chair count is the loop length over `lift.spacing`, so this bounds a line's chairs. |
| `lift.seats` | `2` | 1 to 4 | Seats on each chair (synced). Read live, so a reload changes every chair. |
| `lift.spacing` | `24` | 12 to 48 | Blocks between chairs. More chairs on a line cost more per tick (5 microseconds a chair budget). Read when a line is created or loaded, so lines already in memory keep the old spacing until a restart. |
| `lift.speedLevels` | `[1.5, 2.5, 4.0]` | 1 to 8 values, each 0.1 to 10.0 | Speeds, in blocks per second, that a drive station cycles through when right-clicked. The second one is the default. |
| `lift.maxBoardSpeed` | `4.0` | 0.0 to 10.0 | A chair is refused when its line runs faster than this, in blocks per second (synced; the client's board prompt predicts it). |
| `lift.runUnloaded` | `true` | `true`, `false` | On: a line keeps moving while none of its nodes is in a loaded chunk. Off: it freezes until a node's chunk loads. See the lifts section. |
| `lift.requirePass` | `false` | `true`, `false` | On: boarding needs a lift pass anywhere in the player's inventory or offhand. |
| `lift.dismountAnywhere` | `false` | `true`, `false` | Off: a rider may sneak off only with the bar up, within 4 blocks of the ground, or on a faulted line. On: any time (synced). |
| `lift.mobsAllowed` | `true` | `true`, `false` | Whether mobs may ride (they must also fit the size limits and not be in `#alpineshred:lift_blocked`). Players always may. |
| `lift.maxPassengerWidth` | `1.2` | 0.1 to 4.0 | Widest mob bounding box that may ride, in blocks. |
| `lift.maxPassengerHeight` | `2.4` | 0.1 to 6.0 | Tallest mob bounding box that may ride, in blocks. |
| `lift.petsAutoBoard` | `true` | `true`, `false` | Tamed pets within 8 blocks of a boarding owner board the same chair, while seats are free. Needs `lift.mobsAllowed`. |
| `lift.syncRadius` | `256` | 32 to 1024 | A line is sent to a player when any of its nodes is within this many blocks horizontally. Larger radius means more lines resent (every 100 ticks) to each player. |
| `lift.kineticMaxSpeed` | `4.0` | 0.1 to 10.0 | Create only: the cap on a kinetic drive's line speed, in blocks per second (rpm / 64 up to this). |
| `lift.kineticStress` | `4.0` | 0.1 to 64.0 | Create only: stress units per rpm the kinetic drive takes from the network (4 gives 1024 su at 256 rpm). |
| `lift.driveNeedsRedstone` | `true` | `true`, `false` | On: a drive station runs only with a redstone signal. Off: it always runs. A kinetic drive ignores it. |
| `design.enabled` | `true` | `true`, `false` | Off: the Paint Bench refuses every save and says so. |
| `design.maxCompressedBytes` | `8192` | 512 to 16384 | Largest design a save or share-code import may be, in bytes, both deflated faces together. Bigger designs cost item NBT and sync bandwidth. |
| `design.saveCostCount` | `1` | 0 to 64 | Dye items a save consumes (any dye in `#alpineshred:design_cost`); 0 makes saves free. Creative players never pay. |
| `design.minPermissionLevel` | `0` | 0 to 4 | Permission level a player needs to save a design: 0 everyone, 2 operators. |
| `design.saveCooldownTicks` | `40` | 0 to 1200 | Ticks a player must wait between saves. No cost. |
<!-- config-table:common:end -->

### Client file: `alpineshred-client.json`

<!-- config-table:client:start -->
| Key | Default | Allowed | What it does, and what it costs |
| --- | --- | --- | --- |
| `client.steerMode` | `ride` | `ride`, `keys` | `ride`: steer by looking and leaning; `keys`: steer with the movement keys. |
| `client.stance` | `regular` | `regular`, `goofy` | Which foot leads on a board: `regular` or `goofy`. |
| `client.fovEffect` | `1.0` | 0.0 to 1.0 | Strength of the speed field-of-view effect: 0 off, 1 full. |
| `client.reduceMotion` | `false` | `true`, `false` | Accessibility option: reduces camera and screen motion effects. |
| `client.flashSafe` | `false` | `true`, `false` | Accessibility option: tones down flashing screen effects. |
| `client.hudSpeed` | `false` | `true`, `false` | Shows a speed read-out in blocks per second while strapped in. |
| `client.carveTrails` | `true` | `true`, `false` | Snow trail particles behind a carving rider. |
| `client.gogglesOverlay` | `true` | `true`, `false` | Tints the first-person view with the worn goggles' lens. |
| `client.gearRenderDistance` | `48` | 8 to 256 | Other players' board, ski, goggle, mitt and pole models are drawn within this many blocks. A lower value saves frame time in crowds. |
| `client.autoBoard` | `true` | `true`, `false` | Board a chair automatically when riding up to one. |
| `client.hudScale` | `1.0` | 0.5 to 3.0 | Scale of the riding HUD. |
| `client.showCustomDesigns` | `true` | `true`, `false` | Draw other players' painted designs; off draws the default look for others. |
| `client.designCacheSize` | `64` | 8 to 512 | Most design textures kept in memory (least recently used go first). |
| `client.designGrid` | `true` | `true`, `false` | The paint editor starts with its grid on. |
| `client.hints` | `true` | `true`, `false` | Show control hints. |
| `client.hintsSeen` | `[]` | list of strings | Hint ids already shown. The mod writes this; leave it alone. |
<!-- config-table:client:end -->

## 4. Commands

All are under `/alpineshred`. "Level" is the vanilla permission level the source needs (`hasPermission`); level 2 is
what an operator has by default.

| Command | Level | What it does |
| --- | --- | --- |
| `strap <targets> [on\|off]` | 2 | Straps players in or out of their gear (no `on`/`off` toggles). Reports the players it could not strap, with the reason. |
| `stats [player]` | 0 | Best combo, total score, tricks landed, air time, grind and ride distance, lift rides. Without a player it needs a player source. |
| `design info` | 0 | The held item's design name, author, kind, size and hash. Player only. |
| `design clear <players>` | 2 | Strips the design from each player's main-hand item. |
| `lift list` | 2 | Every line in the source's level: short id, node count, loop length, speed, flags (`powered`, `emergency`, `faulted`). |
| `lift info <id>` | 2 | Nodes, loop length, chairs, speed and target, flags and riders of one line. |
| `lift stop <id>` | 2 | Emergency stop (2.0 blocks per second squared). |
| `lift start <id>` | 2 | Clears the emergency and powers the line, at the drive's target speed if the drive is loaded, else at the second `lift.speedLevels` entry. |
| `lift remove <id>` | 2 | Puts riders on the ground, discards the chairs, clears the loaded nodes and deletes the line. |
| `config reload` | 2 | Re-reads `alpineshred-common.json` (warnings go to the log, the count to the caller) and resyncs every client. |
| `tuning reload` | 2 | Reloads the tuning data (section 10) and resyncs every client. |
| `debug ride on\|off` | 2 | A ride read-out for the caller, with the server validator's verdicts. Player only. |

- `<id>` is a full UUID or a unique prefix of at least 4 characters (`lift list` shows 8). The lift commands act on
  the lines of the level the source stands in, so run them in the right dimension.
- Vanilla `/locate structure alpineshred:<id>` and `/place structure` work on the four structures (section 6).

## 5. Lifts

What a server owner can and cannot change:

| Setting | Effect |
| --- | --- |
| `lift.maxLinesPerLevel`, `lift.maxHeads`, `lift.maxSpan`, `lift.maxLength` | Checked when a player links a line with the linker, and when a resort plans its lift. Lowering one does not remove lines that already exist (nothing in the code does). A level at the line limit refuses new links; a generated resort lift is registered through the same call, so by the code path it is not registered at the limit (not exercised by a test). |
| `lift.spacing`, `lift.seats` | Chairs per line is the loop length divided by `lift.spacing`; seats per chair is `lift.seats`. |
| `lift.speedLevels`, `lift.driveNeedsRedstone` | What a drive station offers and whether it needs a redstone signal. In a generated resort, `driveNeedsRedstone` true leaves the lift stopped until powered; false starts it at the second speed level. |
| `lift.maxBoardSpeed`, `lift.requirePass`, `lift.dismountAnywhere`, `lift.mobsAllowed`, `lift.maxPassengerWidth`, `lift.maxPassengerHeight`, `lift.petsAutoBoard` | Who may ride, and when they may get off. Entity tags: `#alpineshred:lift_blocked` (ender dragon, wither, warden) is refused; `#alpineshred:lift_passengers` (empty) is always allowed. Players always ride. |

### `lift.runUnloaded`

- Every tick the lift manager steps each line's clock. With `runUnloaded` true (default) that includes lines none of
  whose nodes is in a loaded chunk, so a lift keeps its place and speed while nobody is near, and the chairs a
  player walks up to are where the clock says.
- With it false, a line is skipped on every tick when no node's chunk is loaded: it freezes and resumes from the
  same spot when a node loads.
- Cost: the manager's work is a few arithmetic operations per line per tick either way (measured 0.003 to 0.005 ms
  a tick for five lines, section 9). `false` saves that for lines nobody is near, and nothing else.
- Chairs are entities, and not the line's clock: they exist only near players. Every 20 ticks, a line whose nodes
  have a player within 128 blocks gets its missing chairs spawned (in entity-ticking chunks); empty chairs are removed
  when no player is within 192 blocks of any node (`LiftChairs`).

### `lift.syncRadius`

- The server sends a line to a player when any of its nodes is within `lift.syncRadius` blocks horizontally, checked
  every 40 ticks, plus everything in range when the player joins. A line already sent is resent every 100 ticks
  while still in range, so clients' clocks stay on the server's.
- Larger means more lines known to each client and more resend packets. The default is 256.
- A line is sent as soon as it changes (created, powered, stopped, faulted, removed), if the player already has it.

### Faults

A broken head, station or drive faults its line: it slows at 2.0 blocks per second squared and stays stopped
until relinked, and riders may sneak off. The same happens when a wrench picks the block up (section 7).

## 6. World generation

Four structures generate in new chunks of snowy biomes only. Existing chunks are never changed. Each reads its
switch at generation time, so no restart is needed for a change to apply to the next chunk that generates.

| Structure | Switch | Spacing / separation (chunks) | Cost per start |
| --- | --- | --- | --- |
| `alpineshred:terrain_park` | `worldgen.terrainParks` | 28 / 10 | about 3 ms |
| `alpineshred:groomed_run` | `worldgen.groomedRuns` | 22 / 8 | 133 to 138 ms on CI's two cores (about 220 ms before phase 9's work) |
| `alpineshred:ski_resort` | `worldgen.resorts` | 56 / 24 | 133 to 138 ms on CI's two cores, budget 150 ms |
| `alpineshred:snow_feature` | `worldgen.snowFeatures` | 10 / 4 | about 3 ms |

- `worldgen.frequencyMul` (0.1 to 1.0) keeps that share of starts of every type and skips the rest (decided by the
  start's own seed, so the same starts are always kept). It cannot add starts: spacing is the structure set's. To
  place more, change the structure sets in a datapack (`data/alpineshred/worldgen/`).
- A resort is a lodge, a groomed run and, when a valid one can be planned, a lift. It plans its lift inside
  `lift.maxSpan`, `lift.maxHeads` and `lift.maxLength`; if none fits, the resort has no lift (logged at debug).
- Run and resort planning sample the generator's terrain noise on a world generation thread, so the cost shows up
  while chunks are generated (new exploration, or pre-generation). Turning off `worldgen.resorts` and
  `worldgen.groomedRuns` removes nearly all of it. No planning cost is paid in chunks that are already generated.
- The spawn biomes come from the tags `data/alpineshred/tags/worldgen/biome/has_*`.

## 7. Create (optional)

Create is optional on both loaders: Forge declares it `mandatory = false` (`[6.0.0,)`, side BOTH) and Fabric
`suggests` it (`>=6.0.0`). Without it the mod behaves as in phases 1 to 8 and pays nothing for the compat code:
nothing references Create unless the loader reports it present. CI runs both with and without Create, on both loaders.
Tested versions: Forge Create 6.0.8-291, Fabric Create 6.0.8.1.

What changes with Create present:

**The kinetic lift drive** (`alpineshred:kinetic_lift_drive`). A gearbox-style block that powers the drive station
beside it from a Create network. It is registered only when Create is loaded.
- Recipe (shapeless, loaded only with Create): Create gearbox, Create large cogwheel, iron block.
- Which source wins: a drive station asks its six neighbours (down, up, north, south, west, east, first match). While
  a kinetic drive is attached it is the source and redstone is ignored. Remove it and redstone takes over again.
- Speed: `|rpm| / 64` blocks per second, up to `lift.kineticMaxSpeed` (default 4.0). 64 rpm is 1 block per second,
  256 rpm (Create's top) reaches the 4.0 cap. Below 8 rpm in either direction counts as stopped. Direction of
  rotation does not matter; the cable always runs up.
- Stress: a flat `lift.kineticStress` (default 4.0) su per rpm: 1024 su at 256 rpm, whatever the number of chairs.
- Losing rotation, falling under 8 rpm, or overstress is an emergency stop (2.0 blocks per second squared). Rotation
  returning restarts the line.
- Speed changes use the same ramp as redstone (0.5 blocks per second squared); the drive has no motion code of its own.
- `lift.kineticMaxSpeed` and `lift.kineticStress` are server settings; the drive's cost is 0.0006 to 0.0012 ms a tick
  each (budget 0.02 ms).

**Create's wrench** works on our blocks, matched by the item id `create:wrench`:
- Park pieces (rails, boxes, ramps, pipes): a plain click does what the park wrench does (cycle the shape); a
  sneak-click picks the piece up.
- Lift blocks in the tag `alpineshred:wrench/lift_pickup` (pole, head, drive station, return station, loading gate, stop
  button, kinetic drive): a plain click only shows a hint to use the lift linker. A sneak-click picks the block up
  through `Level.destroyBlock`, which faults the line exactly as breaking it by hand does. Poles hold no line data,
  and the gate and stop button have no fault path, so picking those up faults nothing.
- Pickup gives the block's item to the player (nothing in creative) and drops no loot.

**Tags.** Our lift blocks are in `create:non_breakable` (the kinetic drive as an optional entry), so contraptions
leave lift nodes alone. All tag and recipe entries naming Create things are optional, so a world without Create loads
them silently.

**Not yet verified:** a client without Create joining a server with Create (the kinetic drive block would be
missing on that client), and Create versions other than the two above. A world saved with kinetic drives and
loaded without Create loses those blocks, as with any removed mod's blocks.

## 8. Curios and Trinkets (required)

Gear goes in accessory slots, through one abstraction (`GearAccess`): Curios on Forge, Trinkets on Fabric. Both
mods are hard dependencies and the slot data ships in the jar, so there is nothing to configure.

| Gear | Curios slot (Forge) | Trinkets slot (Fabric) |
| --- | --- | --- |
| Boards and skis | `snow_gear` (size 1) | `feet/snow_gear` |
| Goggles | `goggles` (size 1) | `head/face` |
| Mitts | `hands` (Curios' own slot; we add mitts to its tag) | `hand/glove` |

- Slots are added to the `player` entity. Boots are ordinary armor.
- On Fabric, Trinkets 3.7 has no global equip event, so equipping the mod's gear raises its own callbacks and other
  changes are caught by a rebuild every 10 ticks (README 8).
- Not yet verified: designs syncing to other clients through Trinkets on a real Fabric server (CI cannot run it).

## 9. Performance and the measured budgets

Numbers are from CI (a two-core machine) in the phase 8 and 9 runs. A real server differs, so measure with spark.

| Path | Budget | Measured | Notes |
| --- | --- | --- | --- |
| Lift manager, five lines of 50+ chairs | 0.3 ms a tick | 0.003 ms Forge, 0.005 ms Fabric | CI fails at 3 times the budget (README 71). |
| One chair | 5 microseconds a tick | 2.9 (Forge), 3.4 (Fabric) | Same. |
| Cable, client | 0.5 ms a frame | 0.28 ms | Client only. CI fails at 3 times (README 69). |
| Kinetic drive | 0.02 ms a tick each | 0.0006 to 0.0012 ms | CI fails at 3 times. |
| Resort start | 150 ms | 133 to 138 ms (about 210 before phase 9's work) | The game test fails at a 400 ms mean. |
| Groomed run start | 50 ms design figure (README 59) | 132.7 to 138.4 ms (CI round 2 of WP6), 220.4 ms before | Over the design figure. The game test asserts only a 400 ms mean (`WorldgenGameTests.MAX_MEAN_MS`). |
| Terrain park, snow feature start | 50 ms | about 3 ms | The game test fails at 50 ms. |
| Design texture build, client | 5 ms mean | 0.5 to 1.1 ms | Client only. |

Resort and groomed-run starts share the planner, so both fell by the same work (README 86). The budget the code asserts for
each is 400 ms; the 150 ms and 50 ms figures are the design targets.

Knobs, most useful first:
- `worldgen.resorts` and `worldgen.groomedRuns`: remove most generation cost (section 6).
- A **one-core server**: resort planning fetches independent noise columns side by side on the JVM's common pool.
  With one core there is no pool to use and it falls back to the old, sequential speed, about 210 ms a start,
  over the 150 ms budget (README 86). Switch resorts off while pre-generating there, or accept slower chunk generation.
- `lift.maxLinesPerLevel`, `lift.spacing`, `lift.maxLength`: bound a level's lift work (lines times chairs).
- `lift.runUnloaded` (section 5): off skips lines nobody is near.
- `lift.syncRadius`: lower means fewer lift packets per player (section 5).
- `trick.broadcast`: `nearby` or `all` send a packet per trick to other players; `self` or `none` do not.
- `rail.oxidation`: off removes the copper rails' random-tick work.
- `movement.validation.strict`: costs nothing either way; `auto` is strict on dedicated servers.
- Client keys such as `client.gearRenderDistance` and `client.designCacheSize` are for players, not the server.

The lift hot paths do not allocate per line, chair or cable piece per tick (README 88); the chair pass still makes
one small record per chair once a second.

## 10. Datapack knobs

Reloaded with the data packs; `/alpineshred tuning reload` reloads them and resyncs clients without a full `/reload`.
- `data/alpineshred/movement/tuning.json`, `data/alpineshred/surface_profiles`, `data/alpineshred/gear/*.json`,
  `data/alpineshred/box_materials.json`: ride feel, surface behaviour, gear stats and box materials. Synced to clients.
  Air rotation keys in `tuning.json` (README 95): `airFlipRecover` (default 8, 0 to 90) and `airSpinRecover` (default 5, 0 to 90) are the
  degrees per tick at which a rider with no W/S (or no A/D) held levels out toward the nearest whole flip (or the nearest
  forward or switch heading) for the landing; 0 turns the recovery off. Each is capped at the matching hand rate
  (`airFlipMax`, `airSpinMaxBoard`/`airSpinMaxSkis`), so the validator's rate bounds hold whatever is set. A key held at
  takeoff does nothing until it is released; that part has no setting.
- `data/alpineshred/worldgen/`: the structure sets (spacing) and template pools.
- Tags: `alpineshred:lift_blocked`, `alpineshred:lift_passengers` (entity types), `alpineshred:design_cost` (the
  dyes `design.saveCostCount` takes; all 16 vanilla dyes by default).

## 11. NeoForge and known issues

**NeoForge 1.20.1.** The Forge jar is the one for NeoForge: its metadata is the Forge `mods.toml`, which NeoForge
1.20.1 also reads, and the release pipeline lists the one jar for both Forge and NeoForge. **Not yet verified:** the
jar has never been run on a NeoForge 1.20.1 server (the plan has it as a hand check, `docs/playtest-phase8.md`
section 4). Nothing was tested about whether the jar's dependencies (Architectury, Curios, PlayerAnimator, and Create
if present) have builds that load there. If you try it, run the three-player lift check from that page and report.

**Known issues.**
- Server restart with riders on a running line is covered by a game test of the saved data and by a relog in
  the multiplayer smoke, but not yet verified on a real restart (`/save-all`, `/stop`, start) with people riding.
- The spark check on a real server with five long lines is not yet verified; section 9 is CI only.
- `lift.spacing` changes reach a line only when it is created or loaded (section 3).
- Lowering a lift limit does not remove lines that already exceed it.
- Fabric development only: `./gradlew :fabric:runClient` does not start (a Trinkets mixin names a Yarn field). The
  production Fabric jar is not affected.
- Create: see "Not yet verified" in section 7.
- The release metadata and the Modrinth and CurseForge dependency slugs are checked at the first real publish, which
  is after development (README 89).
