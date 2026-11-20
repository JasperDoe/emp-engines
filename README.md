# Empyrean Engines

A Create + Create Aeronautics server pack for NeoForge 1.21.1, built
with [packwiz](https://packwiz.infra.link/). Design doc:
https://claude.ai/artifact/Xnt7M9n6jWAfDeMRLS6Dat

## Layout

| Folder | What lives there | Goes to |
| --- | --- | --- |
| `pack\` | The packwiz pack: mod entries, `config`, `defaultconfigs`, `kubejs`, `resourcepacks` | Client and server (client-only mods are filtered out of the server) |
| `server\` | Server layer: `server.properties`, start scripts, JVM args | Server only |
| `tools\` | Build and test scripts (PowerShell) | Nobody, they run on your PC |
| `docs\` | Testing workflow and checklists | Nobody |
| `build\` | Generated server pack, test server, client exports (not in git) | — |
| `mod-files\` | The actual mod .jar files, downloaded by `download-mods.ps1` (not in git) | — |
| `mods.txt` | The mod list `setup-mods.ps1` reads | — |

## First run

Needs Windows PowerShell, Git and Java 21 ([Temurin 21](https://adoptium.net)).
If scripts are blocked, run once: `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`.

```powershell
.\tools\setup-mods.ps1     # adds every mod in mods.txt (+ dependencies)
.\tools\download-mods.ps1  # downloads the actual .jar files into mod-files\mods
.\tools\test-server.ps1    # builds the server pack, boots it, writes compat-report.txt
```

`setup-mods.ps1` downloads packwiz into `tools\bin` if it isn't installed.

## Everyday commands

| Task | Command |
| --- | --- |
| Add one mod | add its slug to `mods.txt`, run `.\tools\setup-mods.ps1` |
| Remove a mod | `tools\bin\packwiz.exe remove <slug>` inside `pack\`, delete its line in `mods.txt` |
| Update all mods | `tools\bin\packwiz.exe update --all` inside `pack\`, then boot test |
| Boot test | `.\tools\test-server.ps1` |
| Build server pack | `.\tools\build-server.ps1` → `build\emp-engines-server-<version>.zip` |
| Play it yourself | `.\tools\install-client.ps1` (copies the pack into your CurseForge profile "Empyrean Engines") |
| Build client pack | `.\tools\export-client.ps1` → `.zip` (CurseForge) and `.mrpack` |

See `docs\TESTING.md` for the full test workflow and `COMPATIBILITY.md` for
known version requirements and conflicts.
