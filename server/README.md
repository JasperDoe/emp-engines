# Server layer

Files here are copied on top of the server-side mods by
`tools\build-server.ps1`. They exist only on the server, never in the
client pack.

| File | What it's for |
| --- | --- |
| `server.properties` | Server settings. `allow-flight=true` is required for airships. |
| `jvm-args.txt` | Memory (6–10 GB) and garbage-collector settings. Lower `-Xmx` if the host has less RAM. |
| `start.bat` / `start.sh` | Start the server. On first run they download and install the NeoForge version the pack pins. |

Later this folder also gets the weekly wilderness wipe script, backup
settings and admin-only configs.

## Uploading to a host

Upload the contents of `build\skybound-server-<version>.zip`. On a panel
host, either point the start command at `start.sh`, or pick NeoForge with
the version in `neoforge-version.txt` and upload only `mods`, `config`,
`defaultconfigs`, `kubejs` and `server.properties`.
