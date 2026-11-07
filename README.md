# Alpine Shred

Snowboards, skis, park features, groomed runs and ski lifts for Minecraft 1.20.1, on Forge,
NeoForge (via the Forge jar) and Fabric from one codebase. The aim is a mod that looks and feels
real without being ultra realistic: still Minecraft, so blocky, fun and simple. There is no
separate design document. This README (what each phase delivers, and the design clarifications
made while building) and the phase plans in `docs/` are the record. References below to "the
design doc" and its section numbers point to that record.

**Running a server?** Read [docs/server-guide.md](docs/server-guide.md): every config key, the commands and
permission levels, lift and world generation settings, Create, performance budgets and NeoForge notes.

**Status:** Phases 1 to 8 are merged into `main`; phase 9, the last, is next
([docs/phase9-plan.md](docs/phase9-plan.md)). Phase 8 (ski lifts) is built: lift lines of towers, a drive and a return station, chairs
that carry up to two riders (or mobs) and leave them at the top in a glide, lifts that generate at
ski resorts, and `/alpineshred lift` commands. Its automated exit gate passes in CI: the lift game
tests (line data and its save and reload, boarding, riding, unloading, faulted lines, relogs, the
budgets), the `lift` client smoke, and the multiplayer smoke, in which all three clients ride a
lift of over 400 blocks, the watchers' chairs match the server's, a rider logs out mid-ride and
comes back on the same chair, and a broken head faults the line, which slows at 2.0 blocks per
second squared and lets its riders off. The people part (three players on a real lift, a server
restart with riders, render mods, a NeoForge server, a spark check) is in
[docs/playtest-phase8.md](docs/playtest-phase8.md).
Phase 6 (board and ski customization) is built: the Paint Bench, the paint editor,
validated saves, share codes, templates, and procedurally drawn designed boards and skis. Its
automated part passes in CI: the codec, tool and asset unit tests, saves through the bench on
both loaders, a scripted client painting and saving through the real editor, and a three-client
multiplayer run in which both watchers receive and draw the rider's design and twenty more. The
people part of its exit gate (five players each painting and saving a design in under 10
minutes, twenty designs on three clients) is in [docs/playtest-phase6.md](docs/playtest-phase6.md).
Phase 5 (tricks and scoring) is complete: forged reports are rejected in the game tests, and the
smoke client's popup banks exactly what the server banks. Phases 1 to 4 are complete except phase
3's exit gate, five outside testers riding for 10 minutes each
([docs/playtest-phase3.md](docs/playtest-phase3.md)), and the hand checks in
[docs/playtest-phase4.md](docs/playtest-phase4.md). Still to check by hand: the jar on a NeoForge
1.20.1 server, and Trinkets (Fabric) syncing designs, which CI cannot run.

## Phases

The roadmap, in nine phases. Each phase merges into `main` once
the automated part of its exit gate passes in CI; the parts that need people are listed in its
playtest page.

| Phase | What it delivers | State | Plan and playtest |
| --- | --- | --- | --- |
| 1 | Gear: the appendix items, recipes, the `alpine` armor material, config, Curios and Trinkets slots | Done | |
| 2 | Riding: `SlideController`, slopes, carving, jumps, server validation | Done | |
| 3 | Feel: rider animation, lean, camera, sounds and particles | Done, except the outside-tester gate | [playtest](docs/playtest-phase3.md) |
| 4 | Terrain park: rails, boxes, ramps, pipes, the park wrench, grinding | Done, hand checks open | [playtest](docs/playtest-phase4.md) |
| 5 | Tricks and scoring: spins, flips, grabs, grinds, combos, the server's validation | Done | |
| 6 | Board and ski customization: the Paint Bench, editor, share codes | Done, playtest open | [playtest](docs/playtest-phase6.md) |
| 7 | World generation: groomed runs, resorts, parks, snow features | Done, playtest open | [playtest](docs/playtest-phase7.md) |
| 8 | Ski lifts: lines, chairs, boarding, sync, generated resort lifts | Done (merged 2026-11-06), playtest open | [plan](docs/phase8-plan.md), [playtest](docs/playtest-phase8.md) |
| 9 | Create compatibility and polish: the kinetic drive, wrench support, tags, the balance pass, the server guide, optimization, the release pipeline | **Next** | [plan](docs/phase9-plan.md), [server guide](docs/server-guide.md) |

Development ends with phase 9: the compatibility matrix passes, and the release pipeline's dry run
is correct. Then, at the owner's call, comes the release: a candidate soaked for two weeks with no
open blockers, then publishing to Modrinth, CurseForge and GitHub Releases.

## Build and test

Requires a JDK 21 to run Gradle (Architectury Loom 1.11); the mod itself compiles and runs on a
Java 17 toolchain, which Gradle downloads if none is installed.

| Task | Command |
| --- | --- |
| Build all jars, run unit tests | `./gradlew build` |
| Regenerate lang, recipes, tags, loot | `.github/scripts/run-datagen.sh datagen.log` (writes `common/src/main/generated`; commit the result) |
| Game tests, Fabric dedicated server | `./gradlew :fabric:runGametest` |
| Game tests, Forge game-test server | `./gradlew :forge:runGameTestServer` |
| Client smoke test (Forge, needs a display or `xvfb-run`) | `./gradlew :forge:runSmokeClient`; real terrain: `:forge:runMountainClient`; park: `:forge:runParkClient`; structures: `:forge:runWorldgenClient` |
| Multiplayer smoke (dedicated server and three clients) | `.github/scripts/multiplayer-smoke.sh <log dir>` |
| Play-test | `./gradlew :forge:runClient` (see the note on the Fabric dev client below) |
| Regenerate placeholder art | `python3 art/placeholder/generate_textures.py && python3 art/placeholder/generate_models.py` |
| Regenerate placeholder clips, sounds, particles, overlays | `python3 art/placeholder/generate_animations.py`, `generate_sounds.py` (needs ffmpeg), `generate_effects.py` |
| Regenerate park blockstates, models and textures | `python3 art/placeholder/generate_park.py` |
| Regenerate default gear looks and the Paint Bench | `python3 art/placeholder/generate_gear_atlases.py`, `generate_bench.py` |
| Regenerate the park, snow feature and lodge templates | `python3 art/placeholder/generate_structures.py` |

CI (`.github/workflows/build.yml`) runs all of the above except the play-test client, fails if
committed generated data is stale, uploads the jars, and force-pushes logs, test reports, generated
data and the smoke test's screenshots (`client-smoke/screenshots`) to the `ci-logs` branch so they
can be read with plain git.

Known issue: `./gradlew :fabric:runClient` does not start in the development environment. Trinkets
3.7.2's `ClickableWidgetMixin` shadows a field by its Yarn name, which does not exist under the
Mojang mappings this project develops with. Production Fabric (intermediary names) is not affected.
Play-test Fabric with the built jar in a normal Fabric 1.20.1 instance.

Known issue: `./gradlew :fabric:runDatagen` writes every file and then the JVM does not exit
(the thread dump shows only daemon threads plus a process reaper). The script above waits for
datagen's final "Caching: total files" line, stops the process, and judges success from the log.
Running the Gradle task directly works too; stop it with Ctrl+C after that line appears.

Jars: `forge/build/libs/alpineshred-forge-*.jar` (tag it for both Forge and NeoForge when
publishing) and `fabric/build/libs/alpineshred-fabric-*.jar`.

## Pinned versions (2026-10-28)

| | Version | Note |
| --- | --- | --- |
| Minecraft | 1.20.1 | |
| Forge | 47.4.28 | latest and recommended 1.20.1 promotion |
| Fabric Loader / API | 0.16.14 / 0.92.6+1.20.1 | 0.92.12 exists; 0.92.6 is the baseline Trinkets was built against |
| Architectury API / Loom / plugin | 9.2.14 / 1.11.458 / 3.4.162 | |
| PlayerAnimator | 1.0.2-rc1+1.20 | risk R1: Player Animation Library has no 1.20.1 builds, so ship on PlayerAnimator and move to PAL with the 1.21 port |
| Curios (Forge) | 5.14.1+1.20.1 | |
| Trinkets (Fabric) | 3.7.2 | |
| Create | 6.0.8-291 (Forge) / Create Fabric 6.0.8.1 | optional; compile-only, wired in phase 9 (clarification 77) |

## Layout

```text
common/   all game logic (Architectury); client code only under client/
  movement/   pure, unit-tested: RideState, RideSnapshot (packed synced int), SlideModel,
              SlopeMath, GroundContact, Ballistics, MovementTuning, SurfaceProfile,
              SlideController (the riding controller, written against a Body interface),
              RideInput, RideContext, RideValidator, RideTransitions, StateSendThrottle
              game side: PlayerBody, RideRunner, RideHooks (what the mixins ask), RideServer
              (claims, validation, wear, wipeout damage), SurfaceResolver, Riders,
              StrapCheck, StrapController, TuningData
  gear/       items, armor material, GearStats (pure), GearWear (pure), GearAccess,
              GearProfile(s), wax and lens recipes
  feel/       pure, unit-tested: RiderClips (which clip for which state, one-shot director),
              LeanRig (procedural lean pose), RideSoundRules, CameraEffects, ScreenEffects,
              ParticleRules
  park/       pure, unit-tested: V3, Facing, RailShape, RailPath, RailPlacement (smart
              placement), GrindPath, GrindPhysics (catch, ride, join, leave), RampSlope, PipeArc,
              PipeSection, BoxMaterial, ParkView (what the controller asks the world)
    block/    RailBlock, CopperRailBlock, RailPostBlock, BoxBlock, PlasticBoxBlock, RampBlock,
              PipeBlock, PipeCopingBlock, ParkDecorBlock, ParkStates, ParkShapes, ParkCollision,
              GrindPaths (the GrindPathProvider registry), BoxMaterials, LevelParkView, ParkChecks
    item/     PipeItem (a whole pipe), ParkWrenchItem
  trick/      pure, unit-tested: AirRotation, TrickAngles, LandingQuality, TrickScorer, Combo,
              TrickValidator, AirReport, GrindReport, TrickResult, TrickCodec
              game side: TrickServer (sessions on the rider clock, reports, combos), TrickEvents,
              advancement triggers
  design/     pure, unit-tested: DesignKind, Outline, Design, DesignCodec, ShareCode,
              DesignAtlas, GearMesh
    paint/    pure, unit-tested: Canvas, PaintTools, EditorState, UndoStack, Colors,
              DesignTemplate(s)
              game side: DesignNbt, PaintBenchBlock, PaintBenchMenu, DesignServer, DesignEvents
  worldgen/   pure, unit-tested: RunPlanner (section 12's planner), RunShape (what a run does to
              each column, markers, bounds)
              game side: GroomedRunStructure, ResortStructure, ToggleableJigsawStructure (parks
              and snow features), GroomedRunPiece, LodgePiece, GeneratorTerrain, WorldgenToggle,
              ModStructures
  config/     ConfigSpec + AlpineConfig: both JSON files, every key from the doc
  data/       reload listeners for tuning, surface profiles and gear stats
  network/    ModNetwork (packets), SyncCodec (pure payloads), RideAction, PacketRateLimiter
  client/     RideDriver (runs the local player's controller), ClientNetwork, RideDebugOverlay,
              key binds, gear render layer, ClientSmoke (the scripted client test), CameraOrbit
    feel/     RiderTracker (per tick, every rider in 48 blocks), RiderView (what a rider is doing,
              from the controller or the synced state), RiderAnimator (the only PlayerAnimator
              code), LeanLayer, RiderSoundManager, RiderParticles, RideEffects (camera, HUD),
              FeelStats
    particle/ snow spray, poof and trail particles, rail sparks
    park/     ParkOverlay: the rail ghost preview and the wrench's path overlay
    trick/    TrickHud (the popup), TrickNames
    design/   DesignedGearRenderer, DesignTextureCache, PaintBenchScreen, DesignEditorScreen,
              DesignClient
  lift/       pure, unit-tested: LiftPath (the cable loop), LiftMotion, LiftStations, LinkValidator,
              PassengerRule, BoardingRule, AutoBoard, DismountRule, EjectCrossing, PendingLine
              game side: LiftLine, LiftSavedData, LiftManager (steps every line), LiftChairs
              (spawns chairs near players), ChairEntity, Boarding, LiftLinkerItem, the six lift
              blocks and their block entities (LiftNodeBlockEntity and its three kinds),
              LiftDriveSource, LiftSync and LiftLineSync (the packet), LiftCommands
    client/lift/  ClientLiftLines and ClientLine (the synced lines, stepped each client tick),
              TowerHeadBER, BullwheelBER, ChairEntityRenderer, Cables, LiftSoundManager,
              LiftBoardClient (auto-board), LiftRenderStats
  gametest/   game tests shared by both loaders; Course builds riding courses; LiftGameTests
  smoke/      ServerSmoke (the multiplayer server's script, with the lift stage), SmokeCourse
              (the run and the lifts), WorldgenSurvey (finds, builds and counts one of each
              structure for the worldgen smoke run)
  mixin/      PlayerMixin (synced state, travel, edge back-off, jump, footsteps, sneak dismount
              from a lift chair), EntityMixin (step height, descending), PowderSnowBlockMixin,
              LivingEntityMixin (no sprint), MobAccessor (persistence of boarded mobs),
              ServerGamePacketListenerAccessor; client/LocalPlayerMixin (no auto-jump),
              GameRendererMixin (camera roll, FOV, screen effects under the GUI),
              ItemInHandRendererMixin (no first-person arms while sliding), ItemInHandLayerMixin
              (poles drawn by the gear layer), CameraMixin (CameraOrbit), LevelRendererMixin
              (park overlays after the world), BlockEntityWithoutLevelRendererMixin (the built-in
              item renderer hook)
forge/    entry point, CuriosGearAccess, Curios slot data, platform hooks, game-test holder
fabric/   entry points, TrinketsGearAccess, Trinkets slot data, datagen, game-test holder
art/      placeholder generators: textures, models, rider clips, sounds, particles, overlays,
          park blockstates and models, default gear atlases, the Paint Bench, structure templates
```

## What phase 8 delivers

- **Lift lines** (section 13): a drive station at the top, tower heads in link order and a return
  station at the bottom, joined by one `LiftPath` (the cable loop: a sagging catenary-style span
  between nodes, a half circle round each bullwheel, sampled every 0.25 blocks). A line is one
  record in the level's `alpineshred_lifts` saved data (`LiftSavedData`): its nodes, `travel`,
  `speed`, the drive's command and the `faulted` and `emergency` flags; the path, chair count and
  boarding and unload intervals are rebuilt on load. `LiftManager` steps every line each server
  tick with `LiftMotion` (a speed ramp of 0.5 b/s squared, 2.0 for a faulted or emergency stop;
  `travel += speed / 20`) and runs lines whose chunks are unloaded when `lift.runUnloaded` is on.
- **Six blocks** (`lift_tower_pole`, `lift_tower_head`, `lift_drive_station`,
  `lift_return_station`, `lift_loading_gate`, `lift_stop_button`) and two items. The
  `lift_linker` builds a line: right-click the drive, each head in order, then the return
  station; `LinkValidator` (pure) checks span, slope, cable clearance on both sides, head count,
  length and line count, and names its reason when it refuses. Breaking a node, or a node whose
  stored data disagrees with its line when it first loads, faults the line, which stops at 2.0 b/s
  squared and stays stopped until relinked. The drive is run by redstone (right-click cycles
  `lift.speedLevels`; losing the signal while running is an emergency stop); the stop button
  stops the nearest line. `/alpineshred lift list | info | stop | start | remove` (permission 2).
- **Chairs** (`ChairEntity`): placed every tick from the line's `travel`, on both sides, so they
  never depend on move packets. They spawn when a player is within 128 blocks of a node and empty
  ones despawn past 192. A chair seats `lift.seats` riders (two by default); players and mobs
  that pass `PassengerRule` can board (tags `lift_blocked` and `lift_passengers`), and a boarded
  mob is persistent until it gets off.
- **Boarding and riding** (sections 7, 8, 13, 17): right-click a chair, or stand strapped in the
  boarding stretch (the first 4 blocks of loaded cable) and the client boards the next chair that
  passes (`c2s/lift_board`; the server re-checks distance, seat, speed and `lift.requirePass`).
  The strapped rider enters `LIFT`; the bar closes 20 ticks later and a right-click toggles it. At
  the ejection point, one block before the end of the loaded cable, riders are put on the
  platform and a strapped player is set going in `GLIDE`; that counts a `lift_rides` and
  triggers `lift_ridden` (the `bluebird_day` advancement is 100 blocks). Sneaking off is allowed
  with the bar open, within 4 blocks of the ground, on a faulted line, or with
  `lift.dismountAnywhere`. Leads and tamed pets board with their owner. A rider who logs out is
  saved with the chair and comes back on the same slot; if the line is gone they are put on safe
  ground.
- **Sync** (section 15): `s2c/lift_line_sync` (nodes, spacing, travel, speed, target, game time,
  flags, `removed`) goes to a player within `lift.syncRadius` of any node, on a speed, target or
  flag change, every 100 ticks, and on removal. `ClientLiftLines` steps each line with
  `LiftMotion` and catches a synced one up to the client's game time.
- **Rendering and sound** (sections 9, 16): cable, sheaves and bullwheels turning with travel,
  chairs with a bar that eases down over 6 ticks, seated clips for boards and skis, motor, cable
  creak, bar and e-stop sounds, all placeholder art.
- **Generated lifts** (section 12): each ski resort gets a line of about 200 blocks from the top
  of its run to the lodge, planned by `ResortLiftPlanner` against the generated terrain and
  skipped if it would not pass `LinkValidator`. `LiftLinePiece` places its blocks with a
  `PendingLine`, which the first block entity to load adopts, once.
- **Tests.** Unit: `LiftPathTest`, `LiftMotionTest`, `LiftStationsTest`, `LinkValidatorTest`,
  `PassengerRuleTest`, `BoardingRuleTest`, `AutoBoardTest`, `DismountRuleTest`,
  `EjectCrossingTest`, `LiftSyncTest`. Game tests on both loaders (`LiftGameTests`): the saved
  data round trip, travel at fixed ticks, the fault paths, drops, the linker, chair positions and
  spawning, mobs, the catch-up of a client line, boarding, riding to the top, sneak dismount,
  leads and pets, relog and line removal with riders, the redstone drive, the stop button, the
  commands, a resort's generated line, and phase 8's last two: `liftSurvivesSaveReload` (the
  saved data and the rider's NBT, through a real log out and in) and `liftManagerBudget`. The
  `lift` client smoke builds a line, pictures it, rides it and checks the renderers' cost and
  sounds. The multiplayer smoke gains a lift stage (below).

### Three clients ride a 400-block lift (CI step 9)

After the run, `ServerSmoke` builds a lift 30 blocks east (`SmokeCourse.longLift`: five nodes
climbing 40 blocks over 208, a loop of over 400 blocks) and puts the three players on it one at
a time, strapped in at the bottom station, 80 ticks apart so that each rides a chair alone; their
own clients auto-board. Every ten game ticks the server logs where each chair is
(`chair gt<time> <index> x y z`) and each client logs the chairs it sees (`lchair ...`);
`multiplayer-check.py` compares them by game time and chair and fails if over 5% of a client's
samples are off by more than 0.25 blocks or there are fewer than 50. The server kicks `watcher1`
100 ticks after it boards; the client reconnects and the server checks that it is on a chair of
the same line and slot again. Near the top (40 blocks of cable short of the ejection point) a
`setblock` command removes the topmost head: the line must fault, slow at 2.0 b/s squared (the
mean drop over the ticks above zero, within 0.01), and each rider's client sneaks off the faulted
line. The server's validator must flag nobody on the lift.

## What phase 7 delivers

- **Four structures in snowy biomes** (section 12), all data under `data/alpineshred/worldgen/`
  (structures, sets, template pools) and `tags/worldgen/biome/has_*`, so datapacks can retune or
  replace them: `terrain_park` (spacing 28 / separation 10 chunks), `groomed_run` (22 / 8),
  `ski_resort` (56 / 24, 6 chunks clear of runs) and `snow_feature` (10 / 4). Each type asks its
  `worldgen.*` switch before placing; `worldgen.frequencyMul` below 1 skips that share of starts.
  `/locate structure alpineshred:<id>` and `/place structure` work unchanged.
- **Groomed runs** (`RunPlanner`, `RunShape`, `GroomedRunPiece`): the planner of section 12 on the
  generator's own heights (start at least sea level plus 20 on a 0.15 to 0.60 grade; green, blue
  or black from the seed; 8 headings every 8 blocks; stops at the length, flat ground, water or the
  biome's edge; a 16-block run-out; Catmull-Rom smoothing every 2 blocks; width 9, 11 or 13). Each
  chunk builds only its own columns: cut or fill by up to 2 blocks to the never-climbing profile,
  groomed snow over two snow blocks, trees, plants and snow layers cleared 10 blocks up, edges
  fading into snow layers over 3 blocks, slope markers in the difficulty's color on both edges
  every 24 blocks. Snowfall can lay snow layers on a run, as on any snow.
- **Ski resorts** (`ResortStructure`, `LodgePiece`): a run and, past its run-out, the lodge (24 by
  18, spruce, a fireplace, tables, lanterns, a Paint Bench), its door turned up the run and its
  floor level with the run's end, on a cobblestone foundation; its chest uses
  `alpineshred:chests/lodge`. The lift line comes with lifts in phase 8.
- **Terrain parks** (jigsaw, `park/entry`, `park/lines`, `park/ends`): an entry gate, then up to
  five lines, each starting a block lower than the last: flat, kinked and curved rails, a box line,
  a kicker with table and landing, a quarter pipe pair, a jib line; ending in a bench and fence or
  flags. Walking into one awards `park_rat`.
- **Snow features** (`snow/features`): natural kickers, rollers and cornices built from snow.
- **Templates** are written by `art/placeholder/generate_structures.py` (vanilla structure NBT),
  so the pieces' block states (rail connections, legs, box ends, pipe indices) come from the same
  rules as placing them by hand.
- **Tests.** Unit tests for the planner on synthetic slopes (starts, stops at water and biome
  edges, smoothing, the profile never climbing, 1000 random slopes within reach, markers, the
  seed); game tests that every template is whole (park pieces 9 wide, jigsaws where the pools
  expect them, rail flags as the rails' own rules set them, valid pipe cells, the lodge's chest
  marker) and that the four structures, generated over real overworld noise for three seeds,
  give over 1000 starts with every piece within 7 chunks of its start and inside the world's
  height; a worldgen smoke run in a default world finds, builds, counts and pictures one of each
  (`client-worldgen/screenshots`) and checks `park_rat`.

## What phase 6 delivers

- **Paint Bench** (`paint_bench`, section 22): a crafting table plus red, blue and yellow dye. Its
  menu has a slot for a board or skis and one for the dye a save costs; placing gear opens the
  editor. Like a crafting table it keeps nothing: closing it hands both back.
- **The editor** (`DesignEditorScreen`, logic in `design.paint.EditorState`): front and back
  canvases (64 by 16 for boards, 64 by 8 for skis; pixels outside the gear's outline are locked),
  pencil, eraser, fill, line, rectangle, gradient (linear or radial, middle stop, ordered dither,
  lock transparent), pattern fill, eyedropper and mirror, with section 22's keys (B E F L R G P I
  M, X, [ ], #, Tab, Ctrl+Z / Ctrl+Y, 32 undo steps, one per stroke); a turning 3D preview; a
  hue-saturation-value picker with hex field, the 32-swatch palette and the 8 most recent colors;
  edge color, ski pair (same or mirror), copy to the other face, a name, twelve templates, share
  codes (`AS1:`), PNG export to `config/alpineshred/exports/`, Reset and Save.
- **Saving** (`c2s/design_save`, `s2c/design_result`, `DesignServer`): the server checks the seven
  rules (bench open with the item in it, sizes and strict inflation, alpha, outline, name, size
  cap, permission, cooldown), writes the author itself, and takes the dye in the same step as the
  write. `DesignEvents.BEFORE_SAVE` can refuse a save; `DesignEvents.SAVED` follows each one.
- **Data** (`Design`, `DesignCodec`, `DesignNbt`): the `Design` tag of section 22 (deflated RGBA
  faces, edge, pair, name, author, CRC32 hash); other keys untouched; an unknown version or bad
  data draws the default look and is left as it is.
- **Rendering** (`DesignedGearRenderer`, `GearMesh`, `DesignTextureCache`): boards and skis are
  drawn procedurally (tail kick, flat middle, nose kick; walls that follow the outline) from one
  atlas per design, in one cutout render type, everywhere: riding, carried on the back, held,
  dropped, in inventories (a `builtin/entity` model through the built-in item renderer hook) and in
  the bench preview. The cache keeps `client.designCacheSize` textures, builds at most two a frame,
  and clears on a resource reload; `client.showCustomDesigns` hides other players' designs.
  Default looks are `textures/gear/<item>.png` in the same atlas layout, so packs can re-skin them.
- **Templates** (`data/<namespace>/design_templates/*.json`): twelve built in (solid, stripes,
  pinstripe, checker, diagonal split, sunset, glow, camo, polka dots, racing stripe, two tone, retro
  fade); packs add more, and the server sends its set to clients (`s2c/design_templates`).
- **Moderation**: `design.enabled`, `design.minPermissionLevel`, `/alpineshred design info` and
  `/alpineshred design clear <players>`, the author in every tooltip, and the advancement
  `fresh_coat`.
- **Tests.** Unit tests for the codec (round trips, wrong sizes, partial alpha, oversize data,
  decompression bombs, the outline, names, share codes, the hash), the tools (fill, gradients and
  dither, mirror, patterns, undo, the editor's strokes and faces), the templates and the color math,
  the mesh, and the assets; game tests saving through the bench with and without dye, rejected
  saves charging nothing, smithing and a save and load keeping the design; the smoke client paints
  and saves through the real editor and checks the server's copy; in the multiplayer run the rider's
  board carries a design, and both watchers must receive it through the gear slot and draw it.

## What phase 5 delivers

- **Tricks in the air** (in `SlideController`, rates in `AirRotation`). A and D spin (ramping to 24
  degrees per tick on a board, 28 on skis), W and S flip, Z X C V grab Indy, Mute, Nose and Tail,
  sneak grabs Method; a grab slows rotation by 20%. The board turns with the spin; flips turn the
  rider's body (a snowboarder rolls, a skier pitches).
- **Landing grades** (`LandingQuality`). The board's angle to the direction of travel (a switch
  landing counts as straight), the flip left over and the descent against the slope landed on (a
  quarter pipe's arc included) give PERFECT, CLEAN, SKETCHY (40% speed lost) or BAIL (a wipeout),
  widened by the gear's landing forgiveness. Off a rail or box the board turns out of the slide by
  itself. The landing clip, sound and snow poof follow the grade.
- **Scoring** (`TrickScorer`, `Combo`). Section 8's catalog: spins by half-turns, flips, grabs by
  time held (mitts add 10%), grinds and box slides per second by variant, switch entries, switch-ups,
  spin in and spin out, air time and height; variety and combo multipliers, landing factors, the
  repeat rule and the `trick.maxComboScore` cap. A combo continues while the next landing or grind
  entry comes within 60 ground ticks at speed, banks when it runs out, and a bail loses it.
- **Server authority** (`TrickServer`, `TrickValidator`). The client reports each air session with
  something to score and each rail or box (`c2s/air_report`, `c2s/grind_report`); the server times
  every session itself from the rider's state claims on the rider clock, and checks the reports
  against that (air ticks, apex, time on the rail), the rail or box in the world, the spin and flip
  rates, and the landing heading against the spin, then grades the landing with the slope it reads
  from the world and scores it. A forged report scores nothing and counts as a violation.
  Results go back as `s2c/trick_result` (to the rider, or as `trick.broadcast` says) and fire
  `TrickEvents.LANDED` for other mods.
- **Popup.** At the top center: the combo's tricks stacking up, its running score and multiplier,
  and a timer bar draining over the 60 ground ticks. On a bank, the total and a chime; on a bail,
  "Bail!" and a thud. Every number is the server's.
- **Statistics and advancements.** Custom stats `trick_score`, `tricks_landed`, `air_time`,
  `grind_distance`, `lift_rides`, `ride_distance` and `best_combo`; triggers `trick_landed` and
  `grind_completed`; advancements `root`, `first_run`, `waxed_up`, `first_grind`, `first_air` and
  `spin_cycle`; `/alpineshred stats [player]`.
- **Animation.** Ten grab clips (five each for board and skis), flips turning the body root, and
  the perfect, clean and sketchy landing clips. Remote riders' grabs and flips come from the synced
  state's grab and rotation flags, which the server now passes on in the air.
- **Tests.** Unit tests for the catalog, landing windows, combo formula, report bounds and wire
  formats, and the controller's spins, flips and grabs; game tests sending forged and honest reports
  through the server (rejected and counted; scored exactly), a report arriving before its landing's
  claim (the order a real client sends them in), chaining and banking into the stats, a bail losing
  the combo, grind reports needing their rail, and the advancements; and the smoke run spins a 180
  off the kicker and checks the popup banked the same total the server did (the kicker's air is
  short of the 17 ticks a 360 takes; the game tests and unit tests score 360s).

## What phase 4 delivers

- **Rails** (`RailBlock`, section 10). `pipe_rail`, `flat_rail`, the copper pipe rail in four
  oxidation stages and their waxed versions, and `rail_post`. Pieces are `FLAT`, `RISE_A`, `RISE_B`
  (a one-block rise is two pieces) and `CURVE`, in four facings, with the section 10 paths. One
  multipart model per block: the bar, an end cap on each end no piece continues, and a leg where
  the rail stands on solid ground (none on a post, fence or wall). Collision is a thin bar at the
  path; for riders it is solid only from above, so nothing snags a rider on the rail or beside it.
- **Smart placement** (`RailPlacement.choose`, one pure function for the server and the client
  preview). Pieces continue an open end, climb where the ground steps up a block (both halves of
  the rise are picked automatically), fall where it drops, and turn the last piece of a line into
  a curve when placed beside it, as vanilla rails do; sneak places a straight piece. While a rail
  is held, a see-through ghost of the piece shows where it goes, green when it joins a line.
- **Grinding** (in `SlideController`, physics in `GrindPhysics`). Catch rules, the grind assist
  (0.65 within 20 degrees of the rail), piece to piece along a line, slope and friction (waxed,
  oxidized and board wax change it), switch-ups on A and D, the press on sneak, pop off with the
  rail's velocity, and off the end into the air or onto the ground. Fences and walls grind
  (`#alpineshred:grindable_vanilla`), as does `pipe_coping`; `GrindPaths.register` takes more.
- **Boxes** (`BoxBlock`: `wood_box`, `metal_box`, `plastic_box` in 16 colors). Low (sneak) or
  full, along the axis placed, bevelled where the row ends, waxed with honeycomb, recolored with
  dye. Anything in `#alpineshred:slide/boxes` slides, slabs as low boxes, with friction from
  `data/alpineshred/box_materials.json`; steering drops to 30% on a box.
- **Ramps** (`park_ramp`). Quarter, half and full slopes as eight 2-pixel steps per block, placed
  in a row they continue one slope. The wrench marks the lip; jump just after leaving a lip adds
  20% pop.
- **Quarter pipes** (`quarter_pipe`). One item places a whole radius 3 pipe rising the way you
  face (sneak: radius 5); breaking any block removes it. The controller rides the arc itself, back
  down onto the flat, or over the top straight up and back in. `pipe_coping` is the lip block.
- **Park wrench.** Cycles a rail through the pieces that keep its line joined (says so when only
  one fits), turns it with sneak; changes a ramp's slope and lip, a box's height and axis, turns
  coping and decor. While held it draws every rail path nearby.
- **Wax and copper.** Honeycomb waxes rails and boxes; an axe takes wax off and scrapes copper
  back a stage; unwaxed copper rails weather on random ticks (`rail.oxidation`).
- **Feel.** Grind loops for metal and copper rails, slide loops for wood, metal and plastic boxes,
  a clank on the catch, rail sparks (green off copper) and box dust, and five grind clips
  (`grind_5050`, `grind_nose`, `grind_tail`, `boardslide`, `slide_skis`) picked from the board's
  angle to the rail.
- **Server.** `GRIND` and `SLIDE` claims need a rail in the catch zone or a box underfoot. The
  validator counts a rider on a rail, box or pipe arc as supported, allows a pipe's rise and its
  top launch, and treats any server teleport as a fresh start.
- **Park decor.** Green, blue and black slope markers, `park_flag` and `start_gate`, for
  generated parks (phase 7).
- **Tests.** 15 park game tests per loader (placement and joins, the 10-piece chain, box slide,
  pipe ride and pipe launch, lip ramp, wrench, wax and oxidation, fences and walls, park claims,
  and three replays through a real server connection), unit tests for the pure geometry,
  placement and grind physics, and the park smoke run with screenshots in `client-park/`.

## What phase 3 delivers

- **Rider animation** (`RiderAnimator`, on PlayerAnimator). 22 placeholder clips in
  `assets/alpineshred/player_animation/`: idle, strapped walk, glide (regular, goofy, skis), tuck,
  brake and snowplow, pop charge and launch, air, three landings, two wipeouts, pole carry and
  pushes. The base clip follows the ride state, cross-faded over 4 ticks; a procedural lean layer
  tilts the body into turns about the feet, counter-rotates the torso and crouches with speed and
  pop charge; a hands layer carries and pushes poles. Snowboarders' bodies turn sideways by stance,
  and strapped riders stand on the board or skis (drawn a half pixel above the snow) instead of
  sinking it. Ski poles show as a pair, one in each hand, rather than the held inventory sprite.
  The head is never animated, so it follows the player's look. Every client animates every rider
  within 48 blocks from the synced state; there are no animation packets.
- **Camera.** Roll into carves (up to 6 degrees) and into wipeouts, speed field of view from 8 b/s
  (up to +18 degrees, scaled by `client.fovEffect`), and no first-person arms while sliding.
  `client.reduceMotion` turns off the roll, the field of view change and the speed lines.
- **Sound.** 16 sound events with subtitles. Glide loops per surface (groomed snow, ice, powder),
  scrape, carve and brake loops, each faded per tick from speed and lean; pop, soft and hard
  landings, wipeout, pole push, strap in and out, wax and edge tuning. Placeholder audio is
  synthesized (`generate_sounds.py`) until real recordings replace the files.
- **Particles.** Snow spray off carves and brakes, a poof on landings and wipeouts, block dust off
  other surfaces, and fading carve trails (`client.carveTrails`).
- **HUD.** Goggle lens tint and frame in first person (`client.gogglesOverlay`), speed lines, a
  whiteout vignette in snowfall without goggles, a flash on hitting powder at speed without
  goggles and a red pulse on wipeouts (both off with `client.flashSafe`), and, with
  `client.hudSpeed`, a speedometer and the board's wax and edge, scaled by `client.hudScale`.
- **Client smoke test** (`ClientSmoke`, CI step 6). A Forge client on a virtual display builds a
  snow run in a flat world, rides it on a snowboard and then on skis with poles (skate, pole push,
  carve both ways, pop, kicker, powder, brake), and photographs each moment from behind, from the
  side and in first person, then every clip in a still gallery. It fails on a mixin that does not
  apply, a crash, or a rider that never slides, carves, launches, makes a sound, spawns a particle,
  rolls the camera or widens the field of view.

## What phase 2 delivers

- **Riding.** While strapped, the local player's movement goes to `SlideController` (a mixin at
  the head of `Player.travel`): slope sampling with smoothing, ground snap, step-up on snow at
  speed, ramp launches, pop (charge on jump, release to pop), wall wipeouts that lock input for 30
  ticks, powder floating above the float speed, skating on flat snow, pole pushes on skis, and
  braking. Off snow below `scrapeSlideMinSpeed`, or sinking in powder, the rider walks with the
  -35% strapped modifier.
- **Sync.** The client sends its state with `c2s/ride_state` (on change, at most 10 per second);
  the server checks it against the transition table and writes it into the synced data every client
  animates from. `c2s/ride_action` reports pops, launches and pole pushes. At login and after every
  datapack or tuning reload the server sends `s2c/config_sync` (with a protocol version: on a
  mismatch the client turns riding off and says why) and `s2c/tuning_sync` (tuning, surface
  profiles and gear stats, compressed).
- **Server validation** (`RideServer`, `RideValidator`): horizontal speed over a window, rise
  (step-up on the ground, launch allowance after a reported pop or launch, a ballistic budget in
  the air), AIR claims while standing, refused transitions, and dropped packets each count as a
  violation; three within 40 ticks send the rider back to its last valid position in WIPEOUT with
  `s2c/ride_correct`, in strict mode (`movement.validation.strict`, on by default for dedicated
  servers). Without strict mode the server logs what it would have corrected.
- **Server-side wear and damage.** Distance comes from accepted positions: wax per block on snow,
  edges while carving, durability by surface (scraping is fast), and a broken board leaves the slot.
  Wipeout damage is `(impact - 0.5) * 8` from the speed the server observed, 40% less with a helmet.
- **`/alpineshred debug ride on|off`** (level 2): an overlay of the controller's state and mode,
  speed, heading and lean, slope grade and downhill direction, surface, ground contact and charge,
  next to the state the server echoed and the server validator's counts.

### Risk R2: closed

Two game tests (`vanillaAcceptsARideAtTheSpeedCap`, `vanillaAcceptsKickerAir`) run the real
controller on a test player as the "client" and send every tick's position through the dedicated
server's real `handleMovePlayer`, then the client's `ride_action` and `ride_state`, then the
server's per-tick checks. The courses cover the 28 b/s cap, a pop, a one-block step-up taken at
speed, a voxel staircase, braking (sneaking) off a ledge, and a kicker launch with 14+ ticks of air. Any
rubber-band (a pending teleport or a position mismatch), a floating kick, a validator violation or
a wipeout fails the test; a final move through a wall proves the harness sees rubber-banding at all.
No relaxation of vanilla's checks was needed. What the server replay needs instead is in
`RideHooks`: no edge back-off for strapped riders (sneaking brakes, and back-off would land the
replay short of the client), and vanilla step height (a client step-up arrives as an upward delta,
which the replay follows without stepping).

### Three clients see correct state (CI step 9)

`.github/scripts/multiplayer-smoke.sh` starts a Forge dedicated server (`:forge:runSmokeServer`,
`ServerSmoke` builds the run beside spawn and stages the players) and three Forge clients that
join it by quick play: `rider` rides the smoke run with simulated keys while `watcher1` and
`watcher2` hover beside the slope and the kicker, look at the rider and log the state and position
their client was sent for it. It passes when the server's validator saw nothing wrong, both
watchers saw the rider glide, carve, jump and brake and travel the run with its animation
changing, and `multiplayer-check.py` finds every watched position within a block of the rider's own
track (allowing for the few ticks a remote player is drawn behind) and every watched state one the
rider was in. Watcher screenshots are on the `ci-logs` branch under `multi/`.

## What phase 1 delivers

- All 21 items from the appendix plus `groomed_snow`, with recipes (datagen), tooltips (stats,
  wax, edge), dyeable boots/helmet/mitts, goggles lenses, the `alpine` armor material, and the
  `wax_apply` and `goggles_lens` special recipes. The edge tuner works; the wrench, linker and
  lift pass are described placeholders until their blocks ship.
- Snow gear, goggles and mitts slots on Curios (Forge) and Trinkets (Fabric) behind `GearAccess`,
  and a cached `GearProfile` rebuilt on equip events with a 10-tick fallback.
- Strap in/out on `G`, validated on the server: gear, matching boots, vehicle, fluid, elytra,
  spectator. Auto-unstraps on fluid, vehicles, death, dimension change, gear or boot removal.
  While strapped: -35% walk speed, no sprinting.
- The packed rider state as synced entity data, so every client sees every rider's state.
- `SnowGearLayer` draws goggles, mitts, and the board or skis (under the feet when strapped,
  on the back otherwise) for both loaders.
- `/alpineshred strap`, `config reload`, `tuning reload`. Both config files with every key.
- 83 unit tests (all section 19 feel targets among them) and 11 game tests per loader at the end
  of phase 1.

### Section 19 feel targets, measured on the default tuning

| Target | Measured |
| --- | --- |
| 20 degree groomed: 20 b/s in 6 to 9 s, under the 28 b/s cap | 7.2 s, levels off at 26.8 b/s (voxel staircase: same, no AIR flicker) |
| 5 degree groomed settles at 10 to 14 b/s | 12.1 b/s |
| 10 b/s on flat snow coasts at least 50 blocks | 71 blocks |
| Half-slope ramp at 16 b/s: apex 0.7 to 1.2 | 0.89 |
| Full-slope ramp at 16 b/s: apex 2.5 to 3.8, 16+ air ticks | 3.07, 18 ticks |
| 10-piece rail at 8 b/s exits with 80%+ | 84.9% |
| 360 off a full-slope ramp | done at tick 17 of 18 (meets the target, little margin) |

## Design clarifications made while building

These are places where the doc was ambiguous, inconsistent, or could not work as written. Each is
covered by a test or noted in code.

1. **Durability is not data-driven.** Item max damage is fixed at registration and 1.20.1 Fabric
   has no per-stack hook, so the `gear/*.json` files carry the five multipliers only; durability
   comes from the section 5 table, and `gear.durabilityMul` tunes wear.
2. **Gear stats sync to clients.** The client-side slide controller reads them, so
   `tuning_sync` carries gear stats along with tuning and surfaces.
3. **Ground snap distance** is `snapDown + speed`. A fixed 1.0 cannot follow a 45 degree voxel
   slope at the speed cap (up to 2 blocks of drop per tick) without flickering into AIR.
4. **Ramp launch grade** uses the ramp's declared slope (park ramps) or, on terrain, the steeper
   of the rider's per-tick climb and the terrain grade at the lip, capped at 45 degrees. Averaging
   over ticks understates a one-block kicker; a ramp of snow layers climbs in steps the last ticks
   may not cross. The lip is the highest recent ground sample, cast back one and two blocks: vanilla
   collision resolves the vertical part of a move first, so at speed a rider ends a tick hovering
   past a step edge while reported on the ground, and that position must not read as a ramp top.
5. **Switch riding.** Step 4's slip formula, taken literally, brakes a rider who travels
   tail-first. Steering, grip and slip use whichever end leads, with 10 degrees of stance
   hysteresis, and looking more than 135 degrees behind is treated as looking over a shoulder.
6. **"Speed scales glide"** is implemented as drag divided by speed squared, so terminal speed
   scales with the stat; neutral gear matches the doc formula exactly.
7. **Armor texture name** is `alpineshred_alpine` in the `minecraft` namespace. A namespaced
   material name breaks vanilla's armor layer on Fabric.
8. **Trinkets data paths** are `data/trinkets/slots/<group>/<slot>.json`, and Trinkets ships no
   slots, so `head/face` and `hand/glove` are declared here too. Trinkets 3.7 (the 1.20.1 line)
   also has no global equip events, so `onGearChanged` registers this mod's gear as Trinkets to get
   per-item equip callbacks; other items are caught by the 10-tick fallback rebuild.
9. **State naming.** Section 6 says `STRAPPED_IDLE`; section 7's state list says `IDLE`. Code uses `IDLE`.
10. **Default keys** `X` and `C` overlap vanilla's creative hotbar save/load activators.
11. **Risk table phases** for R7 (lifts) and R8 (Create) point at phases 7 and 8; the roadmap
    has lifts in phase 8 and Create in phase 9.
12. **Skis on the player** are drawn as a pair under the feet and side by side on the back. Per-leg
    skis and the crossed back carry need per-ski geometry, which the procedural
    `DesignedGearRenderer` (section 22) provides.
13. **Lift boarding** was capped at 3.5 b/s while the fast speed level is 4.0 b/s, so nobody could
    board a fast lift by hand. The default `lift.maxBoardSpeed` is now 4.0, so every default level
    can be boarded; a server can still lower it. (Phase 8, decision 1.)
14. **Walking while strapped.** The controller rides on snow, ice and fast powder, and slides on
    other ground only above `scrapeSlideMinSpeed` (0.25); below it, or with scraping disabled,
    vanilla movement runs with the strapped modifier. Holding W on flat snow skates up to
    `skateMaxSpeed` (0.14) so a stopped rider can get moving. Both are new tuning keys.
15. **Client-authoritative movement.** The client simulates and the server validates results
    (rules 1 to 5) instead of simulating, as section 7 intends. The client never writes its own
    synced state; it waits for the server's echo, and local code reads the controller.
16. **The validator runs on client time.** One tick per move packet (the vanilla packet counter),
    allowed to run at most 40 ticks ahead of server time. Judged per server tick, a second of
    packets delivered at once after a lag spike, or a server below 20 TPS, looks like speeding.
    The rise allowance scales with the ticks covered.
17. **Rate limiting** uses wall-clock time (clients send at their own 20 ticks per second, so game
    time would make every client flood a lagging server), and a burst of drops counts as one
    violation per second, not one per packet. The client throttles `ride_state` to 10 per second
    for every change, state changes included: tapping A and D flips GLIDE and CARVE faster.
18. **Rule 3 for AIR** is read as "AIR while the server sees the rider standing": a violation after
    4 ticks on the ground in AIR. "No ground within 0.6 blocks for 2 ticks" would flag every pop
    (the first ticks of any jump are within 0.6 blocks of the ground).
19. **Ramp launches rise the tick after they are reported**, unlike pops, so the validator starts
    their allowance one tick later.
20. **Wall impact speed** is the part of the velocity the wall stopped, not the distance lost in the
    tick: a head-on hit at 0.8 is an 0.8 impact even when the rider started the tick 0.7 blocks
    short of the wall, and glancing along a wall is not a crash.
21. **Fall damage** is still vanilla's. Section 7 replaces it with landing-quality damage, which
    needs the phase 5 landing evaluation.
22. **`/alpineshred debug ride`** toggles the overlay for the player who runs it.
23. **Test server players.** Vanilla's `makeMockServerPlayerInLevel` gives its connection no
    channel, which Forge's `placeNewPlayer` cannot handle, so the tests log players in on a Netty
    `EmbeddedChannel` instead.

24. **The strapped walk modifier is dropped locally while sliding.** The -35% movement modifier
    narrows the field of view like slowness does, which fought the speed FOV. The client removes it
    while the controller moves the player and puts it back on release; the server keeps it, and
    walking is unchanged.
25. **Remote riders' events are inferred.** Other clients see pops, landings, wipeouts and pole
    pushes from synced state transitions, vertical motion and arm swings, so effects need no
    packets. The local rider uses the controller's own events.
26. **Landing clips.** `land_clean` plays on every landing until phase 5 grades landings
    (`land_perfect` and `land_sketchy` ship now, unused).

27. **Rail legs** stand the rail on the ground: they show where the block below has a solid top,
    and not on a post, fence or wall (which carries the rail) or over air. Section 10's "auto true
    unless the rail rests on a solid face" reads the other way round; legs into air looked wrong.
28. **Rail collision for riders is solid from above only.** A thin bar that also blocks from the
    side snags the server's replay of a rider grinding a rise (the rider is always a little below
    the next bar segment's top). Walkers still get the whole bar.
29. **Pipes ride with the slope gravity.** The arc uses `slopeAccelScale` like any slope, so a
    rider rises on a pipe as high as they could have ridden down a slope with the same speed:
    0.48 b/t climbs a radius 3 wall to the top. Air time off the top uses full gravity.
30. **One item, whole pipe.** Section 11 describes the pipe as a column of segments; the item
    places all of them at once (and breaking one removes the pipe), so a pipe is never half built.
31. **Park recipes** are new (the doc names no ingredients for park blocks). The wood box is
    slabs over planks so it does not clash with the chest, and the ramp has a snow base so it does
    not clash with stairs.
32. **Plastic box colors** are one block with a `color` property and a tinted model; the item
    carries its color in the vanilla `BlockStateTag`, the creative tab lists all 16 and broken
    boxes keep theirs.

33. **Spins count to the nearest half-turn** (170 degrees is a 180): the landing grade already
    charges for an under-rotated landing. Flips likewise. A grab needs 3 ticks to count.
34. **The combo clock runs on the ground only.** Section 8's "within 60 ticks" would end every
    combo during a long air; the clock pauses in the air and on rails, and restarts on any landing.
35. **Repeats score half** each time after the first in a combo (not a quarter, then an eighth).
36. **Variety and combo multipliers apply to the whole combo**, so the popup shows one running
    total and one multiplier; each landing's own points carry its landing factor (and 1.1 when
    switch) as they come in.
37. **A grind's entry grade** is PERFECT within 15 degrees of its variant's own line (along the rail
    for a 50-50, across it for a boardslide, 45 degrees off for nose and tail), otherwise CLEAN. An
    air session that ends on a rail scores its tricks at CLEAN; the grind's report grades the entry.
38. **The landing heading is judged against the way of travel at takeoff**, which the air does not
    change: the report carries the board's angle at takeoff, the heading error is that plus the
    spin, and rule 4 checks exactly that. Hitting a wall in the air no longer turns a clean landing
    into a bail, and a rider going straight up out of a quarter pipe is judged against the wall's
    fall line, not the drift along it.
39. **Two extra statistics**: `ride_distance` (for `first_run`, through a vanilla tick trigger with a
    player stats predicate) and `best_combo` (for `/alpineshred stats`).
40. **`trick_landed`'s `min_score`** is the landing's own points before the combo multipliers.
41. **Off a rail or box the board turns out of the slide by itself** (30 degrees a tick, to the
    nearer of forward and switch), so riding a boardslide off the end is no bail; spins count from
    there.
42. **Hops with nothing to score send no report.** The server sees every landing in the state
    claims, and any landing restarts the combo clock.
43. **The server times sessions on the rider clock** (the client's ticks, counted in move packets)
    from the state claims, in packet order, not by sampling the state each server tick: a report
    comes just before its landing's claim, and on a real connection several claims can arrive
    between two server ticks.
44. **Landing slope on a quarter pipe** is the angle between the velocity and the arc where the rider
    comes down (straight down onto the top of the wall is a perfect match).

45. **A transparent pixel shows the gear's default look at that pixel**, not one flat core color, so
    a design can paint over the stock graphics. Default looks are atlases in the design layout
    (`textures/gear/<item>.png`) with steel edges; the design's edge color replaces the strip.
46. **Each face is stored as seen from its own side with the nose to the right**, so the editor
    shows both as they will read; "copy to other face" is a straight copy (which on the gear is the
    mirror image across the length, as a flipped board is).
47. **Reset** goes through the save packet with a reset flag: free, and under the same cooldown.
48. **The built-in item renderer hook is one common mixin** into the vanilla built-in renderer,
    which both loaders reach for `builtin/entity` models (Forge's default item extension and
    Fabric's unregistered path), so `registerBuiltinItemRenderer` needs no per-loader code.
49. **Templates are lists of painting operations** in face fractions (fill, rect, line, pattern,
    gradient with optional posterize steps, seeded camo) with an `order` for the list, so one file
    fits boards and skis. The server sends its set to clients, since the editor runs on the client.
50. **The rate limit counts saves**, not attempts; attempts are bounded by the packet rate limit.
51. **Outlines**: board corners are quarter ellipses 10 by 7 pixels at both ends; a ski has a 12 by
    4 tip and 3 by 2 tail corners. The edge walls follow the outline pixel by pixel.
52. **The pair of skis is still drawn as one item between the feet**; skis that follow each leg
    remain for later.

53. **Park pieces are rigid, on `beard_thin`**, rather than `terrain_matching` bases with snow cover
    and gravity processors: each piece's snow pad drops one block over its first 8 blocks, so a
    line steps down the hill, and the beard fills under and clears over every piece. The pads are
    snow already, so no snow cover processor is needed.
54. **`JigsawStructure` is final in 1.20.1**, so `ToggleableJigsawStructure` holds one built from
    the same JSON fields and hands on its pieces after asking the switch.
55. **A run starts uphill of its start chunk** (walking up the steepest rise for up to 88 blocks), so
    it can use the 112-block reach on both sides of the chunk; a resort keeps 28 blocks of that
    reach for its lodge. Every piece stays within 7 chunks of the start.
56. **`ski_resort` uses no terrain adaptation**: its run shapes the ground itself, and a beard would
    bury it. The lodge carves its own box and fills its own foundation instead.
57. **Before planning, runs and resorts look for their biome** at the start chunk or 48 blocks away:
    every candidate chunk of a structure set is asked, snowy or not, and planning costs height
    samples. Water, for the planner, is a surface at or below sea level.
58. **`worldgen.frequencyMul` thins starts** (below 1, by the start's own seed); spacing stays the
    structure set's, since sets are data a pack may change.
59. **Runs and resorts are over section 19's generation budget**: on the CI machine (two cores) a
    run start takes about 230 ms and a resort about 190 ms, against 50 and 150 ms; parks and snow
    features take about 3 ms. The planner reads the generator's own heights from a lattice every 8
    blocks, about a hundred noise columns a run at a millisecond or two each. A run is planned once
    per candidate chunk (one per 22 by 22 chunk region) on a world generation thread, and away from
    snowy biomes a candidate costs five biome samples. The game test fails if the means pass 400 ms.

60. **A removed lift line is dropped on clients.** `s2c/lift_line_sync` carries a `removed` flag; a
    client that gets it drops the line and, with it, its chairs.
61. **"Beside a station."** A stop button controls the line whose drive or return station is nearest
    within 8 blocks. With no station in range it does nothing and says so in the action bar.
62. **Boarding and unloading stretches** are computed once, in a pure `LiftStations`, and stored on
    the line. Boarding is the first 4 blocks of loaded cable after the return wheel. Unloading is
    the last part of the loaded cable before its end where the seat is within 3 blocks of the
    drive station's own Y, above or below: the doc's "at most 3 blocks above" would cover the whole
    cable of a climbing line, since the drive is its highest point. The ejection point is 1 block
    before the end.
63. **Generated lifts place pending lines.** Structure pieces cannot touch saved data while a chunk
    generates, so a resort piece writes each station and head with block-entity NBT holding a
    `PendingLine` (absolute node positions and a line UUID). The first block entity of that line to
    run its first tick makes the line (`LiftManager.adoptPending`); the others find it by UUID.
    The load check runs on a block entity's first server tick, not on load, because a structure
    template applies its NBT after the block entity is created.
64. **Sneaking off a chair** goes through a mixin on `Player#wantsToStopRiding`: while the vehicle
    is a `ChairEntity` it refuses unless `DismountRule` allows (bar open, within 4 blocks of the
    ground, faulted line, `lift.dismountAnywhere`). Client and server make the same call; the
    server is what dismounts.
65. **Lift speeds are doubles** (`LiftLine.speed`, `targetSpeed`, and in the sync packet), so the
    server steps exactly as `LiftMotion.advance` does on a client and a catch-up matches to 1e-6.
66. **The drive's renderer draws span 0** (the cable between the drive and the first head, or the
    return station on a line with no heads); each tower head draws the span to its next node.
67. **A seat's height is split between players and mobs.** A chair's origin is the top of its seat
    plank. A mob's feet are 0.02 above it; a player's are 0.4 below it before vanilla's riding
    offset (so a seated player's hips, 0.75 above their feet, land on the plank).
68. **Chairs render out to 128 blocks**, the distance they are spawned at, rather than the 60
    blocks a box 1.2 by 0.5 would give.
69. **The cable's frame budget is 0.5 ms, final.** The design doc's figure was never at hand; phase 9
    keeps the phase 8 allowance (the `lift` client smoke measured 0.28 ms a frame) as the budget, and the
    smoke fails at three times it (1.5 ms), as for the other budgets (71).
70. **Seated riders face along the cable.** The chair sets a seated player's body yaw each tick and
    the rider tracker leaves it alone, so the third-person body does not swing against the chair;
    the head may turn 105 degrees either side, as in a boat.
71. **The lift budgets are asserted at three times the doc's** (section 22: 0.3 ms a tick for five
    lines, 5 microseconds a chair), since CI hardware varies; the game test `liftManagerBudget`
    runs five lines of 50 or more chairs for 200 ticks after 40 of warm-up and logs the means.
    Measured on the CI machine, with the budgets met many times over: the manager takes 0.003 ms a
    tick (Forge) and 0.005 ms (Fabric) for five lines, and a chair 2.9 and 3.4 microseconds a tick
    (the chairs are loose entities, so this is the chair's own work, without the entity manager's
    move bookkeeping).
72. **A sync stamped a few ticks after the client's game time is rewound, not clamped.** A client
    that lags the server's clock by a tick or three (software-rendered CI clients do) would
    otherwise sit ahead of the server's chairs until the next 100-tick resend: the multiplayer
    smoke measured the watchers' chairs a median 0.125 blocks (one tick at 2.5 b/s) ahead and up
    to 0.376 at worst, over the 0.25 tolerance. `ClientLine.apply` now rewinds `travel` by
    `speed / 20` per tick for gaps of up to 10 ticks; a larger gap is taken as it is. Between syncs
    `ClientLiftLines` follows the level's game time instead of counting ticks, so when the server's
    time packet moves a drifted client's clock the lines move with it (the first run with only the
    rewind still showed clusters of 0.25 to 1.25 blocks, up to ten ticks, right after such jumps).
    The client that logs out and back in mid-ride (`watcher1`) still had 5.8% of its samples
    over 0.25 (worst 0.626): its line state was dropped on logout and only came back at the next
    40-tick scan, with the chairs left to the entity packets until then. `LiftSync.join` now
    clears the player's synced set and sends every line in range on login. Final multiplayer
    run (CI run 42203872917, sha 3f8af2b), chair positions against the server's: rider 1879
    samples, max deviation 0.251 (6 over 0.25); watcher1 1565 samples, max 0.251 (18 over);
    watcher2 1864 samples, max 0.251 (16 over). The remaining outliers all sit at 0.250 to 0.251
    (two ticks at 2.5 b/s, just over the tolerance), a few sample times per client rather than a
    cluster around the relog; the check prints them by game time. The merge run on `wip/phase-8`
    (CI run 42209693937, sha 2eb24c8) passed too: rider 1811 samples, max 0.251 (13 over);
    watcher1 1507, max 0.126 (none over); watcher2 1790, max 0.251 (54 over, 3.0%, the closest to
    the 5% limit; spread over the ride, so watch it for flakiness).

73. **A sync that arrives with a time-packet jump is not shifted by the jump twice.** The multiplayer
    chair comparison was intermittently red on code that had passed (sha eea6ab8: run 42251546818
    green, run 42251558943 red with watcher1 at 88 of 1496 samples over 0.25, max 0.376, watcher2 at
    89 of 1813, max 0.626). The outliers came in windows of about 80 ticks that began at a 100-tick
    resend and ended at the next one, with every chair off by a whole number (2, 3 or 5) of ticks
    of travel. Cause: the server sends its time packet (every 20 ticks) and the line resend (every
    100) in the same tick, so both reach the client between two client ticks, and the clock has
    drifted by a few ticks of its own. `ClientLiftLines.accept` applied the sync at the already
    moved clock, but `lastGameTime` still held the old one, so the next `tick` shifted every line
    by the jump again. `LineClock` now brings all lines to the current game time before a sync is
    applied (a sync then starts a tick from the clock it was applied at). Unit test
    `LineClockTest`. The smoke clients also log each sync stamp against their clock and each tick
    in which the clock moved by other than one, which showed jumps of -2 to +3 every 20 ticks,
    syncs arriving with them ("clock moved -1 since the last tick"). Result (CI run 42260553304,
    sha fa0b582, chair positions against the server's): rider 1749 samples, max deviation 0.126,
    0 over 0.25; watcher1 1419, max 0.251, 8 over (one sample time, gt5640); watcher2 1725, max
    0.126, 0 over; the check passed. A second run (CI run 42268884498, sha 4a2f654) passed too: rider 1776
    samples, 0 over; watcher1 1422, 18 over (two sample times with 9 chairs each: gt6480 at 0.251,
    and gt5280 at 1.001, the one tick in which a stall made the client's clock jump by 9; the chair
    is positioned before the post-tick line shift, so it lags a jump by one tick); watcher2 1756,
    0 over. No windows of 0.375 or more.

74. **`config_sync` carries the lift keys.** `lift.maxBoardSpeed` and `lift.dismountAnywhere` join the
    synced settings and `SyncCodec.PROTOCOL_VERSION` goes from 1 to 2 (a client on another protocol
    already ignores the sync and keeps its defaults, which are the config defaults, 4.0 and off). The
    client's board prompt (`LiftBoardClient`) and the sneak-dismount prediction
    (`ChairEntity.dismountAllowed` on the client, through `ChairEntity.clientDismountAnywhere`) read the
    synced values, so they agree with a server that changed them; the server still decides. The
    auto-board decision reads no config key (`AutoBoard` is geometry only), so it needed no change.
    `ClientLiftLines` now steps its lines at the start of the client tick (`CLIENT_PRE`), before entity
    ticks, to the level's game time plus one (packets, the server's time packet included, are applied
    before the tick starts, and `ClientLevel.tick` advances the time before it ticks entities), so a
    chair no longer lags a clock jump by a tick. `LineClock` and its contract are unchanged.

75. **`resortHasALinkedLift` raced the level's random seed, not another test.** It failed once on Forge
    ("no pole under the head at -5979,97,-5013", run 42260553304) and passed on the rerun at the same
    coordinates. The resort is planned on terrain from the test's own fixed seed, so the site and the
    node heights are the same every run, but the structure is placed into the game-test server's level,
    and that server starts with a random seed. `LiftLinePiece.tower` builds its pole from the ground
    under the head (read from the level) up to the head; where the level's terrain at the head's column
    rose to the head's height or higher, there was nothing to build and no pole was placed. Generation
    in a real world is unaffected: the planner and the placement read the same terrain. The test now
    makes the level's terrain under each tower head what the plan assumed (stone up to three blocks under
    the head, air above) before placing; the check is unchanged.

76. **`config_sync` carries `lift.seats` too, with no protocol bump.** The client's "right-click to board"
    prompt asked whether a chair has a free seat against the client's own `lift.seats`, so a server that
    raised or lowered it showed a wrong prompt. `lift.seats` joins `config_sync` as an integer clamped to
    1 to 4 (the config range), default 2. A missing key keeps the default, which is how an old payload
    reads, and protocol 2 has not shipped, so the version stays. `LiftBoardClient` reads the synced value.
    The chair model's width in `ChairEntityRenderer` and the seat count in `ChairEntity` still read the
    local config; they only change what a client draws or predicts for a seat it cannot reach, and the
    server decides boarding either way.
77. **Create is wired in as an optional, compile-only dependency, with its own CI column.** Phase 9's
    decision 1, as built:
    - *Versions.* Forge compiles against Create `6.0.8-291` (the `-slim` jar from `maven.createmod.net`),
      Flywheel `1.0.6`, Ponder `1.0.92` and Registrate `MC1.20-1.3.11` (the last from `maven.tterrag.com`);
      the `create_forge_version=6.0.0` pin in `gradle.properties` was not a published build. Fabric compiles
      against Create Fabric `6.0.8.1+build.1744-mc1.20.1` from Modrinth's maven (the version pinned earlier;
      `mvn.devos.one` stops at 6.0.7), whose jar carries Flywheel, Ponder, Registrate and Porting Lib inside
      it. Loom does not unpack that jar-in-jar content for the dev runtime, so the Create job downloads the
      jar, checks it against a pinned sha256, unzips the nested jars into `fabric/build/create-nested`, and
      `fabric/build.gradle` adds them as runtime mods (and fails with a clear message if the folder is empty).
      All are `modCompileOnly` normally; none is bundled, and the jars' `mods.toml` / `fabric.mod.json` still list
      Create as optional.
    - *The Create column.* `-PwithCreate` turns the same coordinates into `modImplementation` and sets
      `-Dalpineshred.create=true` on the game-test runs; on Fabric it also raises Loader to 0.17.2, which
      Create Fabric 6.0.8.1 requires. CI runs it as a second job (`create`) beside the existing one, so the
      main job's 120-minute limit is untouched; Create's own Fabric game tests run in that column too (Fabric
      runs every mod's), so `.github/scripts/gametest-junit-check.py` judges the step from `junit.xml` on our
      tests alone and only reports Create's failures (Forge filters them out by namespace). The job writes its
      logs to the `ci-logs-create` branch (with its own `RUN.txt`), so they never overwrite `ci-logs`.
    - *The bridge.* Common code sees Create only through `compat.create.CreateBridge` (`live()`,
      `version()`), held in `CreateCompat` and empty (`ABSENT`) until a loader installs one. Each loader's
      `compat.create` package has the bridge and an `init()`, called from the entry point only when
      `PlatformHooks.isCreatePresent()`. Nothing else imports Create.
    - *The load test.* `createBridgeMatchesColumn` is registered in the ordinary holders and runs in both
      columns, asserting what the column declares: with Create, it is present, the bridge is installed and
      live; without it, there is no bridge. That is stronger than skipping it outside the Create column, and
      a Forge namespace filter would have needed a second copy of the empty test template. Later Create
      tests live in `CreateGameTests` and begin with `inCreateColumn()`.
78. **The kinetic lift drive is a Create gearbox that runs the station beside it.** Phase 9 decision 2, as built:
    - *The block.* `kinetic_lift_drive` is a log-style block (axis `x`, `y` or `z`) with a cable wheel on its four
      side faces and a shaft hole in both ends, so it joins a Create network the way a shaft does. It and its
      block entity `KineticLiftDriveBE` (a Create `KineticBlockEntity` that implements `LiftDriveSource`) live in
      each loader's `compat.create` package and are registered, in their own Architectury registers, only when
      Create is present. It drops itself in code (`getDrops`), not from a loot table: datagen runs without Create,
      and a loot file naming an item that does not exist would be a load error in the normal column.
    - *Which drive wins.* `LiftDriveStationBlockEntity.enabled()` and `targetSpeed()` first ask
      `CreateCompat.bridge().kineticDriveNear(...)`, which looks at the six neighbours in the fixed order down, up,
      north, south, west, east and returns the first kinetic drive or null. With Create absent the bridge is the
      empty one and answers null, so the station behaves as before and costs nothing. While one is attached it
      is the source and redstone is ignored; remove it and the station's neighbour update hands control back to
      redstone. Right-clicking such a station clears an emergency the rotation allows to run, and otherwise only
      reports the speed (there is no level to cycle).
    - *Numbers.* `KineticDriveMath` (pure, common, unit-tested): speed is `|rpm| / 64` blocks per second clamped to
      `lift.kineticMaxSpeed` (4.0, range 0.1 to 10.0, already in the config); under 8 rpm in either direction is
      stopped; stress is a flat `lift.kineticStress` (4.0, range 0.1 to 64.0, new) per rpm, so 256 rpm takes
      1024 su whatever the chair count. `calculateStressApplied()` returns that per-rpm impact and Create
      multiplies by the speed; the item tooltip quotes the figure at 256 rpm.
    - *Motion.* No new motion code. The drive tells its neighbouring stations to `refresh()` when its command
      (`enabled`, target speed) changes, and the station writes it into the line exactly as for redstone, so
      `LiftMotion` ramps a speed change and an emergency stop (losing rotation, falling under 8 rpm, or
      overstress, which Create reports as speed 0) decelerates at 2.0 blocks per second squared. Rotation coming
      back clears the emergency, as redstone returning does. The block entity compares its command every tick
      (two float reads and a compare, `KineticDriveWatch`, no allocation) as well as in `onSpeedChanged`, because
      Create zeroes an unsourced block's speed without calling it. A fresh drive starts as "stopped" and a drive
      read from disk takes its saved rotation as the baseline, so a reload is never a false emergency stop.
79. **The kinetic drive's data is written by id, and its tests use Create's real network.**
    - *Data without Create.* The datagen run has no Create, so `AlpineCreateProvider` writes the recipe and its
      advancement as JSON by id. The recipe (a shapeless gearbox, large cogwheel and iron block; WP5 may balance
      it) carries both loaders' load conditions (`conditions` with `forge:mod_loaded`, and
      `fabric:load_conditions`), so a world without Create skips it before parsing; its advancement unlocks on the
      drive station, an item that always exists. The block's `mineable/pickaxe` and `create:non_breakable` entries
      are `required: false`. The lang keys are plain strings, and the blockstate, models and textures come from
      `art/placeholder/generate_kinetic_drive.py` as for the other lift blocks. The normal column's logs show no
      error or warning from any of it. The two stress tooltip lines read the client's local `lift.kineticStress`
      (it is not in `config_sync`; only the server's value matters for the drive).
    - *Tests.* `KineticDriveMathTest` and `KineticDriveWatchTest` (unit). `KineticDriveGameTests`, in the Create
      column only (each returns at once elsewhere): rotation runs the line and its speed follows the rpm on the
      `LiftMotion` ramp up to the cap; losing rotation (the motor removed) is an emergency stop at 2.0 b/s^2 and
      rotation back restarts it; overstress stops it and relief restarts it; redstone is ignored while a drive is
      attached and returns when it is removed; the drive's saved data reads back as the same command; and the
      tick budget, under 3x 0.02 ms a drive (decision 6). The rig is a station, a kinetic drive against its east
      face and Create's creative motor beyond it, built through a few `test...` fixture methods on `CreateBridge`
      (the only way common test code can name Create's blocks).
    - *Build and CI.* The Fabric kinetic block entity extends Create's, so javac needs Create's whole supertype chain,
      which reaches into Porting Lib. `.github/scripts/unpack-create-nested.sh` (the Create job's step from
      README 77, now a script) therefore runs in both jobs, and `fabric/build.gradle` puts the unpacked jars on the
      compile classpath always and on the runtime classpath under `-PwithCreate`. The six kinetic game tests run in
      their own game-test batch (`kineticDrive`): batches run one after another, and `resortHasALinkedLift` counts
      the level's lines before and after, so lines made and removed by tests running at the same time broke it.
80. **Create's wrench works on our blocks by four rules.** Matched by registry id `create:wrench` (the `create:wrench` item tag does not hold the wrench in Create 6.0.8), handled by
    `compat.create.WrenchActions` (the loaders' `RightClickBlock` and `UseBlockCallback` only translate the event,
    behind `isCreatePresent()`); no interface on our blocks. The decision table is `WrenchRule`:
    - *Park pieces* (what the park wrench changes): a plain click runs `ParkWrenchItem.use`, the park wrench's
      own code, now a shared static method. A sneak-click picks the piece up, as Create does for its own blocks.
      The plan said both "turn when sneaking" and "sneak picks up"; sneak picks up, so turning stays a
      park-wrench job (the park wrench is unchanged and stays the park tool).
    - *Lift blocks* in the data-driven tag `alpineshred:wrench/lift_pickup` (pole, head, drive and return
      station; the loading gate and stop button, which hold no line data, and the kinetic drive joins by tag): a plain click does nothing but show the action-bar hint
      `message.alpineshred.wrench.lift_hint` (use the lift linker). A sneak-click picks the block up through
      `Level.destroyBlock`, which runs the same `onRemove` as breaking it by hand, so the head and stations
      fault their line with the same reason. A pole has no data and no fault path, by hand or by wrench
      (poles keep their old behaviour); the gate and stop button have no `onRemove` or block entity either, so picking them up faults nothing, as breaking them by hand.
    - *Pickup* hands the block item to the player (nothing in creative) and drops no loot.
81. **WP4's tests.** Unit: `WrenchRuleTest`. Create-column game tests (return early outside it, via
    `inCreateColumn()`; the common handler is called with a mock player holding `create:wrench`):
    `wrenchIsMatchedById`, `wrenchOnParkPieces`, `wrenchOnLiftBlocks`, and `wrenchPickupFaultsLikeBreaking`
    (builds the same line twice, removes a node by hand and by wrench, and compares the fault reasons).
82. **The client obeys the server's flip limit.** CI run 42355456284 failed because `TrickAngles.flips` rounds to
    the nearest, so 228 degrees in 11 ticks counts as one whole flip, and the validator's rule 2 says a whole flip
    needs 16 air ticks. The validator was right (a flip past 180 degrees is a flip, as scored); the client was
    not bound by it. `FlipLimit` (common, pure) holds both flip bounds, the rate and the 16 ticks per whole flip.
    `TrickValidator` rejects with it, and `AirRotation.step` takes the air tick count and holds the pitch at the
    limit, so an honest rider can never send a flip report the server rejects. The validator's limits are
    unchanged. A short air holds a flip at just under half a turn until tick 16. Unit: `FlipLimitTest`.
83. **The balance pass changed one recipe, and settled the rest as they were.** Phase 9 decision 5: data only, no
    code or stat changes (gear stats and durabilities stay as phase 6 tuned them, since the movement tests rest on them).
    Every changed number:

    | Recipe | Before | After |
    |---|---|---|
    | Pro Snowboard (smithing table) | netherite upgrade template, a park or freeride board, netherite ingot | diamond (template slot), a park or freeride board, amethyst shard |
    | Pro Skis (smithing table) | netherite upgrade template, carving or park skis, netherite ingot | diamond (template slot), carving or park skis, amethyst shard |

    Why: "nothing needs end-game items", and netherite is the one end-game material the mod asked for. The pro tier
    stays a smithing recipe because the smithing transform is what carries a painted design onto the pro board
    (`smithingCarriesTheDesign`); that test's table now holds a diamond and an amethyst shard. A diamond and an
    amethyst shard (the same shard the carbon poles use) put the top tier at mid-game cost. Checked and kept:
    - *Starter gear.* A snowboard is 4 spruce planks, 1 leather and 2 iron nuggets; snowboard boots are 2 leather,
      a string and 2 wool. That is one log, three leather, two wool, a string and a fifth of an iron ingot, so a new
      player is riding after a cow, a sheep and a smelt. Skis, ski boots, mitts and the park pieces are as cheap.
    - *Mid-game gear.* Park and freeride boards, the three ski upgrades, the carbon poles and the park wrench stay at
      iron, copper, slime, phantom membrane, packed ice, amethyst and redstone.
    - *Lifts.* Pole (4 per iron ingot and 2 bars), head (lightning rod, 3 ingots), drive station (iron block, piston,
      4 redstone), return station (iron block, piston), gate and stop button are unchanged. A small lift is two iron
      blocks and two pistons plus poles: an iron-tier build, with no diamond and no Nether item in it.
    - *The kinetic drive* stays a gearbox, a large cogwheel and an iron block: the same iron block as the drive
      station it sits beside, and only Create's andesite-tier parts otherwise. Its recipe keeps its Create load
      conditions on both loaders.
84. **Common tags are Forge's, written by datagen; Fabric has none to join.** Datagen runs on Fabric only, but a
    `forge:` tag is just a data file, so `AlpineItemTagProvider` writes `forge:armors`, `forge:armors/boots`,
    `forge:armors/helmets` (boots and helmet) and `forge:tools` (poles, edge tuner, park wrench, lift linker). The API
    probe of fabric-convention-tags-v1 1.5.6 (the 1.20.1 Fabric API in use) showed no armor, tool or wrench tags
    there, so no `c:` copies are invented. The park wrench is in no wrench tag: other mods treat that tag as
    "rotates my blocks", and it is the park tool only (README 80). Groomed snow joins the vanilla `minecraft:snow`
    block tag, which is what makes grass under it show its snowy side. Neither loader has a convention for dyeable
    gear or snow blocks, so nothing is invented for them.
85. **WP5's checks.** `GeneratedDataTest` (unit, reads the committed JSON and the generated language file for the
    ids): every item has a recipe or a listed reason (the three weathered copper rails, which only weather), and every
    block has a loot table and a `minecraft:mineable` tag or a listed reason (the quarter pipe and the kinetic drive
    drop in code). A stale reason fails too, and a failure names the offending ids. The lift tags were reviewed
    against the vanilla mobs and left as they were: `lift_blocked` keeps the ender dragon, wither and warden, and the
    size limits (1.2 wide, 2.4 tall) already keep out every other large mob; `lift_passengers` stays empty.

86. **Resort and run generation fetch their noise together.** The planner's cost is the generator's noise
    (about 2.5 ms a lattice column on the CI machine, 80 to 90 columns a start), and the heights it reads
    cannot change without changing the plan. So the lattice and its interpolation stay, and
    `RunPlanner.Terrain.prefetch` (and `ResortLiftPlanner.Ground.prefetch`) tell the terrain which columns a
    step is about to read: the eight candidate headings, the uphill walk's ring, the profile's points and the
    lift's spans. `GeneratorTerrain` computes the missing lattice columns side by side on the common pool and
    stores them; every height is the value `getBaseHeight` gives. A plan is the same for a seed: `RunPlannerTest`
    holds fingerprints of the plans the unchanged planner made for eight seeds and checks them with and without
    prefetching. Resorts measured about 210 ms a start before and 133 ms after on the CI machine (150 ms budget).
87. **Design textures are warmed at resource load.** A texture build is the default atlas lookup, the atlas build
    and the upload. The first design on each board or ski read its default file and decoded the PNG inside the
    frame, and the atlas build ran interpreted. The defaults are now read when resources load (`preload`), the
    atlas build and an upload are run once per kind then, and the build writes into a reused buffer. The 5 ms
    budget is unchanged and still asserted on the mean build time; the smoke's read-out splits it into defaults,
    atlas and upload.
88. **The lift's tick and frame paths do not allocate.** `LiftMotion.nextTravel` replaces the `{speed, travel}`
    array per line per tick (server and client); `LiftPath.chairPlace` replaces the three point and heading
    objects per chair per tick; `LiftLine.nodeAt` replaces the node list built per line per tick and per player
    in the chair pass; the cable's pieces are computed in doubles with the sag worked out once a span; the rider
    tracker reuses its list. The arithmetic is the same bit for bit (`LiftPathTest`, `LiftMotionTest`). Left as
    is: the per-chair `Key` records in `LiftChairs.update`, which runs once a second.
89. **The release pipeline is `release.yml`, and a tag is the only way to publish.** Phase 9 decision 7, as built
    (WP8; the long `build.yml` is untouched).
    - *Triggers.* A `v*` tag push runs `build` (both jars at the tag's version, the unit tests, the metadata check),
      then `github-release` and `publish` in parallel. A manual run (`workflow_dispatch`, `dry_run` true by default,
      plus a `version` input, 1.0.0 by default) runs `build` only and uploads the jars and the changelog section as
      the `release-jars` artifact. A manual run with `dry_run` false fails at once: publishing from a branch with a
      made-up version is what the tag rule is there to prevent.
    - *Tokens.* The workflow is read-only by default. Only `github-release` gets `contents: write`, and it uses
      `GITHUB_TOKEN` with the `gh` CLI (`gh release create`, jars plus the version's changelog section; a version
      with a `-` is a prerelease). `publish` reads `MODRINTH_TOKEN` and `CURSEFORGE_TOKEN` from secrets and
      `MODRINTH_PROJECT_ID` and `CURSEFORGE_PROJECT_ID` from repository variables. Each of the four publish steps is
      skipped when its token or id is empty, so a tag with no secrets set still makes the GitHub release. Secrets
      are passed to the action as inputs and never echoed.
    - *Why `Kir-Antipov/mc-publish@v3.3.0`.* It is the widely used action for exactly this, one step handles both
      Modrinth and CurseForge (loaders, game versions, dependencies and release type), and the version tag is more
      than a few weeks old. Plain curl against the two APIs would be about 60 lines of JSON and multipart handling
      to own. It is given no `github-token`, so it cannot touch releases. The dependency slugs (`fabric-api`,
      `architectury-api`, `trinkets`, `playeranimator`, `curios`, `create`, `create-fabric`) are written from
      memory and are checked by hand at the first real publish, which is after development.
    - *The build.* Gradle runs `build -Pmod_version=<tag without v>` after `.github/scripts/unpack-create-nested.sh`
      (the Fabric compile needs it on every build, README 79). A tag must look like `1.2.3` or `1.2.3-rc.1`.
    - *Changelog.* `CHANGELOG.md` is built from the "What phase N delivers" sections, newest section on top
      (`## [1.0.0] - unreleased`). `.github/scripts/changelog-section.sh` prints the section whose heading starts
      with `## [<version>]` and fails when it is missing or empty, so a tag with no changelog section cannot build.
      The date in the heading is edited when the tag is cut.
90. **The jars' metadata is checked after the build, from inside each jar.** `.github/scripts/verify-jar.py` reads
    `fabric.mod.json` or `META-INF/mods.toml` out of the built jar and fails on a mismatch:
    - *Version.* `gradle.properties` holds `mod_version`; `build.gradle` makes the project version
      `<mod_version>+mc<minecraft_version>`, and both resource tasks expand it into the metadata, so
      `-Pmod_version=1.0.0` gives `1.0.0+mc1.20.1` in the file name and in both metadata files. No change was needed
      there. The check expects exactly that string.
    - *Ranges.* Minecraft `~1.20.1` (Fabric) and `[1.20.1,1.20.2)` (Forge); Fabric loader `>=0.15.0`; Forge `[47,)`.
    - *Create is optional on both loaders.* Fabric `suggests` `create` at `>=6.0.0`, the line our compat code is
      built against (Create Fabric 6.0.8.1 in CI; a wrong range on a suggestion only warns). Forge has a
      `mandatory = false` dependency at `[6.0.0,)` with `side = "BOTH"`. The check also fails if Create is ever a
      hard dependency on Fabric.
    - *NeoForge.* The Forge jar runs on NeoForge 1.20.1, whose mod loader reads the same `mods.toml`, so the jar is
      unchanged and the publish step lists it under both `forge` and `neoforge`. The Fabric jar lists `fabric` only.


Not in phases 5 to 8, by design: line fill and waxing vanilla blocks (v1.1), PNG import of designs
(v1.1). The kinetic lift drive arrived in phase 9 (README 78).
Real animation clips, sounds and textures replace the placeholders file by file under the same
names.

## License

All Rights Reserved. See `LICENSE`.
