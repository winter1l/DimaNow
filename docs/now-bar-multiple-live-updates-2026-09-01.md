# Samsung Now Bar 다중 Live Update 실기기 시험

검증 일시: 2026-09-01 19:36~19:40 KST

## 목적

DIMA Now가 서로 다른 알림 ID로 적격 Live Update 두 개를 동시에 게시했을 때 Android 16과 Samsung One UI가 이를 어떻게 처리하는지 확인했다. 제품 기능은 변경하지 않았다.

## 시험 환경

- 기기: Samsung Galaxy SM-S918N, ADB serial `R3CW203NFSL`
- Android API: 36
- 빌드: `BP4A.251205.006.S918NKSS8FZG1`
- Samsung One UI 속성 원시값: `ro.build.version.oneui=80500`
- 앱: `com.example.dimanow`, version 1.5 (6)
- Live Update 시험 조건: `모든 앱의 실시간 정보 보기`에 대응하는 `enable_notification_nowbar_test=1`을 시험 동안만 사용

## 방법

1. 기존의 명시적 물리기기 Live Update 계측 경로를 임시로 확장했다.
2. 같은 채널에서 서로 다른 ID `6300`, `6301`로 수업과 셔틀을 나타내는 ongoing 알림 두 개를 게시했다.
3. 각 알림은 게시 전 `hasPromotableCharacteristics()`를 통과하도록 구성했다.
4. 게시 10초 후 두 알림의 활성 상태와 `Notification.FLAG_PROMOTED_ONGOING`을 검사했다.
5. 잠금화면 Now Bar와 알림 센터를 실제 Galaxy 화면에서 확인했다.
6. 시험 알림과 계측 패키지를 제거하고 optimized APK를 `adb install -r`로 복구했다.

R8 optimized 앱에 debug 계측 APK를 바로 연결한 첫 시도는 테스트가 시작되기 전에 Kotlin `Intrinsics` 런타임 불일치로 계측 프로세스가 종료됐다. 동일 소스와 서명의 debug 앱을 `install -r`한 상태에서 계측을 수행했고, 시험 뒤 optimized 앱을 다시 `install -r`했다. 이는 알림 제품 로직의 실패가 아니다.

## 결과

| 확인 항목 | 결과 | 근거 |
|---|---|---|
| 서로 다른 알림 두 개 활성 | 통과 | ID `6300`, `6301`이 동시에 활성 상태 |
| 앱 측 승격 요건 | 통과 | 두 알림 모두 `hasPromotableCharacteristics()=true` |
| Samsung 시스템 승격 | 통과 | 두 알림 모두 `ONGOING_EVENT\|ONLY_ALERT_ONCE\|PROMOTED_ONGOING\|SILENT` |
| 알림 센터 | 통과 | `실시간 정보` 영역에 수업·셔틀 두 카드가 동시에 별도 표시됨 |
| 잠금화면 Now Bar | 통과 | 한 번에 한 카드가 표시됨 |
| 잠금화면 항목 전환 | 통과 — 사용자 직접 관찰 | Now Bar를 위로 넘기면 다음 DIMA Now 카드가 표시됨 |

따라서 이 Galaxy 빌드에서는 **한 앱이 Live Update를 여러 개 동시에 게시하고 승격받을 수 있다.** 알림 센터는 두 항목을 동시에 보여주고, 잠금화면 Now Bar는 한 항목씩 보여주되 위로 넘기는 제스처로 다음 항목을 확인한다.

상태 표시줄의 compact chip이 여러 항목을 어떤 순서로 교체하는지는 이번 시험에서 별도로 판정하지 않았다. 이 동작은 Samsung 시스템 업데이트에 따라 달라질 수 있으므로 다른 기기나 버전에 일반화하지 않는다.

## 정리 및 복구

- 두 시험 알림 취소 완료
- 임시 계측 패키지 `com.example.dimanow.test` 제거 완료
- 임시 계측 소스 변경 제거 완료
- `enable_notification_nowbar_test`를 시험 전 값인 미설정(`null`)으로 복구
- optimized version 1.5 (6) `adb install -r` 복구 완료
- 복구 APK SHA-256: `F55B823BD6622E2913170DF00EEEFB4074AB351545F29CE3343E0247288B24F9`
- 복구 후 앱 PID `25081` 생존, 해당 PID의 즉시 FATAL/ANR 없음
- 다른 앱의 개인 알림이 포함된 원본 화면 캡처는 저장하지 않고 삭제함

## 참고

- [Android 공식 Live Update 문서](https://developer.android.com/develop/ui/views/notifications/live-update)

