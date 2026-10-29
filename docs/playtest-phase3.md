# Phase 3 play-test

Phase 3's exit gate: five testers from outside the project each ride for 10 minutes. This page is
the setup and the questions, so every session runs the same way and the answers can be compared.

## Set up

Build the jars with `./gradlew build` (or take them from the CI run's artifacts). Each tester needs
a Minecraft 1.20.1 instance with:

| Forge 47.4.28 (also NeoForge 1.20.1) | Fabric Loader 0.16.14 |
| --- | --- |
| `alpineshred-forge-*.jar` | `alpineshred-fabric-*.jar` |
| Architectury API 9.2.14 (Forge) | Architectury API 9.2.14 (Fabric) |
| PlayerAnimator 1.0.2-rc1+1.20 (Forge) | PlayerAnimator 1.0.2-rc1+1.20 (Fabric) |
| Curios 5.14.1+1.20.1 | Trinkets 3.7.2, Fabric API 0.92.6+1.20.1 or later |

Make a creative world and find snowy mountains with `/locate biome minecraft:snowy_slopes`
(frozen peaks and groves work too). Give the gear:

```text
/give @s alpineshred:snowboard
/give @s alpineshred:snowboard_boots
/give @s alpineshred:goggles
/give @s alpineshred:mitts
/give @s alpineshred:ski_helmet
/give @s alpineshred:skis
/give @s alpineshred:ski_boots
/give @s alpineshred:ski_poles
```

Put the board (or skis) in the snow gear slot (Curios on Forge, the feet group in Trinkets on
Fabric), goggles and mitts in theirs, and the matching boots on your feet. A run of
`alpineshred:groomed_snow` is the smoothest surface if the mountain is rough.

## Controls to tell testers

- **G** straps in and out. Look where you want to go; the board follows.
- **W** skates on flat snow to get moving. **Shift** brakes.
- Hold **Space** to crouch for a pop and release to jump; ramps and ledges launch you on their own.
- On skis, **right-click** with poles in hand to push.
- **F5** for third person, to watch the rider.

Say nothing else about how it should feel. Let them ride.

## The 10 minutes

1. 3 minutes free riding on a snowboard, on a slope of their choosing.
2. 2 minutes of turns: wide carves, then quick ones.
3. 2 minutes looking for air: a ledge, a pop off a roller, a kicker built from snow layers.
4. 1 minute through powder snow, if the mountain has any (or place some).
5. 2 minutes on skis with poles.

Note anything they do not find without help, and where they stop or hesitate.

## Questions afterwards

Ask each one open, then record the answer in a word or two.

1. Did the rider's lean and the camera match what you were doing with the controls?
2. Was the camera roll or the zoom at speed uncomfortable at any point?
3. Could you hear the difference between groomed snow, ice and powder? Did any sound get annoying?
4. The snow spray: too much, about right, or too little?
5. Did anything look wrong: the board not under the feet, a limb at an odd angle, arms showing in
   first person, a flicker?
6. At the end, did you want to keep riding?

Also record: frame rate with and without riding (F3), any crash or error in the log, loader, and
whether they tried the comfort settings below.

## Settings to point out if someone is uncomfortable

In `config/alpineshred-client.json`: `client.reduceMotion` (no camera roll, zoom or speed lines),
`client.fovEffect` (0 to 1, how much the view widens), `client.flashSafe` (no white flash or red
pulse), `client.hudSpeed` (speedometer and the board's wax and edge).

`/alpineshred debug ride on` shows the ride state, speed, lean and surface, useful when a tester
reports something odd.
