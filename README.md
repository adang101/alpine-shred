# Alpine Shred

Snowboards, skis, park features, groomed runs and ski lifts for Minecraft 1.20.1, on Forge,
NeoForge (via the Forge jar) and Fabric from one codebase. The design document is the source of
truth; this README covers how to build the code and where it stands against that document.

**Status:** Phase 3 (feel) is built and green in CI: rider animation, camera roll and speed field
of view, sliding and impact sounds, snow particles, and the basic HUD. 148 unit tests and 26 game
tests per loader pass, and a scripted Forge client rides a test run on a snowboard and on skis
under a virtual display, with screenshots. What remains of phase 3 is its exit gate, five outside
testers riding for 10 minutes each ([docs/playtest-phase3.md](docs/playtest-phase3.md)). Phases 1
and 2 are complete. Still to check by hand from earlier phases: three clients on one dedicated
server (multiplayer script step 1), riding real vanilla mountains with the debug overlay on (risk
R4), and the jar on a NeoForge 1.20.1 server.

## Build and test

Requires a JDK 21 to run Gradle (Architectury Loom 1.11); the mod itself compiles and runs on a
Java 17 toolchain, which Gradle downloads if none is installed.

| Task | Command |
| --- | --- |
| Build all jars, run unit tests | `./gradlew build` |
| Regenerate lang, recipes, tags, loot | `.github/scripts/run-datagen.sh datagen.log` (writes `common/src/main/generated`; commit the result) |
| Game tests, Fabric dedicated server | `./gradlew :fabric:runGametest` |
| Game tests, Forge game-test server | `./gradlew :forge:runGameTestServer` |
| Client smoke test (Forge, needs a display or `xvfb-run`) | `./gradlew :forge:runSmokeClient` |
| Play-test | `./gradlew :forge:runClient` (see the note on the Fabric dev client below) |
| Regenerate placeholder art | `python3 art/placeholder/generate_textures.py && python3 art/placeholder/generate_models.py` |
| Regenerate placeholder clips, sounds, particles, overlays | `python3 art/placeholder/generate_animations.py`, `generate_sounds.py` (needs ffmpeg), `generate_effects.py` |

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
| Create | 6.0.0 / Create Fabric 6.0.8.1 | optional, wired in phase 9 |

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
  trick/      GrabType, AirRotation (pure)
  config/     ConfigSpec + AlpineConfig: both JSON files, every key from the doc
  data/       reload listeners for tuning, surface profiles and gear stats
  network/    ModNetwork (packets), SyncCodec (pure payloads), RideAction, PacketRateLimiter
  client/     RideDriver (runs the local player's controller), ClientNetwork, RideDebugOverlay,
              key binds, gear render layer, ClientSmoke (the scripted client test), CameraOrbit
    feel/     RiderTracker (per tick, every rider in 48 blocks), RiderView (what a rider is doing,
              from the controller or the synced state), RiderAnimator (the only PlayerAnimator
              code), LeanLayer, RiderSoundManager, RiderParticles, RideEffects (camera, HUD),
              FeelStats
    particle/ snow spray, poof and trail particles
  gametest/   game tests shared by both loaders; Course builds riding courses
  mixin/      PlayerMixin (synced state, travel, edge back-off, jump, footsteps), EntityMixin
              (step height, descending), PowderSnowBlockMixin, LivingEntityMixin (no sprint),
              ServerGamePacketListenerAccessor; client/LocalPlayerMixin (no auto-jump),
              GameRendererMixin (camera roll, FOV, screen effects under the GUI),
              ItemInHandRendererMixin (no first-person arms while sliding), ItemInHandLayerMixin
              (poles drawn by the gear layer), CameraMixin (CameraOrbit)
forge/    entry point, CuriosGearAccess, Curios slot data, platform hooks, game-test holder
fabric/   entry points, TrinketsGearAccess, Trinkets slot data, datagen, game-test holder
art/      placeholder generators: textures, models, rider clips, sounds, particles, overlays
```

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
13. **Lift boarding** is capped at 3.5 b/s but the fast speed level is 4.0 b/s, so nobody can board
    a fast lift by hand. To settle in phase 8.
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

Not in phase 3, by design: tricks (grabs, spins, flips; phase 5). Real animation clips, sounds and
textures replace the placeholders file by file under the same names.

## License

All Rights Reserved. See `LICENSE`.
