# Local device connection information

Before device work, read `.local/device-connections.json` from the repository root.
It contains last-known connection details and historical endpoints for this PC.
Verify the connection before use; historical entries are not fallback targets to
probe automatically. This file is intentionally ignored by Git and is not
available in another clone unless the user transfers it privately.

Use device aliases in tracked documents and command examples. Never copy endpoint
values, device serials, or private connection details into tracked files or logs.
Keep captures and diagnostic output in ignored `artifacts/` directories.
