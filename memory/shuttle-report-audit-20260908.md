# Shuttle report/time-table discrepancy - 2026-09-08

Scope: read-only investigation of the user's screenshot, Tuesday 18:46 at MAIN, with an 18:45 missed-shuttle report card but no corresponding departure in the timetable. No app/worker implementation, existing tests, installed device, or live report data were changed. Diagnostic harnesses and output are under ignored artifacts/shuttle-report-audit-20260908.

## Confirmed findings

- data-source/shuttle.csv:215 contains B-evening YEIN departure 18:40 and MAIN arrival 18:45. Lines 216-218 contain ONE_ROOM departure 18:50 / MAIN arrival 18:55 and MAIN-to-YEIN departure 18:55. The complete Tuesday CSV has zero MAIN departures at 18:45.
- GuidanceEngine.prepareShuttleTopology (around lines 225-270) links these legs into a five-call loop. firstMainTime is ONE_ROOM departure minus five minutes, validated against the first leg's arrival. Thus the arrival becomes a MAIN call at 18:45.
- reportableMissedEvents (117-126) considers all calls, including arrivals, without requiring a matching displayed departure. At 18:46 MAIN it selects this 18:45 call. Its interval is [18:45,18:55): the next same-stop call closes the window before the 15-minute maximum.
- annotatedServiceDepartures (349 onward) lists origin/destination departure times, so the same data shows 18:55 but no 18:45 MAIN departure. ShuttleScreen selects the first reportable call (1837) and labels it only with expectedTime (1984), omitting the arriving vehicle's origin, direction, and arrival-vs-departure distinction.
- The local Worker buildShuttleReportEvents (272 onward) also creates the 18:45 loop call. This is consistent model/presentation disagreement, not evidence of an app-only five-minute countdown or stale-cache problem. No claim is made about live deployment or actual report submission acceptance.

## Reproduction

Run the standalone ShuttleReportProbe.java against the already-built runtime_app_classes_jar/debug/bundleDebugClassesToRuntimeJar/classes.jar plus kotlin-stdlib 2.3.20, using the local JDK 17. The three-row fixture is taken from the relevant Tuesday legs and invokes the real GuidanceEngine methods. Output:

    VISIBLE_MAIN_DEPARTURES=[18:55]
    REPORTABLE_AT_1846=[18:45]
    TOPOLOGY=[YEIN 18:40, MAIN 18:45, ONE_ROOM 18:50, MAIN 18:55, YEIN 19:00]
    REPORTABLE_18:44:59=[]
    REPORTABLE_18:45:00=[18:45]
    REPORTABLE_18:54:59=[18:45]
    REPORTABLE_18:55:00=[18:55]
    SYMPTOM_REPRODUCED: report offered for a time absent from MAIN departures

Exit 1 deliberately flags the user's symptom. A second Node probe reads the complete Tuesday CSV and calls the real local Worker event builder, confirming both zero MAIN 18:45 departures and the generated evening-loop-tuesday-1850:1 arrival event at 18:45. Evidence logs: reproduction.log and server-reproduction.log.

## Interpretation and unresolved policy

Confirmed: the report card displays an arrival/loop call in a way that reads as an unlisted departure. The screenshot does not establish whether riders can actually board at MAIN at 18:45; physical boarding eligibility remains unknown. A future fix should either bind departure reports to displayed boarding departures, or explicitly name arrival-check reports with the original departure, arrival location/time, and direction. Do not add a fabricated departure merely to make the UI lists match. The user requested investigation only; no remediation was applied.
