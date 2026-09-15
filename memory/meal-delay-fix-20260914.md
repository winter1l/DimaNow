# Student meal publication delay - 2026-09-14

Confirmed request: diagnose and repair delayed new meal visibility using wireless ADB. The user restored Tailscale connectivity during diagnosis.

## Observations

All times below are KST on September 14, 2026.

| Boundary | Time | Evidence |
| --- | --- | --- |
| Failed server collection | 10:00:13 | Revision 8 retained September 7 menu; NEEDS_REVIEW message contains Instagram profile HTTP 401 |
| Successful server publication | 13:43:29 | Revision 9, September 14-18 student menu, SHA d1d17b437d296e948606136716662ab78a12246dab83fc86da42826de901eaa1 |
| Phone import | 14:12:20 | Meal-only sync_state.lastImportedEpochMillis on test-device-galaxy-api37-a |
| Phone screen observation | 14:21 | September 14 menu: beef seaweed soup, cheese chicken meatballs, japchae, fresh kimchi, rice, kimchi |

Observed: server attempts were separated by 223 minutes although the deployed D-062 Monday cron already requested 30-minute checks. The phone imported 29 minutes after publication, consistent with the existing publication-watch interval. No evidence identifies an additional app parser/cache failure. The source post's exact publication time remains unknown; the failed 10:00 request does not prove the school had already posted.

Evidence files (ignored): artifacts/meal-delay-20260914/upstream-evidence.json, phone-meal-sync.json, baseline-layout.json, phone-baseline.png. A temporary copy of the campus DB was removed after selecting meal sync rows; LMS data and phone preferences were not changed.

## Repair and deployment

Implementation choice under the authorized repair: an independent Cloudflare scheduled check verifies the content-addressed current student meal week. A complete Monday-Friday week skips collection. Missing meals with no recent collection attempt send the authenticated, fixed repository_dispatch event student-meal-publication-watch. The quiet period is 25 minutes Monday 09:00-14:00 and 110 minutes otherwise, avoiding duplicate collection near the existing 30-minute/two-hour cadence. No public request endpoint can trigger this check. Responses have byte limits, timeouts, redirect rejection, date/schema checks and SHA verification. Existing GitHub App Contents permission suffices; no credentials were exported or added to Android.

Observed deployment: GitHub main commit 092f58b82bd86ffc8fdb2a0ee75ee0a642883c7e changes only the event trigger and collect-meal routing in the previously deployed workflow. Cloudflare version af8b5d05-a57b-4ba0-b9db-f131c1babf62 adds the standalone module and scheduled handler to the downloaded existing Worker. The content-only API preserved live settings and secret bindings; three named-day cron schedules were configured and read back. Current local D-070 source differs from deployment, so those unrelated changes were not included in this repair deployment. Local integration also reuses the 17:07 UTC daily watch slot for its existing maintenance to stay within three cron entries.

Source/deployment boundary: local main still has its pre-existing history through 7b1ddec; the scoped remote workflow commit was made against remote e2240b0 using its expected blob SHA. Future publication must reconcile this remote workflow commit instead of force-pushing local main. Local Worker integration/module/tests remain uncommitted. The exact remote before/after Worker and workflow patches are preserved in artifacts/meal-delay-20260914.

## Validation

Observed red: recorded attempt gap check failed at 223.3 minutes; standalone watch initially had 18 failing tests. Observed green: 23 standalone watch tests and 49 total Worker tests pass, including scheduled-handler dispatch and retained daily maintenance; the actual deployment candidate separately passes its scheduled-handler integration test. Wrangler dry-run succeeded. Workflow YAML parsing and bash syntax checks passed.

Observed live: repository_dispatch returned HTTP 204; run https://github.com/winter1l/DimaNow/actions/runs/34810100369 completed successfully and logged that the current week was already published and collection was skipped. Running the watch against live public JSON skipped before requesting a token. Existing Worker upload route still returns the expected HTTP 405 to GET. Phone remained on the current menu; no APK installation or phone data reset was needed.

Unknown: the first future Cloudflare scheduled tick and a future delayed-school-publication recovery have not yet elapsed. Trigger registration, candidate execution, current live data and GitHub event handling are verified separately; this does not promise exact delivery times from either hosting platform. Instagram's unauthenticated profile fallback can still return 401; the new independent retries recover once the school homepage exposes a usable post.

References: https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#schedule documents schedule delays/dropped jobs; https://docs.github.com/en/rest/repos/repos#create-a-repository-dispatch-event documents Contents-write dispatch; https://developers.cloudflare.com/workers/configuration/cron-triggers/ documents named UTC cron days.
