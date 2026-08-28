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

## Operating principles

1. Record confirmed decisions immediately.
2. Keep user-confirmed decisions separate from agent assumptions.
3. Label claims as confirmed, observed, assumed, hearsay, or unknown.
4. Product capability claims require code, test, or device evidence.
