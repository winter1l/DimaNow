# Checkpoint — Local tracking privacy fix verified — 2026-09-16

## The story so far
D080 local cleanup verified and staged. Two original JPGs preserved locally, removed from index.8 address occurrences in5 docs anonymized. Ignored .local/device-connections.json retains actual last-known/historical values; future sessions read memory/LOCAL-ONLY.md first. Exclusion policy and trackedtext/privatepath checker strengthened; CI regression31 assertions passes, actual checker and staged diffcheck pass. No trackedignored conflicts. No commit/push/history rewrite; old local photo commits and public metadata remain in history.

## Decided
User explicitly requires ignored local connection handoff. Keep reviewed artifactdocs/sharedgeometry and original photos. Never copy values into trackedfiles.

## Waiting on the user
Whether to rewrite local screenshot-bearing commits and public GitHub history; rewritten hashes/force-push require explicit reviewed scope.

## Next first action
Read the user's history-cleanup decision; if no decision, do not publish or rewrite commits. For device work read memory/LOCAL-ONLY.md.

## Tried
Sandbox index write required approved escalation. Guard initially flagged intentional CGNAT fixture; exact fixture/value/noport exemption tested. No broad testfile bypass.
