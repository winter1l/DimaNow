// D-119: 문의·오류 제보 from inside the app become GitHub issues on this repository. The app sends only what the
// student wrote, its category, and (when the student leaves it on) the app and Android versions. D-120: an 오류
// report may add up to three screenshots, which the app re-encodes without metadata and this Worker keeps in the
// private bucket and serves at a public address the issue links, and the app's own redacted warning log.
// Nothing about the device, account or location is sent, and the issue is public, which the app says before sending.

export const FEEDBACK_ISSUES_URL = 'https://api.github.com/repos/winter1l/DimaNow/issues';
export const FEEDBACK_IMAGE_PREFIX = 'feedback/';
export const FEEDBACK_IMAGE_KEY_PATTERN = /^feedback\/([A-Za-z0-9_-]{1,64})\.(jpg|png|webp)$/;

const MAX_FEEDBACK_BODY_BYTES = 8 * 1024 * 1024;
const MIN_MESSAGE_CHARS = 5;
const MAX_MESSAGE_CHARS = 2000;
const MAX_LOG_CHARS = 20000;
const MAX_IMAGES = 3;
const MAX_IMAGE_BYTES = 2 * 1024 * 1024;
const MIN_IMAGE_SIDE = 64;
const MAX_IMAGE_SIDE = 6000;
// R2 has no per-prefix quota; the Worker stops taking screenshots past this total.
const FEEDBACK_IMAGE_STORAGE_LIMIT_BYTES = 300 * 1000 * 1000;
const FEEDBACK_LEASE_SECONDS = 10 * 60;
const IMAGE_EXTENSIONS = new Map([
  ['image/jpeg', 'jpg'],
  ['image/png', 'png'],
  ['image/webp', 'webp'],
]);
export const FEEDBACK_CATEGORIES = new Map([
  ['bug', '오류'],
  ['question', '문의'],
  ['idea', '제안'],
]);

/**
 * `POST /v1/feedback` with `{"category":"bug|question|idea","message":"…","appVersion":"2.1","androidSdk":36,
 * "log":"…","images":[{"type":"image/jpeg","data":"<base64>"}]}` (log and images only for bug). One report per
 * address every ten minutes and a daily and weekly budget. Answers 201 with the issue number and URL, 429 while the
 * address or the budget is used up, 502 when GitHub refuses (stored screenshots are then removed).
 */
export async function handleFeedback(request, env, dependencies) {
  const { jsonResponse, rateLimitClientKey, sha256Hex, positiveLimit, uploadBudgetWindows } = dependencies.helpers;
  if (request.method !== 'POST') return jsonResponse(405, 'POST 요청만 지원합니다.', { Allow: 'POST' });
  const mimeType = request.headers.get('Content-Type')?.split(';', 1)[0]?.trim().toLowerCase();
  if (mimeType !== 'application/json') return jsonResponse(415, 'JSON 요청만 지원합니다.');
  const { readBody, isBodyTooLarge } = dependencies.helpers;
  let body;
  try {
    const bytes = await readBody(request, MAX_FEEDBACK_BODY_BYTES);
    body = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes));
  } catch (error) {
    if (isBodyTooLarge?.(error)) return jsonResponse(413, '제보 내용이 너무 깁니다.');
    return jsonResponse(400, '제보 내용을 읽지 못했습니다.');
  }
  const report = feedbackReport(body, dependencies.helpers);
  if (!report) return jsonResponse(400, '제보 내용을 확인할 수 없습니다.');
  if (report.images.length && !env.DORM_PHOTOS) return jsonResponse(503, '제보 서비스가 준비되지 않았습니다.');

  const clientKey = rateLimitClientKey(request.headers.get('CF-Connecting-IP'));
  if (!clientKey) return jsonResponse(400, '제보 요청을 확인할 수 없습니다.');
  const rateLimitSalt = env.RATE_LIMIT_SALT?.trim();
  if (!rateLimitSalt || rateLimitSalt.length < 32) return jsonResponse(503, '제보 서비스가 준비되지 않았습니다.');
  if (env?.GITHUB_OWNER !== 'winter1l' || env?.GITHUB_REPOSITORY !== 'DimaNow') {
    return jsonResponse(503, '제보 서비스가 준비되지 않았습니다.');
  }
  let securityStore;
  try {
    securityStore = dependencies.securityStoreFactory(env);
  } catch {
    return jsonResponse(503, '제보 서비스가 준비되지 않았습니다.');
  }
  const current = dependencies.now();
  const nowSeconds = Math.floor(current.getTime() / 1000);
  const windows = uploadBudgetWindows(current);
  const addressHash = await sha256Hex(`${rateLimitSalt}:${clientKey}`);
  const leaseScope = `feedback:${addressHash}`;
  const leaseOwner = dependencies.randomUUID();
  const budget = {
    dayScope: `feedback-day:${windows.date}`,
    dayLimit: positiveLimit(env.FEEDBACK_DAILY_LIMIT, 20),
    dayExpiresAt: windows.dayExpiresAt,
    weekScope: `feedback-week:${windows.weekStart}`,
    weekLimit: positiveLimit(env.FEEDBACK_WEEKLY_LIMIT, 60),
    weekExpiresAt: windows.weekExpiresAt,
  };
  try {
    if (!await securityStore.claimLease(leaseScope, leaseOwner, nowSeconds, nowSeconds + FEEDBACK_LEASE_SECONDS)) {
      return jsonResponse(429, '방금 제보를 받았습니다. 10분 뒤에 다시 보내 주세요.');
    }
    if (!await securityStore.consumeUploadBudgets(budget)) {
      await securityStore.releaseLease(leaseScope, leaseOwner);
      return jsonResponse(429, '오늘은 제보를 더 받을 수 없습니다. 내일 다시 보내 주세요.');
    }
  } catch (error) {
    console.error('Feedback admission failed', error instanceof Error ? error.message : error);
    return jsonResponse(503, '제보 서비스가 준비되지 않았습니다.');
  }

  const storedKeys = [];
  const giveBack = async () => {
    try {
      if (storedKeys.length) await env.DORM_PHOTOS.delete(storedKeys);
      await securityStore.refundUploadBudgets(budget);
      await securityStore.releaseLease(leaseScope, leaseOwner);
    } catch (refundError) {
      console.error('Feedback refund failed', refundError instanceof Error ? refundError.message : refundError);
    }
  };
  try {
    const imageUrls = [];
    if (report.images.length) {
      const used = await prefixStorageBytes(env.DORM_PHOTOS, FEEDBACK_IMAGE_PREFIX);
      const adding = report.images.reduce((sum, image) => sum + image.bytes.byteLength, 0);
      if (used + adding > FEEDBACK_IMAGE_STORAGE_LIMIT_BYTES) {
        await giveBack();
        return jsonResponse(507, '스크린샷 저장 공간이 가득 찼습니다. 스크린샷 없이 보내 주세요.');
      }
      const origin = new URL(request.url).origin;
      for (const image of report.images) {
        const key = `${FEEDBACK_IMAGE_PREFIX}${dependencies.randomUUID()}.${image.extension}`;
        await env.DORM_PHOTOS.put(key, image.bytes, { httpMetadata: { contentType: image.type } });
        storedKeys.push(key);
        imageUrls.push(`${origin}/v1/feedback-images/${key.slice(FEEDBACK_IMAGE_PREFIX.length)}`);
      }
    }
    const issue = feedbackIssue(report, imageUrls);
    const token = await dependencies.githubTokenProvider(env, { issues: 'write' });
    const created = await createFeedbackIssue(dependencies.fetchImpl, token, issue);
    return new Response(JSON.stringify({ message: '제보를 올렸습니다.', number: created.number, url: created.url }), {
      status: 201,
      headers: dependencies.helpers.jsonHeaders(),
    });
  } catch (error) {
    console.error('Feedback issue failed', error instanceof Error ? error.message : error);
    // Nothing was posted: remove the screenshots and give the address and the budget back.
    await giveBack();
    return jsonResponse(502, '제보를 올리지 못했습니다. 잠시 후 다시 시도해 주세요.');
  }
}

/** `GET /v1/feedback-images/<id>.<ext>`: a screenshot an issue links. Anything else is the usual 404. */
export async function handleFeedbackImage(request, env, url, jsonResponse) {
  if (request.method !== 'GET' && request.method !== 'HEAD') return jsonResponse(404, '요청한 경로가 없습니다.');
  const key = `${FEEDBACK_IMAGE_PREFIX}${url.pathname.slice('/v1/feedback-images/'.length)}`;
  if (!FEEDBACK_IMAGE_KEY_PATTERN.test(key) || !env.DORM_PHOTOS) return jsonResponse(404, '요청한 경로가 없습니다.');
  const object = await env.DORM_PHOTOS.get(key);
  if (!object) return jsonResponse(404, '요청한 경로가 없습니다.');
  return new Response(request.method === 'HEAD' ? null : object.body, {
    status: 200,
    headers: {
      'Content-Type': object.httpMetadata?.contentType ?? 'application/octet-stream',
      'Cache-Control': 'public, max-age=31536000, immutable',
      'X-Content-Type-Options': 'nosniff',
    },
  });
}

async function prefixStorageBytes(bucket, prefix) {
  let total = 0;
  let cursor;
  do {
    const page = await bucket.list({ prefix, cursor, limit: 1000 });
    for (const object of page.objects) total += Number(object.size ?? 0);
    cursor = page.truncated ? page.cursor : undefined;
  } while (cursor);
  return total;
}

/** The checked report for a request body, or null. Exported for tests. */
export function feedbackReport(body, helpers = {}) {
  const label = FEEDBACK_CATEGORIES.get(typeof body?.category === 'string' ? body.category : '');
  if (!label || typeof body?.message !== 'string') return null;
  const message = cleanText(body.message);
  const length = [...message].length;
  if (length < MIN_MESSAGE_CHARS || length > MAX_MESSAGE_CHARS) return null;
  const appVersion = typeof body.appVersion === 'string' && /^[0-9A-Za-z.+-]{1,32}$/.test(body.appVersion) ? body.appVersion : null;
  const androidSdk = Number.isInteger(body.androidSdk) && body.androidSdk >= 21 && body.androidSdk <= 99 ? body.androidSdk : null;
  const bug = body.category === 'bug';
  let log = null;
  if (body.log !== undefined && body.log !== null) {
    if (!bug || typeof body.log !== 'string') return null;
    log = cleanText(body.log);
    if (log.length > MAX_LOG_CHARS) return null;
    if (!log) log = null;
  }
  const images = [];
  if (body.images !== undefined && body.images !== null) {
    if (!bug || !Array.isArray(body.images) || body.images.length > MAX_IMAGES) return null;
    for (const image of body.images) {
      const extension = IMAGE_EXTENSIONS.get(image?.type);
      if (!extension || typeof image?.data !== 'string' || image.data.length > Math.ceil(MAX_IMAGE_BYTES / 3) * 4 + 4) return null;
      let bytes;
      try {
        bytes = Uint8Array.from(atob(image.data), (char) => char.charCodeAt(0));
      } catch {
        return null;
      }
      if (bytes.byteLength === 0 || bytes.byteLength > MAX_IMAGE_BYTES) return null;
      if (helpers.hasExpectedImageSignature && !helpers.hasExpectedImageSignature(bytes, image.type)) return null;
      const size = helpers.imageDimensions?.(bytes, image.type);
      if (helpers.imageDimensions && (!size || Math.min(size.width, size.height) < MIN_IMAGE_SIDE ||
        Math.max(size.width, size.height) > MAX_IMAGE_SIDE)) return null;
      images.push({ type: image.type, extension, bytes });
    }
  }
  return { label, message, appVersion, androidSdk, log, images };
}

/** The issue title and body for a checked report and the screenshots' addresses. Exported for tests. */
export function feedbackIssue(report, imageUrls = []) {
  const firstLine = report.message.split('\n').find((line) => line.trim())?.trim() ?? '';
  const summary = [...noMentions(firstLine)].slice(0, 50).join('');
  // The student's words and the log go in fenced blocks, so links, images and mentions stay plain text.
  const lines = [
    `앱 안에서 보낸 ${report.label} 제보입니다.`,
    '',
    ...fenced(report.message),
    '',
    `- 앱 버전: ${report.appVersion ?? '보내지 않음'}`,
    `- Android SDK: ${report.androidSdk ?? '보내지 않음'}`,
  ];
  if (imageUrls.length) {
    lines.push('', '### 스크린샷', '');
    imageUrls.forEach((url, index) => lines.push(`![스크린샷 ${index + 1}](${url})`));
  }
  if (report.log) {
    lines.push('', '<details><summary>앱 로그 (개인정보를 가린 경고·오류 기록)</summary>', '', ...fenced(report.log), '', '</details>');
  }
  return { title: `[앱 ${report.label}] ${summary}`, body: lines.join('\n') };
}

function fenced(text) {
  const longestTicks = Math.max(0, ...[...text.matchAll(/`+/g)].map((match) => match[0].length));
  const fence = '`'.repeat(Math.max(3, longestTicks + 1));
  return [`${fence}text`, text, fence];
}

function cleanText(text) {
  // Keep line breaks and tabs; drop every other control character.
  return text.replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g, '').replace(/\r\n?/g, '\n').trim();
}

/** Breaks `@name` so the issue title never notifies anyone. */
function noMentions(text) {
  return text.replace(/@(?=[A-Za-z0-9_-])/g, '@​');
}

export async function createFeedbackIssue(fetchImpl, token, issue) {
  if (typeof token !== 'string' || !token) throw new Error('GitHub installation token unavailable');
  const response = await fetchImpl(FEEDBACK_ISSUES_URL, {
    method: 'POST',
    redirect: 'manual',
    signal: AbortSignal.timeout(10000),
    headers: {
      Accept: 'application/vnd.github+json',
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      'User-Agent': 'DIMA-Now-Feedback',
      'X-GitHub-Api-Version': '2022-11-28',
    },
    body: JSON.stringify({ title: issue.title, body: issue.body }),
  });
  if (response.status !== 201) {
    await response.body?.cancel();
    throw new Error(`Feedback issue failed: ${response.status}`);
  }
  const created = await response.json();
  if (!Number.isInteger(created?.number) || typeof created?.html_url !== 'string') throw new Error('Unexpected issue response');
  return { number: created.number, url: created.html_url };
}
