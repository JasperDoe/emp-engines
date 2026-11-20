#!/usr/bin/env python3
"""
Recipe overrides for the foundry-centred metal progression. Writes datapack files into
pack/kubejs/data (KubeJS loads that folder as a datapack, after the mods, so these win).

    python tools/recipes/build_recipe_overrides.py <mods folder>

The mods folder needs the Silent Gear, Create and Create Crafts & Additions jars. (Productive Metalworks and
Silent Gear Metalworks are gone: Tiered Foundry melts, alloys and casts, including Silent Gear parts and coating.
Their old override files under pack/kubejs/data are switched off by this script.)

What it does
  1. Alloys only come from the foundry: recipes that make an alloy out of ingots elsewhere are
     switched off (crafting table, Create mixer, Silent Gear's Alloy Forge).
  2. Silent Gear's Alloy Forge is removed (the foundry alloys all four of its metals). Its gem
     alloyer (Recrystallizer) and Dimerald go too: Dimerald is a diamond-tier gem that could be
     made into parts by hand.
  3. Diamond-tier and better materials can't be turned into Silent Gear tools by hand: the vanilla
     diamond and netherite tool/armour conversions are removed.
  4. (Heat is Tiered Foundry's business now: each metal's melting temperature is in tools/foundry/gen.py.)
  6. The machines this leaves with nothing to do (Alloy Forge, Recrystallizer,
     Silent Gear's custom ingot/gem) are hidden from EMI, together with every recipe that uses them.
  9. Vanilla tools and armour have no recipes and are hidden in EMI; Silent Gear tools and armour of iron tier
     and below can be crafted straight from material + sticks in vanilla shapes (emp_engines:sg_direct/*).
     Coatings only come from pouring metal over gear on a casting table: every hand coating recipe is off and
     the coating items are hidden.
  8. Vanilla gear converts to Silent Gear automatically (Silent Apotheosis Raids, in players' inventories):
     Silent Gear's conversion recipes are copied to data/silentraids/gear_conversion/ and switched off as
     crafting recipes.
  7. Hand crafting goes up to iron tier, for vanilla and Silent Gear alike:
     - vanilla diamond tools and armour can't be crafted, and the netherite smithing upgrades are off;
     - every Silent Gear tool material above iron (harvest level over 2: diamond, crimson iron,
       netherite, the Silent Gear alloys...) gets the category "emp_cast_only", and every hand recipe
       that takes a head/plate, rod, tip or coating material (crafting grid and the coating smithing
       recipe) refuses that category. Iron tier and below can be made by hand.
       Above iron the head is cast (Tiered Foundry part casts), so a recipe viewer shows hand
       recipes with iron-tier materials only, and "head + rod" for the finished tool.

Besides the datapack files it writes pack/kubejs/server_scripts/foundry_overrides.js, which removes
the same recipes by id (so they go even if a mod's own data loads after ours) and hides the items.
"""
import io, json, os, sys, zipfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "pack", "kubejs", "data")

OFF = [
    # --- Silent Gear: Alloy Forge and everything it makes (the foundry alloys these) ---
    "silentgear:alloy_forge",
    "silentgear:alloying/metal/azure_electrum_ingot", "silentgear:alloying/metal/blaze_gold_ingot",
    "silentgear:alloying/metal/crimson_steel_ingot", "silentgear:alloying/metal/tyrian_steel_ingot",
    "silentgear:alloying/metal/custom_ingot.high_carbon_steel",
    # its crafting-table alloys
    "silentgear:crimson_steel_ingot", "silentgear:azure_electrum_ingot", "silentgear:blaze_gold_ingot",
    # gem alloyer and Dimerald (diamond-tier gem that skipped the foundry)
    "silentgear:recrystallizer", "silentgear:alloying/gem/custom_gem.dimerald",
    # --- alloys from ingots outside the foundry ---
    "minecraft:netherite_ingot",
    "create:mixing/brass_ingot",
    "createaddition:mixing/electrum",
    # --- Apotheosis: gems only, no affix gear. No reforging or augmenting (nothing to reforge), and Sigils of
    # Socketing come only from blood moons and quests (Silent Apotheosis Raids) ---
    "apotheosis:reforging_table", "apotheosis:simple_reforging_table", "apotheosis:augmenting_table",
    "apotheosis:reforging/common", "apotheosis:reforging/uncommon", "apotheosis:reforging/rare",
    "apotheosis:reforging/epic", "apotheosis:reforging/mythic",
    "apotheosis:sigil_of_socketing",
]

# Hidden from EMI (with every recipe that uses them): machines with nothing left to do
HIDE = [
    "silentgear:alloy_forge", "silentgear:recrystallizer",
    "silentgear:custom_ingot", "silentgear:custom_gem",          # High Carbon Steel and Dimerald
    "apotheosis:reforging_table", "apotheosis:simple_reforging_table", "apotheosis:augmenting_table",
]
# vanilla gear -> Silent Gear gear: no crafting-grid conversions at all. Silent Apotheosis Raids converts vanilla
# gear in players' inventories automatically, from copies of these recipes in data/silentraids/gear_conversion/
# (written below), so recipe viewers don't list them. The convert recipes themselves are found in the jars.
GEAR_PIECES = ("axe", "hoe", "pickaxe", "shovel", "sword", "helmet", "chestplate", "leggings", "boots")
# Vanilla tools and armour are gone: no recipes (the netherite smithing upgrades included) and hidden in EMI.
# Any that turn up (loot, mobs, trades) become Silent Gear gear in the player's inventory.
TOOL_PIECES = ("axe", "hoe", "pickaxe", "shovel", "sword")
ARMOUR_PIECES = ("helmet", "chestplate", "leggings", "boots")
VANILLA_GEAR = []
for metal in ("wooden", "stone", "iron", "golden", "diamond", "netherite"):
    VANILLA_GEAR += [f"minecraft:{metal}_{p}" for p in TOOL_PIECES]
for metal in ("leather", "iron", "golden", "diamond", "netherite"):
    VANILLA_GEAR += [f"minecraft:{metal}_{p}" for p in ARMOUR_PIECES]
for item in VANILLA_GEAR:
    if item.startswith("minecraft:netherite_"):
        OFF.append(item + "_smithing")
    else:
        OFF.append(item)
HIDE += VANILLA_GEAR

# Silent Gear coatings only come from the casting table (Silent Apotheosis Raids' CastCoating): the coating part,
# its blueprint/template, the coating smithing template and every coating smithing recipe are gone.
OFF += ["silentgear:part/coating", "silentgear:coating_blueprint", "silentgear:coating_template",
        "silentgear:coating_smithing_template", "silentgear:coating_smithing_template_duplication"]
HIDE += ["silentgear:coating", "silentgear:coating_blueprint", "silentgear:coating_template",
         "silentgear:coating_smithing_template"]

# Silent Gear: hand crafting up to iron tier (see 7. above)
CAST_ONLY = "emp_cast_only"
HAND_MAX_LEVEL = 2.0                     # iron
HAND_PART_TYPES = {"silentgear:main", "silentgear:rod", "silentgear:tip", "silentgear:coating"}
HAND_RECIPE_TYPES = {"silentgear:compound_part", "silentgear:gear_crafting", "silentgear:shaped_gear_crafting",
                     "silentgear:smithing/coating"}

DISABLED = {"neoforge:conditions": [{"type": "neoforge:false"}]}


def write(rel, data):
    path = os.path.join(OUT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def recipe_path(rid):
    ns, path = rid.split(":", 1)
    return f"{ns}/recipe/{path}.json"


def jars(folder):
    for name in sorted(os.listdir(folder)):
        if name.endswith(".jar"):
            yield name, zipfile.ZipFile(os.path.join(folder, name))


def effective(folder, prefix):
    """{path under data/: json} for files under data/<prefix> in Silent Gear's own jar (Silent Gear Metalworks,
    if its jar is still lying in the folder, is ignored: it's no longer in the pack)."""
    found = {}
    for name, z in jars(folder):
        low = name.lower()
        if "sgearmetalworks" in low or "metalworks" in low or ("silent-gear" not in low and "silentgear" not in low):
            continue
        for entry in z.namelist():
            if entry.startswith("data/" + prefix) and entry.endswith(".json"):
                try:
                    found[entry[len("data/"):]] = json.loads(z.read(entry))
                except ValueError:
                    pass
    return found


def harvest_level(material):
    for props in (material.get("properties") or {}).values():
        if isinstance(props, dict):
            for key, value in props.items():
                if "harvest_tier" in key and isinstance(value, dict):
                    try:
                        return float(value.get("level_hint"))
                    except (TypeError, ValueError):
                        return None
    return None


def hand_tier_cap(folder):
    """Tag materials above iron as cast-only and make the hand recipes refuse them."""
    materials = 0
    for prefix in ("silentgear/silentgear_materials/",):
        for rel, d in effective(folder, prefix).items():
            level = harvest_level(d)
            if level is None or level <= HAND_MAX_LEVEL:
                continue
            d = json.loads(json.dumps(d))
            cats = d.setdefault("crafting", {}).setdefault("categories", [])
            if CAST_ONLY not in cats:
                cats.append(CAST_ONLY)
            write(rel, d)
            materials += 1

    def fix(x):
        changed = False
        if isinstance(x, dict):
            if x.get("type") == "silentgear:material" and x.get("part_type", "silentgear:main") in HAND_PART_TYPES:
                nots = [c for c in x.get("not_categories", []) if c != "casting"] + [CAST_ONLY]
                x["not_categories"] = nots
                changed = True
            for v in x.values():
                changed = fix(v) or changed
        elif isinstance(x, list):
            for v in x:
                changed = fix(v) or changed
        return changed

    recipes = 0
    off = set(OFF)
    for rel, d in effective(folder, "silentgear/recipe/").items():
        if d.get("type") not in HAND_RECIPE_TYPES or "silentgear:" + rel[len("silentgear/recipe/"):-len(".json")] in off:
            continue
        d = json.loads(json.dumps(d))
        if fix(d):
            write(rel, d)
            recipes += 1
    return materials, recipes


# Silent Gear tools and armour straight from the crafting grid, vanilla style (material + sticks), for iron tier
# and below: emp_engines:sg_direct/<item>. Above iron the head is cast.
DIRECT_SHAPES = {
    "pickaxe": ["###", " / ", " / "],
    "axe": ["##", "#/", " /"],
    "shovel": ["#", "/", "/"],
    "hoe": ["##", " /", " /"],
    "sword": ["#", "#", "/"],
    "helmet": ["###", "# #"],
    "chestplate": ["# #", "###", "###"],
    "leggings": ["###", "# #", "# #"],
    "boots": ["# #", "# #"],
}


def direct_recipes():
    for item, pattern in DIRECT_SHAPES.items():
        key = {"#": {"type": "silentgear:material", "gear_type": f"silentgear:{item}",
                     "not_categories": [CAST_ONLY], "part_type": "silentgear:main"}}
        if any("/" in row for row in pattern):
            key["/"] = {"tag": "c:rods/wooden"}
        write(f"emp_engines/recipe/sg_direct/{item}.json", {
            "type": "silentgear:shaped_gear_crafting", "category": "equipment",
            "key": key, "pattern": pattern, "result": {"count": 1, "id": f"silentgear:{item}"}})
    return len(DIRECT_SHAPES)


def coating_smithing(folder):
    """Every Silent Gear coating smithing recipe goes (coatings are cast)."""
    for rel in effective(folder, "silentgear/recipe/smithing/coating/"):
        rid = "silentgear:" + rel[len("silentgear/recipe/"):-len(".json")]
        if rid not in OFF:
            OFF.append(rid)


def gear_conversions(folder):
    """Moves Silent Gear's vanilla -> Silent Gear conversion recipes out of the recipe folder."""
    moved = 0
    for rel, d in effective(folder, "silentgear/recipe/gear/convert/").items():
        if d.get("type") != "silentgear:conversion":
            continue
        name = rel.rsplit("/", 1)[1][:-len(".json")]
        write(f"silentraids/gear_conversion/{name}.json", d)
        rid = "silentgear:gear/convert/" + name
        if rid not in OFF:
            OFF.append(rid)
        moved += 1
    return moved


def retire_metalworks():
    """Productive Metalworks and Silent Gear Metalworks left the pack: drop every override this script once
    wrote for them. (Silent Gear reads its material files with its own loader, which doesn't understand
    neoforge:conditions, so a switched-off stub there crashes world loading.) On the PC the removed files
    are pushed as EMP_TOMBSTONE files, which tools/common.ps1 deletes."""
    import shutil
    n = 0
    for ns in ("productivemetalworks", "sgearmetalworks"):
        base = os.path.join(OUT, ns)
        if os.path.isdir(base):
            n += sum(len(f) for _, _, f in os.walk(base))
            shutil.rmtree(base)
    return n


def main(folder):
    converted = gear_conversions(folder)
    coating_smithing(folder)
    direct = direct_recipes()
    for rid in OFF:
        write(recipe_path(rid), DISABLED)

    retired = retire_metalworks()

    # Apotheosis: no affix gear in chests or turned out of other loot (gems are untouched)
    write("apotheosis/loot_modifiers/affix_conversion.json", {"type": "apotheosis:affix_conversion", "conditions": [],
          "entries": [{"chance": 0.0, "pattern": {"path_regex": ".*"}}]})
    write("apotheosis/loot_modifiers/affix_loot_injection.json", {"type": "apotheosis:affix_loot", "conditions": [],
          "entries": [{"chance": 0.0, "pattern": {"path_regex": "chests.*"}}]})
    # Sigil of Socketing on the smithing table: Silent Apotheosis Raids' version, one sigil socket per item
    # (the rest come from Silent Gear levels). Not in OFF: KubeJS would remove this override too.
    write("apotheosis/recipe/sigil_add_sockets.json", {"type": "silentraids:sigil_socket",
          "input": {"item": "apotheosis:sigil_of_socketing"}})

    # EMI (and any recipe viewer) skips items in this tag
    write("c/tags/item/hidden_from_recipe_viewers.json", {"replace": False, "values": [
        {"id": i, "required": False} for i in HIDE]})

    js = os.path.join(ROOT, "pack", "kubejs", "server_scripts", "foundry_overrides.js")
    with open(js, "w", newline="\n") as f:
        f.write("// Written by tools/recipes/build_recipe_overrides.py; edit that and re-run it.\n")
        f.write("// Alloys only from the foundry, no Alloy Forge / Recrystallizer / Alloy Kiln, no hand-converted\n")
        f.write("// diamond or netherite Silent Gear gear, no vanilla diamond gear or netherite upgrade by hand, no Apotheosis\n")
        f.write("// reforging/augmenting or crafted Sigils of Socketing.\n")
        f.write("// The same recipes are also switched off in pack/kubejs/data.\n\n")
        f.write("const REMOVED_RECIPES = [\n")
        for rid in OFF:
            f.write(f"  '{rid}',\n")
        f.write("]\n\nconst HIDDEN_ITEMS = [\n")
        for i in HIDE:
            f.write(f"  '{i}',\n")
        f.write("]\n\n")
        f.write("ServerEvents.recipes(event => {\n  REMOVED_RECIPES.forEach(id => event.remove({ id: id }))\n})\n\n")
        f.write("// Hides the items and every recipe that uses them in EMI\n")
        f.write("RecipeViewerEvents.removeEntriesCompletely('item', event => {\n  HIDDEN_ITEMS.forEach(id => event.remove(id))\n})\n")

    materials, recipes = hand_tier_cap(folder)
    print(f"{len(OFF)} recipes switched off, {retired} old Metalworks override files switched off, written to {OUT}")
    print(f"{materials} Silent Gear materials above iron marked {CAST_ONLY}, {recipes} hand recipes refuse them")
    print(f"{converted} vanilla -> Silent Gear conversions moved to data/silentraids/gear_conversion")
    print(f"{direct} direct Silent Gear recipes (iron tier and below) written to emp_engines/recipe/sg_direct")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
