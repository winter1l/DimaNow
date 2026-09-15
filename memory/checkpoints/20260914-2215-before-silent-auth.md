# Checkpoint — LMS recovery complete — 2026-09-14 21:09

## The story so far
D-072/D-073 complete. Removed Kakao intake while retaining manual dated class overrides, Home tasks and occurrence dismissal. Phone LMS failure came from D-070 blocking school SSO; user explicitly approved narrow bridge restoration and Android-domain/app-path layering. Final optimized APK installed and pulled SHA matched. Actual phone Courses > All shows data/deadlines after manual refresh; screenshot visually checked. 240 JVM tests, 25 targeted emulator tests, lint/build pass. See lms-recovery-20260914.md.

## Decided
D-072 removes Kakao integration. D-073 allows exact sso.dima.ac.kr domain at Android layer and only port 8080/two SSO raw paths in login WebView; regular transport remains HTTPS-only.

## Waiting on the user
None. Prior Kakao setup questions withdrawn.

## Next first action
Read lms-recovery-20260914.md before modifying login policy; the school bridge is explicitly user-approved and verified on phone.

## Tried
HTTPS SSO unavailable at tested endpoints; direct HTTPS LMS form did not authenticate this account and was reverted. Hidden popup ancestors caused an additional false authentication challenge; reproduced and fixed. Automatic review initially blocked domain allowance, then user explicitly approved the layered policy and normal patch succeeded. Temporary LmsProbe logs were removed. Preserve unrelated prior meal-worker changes and remote main divergence documented in meal-delay-fix-20260914.md; do not push local history indiscriminately.
