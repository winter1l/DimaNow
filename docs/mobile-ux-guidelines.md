# 모바일 앱 UI/UX 가이드 요약 — Apple HIG · Material 3 Expressive · Toss

조사일: 2026-10-22 (원문을 직접 읽고 정리) · 2026-10-23 원문 대조 검증 반영 · 범용 참고 문서로 어느 프로젝트에 복사해 써도 된다.

세 가이드는 맡는 영역이 다르므로 함께 쓴다. 화면 모양과 컴포넌트는 플랫폼 가이드(Android는 Material 3, iOS는 Apple HIG)를 따르고, 흐름·권한·문구는 세 가이드 중 사용자에게 더 친절한 쪽을 고른다. 가이드는 자주 바뀌므로 적용 전에 아래 링크의 원문을 다시 확인한다.

## 목차

1. [이 문서 사용법](#1-이-문서-사용법)
2. [세 가이드의 공통 원칙](#2-세-가이드의-공통-원칙)
3. [Apple HIG 핵심 규칙](#3-apple-hig-핵심-규칙)
4. [Material 3 / M3 Expressive 핵심 규칙](#4-material-3--m3-expressive-핵심-규칙)
5. [Toss UI/UX 가이드 핵심 규칙](#5-toss-uiux-가이드-핵심-규칙)
6. [가이드끼리 충돌할 때](#6-가이드끼리-충돌할-때)
7. [적용 체크리스트](#7-적용-체크리스트)
8. [참고 링크](#8-참고-링크)

## 1. 이 문서 사용법

| 가이드 | 주로 정하는 것 | 쓰는 법 |
| --- | --- | --- |
| [Material 3 / M3 Expressive](https://m3.material.io/) | 색·모양·글꼴·모션 토큰, 컴포넌트 규격과 사용법 | Android 앱의 시각 기준. 수치와 토큰을 그대로 따른다 |
| [Apple HIG](https://developer.apple.com/kr/design/human-interface-guidelines/) | 온보딩·권한·알림·모달 흐름, 글쓰기, 접근성 | 플랫폼과 무관한 흐름·권한 원칙으로도 쓴다 |
| [Toss UI/UX 가이드](https://developers-apps-in-toss.toss.im/design/consumer-ux-guide) | 한국어 UX 라이팅, 다크패턴 금지, 목록·그래픽 | 한국어 서비스의 문구와 중단(인터럽트) 기준으로 쓴다 |

- 규칙 앞의 **필수**는 원문이 must·never 수준으로 쓴 것이고, 나머지는 권장이다(원문이 "avoid"면 권장이다).
- **[미확인]**은 검증 때 원문에서 해당 문장을 찾지 못한 항목이다(원문 예시가 이미지라 읽지 못한 경우 포함). 쓰기 전에 원문을 직접 확인한다.
- Apple HIG 한국어판은 일부 예시가 영어판과 반대로 번역돼 있다(글쓰기 페이지의 버튼 예시). 의미가 다르면 영어판을 따른다. 실시간 현황(Live Activities) 페이지처럼 영어판만 최신인 경우도 있다.
- Toss 가이드는 앱인토스 미니앱용이라 로고·브랜드 색·토스 내비게이션 바·라이트 모드 전용 규칙은 일반 앱에 적용하지 않는다.
- 가이드는 기준선이다. 브랜드나 제품 사정으로 의도적으로 벗어날 수 있지만, 그때는 이유를 결정 기록으로 남긴다.

## 2. 세 가이드의 공통 원칙

요약하면 "필요할 때 묻고, 막지 말고, 기다리게 하지 말고, 무엇이 일어나는지 말하라"이다. 어느 플랫폼에서든 아래를 기본값으로 삼는다.

**온보딩과 권한**

- 권한은 기능이 처음 필요할 때 묻는다. 앱이 없으면 동작하지 않는 권한만 첫 실행에서 묻는다.
- 권한마다 "어떻게·왜 쓰는지"를 한 문장으로 말한다. "더 나은 경험을 위해" 같은 문구는 쓰지 않는다.
- 시스템 권한 창 앞의 안내 화면은 버튼 하나("계속")만 두고, "허용"처럼 보이게 만들지 않는다. 닫기·취소·"나중에"처럼 시스템 창을 보지 않고 나가는 버튼도 두지 않는다. 거절은 시스템 창에서 하며, 사용자를 오해하게 만드는 사전 화면은 App Store 심사에서 거절된다.
- 거절해도 나머지 기능은 그대로 동작하고, 나중에 설정에서 켜는 길이 있다.
- 온보딩은 짧고 건너뛸 수 있게 한다. 건너뛴 안내는 다시 띄우지 않고, 선택 설정은 기본값으로 미룬다.

**중단과 다크패턴**

- 앱을 열자마자 대화상자·바텀시트(업데이트·로그인·알림 동의)를 띄우지 않는다. 필요하면 화면 안 카드로 알린다(Toss는 진입 바텀시트를 심사 탈락 사유로 둔다. HIG는 실행 직후 알림창을 "피하라"고 쓴다).
- **필수:** 뒤로 가기를 가로채는 시트나, 나갈 길이 없는 화면을 만들지 않는다.
- 모달은 한 번에 하나만 띄우고, 제목과 닫는 길을 둔다. 저장 안 된 입력을 닫으면 버릴지 물어본다.

**로딩과 피드백**

- 바로 무언가를 보여 준다(캐시·스켈레톤). 오프라인이면 오류 대화상자 대신 캐시와 조용한 상태 문구를 보여 준다.
- 기다릴 것이 없으면 로딩 표시를 쓰지 않는다. 진행률을 알면 확정 진행 표시를 쓴다.
- 상태는 해당 항목 옆에 표시한다. 되돌릴 수 있는 동작은 확인 대화상자 대신 실행 취소(undo)를 준다.
- 당기기 새로고침에는 한 번 누르는 대안(상단 버튼)을 함께 둔다.

**접근성**

- 터치 영역은 Android 48dp, iOS 44pt 이상이다. 글자는 200%까지 커져도 잘리지 않고 줄바꿈된다.
- 대비는 작은 글자 4.5:1, 큰 글자·아이콘 3:1 이상이며, 라이트·다크 모드 모두에서 확인한다.
- 색만으로 의미를 전하지 않는다. 모든 컨트롤은 역할·이름·상태(선택·펼침·오늘)를 화면 낭독기에 알린다.
- 시스템 '동작 줄이기' 설정을 따르고, 제스처에는 버튼 대안을 둔다.

**글쓰기**

- 버튼은 결과를 말하는 동사로 쓴다("저장", "설정 열기"). "확인"·"예/아니요"는 피한다.
- 오류는 문제 위치에 띄우고, 탓하지 않으며, 고치는 법을 말한다. "오류" 같은 단어만 남기지 않는다.
- 빈 화면은 다음 할 일을 버튼으로 준다. 설정 항목은 켜면 무엇이 되는지를 설명한다.
- 자주 쓰는 용어 목록을 정하고 앱 전체에서 한 말투를 유지한다.

## 3. Apple HIG 핵심 규칙

HIG는 iOS 전용 규칙보다 흐름·권한·글쓰기 원칙에서 플랫폼과 무관하게 쓸 내용이 많다. 주제 이름이 한국어판 원문 링크다.

- [온보딩](https://developer.apple.com/kr/design/human-interface-guidelines/onboarding): 빠르고 선택적으로, 실행이 끝난 뒤에. 긴 튜토리얼보다 상황별 팁. 약관 문구는 넣지 않거나, 꼭 필요하면 흐름을 방해하지 않게 균형 있게 넣는다.
- [실행](https://developer.apple.com/kr/design/human-interface-guidelines/launching): 바로 쓸 수 있게 하고, 다시 열면 이전 위치·스크롤을 복원한다. 실행 화면에는 글자·광고를 두지 않는다.
- [개인정보와 권한](https://developer.apple.com/kr/design/human-interface-guidelines/privacy): 필요한 데이터만, 필요한 순간에. 목적 문구는 짧고 구체적인 능동형 한 문장. 가능하면 기기 안에서 처리하고, 비밀 값은 시스템 보안 저장소에 둔다.
- [피드백](https://developer.apple.com/kr/design/human-interface-guidelines/feedback): 색·텍스트·소리·햅틱 여러 통로로. 경고는 예상 못한·되돌릴 수 없는 데이터 손실 앞에서만. 성공 확인은 중요한 작업에만.
- [로딩](https://developer.apple.com/kr/design/human-interface-guidelines/loading)·[진행 표시](https://developer.apple.com/kr/design/human-interface-guidelines/progress-indicators): 자리표시를 먼저 보여 주고 계속 작업하게 한다. 측정 가능하면 확정형. 멈추면 이유와 해결법을 말한다. 주기적 자동 새로고침도 함께.
- [알림창(Alert)](https://developer.apple.com/kr/design/human-interface-guidelines/alerts): 드물게, 중요하고 행동할 수 있는 정보에만. 실행 직후 알림창은 피한다. 제목은 무슨 일이 왜 일어났는지 두 줄 이내. 버튼은 최대 3개, 결과를 말하는 동사. 취소 버튼 이름은 "취소"이고 기본 버튼이 아니다. 파괴적 동작은 사용자가 일부러 고른 경우(예: 휴지통 비우기)에만 기본 버튼이 될 수 있다.
- [모달](https://developer.apple.com/kr/design/human-interface-guidelines/modality)·[시트](https://developer.apple.com/kr/design/human-interface-guidelines/sheets)·[액션 시트](https://developer.apple.com/kr/design/human-interface-guidelines/action-sheets): 집중이 도움이 될 때만. 한 번에 하나. 완료는 취소(또는 뒤로)와 짝을 이루되 세 개를 모두 두지 않는다. 시트의 닫는 버튼은 "취소" 또는 "닫기". 액션 시트는 취소를 포함하고 스크롤하지 않게.
- [글쓰기](https://developer.apple.com/kr/design/human-interface-guidelines/writing): 목소리를 정하고 중요한 정보를 앞에. 기발함보다 명확함. 링크는 가는 곳을 말한다. 흐름은 "시작하기" → "계속"·"다음" → "완료". "우리"와 소유격을 줄인다.
- [접근성](https://developer.apple.com/kr/design/human-interface-guidelines/accessibility): 터치 44×44pt(최소 28), 간격 12–24pt, 글자 200%, 대비 4.5:1·3:1. 시간이 지나면 저절로 닫히는 UI는 최소화하고 직접 닫게 한다.
- [알림](https://developer.apple.com/kr/design/human-interface-guidelines/notifications)·[알림 관리](https://developer.apple.com/kr/design/human-interface-guidelines/managing-notifications): 짧고 유용하게, 반복 금지, 오류 통보에 쓰지 않는다. 민감 정보 금지, 앱 이름 생략. 긴급도를 정확하게(시간 민감은 지금~1시간 안의 일만). **필수:** 마케팅 알림은 명시적 동의를 받은 뒤에만, 시간 민감 알림을 마케팅에 쓰지 않고, 알림 설정을 앱 안에 제공한다.
- [실시간 현황(Live Activities)](https://developer.apple.com/kr/design/human-interface-guidelines/live-activities): 시작과 끝이 분명한 최대 8시간의 일. 핵심 정보만, 바뀔 때만 갱신하고 같은 변화에 푸시 알림을 같이 보내지 않는다. 누르면 관련 화면으로. 폭이 좁으면 약어·그래픽과 짧은 단위(영어판 최신 규칙).
- [위젯](https://developer.apple.com/kr/design/human-interface-guidelines/widgets): 앱의 핵심 목적 하나에 시의적절한 데이터. 누르면 해당 화면으로 딥링크. 자리표시보다 오래된 데이터를 보여 주고, 로그아웃 상태에서는 로그인하면 무엇이 생기는지 말한다.
- [설정](https://developer.apple.com/kr/design/human-interface-guidelines/settings): 좋은 기본값과 적은 설정. 앱이 감지할 수 있는 것은 묻지 않는다. 화면별 옵션은 그 화면에, 시스템 설정 페이지를 바로 여는 버튼을 둔다.
- [계정](https://developer.apple.com/kr/design/human-interface-guidelines/managing-accounts)·[데이터 입력](https://developer.apple.com/kr/design/human-interface-guidelines/entering-data)·[텍스트 필드](https://developer.apple.com/kr/design/human-interface-guidelines/text-fields): 로그인은 필요한 곳에서 최대한 늦게, 이유와 함께. 기본값을 채우고 입력 중에 검사한다. 필수 항목이 채워질 때만 다음을 켜고, 라벨은 항상 보이게. **필수:** 비밀번호는 미리 채우지 않는다. 시스템에서 얻을 수 있는 정보는 묻지 않는다.
- **필수:** 계정을 만들 수 있는 앱은 비활성화가 아닌 계정 삭제를 앱 안에서 제공하고, 앱 안에서 처리할 수 없으면 삭제 웹페이지로 바로 연결한다([계정](https://developer.apple.com/kr/design/human-interface-guidelines/managing-accounts)).
- 컴포넌트
    - [목록](https://developer.apple.com/kr/design/human-interface-guidelines/lists-and-tables): 행을 짧게, 긴 내용은 상세 화면으로. 선택은 체크 표시.
    - [버튼](https://developer.apple.com/kr/design/human-interface-guidelines/buttons): 44pt 이상, 화면당 눈에 띄는 버튼 1–2개, 파괴적 동작은 기본 버튼이 아니다. 오래 걸리면 버튼 안에 진행 표시.
    - [토글](https://developer.apple.com/kr/design/human-interface-guidelines/toggles): 반대 두 상태에만, 색만으로 표시하지 않는다.
    - [세그먼트 컨트롤](https://developer.apple.com/kr/design/human-interface-guidelines/segmented-controls): 선택이나 동작 중 하나에 쓰고 한 컨트롤 안에 섞지 않는다. 폰에서 최대 5개, 같은 너비, 글자나 아이콘 중 하나만.
    - [탭 바](https://developer.apple.com/kr/design/human-interface-guidelines/tab-bars): 탐색에만, 항상 보이게. 탭은 적을수록 쓰기 쉽고 "더 보기" 탭은 피한다. 탭을 숨기거나 끄지 않고 빈 내용을 설명한다. 배지는 중요한 새 정보에만.
- [레이아웃](https://developer.apple.com/kr/design/human-interface-guidelines/layout)·[타이포그래피](https://developer.apple.com/kr/design/human-interface-guidelines/typography)·[색](https://developer.apple.com/kr/design/human-interface-guidelines/color): 가장 중요한 내용을 위에, 관련 항목은 묶어서, 세부는 점진적으로. 큰 글자에서는 가로 배치를 세로로 쌓고 얇은 굵기를 피한다. 색마다 의미 하나, 고대비·야외 밝기에서도 확인한다.
- [다크 모드](https://developer.apple.com/kr/design/human-interface-guidelines/dark-mode): 앱 전용 화면 모드 설정을 두지 말고 시스템 설정을 따른다. 고대비·투명도 줄이기와 함께 확인한다.
- [햅틱](https://developer.apple.com/kr/design/human-interface-guidelines/playing-haptics): 시스템 햅틱을 의미에 맞게 쓰고, 과용하지 않고, 끌 수 있게 한다.
- [오른쪽에서 왼쪽(RTL)](https://developer.apple.com/kr/design/human-interface-guidelines/right-to-left): 뒤로·진행 방향 아이콘은 뒤집고, 숫자 자체와 실제 방향을 뜻하는 아이콘은 뒤집지 않는다.
- [평점과 리뷰](https://developer.apple.com/kr/design/human-interface-guidelines/ratings-and-reviews): 평점 요청은 사용자가 충분히 써 본 뒤 시스템 창으로 하고, 첫 실행이나 작업 도중에는 띄우지 않는다.
- 앱을 제출할 때 개인정보 처리 정보를 함께 낸다([개인정보](https://developer.apple.com/kr/design/human-interface-guidelines/privacy)).
- [실행 취소](https://developer.apple.com/kr/design/human-interface-guidelines/undo-and-redo)·[도움말](https://developer.apple.com/kr/design/human-interface-guidelines/offering-help): 실행 취소는 이름을 붙여 예측 가능하게("이름 변경 취소"). 팁은 한두 문장, 이미 쓰는 사람에게는 띄우지 않고 하루 한 번 정도, 쉽게 닫히게.

## 4. Material 3 / M3 Expressive 핵심 규칙

M3 Expressive의 핵심은 모양·색 대비·강조 글꼴·묶음으로 강조를 만들되, "히어로 순간"은 제품당 1–2개로 제한하는 것이다([소개 글](https://m3.material.io/blog/building-with-m3-expressive)). 아래 수치는 사이트와 Compose Material3 1.5.0-beta02 토큰에서 확인했다.

### 색

[역할](https://m3.material.io/styles/color/roles) · [색 적용](https://m3.material.io/styles/color/advanced/apply-colors) · [새 색 정의](https://m3.material.io/styles/color/advanced/define-new-colors) · [고도](https://m3.material.io/styles/elevation/applying-elevation)

- **필수:** 화면의 모든 색은 ColorScheme 역할을 쓰고, 글자·아이콘은 짝이 맞는 on-역할을 쓴다. hex 값을 화면에 직접 쓰지 않는다.
- 정적 브랜드 색을 쓰면 라이트·다크 모두 모든 역할(surfaceContainer 단계, Bright/Dim, Fixed, inverse)을 채운다. 빠뜨린 역할은 기본 보라색으로 나온다.
- primary는 가장 중요한 동작·활성 상태, secondary는 필터 칩·내비게이션 표시·톤 버튼, tertiary는 배지 같은 대비 강조에 쓴다.
- 본문은 surface, 내비게이션 영역은 surfaceContainer. 겹치는 컨테이너는 서로 다른 역할을 쓴다. 그림자는 적게, 구분은 톤 차이로.
- error 역할은 오류에 쓴다. 경고·성공은 네 가지 역할(색, on-색, 컨테이너, on-컨테이너)을 갖춘 별도 정적 색으로 정의한다.
- outline은 중요한 경계(3:1), outlineVariant는 구분선·카드 테두리·칩에 쓴다. Fixed 색은 라이트·다크에서 톤이 같으므로 대비가 중요한 곳에 쓰지 않는다.
- 다크 모드에서는 그림자가 거의 보이지 않으므로, 카드 구분은 톤 차이로 확인한다.

### 모양

[모서리 단계](https://m3.material.io/styles/shape/corner-radius-scale) · [모양 원칙](https://m3.material.io/styles/shape/overview-principles) · [모양 변형](https://m3.material.io/styles/shape/shape-morph)

- 단계: 0·4·8·12·16·20·28·32·48dp·full. Expressive에서 largeIncreased 20, extraLargeIncreased 32, extraExtraLarge 48이 추가됐다.
- 안쪽 모서리 = 바깥 모서리 − 여백(광학적 둥글기). 정보가 빽빽한 컨테이너에는 큰 모서리를 쓰지 않는다.
- 누른 버튼은 더 모나게 변한다. 장식용 도형(MaterialShapes, [모양 원칙](https://m3.material.io/styles/shape/overview-principles))은 이미지·아바타에만 쓰고 글이 많은 컨테이너에는 쓰지 않는다.

### 글꼴

[유형 단계 토큰](https://m3.material.io/styles/typography/type-scale-tokens) · [글꼴 적용](https://m3.material.io/styles/typography/applying-type)

| 역할 | 크기(sp) L/M/S | 기본 굵기 | 강조(Emphasized) 굵기 | 한국어 줄 높이(sp) L/M/S |
| --- | --- | --- | --- | --- |
| Display | 57 / 45 / 36 | 400 | 500 | 73 / 56 / 47 |
| Headline | 32 / 28 / 24 | 400 | 500 | 42 / 38 / 35 |
| Title | 22 / 16 / 14 | L 400, M·S 500 | L 500, M·S 700 | 31 / 27 / 23 |
| Body | 16 / 14 / 12 | 400 | 500 | 27 / 23 / 18 |
| Label | 14 / 12 / 11 | 500 | 700 | 23 / 18 / 18 |

- 한국어는 "medium" 언어 높이 그룹이라 줄 높이가 약 7% 높다. Compose에는 이 API가 없어 직접 지정해야 한다. 높이가 고정된 컴포넌트는 잘릴 수 있다.
- 강조 글꼴은 선택 상태·주요 동작·제목·안 읽음에만. 바뀌는 숫자(시각·카운트다운)는 고정폭 숫자(tabular figures)를 쓴다.
- 크기는 바꾸지 않고, 브랜드 글꼴은 display·headline에, 기본 글꼴은 body·label에 쓴다.

### 모션

[명세](https://m3.material.io/styles/motion/overview/specs) · [동작 원리](https://m3.material.io/styles/motion/overview/how-it-works) · [전환 패턴](https://m3.material.io/styles/motion/transitions/transition-patterns) · [모션 테마](https://m3.material.io/blog/m3-expressive-motion-theming)

| 스프링(감쇠/강성) | 빠름 | 기본 | 느림 |
| --- | --- | --- | --- |
| Expressive 공간(spatial) 스프링: 위치·크기·모양 | 0.6 / 800 | 0.8 / 380 | 0.8 / 200 |
| Standard 공간(spatial) 스프링 | 0.9 / 1400 | 0.9 / 700 | 0.9 / 300 |
| 효과(effects) 스프링: 색·투명도, 두 스킴 공통 | 1.0 / 3800 | 1.0 / 1600 | 1.0 / 800 |

- Expressive 스킴은 대부분의 상황에 권장된다(Compose의 기본 MaterialTheme은 standard 스킴, MaterialExpressiveTheme은 expressive 스킴을 쓴다). 공간 스프링은 튀어도 되고 효과 스프링은 튀지 않는다. 직접 만드는 애니메이션도 tween 대신 motionScheme 스프링을 쓴다.
- 속도: 작은 컴포넌트는 빠름, 화면 일부는 기본, 전체 화면은 느림.
- 내비게이션 바 목적지 전환은 페이드 스루(나감 후 들어옴)다. 옆으로 미는 전환은 탭·캐러셀에만. 동작 줄이기가 켜지면 패럴랙스·모양 변형을 끈다. 내용이 툭 튀어나오기보다 스켈레톤을 쓴다.

### 컴포넌트

- [내비게이션 바](https://m3.material.io/components/navigation-bar/guidelines): 목적지 3–5개, 아이콘과 줄이지 않는 라벨. 선택은 채운 아이콘·표시기, 다시 누르면 맨 위로. 목적지 사이 스와이프 금지. Expressive는 64dp 플렉서블 바(표시기 56×32dp, secondaryContainer)를 권장하고 80dp 기본 바는 권장하지 않는다. 화면 낭독기가 켜져 있으면 스크롤해도 내비게이션 바를 숨기지 않는다. 태블릿 등 넓은 창은 [내비게이션 레일](https://m3.material.io/components/navigation-rail/overview).
- [상단 앱 바](https://m3.material.io/components/app-bars/guidelines): 제목과 필수 동작 1–2개. 평상시 surface, 스크롤하면 surfaceContainer(그림자 없음). Small 64dp, Medium flexible 112dp, Large flexible 120dp. 제목은 자르지 않는다.
- [카드](https://m3.material.io/components/cards/guidelines)·[카드 접근성](https://m3.material.io/components/cards/accessibility): 한 번에 누르는 대상이거나 자기 버튼을 담는 컨테이너 중 하나(둘 다는 안 됨). 12dp 모서리, 16dp 여백, 카드 사이 8dp 이하, 카드 안 스크롤 금지. elevated=surfaceContainerLow, filled=surfaceContainerHighest, outlined=surface+outlineVariant. 여백이나 구분선으로 충분하면 카드로 감싸지 않는다.
- [목록](https://m3.material.io/components/lists/guidelines): 행 전체가 터치 대상. 켜기/끄기는 스위치, 하나 선택은 라디오, 여러 개는 체크박스. 선택은 색 외의 방법으로도 표시. Expressive 세그먼트 목록은 2dp 간격, 바깥 16dp·안쪽 4dp 모서리.
- [대화상자](https://m3.material.io/components/dialogs/guidelines): 막아야 하는 결정에만. 동작 최대 2개, 끝쪽에 텍스트 버튼으로 확인. 선택이 필요하면 고를 때까지 확인을 끄고, 닫기는 끄지 않는다. 자판이 필요한 양식은 전체 화면 대화상자(닫기 X, 구체적 동사, 버리기 확인).
- [바텀시트](https://m3.material.io/components/bottom-sheets/guidelines): 긴 선택지 목록에. 처음에는 화면 높이의 50% 이하로 연다.
- [스낵바](https://m3.material.io/components/snackbar/guidelines): 낮은 우선순위 피드백, 한 번에 하나, 동작 최대 1개, 1–2줄, inverseSurface, 내비게이션 바 위. 동작이 없으면 4–10초 후 사라짐. 핵심 기능의 유일한 길이 되지 않는다.
- [버튼](https://m3.material.io/components/buttons/guidelines)·[FAB](https://m3.material.io/components/floating-action-button/guidelines): 채운 버튼은 화면당 하나, 라벨 1–3단어. FAB는 주요 생성 동작에만, 끄기보다 숨기기 [미확인]. 채운 버튼과 FAB를 함께 두지 않는다(근거는 [툴바](https://m3.material.io/components/toolbars/guidelines) 페이지).
- [버튼 그룹](https://m3.material.io/components/button-groups/guidelines): 세그먼트 버튼은 더 이상 권장하지 않고 연결형 버튼 그룹을 쓴다. 2dp 간격, 컨테이너 너비, 줄바꿈 없음, 버튼마다 48dp, 선택 상태를 낭독.
- [툴바](https://m3.material.io/components/toolbars/guidelines)·[FAB 메뉴](https://m3.material.io/components/fab-menu/guidelines): 내비게이션 바와 도킹 툴바를 함께 두지 않는다. FAB 메뉴는 2–6개.
- [로딩 표시](https://m3.material.io/components/loading-indicator/guidelines)·[진행 표시](https://m3.material.io/components/progress-indicators/guidelines): 200ms 미만은 아무것도 표시하지 않고, 200ms–5초는 48dp LoadingIndicator(내용 위·당기기 새로고침은 contained), 5초 이상이거나 진행률을 알면 진행 표시. 항목 묶음당 표시 하나. 로딩 표시를 진행 표시로 바꾸지 않는다.
- [배지](https://m3.material.io/components/badges/guidelines): 점은 새것, 숫자는 4자 이내. 방문하면 지운다 [미확인].
- [칩](https://m3.material.io/components/chips/guidelines): 상호작용 요소다. 누를 수 없는 것을 칩처럼 꾸미지 않는다. 필터 칩은 20자 이하 명사. 칩 묶음이 넘치면 가로로 스크롤하거나 다음 줄로 넘긴다.
- [날짜 선택기](https://m3.material.io/components/date-pickers/guidelines)·[시간 선택기](https://m3.material.io/components/time-pickers/guidelines): 직접 입력을 허용하고 시스템 12/24시간 설정을 따른다. 생일처럼 먼 날짜는 달력 대신 입력으로 받는다. 다이얼이 잘리거나 스크롤되지 않게.
- [텍스트 필드](https://m3.material.io/components/text-fields/guidelines): 양식당 필드 스타일 하나, 라벨은 항상 보이게, 필수는 별표. 오류 문구가 도움말을 대신하며 고치는 법을 말한다. 비밀번호 보기 토글에는 라벨.
- [검색](https://m3.material.io/components/search/guidelines): 검색창은 검색 대상 내용의 맨 위에, 힌트는 무엇을 찾을 수 있는지 말한다.

### 레이아웃·접근성·글쓰기

- [레이아웃](https://m3.material.io/foundations/layout/breakpoints/compact): 폰은 한 화면, 16dp 여백, 목적지 3–5개면 내비게이션 바. 목록·상세는 폰에서 둘 중 하나를 보여 주고 스크롤·읽음 상태를 유지한다([목록-상세](https://m3.material.io/foundations/layout/canonical-examples/list-detail)).
- [큰 화면·폴더블](https://m3.material.io/foundations/layout/breakpoints/overview): 창 크기는 멀티 윈도·펼침으로 바뀌므로 기기 종류가 아니라 창 너비 구간(breakpoint)으로 판단한다. 600dp부터 내비게이션 레일, expanded 구간은 목록-상세 두 창.
- [제스처](https://m3.material.io/foundations/interaction/gestures): Android 예측형 뒤로 가기를 지원한다(바텀시트 등).
- [접근성 구조](https://m3.material.io/foundations/designing/structure)·[색 대비](https://m3.material.io/foundations/designing/color-contrast)·[글자 크기](https://m3.material.io/foundations/writing/text-resizing): 터치 48×48dp, 사이 8dp. 200% 글자에서 컨테이너는 커지고, 나란한 버튼은 쌓이고, 세로로만 스크롤한다. 라벨에 역할 단어("버튼")를 넣지 않는다. 대화상자에 포커스가 들어가고 닫으면 연 곳으로 돌아간다.
- [UX 라이팅](https://m3.material.io/foundations/content-design/style-guide/ux-writing-best-practices)·[문법과 문장부호](https://m3.material.io/foundations/content-design/style-guide/grammar-and-punctuation)·[알림](https://m3.material.io/foundations/content-design/notifications): 결과를 중립적으로, "정말 ~할까요?" 같은 모호한 질문 금지, 빈 화면에 "!" 금지, 버튼에 "…" 금지. 알림은 핵심 사실을 앞에, 동작 최대 2개, 앱 이름 생략, 온보딩 중에는 보내지 않는다.

### Compose 구현 메모

Material3 1.5.0-beta02(2026-10-21, 아직 베타) 기준이다. 버전이 다르면 [릴리스 노트](https://developer.android.com/jetpack/androidx/releases/compose-material3)로 다시 확인한다.

- 실험 표시(opt-in) 없이 쓸 수 있음: MaterialExpressiveTheme, MotionScheme.expressive(), Typography.*Emphasized, ShortNavigationBar, WideNavigationRail, MediumFlexibleTopAppBar, ButtonGroup·ToggleButton, SegmentedListItem, ButtonDefaults.shapes().
- 아직 실험 API(@ExperimentalMaterial3ExpressiveApi): LoadingIndicator, PullToRefreshDefaults.LoadingIndicator, MaterialShapes.
- 1.4.0 안정판에는 Expressive API가 없다. 빌드 요구 사항(compileSdk·AGP)은 버전마다 바뀌므로 릴리스 노트에서 확인한다.
- "권장하지 않음"(SegmentedButton, 80dp NavigationBar)은 가이드에만 있고 코드에는 폐기 표시가 없다.
- m3.material.io는 JavaScript 앱이라 단순 웹 가져오기로는 제목만 나온다. 브라우저로 렌더링해서 읽어야 한다.

## 5. Toss UI/UX 가이드 핵심 규칙

Toss 가이드는 한 페이지(브랜딩, 다크패턴 방지, UX 라이팅, 그래픽, 해상도)에 출시 체크리스트·TDS 컴포넌트·접근성 가이드가 딸려 있다. 어기면 심사에서 떨어지고 출시 후 노출이 제한될 수 있는 최소 기준이다.

### UX 라이팅 (한국어)

[원문](https://developers-apps-in-toss.toss.im/design/consumer-ux-guide)

- 모든 문구는 해요체로 쓴다.
- 능동형을 기본으로 한다(됐어요 → 했어요). '~되었어요·~됐어요' 같은 피동 표현과 '-아/어지다' [미확인]를 직접 동사로 바꾼다.
- 긍정형으로 쓴다. "안 돼요/없어요" 대신 "~하면 할 수 있어요"처럼 가능하게 하는 조건을 말한다.
- 오류 문구도 긍정형이다. 대화상자 제목은 해야 할 행동, 이유는 본문에 둔다 [미확인].
- 일부 혜택만 못 받으면 여전히 받는 것을 말한다(사용자는 훑어 읽어 부정문을 "아무것도 안 됨"으로 읽는다).
- 높임은 가볍게: ~시겠어요·~시나요·~께를 쓰지 않는다. 계시다→있다, 여쭈다→확인하다·묻다, 께→에게.
- '시'를 빼서 어색한 질문은 정보를 주어로 하거나 선택하게 한다.
- 한자어 명사 나열을 피하고 동사로 풀어 쓴다.
- **필수:** "되어요"가 아니라 "돼요".
- 예외: 서비스 종료·만료에는 수동형이 괜찮지만, 정기적으로 반복되는 끝("운행 종료돼요")에는 쓰지 않는다. 사용자 자신의 행동 결과(연체·해지·적용)와 개인정보 수집 안내의 안심 문구도 예외다. 높임 질문은 사용자 상황을 쓰거나 불확실한 판단을 부탁하거나 설문처럼 호의를 구할 때 괜찮다. 정책상 막힘(이유 명시), 기능 하나가 안 될 때(이름 명시), 안심 문구에는 부정형이 괜찮다.
- 욕설·은어·과한 유행어는 쓰지 않는다.

### 버튼·대화상자

- 대화상자의 닫기(왼쪽) 버튼은 "닫기"다. "취소"는 사용자의 작업을 취소하는 것으로 읽히기 때문이다.
- CTA는 다음에 일어날 일을 말한다. 화면 홍보 문구를 반복하거나 과장·중복된 도움말을 위에 두지 않는다(좋은 예: "이동하기" [미확인]).
- 주요 버튼은 fill, 보조는 weak. 로딩 중에도 버튼 너비 유지, 비활성은 비활성처럼 보이게, 파괴적 버튼의 화면 낭독용 라벨에는 경고 맥락을 덧붙인다(예: "계정 삭제 - 주의 필요", [TDS 버튼](https://tossmini-docs.toss.im/tds-mobile/components/button/)).

### 다크패턴 방지

- **필수:** 화면이 열리자마자 바텀시트(프로모션·로그인·알림 동의)를 띄우지 않는다.
- **필수:** 뒤로 가기를 가로채는 시트(알림 유도 등)를 띄우지 않는다.
- 거절하거나 나갈 길을 항상, 모든 화면에서 분명히 둔다.
- 화면 전환 중 행동을 강요하는 시트, 예상 못한 전체 화면 광고, 자기 앱·사이트로 밀어내기를 하지 않는다.
- 애원하거나 죄책감을 주는 이미지를 쓰지 않는다.

### 내비게이션·권한·성능

[비게임 출시 체크리스트](https://developers-apps-in-toss.toss.im/checklist/app-nongame) · [내비게이션 바](https://developers-apps-in-toss.toss.im/documentation/common/navigationbar)

- 뒤로 가기는 딥링크로 들어온 경우를 포함해 모든 화면에서 동작한다. Android 시스템 뒤로는 뒤로 가거나 종료하고, 첫 화면에서는 종료한다. 뒤로 버튼을 두 개 두지 않고 뒤로와 닫기를 헷갈리지 않게.
- 상단 바의 추가 동작은 최대 하나, 단색 아이콘만.
- 탭 바는 선택 사항이며 쓰면 2–5개. 빨간 점은 중요한 업데이트에만, 라벨에 "탭"을 넣지 않는다 [미확인].
- 기기 권한(위치·알림)은 동의를 먼저 받고, 거절해도 나머지는 동작한다. 로그인을 쓰면 묻기 전에 소개 화면이 서비스를 설명하고, 연결을 끊으면 사용자 데이터가 남지 않는다.
- 스크롤·터치·화면 전환이 2초 이상 지연되지 않는다. 기다릴 것이 없으면 로딩 애니메이션을 쓰지 않고, 진짜 오류가 아니면 "!" 아이콘을 쓰지 않는다. 스켈레톤은 최종 레이아웃을 닮게.
- 재시도는 횟수·간격을 제한하고, 반복·타이머·새로고침으로 중복 호출하지 않는다. 필요한 데이터는 재실행 후에도 남는다.
- API는 HTTPS만 쓰고, 민감 정보는 암호화해 저장하며, 네트워크·메모리 사용량이 갑자기 늘지 않게 한다.

### 화면 구조·목록·그래픽

- 화면은 Top 헤더와 기본 여백의 ListRow로 구성한다(375px 기준, [디자인 도구](https://developers-apps-in-toss.toss.im/design/prepare/design)). 제목은 heading 1, 부제는 heading 2, 정적 제목과 누를 수 있는 제목은 다르게 보인다.
- ListRow: 앞 아이콘 → 텍스트 → 뒤 정보·동작. 쉐브론이나 터치 피드백으로 누를 수 있음을 보이고, 비활성 행은 누를 수 있어 보이지 않게. 강조 효과는 새것·중요한 것에만 드물게([TDS ListRow](https://tossmini-docs.toss.im/tds-mobile/components/ListRow/list-row-overview/)).
- ListHeader는 누를 수 있을 때만 쉐브론. 글자가 200% 이상이면 제목과 오른쪽 영역의 너비 비율을 0.5로 고정한다. 목록 항목 사이는 선, 섹션 사이는 16px 간격.
- 배지는 중요한 상태에 fill, 그 외 weak.
- 그래픽은 꾸밈이 아니라 설명이다. 화면당 핵심 그래픽 하나, 핵심 정보를 스크롤 아래로 밀지 않는다. 파티클·강한 그라데이션 금지. 라이트·다크 양쪽에서 읽히게 중간 톤. 아이콘은 24–40px, 두 개를 나란히 두지 않는다.
- 한 논리 해상도로 설계해 확대하고(1x/2x 에셋), 화면비가 다르고 카메라 컷아웃이 크고 화면이 작은 기기 3–5대에서 시험한다.

### 접근성 (Toss가 링크한 [Frontend Fundamentals 접근성 가이드](https://frontend-fundamentals.com/a11y/))

- 모든 컨트롤은 역할·이름·상태를 노출한다. 컨트롤 이름은 동작으로, 화살표에는 방향을. placeholder는 라벨이 아니다.
- 이름이 같은 컨트롤에는 보이는 단어를 유지한 채 항목 맥락을 더한다.
- 의미 있는 이미지는 목적을 구체적으로 설명하고, 장식·중복 이미지는 숨긴다. 라벨에 "아이콘"·"버튼"을 넣지 않는다.
- 차트·진행 표시·로딩은 값이나 단계를 알린다. 선택·체크·펼침·현재(오늘 날짜)·바쁨 상태를 노출한다. 업데이트는 polite, 급한 오류만 assertive로 알린다.
- 모달: 포커스가 들어가고, 뒤로·ESC로 닫히고, 배경은 비활성, 닫으면 연 곳으로 포커스가 돌아가며, 제목이 있다.
- 상호작용 요소 안에 상호작용 요소를 넣지 않고, 보이는 대로 동작한다. 모든 입력 필드에 라벨, Enter로 제출.

## 6. 가이드끼리 충돌할 때

판단 순서: 플랫폼의 시스템 동작과 접근성 > 사용자의 데이터·시간을 지키는 쪽 > 제품의 한국어 말투 > 시각 취향. 브랜드 결정으로 기준에서 벗어나면 이유를 기록한다.

| 주제 | Apple HIG | Material 3 | Toss | 권장 판단 |
| --- | --- | --- | --- | --- |
| 대화상자의 닫는 버튼 이름 | 알림창은 "취소", 시트는 "취소" 또는 "닫기" | 닫기 동작(보통 "취소") | 항상 "닫기"("취소"는 작업 취소로 읽힘) | 사용자의 입력·작업을 버리면 "취소", 단순히 창만 닫으면 "닫기". 앱 안에서 하나로 통일 |
| 권한 사전 안내 화면의 나갈 길 | 버튼 하나("계속"), 시스템 창을 거치지 않고 나가는 버튼 없음(법적 동의 예외), 오해를 부르면 심사 거절 | 해당 없음 | 모든 화면에 나갈 길 | 사전 화면에는 "계속" 하나만 둔다. 나갈 길은 시스템 권한 창의 거절이 맡는다 |
| 되돌릴 수 있는 삭제 | 확인 대신 실행 취소 | 스낵바 실행 취소 | "…할까요?" 확인 [미확인] | 되돌릴 수 있으면 실행 취소, 되돌릴 수 없으면 확인 대화상자 |
| 하단 탭 개수 | 적을수록 좋고 "더 보기" 탭은 피함 | 3–5개 | 2–5개 | 3–5개. 나머지는 설정이나 상단 동작으로 |
| 하나 고르기 컨트롤 | 세그먼트 컨트롤(최대 5, 같은 너비) | 연결형 버튼 그룹(세그먼트 버튼 비권장) | TDS 탭·세그먼트 | Android는 연결형 버튼 그룹, iOS는 세그먼트 컨트롤 |
| 대화상자 확인 버튼 모양 | 기본 버튼 강조 | 끝쪽 텍스트 버튼 | fill/weak 버튼 | 플랫폼을 따른다. 파괴적 확인은 오류 색 텍스트 |
| 탭 전환 애니메이션 | 시스템 기본 | 페이드 스루 | 언급 없음 | Android는 페이드 스루, 좌우 슬라이드는 스와이프 가능한 탭에만 |
| 저절로 닫히는 UI | 최소화하고 직접 닫게 함 | 동작이 있는 스낵바는 저절로 닫지 않음 | 언급 없음 | 사용자가 행동해야 하는 메시지는 저절로 사라지게 하지 않는다 |
| 한국어판과 영어판이 다를 때 | 영어판이 원문 | 해당 없음 | 해당 없음 | 영어판을 따른다 |

## 7. 적용 체크리스트

새 화면을 만들거나 리뷰할 때 위에서부터 확인한다.

**흐름·권한**

- [ ] 앱을 열자마자 뜨는 대화상자·시트가 없다(업데이트·로그인·알림 동의 포함)
- [ ] 권한은 그 기능을 처음 쓸 때 한 문장 이유와 함께 묻고, 거절해도 나머지가 동작한다
- [ ] 시스템 설정에서만 켤 수 있는 권한은 그 페이지를 바로 여는 버튼이 있다
- [ ] 모든 화면에서 뒤로 가기가 동작하고(딥링크 포함), 나갈 길이 분명하다
- [ ] 모달은 한 번에 하나, 제목이 있고, 저장 안 된 입력은 버리기 전에 묻는다
- [ ] 계정을 만들 수 있으면 앱 안에서 계정을 삭제할 수 있다
- [ ] Android 예측형 뒤로 가기를 지원한다

**레이아웃·컴포넌트**

- [ ] 카드는 통째로 누르는 대상이거나 자기 버튼을 담는 컨테이너 중 하나다
- [ ] 화면당 강조(채운) 버튼은 하나, FAB와 함께 두지 않는다
- [ ] 누를 수 없는 것은 칩·버튼처럼 보이지 않는다
- [ ] 하나 고르기는 연결형 버튼 그룹(Android)이나 세그먼트(iOS), 켜기/끄기는 스위치다
- [ ] 하단 탭은 3–5개, 선택은 채운 아이콘과 표시기, 다시 누르면 맨 위로

**색·글꼴·모션**

- [ ] 모든 색은 색 역할 토큰이고, 라이트·다크 모두 모든 역할이 정의돼 있다
- [ ] 화면 모드는 시스템 설정을 따른다(앱 전용 다크 모드 설정 없음)
- [ ] 오류 색은 오류에만, 경고는 별도 색 4역할로
- [ ] 바뀌는 숫자는 고정폭 숫자, 강조 글꼴은 제목·선택·주요 동작에만
- [ ] 애니메이션은 모션 스킴 스프링을 쓰고, 동작 줄이기에서 꺼진다

**로딩·피드백**

- [ ] 처음부터 캐시나 스켈레톤이 보이고, 200ms 미만 작업에는 로딩 표시가 없다
- [ ] 당기기 새로고침에는 상단 새로고침 버튼 같은 대안이 있다
- [ ] 되돌릴 수 있는 동작은 실행 취소, 되돌릴 수 없는 동작만 확인 대화상자

**접근성**

- [ ] 터치 48dp(Android)·44pt(iOS) 이상, 글자 200%에서 잘림 없음
- [ ] 대비 4.5:1(작은 글자)·3:1(큰 글자·아이콘), 색만으로 의미를 전하지 않음
- [ ] 모든 컨트롤에 동작 이름과 상태(선택·펼침·오늘), 라벨에 "버튼"·"아이콘" 없음
- [ ] 화면 낭독기가 켜져 있으면 스크롤해도 내비게이션 바가 숨지 않는다

**문구(한국어)**

- [ ] 해요체, 능동형, "돼요", 한자어 나열 없음
- [ ] 버튼은 결과를 말하는 동사, 오류는 고치는 법을 말한다
- [ ] 빈 화면은 다음 할 일을 버튼으로 준다
- [ ] 반복되는 끝을 "종료돼요"로 쓰지 않는다

## 8. 참고 링크

**Apple HIG (한국어판)**

- 전체: https://developer.apple.com/kr/design/human-interface-guidelines/
- 한국어판 본문은 브라우저에서 렌더링되므로, 자동화 도구로 읽을 때는 데이터 피드 `https://developer.apple.com/tutorials/data/kr/design/human-interface-guidelines/<slug>.json`을 쓰면 된다
- 영어판: https://developer.apple.com/design/human-interface-guidelines/

**Material 3**

- 시작하기: https://m3.material.io/get-started
- M3 Expressive 소개: https://m3.material.io/blog/building-with-m3-expressive
- 모션 테마: https://m3.material.io/blog/m3-expressive-motion-theming
- 색 역할: https://m3.material.io/styles/color/roles
- 글꼴 토큰: https://m3.material.io/styles/typography/type-scale-tokens
- 모서리 단계: https://m3.material.io/styles/shape/corner-radius-scale
- 모션 명세: https://m3.material.io/styles/motion/overview/specs
- 컴포넌트 목록: https://m3.material.io/components
- 창 크기 구간(breakpoint): https://m3.material.io/foundations/layout/breakpoints/overview
- Compose Material3 릴리스 노트: https://developer.android.com/jetpack/androidx/releases/compose-material3

**Toss**

- UI/UX 가이드: https://developers-apps-in-toss.toss.im/design/consumer-ux-guide
- 비게임 출시 체크리스트: https://developers-apps-in-toss.toss.im/checklist/app-nongame
- 게임 출시 체크리스트: https://developers-apps-in-toss.toss.im/checklist/app-game
- 내비게이션 바: https://developers-apps-in-toss.toss.im/documentation/common/navigationbar
- 디자인 도구: https://developers-apps-in-toss.toss.im/design/prepare/design
- TDS Mobile(시작하기·컴포넌트): https://tossmini-docs.toss.im/tds-mobile/start/
- 접근성 가이드(Frontend Fundamentals): https://frontend-fundamentals.com/a11y/
