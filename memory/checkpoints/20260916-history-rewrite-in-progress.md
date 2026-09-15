# Checkpoint — Authorized history cleanup in progress — 2026-09-16

## The story so far
Confirmed: D-080 history rewrite and force updates explicitly approved. Private backups and scripts are under ignored .local/history-cleanup-20260916. Observed: prepared mirror has 122 commit trees compared, 14 standalone Codex trees cleaned and zero remaining known private identifiers or screenshot objects among 2220 reachable objects. Original working repository refs and GitHub refs are not yet updated. The publish-data workflow is temporarily disabled to prevent reprocessing an existing meal image during force push.

## Decided
Keep actual device connections in ignored .local/device-connections.json. Preserve local app changes and authorized public meal photos. Update active meal source URL after its submission commit changes. Exact remote ref leases are required.

## Waiting on the user
None for the authorized rewrite. GitHub Support messages are not sent automatically.

## Next first action
Read .local/history-cleanup-20260916/rewrite-verification.json, finish narrow remote protection/data commits, then compare remote leases before atomic push.

## Tried
Git-filter-repo skips standalone tree refs; a separate tree rewrite compared all commit trees and cleaned those snapshots. Windows long ref paths required hashed local mirror aliases.
