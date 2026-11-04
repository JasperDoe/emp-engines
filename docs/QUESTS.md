# Quest book and research locks

The quest book and the research stages are generated from one file:

    python tools/questgen/build_quests.py --check <items.json>

It writes `pack/config/ftbquests/quests/` and `pack/kubejs/data/skybound/chapters/stages/`.
IDs come from stable keys, so regenerating keeps player progress. Once you start editing the
book in-game, stop regenerating, or copy your edits back into the script first, because it
overwrites the quest folder.

## How research works (for now)

Each age starts with a gear-shaped **Research** quest: pay XP points plus sample items, then
claim the reward. The reward runs `/chapters add <player> skybound:<stage>`.

FTB Quests progress is shared by a team, but rewards are claimed per player, so every team
member has to claim the research reward. This is a stopgap until the research bench.

| Stage | XP points (≈ level) | Unlocks |
|---|---|---|
| andesite_age | 160 (10) | Create kinetics and basic machines |
| logistics | 315 (15) | Vaults, packages, frogports, stock tickers |
| alloy_age | 550 (20) | IE coke oven, blast furnace, alloy kiln, copper fluids, brass, foundry |
| flight | 910 (25) | Create Aeronautics, Simulated, Offroad |
| brass_age | 1395 (30) | Brass machines, crafters, deployers, arms, trains |
| electricity_age | 2920 (40) | Crafts & Additions, IE powered machines |
| exotic_age | 5345 (50) | Create: Enchantment Industry |

## Server-wide unlocks

`/skybound unlock nether`, `/skybound lock nether`, `/skybound status` (ops only). The Nether Key
quest runs the unlock. Players get or lose the stage at login, so offline players catch up.

## Admin commands

    /chapters list <player>
    /chapters add <player> skybound:<stage>
    /chapters remove <player> skybound:<stage>
    /ftbquests editing_mode
