# Phase 8 plan: ski lifts

This is the work plan for finishing phase 8. The design doc is the source of truth: section 13 covers lifts, and
the sections listed under each package cover the rest. When this plan and the doc disagree, the doc wins, unless
the plan records a clarification (see "Decisions" below). Every clarification also goes into the README under
"Design clarifications made while building".

**Exit gate** (section 20):
- Three players ride a 400-block lift, and the ride survives a save and reload.
- Faulted-line handling works.
- The performance budgets are met.

**Process.** Each work package (WP) below is sized for one implementing agent (Sonnet). Packages run on their
own branch cut from `wip/phase-8`. The orchestrator (Opus) reviews each one and merges it into `wip/phase-8`.
`wip/phase-8` is merged into `main` only once every automated part of the exit gate is green in CI. The parts
that need people go into `docs/playtest-phase8.md`.

---

## 0. Where it stands (2026-11-01)

| Item | State |
|---|---|
| WP1: `LiftLine`, `LiftStations`, `PendingLine`, `LiftSavedData`, `LiftManager`, with unit and game tests | Done (`9d99b9f`) |
| WP2: six lift blocks, `LiftNodeBlockEntity` (head, drive, return), first-tick load check, datagen, 4 game tests (60 per loader) | Done (`59c9a4d`) |
| WP3: `LiftLinkerItem`, pure `LinkValidator` sharing `LiftPath` geometry (`anchor`, `spanPoint`), 14 unit tests, 2 game tests | Done (merged after WP4) |
| WP4: `ChairEntity`, `LineView`, `PassengerRule`, `LiftChairs` spawning, re-attach pieces, `MobAccessor` mixin, 8 game tests | Done (`293f73f`) |
| `LiftPath` (cable loop: sag, bullwheel half circles, 0.25 sampling, `pos`, `tangent`, `chairPos`, `chairCount`) | Done on `wip/phase-8`, with unit tests in `LiftPathTest` (7 tests) |
| `LiftMotion` (speed ramp 0.5 b/s², emergency 2.0, `travel += speed/20`, client catch-up `advance`) | Done on `wip/phase-8`. **No unit tests yet** (added in WP1) |
| Config keys `lift.*` (`AlpineConfig` lines 39–57, including `syncRadius`, `driveNeedsRedstone`, `kineticMaxSpeed`) | Done on main |
| Items `lift_linker` and `lift_pass` | Registered as `DescribedItem` placeholders with no behaviour yet |
| Stat `lift_rides` | Registered, never incremented |
| `RideState.LIFT` | Exists, but `RideTransitions` refuses every move into it |
| Lift blocks, block entities, chair entity, packets, renderers, sounds, clips, `lift_ridden` trigger, `bluebird_day`, commands, resort lift line | Missing |

`wip/phase-8` is based on `8b171fa`, and `main` (`3c84883`) has the same tree. Start by merging `main` into
`wip/phase-8`. The merge is a formality, since the trees match.

## 1. Conventions every package follows

- **Code layout.**
  - Common code goes in `io.github.adang101.alpineshred.lift` (server and shared code) and `...client.lift`
    (client code).
  - Loader modules get only registration hooks, following the patterns already in the repo
    (`ClientPlatformHooks`, the `forge` and `fabric` gametest classes).
- **Pure logic stays pure.** If something can be computed without Minecraft (geometry, validation, intervals,
  passenger rules over plain numbers), put it in a pure class and unit-test it in `common/src/test`.
- **Determinism.** Lift math uses doubles in a fixed order (see the `LiftPath` javadoc). Never use floats or
  `Math.fma`, and never iterate a `HashMap` in path or interval code.
- **Generated data.** Lang, recipes, loot tables and block tags come from the Fabric datagen providers
  (`fabric/.../datagen`). After changing a provider, regenerate and commit `common/src/main/generated`.
- **Placeholder art.** Generate it with a Python script in `art/placeholder/` (see `generate_bench.py` and
  `generate_structures.py`). Commit both the script and its output.
- **Commits.** Use the message style `Phase 8: <what>`, one commit per coherent step. Never push to `main`.
- **Before handing back,** each of these must pass locally with JDK 21: `./gradlew build` (which includes the
  unit tests), `./gradlew :fabric:runGametest` and `./gradlew :forge:runGameTestServer`.
- **The handback** lists:
  - what was built,
  - the tests added,
  - the test results,
  - any deviation from the doc, with its reason.

## 2. Decisions (settled now; record each in the README clarifications)

1. **Boarding speed versus the fast level** (README clarification 13). The default `lift.maxBoardSpeed` rises
   from 3.5 to 4.0, so every default speed level can be boarded. A server can still lower it.
2. **Line removal on clients.** `s2c/lift_line_sync` gains a `removed` flag. A removed line is dropped from the
   client cache, and its chairs are dropped with it.
3. **"Beside a station."** A stop button controls the line whose drive or return station is nearest within 8
   blocks. A button with no station in range does nothing and says so in the action bar.
4. **Boarding and unload intervals.**
   - The boarding stretch is `s ∈ [0, 4]`: the first 4 blocks of loaded cable after the return wheel.
   - The unload stretch is the last part of the loaded cable before `loadedEnd` where the seat is within 3 blocks
     of the drive station's platform, above or below. That platform is the station block's Y. (The doc says "at
     most 3 blocks above", but on a climbing line that covers the whole loaded cable, so the band is two-sided.
     This was settled in WP1.)
   - The ejection point is 1 block before `loadedEnd`.
   - Both stretches are computed in a pure `LiftStations` class and stored on the line.
5. **Generated lifts.** Structure pieces cannot touch `SavedData` during generation. Instead the resort piece
   places the station and head blocks with block-entity NBT that holds a `PendingLine` (the node list plus a
   generated line UUID). The first block entity of that line to load calls `LiftManager.adoptPending`, which
   creates the line once. The others find it by UUID.
6. **Sneak dismount.** A mixin on `Player#wantsToStopRiding`, or the equivalent in `rideTick`, refuses the
   dismount while riding a `ChairEntity` unless the dismount rules in section 13 allow it. Add the mixin to
   `alpineshred.mixins.json` and to the doc's mixin inventory list in the README.

7. **Speeds are doubles** (settled in WP1). `LiftLine.speed` and `targetSpeed` are `double`, so the server's
   stepping matches `LiftMotion.advance` exactly. Packets and caches carry them as doubles.

## 3. Work packages

Dependencies: WP1 → WP2 → WP3. WP4 needs WP1. WP5 needs WP4. WP6 needs WP4 and WP5. WP7 needs WP2. WP8 needs WP4
and WP5. WP9 needs WP2 and WP3. WP10 runs throughout and closes the phase. WP7 and WP8 can run in parallel after
WP5.

### WP1: Line data, saved data, manager (sections 13 and 15)

**Build:**
- **`LiftLine`**: `UUID id`, `BlockPos drive`, `BlockPos returnStation`, `List<BlockPos> heads`, `double travel`,
  `float speed`, `float targetSpeed`, `boolean powered`, `boolean faulted`, `boolean emergency`.
  - Cached values, not saved: the `LiftPath`, the chair count, the spacing, and the boarding and unload intervals
    (decision 4).
  - Write NBT with `CompoundTag` and keep the head order exactly as it is.
- **`LiftStations`** (pure): computes the boarding and unload intervals and the ejection `s` from a `LiftPath`
  and the station Ys.
- **`LiftSavedData`**: one per level, `computeIfAbsent` through `DimensionDataStorage`, keyed
  `alpineshred_lifts`.
  - It holds lines by id, preserving insertion order.
  - It enforces `lift.maxLinesPerLevel`.
- **`LiftManager`**:
  - Registered on the server level post-tick (Architectury `TickEvent.SERVER_LEVEL_POST`).
  - Each tick, it steps every line with `LiftMotion.step`. The target is `targetSpeed` when the line is powered,
    not faulted and not in emergency, and 0 otherwise. Emergency uses the 2.0 deceleration.
  - It marks the saved data dirty only when the state changes meaningfully (a speed or flag change), plus once
    every 200 ticks for `travel`.
  - It respects `lift.runUnloaded`. When that is false, it skips lines with no loaded node.
- **API for later packages:** `create(nodes)`, `remove(id)`, `fault(id, reason)`, `get(id)`, `lines()`,
  `setDrive(id, powered, target)`, `emergencyStop(id)`, `adoptPending(PendingLine)`.

**Tests:**
- Unit:
  - `LiftMotionTest`:
    - ramp up and ramp down at 0.5 b/s²,
    - emergency stop at 2.0,
    - `advance(n)` equals `n × step` to 1e-9,
    - the wrap at `loopLength`.
  - `LiftStationsTest`:
    - the stretches lie inside the loaded cable,
    - the ejection point sits inside the unload stretch,
    - a straight 2-node line and a 10-head line both give sane intervals.
- Game tests (`LiftGameTests` in common, registered in both the Forge and Fabric gametest classes):
  - `savedDataRoundTrip`: create a line, save the NBT, load a fresh `LiftSavedData`. Every field is equal and the
    head order is unchanged.
  - `travelAtFixedTicks`: a powered line run for 200 ticks has the travel and speed `LiftMotion.advance`
    predicts, to 1e-6.

### WP2: Blocks and block entities (sections 13, 16, 18, appendix)

**Build:**
- **Blocks:** `lift_tower_pole`, `lift_tower_head` (+BE), `lift_drive_station` (+BE), `lift_return_station` (+BE),
  `lift_loading_gate`, `lift_stop_button`. Register them in `ModBlocks`, the block entity types in a new
  `ModBlockEntities`, and their items in `ModItems`. Add them to the creative tab after the park blocks.
- **Head BE** stores `lineId`, `prev` and `next`. **Station BEs** store `lineId`, and the drive also stores
  `speedLevel`.
  - In `onLoad` (and on Fabric's first server tick, if there is no `onLoad`), each BE compares its stored data
    with the saved line. A mismatch faults the line.
  - Each BE also carries an optional `PendingLine` (decision 5).
- **Breaking** a head or a station faults its line (WP1 `fault`).
- **Placement warning:** placing a station core with solid blocks in the 5×5×5 around it sends an action-bar
  warning. Placement still succeeds.
- **Data:**
  - blockstates, models, placeholder textures and a generator script,
  - loot tables (each block drops itself),
  - `mineable/pickaxe` tags,
  - `create:non_breakable` with `required: false` entries,
  - lang,
  - recipes (the cost is provisional; phase 9 runs the balance pass):
    - pole: iron bars and iron ingots
    - head: iron, iron bars, and a lightning rod or chain
    - drive: iron block, redstone, piston
    - return: iron block, piston
    - gate: fence gate and iron
    - stop button: stone button and redstone
    - linker: iron ingot, redstone, string (this one is from the doc's item table)

**Tests:**
- Game tests:
  - `breakingAHeadFaultsTheLine`: build a 3-node line in the test structure and break the head. The line is
    faulted and its speed falls at 2.0 b/s².
  - `mismatchedBlockEntityFaultsTheLine`: edit a head's stored `next` and reload the chunk, or call `onLoad`
    again. The line is faulted.
  - `everyLiftBlockDropsItself` (loot table check).
- Datagen output must be committed, and CI step 1 (datagen) must stay clean.

### WP3: Linker (section 13, "Linking with lift_linker")

**Build:**
- Replace the `lift_linker` placeholder with a `LiftLinkerItem`. Its in-progress chain goes in item NBT:
  - Right-click a drive station to start.
  - Right-click heads in order to add them.
  - Right-click a return station to finish.
  - Sneak and right-click a head to unlink its line.
- **`LinkValidator`** (pure, over a `BlockSolid` predicate interface). Each step must satisfy:
  - the horizontal distance is within `maxSpan`,
  - the slope is at most 60°,
  - the sagging cable, sampled every 2 blocks along `LiftPath`'s span formula, clears solid blocks by 1 block,
    on both the loaded side and the return side.
- **On finish:** check `maxHeads`, `maxLength` (the loop length), `maxLinesPerLevel` and that the drive and return
  stations are distinct. Then create the line, write `lineId`, `prev` and `next` into every BE, and save.
- **Messages:** every rejection names its reason (lang keys `message.alpineshred.linker.*`).
- **Relinking** a faulted line through the same nodes rebuilds it and clears the fault.

- **Writing node data:** write it through `LiftTowerHeadBlockEntity.setLink(prev, next)` and the BEs'
  `setLineId`, the hooks WP2 provided.

**Tests:**
- Unit `LinkValidatorTest`:
  - each rejection reason, in isolation: too far, too steep, cable blocked on the loaded side, cable blocked on the
    return side, too many heads, too long;
  - a valid 10-head chain passes;
  - a blocked check reports the first blocked sample.
- Game test `linkerBuildsALine`: a scripted fake-player use of the linker on drive, 3 heads and return produces a
  line with the right node order, and every BE holds the right `prev` and `next`.
- Game test `linkerRejectsABlockedSpan`: a stone wall across the span blocks it, and no line is created.

### WP4: Chair entity and spawning (section 13, "Chair entity")

**Build:**
- **`ChairEntity`** (registered as `lift_chair`):
  - fields `lineId`, `chairIndex`, and synced `barDown`;
  - no gravity, no physics, invulnerable, not pushable;
  - tracking range 10 chunks, update interval 20.
  - Every tick on both sides, it sets its position to `path.chairPos(travel, index, count)` and its yaw from
    `tangent`.
    - The client uses its own line cache (WP5), so chair motion never depends on move packets.
  - It overrides `save` and `shouldBeSaved` so that an empty chair is never written.
- **Seats:** `lift.seats`. `positionRider` puts seats 0.9 blocks apart, centred on the chair (±0.45 for two seats, as
  in the doc). The passenger faces the travel direction.
- **Passenger rule** (pure `PassengerRule` taking width, height, a player flag and the tag flags):
  - a player;
  - a living mob that is not in `#alpineshred:lift_blocked` and is within `maxPassengerWidth` and
    `maxPassengerHeight`;
  - or any type in `#alpineshred:lift_passengers`.
  - Tags: `lift_blocked` defaults to the ender dragon, the wither and the warden. `lift_passengers` is empty.
  - A mob that boards is marked persistent, and its original flag is restored when it gets off or unloads.
- **Manager spawning:** every 20 ticks, for each line, spawn the missing chairs whose position is in a loaded
  chunk when a player is within 128 blocks. Despawn empty chairs when no player is within 192 blocks.
- **Re-attach:** a player who logs in with a saved chair vehicle re-attaches to the chair with the same `lineId`
  and `chairIndex`, spawning it if needed. If the line is gone, eject the player onto the nearest safe ground.
  The ground search must not loop forever: search a capped column, and fall back to the drive's platform.

**Tests:**
- Unit `PassengerRuleTest`:
  - a player is allowed;
  - a cow is allowed;
  - a warden is blocked;
  - a mob that is too wide or too tall is blocked;
  - a type on `lift_passengers` is allowed even when too big;
  - the seat count is respected.
- Game tests:
  - `chairPositionsAtFixedTicks`: after N ticks every chair's position equals `chairPos` to 1e-6, and its yaw
    matches the tangent.
  - `chairsSpawnNearPlayersAndDespawnFar`: use a mock player position, or spawn a fake player.
  - `blockedMobCannotBoard`, and `mobBoardsAndIsPersistent`, which also checks that the flag is restored after
    dismount.

### WP5: Sync (section 15)

**Build:**
- **`s2c/lift_line_sync`** carries: id, node positions, the chair spacing (`lift.spacing` is server config, and
  clients must use the server's value to get the same chair count), travel (a double), speed, target, game time, the faulted,
  running and emergency flags, and `removed` (decision 2).
- **When it is sent:**
  - once when a player comes within `lift.syncRadius` of any node (checked every 40 ticks against a per-player
    set of synced ids);
  - on a speed, target or flag change;
  - every 100 ticks to players in range;
  - on removal.
- The per-player set clears on logout and dimension change.
- **Chair hook:** set `ChairEntity.clientLineLookup` (`Function<UUID, LineView>`) from `ClientLiftLines`, and make
  the client line class implement `LineView`. Until this lands, client chairs move only from vanilla move packets,
  which arrive every 20 ticks.
- **`ClientLiftLines`:** a cache of `LiftPath`s and states. Each client tick it steps each line with
  `LiftMotion`. When a sync arrives, it catches up from the packet's game time to the client's level game time
  with `advance`, then replaces its state.
- **Register** in `ModNetwork` and `ClientNetwork`, following the existing packet patterns (the receiver queues
  onto the main thread).

**Tests:**
- Unit: a test of the packet's round trip through a `FriendlyByteBuf` built over `Unpooled.buffer()`. That needs
  no bootstrap, but check that first. If it does need one, make it a game test instead.
- Game test `clientCatchUpMatchesServer`: simulate a sync at game time T and a client advance to T+137. The result
  equals the server's travel at T+137 to 1e-6.
- Multiplayer smoke (WP10) checks the watchers' chair positions against the server's.

### WP6: Boarding, riding, unloading, mobs, progression (sections 7, 8, 13, 17, 18)

**Build:**
- **Manual boarding:** right-click a chair within 3 blocks that has a free seat, at line speed ≤
  `maxBoardSpeed`, holding a `lift_pass` if `requirePass` is on (the pass is not consumed).
- **Auto-board** (client option `client.autoBoard`, default true): a strapped player standing in the boarding
  stretch sends `c2s/lift_board` with a chair id when a chair with a free seat passes within 1.2 blocks
  horizontally and 2.5 vertically.
  - The server re-checks distance, seat, speed and pass.
  - A rate limit applies through the existing token bucket.
- **Ride state:**
  - Allow `→ LIFT` in `RideTransitions` when the vehicle is a `ChairEntity`.
  - Staying strapped on a chair is the doc's auto-unstrap exception (section 6). Check the unstrap-on-mount code
    path.
  - The validator must skip movement checks while the rider is in `LIFT`.
- **Bar:** it closes 20 ticks after boarding, and a passenger toggles it by right-clicking. It opens on entering
  the unload stretch.
- **Unloading:** at the ejection `s`, the rider stops riding, moves to the platform ground point and is set to
  `GLIDE` with velocity `tangent × 0.35`. Mobs are ejected with no velocity.
- **Sneak dismount** (decision 6) is allowed only when the bar is open, the chair is within 4 blocks of the
  ground, the line is faulted, or `dismountAnywhere` is on.
- **Mobs:**
  - With a lead: leashed mobs within 6 blocks board up to the free seats, and their leads return to the
    inventory.
  - Pets: tamed pets within 8 blocks of their owner board with them (`petsAutoBoard`).
  - Vanilla `/ride ... mount` follows the passenger rule.
  - All of this is gated on `mobsAllowed`.
- **Progression:**
  - Increment `lift_rides` on each completed ride (an unload at the top).
  - The trigger `alpineshred:lift_ridden` takes `min_length`, the loaded cable length ridden in blocks. Register
    it in `ModTriggers`.
  - Add the advancement `bluebird_day` (`lift_ridden`, `min_length` 100) and its lang.
- **HUD:** an action-bar prompt when boarding is possible (section 17).

**Tests:**
- Game tests, using a fake player (follow the patterns of the existing game tests that use a server connection):
  - `boardAndRideToTheTop`: board in the stretch, run the ticks, and the player is ejected at the top in `GLIDE`
    with the expected velocity. `lift_rides` is +1 and `bluebird_day` is granted when the line is at least
    100 blocks.
  - `boardingRefusedTooFastFullOrNoPass`: three separate refusals.
  - `sneakDismountRules`: refused mid-span with the bar closed; allowed when faulted; allowed with
    `dismountAnywhere`.
  - `leashedMobsBoard` and `petFollowsOwner`.
  - `riderSurvivesRelog`: a save-and-load round trip of the player's NBT with the root vehicle, then re-attachment
    to the same `lineId` and index. Also `riderEjectedWhenLineGone`.
- Unit: the auto-board proximity test as a pure function.

### WP7: Drive, stop button, commands (sections 13 and 18)

**Build:**
- **`LiftDriveSource`** interface: `enabled()`, `targetSpeed()`. `DriveStationBE` implements it.
  - It is enabled by a redstone signal, or always when `lift.driveNeedsRedstone` is false.
  - Right-click cycles through `lift.speedLevels`. The exception is a player holding a `lift_linker`: the drive's
    `use` must return `PASS`, because vanilla runs the block's `use` before the item's `useOn`, and otherwise the
    drive would swallow linker clicks. A game test must cover this.
  - Every change writes `powered` and `targetSpeed` into the line. Unloaded drives keep their last command.
  - Nothing else may know which class implements it: phase 9 adds the Create variant.
- **Emergency stop:** a stop button press, or losing redstone while running when `driveNeedsRedstone` is on,
  triggers `emergencyStop`. A redstone signal coming back, or right-clicking the drive, clears it.
- **Commands** (permission level 2): `/alpineshred lift list | info <id> | stop <id> | start <id> | remove <id>`.
  - `info` prints the nodes, loop length, chairs, speed, flags and rider count.
  - `remove` must call `clearLine()` on every loaded node BE of the line. Unloaded ones clear themselves on their
    next load check, because the line is missing.
  - `remove` deletes the line, clears the BEs' data, discards its chairs (ejecting riders safely) and sends the
    removal sync.

**Tests:**
- Game tests:
  - `redstoneDrivesTheLine`: with power the line ramps to 2.5. Removing power triggers an emergency stop at
    2.0 b/s².
  - `stopButtonStopsTheNearestLine`.
  - `speedLevelsCycle`.
  - `liftCommands`: run each subcommand through the server's command dispatcher and check its effect.

### WP8: Rendering, animation, sound (sections 9, 13, 16, 17)

**Build:**
- **`TowerHeadBER`:**
  - Sheaves rotate with travel.
  - The cable from this head to its `next` (and the drive's to the first head) is drawn on both the loaded and
    return sides, as textured cross-quads 0.12 wide, at most 2 quads per block.
  - The texture scrolls with travel.
  - It overrides `shouldRenderOffScreen` to true, with a view distance of 256.
  - Light is sampled at each span's midpoint.
- **`BullwheelBER`** on both stations: the wheel rotates by `travel / WHEEL_RADIUS`.
- **Passenger seat height:** WP4 seats passengers at chair Y + 0.1 + `getMyRidingOffset()`. The 0.1 is a
  placeholder; tune it against the chair model so riders sit on the seat.
- **`ChairEntityRenderer`:** draws the hanger, seat, backrest and bar. The bar eases over 6 ticks. Light is sampled
  at the chair. The model is placeholder geometry, at most 60 cuboids, with a 64×64 texture.
- **Animation clips** `lift_mount`, `lift_sit_board`, `lift_sit_skis`, `lift_dismount` (placeholders like the
  existing ones in `assets/alpineshred/player_animation/`). `RiderClips` maps `LIFT` to the sitting clips,
  replacing the current `IDLE` fallback.
- **Sounds:** `lift.motor_loop` (positional at the drive, volume `speed/4`), `lift.cable_creak` (random interval
  per head, gated by speed), `lift.bar_close`, `lift.bar_open` and `lift.e_stop`, all in the BLOCKS category.
  - `LiftSoundManager` plays these only for loaded block entities near the player.
  - Register them in `ModSounds` and `sounds.json`, with placeholder `.ogg` files in the same way earlier phases
    did.

**Tests:**
- Client smoke scenario `lift` (`ClientSmoke`, CI step "Client smoke test"):
  - Build a line beside spawn and power it.
  - Screenshots: cable and chairs from the side, the bullwheel, and seated first and third person.
  - Board, ride, get ejected, and assert `GLIDE` at the top.
  - Log the frame cost of the lift renderers. Fail if the mean cable cost is over 3× the budget (CI hardware
    varies). The real budget is checked by hand with spark.
- Visual review of the screenshots by the orchestrator.

### WP9: Generated lifts (section 12)

**Build:**
- `ResortStructure` gains the lift line: a drive station at the top of the resort's run, a return station by the
  lodge, and towers about every 40 blocks along the run's side, offset so the cable clears the run.
  - The line must pass `LinkValidator` against the generated terrain. If it does not, skip the lift and log at
    debug level. Never place a broken line.
  - Each piece places its blocks with `PendingLine` NBT (decision 5). The node positions must be **absolute
    world positions, computed after the piece's rotation and placement**. Template NBT is not transformed, so the
    piece writes the BE data in code when it places the blocks.
  - The stations get small platforms built from ordinary blocks.
  - The line spans about 200 blocks (from the doc).
- Generated lines start powered at the normal speed when `lift.driveNeedsRedstone` is off. Otherwise they get a
  lever by the drive, left off.
- Keep within the doc's 150 ms structure budget for a resort.

**Tests:**
- Worldgen game tests:
  - `resortHasALinkedLift`: place a resort and tick until the BEs load. Exactly one line exists, its nodes match
    the placed blocks, it is not faulted, and all of it stays inside the structure's bounds.
  - `pendingLineAdoptedOnce`: when every BE loads, still only one line exists.
- The worldgen tour (`WorldgenSurvey`) also counts resort lifts, and checks there are no faulted generated lines
  across the test seeds.

### WP10: Integration tests, multiplayer, budgets, docs

**Build:**
- **Multiplayer smoke** (`multiplayer-smoke.sh`, `ServerSmoke`, `ClientSmoke` scenario `multi`, and
  `multiplayer-check.py`). Add a lift stage after the run:
  - `SmokeCourse` builds a lift with a loop length of at least 400 blocks.
  - All three clients board and ride. Watchers sample the chair positions they see, and the server logs its own;
    the check compares them (tolerance 0.25 blocks).
  - Watcher 1 logs out mid-ride and back in, and must re-attach to the same chair.
  - The rider breaks a head with a command once at the top. The line goes faulted, decelerates at 2.0, and the
    riders can dismount.
- **Save and reload with riders:**
  - On the dedicated server: `save-all`, stop, restart with the same world, and the clients reconnect.
  - If this makes CI too slow or flaky, keep a game-test version (`liftSurvivesSaveReload`: the saved data plus
    the rider NBT round trip) and move the full restart into `docs/playtest-phase8.md`.
- **Budgets:** a game test `liftManagerBudget` runs 5 lines with 50 chairs each for 200 ticks and logs the mean
  manager time and the per-chair tick time.
  - Assert at 3× the budget (0.9 ms per tick, 15 µs per chair). Record the real numbers in the README.
  - The doc's budgets are 0.3 ms per tick for 5 lines and 5 µs per chair.
- **Docs:**
  - README:
    - Status line.
    - A "What phase 8 delivers" section.
    - Clarifications 13 onward, covering the decisions above.
    - Update the "Not in phases 5 to 7" note.
  - `docs/playtest-phase8.md`, the manual checks:
    - three people ride a 400-block lift together, with a logout mid-ride and a head broken;
    - a repeat with 200 ms latency and 2% loss (clumsy on Windows, or `tc netem`);
    - lifts with Sodium/Embeddium and Iris/Oculus;
    - a NeoForge server;
    - building a lift by hand in survival.
    - The linker checks that the cable clears terrain, but not the chairs hanging 1.6 blocks below it. Check by
      hand that chairs on a valid line don't clip through hills. If they do, add a chair-clearance sample to
      `LinkValidator`.

## 4. Test inventory (what "done" means for phase 8)

| Layer | Tests |
|---|---|
| Unit (JUnit, `common/src/test`) | `LiftPathTest` (done), `LiftMotionTest`, `LiftStationsTest`, `LinkValidatorTest`, `PassengerRuleTest`, auto-board proximity, `lift_line_sync` round trip (if it can run without a bootstrap) |
| Game tests (both loaders) | savedDataRoundTrip, travelAtFixedTicks, breakingAHeadFaultsTheLine, mismatchedBlockEntityFaultsTheLine, everyLiftBlockDropsItself, linkerBuildsALine, linkerRejectsABlockedSpan, chairPositionsAtFixedTicks, chairsSpawnNearPlayersAndDespawnFar, blockedMobCannotBoard, mobBoardsAndIsPersistent, clientCatchUpMatchesServer, boardAndRideToTheTop, boardingRefusedTooFastFullOrNoPass, sneakDismountRules, leashedMobsBoard, petFollowsOwner, riderSurvivesRelog, riderEjectedWhenLineGone, redstoneDrivesTheLine, stopButtonStopsTheNearestLine, speedLevelsCycle, liftCommands, resortHasALinkedLift, pendingLineAdoptedOnce, liftSurvivesSaveReload, liftManagerBudget |
| Client smoke (CI) | scenario `lift`: screenshots, board, ride, eject, renderer frame cost |
| Worldgen tour (CI) | resort lifts counted, none faulted |
| Multiplayer smoke (CI) | lift stage: three riders on a lift of 400+ blocks, watchers' chair positions match the server, re-attach after relog, faulted line on a broken head |
| Manual (`docs/playtest-phase8.md`) | latency and loss repeat, render mods, NeoForge, survival build, full server restart with riders if CI cannot do it |

Earlier phases' tests must stay green. That means the game tests (48+ per loader today) and every CI smoke run.

## 5. Exit gate mapping

| Gate item | Covered by |
|---|---|
| Three players ride a 400-block lift | Multiplayer smoke lift stage (WP10) |
| …across a save and reload | `liftSurvivesSaveReload` and `riderSurvivesRelog`, plus a server restart in the smoke run or the playtest |
| Faulted-line handling works | `breakingAHeadFaultsTheLine`, `mismatchedBlockEntityFaultsTheLine`, `sneakDismountRules`, `riderEjectedWhenLineGone`, the multiplayer smoke's head break |
| Budgets met | `liftManagerBudget`, client smoke renderer timing, a spark check by hand (playtest) |

## 6. After phase 8

Phase 9, "Create compatibility and polish", is the last phase. Its plan gets written when phase 8 merges.

**Deliverables (section 20):**
- the kinetic drive (`KineticLiftDriveBE implements LiftDriveSource`; section 14),
- wrench support,
- tags,
- recipes and the balance pass,
- the server guide,
- optimization,
- the release pipeline.

**Exit gate:** the section 19 compatibility matrix passes, and a release candidate runs for two weeks with no
open blockers.
