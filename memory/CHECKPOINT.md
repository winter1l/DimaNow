# Checkpoint — D-106/D-107 on the worktree branch and the phone — 2026-10-23

## The story so far
Observed: main 2baac30 (not pushed) has D-102..D-105. The worktree branch `claude/ux-guide-pass` adds D-106 (semester-week Home title, exam weeks, screen titles at baseline weight) and D-107 (academic schedule card and reminder; intranet/DIMA C reads: timetable import, 예인관 상벌점, 장비 대여; Courses-style sign-in form). The phone runs the release-signed worktree build (`dist/d107/`, sha256 b3c589cd…). 309 JVM tests pass; tablet screens checked up to the sign-in form; signed-in reads untested (Q-027).

## Decided
- D-106; D-107 with its 2026-10-23 amendment (Courses-style sign-in form).

## Waiting on the user
Try 장비 대여, 시간표 가져오기 and 상벌점 불러오기 on the phone with the school account and report what happens (Q-027). Whether to merge the branch into main. Push main / publish v2.1 when asked (calendar data with exam weeks and 학사일정 is published only after main is pushed). 2027 학사일정 when the school posts it (Q-026).

## Next first action
A handoff is waiting in `memory/HANDOFF.md` (2026-10-23): read it first.
Ask how the signed-in reads behaved on the phone; fix the route from the failure message if one appears. Reply in Korean.

## Open follow-ups
OPEN-QUESTIONS Q-021..Q-023, Q-025..Q-027; lab-room availability and other DIMA C ideas offered; blank-classroom separator task; Material3 1.5.0 stable.
