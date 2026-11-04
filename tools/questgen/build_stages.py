#!/usr/bin/env python3
"""
Generates the research stage locks (Chapters) from a seed list per tier, then
locks everything crafted from a locked item as well.

    python tools/questgen/build_stages.py <mods folder> [<mods folder> ...]

For every recipe in the mod jars (and their bundled jars), an item is locked to the
lowest tier at which ALL of one of its recipes' ingredients are unlocked. So the
andesite alloy block (made only from andesite alloy) is locked to the Andesite Age,
while an item that also has a recipe from free ingredients stays free.

- Vanilla (minecraft:) items are never locked.
- Fluid ingredients are ignored, so a recipe made only from fluids counts as free.
- An item tag counts as locked only if every member we can see is locked.
  Vanilla and NeoForge tags aren't in the mod jars, so a tag they extend is treated
  by its modded members only.

Writes pack/kubejs/data/skybound/chapters/stages/*.json and stages-report.txt.
"""
import io, json, os, re, sys, zipfile
from collections import defaultdict

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SDIR = os.path.join(ROOT, "pack", "kubejs", "data", "skybound", "chapters", "stages")

# --------------------------------------------------------------------------
# Research tiers -> Chapters stages
# Locked items can still be held, used and traded; only crafting is locked
# and the recipes are hidden in JEI until the player has the stage.
# --------------------------------------------------------------------------
LOCK = {"preventCraft": True, "hideInJei": True, "preventPickup": False,
        "dropFromInventory": False, "preventTransfer": False}

STAGES = {
    "andesite_age": {"items": [
        "create:andesite_alloy", "create:shaft", "create:cogwheel", "create:large_cogwheel",
        "create:gearbox", "create:vertical_gearbox", "create:clutch", "create:gearshift",
        "create:encased_chain_drive", "create:hand_crank", "create:water_wheel",
        "create:large_water_wheel", "create:windmill_bearing", "create:mechanical_bearing",
        "create:white_sail", "create:sail_frame", "create:millstone", "create:mechanical_press",
        "create:mechanical_mixer", "create:basin", "create:whisk", "create:propeller",
        "create:encased_fan", "create:mechanical_drill", "create:mechanical_saw",
        "create:mechanical_harvester", "create:mechanical_plough", "create:andesite_casing",
        "create:andesite_funnel", "create:andesite_tunnel", "create:chute", "create:depot",
        "create:weighted_ejector", "create:goggles", "create:wrench", "create:speedometer",
        "create:stressometer", "create:cart_assembler", "create:linear_chassis",
        "create:radial_chassis", "create:super_glue", "create:mechanical_piston",
        "create:sticky_mechanical_piston", "create:piston_extension_pole",
        "create:gantry_carriage", "create:gantry_shaft", "create:rope_pulley",
        "create:belt_connector"]},
    "alloy_age": {"items": [
        "create:brass_ingot", "create:copper_casing", "create:fluid_pipe", "create:mechanical_pump",
        "create:fluid_tank", "create:fluid_valve", "create:spout", "create:item_drain",
        "create:hose_pulley", "create:portable_fluid_interface",
        "immersiveengineering:cokebrick", "immersiveengineering:blastbrick",
        "immersiveengineering:alloybrick", "immersiveengineering:hammer",
        "silentgear:alloy_forge"],
        "namespaces": ["productivemetalworks"]},
    "brass_age": {"items": [
        "create:brass_casing", "create:electron_tube", "create:precision_mechanism",
        "create:mechanical_crafter", "create:deployer", "create:brass_hand", "create:mechanical_arm",
        "create:brass_funnel", "create:brass_tunnel", "create:smart_chute", "create:smart_fluid_pipe",
        "create:rotation_speed_controller", "create:sequenced_gearshift",
        "create:adjustable_chain_gearshift", "create:steam_engine", "create:crushing_wheel",
        "create:mechanical_roller", "create:schematicannon", "create:schematic_table",
        "create:elevator_pulley", "create:contraption_controls", "create:display_link",
        "create:display_board", "create:nixie_tube", "create:content_observer",
        "create:stockpile_switch",
        # trains
        "create:track", "create:track_station", "create:track_signal", "create:track_observer",
        "create:controls", "create:railway_casing"]},
    "electricity_age": {"items": [
        "immersiveengineering:light_engineering", "immersiveengineering:heavy_engineering",
        "immersiveengineering:blastbrick_reinforced", "immersiveengineering:circuit_table",
        "immersiveengineering:component_iron", "immersiveengineering:component_steel",
        "immersiveengineering:component_electronic", "immersiveengineering:component_electronic_adv",
        "immersiveengineering:circuit_board", "immersiveengineering:logic_circuit",
        "immersiveengineering:graphite_electrode"],
        "namespaces": ["createaddition"]},
    "exotic_age": {"items": [],
        "namespaces": ["create_enchantment_industry"]},
    "flight": {"items": [],
        "namespaces": ["aeronautics", "simulated", "offroad", "aeroworks"]},
    "logistics": {"items": [
        "create:item_vault", "create:portable_storage_interface", "create:packager",
        "create:repackager", "create:package_frogport", "create:package_filter",
        "create:stock_link", "create:stock_ticker", "create:redstone_requester",
        "create:factory_gauge"]},
    # server-wide unlocks, granted by /skybound unlock <name>
    "nether_access": {"dimensions": ["minecraft:the_nether"], "travel": True},
    "end_access": {"dimensions": ["minecraft:the_end"], "travel": True},
}


# Tier order. An item that needs inputs from several tiers goes in the highest one.
ORDER = ["andesite_age", "logistics", "alloy_age", "brass_age", "flight", "electricity_age", "exotic_age"]
RANK = {s: i + 1 for i, s in enumerate(ORDER)}

# --------------------------------------------------------------------------
# Jar scanning
# --------------------------------------------------------------------------
RECIPE_RE = re.compile(r"^data/([^/]+)/recipes?/(.+)\.json$")
TAG_RE = re.compile(r"^data/([^/]+)/tags/items?/(.+)\.json$")
LANG_RE = re.compile(r"^assets/([^/]+)/lang/en_us\.json$")
MODID_RE = re.compile(r'modId\s*=\s*"([^"]+)"')

def scan_zip(zf, out):
    for name in zf.namelist():
        if name.endswith(".jar"):
            try:
                with zipfile.ZipFile(io.BytesIO(zf.read(name))) as inner:
                    scan_zip(inner, out)
            except zipfile.BadZipFile:
                pass
            continue
        if name.endswith("mods.toml"):
            out["mods"].update(MODID_RE.findall(zf.read(name).decode("utf-8", "replace")))
            continue
        m = RECIPE_RE.match(name)
        if m:
            try:
                out["recipes"].append((f"{m[1]}:{m[2]}", json.loads(zf.read(name))))
            except Exception:
                pass
            continue
        m = TAG_RE.match(name)
        if m:
            try:
                out["tags"][f"{m[1]}:{m[2]}"].append(json.loads(zf.read(name)))
            except Exception:
                pass
            continue
        m = LANG_RE.match(name)
        if m:
            try:
                for k in json.loads(zf.read(name)):
                    p = k.split(".")
                    if len(p) == 3 and p[0] in ("item", "block"):
                        out["items"].add(f"{p[1]}:{p[2]}")
            except Exception:
                pass

def scan(folders):
    out = {"mods": {"minecraft", "neoforge", "c"}, "recipes": [], "tags": defaultdict(list), "items": set()}
    for folder in folders:
        for fn in sorted(os.listdir(folder)):
            if fn.endswith(".jar"):
                with zipfile.ZipFile(os.path.join(folder, fn)) as zf:
                    scan_zip(zf, out)
    return out

# --------------------------------------------------------------------------
# Recipe parsing
# --------------------------------------------------------------------------
RESULT_KEYS = {"result", "results", "output", "outputs"}
SKIP_KEYS = {"type", "pattern", "category", "group", "conditions", "neoforge:conditions",
             "fabric:load_conditions", "transitional_item", "show_notification", "heat_requirement",
             "processing_time", "processingTime", "experience", "cookingtime", "loops", "keepHeldItem"}

def cond_ok(c, mods):
    if not isinstance(c, dict):
        return True
    t = c.get("type", "")
    if t in ("neoforge:mod_loaded", "forge:mod_loaded"):
        return c.get("modid") in mods
    if t in ("neoforge:not", "forge:not"):
        return not cond_ok(c.get("value"), mods)
    if t in ("neoforge:and", "forge:and"):
        return all(cond_ok(x, mods) for x in c.get("values", []))
    if t in ("neoforge:or", "forge:or"):
        return any(cond_ok(x, mods) for x in c.get("values", []))
    return True

def is_ingredient(o):
    return isinstance(o, dict) and any(k in o for k in ("item", "tag", "items", "children", "base", "fluid", "fluid_tag"))

def alts(o):
    """Alternatives of one ingredient slot: ('i', id) / ('t', tag) / None for free or unknown."""
    if isinstance(o, list):
        r = []
        for x in o:
            r += alts(x)
        return r
    if isinstance(o, str):
        return [("t", o[1:])] if o.startswith("#") else [("i", o)]
    if not isinstance(o, dict):
        return [None]
    if "fluid" in o or "fluid_tag" in o or "amount" in o:
        return [None]
    if "item" in o:
        v = o["item"]
        return [("i", v)] if isinstance(v, str) else alts(v)
    if "tag" in o:
        return [("t", o["tag"])]
    if "items" in o:
        return alts(o["items"])
    if "children" in o:
        return alts(o["children"])
    if "base" in o:
        return alts(o["base"])
    return [None]

def slots(o, in_list=False):
    if is_ingredient(o):
        yield alts(o)
    elif isinstance(o, dict):
        for k, v in o.items():
            if k in RESULT_KEYS or k in SKIP_KEYS:
                continue
            if isinstance(v, list):
                for x in v:
                    if isinstance(x, list):
                        a = alts(x)
                        if a:
                            yield a
                    else:
                        yield from slots(x)
            else:
                yield from slots(v)

def outputs(r):
    res = []
    for k in RESULT_KEYS:
        v = r.get(k)
        for x in (v if isinstance(v, list) else [v]):
            if isinstance(x, str):
                res.append(x)
            elif isinstance(x, dict) and "amount" not in x:
                i = x.get("id") or x.get("item")
                if isinstance(i, str):
                    res.append(i)
                elif isinstance(i, dict) and isinstance(i.get("id"), str):
                    res.append(i["id"])
    return res

# --------------------------------------------------------------------------
def build(folders):
    data = scan(folders)
    mods, items = data["mods"], data["items"]

    # tags -> members (resolved later, recursively)
    tags = defaultdict(set)
    for tag, files in data["tags"].items():
        for f in files:
            for v in f.get("values", []):
                if isinstance(v, dict):
                    v = v.get("id", "")
                tags[tag].add(v)

    recipes = defaultdict(list)  # output -> [ [slot alts], ... ]
    for rid, r in data["recipes"]:
        if not isinstance(r, dict):
            continue
        conds = r.get("neoforge:conditions", []) + (r.get("conditions", []) if isinstance(r.get("conditions"), list) else [])
        if not all(cond_ok(c, mods) for c in conds):
            continue
        outs = outputs(r)
        if not outs:
            continue
        trans = r.get("transitional_item", {})
        trans = trans.get("id") or trans.get("item") if isinstance(trans, dict) else None
        sl = [[a for a in s if a != ("i", trans)] or [None] for s in slots(r)]
        for o in outs:
            recipes[o].append(sl)

    # seeds
    seed = {}
    for st, s in STAGES.items():
        if st not in RANK:
            continue
        for i in s.get("items", []):
            seed[i] = st
        for ns in s.get("namespaces", []):
            for i in items:
                if i.startswith(ns + ":"):
                    seed.setdefault(i, st)
    rank = {i: RANK[st] for i, st in seed.items()}

    def item_rank(i):
        if i.startswith("minecraft:"):
            return 0
        return rank.get(i, 0)

    tag_cache = {}
    def tag_rank(t, seen=()):
        members = tags.get(t)
        if not members:
            return 0
        best = None
        for m in members:
            if m.startswith("#"):
                r = 0 if m[1:] in seen else tag_rank(m[1:], seen + (t,))
            else:
                r = item_rank(m)
            best = r if best is None else min(best, r)
            if best == 0:
                break
        return best or 0

    def slot_rank(s):
        best = None
        for a in s:
            if a is None:
                r = 0
            elif a[0] == "i":
                r = item_rank(a[1])
            else:
                r = tag_rank(a[1])
            best = r if best is None else min(best, r)
            if best == 0:
                break
        return best or 0

    changed = True
    rounds = 0
    while changed and rounds < 50:
        changed, rounds = False, rounds + 1
        for out, rs in recipes.items():
            if out in seed or out.startswith("minecraft:"):
                continue
            r = min(max([slot_rank(s) for s in sl] or [0]) for sl in rs)
            if r > rank.get(out, 0):
                rank[out] = r
                changed = True

    derived = defaultdict(list)
    for i, r in rank.items():
        if r and i not in seed:
            derived[ORDER[r - 1]].append(i)

    os.makedirs(SDIR, exist_ok=True)
    for f in os.listdir(SDIR):
        if f.endswith(".json"):
            os.remove(os.path.join(SDIR, f))
    report = [f"Scanned {len(data['recipes'])} recipes, {len(items)} items, {len(tags)} item tags", ""]
    for name, s in STAGES.items():
        if s.get("travel"):
            out = {"restrictions": {"preventTravel": True}, "dimensions": s["dimensions"]}
        else:
            ns = s.get("namespaces", [])
            extra = sorted(i for i in derived.get(name, []) if i.split(":")[0] not in ns)
            out = {"restrictions": dict(LOCK), "items": list(s.get("items", [])) + extra}
            if ns:
                out["namespaces"] = ns
            report.append(f"== {name}: {len(s.get('items', []))} listed + {len(extra)} crafted from them"
                          + (f", whole mods: {', '.join(ns)}" if ns else ""))
            report += [f"  {i}" for i in extra]
            report.append("")
        with open(os.path.join(SDIR, name + ".json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump(out, f, indent=2)
            f.write("\n")
    with open(os.path.join(ROOT, "tools", "questgen", "stages-report.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(report) + "\n")
    print("\n".join(l for l in report if not l.startswith("  ")))

if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    build(sys.argv[1:])
