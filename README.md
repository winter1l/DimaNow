# DIMA Now

DIMA Now는 동아방송예술대학교 학생의 수업, 셔틀, 본관 학생식당 정보를 한곳에서 확인하는 개인용 Android 앱입니다.

시간표와 설정, 위치 구역, 셔틀·식단 캐시는 모두 기기에 저장됩니다. 별도 계정이나 백엔드 서버를 사용하지 않으며, 학교 공식 페이지와 공식 Instagram의 공개 자료만 가져옵니다.

> 이 프로젝트는 개인용으로 개발된 비공식 앱이며 동아방송예술대학교가 배포하거나 보증하는 공식 앱이 아닙니다.

## 주요 기능

- 홈: 현재 위치, 다음 수업, 셔틀 출발, 오늘의 학생식당 메뉴, 학교 공지 요약
- 시간표: 과목 추가·수정·삭제, 학기 기간, 휴강일과 휴강 모드 관리
- 셔틀: 요일별 전체 시간표, 실시간 남은 시간, 첫차·막차, 본관·운동장 승차 구분
- 식단: 월요일부터 금요일까지의 주간 메뉴와 운영 상태
- 위치: 승인된 예인관·본관·원룸촌 다각형을 이용한 오프라인 구역 판정과 테스트 모드
- Live Update: 수업과 셔틀 안내, 일반 진행 알림 대체 동작, Samsung Now Bar 지원 요청
- 홈 화면 위젯: 셔틀, 식단, 캠퍼스 종합 위젯
- 데이터 관리: 공식 원문, 캐시 상태, 마지막 동기화 결과와 수동 새로고침

## 지원 환경

| 항목 | 값 |
|---|---|
| 언어 | Kotlin |
| UI | Jetpack Compose / Material 3 |
| 로컬 저장소 | Room / DataStore |
| 최소 Android | Android 12, API 31 |
| compileSdk / targetSdk | API 36 |
| 기준 시간대 | Asia/Seoul |
| 주 개발·검증 기기 | Galaxy SM-S918N, Android 16 / One UI 8 |

현재 앱 본체는 Galaxy 휴대폰 화면을 기준으로 구성되어 있습니다. 태블릿과 Galaxy Fold의 펼친 화면에서도 실행할 수 있지만, Navigation Rail·2열 화면·힌지 회피와 같은 대화면 전용 UI 최적화는 아직 적용되지 않았습니다. 홈 화면 위젯은 크기별 소형/확장형 레이아웃을 별도로 지원합니다.

## 설치

비공개 저장소에 접근할 수 있는 계정은 [DIMA Now v1.0 릴리스](https://github.com/winter1l/DimaNow/releases/tag/v1.0)에서 APK를 받을 수 있습니다.

ADB로 기존 데이터를 유지하며 설치하려면 다음 명령을 사용합니다.

```powershell
adb devices
adb -s <device-serial> install -r DIMA-Now-v1.0-optimized-20260828.apk
```

릴리스 APK는 개인 직접 설치를 위해 Android 디버그 인증서로 서명되어 있습니다. Google Play 배포용 서명이 아니며, 기존 설치본과 서명 인증서가 다르면 `-r` 업데이트가 거부될 수 있습니다.

## Samsung Now Bar 설정

앱 최초 안내 또는 설정 화면에서 다음 두 항목을 확인합니다.

1. 잠금화면 알림에서 `잠긴 상태에서 알림 내용 표시`를 활성화합니다.
2. 개발자 옵션에서 `모든 앱의 실시간 정보 보기`를 활성화합니다.

앱은 Android 16의 promoted ongoing notification을 요청하고, 사용할 수 없으면 조용한 진행 알림으로 대체합니다. Now Bar 표시 여부와 최종 잠금화면·AOD 배치는 Samsung 시스템이 결정하므로 앱에서 강제로 보장할 수 없습니다.

## 로컬 빌드

### 준비 사항

- JDK 17
- Android SDK Platform 36
- Android SDK Build Tools 및 Platform Tools

일반적인 JDK 17 환경에서는 Gradle Wrapper를 직접 사용합니다.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

`gradlew-local.bat`은 프로젝트 루트의 `.tooling\jdk-17.0.20.1+1`을 사용하도록 만든 개발 PC 전용 실행기입니다. `.tooling`은 Git에 포함되지 않으므로 해당 경로를 직접 준비한 환경에서만 사용할 수 있습니다.

Debug APK 위치:

```text
app\build\outputs\apk\debug\app-debug.apk
```

R8, 리소스 축소, Baseline/Startup Profile을 적용한 개인 설치용 빌드:

```powershell
.\gradlew.bat assembleOptimized
adb -s <device-serial> install -r app\build\outputs\apk\optimized\app-optimized.apk
```

Optimized APK는 디버그 빌드와 같은 application ID, 버전, 서명을 사용하므로 동일 서명으로 설치된 개발 빌드 위에 `adb install -r`로 올리면 Room과 DataStore 데이터를 유지합니다.

## 테스트

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat compileDebugAndroidTestKotlin
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug assembleOptimized
```

API 31 이상 기기 또는 에뮬레이터를 명시적으로 선택한 뒤 계측 테스트를 실행합니다.

```powershell
adb devices
.\gradlew.bat connectedDebugAndroidTest
```

`benchmark` 모듈은 API 36 테스트 기기에서 앱 시작, 다섯 탭 이동, 셔틀 시간표 스크롤과 Baseline Profile을 측정합니다. 성능 경로에서는 네트워크 새로고침과 식단 OCR을 실행하지 않습니다.

2026년 8월 28일 기준 확인된 자동 검증 결과는 JVM 테스트 88개 통과, `lintDebug` 통과, `assembleOptimized` 통과입니다. Samsung Now Bar, 실제 세 구역 geofence, 잠금화면·AOD, 하루 배터리는 실제 기기 관찰이 필요한 별도 검수 항목입니다.

## 데이터 출처와 캐시 정책

- 셔틀: [DIMA 공식 셔틀버스 안내](https://www.dima.ac.kr/?p=97)의 A/B/C 표
- 식단 발견: [DIMA 공식 홈페이지](https://www.dima.ac.kr/?p=1)와 학생식당 공식 공개 Instagram 게시물
- 학교 공지: [DIMA 공식 공지사항](https://www.dima.ac.kr/?p=111)
- 위치 경계: 사용자 승인 GeoJSON과 © OpenStreetMap contributors

셔틀 원본 표는 주간 행으로 보존하고, 사용자 화면에서만 서비스일·출발지·목적지·시각 기준으로 노선 변형 중복을 합칩니다. 식단은 기기 내 Korean ML Kit OCR로 인식하며, 날짜와 메뉴 유효성 검증을 통과하지 못한 새 후보가 마지막 정상 주간 캐시를 덮어쓰지 못합니다.

셔틀은 KST 기준 새 주가 시작되어 기존 성공 캐시가 이전 주일 때 자동 갱신합니다. 식단은 검증된 주간 식단의 마지막 날짜가 지난 뒤 새 주 식단을 받을 때까지 재시도 간격을 늘려 가며 다시 확인합니다.

## 위치 판정

앱에는 지도나 좌표 편집 기능이 없습니다. 승인된 고정 다각형 `CAMPUS_ZONES_V2_USER_2026_08_27`을 APK에 포함하고 예인관, 본관, 원룸촌을 오프라인으로 판정합니다.

Android 원형 geofence는 앱을 깨우는 용도로만 사용하며 최종 판정은 다각형으로 수행합니다. 예인관과 본관이 원룸촌보다 우선하고, 예인관과 본관이 겹치면 신선한 위치 표본에서 더 가까운 중심을 선택합니다. 테스트 모드에서는 GPS와 geofence 결과를 무시하고 네 구역을 직접 전환할 수 있습니다.

승인된 GeoJSON과 해시는 `artifacts/zone-boundary-capture/`에 보존되어 있습니다.

## 프로젝트 구조

```text
app/
  src/main/          앱, 위젯, 알림, Room/DataStore, 데이터 소스
  src/test/          JVM 단위 테스트와 파서 고정 자료 테스트
  src/androidTest/   API 36 Compose·Room·Live Update 계측 테스트
benchmark/           Macrobenchmark와 Baseline Profile 생성
artifacts/           승인된 구역 경계와 검증 보고서 일부
memory/              제품 결정과 검증 근거
```

핵심 순수 로직은 `GuidanceEngine`이 담당합니다. 홈, 셔틀 화면, 위젯, Live Update는 동일한 셔틀 계산과 분 단위 반올림 결과를 소비합니다. 공개 테스트 경계는 `GuidanceEngine`, `CampusDataRepository`, `ShuttleSource`, `MealSource`, `LocationResolver`, `LiveSurfaceController`입니다.

## 범위 밖

- Everytime 기숙사 식단 자동화
- 로그인 자동화
- 백엔드·서버 운영
- 다중 사용자 배포
- Google Play Store 출시
