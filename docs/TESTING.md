# Testing workflow

Every change goes through the stages in order. Nothing reaches the real
server without passing staging.

| Stage | Where | When | Pass means |
| --- | --- | --- | --- |
| 1 Local dev | Singleplayer instance in Prism or CurseForge | While writing scripts and configs | It works in your own world |
| 2 Boot test | `tools\test-server.ps1` on your PC | After **every** mod added, removed or updated | `compat-report.txt` says PASS with no mixin failures |
| 3 Staging | A separate server, fresh world, 2–3 testers | Before each release | Every box in the checklist below ticked |
| 4 Production | The real server | Tagged releases only | Backup taken first, changelog posted |

## Stage 2 rules

- **One change at a time.** Add one mod (or a small related group), run
  the boot test, commit. If a run fails you know what caused it.
- **Finding a culprit in a big batch:** move half the new entries out of
  `mods.txt`, remove their files from `pack\mods\`, re-test, and keep halving.
- Commit only passing states: `git commit -m "Add create-tiers (boot test PASS)"`.

## Stage 3 checklist (staging)

Core
- [ ] Server boots, two players join, no client crashes
- [ ] An airship assembles, flies and lands
- [ ] Ship building and repair works above Y=120; normal building above Y=120 is blocked
- [ ] Ship-mounted drills can't break sky structures

Systems
- [ ] Research bench grants a stage; locked recipes can't be crafted by hand or by Create machines (including mechanical crafters)
- [ ] Silent Gear conversion: vanilla tool and armour recipes give Silent Gear items
- [ ] Foundry forms, melts ore at 2x, casts Silent Gear parts
- [ ] High-tensile shafts run above 256 RPM; normal shafts still break above 256
- [ ] Portal locks hold until the quest flag is set
- [ ] Sky Gate warps a ship to the wilderness and back, 10 times in a row
- [ ] Weekly wipe with a ship still in the wilderness: nothing corrupts
- [ ] Mob farm and fake-player kill cheese is blocked

Performance
- [ ] `/spark tps` stays at 20 with several ships flying and a factory running
- [ ] `/spark profiler` for 5 minutes shows no single mod above 20% of tick time

## Releases

1. Bump `version` in `pack\pack.toml` and add a `CHANGELOG.md` entry.
2. `.\tools\build-server.ps1` and `.\tools\export-client.ps1`.
3. Tag: `git tag v0.2.0` and push.
4. Back up the production world, upload the server zip, share the client zip.
