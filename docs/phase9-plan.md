# Phase 9 plan: Create compatibility and polish

**Status:** Merged into `main`. WP0 to WP9 and the flip fix are done, and every automated exit-gate item passes in
CI. Open before the release: the hand rows and open list in `docs/playtest-phase9.md`, including the spark check.

This is the work plan for phase 9, the last phase of development. There is no separate design document. What
the game does is defined by this plan, the earlier phase plans, and the README ("What phase N delivers" and
"Design clarifications made while building"). Older text that cites "the design doc" or its section numbers refers
to that record. When something isn't covered, Opus decides it by the direction below and records it as a README
clarification. The README's "Phases" table is the project-wide overview. Update its phase 9 row when this phase
merges.

**Direction: real-feeling, but blocky, fun and simple.** It's Minecraft:
- Things should behave the way a player expects a ski lift, a Create machine or a snowboard to behave.
- Art stays pixel art and chunky models in vanilla's style.
- Numbers are chosen for fun and readability over realism.
- When in doubt, pick the simpler rule with fewer special cases.

This applies to every deliverable and every review.

**Deliverables:**
- the kinetic drive (`KineticLiftDriveBE implements LiftDriveSource`), a Create-powered way to run a lift,
- wrench support (Create's wrench on our blocks),
- tags,
- recipes and the balance pass,
- the server guide,
- optimization,
- the release pipeline, built and dry-run only: publishing comes after development.

**Exit gate (end of development):**
- The compatibility matrix (WP9) passes in CI and by hand.
- Every budget is met.
- The release pipeline's dry run builds correct jars for all three targets.

The release itself follows at the user's call, after development: a release candidate, a two-week soak with no
open blockers, then publishing to Modrinth, CurseForge and GitHub Releases. That isn't part of this phase's merge.

---

## 0. Roles and the path to `main`

Two roles. Neither does the other's job.

**Sonnet, the developer.** It writes all the code, tests and docs for one work package (WP) at a time.
- Each WP gets its own branch, `wip/phase-9-wpN`, cut from `wip/phase-9`.
- It follows section 1 of this plan, runs its own CI loop to green, and hands back.
- It never merges, never pushes to `wip/phase-9` or `main`, and never loosens a test, a tolerance or a budget to
  get green. If a check looks wrong, it says so in the handback.

**Opus, the reviewer.** It gives the final approval and the criticism. No code reaches `main` without it.
- It reviews every WP diff against the checklist in section 2. Then it approves, or it sends a numbered list of
  fixes back to the same Sonnet agent, which keeps its context.
- It merges approved WPs into `wip/phase-9`.
- It reviews `wip/phase-9` as a whole before the merge to `main` (section 2, "Final review").
- It settles open design questions by the direction above, records each as a README clarification, and asks the
  user only about taste calls that a player would notice, before the WP that needs them.
- It asks the user before merging into `main`.
- It writes code only for one-line fixes that cost less than another round trip. It records those in the merge
  commit.

**The path**, for every WP:

```
Sonnet: wip/phase-9-wpN  ->  CI green  ->  handback
Opus:   review  ->  fixes back (loop)  or  approve  ->  merge into wip/phase-9  ->  CI green on wip/phase-9
...after the last WP...
Opus:   final review of wip/phase-9 against main  ->  user confirms  ->  merge into main
```

**Start of phase:** `wip/phase-9` is cut from `main` at `3adc746` or later, where phase 8 is merged.

## 1. What Sonnet follows while working

### 1a. How CI works here

- The cloud container cannot reach the Fabric, Forge or Architectury mavens, so `./gradlew` does not run locally.
  CI is the build. Push the branch, and a run takes 40 to 80 minutes.
- When a run finishes, it force-pushes its logs to the `ci-logs` branch. `RUN.txt` there names the run, its sha and
  its conclusion. Read logs with `git show origin/ci-logs:<path>`, and list them with
  `git ls-tree -r --name-only origin/ci-logs`.
- Runs on different branches overwrite each other's `ci-logs`. Only one branch runs CI at a time: Opus says which.
- Wait with a background shell loop that polls `RUN.txt` for your sha:
  `until git fetch -q origin ci-logs; git show origin/ci-logs:RUN.txt | grep -q <sha>; do sleep 120; done`.
  Don't poll in the foreground.
- To check a Minecraft, Create or loader API signature before relying on it, use the API probe. Add the lookups to
  `.github/api-probe.txt`, then delete that file in the next commit (see `.github/scripts/api-probe.sh`).
- Three CI rounds per WP at most. If it is still red after that, hand back with what you know.

### 1b. Code quality (reviewed on every WP)

- **Code layout.**
  - Common code goes in `io.github.adang101.alpineshred.<area>`.
  - All Create code goes in `...compat.create` (common) and the loaders' `compat.create` packages. Nothing outside
    those packages imports a Create class (section 3, decision 1).
  - Loader modules get only registration hooks and the code that has to be loader-specific.
- **Pure logic stays pure.** If something can be computed without Minecraft (rpm to speed, stress, balance
  tables, matrix rows), put it in a pure class with a JUnit test in `common/src/test`.
- **Reuse before writing.** Look for an existing helper first:
  - **Drives:** `LiftDriveSource` (every drive goes through it and never names an implementing class) and
    `LiftMotion.targetFor`.
  - **Config:** `ConfigSpec` and `AlpineConfig`.
  - **Packets and payloads:** `SyncCodec` and `ModNetwork`.
  - **Generated data:** the datagen providers in `fabric/.../datagen`.
  - **Game tests:** the patterns in `LiftGameTests`, `Course` and `SmokeCourse`.
  - **Mod presence:** `PlatformHooks.isCreatePresent`.

  A second copy of logic that already exists gets sent back.
- **Determinism.**
  - Lift and path math uses doubles in a fixed order. Never use floats or `Math.fma`.
  - Never iterate a `HashMap` where order reaches output, saved data or a packet.
- **Generated data.** Lang, recipes, loot tables and tags come from datagen. After changing a provider, regenerate
  with `.github/scripts/run-datagen.sh` and commit `common/src/main/generated`. CI fails on stale data.
- **Placeholder art.** Generate it with a Python script in `art/placeholder/`, and commit both the script and its
  output.
- **Comments.** Write a javadoc for each public class and each non-obvious method, giving its reason (and the
  README clarification, if one applies). Don't narrate what the code does.
- **Art and feel.**
  - Textures are 16×16 pixel art in vanilla's palette, and models are chunky boxes.
  - Sounds are short and readable.
  - Every new behaviour is something a player can predict after trying it once.

### 1c. Game performance (reviewed on every WP)

- **No allocations in per-tick or per-frame code.** That covers server ticks, block entity ticks, renderers and
  client tick handlers: no collections, boxed numbers, streams, lambdas that capture, or string formatting there.
  Reuse buffers. A hot path with `new` in it needs a reason in the handback.
- **Cache whatever is derived.** Rebuild it only when its input changes. `LiftPath` and the station intervals are
  the model.
- **Budgets are asserted in tests.** Measure every new per-tick system the way `liftManagerBudget` does, at three
  times the figure in decision 6 (README clarification 71), and report the measured number in the handback.
- **Sync traffic stays event-driven.** Send on change plus a slow resend, as `LiftSync` does. Never send every
  tick.
- **Without Create, the cost is zero.** No Create class loads, no extra block entity ticks, and no extra packet.

### 1d. Token efficiency

- **Read narrowly.**
  - Use `grep -n` to find a symbol, then read that part of the file with an offset and limit. Don't read whole
    large files or generated data.
  - Read CI logs through `grep` and `tail`. Never dump a whole log.
- **Get it right before pushing.** A CI round trip costs an hour, so a compile error is the most expensive
  mistake:
  - check every import, signature and mapping name against code already in the repo, or the API probe;
  - then reread your own diff once before pushing.
- **Keep scope tight.** Change what the WP needs. Write refactors you notice as a line in the handback, not as
  code.
- **Keep the handback short.** Use this template:

```
WPn handback
Built:        <bullets: what, with file paths>
Tests added:  <unit / game test / smoke names>
CI:           <run id, sha, result> per round
Measured:     <budget numbers, where relevant>
Deviations:   <from this plan, each with its reason>  (or "none")
Open:         <anything Opus must decide>                       (or "none")
Branch tip:   <sha>
```

### 1e. Commits

- Use the message style `Phase 9: <what>`, one commit per coherent step. Never push to `wip/phase-9` or `main`.
- Earlier phases' tests stay green: the unit tests, the game tests on both loaders, and every CI smoke run.

## 2. What Opus checks

### Per-WP review

Read the handback, then `git diff wip/phase-9...wip/phase-9-wpN`. Read whole files only where the diff isn't
enough. Approve only when every item below holds:

1. **Correct against this plan.** Each deliverable of the WP is there, and each deviation is justified and
   recorded as a README clarification.
2. **Blocky, fun, simple.** It feels like Minecraft: the art is in vanilla's style, the rules are few and
   predictable, and the numbers are easy to read. There is no realism that costs fun or adds settings nobody
   needs.
3. **Tests prove it.** The new behaviour has a unit or game test that would fail without it. No test was
   weakened, skipped or retimed to pass. CI is green on the handback sha (`RUN.txt`).
4. **Reuse.** Nothing duplicates an existing helper (section 1b). New helpers are general enough for the next WP.
5. **Performance.**
   - There are no allocations in hot paths.
   - Budgets are asserted and the measured numbers reported.
   - Without Create, nothing extra runs (section 1c).
6. **Isolation.** No Create import outside `compat.create`. The mod still loads without Create; the existing CI
   runs are the "without Create" column.
7. **Determinism and data.** Ordering is fixed where it matters, and generated data is regenerated and committed.
8. **Scope.** There are no unrelated changes, no debug leftovers, and no probe files.

Fixes go back as a numbered list, most severe first, each with the file, the line and the expected outcome. A
finding is either **must fix** (it blocks the merge) or **note** (record it, don't block). Keep each review
round to one message.

### Final review (`wip/phase-9` into `main`)

- `git diff main...wip/phase-9 --stat`, then a pass over each WP's merge for interactions between packages, such
  as two WPs touching the same registry, config or packet.
- CI is green on the `wip/phase-9` tip, with both the run with Create and the run without it.
- The exit gate mapping in section 5 is complete, and every row points to a passing test or a playtest line.
- README: the phase 9 row in "Phases", "What phase 9 delivers", the new clarifications, and the layout section.
- Then ask the user, and only then merge with `--no-ff`.

## 3. Decisions (settled now; record each in the README clarifications)

1. **Create stays optional and isolated.**
   - Compile against Create with `modCompileOnly` on each loader: Forge `create_forge_version=6.0.0`, Fabric
     `create_fabric_version=6.0.8.1`, both already in `gradle.properties`. Add it as dev runtime only for the
     Create game-test runs.
   - Common code sees Create through a small interface in `compat.create`, such as `CreateBridge`. Each loader
     implements it in its own `compat.create` package, and that class is loaded only behind
     `PlatformHooks.isCreatePresent()`.
   - No Create type appears in a common class's signature, a mixin or an interface our blocks implement, so the
     mod never fails class loading without Create.
2. **The kinetic drive is a separate block.** `kinetic_lift_drive` is a chunky gearbox block that sits beside a
   drive station and has a `KineticLiftDriveBE` that implements `LiftDriveSource`. It is registered only when
   Create is present, and its recipe is conditional on Create. The drive station's behaviour without it is
   unchanged.
   - **Speed:** `|rpm| / 64` blocks per second, clamped to `lift.kineticMaxSpeed` (4.0 by default). So 64 rpm is
     1 b/s and 256 rpm, Create's top speed, is the 4 b/s cap. Below 8 rpm it counts as stopped. The direction of
     rotation doesn't matter; the cable always runs up.
   - **Stress:** a flat impact of `lift.kineticStress` per rpm, 4.0 by default. That puts a full-speed lift at
     1024 su, about a big water wheel setup. It doesn't depend on the chair count: simple, and predictable from
     the tooltip.
   - **Losing rotation** while running, or overstress, is an emergency stop, as losing redstone is (README "What
     phase 8 delivers").
   - **Which drive wins:** while a kinetic drive is attached, it is the source and redstone is ignored. Remove it,
     and the redstone drive takes over again.
3. **Server config that the client predicts goes into `config_sync`.** `lift.maxBoardSpeed` and
   `lift.dismountAnywhere` join it, and `SyncCodec.PROTOCOL_VERSION` goes from 1 to 2. A client on another
   protocol already ignores the sync, as phase 8 does today.
4. **A second CI column with Create.**
   - The game tests run a second time with Create on the runtime classpath, on both loaders. Kinetic tests live
     in a separate holder that only runs there.
   - The current job is at 120 minutes, and a full run takes up to 80. If the extra runs push it past about 100,
     WP2 moves them into a second job that runs in parallel. It must not raise the timeout again.
5. **The balance pass changes data, not code.** Recipe costs and gear stats change in the datagen providers and
   the data-driven stats files. Every changed number is listed in one README clarification, with the reason.

6. **Budgets are the project's own.** The phase 8 figures stand: the lift manager at 0.3 ms a tick for five lines,
   5 µs a chair, and resort generation at 150 ms a start. The cable's frame budget becomes final at 0.5 ms a frame
   (measured 0.28 ms, README clarification 69). The kinetic drive's budget is 0.02 ms a tick per drive. Tests
   assert each at three times, as before.
7. **Release targets:** Modrinth, CurseForge and GitHub Releases, all three. WP8 builds the pipeline with a dry
   run only. Real publishing, and the tokens it needs (repository secrets, never in the repo or the chat), come
   after development, at the user's call.

## 4. Work packages

Dependencies: WP1 runs first and on its own. WP2 needs WP1. WP3 and WP4 need WP2 and can run in parallel. WP5
needs WP3, so the kinetic drive's recipe gets balanced. WP6 can start after WP1. WP7 needs WP3 to WP6. WP8 can
start after WP1. WP9 closes the phase.

Since only one branch runs CI at a time (section 1a), "in parallel" means the work overlaps but the pushes take
turns. Opus schedules them.

### WP0: The chair-sync fix (done)

The multiplayer chair check failed intermittently on `main`'s code. The cause: the server's time packet and
the 100-tick resend arrive between the same two client ticks, and the jump was applied to the freshly synced
line twice. `LineClock` fixes it (README clarification 73), with `LineClockTest`. Two green multiplayer runs
followed (CI runs 42260553304 and 42268884498). Merged into `wip/phase-9`.

### WP1: Carry-overs from phase 8 (small)

**Done.** Merged into `wip/phase-9` (CI run 42293537234 on `5629994`, all green; chair check 0/0/0 over 0.25, max
0.126 on every client). The catch-up runs at `CLIENT_PRE` and follows `gameTime + 1`. The flake's cause: the test
places a start planned on its own seed into the game-test level, whose terrain comes from a random seed and could rise
above a tower head; the test now sets each head's column to what the plan assumed (README 74, 75). Also fixed:
`liftSurvivesSaveReload` took chair 0 when a concurrent test's player had already spawned it. Left over: `lift.seats`
is not synced (moved to WP2).

- Make `ClientLiftLines` import `Consumer` instead of naming it in full (WP0 review note).
- Move the line catch-up before entity ticks (the client's tick start), so chairs don't lag a large clock jump
  by one tick. WP0 saw a single 1.0-block sample right after a 485 ms server stall. Check it with the
  multiplayer smoke's `lift:` trace lines.
- `resortHasALinkedLift` failed once on Forge ("no pole under the head", run 42260553304) and passed on the
  rerun. Find the cause (concurrent tests sharing the level, or chunk generation order; see "Carried forward" in
  the phase 8 plan, section 3b) and fix it. Don't retry it away.

- Add `lift.maxBoardSpeed` and `lift.dismountAnywhere` to `config_sync` and bump the protocol (decision 3). The
  client's board prompt and dismount prediction read the synced values.
- Add `.github/scripts/__pycache__/` to `.gitignore`.
- Tests: a `SyncCodec` round trip including the two keys (unit), and a game test that a changed server value
  reaches a fake client's view.
- Done when: CI is green, and README clarification "config_sync carries the lift keys" is added.

### WP2: Create groundwork

- Add `lift.seats` to `config_sync` (WP1 leftover; the client's free-seat check reads it). No protocol bump: a
  missing key keeps its default, and protocol 2 has not shipped.
- Add the Gradle dependencies (decision 1), the `compat.create` packages, `CreateBridge` and its two loader
  implementations, registered behind `isCreatePresent`.
- Add the CI column with Create (decision 4), with one trivial game test that proves Create loaded and the
  bridge is live on both loaders.
- Probe the Create API first (section 1a). Write down the classes the later WPs will use: the kinetic block
  entity base, speed and stress, wrench hooks and tags.
- Done when: both columns are green, the mod loads without Create (the existing runs), and the probe results are
  in the handback for WP3 and WP4.

**Done.** Merged into `wip/phase-9` (CI run 42308714073 on `386824b`: main job green, chair check 0/0/0 over 0.25;
Create column green on both loaders, 91 of ours each). Pins: Forge Create 6.0.8-291 (slim) with Flywheel 1.0.6,
Ponder 1.0.92, Registrate MC1.20-1.3.11, MixinExtras at runtime; Fabric Create 6.0.8.1 from the Modrinth maven,
whose nested jars a sha256-checked CI step unpacks, and Fabric Loader 0.17.2 in that column only. On Fabric,
Create's own game tests run too and are reported but do not gate; `gametest-junit-check.py` judges ours (README 77).
It took 6 CI rounds (3 granted extra for the Fabric runtime).

**Create API notes for WP3 and WP4** (from WP2's probe; same names on both loaders):
- Kinetic base: `com.simibubi.create.content.kinetics.base.KineticBlockEntity` (extends `SmartBlockEntity`,
  constructor `(BlockEntityType<?>, BlockPos, BlockState)`). A consumer reads `getSpeed()` (signed rpm, float),
  `isOverStressed()`, overrides `calculateStressApplied()` and `onSpeedChanged(float)`; `addToGoggleTooltip`.
- Stress: `com.simibubi.create.api.stress.BlockStressValues.IMPACTS` (a `SimpleRegistry<Block, DoubleSupplier>`);
  register the drive's impact there, or override `calculateStressApplied()` with `lift.kineticStress`.
- Blocks: `KineticBlock` / `HorizontalKineticBlock` implementing `IRotate` (`hasShaftTowards`, `getRotationAxis`),
  plus `IBE<T>` (`getBlockEntityClass`, `getBlockEntityType`). Our block entity type stays in our Architectury
  registry, not Registrate.
- Wrench: `content.equipment.wrench.IWrenchable` (`onWrenched`, `onSneakWrenched`); our common blocks must not
  implement it (decision 1). Use loader-side click handlers in `compat.create` (Forge `RightClickBlock`, Fabric
  `UseBlockCallback`) for `AllItems.WRENCH`, or the `create:wrench_pickup` tag for pickup.
- Tags (`AllTags.AllBlockTags`): `create:non_movable`, `create:non_breakable`, `create:wrench_pickup`,
  `create:safe_nbt`, `create:relocation_not_supported`, `create:brittle`, `create:seats`; item tag
  `create:wrench`. Tag JSON needs no Create compile dependency (optional entries).
- Compile needs only the Create jar; if a Registrate or Flywheel type appears in a signature WP3 touches, add that
  artifact as `modCompileOnly`.

### WP3: The kinetic drive (decision 2)

- `kinetic_lift_drive` and `KineticLiftDriveBE implements LiftDriveSource`, in the loaders' `compat.create`
  packages on Create's kinetic base. The pure rpm-to-speed mapping lives in common.
- The drive station takes its `LiftDriveSource` from an adjacent kinetic drive when there is one, and otherwise
  from redstone (decision 2).
- Ramping and emergency stops reuse `LiftMotion`. There is no new motion code.
- Datagen: the block, its item, lang (with a tooltip that shows the stress), a conditional recipe, loot, and the
  `create:` tags it needs. Placeholder art (a gearbox with a cable wheel face) goes in a script.
- Tests:
  - unit: rpm to speed, the clamp, direction;
  - game tests (Create column): rotation drives the line; losing rotation is an emergency stop; overstress stops
    it; the speed follows rpm within the ramp; the drive survives a save and reload;
  - a budget test: under 3× 0.02 ms a tick per drive (decision 6).
- Done when: both columns are green, the `lift` smoke is unchanged, and the measured numbers are reported.

**Done.** Merged into `wip/phase-9` (run 42355396757 on `dba2f22`, green on both jobs; 97 of ours per loader in
the Create column). Measured 0.0006 to 0.0012 ms a tick per drive against the 0.02 ms budget. The drive drops
itself in code (no loot table), its recipe and tag entries are written by id with load conditions or
`required: false`, and its six game tests run in their own batch (README 78, 79). At the merge it joined
`alpineshred:wrench/lift_pickup` as an optional entry. Note for WP8: the Fabric compile now needs Create's nested
jars on every build (`.github/scripts/unpack-create-nested.sh`), so release builds must run that script too.

### WP4: Wrench support

Create's wrench on our blocks, kept to rules a player can guess:
- **Park pieces** (rails, boxes, ramps, pipes): a click does what the park wrench does (cycle the shape, or turn
  it when sneaking) by calling the same code. Sneak-clicking with Create's wrench picks the piece up, as Create
  does for its own blocks.
- **Lift blocks:** a plain click does nothing, and the action bar says to use the lift linker. Sneak-clicking
  picks up a pole, a head, a station or the kinetic drive, and that faults the line exactly as breaking it by
  hand does. Lift nodes are already in `create:non_breakable`, so contraptions leave them alone.
- Use tags and Create's events or registries from `compat.create`, never an interface on our block classes
  (decision 1).
- Check that the park wrench and the lift linker still work as before, and don't add Create behaviour to them
  (the park wrench stays the park tool).
- Tests: game tests in the Create column for each rule above, and one test that the faulting path is shared.

**Done.** Merged into `wip/phase-9` (tip `8e8852b`; run 42321366075 green on both jobs at `8f2a9a9`, and run
42355456284 at the tip green on the Create job). Create's wrench is matched by the id `create:wrench` (Create
6.0.8's `create:wrench` item tag lacks it). Plain click on a park piece runs the park wrench's cycle, sneak picks
up; lift blocks in `alpineshred:wrench/lift_pickup` show the linker hint or are picked up through
`destroyBlock`, the same fault path as breaking by hand (README 80, 81). Playtest: a wrench pickup on Fabric reaches
the server in real play.

**Open, found by WP4's run 42355456284:** the Mountain ride smoke failed once with "server validator flagged
real-terrain riding 1 times: air report: flip 228 in 11 ticks". The rider is the scripted honest client, so the
client let a flip through that `TrickValidator` rejects (flip rate, or whole flips needing 16 air ticks each).
Find where the client and server rules differ and fix it so they agree; don't loosen the validator. Assigned to a
small standalone fix before WP6.
**Fixed** (merged; branch `wip/phase-9-flip`, run 42359456092): `TrickAngles.flips` rounds, so 228 degrees is a
whole flip and needs 16 air ticks; the client's `AirRotation` had no air bound. Shared `FlipLimit` now holds both
rules; the validator calls it unchanged and the client clamps pitch to it (README 82). A short air holds an
unfinished flip under a half turn. That run's only real failure was a WP6 item: watcher1's design textures built
in a mean 5.585 ms against the 5 ms budget.

### WP5: Tags, recipes and the balance pass

- **Tags:** common tags (`c:` on Fabric, `forge:` on Forge) wherever another mod would look for them (tools,
  armor, dyeable gear, snow blocks), plus `lift_blocked` and `lift_passengers` reviewed against vanilla mobs.
  Everything goes through datagen.
- **Recipes:** retire the "provisional until the phase 9 balance pass" note in `AlpineRecipeProvider`. Settle the
  lift costs, the kinetic drive recipe and the Create-conditional recipes.
- **Balance**, for fun and fair progression rather than realism:
  - starter gear is cheap, so a new player is riding within minutes;
  - better gear and lifts cost mid-game materials (iron, copper, redstone), and nothing needs end-game items;
  - list every changed number in one README clarification (decision 5).
- Tests: a datagen check that every item has a recipe or a reason, and every block has a loot table and a
  mineable tag (a unit test over the generated JSON, or a game test).

**Done.** Merged into `wip/phase-9` (run 42364491124 on `08cafc7`: everything green, Create job included, apart
from the multiplayer watcher's design-texture mean, 5.375 ms against 5 ms, the WP6 carry-over seen a second time).
The balance pass changed only the pro tier: diamond and an amethyst shard replace netherite in the smithing recipe,
which keeps carrying the design. Common tags are `forge:` only, since Fabric's 1.20.1 conventions have none for
these; groomed snow joins `minecraft:snow`. `GeneratedDataTest` checks recipes, loot tables and mineable tags
against the committed data (README 83 to 85).

### WP6: Optimization

- Carry-over: the multiplayer watcher's design-texture build ran a mean 5.585 ms against its 5 ms budget once
  (run 42359456092). Find the cost and bring it well under budget; don't raise the budget.

- **Resort generation:** about 200 ms a start in CI against the 150 ms budget (README clarification 59). The run
  planner's noise sampling dominates. Cache or downsample it, keeping the output identical for a seed (assert
  that in a test), and measure the result in the worldgen tour.
- **Hot-path audit:** go over every per-tick and per-frame path added in phases 5 to 8 (`LiftManager`,
  `LiftChairs`, `ClientLiftLines`, the lift renderers, `RiderTracker`) for allocations and repeated work (section
  1c). Fix each one with its before and after numbers.
- **Cable budget:** make `CABLE_BUDGET_MS` final at 0.5 ms (decision 6), and update README clarification 69.
- Don't change behaviour: every smoke and game test stays green with unchanged results.

**Done.** Merged into `wip/phase-9` (runs 42367878134 and 42376722696, green on both jobs). Design textures:
the watcher mean fell from 3.2 to 5.6 ms to 0.5 to 1.1 ms by warming the atlas at resource load (README 87). Resort
starts fell from about 210 ms to 133 to 138 ms by fetching independent noise columns side by side on the common
pool, with plans identical to the old planner for 8 seeds (README 86); a one-core server falls back to the old
speed. Hot paths no longer allocate per line, chair or cable piece per tick, each change bit-identical by test
(README 88). `CABLE_BUDGET_MS` is final at 0.5 ms (README 69).

### WP7: The server guide

- `docs/server-guide.md` for server owners:
  - every config key (generated from `AlpineConfig` if that's cheap, so it cannot drift), its default and range,
    and what it costs;
  - the commands and permissions;
  - lifts: limits, `runUnloaded` and `syncRadius`;
  - world generation toggles;
  - Create, and what changes with it present;
  - Curios and Trinkets;
  - the performance knobs and the measured budgets;
  - NeoForge, and known issues.
- The README links to it.
- Opus reviews it for accuracy against the code, not for prose.

**Done.** Merged into `wip/phase-9` (run 42385350787 on `262e701`, green on both jobs). `docs/server-guide.md` has
11 sections; its two config tables are rendered from `AlpineConfig` and `ServerGuideTest` keeps them in step. Review
fixes: the groomed-run figures from WP6, and `worldgen.frequencyMul` narrowed to 0.1 to 1.0 (README 91). Untested
claims (NeoForge, other Create versions, a client without Create) are marked "not yet verified".

### WP8: The release pipeline

- A workflow triggered by a `v*` tag:
  - build both jars with the version from the tag;
  - attach them and a changelog to a GitHub release;
  - publish steps for Modrinth, CurseForge and GitHub Releases (decision 7), reading their tokens from repository
    secrets and skipped when a token is missing;
  - a dry run (`workflow_dispatch`) that builds everything and publishes nothing. That dry run is all phase 9
    runs.
- Mod metadata: the version, the loader and Minecraft ranges, Create as an optional dependency on both loaders,
  and the Forge jar marked for NeoForge too.
- A `CHANGELOG.md`, built from the phase summaries in the README.
- Done when: a dry run on the WP branch produces both jars with the right metadata. No real publish happens in
  development.

**Done (dry run).** Merged into `wip/phase-9` (main CI run 42367538642 green on both jobs; dry run 42367602934
built and verified both jars, version `1.0.0+mc1.20.1`, nothing published). `release.yml` publishes only from a `v*`
tag; a manual run cannot publish. At the merge Opus pinned `mc-publish` to its v3.3.0 commit and set the Fabric
Create range to `>=6.0.0` to match Forge. Before the first real publish: check the mc-publish dependency slugs
against Modrinth and CurseForge (README 89).

### WP9: The compatibility matrix

- Write the matrix in `docs/playtest-phase9.md`. Mark each row as CI (with the step that covers it) or by hand.
  - **CI rows:** Forge and Fabric, each with and without Create; Curios (Forge) and Trinkets (Fabric); a dedicated
    server with three clients.
  - **By hand:** the Forge jar on a NeoForge 1.20.1 server; Sodium or Embeddium, and Iris or Oculus, for the
    renderers; a survival playthrough from new gear to a working kinetic lift.
- Fill whatever CI gaps are cheap, such as the multiplayer smoke with Create present.
- Collect the playtest items still open from phases 3, 4, 6, 7 and 8 into one list, so they're tracked in one
  place:
  - the generated-lift clearing pass;
  - the real server restart with riders;
  - render mods;
  - a NeoForge server.
- Done when: every CI row is green, and the hand rows are written so that someone can tick them off.

**Done.** Merged into `wip/phase-9` (run 42381069745 on `4514c53`, green on both jobs). `docs/playtest-phase9.md` has
the matrix: 7 rows covered by CI, each naming its job and step, and 5 rows by hand (10 checkboxes H1 to H10), plus the
consolidated open list O1 to O21 from phases 3 to 8; the older playtest docs point to it. The multiplayer smoke with
Create stays a hand row (H1): that step takes about 29 minutes, too costly for the Create job. Trinkets design sync
is by hand too (no Fabric client run in CI).

### After phase 9: the release (at the user's call)

1. Run the hand rows of the matrix and the open playtest list.
2. Tag `v1.0.0-rc1`, and soak it for two weeks. Blockers become fix WPs, and each fix restarts the clock.
3. Add the three tokens as repository secrets, and tag `v1.0.0` to publish to Modrinth, CurseForge and GitHub
   Releases.

## 5. Exit gate mapping

| Gate item | Covered by |
|---|---|
| Kinetic drive works (decision 2) | WP3 game tests (Create column), budget test |
| Mod works with and without Create on both loaders | both CI columns (decision 4), WP2's load test |
| Wrench support | WP4 game tests |
| Tags, recipes, balance | WP5 datagen checks, README balance clarification |
| Budgets met, including resort generation 150 ms | `liftManagerBudget`, the kinetic budget test, the worldgen tour timing, the smoke renderer timings, a spark check by hand |
| Server guide | `docs/server-guide.md`, reviewed against the code |
| Release pipeline | WP8 dry run: correct jars and metadata for all three targets |
| Compatibility matrix | `docs/playtest-phase9.md`: every CI row green; hand rows written up |

## 6. Test inventory (what "done" means for phase 9)

| Layer | Tests |
|---|---|
| Unit | rpm-to-speed mapping, the `config_sync` round trip with the lift keys, the generated-data completeness check, run-planner output unchanged by the optimization |
| Game tests, without Create (existing column) | everything from phases 1 to 8, plus the config sync test |
| Game tests, with Create (new column, both loaders) | the bridge is live, the kinetic drive runs the line, lost rotation is an emergency stop, overstress stops it, speed follows rpm, the drive survives a reload, the wrench rules, wrench breaking faults the line, the kinetic budget |
| Client smoke runs | unchanged scenarios stay green, with the cable budget enforced once it's set |
| Worldgen tour | resort start time measured against 150 ms |
| Release | the dry-run workflow |
| Manual (`docs/playtest-phase9.md`) | the compatibility matrix rows marked by hand, the open playtest items from earlier phases (run at release time) |
