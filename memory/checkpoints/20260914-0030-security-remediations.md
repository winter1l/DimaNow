# Checkpoint — 11 selected security remediations locally verified — 2026-09-13 20:53

## The story so far

Confirmed: all 11 selected findings now have local source changes and regression evidence. LMS login is HTTPS-only; widget providers are non-exported and no longer accept custom refresh actions; the Worker uses server-issued address-bound reporter tokens, mandatory route/client/event throttles, bounded single JSON parsing, D1 atomic upload leases, atomic day/week budgets, pre-body admission, bounded image streaming, current-week checks, schedule caching, and scheduled pruning. Meal discovery validates exact authorities/paths, globally routable DNS answers, every redirect, and response sizes. Gemini dormitory output is a hash-bound `PENDING_REVIEW` candidate; only authenticated manual workflow approval of the exact immutable GitHub source and current-week candidate can write READY/PUBLISHED. Tracked physical ADB identifiers use irreversible device aliases and CI checks for recurrence.

Observed local evidence: 24 Worker tests, 233 app JVM tests, and 46 pipeline tests (one gated skip) pass; Wrangler 4.131.1 dry-run bundles all expected bindings; both D1 migrations apply locally; Android-test compilation, lint, debug assembly, and optimized assembly succeed. Workflow YAML parses and the identifier check passes. Existing uncommitted D-064 through D-069 work remains in place.

Unknown: none of these security changes has been deployed to Cloudflare/GitHub Pages or installed on a phone. The completed scan was for immutable snapshot `e2240b0f50989776037ec8b98acd1224539d5db9`; changed source requires a new scan before describing the original scan findings as independently revalidated.

## Decided

- Confirmed: keep operator approval separate from anonymous image/model processing and bind approval to the exact candidate SHA-256, immutable source URL, current week, and GitHub actor.
- Confirmed: keep rate-limit salts and signing material secret; fail closed when mandatory bindings or secrets are absent.
- Confirmed: no branch, worktree, commit, push, release, production migration, Worker deployment, Pages publication, or device installation was performed.
- Confirmed: preserve the exact UI labels `엔터관`, `본관`, and `원룸촌`; no Samsung/One UI behavior claim was made.

## Waiting on the user

None.

## Next first action

If deployment is separately authorized, first recheck the complete diff and remote Worker ancestry/bindings, apply `0002_gateway_admission.sql` remotely, configure the three rate-limit bindings and required secrets, deploy, and verify live fail-closed/approval behavior without publishing a synthetic report or meal. Otherwise the next security action is a fresh scan of the changed source.

## Tried

- Red-green focused tests covered every selected trust boundary, including invalid/tampered/stale meal candidates, caller-minted shuttle tokens, admission races, quota order, oversized bodies, non-JSON mutations, redirect/private/reserved IP SSRF, and non-exported widget receivers.
- `node --test`: 24 passed.
- Wrangler dry-run and local D1 migrations: passed; no remote action.
- Full Gradle JVM/pipeline/test-compilation/lint/debug/optimized build: passed.
- Physical identifier scan: passed after aliases replaced all current tracked Markdown occurrences; Git history was not rewritten.
