# Checkpoint — Silent LMS authentication — 2026-09-14 22:25

## The story so far
D-074 implemented. Automatic login preserves the cached Courses list, selected mode and bottom navigation while a hidden keyed WebView authenticates. Manual login still has progress/cancel. Abandoned authentication cancels and can retry on re-entry. Build and lint passed; 244 JVM tests and 11 selected API 36 UI/WebView tests passed. Phone optimized update and live verification remain.

## Decided
D-074 foreground invisible automatic authentication; D-073 approved exact SSO policy unchanged.

## Waiting on the user
None.

## Next first action
Install app/build/outputs/apk/optimized/app-optimized.apk with a serial-selected adb replacement update on test-device-galaxy-api37-a, then verify Courses UI and fetch.

## Tried
Old UI failed the held-auth regression because cached rows disappeared. Bridge cancellation and coordinator state tests reproduced abandoned-request and stuck-state errors before fixes. New UI test uses no real school credentials. Artifacts: artifacts/lms-silent-auth-20260914.

