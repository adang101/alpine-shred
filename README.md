# Alpine Shred

Snowboards, skis, park features, groomed runs and ski lifts for Minecraft 1.20.1, on Forge,
NeoForge (via the Forge jar) and Fabric from one codebase. The design document is the source of
truth; this README covers how to build the code and where it stands against that document.

**Status:** Phase 1 (foundations and gear) is complete and green in CI on Forge and Fabric: all
modules compile, 83 unit tests pass, and 11 game tests pass on each loader's headless dedicated
server. The phase 2 movement model exists as pure, unit-tested code; wiring it into the game (the
slide controller) is the next milestone. Still to check by hand for the phase 1 exit gate: gear
rendering in third person, and the jar on a NeoForge 1.20.1 server.

## Build and test

Requires a JDK 21 to run Gradle (Architectury Loom 1.11); the mod itself compiles and runs on a
Java 17 toolchain, which Gradle downloads if none is installed.

| Task | Command |
| --- | --- |
| Build all jars, run unit tests | `./gradlew build` |
| Regenerate lang, recipes, tags, loot | `.github/scripts/run-datagen.sh datagen.log` (writes `common/src/main/generated`; commit the result) |
| Game tests, Fabric dedicated server | `./gradlew :fabric:runGametest` |
| Game tests, Forge game-test server | `./gradlew :forge:runGameTestServer` |
| Play-test | `./gradlew :fabric:runClient` or `./gradlew :forge:runClient` |
| Regenerate placeholder art | `python3 art/placeholder/generate_textures.py && python3 art/placeholder/generate_models.py` |

CI (`.github/workflows/build.yml`) runs all of the above except the clients, fails if committed
generated data is stale, uploads the jars, and force-pushes logs, test reports and generated data to
the `ci-logs` branch so they can be read with plain git.

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
  movement/   RideState, RideSnapshot (packed synced int), SlideModel, SlopeMath,
              GroundContact, Ballistics, MovementTuning, SurfaceProfile  <- pure, unit-tested
              Riders, StrapCheck, StrapController, TuningData             <- game side
  gear/       items, armor material, GearStats (pure), GearWear (pure), GearAccess,
              GearProfile(s), wax and lens recipes
  trick/      GrabType, AirRotation (pure)
  config/     ConfigSpec + AlpineConfig: both JSON files, every key from the doc
  data/       reload listeners for tuning, surface profiles and gear stats
  network/    c2s/strap_toggle, per-player rate limiter
  gametest/   game tests shared by both loaders
  mixin/      PlayerMixin (synced ride state), LivingEntityMixin (no sprint when strapped)
forge/    entry point, CuriosGearAccess, Curios slot data, platform hooks, game-test holder
fabric/   entry points, TrinketsGearAccess, Trinkets slot data, datagen, game-test holder
art/      placeholder texture and model generators
```

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
- 83 unit tests (all section 19 feel targets among them) and 11 game tests per loader.

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
2. **Gear stats must sync to clients.** The client-side slide controller reads them, so the
   phase 2 `tuning_sync` packet needs to carry gear stats along with tuning and surfaces.
3. **Ground snap distance** is `snapDown + speed`. A fixed 1.0 cannot follow a 45 degree voxel
   slope at the speed cap (up to 2 blocks of drop per tick) without flickering into AIR.
4. **Ramp launch grade** uses the ramp's declared slope (park ramps) or the steepest per-tick
   climb, capped at 45 degrees. Averaging over two ticks understates a one-block kicker and misses
   the section 19 apex targets.
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

## License

All Rights Reserved. See `LICENSE`.
