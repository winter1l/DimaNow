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
| `checkpoints/` | Append-only archive of superseded checkpoints | Add a timestamped copy before replacing `CHECKPOINT.md` |

## Operating principles

1. Record confirmed decisions immediately.
2. Keep user-confirmed decisions separate from agent assumptions.
3. Label claims as confirmed, observed, assumed, hearsay, or unknown.
4. Product capability claims require code, test, or device evidence.
`heic-support-20260915.md` — D-076 HEIC sample conversion, phone/public upload and HIGH/LOW Gemini comparison evidence.

`meal-recovery-20260921.md` — Sep21 official meal recovery, Cloudflare runtime fix, student/dorm holiday validation, publication and device evidence.

`shuttle-summary-20260921.md` — D-087 immediate-vehicle notification simplification and validation/device limits.

`device-update-20260921.md` — Wireless replacement install, PC Tailscale startup preference, active-use screen policy and cellular ADB limits.
