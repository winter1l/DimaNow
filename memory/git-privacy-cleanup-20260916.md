# Git privacy maintenance - 2026-09-16

Confirmed: the user authorized removing accidental local attachments and real
device connection identifiers from Git history. Local originals and recovery
bundles remain in ignored storage. Read LOCAL-ONLY.md for the connection handoff.

Tracked files use device aliases. CI checks private file paths and device
identifiers, with regression cases for masked diagnostics and intentional test
fixtures. The public source commit changes only tracking policy, checks and
documentation. Public meal contents are preserved; the active immutable image
reference is updated separately because its commit changed during cleanup.

Repository branch and tag rewriting does not prove removal of cached GitHub
objects or pull-request refs. Such residual copies require separate host review.
