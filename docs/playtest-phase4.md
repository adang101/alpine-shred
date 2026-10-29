# Phase 4 hand check: the park

Phase 4's exit gate is automated (the 10-piece rail chain, box slide and pipe ride game tests run
in CI on both loaders, and a Forge client rides a park line in CI step 8). This page is the short
check by hand that the automated runs cannot see: how building a park feels, and how riding one
looks and sounds. About 15 minutes.

## Set up

Same instance as the phase 3 play-test ([playtest-phase3.md](playtest-phase3.md)), in a creative
flat world (snowy is best: `/fill` some `alpineshred:groomed_snow`). Give the park pieces:

```text
/give @s alpineshred:pipe_rail 32
/give @s alpineshred:flat_rail 16
/give @s alpineshred:copper_pipe_rail 16
/give @s alpineshred:rail_post 8
/give @s alpineshred:wood_box 16
/give @s alpineshred:metal_box 16
/give @s alpineshred:plastic_box 16
/give @s alpineshred:park_ramp 16
/give @s alpineshred:quarter_pipe 4
/give @s alpineshred:pipe_coping 8
/give @s alpineshred:park_wrench
/give @s minecraft:honeycomb 8
/give @s minecraft:iron_axe
```

## Build (5 minutes)

1. **Rail line.** Hold a rail and look at the ground: a see-through piece shows where it will go
   (green when it joins a rail, red when it stands alone). Place 10 or more pieces end to end over
   ground with a one-block step in it. The line should climb the step on its own (two rise pieces),
   show end caps only at its two ends, and stand on short legs. Place one beside the last piece,
   not in line: the end should turn into a curve. Sneak-place forces a straight piece.
2. **Wrench.** Hold the wrench: every rail path nearby is drawn as a line. Right-click a lone rail
   to cycle its shape, sneak-right-click to turn it; in the middle of a line it should say it is
   the only piece that fits.
3. **Wax and copper.** Honeycomb on a copper rail waxes it (particles, and it becomes "Waxed");
   an axe takes the wax off, then scrapes oxidation off a weathered one.
4. **Boxes.** Place boxes in a row (sneak for low boxes): ends bevel where the row stops. Dye a
   plastic box. In creative, the tab has all 16 plastic colors.
5. **Ramps and pipes.** Two ramps in a row make one smooth slope; the wrench changes the slope and
   (sneaking) marks the lip with a red stripe. One quarter pipe item places a whole pipe rising the
   way you face (sneak for radius 5); breaking any block of it removes it all. Put coping along the
   top behind the wall.

## Ride (10 minutes)

1. Glide onto the rail from a run-in at its height, or pop onto it. It should catch with a clank
   and sparks, carry you along the whole line including the rise, and drop you off the end.
2. While grinding, tap **A** or **D** (switch-up: the stance changes), hold **Shift** (press), and
   press **Space** to pop off.
3. Ride up onto a row of low boxes: a slide sound for the material, steering dulled, off the end
   into the air.
4. Ride a fence or wall line (fences and walls grind too).
5. Hit a lip ramp at speed and press **Space** just after leaving it: a little extra height.
6. Ride up a quarter pipe: you should rise and come back down facing the other way; fast enough,
   you leave the top straight up and drop back in.
7. Watch another player do all of the above (two clients on one server), and run
   `/alpineshred debug ride` to watch for server violations (there should be none).

## Note

- Any place a rider snags, sinks into a piece, or is pulled back by the server.
- Any piece that places facing the wrong way, or a line that does not join up.
- Sounds or sparks missing, or playing for the wrong material.
