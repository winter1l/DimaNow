# 개인정보 처리 안내

DIMA Now는 개인용 비공식 Android 앱입니다.

## 기기에 저장되는 정보

- 사용자가 입력한 시간표, 학기 기간, 휴강일과 휴강 모드
- 선택한 귀가 기준지, 표시 옵션, 권한 안내 완료 여부와 테스트 모드
- 앱이 판정한 마지막 캠퍼스 구역
- GitHub Pages에서 동기화한 셔틀·식단·공지 캐시와 동기화 상태
- 마지막 앱 업데이트 확인 시각, 확인한 공개 릴리스 정보, 버전별 안내 닫기 상태와 검증된 대기 APK 경로

이 정보는 앱의 Room 데이터베이스와 DataStore에 저장됩니다. 앱 삭제 또는 Android의 앱 데이터 삭제로 제거할 수 있습니다.

## 위치 정보

위치 권한을 허용하면 기기에서 예인관·본관·원룸촌·캠퍼스 밖을 판정하고 geofence 및 안내에 사용합니다. 원시 GPS 이동 기록을 별도 서버로 보내거나 분석용으로 수집하지 않습니다. 테스트 모드에서는 GPS/geofence 결과가 최종 구역에 반영되지 않습니다.

## 네트워크 통신

앱은 `winter1l.github.io`의 정적 JSON과 GitHub 공개 Releases API를 확인하고, 사용자가 원문 버튼을 누르면 Android 브라우저로 DIMA 또는 Instagram 공개 페이지를 엽니다. 업데이트 다운로드는 사용자가 직접 요청한 경우에만 GitHub release asset에서 실행됩니다. 기숙사 사진을 올릴 때는 Cloudflare Worker가 요청 IP를 중복 제한용 단방향 해시로 최대 10분 처리합니다. GitHub Pages, GitHub API, Cloudflare와 외부 브라우저 서비스에는 해당 서비스의 접속 기록 정책이 적용될 수 있습니다.

공식 DIMA 페이지 수집과 공개 Instagram 이미지 OCR은 앱이 아니라 GitHub Actions에서 실행됩니다. 식단표 이미지는 구조화된 메뉴 JSON을 만들기 위해 Google Gemini API로 전송됩니다. 이때 앱 사용자의 위치·시간표·설정·기기 정보는 전송하지 않으며, Instagram 자격 증명도 사용하지 않습니다.

현재 주 기숙사 식단이 없을 때 사용자가 `사진 올리기`를 선택하면 앱은 먼저 Pages를 새로고침합니다. 여전히 식단이 없고 사용자가 공개 공유 안내를 확인한 경우에만 선택하거나 촬영한 사진을 Cloudflare Worker를 거쳐 공개 GitHub `dorm-submissions` 브랜치에 전송합니다. GitHub 로그인이나 사용자 토큰은 요구하지 않습니다. GitHub Actions가 사진을 Gemini로 보내 기숙사 식단표 여부와 표 전체 노출을 확인하고, 통과한 메뉴 JSON을 GitHub Pages에 게시합니다. 사진과 검증 JSON에는 Cloudflare·GitHub 공개 저장소·Pages의 보존 및 접속 정책이 적용됩니다.

## 공개 저장소 사용 시 주의

Issue나 Pull Request에 개인 시간표, 정확한 현재 위치, 기기 식별자, 전체 logcat 또는 앱 데이터베이스를 첨부하지 마세요.

최종 갱신: 2026-08-31
