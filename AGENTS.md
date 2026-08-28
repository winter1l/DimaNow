# DIMA Now contributor guidance

## Session start (ballast)

1. Before substantive work, read `memory/00-INDEX.md` and `memory/DECISIONS.md`. Standing decisions are followed without relitigating; changes use an append-only superseding decision.
2. Record decisions and important facts in `memory/` in the same session they appear. Put unresolved items and provisional readings in `memory/OPEN-QUESTIONS.md`.
3. Label claims as confirmed, observed, assumed, hearsay, or unknown.
4. Do not claim product behavior without dated evidence in `memory/PRODUCT-TRUTH.md`.

## Project rules

- Keep all app work inside this `DimaNow` directory.
- Use Kotlin, Jetpack Compose, Room, and DataStore. Compile and target API 36; support API 31 and newer.
- Follow vertical red-green TDD at the approved public seams: `GuidanceEngine`, `CampusDataRepository`, `ShuttleSource`, `MealSource`, `LocationResolver`, and `LiveSurfaceController`.
- Use the Korean user-visible stop labels `엔터관`, `본관`, and `원룸촌`. Never expose the official stadium-side wording in UI, widgets, notifications, screenshots, or tests.
- Never claim Samsung Now Bar acceptance without observation on the user's One UI 8 device.
