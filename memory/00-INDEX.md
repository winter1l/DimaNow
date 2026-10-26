# memory/ — DIMA Now brain

Purpose: durable project context that survives session and context changes.

## File map

| File | What | Write rule |
|---|---|---|
| `DECISIONS.md` | User-confirmed product and engineering decisions | Append-only; supersede instead of editing |
| `OPEN-QUESTIONS.md` | Unresolved decisions and provisional readings | Close with a linked decision or finding |
| `SESSION-LOG.md` | Dated implementation history | Append only |
| `PRODUCT-TRUTH.md` | Evidence-backed product state | Evidence and checked date required |
| `figma-current-ui-20260917.md` | Editable current UI Figma file, coverage, evidence and limits | Append dated verification |
| `ui-redesign-20260916.md` | Seven-stage Material 3 Expressive redesign and emulator verification | Append dated verification |
| `phone-ui-audit-20260916.md` | Physical phone and official M3 findings, approved D-082 changes and verification | Append dated evidence |
| `goal/dima-now-android.md` | Goal map, skeleton, and done checks | Update status without erasing superseded cuts |
| `CHECKPOINT.md` | Fast resume point and next live-device action | Replace when the project state materially changes |
| `LOCAL-ONLY.md` | Location of ignored device connection settings for this PC | Tracked instructions only; no connection values |
| `git-privacy-cleanup-20260916.md` | D-080 history rewrite evidence, private recovery location and GitHub residual refs | Append dated verification; never include private values |
| `ux-onboarding-20260914.md` | D-075 UI cleanup and first-run setup implementation and evidence | Append dated evidence |
| `lms-silent-auth-20260914.md` | D-074 invisible automatic authentication, tests and phone update evidence | Append dated evidence |
| `lms-recovery-20260914.md` | D-072 and D-073 login repair and Kakao removal evidence | Append dated evidence |
| `daily-features-20260914.md` | D-071 feature implementation, tests, and phone verification boundaries | Append dated evidence |
| `meal-refresh-20260908.md` | D-062 student cafeteria publication watch: school posting-time evidence and schedule | Append dated evidence |
| `nowbar-duplicate-audit-20260908.md` | Read-only investigation of the locked Now Bar repeating the first shuttle countdown (resolved by D-065) | Historical; append dated notes only |
| `nowbar-refresh-fix-20260908.md` | D-066 folded Now Bar minute-text refresh repair and One UI 9 evidence (closes Q-006) | Append dated evidence |
| `shuttle-report-audit-20260908.md` | Read-only investigation of an 18:45 missed-shuttle report card absent from the timetable (reports later removed by D-091) | Historical; append dated notes only |
| `meal-delay-fix-20260914.md` | Diagnosis and repair of delayed student meal publication over wireless ADB | Append dated evidence |
| `nowbar-metadata-experiment-20260914.md` | Samsung Now Bar metadata-only experiment without the developer test option (Q-010) | Append dated evidence |
| `heic-support-20260915.md` | D-076 HEIC sample conversion, phone/public upload and HIGH/LOW Gemini comparison evidence | Append dated evidence |
| `gemini-redesign-handoff-20260916.md` | User-requested Antigravity/Gemini frontend reconstruction handoff (basis of D-093) | Historical; append dated notes only |
| `gemini-install-20260917.md` | Review of the completed Gemini redesign and its 2026-09-17 phone install | Historical; append dated notes only |
| `meal-recovery-20260921.md` | Sep21 official meal recovery, Cloudflare runtime fix, student/dorm holiday validation, publication and device evidence | Append dated evidence |
| `shuttle-summary-20260921.md` | D-087 immediate-vehicle notification simplification and validation/device limits | Append dated evidence |
| `device-update-20260921.md` | Wireless replacement install, PC Tailscale startup preference, active-use screen policy and cellular ADB limits | Append dated evidence |
| `ui-review-20260930.md` | Read-only M3 Expressive UI/UX review of the D-093 baseline (findings implemented by D-094) | Append dated evidence |
| `ui-review-20261026.md` | Read-only UI/UX review after D-133 (emulator light/dark, font 1.3): snackbar over the FAB, Korean mid-word line breaks, far map distances, Home meal venue, sign-in fold, long school-read waits | Append dated evidence |
| `launch-readiness-20261017.md` | Read-only public-launch readiness review: blockers, decisions needed, external requirements and proposed order | Append dated evidence |
| `ux-guide-pass-20261022.md` | D-102 UX pass from Apple HIG, Material 3 and the Toss guide: sources, changes, kept decisions and emulator evidence | Append dated evidence |
| `m3e-review-20261022.md` | Read-only M3 Expressive conformance review of colors, shape, type, motion and components after D-103 | Append dated evidence |
| `feature-plan-20261025.md` | D-125 feature directions: library, 전자출결, grades and 4402 checks, Q-032 recheck and the phased plan | Append dated evidence |
| `checkpoints/` | Append-only archive of superseded checkpoints | Add a timestamped copy before replacing `CHECKPOINT.md` |

## Operating principles

1. Record confirmed decisions immediately.
2. Keep user-confirmed decisions separate from agent assumptions.
3. Label claims as confirmed, observed, assumed, hearsay, or unknown.
4. Product capability claims require code, test, or device evidence.
