#!/usr/bin/env python3
"""
Visual quest book editor for the Empyrean Engines pack, run locally (no Minecraft needed).

    python tools/questedit/serve.py [--port 8765] [--mods <folder>] [--jar <file.jar>] [--no-browser]

Opens http://127.0.0.1:8765 in your browser. The page edits tools/questgen/book.json;
Save writes it (keeping a .bak copy) and rebuilds the FTB Quests files in
pack/config/ftbquests/quests, the same as running build_quests.py.

Items and their icons are read straight out of the mod jars: every jar in mod-files/mods,
your own mods' built jars (mods/*/build/libs), and Minecraft's own resources (found in the
moddev build folder or your .minecraft folder). The index is cached in
tools/questedit/.cache and rebuilt when a jar changes. Nothing leaves your computer; the
server only listens on 127.0.0.1.
"""
import argparse, glob, hashlib, http.server, io, json, os, re, shutil, sys, threading, time
import urllib.parse, webbrowser, zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
CACHE = os.path.join(HERE, ".cache")
sys.path.insert(0, os.path.join(ROOT, "tools", "questgen"))
import build_quests  # noqa: E402

# --------------------------------------------------------------------------
# Finding jars
# --------------------------------------------------------------------------
def find_jars(extra_mods, extra_jars):
    jars = []
    for folder in [os.path.join(ROOT, "mod-files", "mods"), os.path.join(ROOT, "manual-mods")] + extra_mods:
        jars += sorted(glob.glob(os.path.join(folder, "*.jar")))
    for j in sorted(glob.glob(os.path.join(ROOT, "mods", "*", "build", "libs", "*.jar"))):
        if not re.search(r"-(sources|javadoc|slim|dev)\.jar$", j):
            jars.append(j)
    jars += extra_jars
    vanilla = find_vanilla()
    if vanilla:
        jars.insert(0, vanilla)
    elif not extra_jars:
        print("! Minecraft's own items weren't found. Build one of the mods once (tools/build-mods.ps1),"
              " or pass --jar <path to .minecraft/versions/1.21.1/1.21.1.jar>.")
    seen, out = set(), []
    for j in jars:
        k = os.path.normcase(os.path.abspath(j))
        if k not in seen and os.path.isfile(j):
            seen.add(k); out.append(j)
    return out

def find_vanilla():
    pats = [os.path.join(ROOT, "mods", "*", "build", "moddev", "artifacts", "*client-extra*.jar")]
    appdata = os.environ.get("APPDATA")
    if appdata:
        pats.append(os.path.join(appdata, ".minecraft", "versions", "1.21.1", "1.21.1.jar"))
    pats.append(os.path.expanduser("~/.minecraft/versions/1.21.1/1.21.1.jar"))
    pats.append(os.path.expanduser("~/Library/Application Support/minecraft/versions/1.21.1/1.21.1.jar"))
    for p in pats:
        hits = sorted(glob.glob(p))
        if hits:
            return hits[-1]
    return None

# --------------------------------------------------------------------------
# Item index: ids from item models and lang files, names from lang, icons from models
# --------------------------------------------------------------------------
MODEL_RE = re.compile(r"^assets/([^/]+)/models/(item|block)/(.+)\.json$")
TEX_RE = re.compile(r"^assets/([^/]+)/textures/(.+)\.png$")
LANG_RE = re.compile(r"^assets/([^/]+)/lang/en_us\.json$")
TOML_NAME = re.compile(r'^\s*modId\s*=\s*"([^"]+)"[\s\S]*?^\s*displayName\s*=\s*"([^"]+)"', re.M)

def signature(jars):
    h = hashlib.sha1()
    for j in jars:
        st = os.stat(j)
        h.update(f"{j}|{st.st_size}|{int(st.st_mtime)}\n".encode())
    return h.hexdigest()

def open_jar(spec, cache=None):
    """A jar, or a jar inside a jar ("outer.jar!META-INF/jarjar/inner.jar", as bundled mods ship)."""
    if cache is not None and spec in cache:
        return cache[spec]
    if "!" in spec:
        outer, inner = spec.split("!", 1)
        with zipfile.ZipFile(outer) as z:
            z2 = zipfile.ZipFile(io.BytesIO(z.read(inner)))
    else:
        z2 = zipfile.ZipFile(spec)
    if cache is not None:
        cache[spec] = z2
    return z2

def expand(jars):
    """Adds the mods bundled inside each jar (META-INF/jarjar) after it."""
    out = []
    for j in jars:
        out.append(j)
        try:
            with zipfile.ZipFile(j) as z:
                for n in z.namelist():
                    if n.startswith("META-INF/jarjar/") and n.endswith(".jar"):
                        out.append(j + "!" + n)
        except (zipfile.BadZipFile, OSError):
            pass
    return out

def build_index(jars):
    jars = expand(jars)
    models = {}      # "ns:item/x" or "ns:block/x" -> parsed json
    textures = {}    # "ns:block/x" -> jar index
    names = {}
    lang_items = set()   # ids named with an "item." key
    mods = {}
    item_ids = set()
    for ji, jar in enumerate(jars):
        try:
            z = open_jar(jar)
        except (zipfile.BadZipFile, OSError, KeyError):
            print("  skipped (not a zip):", os.path.basename(jar)); continue
        with z:
            for info in z.infolist():
                n = info.filename
                m = MODEL_RE.match(n)
                if m:
                    ns, kind, path = m.groups()
                    try:
                        models[f"{ns}:{kind}/{path}"] = json.loads(z.read(n).decode("utf-8", "replace"))
                    except ValueError:
                        pass
                    if kind == "item" and "/" not in path:
                        item_ids.add(f"{ns}:{path}")
                    continue
                m = TEX_RE.match(n)
                if m:
                    textures.setdefault(f"{m.group(1)}:{m.group(2)}", ji)
                    continue
                m = LANG_RE.match(n)
                if m:
                    try:
                        lang = json.loads(z.read(n).decode("utf-8", "replace"))
                    except ValueError:
                        continue
                    for k, v in lang.items():
                        parts = k.split(".")
                        if len(parts) == 3 and parts[0] in ("item", "block") and isinstance(v, str):
                            iid = f"{parts[1]}:{parts[2]}"
                            if parts[0] == "item":
                                lang_items.add(iid)
                            if parts[0] == "item" or iid not in names:
                                names[iid] = v
                    continue
                if n in ("META-INF/neoforge.mods.toml", "META-INF/mods.toml"):
                    try:
                        for mid, disp in TOML_NAME.findall(z.read(n).decode("utf-8", "replace")):
                            mods.setdefault(mid, disp)
                    except Exception:
                        pass
    mods.setdefault("minecraft", "Minecraft")
    # Items named in a lang file but with no item model (made at runtime) still count.
    for k in lang_items:
        if k.split(":")[0] in mods:
            item_ids.add(k)
    items = []
    for iid in sorted(item_ids):
        ns, path = iid.split(":", 1)
        items.append({"id": iid, "name": names.get(iid) or path.replace("_", " ").title(),
                      "mod": ns, "icon": icon_for(iid, models, textures)})
    return {"items": items, "mods": mods, "textures": textures, "jars": jars}

ICON_KEYS = ("layer0", "all", "side", "front", "top", "end", "texture", "cross", "wool", "plant", "particle")

def icon_for(iid, models, textures):
    ns, path = iid.split(":", 1)
    if f"{ns}:item/{path}" in textures:   # its own flat sprite, if it has one
        return f"{ns}:item/{path}"
    name = f"{ns}:item/{path}"
    tex = {}
    seen = 0
    while name and seen < 8:
        seen += 1
        if ":" not in name:
            name = "minecraft:" + name
        m = models.get(name)
        if not m:
            break
        for k, v in (m.get("textures") or {}).items():
            tex.setdefault(k, v)
        parent = m.get("parent")
        if not parent or parent.startswith("builtin/") or parent.startswith("minecraft:builtin/"):
            break
        name = parent
    def resolve(v, depth=0):
        while isinstance(v, str) and v.startswith("#") and depth < 6:
            v = tex.get(v[1:]); depth += 1
        return v if isinstance(v, str) else None
    choices = [resolve(tex[k]) for k in ICON_KEYS if k in tex] + [resolve(v) for v in tex.values()]
    for t in choices:
        if not t:
            continue
        key = t if ":" in t else "minecraft:" + t
        if key in textures:
            return key
    # Last try: a texture with the item's own name.
    for guess in (f"{ns}:item/{path}", f"{ns}:block/{path}"):
        if guess in textures:
            return guess
    return None

class Index:
    def __init__(self, jars):
        self.jars = jars
        self.ready = threading.Event()
        self.data = None
        self.error = None
        self._zips = {}
        self._lock = threading.Lock()

    def load(self):
        try:
            os.makedirs(CACHE, exist_ok=True)
            sig = signature(self.jars)
            path = os.path.join(CACHE, "items.json")
            if os.path.exists(path):
                with open(path, encoding="utf-8") as f:
                    cached = json.load(f)
                if cached.get("signature") == sig:
                    self.data = cached
            if not self.data:
                t = time.time()
                print(f"Reading items from {len(self.jars)} jars...")
                d = build_index(self.jars)
                d["signature"] = sig
                with open(path, "w", encoding="utf-8") as f:
                    json.dump(d, f)
                self.data = d
                print(f"  {len(d['items'])} items in {time.time() - t:.1f}s")
            else:
                print(f"{len(self.data['items'])} items (cached)")
        except Exception as e:  # keep the editor usable even if the jar scan fails
            self.error = str(e)
            self.data = {"items": [], "mods": {}, "textures": {}, "jars": []}
            print("! Item scan failed:", e)
        self.ready.set()

    def texture(self, tid):
        ji = self.data["textures"].get(tid)
        if ji is None:
            return None
        ns, path = tid.split(":", 1)
        jar = self.data["jars"][ji]
        with self._lock:
            z = open_jar(jar, self._zips)
            try:
                return z.read(f"assets/{ns}/textures/{path}.png")
            except KeyError:
                return None

# --------------------------------------------------------------------------
# HTTP
# --------------------------------------------------------------------------
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
        if url.path == "/api/book":
            with open(build_quests.BOOK, "rb") as f:
                return self.send(200, f.read())
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
        if url.path != "/api/book":
            return self.send(404, {"error": "not found"})
        # Only accept saves from the page itself (another site can't write to your book).
        origin = self.headers.get("Origin", "")
        if origin and not re.match(r"^http://(127\.0\.0\.1|localhost):\d+$", origin):
            return self.send(403, {"ok": False, "message": "refused: not from the editor page"})
        try:
            n = int(self.headers.get("Content-Length", "0"))
            book = json.loads(self.rfile.read(n).decode("utf-8"))
            bad = build_quests.problems(book)
            if bad:
                return self.send(400, {"ok": False, "message": "Not saved:\n" + "\n".join(bad)})
            if os.path.exists(build_quests.BOOK):
                shutil.copyfile(build_quests.BOOK, build_quests.BOOK + ".bak")
            tmp = build_quests.BOOK + ".tmp"
            with open(tmp, "w", encoding="utf-8", newline="\n") as f:
                json.dump(book, f, indent=1, ensure_ascii=False)
                f.write("\n")
            os.replace(tmp, build_quests.BOOK)
            msg = build_quests.build(book)
            return self.send(200, {"ok": True, "message": "Saved. " + msg + "."})
        except Exception as e:
            return self.send(500, {"ok": False, "message": f"Save failed: {e}"})

def main():
    global INDEX
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--port", type=int, default=8765)
    ap.add_argument("--mods", action="append", default=[], help="another folder of mod jars to read items from")
    ap.add_argument("--jar", action="append", default=[], help="another jar to read items from")
    ap.add_argument("--no-browser", action="store_true")
    a = ap.parse_args()
    INDEX = Index(find_jars(a.mods, a.jar))
    threading.Thread(target=INDEX.load, daemon=True).start()
    server = None
    for port in range(a.port, a.port + 10):   # next port along if that one is taken
        try:
            server = http.server.ThreadingHTTPServer(("127.0.0.1", port), Handler)
            break
        except OSError:
            continue
    if server is None:
        sys.exit(f"Couldn't open a port between {a.port} and {a.port + 9}.")
    url = f"http://127.0.0.1:{port}/"
    print(f"Quest editor: {url}  (Ctrl+C to stop)")
    if not a.no_browser:
        threading.Timer(0.6, lambda: webbrowser.open(url)).start()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopped.")

if __name__ == "__main__":
    main()
