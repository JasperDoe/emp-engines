#!/usr/bin/env python3
"""
Builds the Empyrean Engines quest book (FTB Quests) from tools/questgen/book.json.

book.json is the source: edit it with the visual editor (python tools/questedit/serve.py)
or by hand, then run this. The book is a guide and tutorial: no costs, and the only rewards are the
few Sigils of Socketing listed under "rewards" (sigils come only from quests and blood moons).
Research is done separately (the research bench); each age chapter opens when the player
has learned that age, through a "gamestage" task that ticks itself when Chapters grants it.

    python tools/questgen/build_quests.py [--check items.json]

Quest and task IDs are derived from stable keys, so re-running keeps progress (don't rename
a key once players have done the quest). This overwrites config/ftbquests/quests, so edits
made in game with the FTB Quests editor are lost unless they're copied into book.json.
"""
import hashlib, json, os, shutil, sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
QDIR = os.path.join(ROOT, "pack", "config", "ftbquests", "quests")
BOOK = os.path.join(os.path.dirname(os.path.abspath(__file__)), "book.json")

# --------------------------------------------------------------------------
# SNBT writer
# --------------------------------------------------------------------------
class L(int):
    """long"""
class D(float):
    """double"""

def _key(k):
    return k if all(c.isalnum() or c in "_-+." for c in k) else json.dumps(k)

def snbt(v, ind=0):
    t = "\t"
    if isinstance(v, bool):
        return "true" if v else "false"
    if isinstance(v, L):
        return f"{int(v)}L"
    if isinstance(v, float):
        return f"{float(v)}d"
    if isinstance(v, int):
        return str(v)
    if isinstance(v, str):
        return json.dumps(v, ensure_ascii=False)
    if isinstance(v, list):
        if not v:
            return "[ ]"
        return "[\n" + "\n".join(t * (ind + 1) + snbt(x, ind + 1) for x in v) + "\n" + t * ind + "]"
    if isinstance(v, dict):
        if not v:
            return "{ }"
        inner = "\n".join(t * (ind + 1) + f"{_key(k)}: " + snbt(v[k], ind + 1) for k in sorted(v))
        return "{\n" + inner + "\n" + t * ind + "}"
    raise TypeError(type(v))

def qid(key):
    h = hashlib.sha1(("skybound:" + key).encode()).hexdigest()[:16].upper()
    return h if int(h, 16) > 1 else qid(key + "_")

# --------------------------------------------------------------------------
# book.json -> FTB Quests data
# --------------------------------------------------------------------------
def load_book(path=BOOK):
    with open(path, encoding="utf-8") as f:
        return json.load(f)

def task_data(t):
    ty = t["type"]
    if ty == "item":
        d = {"type": "item", "item": {"id": t["item"], "count": 1}}
        if int(t.get("count", 1)) != 1:
            d["count"] = L(int(t["count"]))
        return d
    if ty == "checkmark":
        return {"type": "checkmark"}
    if ty == "structure":
        return {"type": "structure", "structure": t["structure"]}
    if ty == "dimension":
        return {"type": "dimension", "dimension": t["dimension"]}
    if ty == "gamestage":
        return {"type": "gamestage", "stage": t["stage"], "team_stage": bool(t.get("team_stage", False))}
    if ty == "advancement":
        return {"type": "advancement", "advancement": t["advancement"], "criterion": t.get("criterion", "")}
    raise ValueError("unknown task type " + ty)

def quest_data(q):
    d = {"x": D(q["x"]), "y": D(q["y"])}
    if q.get("icon"): d["icon"] = {"id": q["icon"], "count": 1}
    if q.get("shape"): d["shape"] = q["shape"]
    if q.get("size") not in (None, 1, 1.0): d["size"] = D(q["size"])
    if q.get("invisible"): d["invisible"] = True
    if q.get("hide_until_deps"): d["hide_until_deps_complete"] = True
    if q.get("optional"): d["optional"] = True
    return d

def problems(book):
    """Things that would break the book: returned as a list of messages (empty = fine)."""
    out, keys = [], {}
    files = set()
    groups = {g["key"] for g in book.get("groups", [])}
    for ch in book["chapters"]:
        if ch["file"] in files: out.append(f"two chapters use the file name '{ch['file']}'")
        files.add(ch["file"])
        if ch.get("group") and ch["group"] not in groups: out.append(f"chapter '{ch['title']}' is in unknown group '{ch['group']}'")
        for q in ch["quests"]:
            if q["key"] in keys: out.append(f"quest key '{q['key']}' is used twice ({keys[q['key']]} and {ch['file']})")
            keys[q["key"]] = ch["file"]
            if not q.get("tasks"): out.append(f"quest '{q['key']}' has no tasks")
    for ch in book["chapters"]:
        for q in ch["quests"]:
            for dep in q.get("dependencies", []):
                if dep not in keys: out.append(f"quest '{q['key']}' depends on missing quest '{dep}'")
                if dep == q["key"]: out.append(f"quest '{q['key']}' depends on itself")
    return out

def build(book=None):
    book = book or load_book()
    bad = problems(book)
    if bad:
        raise ValueError("book.json has problems:\n  " + "\n  ".join(bad))
    chapters = book["chapters"]
    keys = {q["key"]: qid("quest:" + q["key"]) for ch in chapters for q in ch["quests"]}
    lang = {"file.0000000000000001.title": book.get("title", "Quests")}
    groups = []
    for g in book.get("groups", []):
        gid = qid("group:" + g["key"])
        groups.append({"id": gid})
        lang[f"chapter_group.{gid}.title"] = g["title"]

    if os.path.isdir(QDIR):
        shutil.rmtree(QDIR)
    for d in ("chapters", "reward_tables", "lang"):
        os.makedirs(os.path.join(QDIR, d))

    for idx, ch in enumerate(chapters):
        fn = ch["file"]
        cid = qid("chapter:" + fn)
        lang[f"chapter.{cid}.title"] = ch["title"]
        if ch.get("subtitle"):
            lang[f"chapter.{cid}.chapter_subtitle"] = [ch["subtitle"]]
        qs = []
        for q in ch["quests"]:
            d = quest_data(q)
            d["id"] = keys[q["key"]]
            if q.get("dependencies"):
                d["dependencies"] = [keys[k] for k in q["dependencies"]]
            d["tasks"] = []
            for n, t in enumerate(q["tasks"]):
                t = task_data(t); t["id"] = qid(f"task:{q['key']}:{n}"); d["tasks"].append(t)
            if q.get("rewards"):
                d["rewards"] = []
                for n, r in enumerate(q["rewards"]):
                    rd = {"id": qid(f"reward:{q['key']}:{n}"), "type": "item", "item": {"id": r["item"], "count": int(r.get("count", 1))}}
                    d["rewards"].append(rd)
            qs.append(d)
            lang[f"quest.{d['id']}.title"] = q["title"]
            if q.get("subtitle"):
                lang[f"quest.{d['id']}.quest_subtitle"] = q["subtitle"]
            if q.get("description"):
                lang[f"quest.{d['id']}.quest_desc"] = q["description"]
        chap = {"id": cid, "filename": fn, "group": qid("group:" + ch["group"]) if ch.get("group") else "",
                "icon": {"id": ch.get("icon") or "minecraft:book", "count": 1}, "order_index": idx, "default_quest_shape": "",
                "default_hide_dependency_lines": False, "quest_links": [], "quests": qs}
        with open(os.path.join(QDIR, "chapters", fn + ".snbt"), "w", encoding="utf-8", newline="\n") as f:
            f.write(snbt(chap) + "\n")

    data = {"version": 13, "default_reward_team": False, "default_consume_items": False,
            "default_autoclaim_rewards": "disabled", "default_quest_shape": "circle",
            "default_quest_disable_jei": False, "drop_loot_crates": False, "disable_gui": False,
            "grid_scale": D(0.5), "pause_game": False, "progression_mode": "flexible",
            "lock_message": "", "emergency_items_cooldown": 300}
    with open(os.path.join(QDIR, "data.snbt"), "w", encoding="utf-8", newline="\n") as f:
        f.write(snbt(data) + "\n")
    with open(os.path.join(QDIR, "chapter_groups.snbt"), "w", encoding="utf-8", newline="\n") as f:
        f.write(snbt({"chapter_groups": groups}) + "\n")
    open(os.path.join(QDIR, "reward_tables", ".gitkeep"), "w").close()
    with open(os.path.join(QDIR, "lang", "en_us.snbt"), "w", encoding="utf-8", newline="\n") as f:
        f.write(snbt(lang) + "\n")
    n = sum(len(c["quests"]) for c in chapters)
    msg = f"Wrote {len(chapters)} chapters, {n} quests"
    print(msg)
    return msg

def verify_ids(path, book=None):
    book = book or load_book()
    known = set()
    for v in json.load(open(path)).values():
        known.update(i for i, _ in v)
    nss = {i.split(":")[0] for i in known}
    want = set()
    for ch in book["chapters"]:
        if ch.get("icon"): want.add(ch["icon"])
        for q in ch["quests"]:
            want.update(t["item"] for t in q["tasks"] if t["type"] == "item")
            if q.get("icon"):
                want.add(q["icon"])
    bad = sorted(i for i in want if i.split(":")[0] in nss and i not in known)
    print("Unknown item ids:", bad or "none")
    return not bad

if __name__ == "__main__":
    build()
    if "--check" in sys.argv:
        sys.exit(0 if verify_ids(sys.argv[sys.argv.index("--check") + 1]) else 1)
