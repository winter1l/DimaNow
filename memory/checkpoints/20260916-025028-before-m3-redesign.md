# Checkpoint — Git privacy history cleanup verified — 2026-09-16

## The story so far
Confirmed: D-080 authorized history cleanup is complete for local Git and all14 public branches/5tags. Known device identifiers and two accidental local screenshot objects are absent from verified reachable history; originals and actual device settings remain ignored locally. Public app sources and authorized meal content are preserved. CI34996317038 and deploy-only34995690653 succeeded. Main protection is identical to its original configuration and the publish workflow is active. Full evidence: git-privacy-cleanup-20260916.md.

## Decided
User approved rewriting commit IDs and force updates. Keep connection data in .local/device-connections.json and use aliases in tracked documents. Private recovery bundles remain in .local/history-cleanup-20260916. Local unpublished app commits remain local; do not overwrite them with public main or restore old refs.

## Waiting on the user
No decision needed for the completed repository cleanup. GitHub-managed closed PR heads1/7/9 and cached commit access remain. A private Support request draft is ready but has not been sent; host-side eligibility and erasure are unconfirmed.

## Next first action
For a GitHub residual follow-up, read .local/history-cleanup-20260916/support-request.md; for device work, read memory/LOCAL-ONLY.md.

## Tried
Git-filter-repo skips standalone tree refs; those were cleaned separately and checked. Windows long refs required hashed mirror aliases. Main protection initially rejected the atomic push; scoped force permission was restored after the approved rewrite. GitHub test command inherited an intentional check-ignore non-match; explicit success and exact expected code fixed it, and final CI passed.
