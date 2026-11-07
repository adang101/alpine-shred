# Phase 8 hand check: ski lifts

> **Moved:** the open items on this page are tracked in
> [playtest-phase9.md](playtest-phase9.md#3-the-open-playtest-list), the one place to tick them off. This page keeps
> the procedure and the questions.

Phase 8's exit gate is "three players ride a 400-block lift, and the ride survives a save and reload;
faulted-line handling works; the performance budgets are met". The automated runs cover most of it:

- the game tests (both loaders) cover the line's saved data, a rider's relog, a save and reload with a rider
  aboard (`liftSurvivesSaveReload`), the fault paths, boarding and dismounting, and the manager's budget
  (`liftManagerBudget`);
- the `lift` client smoke rides a line and times the renderers;
- the multiplayer smoke (CI step 9) puts three clients on a lift of over 400 blocks, compares the chairs
  they see with the server's, logs one rider out mid-ride and back in, and breaks a head near the top.

This page is what CI cannot do: ears, eyes, other mods, other servers, real networks and a real restart. About
90 minutes with three players. The smoke runs run with the sound at 0, on software rendering, on a flat world.

## Set up

A dedicated server with the mod (Forge or Fabric), three players in creative. Each player:

```text
/give @s alpineshred:lift_linker
/give @s alpineshred:lift_tower_pole 32
/give @s alpineshred:lift_tower_head 12
/give @s alpineshred:lift_drive_station
/give @s alpineshred:lift_return_station
/give @s alpineshred:lift_stop_button
/give @s alpineshred:snowboard
/give @s alpineshred:snowboard_boots
/give @s alpineshred:goggles
/give @s alpineshred:mitts
/give @s alpineshred:lift_pass
```

Any snowy slope, or a generated resort (`/locate structure alpineshred:ski_resort`): its lift is already built.
Check `/alpineshred lift list` finds it.

## 1. Three people on a 400-block lift (15 minutes)

Build a line with a loop over 400 blocks (about 200 blocks long, a drive at the top, four or five heads, a
return station at the bottom) and power the drive with a redstone block or lever.

1. All three strap in (G) in the boarding stretch at the bottom and board by walking up to the next chair, or
   right-click a chair. Two can share a chair.
2. The bar closes about a second after boarding; right-click it open and shut.
3. Ride to the top. At the end the chair opens its bar, you are put down on the platform and glide off.
   `Lift Rides` goes up by one and **Bluebird Day** is granted (a ride of 100 blocks or more).
4. **Logout mid-ride.** On the next ride one rider logs out and in again about half way up. They must be on the
   same chair, not in the air. (Try this once while sharing a chair with another player: it is the one case
   the automated runs do not cover, and vanilla may refuse to put the player back on a chair another player
   still holds. Write down what happens.)
5. **Break a head.** On the next ride, once riders are in the upper half, one of you breaks a head (any
   pickaxe, or `/setblock ~ ~ ~ air`). The line must stop in about a second and a half, and everyone on it
   must be able to sneak off, however high they are. Relink it with the linker and run it again.

Write down anything that looked wrong: chairs that jump when you look at them, riders sitting off the seat,
lag on the line.

## 2. A real save and reload, with riders

The CI cannot restart its dedicated server in the middle of a run. Do it here:

1. On the dedicated server, with all three on a running line half way up: `/save-all`, then `/stop`.
2. Start the server again on the same world. The three reconnect.
3. Each rider must be back on a chair of the line, still aboard (not on the ground), and the line must be
   moving. `/alpineshred lift info <id>` shows the line, its speed and its riders.
4. Ride on to the top, and make sure nobody is left with a stuck "strapped" state.

## 3. Latency and loss

Repeat section 1 with 200 ms of latency and 2% packet loss on the clients: clumsy on Windows, or on Linux
`tc qdisc add dev <iface> root netem delay 200ms loss 2%`. Look for:

- chairs jumping or lagging behind the cable on the client (the client steps its own copy of the line from a
  sync every 100 ticks);
- auto-boarding failing to board (the server accepts a chair 1.5 blocks outside the client's reach for lag);
- sneaking off a faulted line being refused on the server.

## 4. Other mods and servers

- **Sodium or Embeddium**, then **Iris or Oculus** with a shader pack: cable, sheaves, bullwheels and chairs
  must draw, at the right place, from the side, from the top and seated. Look for a cable that is cut off at
  the chunk border and chairs that vanish when you turn.
- **A NeoForge 1.20.1 server** (the Forge jar): run section 1 with Forge clients.
- Fabric as well as Forge for the dedicated server, with the same clients.

## 5. Building by hand in survival

In a survival world, with the recipes and no creative items, build a lift from scratch:

1. Craft the linker, poles, heads, drive and return station. The recipes are provisional (phase 9 does the
   balance pass); say whether the cost is wildly off.
2. Place the stations (the placement warns if solid blocks are within 2 blocks of the core), the towers, then
   link them. Every refusal names its reason in the action bar; check each is understandable (too far, too
   steep, cable blocked).
3. **Chair clearance.** The linker checks that the cable clears terrain by a block, but not that the chairs
   hanging 1.6 blocks below it do. Make a line over a hill and look at the chairs: if they pass through the
   ground on a line the linker accepted, add a chair-clearance sample to `LinkValidator`.
4. Run it, ride it.

## 6. By ear and by eye

The CI runs with the volume at 0, so none of these sounds has been heard:

- **Safety bar** closing and opening (`lift.bar_close`, `lift.bar_open`): heard when it happens, not too loud,
  from your own chair and from a chair beside you.
- **Emergency stop** (`lift.e_stop`): press the stop button, and break a head: a clear sound on both.
- **Cable creak** (`lift.cable_creak`): it should come at random intervals from the towers, more when the line
  is fast, and not sound like a loop.
- **Motor** (`lift.motor_loop`): positional at the drive, rising with speed and fading as the line slows.

And the picture:

- **The safety bar's height against the rider.** In the placeholder model it looks chest-high. Look at it from
  the front and the side, third person, with a board and with skis: it should sit across the lap or the
  upper thighs. If it is wrong, change the chair model's bar (`ChairEntityRenderer`) and the art script.
- The seat height: hips on the plank, feet hanging, for a player and for a cow or a wolf.
- The seated clips for a board and for skis; the mount and dismount clips.

## 7. Performance (spark)

CI measures the manager and the renderers on shared hardware and fails only at three times the budget. With
spark (`/spark profiler`, 60 seconds) on a server with five lines of fifty chairs each, and then on a client
looking along a line, check the budgets of design doc section 22: the lift manager under 0.3 ms a tick for
five lines, a chair under 5 microseconds a tick, and the cable's share of a frame. The cable's figure in the
smoke is provisional (0.5 ms) until the doc's is confirmed. Write the real numbers in the README clarification
on the budgets.

## 8. Carried forward

- **Trees growing into a generated cable.** Fly along a generated resort's line after a few in-game days of
  growth (`/gamerule randomTickSpeed 300` helps in a forest biome). If a tree or leaves reach the cable or the
  chairs, decide whether generated lifts need a clearing pass.
- **Server config the client does not know.** `lift.maxBoardSpeed` and `lift.dismountAnywhere` are not in
  `config_sync`: change either on the server and check what the client's board prompt and sneak-off say
  against what the server does (the server always decides). Phase 9 adds them to the sync with a protocol bump.
- **Resort generation time.** It was about 200 ms a start in CI against the 150 ms budget (about 190 ms in
  phase 7, before lifts). Time `/locate`-ing and flying into resorts with a profiler and see whether the lift
  planner shows at all; the cost is the run planner's noise sampling, which goes to phase 9's optimization pass.

## Note

- Coordinates and the log of anything wrong, with a screenshot (F2).
- The `/alpineshred lift info` output of a line that misbehaves, and the server's log around it.
