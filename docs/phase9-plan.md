# Phase 9 plan: Create compatibility and polish

This is the work plan for phase 9, the last phase. The design document is the source of truth. Section 14 covers
the kinetic drive, section 19 the compatibility matrix and budgets, and section 20 the roadmap. When this plan and
the doc disagree, the doc wins, unless the plan records a clarification (see "Decisions"). Every clarification
also goes into the README under "Design clarifications made while building". The README's "Phases" table is the
project-wide overview. Update its phase 9 row when this phase merges.

**Exit gate** (section 20):
- Section 19's compatibility matrix passes.
- A release candidate runs for two weeks with no open blockers.

**Deliverables** (section 20):
- the kinetic drive (`KineticLiftDriveBE implements LiftDriveSource`; section 14),
- wrench support,
- tags,
- recipes and the balance pass,
- the server guide,
- optimization,
- the release pipeline.

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
- It asks the user before merging into `main`, and whenever the design doc isn't at hand for a decision.
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
- **Comments.** Write a javadoc for each public class and each non-obvious method, giving the doc section and the
  reason. Don't narrate what the code does.

### 1c. Game performance (reviewed on every WP)

- **No allocations in per-tick or per-frame code.** That covers server ticks, block entity ticks, renderers and
  client tick handlers: no collections, boxed numbers, streams, lambdas that capture, or string formatting there.
  Reuse buffers. A hot path with `new` in it needs a reason in the handback.
- **Cache whatever is derived.** Rebuild it only when its input changes. `LiftPath` and the station intervals are
  the model.
- **Budgets are asserted in tests.** Measure every new per-tick system the way `liftManagerBudget` does, at three
  times the doc's figure (README clarification 71), and report the measured number in the handback.
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
Deviations:   <from this plan or the doc, each with its reason>  (or "none")
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

1. **Correct against the doc and this plan.** Each deliverable of the WP is there, and each deviation is
   justified and recorded as a README clarification.
2. **Tests prove it.** The new behaviour has a unit or game test that would fail without it. No test was
   weakened, skipped or retimed to pass. CI is green on the handback sha (`RUN.txt`).
3. **Reuse.** Nothing duplicates an existing helper (section 1b). New helpers are general enough for the next WP.
4. **Performance.**
   - There are no allocations in hot paths.
   - Budgets are asserted and the measured numbers reported.
   - Without Create, nothing extra runs (section 1c).
5. **Isolation.** No Create import outside `compat.create`. The mod still loads without Create; the existing CI
   runs are the "without Create" column.
6. **Determinism and data.** Ordering is fixed where it matters, and generated data is regenerated and committed.
7. **Scope.** There are no unrelated changes, no debug leftovers, and no probe files.

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
2. **The kinetic drive is a separate block.** `kinetic_lift_drive` sits beside a drive station and has a
   `KineticLiftDriveBE` that implements `LiftDriveSource`. It is registered only when Create is present, and its
   recipe is conditional on Create. The drive station's behaviour without it is unchanged.
   - Its rpm maps to a target speed in a pure class, clamped to `lift.kineticMaxSpeed` (4.0 by default).
   - Losing rotation while running is an emergency stop, as losing redstone is (README "What phase 8 delivers").
   - Its stress impact comes from the doc. If section 14 isn't at hand, Opus asks the user before WP3 starts.
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

Open, for the user (Opus asks before the WP that needs it):
- Section 14 (kinetic drive: rpm range, stress impact) and section 19 (the compatibility matrix rows) aren't in
  the repo. **Please commit the design doc, or paste those sections.**
- `CABLE_BUDGET_MS` is a provisional 0.5 ms in `ClientSmoke` (README clarification 69). It needs the doc's
  figure.
- Release targets (WP8): Modrinth, CurseForge or GitHub Releases only, and the tokens they need. Tokens go in
  repository secrets, never in the repo or the chat.

## 4. Work packages

Dependencies: WP1 runs first and on its own. WP2 needs WP1. WP3 and WP4 need WP2 and can run in parallel. WP5
needs WP3, so the kinetic drive's recipe gets balanced. WP6 can start after WP1. WP7 needs WP3 to WP6. WP8 can
start after WP1. WP9 closes the phase.

Since only one branch runs CI at a time (section 1a), "in parallel" means the work overlaps but the pushes take
turns. Opus schedules them.

### WP1: Carry-overs from phase 8 (small)

- Add `lift.maxBoardSpeed` and `lift.dismountAnywhere` to `config_sync` and bump the protocol (decision 3). The
  client's board prompt and dismount prediction read the synced values.
- Add `.github/scripts/__pycache__/` to `.gitignore`.
- Tests: a `SyncCodec` round trip including the two keys (unit), and a game test that a changed server value
  reaches a fake client's view.
- Done when: CI is green, and README clarification "config_sync carries the lift keys" is added.

### WP2: Create groundwork

- Add the Gradle dependencies (decision 1), the `compat.create` packages, `CreateBridge` and its two loader
  implementations, registered behind `isCreatePresent`.
- Add the CI column with Create (decision 4), with one trivial game test that proves Create loaded and the
  bridge is live on both loaders.
- Probe the Create API first (section 1a). Write down the classes the later WPs will use: the kinetic block
  entity base, speed and stress, wrench hooks and tags.
- Done when: both columns are green, the mod loads without Create (the existing runs), and the probe results are
  in the handback for WP3 and WP4.

### WP3: The kinetic drive (section 14)

- `kinetic_lift_drive` and `KineticLiftDriveBE implements LiftDriveSource`, in the loaders' `compat.create`
  packages on Create's kinetic base. The pure rpm-to-speed mapping lives in common.
- The drive station takes its `LiftDriveSource` from an adjacent kinetic drive when there is one, and otherwise
  from redstone. Which one wins when both are present follows the doc, or a clarification if it is silent.
- Ramping and emergency stops reuse `LiftMotion`. There is no new motion code.
- Datagen: the block, its item, lang, a conditional recipe, loot, and the `create:` tags it needs. Placeholder
  art goes in a script.
- Tests:
  - unit: rpm to speed, the clamp, direction;
  - game tests (Create column): rotation drives the line; losing rotation is an emergency stop; overstress stops
    it; the speed follows rpm within the ramp; the drive survives a save and reload;
  - a budget test: the kinetic drive adds less than 3× the doc's figure per line per tick.
- Done when: both columns are green, the `lift` smoke is unchanged, and the measured numbers are reported.

### WP4: Wrench support

- Create's wrench on our blocks: what rotates, what is picked up, and what is refused, per the doc. Lift nodes
  are already in `create:non_breakable`, so contraptions leave them alone. Breaking a node by wrench must fault
  the line exactly as breaking it by hand does.
- Use tags and Create's events or registries from `compat.create`, never an interface on our block classes
  (decision 1).
- Check that the park wrench and the lift linker still work as before, and don't add Create behaviour to them
  unless the doc asks for it.
- Tests: game tests in the Create column for each rule above, and one test that the faulting path is shared.

### WP5: Tags, recipes and the balance pass

- **Tags:** common tags (`c:` on Fabric, `forge:` on Forge) for our materials and tools where the doc lists them,
  plus `lift_blocked` and `lift_passengers` reviewed against vanilla mobs. Everything goes through datagen.
- **Recipes:** retire the "provisional until the phase 9 balance pass" note in `AlpineRecipeProvider`. Settle the
  lift costs, the kinetic drive recipe and the Create-conditional recipes.
- **Balance:** go over the gear stats and costs against the doc's tables, and list every change in one README
  clarification (decision 5).
- Tests: a datagen check that every item has a recipe or a reason, and every block has a loot table and a
  mineable tag (a unit test over the generated JSON, or a game test).

### WP6: Optimization

- **Resort generation:** about 200 ms a start in CI against the 150 ms budget (README clarification 59). The run
  planner's noise sampling dominates. Cache or downsample it, keeping the output identical for a seed (assert
  that in a test), and measure the result in the worldgen tour.
- **Hot-path audit:** go over every per-tick and per-frame path added in phases 5 to 8 (`LiftManager`,
  `LiftChairs`, `ClientLiftLines`, the lift renderers, `RiderTracker`) for allocations and repeated work (section
  1c). Fix each one with its before and after numbers.
- **Cable budget:** set `CABLE_BUDGET_MS` once the user gives the doc's figure, and make the renderer meet it.
- Don't change behaviour: every smoke and game test stays green with unchanged results.

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

### WP8: The release pipeline

- A workflow triggered by a `v*` tag:
  - build both jars with the version from the tag;
  - attach them and a changelog to a GitHub release;
  - publish to the targets the user picks (section 3, "Open"), with tokens from repository secrets;
  - support a dry-run that publishes nothing (`workflow_dispatch`).
- Mod metadata: the version, the loader and Minecraft ranges, Create as an optional dependency on both loaders,
  and the Forge jar marked for NeoForge too.
- A `CHANGELOG.md`, built from the phase summaries in the README.
- Done when: a dry run on the WP branch produces both jars with the right metadata. No real publish happens
  until the release candidate, and only with the user's go-ahead.

### WP9: The compatibility matrix and the release candidate

- Turn section 19's matrix into a table in `docs/playtest-phase9.md`. Mark each row as CI (with the step that
  covers it) or by hand. The CI columns are at least: Forge and Fabric, each with and without Create; with
  Curios or Trinkets; and the dedicated server with three clients.
- Fill whatever CI gaps are cheap, such as the multiplayer smoke with Create present if the matrix asks for it.
- Record the playtest items still open from phases 3, 4, 6, 7 and 8 in one list, so the RC soak covers them:
  - the generated-lift clearing pass;
  - the real server restart with riders;
  - render mods;
  - a NeoForge server.
- Tag `v1.0.0-rc1` once the user agrees, and start the two-week soak. Blockers found go to fix WPs, and each fix
  restarts the clock.

## 5. Exit gate mapping

| Gate item | Covered by |
|---|---|
| Kinetic drive works (section 14) | WP3 game tests (Create column), budget test |
| Mod works with and without Create on both loaders | both CI columns (decision 4), WP2's load test |
| Wrench support | WP4 game tests |
| Tags, recipes, balance | WP5 datagen checks, README balance clarification |
| Budgets met, including resort generation 150 ms | `liftManagerBudget`, the kinetic budget test, the worldgen tour timing, the smoke renderer timings, a spark check by hand |
| Server guide | `docs/server-guide.md`, reviewed against the code |
| Release pipeline | WP8 dry run, then the `rc1` release |
| Section 19 compatibility matrix | `docs/playtest-phase9.md`: each row green in CI or checked by hand |
| Two-week RC with no open blockers | the soak log in `docs/playtest-phase9.md` |

## 6. Test inventory (what "done" means for phase 9)

| Layer | Tests |
|---|---|
| Unit | rpm-to-speed mapping, the `config_sync` round trip with the lift keys, the generated-data completeness check, run-planner output unchanged by the optimization |
| Game tests, without Create (existing column) | everything from phases 1 to 8, plus the config sync test |
| Game tests, with Create (new column, both loaders) | the bridge is live, the kinetic drive runs the line, lost rotation is an emergency stop, overstress stops it, speed follows rpm, the drive survives a reload, the wrench rules, wrench breaking faults the line, the kinetic budget |
| Client smoke runs | unchanged scenarios stay green, with the cable budget enforced once it's set |
| Worldgen tour | resort start time measured against 150 ms |
| Release | the dry-run workflow |
| Manual (`docs/playtest-phase9.md`) | the compatibility matrix rows marked by hand, the open playtest items from earlier phases, the RC soak |
