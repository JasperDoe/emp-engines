#!/usr/bin/env python3
"""
Generates the research stage locks (Chapters) from the seeds in tools/questgen/stages.json (edited with
the research editor, tools/researchedit), then locks everything crafted from a locked item as well.

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

Writes pack/kubejs/data/emp_engines/chapters/stages/*.json and stages-report.txt.
With "gates_off": true at the top of stages.json, every stage file is written empty (nothing locked,
no dimension blocked); the seeds are kept, and the report still shows what each stage would lock.
"""
import io, json, os, re, sys, zipfile
from collections import defaultdict

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SDIR = os.path.join(ROOT, "pack", "kubejs", "data", "emp_engines", "chapters", "stages")

# --------------------------------------------------------------------------
# Research tiers -> Chapters stages
# Locked items can still be held, used and traded; only crafting is locked
# and the recipes are hidden in JEI until the player has the stage.
# --------------------------------------------------------------------------
LOCK = {"preventCraft": True, "hideInJei": True, "preventPickup": False,
        "dropFromInventory": False, "preventTransfer": False}

# The stage seeds live in tools/questgen/stages.json (edited with tools/researchedit):
#   "order": lock stages from first to last. An item that needs inputs from several goes in the latest one.
#   "stages": { name: {"kind": "lock", "items": [...], "tags": [...], "namespaces": [...], "except": [...]}
#               or {"kind": "dimension", "dimensions": [...]} }
#   items: locked materials, everything crafted from them is locked too; tags: the same for every modded
#   member of a tag; namespaces: whole mods; except: items this stage never locks (the spread stops there).
STAGES_JSON = os.path.join(os.path.dirname(os.path.abspath(__file__)), "stages.json")


def load_stages(path=STAGES_JSON):
    with open(path, encoding="utf-8") as f:
        cfg = json.load(f)
    stages = cfg.get("stages", {})
    order = [n for n in cfg.get("order", []) if n in stages] + [n for n in stages if n not in cfg.get("order", [])]
    return order, stages


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
def build(folders, data=None, stages_path=STAGES_JSON, quiet=False):
    """Writes the stage files. Returns a summary per stage for the research editor."""
    ORDER, STAGES = load_stages(stages_path)
    with open(stages_path, encoding="utf-8") as f:
        gates_off = bool(json.load(f).get("gates_off"))
    ORDER = [n for n in ORDER if STAGES[n].get("kind", "lock") == "lock"]
    RANK = {s: i + 1 for i, s in enumerate(ORDER)}
    if data is None:
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

    def tag_members(t, seen=()):
        out = set()
        for m in tags.get(t, ()):
            if m.startswith("#"):
                if m[1:] not in seen:
                    out |= tag_members(m[1:], seen + (t,))
            else:
                out.add(m)
        return out

    # items each stage must never lock (stops the spread there)
    excepted = {RANK[st]: set(STAGES[st].get("except", [])) for st in ORDER}

    # seeds: listed materials, modded members of listed tags, every item of listed mods
    seed, tagged = {}, defaultdict(list)
    for st in ORDER:
        s = STAGES[st]
        for i in s.get("items", []):
            seed.setdefault(i, st)
        for t in s.get("tags", []):
            for i in sorted(tag_members(t.lstrip("#"))):
                if not i.startswith("minecraft:") and i not in excepted[RANK[st]] and i not in seed:
                    seed[i] = st
                    tagged[st].append(i)
        for ns in s.get("namespaces", []):
            for i in items:
                if i.startswith(ns + ":") and i not in excepted[RANK[st]]:
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
            if r and out in excepted.get(r, ()):
                continue
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
    if gates_off:
        report[1:1] = ["RESEARCH GATES ARE OFF (\"gates_off\": true in stages.json): the stage files lock nothing.",
                       "What each stage would lock is listed below.", ""]
    summary = {"gates_off": gates_off, "recipes": len(data["recipes"]), "items": len(items), "tags": len(tags), "stages": {}}
    for name in list(ORDER) + [n for n in STAGES if n not in ORDER]:
        s = STAGES[name]
        if s.get("kind") == "dimension":
            out = {"restrictions": {"preventTravel": True}, "dimensions": s.get("dimensions", [])}
            summary["stages"][name] = {"kind": "dimension", "dimensions": s.get("dimensions", [])}
        else:
            ns = s.get("namespaces", [])
            listed = list(s.get("items", [])) + tagged.get(name, [])
            extra = sorted(i for i in derived.get(name, []) if i.split(":")[0] not in ns)
            out = {"restrictions": dict(LOCK), "items": listed + extra}
            if ns:
                out["namespaces"] = ns
            report.append(f"== {name}: {len(listed)} listed + {len(extra)} crafted from them"
                          + (f", whole mods: {', '.join(ns)}" if ns else ""))
            report += [f"  {i}" for i in extra]
            report.append("")
            summary["stages"][name] = {"kind": "lock", "listed": listed, "derived": extra, "namespaces": ns,
                                       "mod_items": sorted(i for i in items if i.split(":")[0] in ns)}
        if gates_off:
            out = ({"restrictions": {"preventTravel": True}, "dimensions": []} if "dimensions" in out
                   else {"restrictions": dict(LOCK), "items": [], "namespaces": []})
        with open(os.path.join(SDIR, name + ".json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump(out, f, indent=2)
            f.write("\n")
    with open(os.path.join(ROOT, "tools", "questgen", "stages-report.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(report) + "\n")
    if not quiet:
        print("\n".join(l for l in report if not l.startswith("  ")))
    return summary

if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    build(sys.argv[1:])
