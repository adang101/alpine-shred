# Phase 7 hand check: world generation

Phase 7's exit gate is "1000 generated structures across test seeds with no crashes, all within
bounds, plus visual review". The automated runs cover the first part: a game test generates the
four structures over real overworld terrain for three seeds and checks every piece's bounds, and
the worldgen smoke run builds the nearest of each in a default world, counts what was built and
takes a picture of it (`client-worldgen/screenshots` on the `ci-logs` branch). This page is the
visual review: about 30 minutes alone, in a new world.

## Set up

A new creative world, default world type, structures on, any seed (try the CI seed `20261029` and
one of your own). Then:

```text
/gamerule doDaylightCycle false
/time set noon
/locate structure alpineshred:ski_resort
/locate structure alpineshred:terrain_park
/locate structure alpineshred:groomed_run
/locate structure alpineshred:snow_feature
```

Click a result to teleport, then fly up 40 blocks and look down before flying in. `/locate biome
minecraft:snowy_slopes` first if none are found: they only generate in snowy biomes.

## Look at each

**Groomed run** (also the run of each resort)

1. It follows the fall line from a high point and ends on a flat run-out. Ride it top to bottom.
2. The surface is groomed snow over snow, never more than 2 blocks above or below the ground
   next to it; no floating ledges or holes wider than a block.
3. Trees, plants and snow layers are gone above it; the edges fade into snow layers over about 3
   blocks.
4. Slope markers stand on both edges about every 24 blocks, green, blue or black, matching how
   steep it is.
5. Where the run crosses a chunk border, nothing is cut off or doubled.
6. Wait for snowfall (`/weather thunder`): snow does not pile up on the groomed surface.

**Ski resort**

1. The lodge stands past the end of the run, its double door facing up the run, its floor about
   level with the run-out. Nothing of the hill pokes through the floor or walls.
2. The foundation reaches the ground under every wall (cobblestone below the stone bricks).
3. Inside: fireplace, four tables, lanterns, a Paint Bench, and one chest; open it: wax and
   honeycomb usually, sometimes mitts or gear.
4. The lift line comes in phase 8; for now there is no lift.

**Terrain park**

1. Entry gate with two flags, then four to six lines downhill: flat, kinked and curved rails,
   boxes, a kicker with a table and landing, a pipe pair, a jib line, ending in a bench or flags.
2. Each piece starts a block lower than the last; the snow steps down smoothly between them.
3. Rails connect inside each piece (no gaps or end caps mid-rail); ride each line once.
4. The ground around it is filled in or cut away, so no piece hangs in the air or is buried.
5. Walking in gives the advancement **Park Rat**.

**Snow features**: kickers, rollers and cornices made of snow on open snow, small (under 20
blocks), sitting on the ground rather than floating.

## Note

- Seeds and coordinates of anything that looks wrong, with a screenshot (F2).
- Time to load new chunks with and without the mod while flying fast over snowy mountains
  (the budget is 50 ms per structure and 150 ms for a resort; the game test prints the measured
  times in its log line `[worldgen test]`).
- Turn each `worldgen.*` switch off in `config/alpineshred-common.json`, start a new world: that
  structure no longer appears.
