# 기여 안내

변경은 작은 Pull Request로 제출하고, 기존 한국어 문구·표시 규칙과 `memory/DECISIONS.md`의 최신 결정을 먼저 확인해 주세요.

## 개발 흐름

1. `main`에서 기능 브랜치를 만듭니다.
2. 공개 seam을 대상으로 실패하는 테스트를 하나 추가합니다.
3. 최소 구현으로 통과시킨 뒤 별도 정리 단계에서 리팩터링합니다.
4. 아래 검증을 실행합니다.

```powershell
.\gradlew.bat testDebugUnitTest :data-pipeline:test compileDebugAndroidTestKotlin lintDebug assembleOptimized
```

셔틀 시간표만 수정할 때도 `data-source/shuttle.csv` 계약 검증을 통과해야 합니다. 앱 화면의 출발지 명칭과 운동장 전환 표시는 기존 제품 결정을 따르며, 원본 605행을 화면 편의를 위해 삭제하지 않습니다.

개인 정보나 실제 기기 DB·logcat 전체를 커밋하지 마세요. Samsung Now Bar/AOD 동작은 실제 기기에서 눈으로 확인하기 전 성공으로 단정하지 않습니다.

식단 파이프라인의 `GEMINI_API_KEY`는 로컬 `.env` 또는 GitHub Actions Secret으로만 관리합니다. 실제 `.env`, API 키, Gemini 요청 인증 헤더를 커밋하거나 로그로 출력하지 마세요. PR 검증은 고정 JSON fixture를 사용하며 외부 API를 호출하지 않습니다.
