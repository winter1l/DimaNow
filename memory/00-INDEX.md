# memory/ — DIMA Now brain

Purpose: durable project context that survives session and context changes.

## File map

| File | What | Write rule |
|---|---|---|
| `DECISIONS.md` | User-confirmed product and engineering decisions | Append-only; supersede instead of editing |
| `OPEN-QUESTIONS.md` | Unresolved decisions and provisional readings | Close with a linked decision or finding |
| `SESSION-LOG.md` | Dated implementation history | Append only |
| `PRODUCT-TRUTH.md` | Evidence-backed product state | Evidence and checked date required |
| `goal/dima-now-android.md` | Goal map, skeleton, and done checks | Update status without erasing superseded cuts |
| `CHECKPOINT.md` | Fast resume point and next live-device action | Replace when the project state materially changes |
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
