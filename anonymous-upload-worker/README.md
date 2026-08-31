# 기숙사 식단 익명 업로드 게이트웨이

Android 앱에서 GitHub 로그인 없이 식단 사진을 제출하기 위한 Cloudflare Worker입니다. 앱은 사진만 전송하며 GitHub 쓰기 자격 증명을 포함하지 않습니다. Worker가 서버에 보관한 GitHub App 개인 키로 짧은 설치 토큰을 발급하고 `dorm-submissions` 브랜치에 새 사진 한 장을 기록합니다.

## 보안 경계

- JPEG, PNG, WebP만 허용하며 파일 시그니처와 15 MiB 제한을 확인합니다.
- Cloudflare가 제공한 요청 IP의 단방향 해시를 KV에 10분 보관해 중복 제출을 제한합니다.
- GitHub App 권한은 이 저장소의 `Contents: read and write`와 필수 `Metadata: read-only`만 사용합니다.
- `GITHUB_APP_PRIVATE_KEY`와 `RATE_LIMIT_SALT`는 Wrangler Secret으로만 저장합니다. APK, Git, 로그에 넣지 않습니다.
- 업로드된 사진은 공개 저장소 브랜치와 GitHub Actions 처리 대상이 됩니다. 앱은 전송 전 공개 공유 사실을 확인합니다.

## 검증

```powershell
node --test
npx wrangler deploy --dry-run
```

## 최초 배포

1. `npx wrangler login`
2. `npx wrangler kv namespace create RATE_LIMIT`
3. 출력된 namespace ID를 `wrangler.jsonc`의 `kv_namespaces`에 연결
4. PKCS#8 PEM 형식의 GitHub App 개인 키를 `npx wrangler secret put GITHUB_APP_PRIVATE_KEY`로 등록
5. 충분히 긴 무작위 값을 `npx wrangler secret put RATE_LIMIT_SALT`로 등록
6. `npx wrangler deploy`

키 내용을 터미널 인수나 저장소 파일에 직접 적지 않습니다.
