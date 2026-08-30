# 보안 정책

## 지원 범위

현재 최신 GitHub Release와 `main` 브랜치를 대상으로 보안 문제를 확인합니다. 개인 설치용 프로젝트이므로 장기 지원 버전은 별도로 운영하지 않습니다.

## 취약점 제보

보안 문제에는 공개 Issue를 사용하지 말고 GitHub 저장소의 **Security → Report a vulnerability**를 사용해 주세요. 재현 조건, 영향 범위, 영향을 받는 버전과 필요한 최소 로그를 포함하되 위치 정보·시간표·기기 식별자·토큰 등 개인 데이터는 제거해 주세요.

실제 사용자나 외부 서비스를 대상으로 한 공격, 과도한 자동 요청, DIMA 또는 Instagram 계정 접근은 하지 마세요.

## 데이터 신뢰 경계

- 앱은 HTTPS 정적 manifest와 content-addressed JSON만 받습니다.
- payload 상대 경로, schema version, SHA-256, 허용된 공식 원문 URL을 검증합니다.
- 검증 실패 시 마지막 정상 Room 캐시를 유지합니다.
- GitHub Actions workflow는 고정 commit SHA의 Actions를 사용하며 최소 권한을 선언합니다.
- 식단 OCR용 `GEMINI_API_KEY`는 GitHub Actions Secret으로만 주입하고 요청 본문·로그·아티팩트에 기록하지 않습니다.
- 기숙사 사진 제출은 이 저장소에만 설치된 전용 GitHub App user token으로 수행하며, 앱 코드는 쓰기 경로를 `dorm-submissions` 브랜치의 신규 이미지로 고정합니다. 앱은 client ID만 포함하고 비밀키를 포함하지 않으며, user token은 Android Keystore 키로 암호화합니다.
- 제출 workflow는 직렬 실행하고, OCR 전에 현재 주 기숙사 식단 존재 여부를 다시 확인합니다. 검증 실패 사진은 정상 식단 캐시를 덮어쓰지 않습니다.
- 앱 업데이트는 공개 GitHub latest stable release의 이름 규칙과 asset SHA-256을 확인합니다.
- 업데이트 다운로드는 허용된 GitHub HTTPS 호스트와 128 MiB 상한으로 제한하고, 완료 전 `.part` 파일은 설치 대상으로 사용하지 않습니다.
- 설치 전 APK의 패키지명, versionName, 더 높은 versionCode와 현재 설치본과 동일한 서명 인증서를 확인합니다. Android의 앱별 설치 출처 권한과 시스템 설치 확인 화면을 우회하지 않습니다.
