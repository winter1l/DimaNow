# Git privacy maintenance — 2026-09-16

## Confirmed scope

D-080 authorizes removing accidental local phone screenshots and real device
identifiers from historical commits, including force updates of published refs.
Keep originals, recovery bundles and actual connection settings in ignored local
storage. Read `LOCAL-ONLY.md` before device work.

## Observed verification

- Two personal JPGs existed only in unpublished local commits. Both originals
  remain on disk; neither object remains in the working repository's Git store
  after ref rewriting, reflog expiry and garbage collection.
- Historical text contained three distinct real device identifiers. All 122
  commit trees were compared against only the intended removal/replacement;
  14 standalone Codex snapshot trees also required cleaning.
- Published 14 branches and five tags with exact expected ref leases in one
  atomic push. Freshly fetched public branches/tags contained 1,750 reachable
  objects and zero known private findings. After the CI follow-up, the final
  public fetch covered 1,754 objects and local verification covered 2,199 objects,
  both with zero findings before final documentation commits.
- Public source change `1570bc55a5d6cb1514777feb2c3f2587e1975990` adds only tracking
  policy, checks and documentation. App, pipeline and Worker source bytes match
  the original public main. Local unpublished app changes remain local.
- Branch-protection handling during the rewrite is recorded privately (P-05).
- Windows and GitHub regressions passed all 31 assertions. The initial GitHub
  job nevertheless propagated the final expected `git check-ignore` non-match.
  Commit `4a37f31e0ef22525a8cd08539fc01907de457766` explicitly returns success and
  requires exactly the expected non-match code, preserving failure detection.
- Final public-main CI run `34996317038` succeeded in 6m11s: both privacy checks,
  shuttle CSV validation, JVM/pipeline tests, Android test compilation/lint/build,
  and anonymous-upload contracts passed. This validates the public main snapshot,
  not a fresh install or phone observation.
- Public dorm meal content and source JPG were preserved. Data commit
  `e0235d2759d34e0113979d06304ef7474d3c697b` changes only the active image reference,
  its content hash and revision (4 to 5), with a correction audit. Other dataset
  descriptors are unchanged. Deployment run `34995690653` succeeded in deploy-only
  mode; AI and collection steps were skipped. The publish workflow is active again.
- Direct Pages verification at 2026-09-16 01:36 KST confirmed revision 5, the
  expected JSON hash, and HTTP 200 for the source JPG. Its 1,963,402 bytes and
  SHA-256 match the original authorized public photo. No phone behavior claim.

## Host-side limitation and recovery

Observed: some server-managed GitHub refs may still retain old history; details are
recorded privately (P-05). Updating branches and tags cannot prove cached-object deletion.
An ignored support request draft is prepared at
`.local/history-cleanup-20260916/support-request.md`; it has not been sent.
GitHub must determine eligibility for host-side sensitive-data removal.

Private original bundles and scripts are in `.local/history-cleanup-20260916`.
They intentionally retain recovery data and must stay outside tracked files.
Do not restore old refs or merge an old clone without cleaning its history first.
Actual connection settings remain in `.local/device-connections.json`.

Official reference:
https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository
