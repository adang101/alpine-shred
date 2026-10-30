# Phase 6 hand check: painting boards and skis

Phase 6's exit gate is mostly about people: five players each paint and save a design in under
10 minutes, and 20 different designs render on three clients within the texture budget. The
automated runs cover the rest (saves through the bench with and without dye, rejected saves
charging nothing, smithing and save-and-load keeping the design, a scripted client painting and
saving through the real editor, and watchers receiving a rider's design through the gear slot).
This page is the session for the people part. About 20 minutes with five players.

## Set up

A server (or a LAN world) with the mod, five players, creative or survival. Each player:

```text
/give @s alpineshred:paint_bench
/give @s alpineshred:snowboard
/give @s alpineshred:skis
/give @s minecraft:red_dye 8
```

Place the bench and use it. Put the board in the left slot and dye in the right one: the editor
opens on its own.

## Paint (10 minutes, timed)

Start a timer for each player when the editor first opens; stop it at their first successful
**Save** (the status line says "Design saved"). Ask them to:

1. Try a template (the `<` and `>` buttons pick one, the middle button applies it).
2. Paint over it with the pencil (B), fill (F) and a gradient (G: drag from one end to the other).
3. Turn on mirror (M) and paint something symmetric.
4. Paint the back (click the lower canvas, or Tab).
5. Name it and press **Save**. One dye is used (none in creative).

Write down each player's time, and anything that confused them.

## Look (5 minutes)

1. Everyone equips their board and straps in (G) near each other. Check every board shows its
   own design on the deck, the base (during a flip, or carried on the back unstrapped) and the
   edges, from your own view and from the others'.
2. Hold the board: the design shows in hand, in the hotbar and in the inventory; drop it: it
   shows on the ground.
3. Hover the board: the tooltip names the design and its author.
4. Copy a share code (**Code**), paste it to another player in chat, and have them paste it
   (**Paste**) on their own board and save.
5. With 20 designs around (four each), note any stutter when they come into view. Turn
   `client.showCustomDesigns` off in `config/alpineshred-client.json` and rejoin: everyone else's
   gear looks default, yours still designed.

## Note

- Times to the first save (the gate is under 10 minutes for each of the five).
- Anything drawn wrong: a face upside down or mirrored, edges missing, a design on the wrong gear.
- Anything lost: a design gone after a restart, after smithing a park board into a pro one, after
  putting the board in a chest.
- Curios (Forge) and Trinkets (Fabric): designs on boards worn in the snow gear slot are seen by
  the other players.
