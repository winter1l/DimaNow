# Local device connection information

Before device work, read `.local/device-connections.json` from the repository root.
It contains last-known connection details and historical endpoints for this PC.
Verify the connection before use; historical entries are not fallback targets to
probe automatically. This file is intentionally ignored by Git and is not
available in another clone unless the user transfers it privately.

Use device aliases in tracked documents and command examples. Never copy endpoint
values, device serials, or private connection details into tracked files or logs.
Keep captures and diagnostic output in ignored `artifacts/` directories.

## Active phone sessions — confirmed 2026-09-21
If this PC's Tailscale backend is unavailable or NoState, launch the installed Tailscale desktop application directly, then verify backend and device connectivity. The user authorizes this; phone Tailscale remains their responsibility.
Keep the phone awake while actively operating it. Snapshot original screen timeout and stay-on settings in ignored local session state, wake without bypassing the lock, and restore temporary settings afterward.

## Private memory values — 2026-09-30 (D-092)
Placeholders in tracked memory (`[classroom-A]`, `[figma-file]`, `[worker-host]`, `[security-scan]`, P-01..P-05) resolve in ignored `.local/memory-private/SENSITIVE.md`. Never copy those values into tracked files.
