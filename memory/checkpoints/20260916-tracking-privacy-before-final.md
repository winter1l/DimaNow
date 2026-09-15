# Checkpoint — Local tracking privacy cleanup — 2026-09-16

## The story so far
D080 applied local ignore and sanitized8 addresses in5 docs; two JPGs removed from index but originals retained. Ignored .local/device-connections.json stores actual endpoints; read memory/LOCAL-ONLY.md before phone work. Checker expanded to trackedtext/privatepaths; current scan passes. Agenttracking_guard_fix finishing testscript; CI includes it. Need finalstage/test and report localvsunchangedhistory boundary. No remote publication/history rewrite.

## Decided
User wants connection information available to other sessions withoutGit tracking. Original screenshots stayonPC; reviewed4artifactdocs/sharedgeometry retained explicitly.

## Waiting on the user
Historical/remote rewrite scope remains tobeapproved after concrete localfix is verified.

## Next first action
Get tracking_guard_fix final regression result, stage onlycleanupfiles and rerun checker; report remaining pastcommit/publichistory issue.

## Tried
Initial git rm --cached blocked bysandbox indexlock access; authorized escalation succeeded. Initial hardenedchecker flagged synthetic CGNAT boundaryfixture; exact fixture/value/noport exception added, no broad test bypass.
