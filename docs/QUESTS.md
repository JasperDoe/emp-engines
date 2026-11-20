# Quest book and research locks

## Quest book

The quest book is a **guide and tutorial only**. It has no costs and no rewards.
Research is done separately, at the research bench (not built yet).

Each age chapter opens with a gear-shaped quest holding a "gamestage" task. That task ticks
itself once the player has the age's stage, which opens the rest of the chapter.

The book lives in `tools/questgen/book.json`. Edit it with the visual editor (no Minecraft needed):

    python tools/questedit/serve.py        (or double-click tools/questedit/QuestEditor.bat)

It opens in your browser at http://127.0.0.1:8765. Chapters on the left, the quest map in the
middle (drag quests, double-click empty space for a new one, Ctrl+click a quest to make the selected
one depend on it), and the selected quest's title, description (with a colour-code preview), tasks,
icon and links on the right. The item picker lists every item in the pack, with names and icons
read straight out of the mod jars (mod-files/mods, your own built mods, bundled mods inside jars,
and Minecraft's own items from the moddev build folder or .minecraft). Check lists unknown item ids
and broken links. Save writes book.json (old copy kept as book.json.bak) and rebuilds the quest files.

## Research tree

EXP Research's tree (the Research Table's tabs and research) has its own visual editor, the same kind as the
quest editor:

    python tools/researchedit/serve.py     (or double-click tools/researchedit/ResearchEditor.bat)

It opens at http://127.0.0.1:8775. Tabs on the left with the stage locks below them (red: no research grants
that lock yet, so what it locks can't be made in survival), the tree in the middle (drag research, double-click
empty space for a new one, Ctrl+click a research to make the selected one need it; the dashed box is roughly
what the in-game panel shows at once), and the selected research's title, id, icon, description, tab,
requirements, XP points, samples and unlocked stages on the right. Items and icons come from the quest
editor's jar scan. Save writes `pack/kubejs/data/emp_engines/expresearch/category/*.json` and `research/*.json`,
deletes the files of research you removed, and keeps the old files in `tools/researchedit/.backup/`.

Or rebuild the quest book from book.json directly:

    python tools/questgen/build_quests.py --check <items.json>

This writes `pack/config/ftbquests/quests/`. IDs come from the quest keys, so rebuilding keeps
player progress (don't rename a key once players have done the quest). Don't edit the book with
the in-game FTB Quests editor: the next rebuild overwrites the quest folder.

## Research locks

Edit them in the research editor's **Stage locks** view (top bar): add, rename, reorder and delete stages, and
for each one pick what it locks:

- **Materials:** modded items. The item can't be crafted and is hidden in EMI, and so is everything crafted
  from it, all the way down.
- **Tags:** the same for every modded item in a tag (e.g. `c:ingots/steel`).
- **Whole mods:** every item the mod adds.
- **Never lock:** items the stage leaves free even when they're made from locked things.
- **Dimension stages** instead block travel to dimensions (`end_access`, opened by `/empyrean unlock end`).

Save rebuilds the lock files from every recipe in the jars (mod-files/mods, manual-mods and our own mods in
pack/mods) and shows what each stage locks. A stage in red has no research unlocking it yet. Run `/reload`
in game (or restart) to pick up new locks. The seeds are kept in `tools/questgen/stages.json`; the same build
runs from the command line with:

    python tools/questgen/build_stages.py "mod-files/mods" "manual-mods" "pack/mods"

This writes `pack/kubejs/data/emp_engines/chapters/stages/*.json` and `tools/questgen/stages-report.txt`.

- **Seeds:** the materials, tags and mods of each stage in `stages.json`.
- **Crafted-from closure:** any item whose recipes all need a locked ingredient is locked too, in
  the latest stage it depends on (stage order is the list order in the editor). The andesite alloy block is locked to the Andesite Age because
  its only recipe uses andesite alloy. An item with any recipe made from free ingredients stays free.
- **Locks are on crafting only:** a locked item can't be crafted and its recipes are hidden in JEI,
  but it can still be picked up, used and traded.
- **Not covered:** vanilla items are never locked, fluid ingredients are ignored, and recipes added only by
  KubeJS scripts aren't seen (recipes in data files are).
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

Ops can run `/empyrean unlock end`, `/empyrean lock end` and `/empyrean status`.
Players get or lose the stage when they log in, so offline players catch up.
The Nether has no server-wide lock: each nether portal needs an Obsidian Key (see MODS.md, Nether portal keys). "The Nether Key" quest completes when you hold one.

## Testing commands

    /chapters list <player>
    /chapters add <player> emp_engines:andesite_age
    /chapters remove <player> emp_engines:andesite_age
    /ftbquests editing_mode
