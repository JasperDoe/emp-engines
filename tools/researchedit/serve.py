#!/usr/bin/env python3
"""
Visual research tree editor for EXP Research (the Research Table's tree), run locally like the quest editor.

    python tools/researchedit/serve.py [--port 8775] [--mods <folder>] [--jar <file.jar>] [--no-browser]

Opens http://127.0.0.1:8775 in your browser. The page edits the research tabs and nodes in
pack/kubejs/data/emp_engines/expresearch/ (category/*.json and research/*.json). Save writes every file,
removes the ones you deleted, and keeps a copy of the old files in tools/researchedit/.backup/.

The Stages view edits the stage locks: what each stage locks (materials, tags, whole mods, exceptions) is kept
in tools/questgen/stages.json, and Save reruns tools/questgen/build_stages.py over the mod jars to write
pack/kubejs/data/emp_engines/chapters/stages/*.json, locking everything crafted from a locked material too.

Items and icons come from the same jar scan as the quest editor (tools/questedit), sharing its cache.
Nothing leaves your computer; the server only listens on 127.0.0.1.
"""
import argparse, glob, http.server, importlib.util, json, os, re, shutil, sys, threading, time
import urllib.parse, webbrowser

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
NS = "emp_engines"
DATA = os.path.join(ROOT, "pack", "kubejs", "data", NS)
CAT_DIR = os.path.join(DATA, "expresearch", "category")
NODE_DIR = os.path.join(DATA, "expresearch", "research")
STAGE_DIR = os.path.join(DATA, "chapters", "stages")
BACKUP = os.path.join(HERE, ".backup")

sys.path.insert(0, os.path.join(ROOT, "tools", "questgen"))
import build_stages  # noqa: E402

JAR_FOLDERS = [os.path.join(ROOT, "mod-files", "mods"), os.path.join(ROOT, "manual-mods"), os.path.join(ROOT, "pack", "mods")]
SUMMARY = os.path.join(HERE, ".cache", "stages-summary.json")

# The quest editor's jar scan (item list and icons), loaded from its file so both editors share one cache.
_spec = importlib.util.spec_from_file_location("questedit_serve", os.path.join(ROOT, "tools", "questedit", "serve.py"))
qe = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(qe)

ID_RE = re.compile(r"^[a-z0-9_]+$")
RL_RE = re.compile(r"^[a-z0-9_.-]+:[a-z0-9_./-]+$")


def read_dir(folder):
    out = {}
    for path in sorted(glob.glob(os.path.join(folder, "*.json"))):
        name = os.path.splitext(os.path.basename(path))[0]
        try:
            with open(path, encoding="utf-8") as f:
                d = json.load(f)
        except ValueError:
            continue
        if isinstance(d, dict) and not d.get("retired"):
            out[f"{NS}:{name}"] = d
    return out


def stages():
    order, st = build_stages.load_stages()
    return [{"id": f"{NS}:{n}", "kind": st[n].get("kind", "lock"), "dimensions": st[n].get("dimensions", [])} for n in order]


class RecipeScan:
    """build_stages' recipe scan of the jars, kept between saves and redone when a jar changes."""
    def __init__(self):
        self.sig, self.data, self.lock = None, None, threading.Lock()

    def get(self):
        folders = [f for f in JAR_FOLDERS if os.path.isdir(f)]
        jars = sorted(j for f in folders for j in glob.glob(os.path.join(f, "*.jar")))
        sig = qe.signature(jars)
        with self.lock:
            if sig != self.sig:
                self.data, self.sig = build_stages.scan(folders), sig
            return folders, self.data


RECIPES = RecipeScan()
STAGE_RE = re.compile(r"^[a-z0-9_]+$")


def stage_problems(cfg):
    bad = []
    for name, s in cfg.get("stages", {}).items():
        if not STAGE_RE.match(name):
            bad.append(f"Stage {name!r}: use lowercase letters, digits and _ only")
        if s.get("kind") not in ("lock", "dimension"):
            bad.append(f"Stage {name}: unknown kind")
        for i in s.get("items", []) + s.get("except", []):
            if not RL_RE.match(i):
                bad.append(f"Stage {name}: {i!r} isn't an item id")
            elif i.startswith("minecraft:"):
                bad.append(f"Stage {name}: vanilla items ({i}) are never locked")
        for t in s.get("tags", []):
            if not RL_RE.match(t.lstrip("#")):
                bad.append(f"Stage {name}: {t!r} isn't a tag id")
        for ns in s.get("namespaces", []):
            if not ID_RE.match(ns.replace(".", "_").replace("-", "_")) or ns == "minecraft":
                bad.append(f"Stage {name}: {ns!r} can't be locked as a whole mod")
    return bad


def save_stages(cfg):
    clean = {"order": [n for n in cfg["order"] if n in cfg["stages"]], "stages": {}}
    for name, s in cfg["stages"].items():
        if s.get("kind") == "dimension":
            clean["stages"][name] = {"kind": "dimension", "dimensions": list(s.get("dimensions", []))}
        else:
            clean["stages"][name] = {"kind": "lock", "items": list(dict.fromkeys(s.get("items", []))),
                                     "tags": list(dict.fromkeys(t.lstrip("#") for t in s.get("tags", []))),
                                     "namespaces": list(dict.fromkeys(s.get("namespaces", []))),
                                     "except": list(dict.fromkeys(s.get("except", [])))}
    if os.path.exists(build_stages.STAGES_JSON):
        with open(build_stages.STAGES_JSON, encoding="utf-8") as f:
            if json.load(f).get("gates_off"):
                clean = {"gates_off": True, **clean}
        os.makedirs(BACKUP, exist_ok=True)
        shutil.copyfile(build_stages.STAGES_JSON, os.path.join(BACKUP, time.strftime("stages-%Y%m%d-%H%M%S.json")))
    tmp = build_stages.STAGES_JSON + ".tmp"
    with open(tmp, "w", encoding="utf-8", newline="\n") as f:
        json.dump(clean, f, indent=2)
        f.write("\n")
    os.replace(tmp, build_stages.STAGES_JSON)
    folders, data = RECIPES.get()
    summary = build_stages.build(folders, data=data, quiet=True)
    os.makedirs(os.path.dirname(SUMMARY), exist_ok=True)
    with open(SUMMARY, "w", encoding="utf-8") as f:
        json.dump(summary, f)
    return summary


def problems(doc):
    bad = []
    cats, nodes = doc.get("categories", {}), doc.get("nodes", {})
    for cid, c in cats.items():
        if not cid.startswith(NS + ":") or not ID_RE.match(cid.split(":", 1)[1]):
            bad.append(f"Tab id {cid!r}: use lowercase letters, digits and _ only")
        if not str(c.get("title", "")).strip():
            bad.append(f"Tab {cid}: no title")
        if not RL_RE.match(str(c.get("icon", ""))):
            bad.append(f"Tab {cid}: no icon")
    for nid, n in nodes.items():
        if not nid.startswith(NS + ":") or not ID_RE.match(nid.split(":", 1)[1]):
            bad.append(f"Research id {nid!r}: use lowercase letters, digits and _ only")
        if n.get("category") not in cats:
            bad.append(f"Research {nid}: its tab {n.get('category')!r} doesn't exist")
        if not str(n.get("title", "")).strip():
            bad.append(f"Research {nid}: no title")
        if not RL_RE.match(str(n.get("icon", ""))):
            bad.append(f"Research {nid}: no icon")
        for p in n.get("parents", []):
            if p not in nodes:
                bad.append(f"Research {nid}: needs {p}, which doesn't exist")
        for s in n.get("samples", []):
            if not RL_RE.match(str(s.get("item", ""))):
                bad.append(f"Research {nid}: a sample has no item")
    # no loops
    state = {}

    def visit(k, path):
        if state.get(k) == 1:
            bad.append("Loop in the tree: " + " -> ".join(path + [k]))
            return
        if state.get(k) == 2:
            return
        state[k] = 1
        for p in nodes.get(k, {}).get("parents", []):
            if p in nodes:
                visit(p, path + [k])
        state[k] = 2

    for k in nodes:
        visit(k, [])
    return bad


def clean_node(n):
    out = {"category": n["category"], "title": n["title"].strip()}
    if n.get("description", "").strip():
        out["description"] = n["description"].strip()
    out["icon"] = n["icon"]
    out["x"] = int(n.get("x", 0))
    out["y"] = int(n.get("y", 0))
    if n.get("parents"):
        out["parents"] = list(n["parents"])
    out["xp"] = max(0, int(n.get("xp", 0)))
    samples = []
    for s in n.get("samples", []):
        e = {"item": s["item"], "count": max(1, int(s.get("count", 1)))}
        if s.get("keep"):
            e["keep"] = True
        samples.append(e)
    if samples:
        out["samples"] = samples
    if n.get("stages"):
        out["stages"] = list(n["stages"])
    return out


def write_all(doc):
    stamp = time.strftime("%Y%m%d-%H%M%S")
    dest = os.path.join(BACKUP, stamp)
    for folder, sub in ((CAT_DIR, "category"), (NODE_DIR, "research")):
        if os.path.isdir(folder):
            shutil.copytree(folder, os.path.join(dest, sub), dirs_exist_ok=True)
    wrote = 0
    for folder, entries, clean in ((CAT_DIR, doc["categories"], lambda c: {"title": c["title"].strip(), "icon": c["icon"], "order": int(c.get("order", 0))}),
                                   (NODE_DIR, doc["nodes"], clean_node)):
        os.makedirs(folder, exist_ok=True)
        keep = set()
        for rid, d in entries.items():
            name = rid.split(":", 1)[1]
            keep.add(name + ".json")
            tmp = os.path.join(folder, name + ".json.tmp")
            with open(tmp, "w", encoding="utf-8", newline="\n") as f:
                json.dump(clean(d), f, indent=2, ensure_ascii=False)
                f.write("\n")
            os.replace(tmp, os.path.join(folder, name + ".json"))
            wrote += 1
        for path in glob.glob(os.path.join(folder, "*.json")):
            if os.path.basename(path) not in keep:
                os.remove(path)
    return f"Saved {len(doc['categories'])} tabs and {len(doc['nodes'])} research (old files backed up in tools/researchedit/.backup/{stamp})"


INDEX = None


class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        pass

    def send(self, code, body, ctype="application/json; charset=utf-8", cache=False):
        if isinstance(body, (dict, list)):
            body = json.dumps(body).encode("utf-8")
        elif isinstance(body, str):
            body = body.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "max-age=86400" if cache else "no-store")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        url = urllib.parse.urlparse(self.path)
        q = urllib.parse.parse_qs(url.query)
        if url.path in ("/", "/index.html"):
            with open(os.path.join(HERE, "editor.html"), "rb") as f:
                return self.send(200, f.read(), "text/html; charset=utf-8")
        if url.path == "/api/research":
            return self.send(200, {"categories": read_dir(CAT_DIR), "nodes": read_dir(NODE_DIR), "stages": stages(), "ns": NS})
        if url.path == "/api/stages":
            order, st = build_stages.load_stages()
            summary = None
            if os.path.exists(SUMMARY):
                with open(SUMMARY, encoding="utf-8") as f:
                    summary = json.load(f)
            return self.send(200, {"order": order, "stages": st, "summary": summary})
        if url.path == "/api/items":
            INDEX.ready.wait()
            d = INDEX.data
            return self.send(200, {"items": d["items"], "mods": d["mods"], "jars": len(d["jars"]), "error": INDEX.error})
        if url.path == "/api/tex":
            INDEX.ready.wait()
            png = INDEX.texture(q.get("id", [""])[0])
            if png is None:
                return self.send(404, b"", "image/png")
            return self.send(200, png, "image/png", cache=True)
        self.send(404, {"error": "not found"})

    def do_POST(self):
        url = urllib.parse.urlparse(self.path)
        if url.path not in ("/api/research", "/api/stages"):
            return self.send(404, {"error": "not found"})
        origin = self.headers.get("Origin", "")
        if origin and not re.match(r"^http://(127\.0\.0\.1|localhost):\d+$", origin):
            return self.send(403, {"ok": False, "message": "refused: not from the editor page"})
        try:
            n = int(self.headers.get("Content-Length", "0"))
            doc = json.loads(self.rfile.read(n).decode("utf-8"))
            if url.path == "/api/stages":
                bad = stage_problems(doc)
                if bad:
                    return self.send(400, {"ok": False, "message": "Stages not saved:\n" + "\n".join(bad)})
                t = time.time()
                summary = save_stages(doc)
                n_items = sum(len(v.get("listed", [])) + len(v.get("derived", [])) for v in summary["stages"].values())
                return self.send(200, {"ok": True, "summary": summary,
                                       "message": f"Stage locks rebuilt from {summary['recipes']} recipes in {time.time() - t:.0f}s: "
                                                  f"{n_items} items locked by material, plus whole mods. In game: /reload."})
            bad = problems(doc)
            if bad:
                return self.send(400, {"ok": False, "message": "Not saved:\n" + "\n".join(bad)})
            return self.send(200, {"ok": True, "message": write_all(doc)})
        except Exception as e:
            return self.send(500, {"ok": False, "message": f"Save failed: {e}"})


def main():
    global INDEX
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--port", type=int, default=8775)
    ap.add_argument("--mods", action="append", default=[], help="another folder of mod jars to read items from")
    ap.add_argument("--jar", action="append", default=[], help="another jar to read items from")
    ap.add_argument("--no-browser", action="store_true")
    a = ap.parse_args()
    INDEX = qe.Index(qe.find_jars(a.mods, a.jar))
    threading.Thread(target=INDEX.load, daemon=True).start()
    server = None
    for port in range(a.port, a.port + 10):
        try:
            server = http.server.ThreadingHTTPServer(("127.0.0.1", port), Handler)
            break
        except OSError:
            continue
    if server is None:
        sys.exit(f"Couldn't open a port between {a.port} and {a.port + 9}.")
    url = f"http://127.0.0.1:{port}/"
    print(f"Research editor: {url}  (Ctrl+C to stop)")
    if not a.no_browser:
        threading.Timer(0.6, lambda: webbrowser.open(url)).start()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopped.")


if __name__ == "__main__":
    main()
