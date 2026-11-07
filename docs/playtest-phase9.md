# Phase 9: the compatibility matrix and the open playtest list

This is the one page for everything that CI cannot do before the release. It has three parts:

1. **The matrix** (section 1): which loader, mod and server combinations are covered by which CI step, and which are
   checked by hand.
2. **The hand rows** (section 2): steps and expected results for each combination that CI does not cover.
3. **The open playtest list** (section 3): every item still open from phases 3, 4, 6, 7 and 8, plus the two items
   phase 9's work packages raised. The older playtest pages keep their procedures; their open items are tracked
   here (each of those pages now starts with a "Moved" note).

The release follows the plan in [phase9-plan.md](phase9-plan.md), "After phase 9": run the hand rows and the open
list, tag `v1.0.0-rc1`, soak it for two weeks, then publish. Tick a box here when it passes, with the date and your
name. A failed box becomes a blocker issue; a fix restarts the soak clock.

## 1. The matrix

CI means a job and step of `.github/workflows/build.yml` (the file's step names are quoted exactly). There are two
jobs: `build` (no Create, 120-minute timeout) and `create` (`-PwithCreate`, Create on the dev runtime, 60-minute
timeout). "Hand" rows are written up in section 2.

| # | Combination | Covered by | How |
|---|---|---|---|
| M1 | Forge, without Create | CI: job `build`, step "Forge game tests (headless game-test server)"; the Forge client runs in steps "Worldgen tour", "Client smoke test", "Mountain ride", "Park ride", "Lift ride" | Every game test from phases 1 to 8 on Forge, and the real Forge client on a virtual display |
| M2 | Fabric, without Create | CI: job `build`, step "Fabric game tests (headless dedicated server)" (and "Compile all modules, run unit tests, build jars" for the unit tests) | Every game test on a headless Fabric dedicated server. No Fabric client runs in CI (the dev client does not start: README "Build and test", Trinkets 3.7.2), see H5 |
| M3 | Forge, with Create | CI: job `create`, step "Forge game tests with Create (headless game-test server)" | Create, Flywheel, Ponder and Registrate on the runtime; the Create bridge, the kinetic drive and the wrench rules run as game tests |
| M4 | Fabric, with Create | CI: job `create`, step "Fabric game tests with Create (headless dedicated server)", judged by `gametest-junit-check.py` (the same step) | As M3. Create's own game tests also run and may fail Gradle's exit code; the check judges only ours (README 77) |
| M5 | Curios (Forge): the snow gear, goggles and mitts slots | CI: game test `accessorySlotsExist` (`AlpineGameTests`, run by `ForgeGameTests`) in M1's step and in M3's. Curios is `modRuntimeOnly` in `forge/build.gradle`, so it is on the classpath in both columns. Design sync through the slot: step "Multiplayer (Forge dedicated server, three clients)", where the rider's board goes into the Curios slot through `GearAccess` and both watchers must receive its design | The slot exists, accepts a board, goggles and mitts, and clears; watchers see the designed board |
| M6 | Trinkets (Fabric): the same slots | CI, **slots only**: game test `accessorySlotsExist` (`FabricGameTests`) in M2's step and in M4's. Trinkets is `modImplementation` in `fabric/build.gradle`. **Design sync through Trinkets is not in CI** (no Fabric client or Fabric multiplayer run), so that part is hand row H6 | The slot exists, accepts a board, goggles and mitts, and clears |
| M7 | A dedicated server with three clients, without Create | CI: job `build`, step "Multiplayer (Forge dedicated server, three clients)" (`.github/scripts/multiplayer-smoke.sh` and `multiplayer-check.py`: tracks, states and animations, the 400-block lift, a relog mid-ride, a broken head, chairs within 0.25 blocks of the server's, 5% sample tolerance) | Forge only |
| M8 | A dedicated server with three clients, with Create | **Hand (H1).** Not added to CI, see "Why the multiplayer smoke is not in the `create` job" below | |
| M9 | The Forge jar on a NeoForge 1.20.1 server | Hand (H2) | |
| M10 | Sodium or Embeddium, and Iris or Oculus, with our renderers | Hand (H3, H4) | |
| M11 | A survival playthrough from new gear to a working kinetic lift | Hand (H7) | |
| M12 | Fabric client in real play (rides, lift, wrench) | Hand (H5, H6) | |

Count: **7 rows are covered by CI** (M1 to M7; M6 only in part), **5 rows are by hand** (M8 to M12), which are
written as the ten hand rows H1 to H10 below.

### Why the multiplayer smoke is not in the `create` job

It was checked, and left out. The step is the longest in the whole run: on the last full run (run 42376722696) it
took 28.6 minutes (13:46:41 to 14:15:18) for one server and three Forge clients, each its own Gradle run. The whole
`create` job took 12 minutes (both game-test steps about 6 minutes each). Adding the step would add about 30
minutes, plus the one minute to install Xvfb and Mesa, to a job that finishes in 12: about three times the "cheap"
limit of 10 extra minutes. It would fit inside the `create` job's 60-minute timeout (about 43 minutes in all), but
the plan asks for the cheap gaps only, and the margin would shrink every time a client is slow to start (the script
already has a watchdog and a relaunch for that). It also would put Create's Flywheel on Mesa software rendering for
the first time, which is a risk of its own. So M8 stays a hand row. The kinetic drive and the wrench rules are
covered on both loaders by the game tests in M3 and M4, and the synced state (`config_sync` protocol 2, the line
sync) does not depend on Create. No README clarification is added for this (92 is not used).

## 2. The hand rows

Set up once: build the jars with `./gradlew build` after `.github/scripts/unpack-create-nested.sh` (Fabric needs
Create's nested jars to compile), or take them from the CI run's `jars` artifact. Versions are in the README's
"Pinned versions" table. Give each tester the gear commands from [playtest-phase8.md](playtest-phase8.md#set-up) and
[playtest-phase3.md](playtest-phase3.md#set-up).

### H1. Dedicated server with three clients, with Create (M8)

Forge server with Create 6.0.8 (and its Flywheel, Ponder, Registrate) and three Forge clients with the same mods.
Repeat it on Fabric (Create Fabric 6.0.8.1, Fabric Loader 0.17.2 or later) with three Fabric clients.

- [ ] The server starts and the three join; no mod-mismatch error and no `config_sync` warnings in either log.
- [ ] One player rides a snowboard down a slope; the other two see the board, the lean and the trick animations
      move with the rider (the same check the CI watchers make).
- [ ] Build a lift of over 400 blocks (the steps in [playtest-phase8.md](playtest-phase8.md#1-three-people-on-a-400-block-lift-15-minutes))
      and power it with redstone. All three ride to the top; chairs look the same on every client.
- [ ] Place a `kinetic_lift_drive` beside the drive station and power it with a Create water wheel or a motor at
      64 rpm: the line runs at 1 block per second; at 256 rpm at 4 blocks per second (the cap). Redstone is ignored while
      it is attached.
- [ ] Stop the rotation while riders are aboard: the line makes an emergency stop (the same sound and slowdown
      as breaking a head) and riders can sneak off.
- [ ] A client without Create connects to the server with Create. Expected: it joins (the kinetic drive is the only
      Create-only block, and its item will show as missing only if the player is given it).

### H2. The Forge jar on a NeoForge 1.20.1 server (M9)

A NeoForge 1.20.1 server (write the build down), the Forge jar, Architectury API (Forge), PlayerAnimator (Forge), Curios. Forge clients.

- [ ] The server starts with no mod-loading error (watch for `mods.toml` or Architectury messages).
- [ ] The three clients join. `/alpineshred debug ride` works.
- [ ] Ride a slope and a lift (section 1 of the phase 8 page); watchers see the rider.
- [ ] `/save-all`, `/stop`, restart: lifts and riders restore (the open item O12 below).
- [ ] Optional, with Create on the NeoForge server: the kinetic drive and the wrench work. Create's Forge build is
      what we compile against; if it does not load on NeoForge, write the versions down and say so in the server guide.

### H3. Sodium or Embeddium (M10)

Embeddium (Forge) and Sodium (Fabric), with our renderers: the cable, sheaves, bullwheels, chairs, paint-bench
preview and the snow spray.

- [ ] Cable, sheaves, bullwheels and chairs draw at the right place from the side, the top and seated.
- [ ] No cable cut off at a chunk border; no chairs that vanish when you turn.
- [ ] Park pieces (rails, boxes, ramps, pipes) and groomed snow look the same as without the mod.
- [ ] Worn gear (board on the back, goggles, mitts) draws on you and on other players; custom designs show.
- [ ] The log has no renderer errors.

### H4. Iris or Oculus with a shader pack (M10)

Iris (Fabric) with Sodium, and Oculus (Forge) with Embeddium; one popular shader pack.

- [ ] The same list as H3, with the shader on.
- [ ] Chair and cable shading is sane (no black or fully lit cable, no missing shadows at the towers).
- [ ] Snow spray and sparks are visible. Designed boards are not blank.

### H5. Fabric client in real play, and the wrench pickup (M12; raised by WP4)

Fabric has no client run in CI. Create (Fabric) and the Create wrench, a creative flat world on a dedicated Fabric
server (so the pickup has to reach a real server).

- [ ] The Fabric client starts and a board rides on snow: strap in (G), steer, jump.
- [ ] A lift: link it, ride it, get put down at the top.
- [ ] **Wrench pickup reaches the server.** With Create's wrench in hand, sneak-right-click a pole, a head, a
      station and the kinetic drive: each is picked up (an item drops into your inventory), the line faults as it does when you break the block by hand, and the server log shows no error. Break the block, relog, and check it is still gone (the pickup is a server action, not just a client one).
- [ ] A plain right-click with the wrench on a lift block shows the "use the lift linker" hint on the action bar and changes nothing.
- [ ] On a park piece, a plain wrench click cycles its shape and a sneak-click picks it up (the same as the park wrench).
- [ ] Repeat the pickup on Forge once.

### H6. Trinkets design sync on Fabric (M6, the part CI cannot run)

Two Fabric clients on a Fabric server, Trinkets 3.7.2.

- [ ] Paint a design on a board and save it ([playtest-phase6.md](playtest-phase6.md)). Put it in the snow gear slot
      (Trinkets "feet" group).
- [ ] The other player sees the designed board on the rider's back and when strapped in, on the deck, base and edges.
- [ ] Take it out, put it in a chest, and take it back: the design is still there.
- [ ] Goggles (Trinkets face) and mitts (hand glove) show on the other player.

### H7. Survival playthrough, from new gear to a working kinetic lift (M11)

A new survival world on Forge with Create, then the same on Fabric with Create. Use only the recipes. The
recipes were balanced in WP5 (README 83 to 85), so also judge the cost.

- [ ] Craft the starter gear (board, boots, goggles, mitts) in the first few minutes of play, from wood, string,
      leather, iron or similar; nothing needs end-game items.
- [ ] Find snow and ride. Craft wax and the park wrench.
- [ ] Craft the lift: the linker, poles, heads, the drive station and the return station. Note any recipe that
      needs a material you do not have by the mid-game, or that is wildly cheap or dear.
- [ ] Build a Create water wheel (or a windmill, or a motor on a creative source if the world has none), place the
      `kinetic_lift_drive` beside the drive station, and link the line.
- [ ] Every placement or link refusal names its reason in the action bar and the reason is understandable (too far, too steep, cable blocked, solid blocks within 2 blocks of a station core).
- [ ] The line runs from the kinetic drive; the tooltip shows the stress it needs (1024 su at full speed, 4 su per rpm); goggles show it in the world. Overstress it with a weak wheel: the line stops.
- [ ] Ride it from the bottom to the top and ski down.
- [ ] Time from a new world to the first ride, and to the first working kinetic lift: write them down.

### H8. Create wrench rules on Forge in real play

- [ ] The same list as H5's wrench steps, on a Forge client. Create's own blocks still wrench normally beside ours.

### H9. Release metadata (raised by WP8)

Before the first real publish, not before the release candidate.

- [ ] Check the `mc-publish` dependency slugs in `.github/workflows/release.yml` (`fabric-api`,
      `architectury-api`, `trinkets`, `playeranimator`, `curios`, `create`, `create-fabric`) against the Modrinth and
      CurseForge project pages, since they were written from memory (README 89).
- [ ] The dry run (`workflow_dispatch` on `release.yml`) still builds both jars with the right metadata on the
      release candidate commit.

### H10. Spark check of the budgets

With [spark](https://spark.lucko.me/): a server with five lines of fifty chairs, then a client looking along a line.

- [ ] The lift manager is under 0.3 ms a tick for five lines, and a chair under 5 microseconds a tick.
- [ ] The cable's share of a frame is under 0.5 ms (README 69).
- [ ] With a kinetic drive attached, its tick is under 0.02 ms.
- [ ] A resort start (`/locate structure alpineshred:ski_resort` then flying in) is under 150 ms in the profiler.
- [ ] Write the real numbers in a README clarification.

## 3. The open playtest list

One list for every item that is still open. Each links to the page and the section where its procedure is. When one
passes, tick it here only; the old pages are not ticked any more. Items that phase 9 has already settled in code are
listed at the end, so nobody repeats them.

### From phase 3 ([playtest-phase3.md](playtest-phase3.md))

- [ ] **O1. Five outside testers each ride for 10 minutes** and answer the six questions
      ([the 10 minutes](playtest-phase3.md#the-10-minutes), [questions](playtest-phase3.md#questions-afterwards)). This
      is the phase 3 exit gate. Expected: at the end, most want to keep riding; no comfort problem that the settings do not fix.

### From phase 4 ([playtest-phase4.md](playtest-phase4.md))

- [ ] **O2. Build the park by hand** (rail line, wrench, wax and copper, boxes, ramps and pipes), 5 minutes
      ([Build](playtest-phase4.md#build-5-minutes)). Expected: every step as written there.
- [ ] **O3. Ride the park** (rails, switch-up, boxes, fence line, lip ramp, quarter pipe, a second player watching),
      10 minutes ([Ride](playtest-phase4.md#ride-10-minutes)). Expected: no snags, no server violations in
      `/alpineshred debug ride`.

### From phase 6 ([playtest-phase6.md](playtest-phase6.md))

- [ ] **O4. Five players each paint and save a design in under 10 minutes** ([Paint](playtest-phase6.md#paint-10-minutes-timed)).
      Write down each time.
- [ ] **O5. Twenty designs on three clients** ([Look](playtest-phase6.md#look-5-minutes)): own designs on deck, base and
      edges, in hand, on the ground; tooltips; share codes; no stutter with 20 around; `client.showCustomDesigns` off works.
- [ ] **O6. Nothing is lost** ([Note](playtest-phase6.md#note)): after a restart, smithing a park board to a pro one,
      and a chest. Designs worn in Curios (Forge) are seen by others (also covered in CI for Forge); Trinkets is H6 above.

### From phase 7 ([playtest-phase7.md](playtest-phase7.md))

- [ ] **O7. The visual review of the four structures** in a new world, with the CI seed `20261029` and your own
      ([Look at each](playtest-phase7.md#look-at-each)). Groomed run, ski resort (now with its lift, see O10), terrain
      park, snow features.
- [ ] **O8. Load time while flying fast over snowy mountains**, with and without the mod, and the `worldgen.*` switches
      ([Note](playtest-phase7.md#note)).

### From phase 8 ([playtest-phase8.md](playtest-phase8.md))

- [ ] **O9. Three people on a 400-block lift** (boarding, the bar, the ride, a logout mid-ride on a shared chair, a
      broken head) ([section 1](playtest-phase8.md#1-three-people-on-a-400-block-lift-15-minutes)). Write down what
      happens when a rider relogs while sharing a chair.
- [ ] **O10. The generated-lift clearing pass.** Fly along a generated resort's lift after a few in-game days of
      growth (`/gamerule randomTickSpeed 300` in a forest biome). If a tree or leaves reach the cable or the chairs,
      decide whether generated lifts need a clearing pass ([section 8](playtest-phase8.md#8-carried-forward), first
      bullet). Expected: a decision, written as a README clarification, either way.
- [ ] **O11. Chair clearance.** Build a line over a hill and look at the chairs, which hang 1.6 blocks below the
      cable: if they pass through the ground on a line the linker accepted, add a chair-clearance sample to
      `LinkValidator` ([section 5](playtest-phase8.md#5-building-by-hand-in-survival), item 3). Fold it into H7.
- [ ] **O12. The real server restart with riders.** On a dedicated server with three riders half way up,
      `/save-all`, `/stop`, start again, reconnect: each is back on a chair, the line is moving, nobody is stuck
      "strapped" ([section 2](playtest-phase8.md#2-a-real-save-and-reload-with-riders)). Do it on Forge and on Fabric.
- [ ] **O13. Latency and loss**: section 1 again with 200 ms and 2% loss
      ([section 3](playtest-phase8.md#3-latency-and-loss)).
- [ ] **O14. Render mods**: Sodium or Embeddium, then Iris or Oculus ([section 4](playtest-phase8.md#4-other-mods-and-servers)).
      Same as H3 and H4 above; tick both places or only one, but run it once.
- [ ] **O15. A NeoForge 1.20.1 server** with the Forge jar ([section 4](playtest-phase8.md#4-other-mods-and-servers)).
      Same as H2.
- [ ] **O16. Fabric as well as Forge for the dedicated server** with the same clients (section 4, last bullet). Part of H1.
- [ ] **O17. By ear and by eye** ([section 6](playtest-phase8.md#6-by-ear-and-by-eye)): the bar, emergency stop, cable
      creak and motor sounds; the bar's height against the rider; the seat height for a player, a cow and a wolf; the seated,
      mount and dismount clips.
- [ ] **O18. Spark** ([section 7](playtest-phase8.md#7-performance-spark)): the same as H10.
- [ ] **O19. Survival build by hand** ([section 5](playtest-phase8.md#5-building-by-hand-in-survival)): the same as
      H7. The recipes are no longer provisional.

### Raised by phase 9's work packages

- [ ] **O20. A Fabric wrench pickup reaching the server in real play** (WP4): H5.
- [ ] **O21. The `mc-publish` dependency slugs** (WP8): H9.

### Settled by phase 9, so no longer open

Listed so nobody repeats them:

- *Server config the client does not know* (phase 8 section 8, second bullet): `lift.maxBoardSpeed`,
  `lift.dismountAnywhere` and `lift.seats` are in `config_sync` (protocol 2; README 74 and 76). A quick look is left in H1: change a key on the server and check
  that the board prompt agrees.
- *Resort generation time* (section 8, third bullet): about 133 to 138 ms a start in CI after WP6 (README 86). The
  profiler check is part of H10.
- *Provisional lift recipes* (section 5, item 1): balanced in WP5 (README 83 to 85). Judging the cost is part of H7.
