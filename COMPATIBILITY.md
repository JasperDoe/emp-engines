# Compatibility notes

What's already known from the mod pages, before any test run. The test
server run (`tools\test-server.ps1`) confirms or breaks these.

## Version baseline

| Thing | Version | Why |
| --- | --- | --- |
| Minecraft | 1.21.1 | Create Aeronautics is 1.21.1-only |
| NeoForge | 21.1.267 (the server host offers only this one for 1.21.1; the client profile must match) | Floor for Sable CleanUp and Upgraded Portable Engines |
| Create | 6.0.10 | Required by Aeronautics 1.3.x, Enchantment Industry 2.4.x, Recipe Need RPM, Upgraded Portable Engines |
| Sable | 2.x | Required by current Aeronautics add-ons |
| Java | 21 | Required by NeoForge 1.21.1 |

If packwiz pulls a newer Create than an add-on supports, pin Create to 6.0.10.

## Dependencies packwiz should pull in

- Create Aeronautics: Sable (and Sable's own libraries, e.g. Veil)
- Create: Enchantment Industry: Create: Dragons Plus
- Restage: ModernUI
- Silent Gear: Silent Lib
- Apothic Enchanting: Apothic Attributes, Placebo
- FTB Quests / Chunks: FTB Library, FTB Teams, Architectury
- KubeJS: Rhino, Architectury

## Known problems

1. **Shaders:** Aeronautics has known visual issues with Iris. No Iris or
   shaderpacks in the pack.
2. **Sky Archipelago vs Tectonic (big one):** Sky Archipelago works by
   changing the world's `level-type`, which replaces the Overworld. Our design
   wants a normal (Tectonic) Overworld and floating islands only in the
   wilderness dimension. Unless Sky Archipelago can drive a custom dimension,
   drop it and build the wilderness with vanilla's built-in
   `minecraft:floating_islands` terrain in a datapack dimension. That needs no
   mod. Decide after the test run.
3. **Distant Horizons + Sky Archipelago:** ghost structures were fixed in Sky
   Archipelago 1.3.6. Use 1.3.6 or newer if it stays.
4. **Silent Gear 4.x and Tiered Foundry:** part casting and coating call
   Silent Gear's API (CompoundPartItem, PartMaterialIngredient, GearData). After
   a Silent Gear update, check a cast pickaxe head and a coating in game.
5. **Silent Updated resource pack** must target Silent Gear 4.x textures;
   check visually in the client test.
6. **Restage and Create Tiers** are very new with few users. If either
   misbehaves: Chapters replaces Restage, and Create Tiers can be dropped
   (keep Create's 256 RPM cap).

## Removed from the design (do not add)

Tinkers' Construct (no stable 1.21.1), Tetra (1.20.1 only), Waystones and
other teleport mods, Iris, Apothic Spawners.

## Client test (after the server test passes)

1. In Prism Launcher: Add Instance > Import > paste the path to `pack.toml`
   (or run `packwiz modrinth export` and import the .mrpack).
2. Launch, join the test server, and check: an airship flies, Ponder scenes
   open, JEI shows recipes, Sodium + Distant Horizons render ships correctly.
3. Send me `logs/latest.log` from the client if anything looks wrong.
