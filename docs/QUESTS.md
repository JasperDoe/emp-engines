# Quest book and research locks

## Quest book

The quest book is a **guide and tutorial only**. It has no costs and no rewards.
Research is done separately, at the research bench (not built yet).

Each age chapter opens with a gear-shaped quest holding a "gamestage" task. That task ticks
itself once the player has the age's stage, which opens the rest of the chapter.

    python tools/questgen/build_quests.py --check <items.json>

This writes `pack/config/ftbquests/quests/`. IDs come from stable keys, so regenerating keeps
player progress. Once you start editing the book in-game, stop regenerating, or copy your edits
back into the script first, because it overwrites the quest folder.

## Research locks

    python tools/questgen/build_stages.py "mod-files/mods"

This writes `pack/kubejs/data/skybound/chapters/stages/*.json` and `tools/questgen/stages-report.txt`.

- **Seed list:** each tier has a hand-written list of items (and whole mods) in `build_stages.py`.
- **Crafted-from closure:** any item whose recipes all need a locked ingredient is locked too, in
  the highest tier it depends on. The andesite alloy block is locked to the Andesite Age because
  its only recipe uses andesite alloy. An item with any recipe made from free ingredients stays free.
- **Locks are on crafting only:** a locked item can't be crafted and its recipes are hidden in JEI,
  but it can still be picked up, used and traded.
- **Not covered:** vanilla items are never locked, and fluid ingredients are ignored.
- **When to re-run:** after adding or updating mods. Check the report for anything locked that
  shouldn't be.

| Tier | Opens the chapter |
|---|---|
| andesite_age | 1 · Andesite Age |
| logistics | Logistics & Transport |
| alloy_age | 2 · Alloy Age |
| brass_age | 3 · Brass Age |
| flight | 1 · First Flight (after finding a wreck) |
| electricity_age | 4 · Electricity Age |
| exotic_age | 5 · Exotic Age |

## Server-wide unlocks

Ops can run `/skybound unlock nether`, `/skybound lock nether` and `/skybound status`.
Players get or lose the stage when they log in, so offline players catch up.
The Nether project mechanic itself is still to be designed (not in the quest book).

## Testing commands

    /chapters list <player>
    /chapters add <player> skybound:andesite_age
    /chapters remove <player> skybound:andesite_age
    /ftbquests editing_mode
