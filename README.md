# DIMA Now

DIMA Now는 동아방송예술대학교 학생의 수업, 셔틀, 본관 학생식당·기숙사 식단을 한곳에서 확인하는 개인용 Android 앱입니다.

시간표와 설정, 위치 구역, 셔틀·식단·공지 캐시는 모두 기기에 저장됩니다. 공개 캠퍼스 데이터는 계정 없이 GitHub Pages의 검증 JSON으로 동기화합니다. 사용자가 선택한 경우에만 공식 DIMA LMS 계정을 기기에 암호화해 저장하고 수업 공지·과제·자료를 불러옵니다. 공식 공개 페이지 수집과 식단 OCR은 GitHub Actions에서 수행되며, 기숙사 식단 사진 제출만 무료 Cloudflare Worker를 통과합니다.

> 이 프로젝트는 개인용으로 개발된 비공식 앱이며 동아방송예술대학교가 배포하거나 보증하는 공식 앱이 아닙니다.

## 주요 기능

- 홈: 현재 위치, 다음 수업, 셔틀 출발, 현재 구역의 오늘 식단, 학교 공지 요약
- 시간표: 과목 추가·수정·삭제, 학기 기간, 휴강일과 휴강 모드 관리
- 수업: 공식 LMS `전체 학습`의 공지·과제·콘텐츠·자료 등 전체 목록, 앱 내부 상세 내용, 첨부파일을 Android 문서 저장 화면으로 저장
- 셔틀: 교내 셔틀과 4402 강남행의 전체 시간표·실시간 남은 시간, 첫차·막차, 본관·운동장 승차 구분, 정류장별 미도착 신고 현황
- 식단: 본관 학생식당·기숙사 주간 메뉴, 운영 상태, 기숙사 식단 사진 제출
- 위치: 승인된 예인관·본관·원룸촌 다각형을 이용한 오프라인 구역 판정과 테스트 모드
- Live Update: 수업과 셔틀 안내, 일반 진행 알림 대체 동작, Samsung Now Bar 지원 요청
- 알림 제어: 수업·교내 셔틀·4402를 각각 `나우바`, `일반 알림`, `끔`으로 설정
- 홈 화면 위젯: 셔틀, 식단, 캠퍼스 종합 위젯
- 데이터 관리: 공식 원문, 캐시 상태, 마지막 동기화 결과와 수동 새로고침
- 앱 업데이트: GitHub 최신 안정 릴리스를 하루 한 번 확인하고, 사용자가 요청한 경우에만 검증된 APK 설치 화면 열기

## 지원 환경

| 항목 | 값 |
|---|---|
| 언어 | Kotlin |
| UI | Jetpack Compose / Material 3 |
| 로컬 저장소 | Room / DataStore |
| LMS 계정 보호 | Android Keystore AES-256-GCM / 백업 제외 암호문 |
| 최소 Android | Android 12, API 31 |
| compileSdk / targetSdk | API 36 |
| 기준 시간대 | Asia/Seoul |
| 주 개발·검증 기기 | Galaxy SM-S918N, Android 16 / One UI 8 |

현재 앱 본체는 Galaxy 휴대폰 화면을 기준으로 구성되어 있습니다. 태블릿과 Galaxy Fold의 펼친 화면에서도 실행할 수 있지만, Navigation Rail·2열 화면·힌지 회피와 같은 대화면 전용 UI 최적화는 아직 적용되지 않았습니다. 앱 화면은 세로 방향으로 고정되며, API 36 대화면에서도 Android 16의 임시 호환 opt-out을 사용해 같은 동작을 유지합니다. 홈 화면 위젯은 크기별 소형/확장형 레이아웃을 별도로 지원합니다.

## 설치

[GitHub Releases](https://github.com/winter1l/DimaNow/releases)에서 APK를 받을 수 있습니다.

ADB로 기존 데이터를 유지하며 설치하려면 다음 명령을 사용합니다.

```powershell
adb devices
adb -s <device-serial> install -r DIMA-Now-v1.5-optimized.apk
```

릴리스 APK는 개인 직접 설치를 위해 Android 디버그 인증서로 서명되어 있습니다. Google Play 배포용 서명이 아니며, 기존 설치본과 서명 인증서가 다르면 `-r` 업데이트가 거부될 수 있습니다.

앱은 실행 시 마지막 확인으로부터 24시간이 지났을 때 GitHub의 최신 안정 릴리스를 확인합니다. 새 버전 안내는 버전마다 한 번만 자동 표시하며, 설정의 `앱 업데이트`에서 언제든 다시 확인할 수 있습니다. APK는 자동으로 설치되지 않습니다. `다운로드 및 설치`를 누른 경우에만 GitHub asset을 내려받아 SHA-256, 패키지명, 더 높은 versionCode, versionName, 현재 앱과 같은 서명을 확인한 뒤 Android 시스템 설치 확인 화면을 엽니다. 필요한 경우 DIMA Now 한 앱에 대한 `알 수 없는 앱 설치` 권한 화면이 먼저 열립니다.

## Samsung Now Bar 설정

앱 최초 안내 또는 설정 화면에서 다음 두 항목을 확인합니다.

1. 잠금화면 알림에서 `잠긴 상태에서 알림 내용 표시`를 활성화합니다.
2. 개발자 옵션에서 `모든 앱의 실시간 정보 보기`를 활성화합니다.

앱은 Android 16의 promoted ongoing notification을 요청하고, 사용할 수 없으면 조용한 진행 알림으로 대체합니다. Now Bar 표시 여부와 최종 잠금화면·AOD 배치는 Samsung 시스템이 결정하므로 앱에서 강제로 보장할 수 없습니다.

교내 셔틀과 4402 안내는 목적지와 남은 시간을 `본관행 8분`, `강남행 8분`처럼 표시하고 매분 다시 계산합니다. 수업 countdown은 Android 시스템 chronometer를 계속 사용합니다. 4402는 대학 셔틀 정류장(34710)의 [안성시 공식 고정 시간표](https://www.anseong.go.kr/depart/contents.do?mId=0303010000)를 사용합니다. 원룸촌 앞(33243)은 공식 기점에서 1분 뒤 도착하는 예정 시각으로 명확히 구분하며, 반대 방향 정류장은 포함하지 않습니다.

4402 위치 안내는 기존 예인관·본관·원룸촌 구역을 바꾸지 않습니다. 각 정류장 120m 원은 저전력 깨움에만 쓰고, 최근 30초 이내·정확도 25m 이내 위치가 정류장 40m 안에서 30초 유지될 때만 4402 안내로 전환합니다. 70m를 벗어나거나 위치를 2분 동안 확인하지 못하면 해제합니다.

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

각 변경은 GitHub Actions의 JVM·파이프라인·계측 컴파일·lint·optimized 빌드를 모두 통과해야 합니다. Samsung Now Bar, 실제 세 구역 geofence, 잠금화면·AOD, 하루 배터리는 실제 기기 관찰이 필요한 별도 검수 항목입니다.

## LMS 수업 탭

하단 `수업` 탭에서 학번과 비밀번호를 입력하면 Android Keystore의 내보낼 수 없는 키로 AES-256-GCM 암호화한 암호문만 `noBackupFilesDir`에 저장합니다. 계정 원문, LMS 쿠키와 세션 값은 Room·DataStore·로그에 기록하지 않습니다. 자동 로그인은 앱이 포그라운드에 있을 때 공식 `portal.dima.ac.kr`·`lms.dima.ac.kr` HTTPS 화면에서만 실행합니다. 정확한 포털 3045 중복 세션 화면은 그 페이지가 제공한 공식 전환 동작을 한 번만 수행하며, 반복 충돌·CAPTCHA·OTP·추가 인증·계정 잠금은 우회하지 않습니다.

목록은 LMS의 `전체 학습` 화면을 기준으로 한 번에 동기화하고 5분 동안 기기 캐시를 사용합니다. 기본 `오늘` 보기는 KST 기준 새 소식, 기한이 지난 미완료 항목, 오늘부터 3일 뒤까지의 일정과 접힌 완료 항목을 묶어 표시합니다. `전체` 보기에서는 기존 과목·유형·읽음 필터와 전체 기록을 그대로 사용할 수 있습니다. 영상 강의도 목록에서 `콘텐츠` 유형으로 구분하고, LMS가 제공한 완료·미완료 탭을 정상적으로 확인한 경우에만 `수강 완료` 또는 `미수강` 상태를 표시합니다. 선택 상태 요청이 실패하면 완료 여부를 추정하지 않고 전체 목록과 직전 상태를 보존합니다. 새 항목과 제목·마감·링크 변경은 열기 전까지 각각 `새 항목`, `변경됨`으로 표시됩니다.

공지·자료·과제는 외부 브라우저나 글 WebView를 열지 않고 네이티브 상세 화면에서 과목, 유형, 제목, 작성자·등록일, 내용, 제출기간·만점, 첨부파일 중 존재하는 항목만 구분해 표시합니다. 본문은 실행 가능한 태그·form·이벤트 속성을 제거합니다. 영상 콘텐츠는 목록의 존재와 LMS가 제공한 수강 완료 상태를 보여주는 것이 우선입니다. 재생은 후순위 기능으로, 사용자가 직접 선택하면 `학습 시작을 하실건가요?` 확인 뒤 제목과 차시가 유일하게 맞는 같은 로그인 세션의 공식 LMS 화면을 엽니다. 앱은 재생이나 진도를 완료로 추정하거나 LMS 완료 상태를 조작하지 않습니다. 대상을 정확히 찾지 못하거나 토론·팀프로젝트·퀴즈·시험처럼 네이티브 미지원 유형이면 잘못 추정하지 않고 같은 로그인 세션의 공식 LMS 화면으로 연결합니다.

첨부 요청은 실제 상세 페이지의 최종 주소와 Referer, 공식 form의 GET/POST 방식과 필요한 필드를 함께 보존합니다. 응답은 앱 전용 임시 캐시에 먼저 받고 0바이트, 길이 불일치, 로그인·오류 HTML, LMS 밖으로 이탈한 응답을 거부합니다. 검증을 마친 파일만 Android 문서 저장 화면으로 전달하며, 출력 스트림 실패나 부분 복사는 성공으로 표시하지 않습니다. 광범위한 저장소 권한은 요구하지 않습니다. 상세를 열 때 서버를 다시 확인해 첨부파일이 추가·삭제·변경됐는지 알리고, 연결할 수 없으면 마지막으로 저장한 상세를 명시해 표시합니다. 설정의 `LMS 계정`에서 저장 계정, 세션과 별도 `lms-cache.db`를 함께 삭제할 수 있습니다.

LMS는 학교가 운영하는 외부 시스템입니다. 서버 화면·세션 정책이 바뀌면 자동 로그인이나 `전체 학습` 동기화가 일시적으로 동작하지 않을 수 있으며, 앱은 로그인·중복 세션 전환을 반복해 계정 잠금을 유발하지 않도록 각 자동 시도를 한 번으로 제한합니다. 수강 신청, 제출, 댓글 같은 쓰기 동작은 제공하지 않습니다.

## 정적 데이터 동기화

앱의 동기화 endpoint는 [`manifest.json`](https://winter1l.github.io/DimaNow/data/v1/manifest.json)입니다. manifest에는 데이터셋별 revision, 게시 시각, 상태, SHA-256과 content-addressed JSON 경로가 들어갑니다. 앱은 HTTPS, 허용된 상대 경로, schema version과 SHA-256을 모두 검증한 뒤 Room 캐시를 한 트랜잭션으로 교체합니다. 네트워크·파싱·무결성 오류가 나면 마지막 정상 캐시를 보존합니다.

- 셔틀: 관리자가 [`data-source/shuttle.csv`](data-source/shuttle.csv)를 수정하면 `main` 반영 후 자동 게시합니다.
- 식단: GitHub Actions가 DIMA 공식 공개 게시물과 공개 Instagram embed를 확인하고, Gemini 3.5 Flash-Lite의 구조화 JSON 출력으로 식단 이미지를 전사합니다. 월~금 날짜와 빈 메뉴 여부만 최소 검증한 뒤 게시합니다.
- 기숙사 식단: 현재 주 데이터가 없을 때 누구나 앱에서 사진을 고르거나 촬영할 수 있습니다. 앱은 Pages를 먼저 다시 확인하고, 제출 workflow도 직렬화된 상태에서 중복 주차를 다시 검사합니다. 검증 호출은 표의 출처·전체 노출 여부만 `HIGH` 추론으로 확인하고, 통과한 사진만 별도의 Flash-Lite 구조화 출력으로 전사합니다.
- 공지: GitHub Actions가 DIMA 공식 공지 목록을 읽어 최대 10개를 게시합니다.
- 앱: 실행 시와 12시간마다 작은 manifest를 확인합니다. 수동 새로고침도 같은 endpoint만 사용합니다.

식단 후보가 아직 게시되지 않았거나 검증에 실패하면 manifest를 `WAITING` 또는 `NEEDS_REVIEW`로 갱신하고 기존 정상 식단 JSON은 유지합니다. GitHub Actions의 예약 실행은 지연될 수 있으므로 실시간 보장 서비스가 아닙니다.

공식 셔틀 캐시 605행은 원문 그대로 유지합니다. 2026-09-02 현장 시간표에서 확인한 원룸촌발 평일 14:30·15:30·16:30·17:30 네 편은 `A-field-extra` 투영 20행으로 분리하며, 사진에 없는 도착·복귀 시각은 추정하지 않습니다. 주간 A/B 운행은 각각 원룸촌↔본관, 본관↔엔터관이고, 저녁에는 엔터관→본관(운동장)→원룸촌→본관(운동장)→엔터관의 한 물리 차량 운행으로 계산합니다.

`셔틀이 오지 않나요?` 신고는 해당 정류장의 예정 시각부터 최대 15분 동안만 열립니다. 앱은 신선한 GPS 표본으로 정류장 구역을 다시 확인하며, 테스트 모드에서는 현황만 표시하고 신고할 수 없습니다. 신고 수는 같은 물리 차량의 현재·이전 정류장에만 전파되는 참고 정보이며 지연·결행을 확정하지 않습니다. 홈과 셔틀 화면에만 표시하고 위젯·Now Bar에는 넣지 않습니다.

식단 게시 workflow에는 저장소 Actions Secret `GEMINI_API_KEY`가 필요합니다. 로컬 개발에서는 `.env.example`을 참고하되 실제 `.env`와 API 키를 커밋하지 않습니다. Gemini에는 공개 학생식당 이미지 또는 사용자가 명시적으로 제출한 기숙사 식단표 사진만 전송하며 앱의 위치·시간표·설정은 전송하지 않습니다.

기숙사 사진은 GitHub 로그인 없이 Cloudflare Worker로 전송됩니다. Worker는 이미지 형식·15 MiB 제한을 확인하고 IP 단방향 해시로 짧은 중복 제출 제한을 적용한 뒤, 이 저장소에만 설치된 전용 GitHub App의 단기 설치 토큰으로 `dorm-submissions` 브랜치에 신규 사진을 기록합니다. Gemini 키·GitHub App 개인 키·저장소 토큰은 APK와 Git에 포함하지 않습니다. 제출 사진은 공개 브랜치에 올라가므로 업로드 확인창에서 공개 공유 사실을 먼저 알립니다.

## 앱 업데이트 경로

업데이트 확인은 공개 GitHub Releases API의 latest stable release만 사용합니다. draft와 prerelease는 받지 않으며, 태그는 `vMAJOR.MINOR` 또는 `vMAJOR.MINOR.PATCH`, asset 이름은 `DIMA-Now-v<버전>-optimized.apk` 하나와 일치해야 합니다. 다운로드는 GitHub HTTPS 호스트로만 제한하고 128 MiB를 넘는 파일, GitHub digest가 없는 asset, 해시·패키지·버전·서명이 다른 APK는 삭제하고 설치하지 않습니다.

이 기능은 Google Play의 무인 업데이트가 아닙니다. 다운로드와 Android 설치 확인은 항상 사용자 동작이 필요하고, 시스템의 설치 차단이나 확인 화면을 우회하지 않습니다.

### 셔틀 수정 방법

1. `data-source/shuttle.csv`에서 행을 추가·수정합니다.
2. `운행요일,노선ID,출발구역,승차정류장,목적구역,출발시각,도착시각` 7개 열을 유지합니다.
3. Pull Request의 `검증 / test`가 통과한 뒤 `main`에 반영합니다.
4. `캠퍼스 데이터 게시` workflow가 `data` 브랜치와 GitHub Pages를 갱신합니다.

CSV는 정확히 같은 물리 운행 행의 중복, 잘못된 요일·구역·시각을 게시 전에 거부합니다. 공식 605행 캐시는 그대로 보존하며 앱 표시 단계에서만 route 변형 중복을 합칩니다.

## 데이터 출처와 라이선스

- 셔틀: [DIMA 공식 셔틀버스 안내](https://www.dima.ac.kr/?p=97)의 A/B/C 표
- 식단 발견: [DIMA 공식 홈페이지](https://www.dima.ac.kr/?p=1), 학생식당 공식 공개 Instagram embed, 사용자가 명시적으로 제출한 기숙사 주간 식단표
- 학교 공지: [DIMA 공식 공지사항](https://www.dima.ac.kr/?p=111)
- 위치 경계: 사용자 승인 GeoJSON과 © OpenStreetMap contributors

앱 소스는 [Apache License 2.0](LICENSE)으로 공개합니다. 학교와 Instagram의 원문·이미지는 각 권리자에게 있으며 이 저장소의 소프트웨어 라이선스가 적용되지 않습니다. 위치 데이터 attribution과 제3자 구성요소는 [NOTICE](NOTICE)를 확인하세요.

## 위치 판정

앱에는 지도나 좌표 편집 기능이 없습니다. 승인된 고정 다각형 `CAMPUS_ZONES_V2_USER_2026_08_27`을 APK에 포함하고 예인관, 본관, 원룸촌을 오프라인으로 판정합니다.

Android 원형 geofence는 앱을 깨우는 용도로만 사용하며 최종 판정은 다각형으로 수행합니다. 예인관과 본관이 원룸촌보다 우선하고, 예인관과 본관이 겹치면 신선한 위치 표본에서 더 가까운 중심을 선택합니다. 테스트 모드에서는 GPS와 geofence 결과를 무시하고 네 구역을 직접 전환할 수 있습니다.

승인된 GeoJSON과 해시는 `artifacts/zone-boundary-capture/`에 보존되어 있습니다.

## 프로젝트 구조

```text
app/
  src/main/          앱, 위젯, 알림, Room/DataStore, LMS·정적 데이터 동기화
  src/test/          JVM 단위 테스트
  src/androidTest/   API 36 Compose·Room·Live Update 계측 테스트
benchmark/           Macrobenchmark와 Baseline Profile 생성
sync-contract/       앱과 게시 파이프라인이 공유하는 JSON 계약
data-pipeline/       셔틀 CSV 검증, 식단 OCR, 공지 수집, 정적 사이트 게시
anonymous-upload-worker/  GitHub 로그인 없는 기숙사 사진 제출 게이트웨이
data-source/         관리자가 수정하는 셔틀 CSV
.github/workflows/   검증과 GitHub Pages 데이터 게시
artifacts/           승인된 구역 경계와 검증 보고서 일부
memory/              제품 결정과 검증 근거
```

핵심 순수 로직은 `GuidanceEngine`이 담당합니다. 홈, 셔틀 화면, 위젯, Live Update는 동일한 셔틀 계산과 분 단위 반올림 결과를 소비합니다. 캠퍼스 기능의 공개 테스트 경계는 `GuidanceEngine`, `CampusDataRepository`, `ShuttleSource`, `MealSource`, `LocationResolver`, `LiveSurfaceController`이며, LMS 기능은 `LmsCredentialStore`, `LmsSessionController`, `LmsSource` 경계로 분리됩니다.

## 범위 밖

- LMS의 수강 신청·과제 제출·댓글 등 쓰기 자동화
- 별도 상시 서버·개인 PC 서버 운영
- 다중 사용자 배포
- Google Play Store 출시

## 개인정보와 보안

개인정보 처리 범위는 [PRIVACY.md](PRIVACY.md), 취약점 제보는 [SECURITY.md](SECURITY.md)를 확인하세요. 공개 Issue에는 위치 좌표, 시간표, 기기 로그 등 개인 데이터를 올리지 마세요.
