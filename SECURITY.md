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
- 기숙사 사진은 인증 없는 Cloudflare Worker endpoint로 제출합니다. Worker는 이미지 형식·크기와 짧은 IP 해시 기반 중복 제한을 확인하고, 이 저장소에만 설치된 최소 권한 GitHub App의 단기 설치 토큰으로 `dorm-submissions` 브랜치의 신규 이미지 경로만 씁니다. GitHub App 개인 키와 rate-limit salt는 Wrangler Secret으로만 보관하며 APK·Git·로그에 포함하지 않습니다.
- 제출 workflow는 직렬 실행하고, OCR 전에 현재 주 기숙사 식단 존재 여부를 다시 확인합니다. 검증 실패 사진은 정상 식단 캐시를 덮어쓰지 않습니다.
- 셔틀 미도착 API는 현재 게시된 셔틀 revision과 서버가 계산한 물리 운행·정류장 호출만 허용합니다. 신고 가능 시간은 예정 시각부터 다음 동일 정류장 차량 또는 15분 중 이른 시각까지이며, 설치 토큰은 서버 전용 HMAC 키로 해시해 D1에 보관합니다. 원시 토큰·GPS 좌표·계정은 저장하지 않고 신고 행은 7일 뒤 정리합니다.
- 셔틀 신고 endpoint는 Cloudflare rate-limit binding과 설치별 물리 정류장 호출 단일 PK로 중복을 제한합니다. `SHUTTLE_REPORT_HMAC_KEY`는 Worker Secret, 신고 데이터는 전용 D1에만 두며 APK·Git·로그에 포함하지 않습니다.
- 앱 업데이트는 공개 GitHub latest stable release의 이름 규칙과 asset SHA-256을 확인합니다.
- 업데이트 다운로드는 허용된 GitHub HTTPS 호스트와 128 MiB 상한으로 제한하고, 완료 전 `.part` 파일은 설치 대상으로 사용하지 않습니다.
- 설치 전 APK의 패키지명, versionName, 더 높은 versionCode와 현재 설치본과 동일한 서명 인증서를 확인합니다. Android의 앱별 설치 출처 권한과 시스템 설치 확인 화면을 우회하지 않습니다.
- LMS 자동 로그인 계정은 `dima_now_lms_credentials_v1` Android Keystore AES-256-GCM 키로 암호화하고 백업 제외 파일에만 저장합니다. 키 손상·무효화·암호문 변조 시 파일을 폐기하고 재입력을 요구합니다.
- LMS WebView의 메인 탐색과 WebView가 직접 전달하는 HTTP(S) 요청은 정확한 `lms.dima.ac.kr`·`portal.dima.ac.kr` HTTPS 호스트만 허용하고, 파일/콘텐츠 접근과 mixed content를 차단하며 `addJavascriptInterface`를 사용하지 않습니다. Android WebView는 하위 리소스의 30x 후속 URL을 다시 전달하지 않을 수 있으므로 이 경계를 모든 리다이렉트 홉의 완전한 네트워크 allowlist로 과장하지 않습니다. 계정 주입은 공식 포털의 `#txtID`·`#txtPwd`와 `Login('N')`, 또는 공식 LMS 로그인 화면의 `#id`·`#pass`와 `login_proc()` 조합에만 한정하고, 같은 form에 추가 인증 입력이 있으면 값을 쓰기 전에 중단합니다. 공지·자료·과제 본문은 WebView가 아닌 sanitizer를 거친 네이티브 UI로 표시합니다. 영상은 목록과 서버 제공 완료 상태 표시가 우선이며, 사용자가 선택한 CONTENT와 미지원 항목의 공식 화면만 같은 제한 세션 안에서 후순위로 엽니다.
- 자동 로그인은 직렬화된 1회 시도만 수행하고 실패 자격 증명을 반복하지 않습니다. 중복 세션은 정확한 HTTPS `portal.dima.ac.kr/sso/error.aspx?errorCode=3045`에서 공식 페이지의 검증된 전환 동작만 한 번 제출합니다. 반복 3045·다른 호스트·동작 불일치·CAPTCHA·OTP·추가 인증·계정 잠금은 우회하지 않으며 네트워크 실패 자동 재시도는 15분 억제합니다.
- LMS 목록 동기화는 `전체 학습`만 사용하고 상세 페이지를 미리 열지 않습니다. 완료·미완료 보조 목록 실패는 전체 목록 실패로 확대하지 않으며 이전 상태를 보존합니다. 사용자가 상세를 열 때만 최신 본문과 첨부 메타데이터를 재확인합니다. 상세 HTML은 script/form/iframe과 이벤트 속성을 제거합니다.
- 첨부파일은 실제 상세 final URL과 Referer, 공식 form의 method/필드를 보존한 같은 호스트 요청으로만 받습니다. 512 MiB 상한, 0바이트, 선언 길이 불일치, 로그인·오류 HTML, 비-LMS redirect를 검증하고 앱 전용 임시 캐시에 완전히 받은 뒤에만 Android 문서 출력을 엽니다. 출력 스트림 실패나 부분 복사는 성공 처리하지 않고 생성된 빈 문서를 최선 노력으로 제거하며, 임시 파일은 성공·실패·취소 경로에서 정리합니다.
