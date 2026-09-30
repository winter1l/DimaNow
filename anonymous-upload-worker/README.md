# DIMA Now 익명 데이터 게이트웨이

Android 앱에서 GitHub 로그인 없이 식단 사진을 제출하기 위한 Cloudflare Worker입니다. 앱은 사진만 전송하며 GitHub 쓰기 자격 증명을 포함하지 않습니다. Worker가 서버에 보관한 GitHub App 개인 키로 짧은 설치 토큰을 발급하고 `dorm-submissions` 브랜치에 새 사진 한 장을 기록합니다.

## 보안 경계

- JPEG, PNG, WebP만 허용하며 파일 시그니처와 15 MiB 제한을 확인합니다.
- Cloudflare가 제공한 요청 IP의 단방향 해시를 D1에서 원자적으로 claim해 10분 중복 제출을 막고, 일간 20건·주간 40건의 전체 처리 예산을 GitHub 권한보다 먼저 적용합니다.
- GitHub App 권한은 이 저장소의 `Contents: read and write`와 필수 `Metadata: read-only`만 사용합니다.
- `GITHUB_APP_PRIVATE_KEY`와 `RATE_LIMIT_SALT`는 Wrangler Secret으로만 저장합니다. APK, Git, 로그에 넣지 않습니다.
- 업로드된 사진은 공개 저장소 브랜치와 GitHub Actions 처리 대상이 됩니다. 앱은 전송 전 공개 공유 사실을 확인합니다.
- 두 단계 모델 검증과 OCR, 현재 KST 주(주말에는 다음 주)·중복 주 확인을 통과한 사진만 GitHub Actions가 자동으로 `READY`와 `PUBLISHED`로 게시합니다(D-089). 실패는 짧은 사유와 함께 `REJECTED`/`ERROR`로 기록되고 기존 정상 식단은 유지됩니다.

## 검증

학생식당 게시 지연을 보완하는 scheduled handler도 제공합니다. 공개 식단의 SHA-256과 현재 주 월~금 데이터를 확인하고, 새 식단이 없고 최근 수집 시도도 없으면 기존 GitHub App 권한으로 `student-meal-publication-watch` 이벤트를 보냅니다. 월요일 오전에는 30분, 그 밖에는 두 시간 간격이며 현재 주 식단이 완성되면 수집을 건너뜁니다. GitHub `publish-data.yml`의 동일한 `repository_dispatch` 수신 설정이 필요합니다. 공개 HTTP 요청에서는 이 동작을 실행할 수 없습니다. 세 개 cron 중 매일 17:07 UTC 실행에서 기존 정리 작업도 수행합니다.

2026-09-14 배포는 기존 운영 Worker에 이 보완 경로만 적용했습니다. 로컬 소스의 다른 보안 변경 배포 여부와 검증 범위는 `../memory/meal-delay-fix-20260914.md`를 확인하세요.

```powershell
node --test
npx wrangler deploy --dry-run
```

## 최초 배포

1. `npx wrangler login`
2. `npx wrangler d1 create dima-now-shuttle-reports` 후 `SHUTTLE_REPORTS` binding을 연결 (이름은 과거 셔틀 신고 기능에서 온 것이며, 지금은 업로드 admission 테이블만 사용)
3. `npx wrangler d1 migrations apply dima-now-shuttle-reports --remote`로 migration을 적용 (`0001`의 신고 테이블은 더 이상 쓰지 않으며, `0002_gateway_admission.sql`의 원자 admission 테이블을 사용)
4. PKCS#8 PEM 형식의 GitHub App 개인 키를 `npx wrangler secret put GITHUB_APP_PRIVATE_KEY`로 등록
5. 충분히 긴 무작위 값을 `npx wrangler secret put RATE_LIMIT_SALT`로 등록
6. `wrangler.jsonc`의 일간·주간 업로드 한도를 운영 용량에 맞게 검토
7. `npx wrangler deploy`

키 내용을 터미널 인수나 저장소 파일에 직접 적지 않습니다.
