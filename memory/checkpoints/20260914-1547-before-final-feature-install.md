# Checkpoint — Daily features — 2026-09-14 15:38

## The story so far
Implemented home today summary, dated class overrides, opt-in Kakao notification proposals with user review, and persistent per-occurrence guidance dismissal. Unit tests, debug/optimized builds, and lint passed. Phone has not received the new APK. Earlier meal repair is complete; preserve its unrelated dirty changes and remote divergence documented in meal-delay-fix-20260914.md.

## Decided
User requested all three annotated additions. Kakao processing is local, exact-room allowlisted, default off, and changes require review.

## Waiting on the user
Exact Kakao classroom chat room name was requested asynchronously; no answer yet. Special notification access remains user-controlled.

## Next first action
Install app/build/outputs/apk/debug/app-debug.apk on emulator-5554 and require success before running the six targeted instrumentation classes listed in artifacts/daily-features-20260914/emulator-tests.log.

## Tried
Initial emulator install failed with insufficient storage; tests then ran against an old main APK and are invalid. Cache trim completed. Parser mutation produced 4 failures out of 8 tests; normal parser restored and green. Earlier build raced a listener API rename; final build succeeded.
