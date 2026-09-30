import { createStudentMealPublicationWatch, STUDENT_MEAL_WATCH_CRONS } from './meal-publication-watch.mjs';

const MAX_IMAGE_BYTES = 15 * 1024 * 1024;
const RATE_LIMIT_SECONDS = 10 * 60;
const IMAGE_EXTENSIONS = new Map([
  ['image/jpeg', 'jpg'],
  ['image/png', 'png'],
  ['image/webp', 'webp'],
]);

export function createWorker(dependencies = {}) {
  const fetchImpl = dependencies.fetch ?? fetch;
  const now = dependencies.now ?? (() => new Date());
  const randomUUID = dependencies.randomUUID ?? (() => crypto.randomUUID());
  const githubTokenProvider = dependencies.githubTokenProvider
    ?? ((env) => createGitHubInstallationToken(env, fetchImpl, now));
  const studentMealWatch = createStudentMealPublicationWatch({ fetch: fetchImpl, now, githubTokenProvider });
  // The D1 binding keeps its historical name; it now holds only the dormitory upload admission tables.
  const securityStoreFactory = dependencies.securityStoreFactory
    ?? ((env) => createD1SecurityStore(env.SHUTTLE_REPORTS));
  const dormitoryPublicationProvider = dependencies.dormitoryPublicationProvider
    ?? ((_env, current) => hasCurrentDormitoryPublication(fetchImpl, current));
  const bodyReader = dependencies.bodyReader ?? readBoundedRequestBody;

  return {
    async fetch(request, env) {
      const url = new URL(request.url);
      if (url.pathname === '/v1/dormitory-meals') {
        return handleDormitoryMealUpload(request, env, {
          now,
          randomUUID,
          fetchImpl,
          githubTokenProvider,
          securityStoreFactory,
          dormitoryPublicationProvider,
          bodyReader,
        });
      }
      return jsonResponse(404, '요청한 경로가 없습니다.');
    },
    scheduled(event, env, context) {
      if (STUDENT_MEAL_WATCH_CRONS.includes(event.cron)) {
        context.waitUntil(studentMealWatch(env).then((result) => console.info('Student meal publication watch', result)));
        // Reuse the daily 17:07 UTC watch slot for maintenance, keeping three Cron Triggers.
        if (new Date(event.scheduledTime).getUTCHours() !== 17) return;
      }
      context.waitUntil((async () => {
        const securityStore = securityStoreFactory(env);
        await securityStore.prune?.(Math.floor(now().getTime() / 1000));
      })().catch((error) => {
        console.error('Scheduled gateway maintenance failed', error instanceof Error ? error.message : error);
      }));
    },
  };
}

async function handleDormitoryMealUpload(request, env, dependencies) {
  if (request.method !== 'POST') {
    return jsonResponse(405, 'POST 요청만 지원합니다.', { Allow: 'POST' });
  }
  const mimeType = request.headers.get('Content-Type')?.split(';', 1)[0]?.trim().toLowerCase();
  const expectedExtension = IMAGE_EXTENSIONS.get(mimeType);
  const requestedExtension = request.headers.get('X-Dima-Image-Extension')?.trim().toLowerCase();
  if (!expectedExtension || requestedExtension !== expectedExtension) {
    return jsonResponse(415, '지원하지 않는 식단 이미지 형식입니다.');
  }
  const contentLength = Number(request.headers.get('Content-Length'));
  if (Number.isFinite(contentLength) && contentLength > MAX_IMAGE_BYTES) {
    return jsonResponse(413, '식단 이미지는 15MB 이하여야 합니다.');
  }
  const clientAddress = request.headers.get('CF-Connecting-IP')?.trim();
  if (!clientAddress) return jsonResponse(400, '업로드 요청을 확인할 수 없습니다.');
  const rateLimitSalt = env.RATE_LIMIT_SALT?.trim();
  if (!rateLimitSalt || rateLimitSalt.length < 32) {
    return jsonResponse(503, '업로드 서비스가 준비되지 않았습니다.');
  }

  let securityStore;
  try {
    securityStore = dependencies.securityStoreFactory(env);
  } catch {
    return jsonResponse(503, '업로드 서비스가 준비되지 않았습니다.');
  }
  const current = dependencies.now();
  const nowSeconds = Math.floor(current.getTime() / 1000);
  const leaseOwner = dependencies.randomUUID();
  const addressHash = await sha256Hex(`${rateLimitSalt}:${clientAddress}`);
  const leaseScope = `upload-address:${addressHash}`;
  try {
    const claimed = await securityStore.claimLease(
      leaseScope,
      leaseOwner,
      nowSeconds,
      nowSeconds + RATE_LIMIT_SECONDS,
    );
    if (!claimed) {
      return jsonResponse(429, '잠시 후 다시 시도해 주세요.', { 'Retry-After': String(RATE_LIMIT_SECONDS) });
    }
    const windows = uploadBudgetWindows(current);
    const weeklyLimit = positiveLimit(env.UPLOAD_WEEKLY_LIMIT, 40);
    const dailyLimit = positiveLimit(env.UPLOAD_DAILY_LIMIT, 20);
    if (!await securityStore.consumeUploadBudgets({
      dayScope: `upload-day:${windows.date}`,
      dayLimit: dailyLimit,
      dayExpiresAt: windows.dayExpiresAt,
      weekScope: `upload-week:${windows.weekStart}`,
      weekLimit: weeklyLimit,
      weekExpiresAt: windows.weekExpiresAt,
    })) {
      return jsonResponse(429, '현재 익명 식단 처리 한도에 도달했습니다.', {
        'Retry-After': String(Math.max(1, windows.dayExpiresAt - nowSeconds)),
      });
    }
  } catch (error) {
    console.error('Dormitory meal admission failed', error instanceof Error ? error.message : error);
    return jsonResponse(503, '업로드 서비스가 준비되지 않았습니다.');
  }

  try {
    if (await dependencies.dormitoryPublicationProvider(env, current)) {
      return jsonResponse(409, '이번 주 기숙사 식단이 이미 등록되어 있습니다.');
    }
  } catch (error) {
    console.error('Dormitory publication check failed', error instanceof Error ? error.message : error);
    await securityStore.releaseLease(leaseScope, leaseOwner);
    return jsonResponse(503, '현재 식단 게시 상태를 확인하지 못했습니다.');
  }

  let image;
  try {
    image = await dependencies.bodyReader(request, MAX_IMAGE_BYTES);
  } catch (error) {
    if (error instanceof BodyTooLargeError) return jsonResponse(413, '식단 이미지는 15MB 이하여야 합니다.');
    return jsonResponse(400, '식단 사진을 읽지 못했습니다.');
  }
  if (image.byteLength === 0) return jsonResponse(400, '식단 사진이 비어 있습니다.');
  if (!hasExpectedImageSignature(image, mimeType)) {
    return jsonResponse(415, '사진 파일 형식을 확인해 주세요.');
  }

  const submissionId = dependencies.randomUUID();
  const uploadedAt = current.toISOString();
  try {
    const token = await dependencies.githubTokenProvider(env);
    const owner = env.GITHUB_OWNER ?? 'winter1l';
    const repository = env.GITHUB_REPOSITORY ?? 'DimaNow';
    const branch = env.GITHUB_SUBMISSION_BRANCH ?? 'dorm-submissions';
    const path = `dorm-submissions/${submissionId}.${expectedExtension}`;
    const response = await dependencies.fetchImpl(
      `https://api.github.com/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repository)}/contents/${path}`,
      {
        method: 'PUT',
        headers: {
          Accept: 'application/vnd.github+json',
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
          'User-Agent': 'DIMA-Now-Meal-Upload',
          'X-GitHub-Api-Version': '2022-11-28',
        },
        body: JSON.stringify({
          message: `dormitory meal submission ${submissionId}`,
          content: bytesToBase64(image),
          branch,
        }),
      },
    );
    if (!response.ok) throw new Error(`GitHub content upload failed: ${response.status}`);
    return new Response(JSON.stringify({ submissionId, uploadedAt }), {
      status: 202,
      headers: jsonHeaders(),
    });
  } catch (error) {
    await securityStore.releaseLease(leaseScope, leaseOwner);
    console.error('Dormitory meal upload failed', error instanceof Error ? error.message : error);
    return jsonResponse(502, '사진을 올리지 못했습니다. 잠시 후 다시 시도해 주세요.');
  }
}

function positiveLimit(value, fallback) {
  const parsed = Number(value ?? fallback);
  if (!Number.isSafeInteger(parsed) || parsed <= 0 || parsed > 10_000) {
    throw new Error('invalid upload budget');
  }
  return parsed;
}

function uploadBudgetWindows(current) {
  const kst = new Date(current.getTime() + 9 * 60 * 60 * 1000);
  const date = kst.toISOString().slice(0, 10);
  const day = kst.getUTCDay();
  const week = new Date(kst);
  week.setUTCDate(week.getUTCDate() - ((day + 6) % 7));
  const weekStart = week.toISOString().slice(0, 10);
  const nextDay = new Date(`${date}T00:00:00+09:00`);
  nextDay.setUTCDate(nextDay.getUTCDate() + 1);
  const nextWeek = new Date(`${weekStart}T00:00:00+09:00`);
  nextWeek.setUTCDate(nextWeek.getUTCDate() + 7);
  return {
    date,
    weekStart,
    dayExpiresAt: Math.floor(nextDay.getTime() / 1000),
    weekExpiresAt: Math.floor(nextWeek.getTime() / 1000),
  };
}

function createD1SecurityStore(database) {
  if (!database) throw new Error('SHUTTLE_REPORTS D1 binding is required for gateway admission');
  return {
    async claimLease(scope, owner, nowSeconds, expiresAtSeconds) {
      const result = await database.prepare(`
        INSERT INTO gateway_leases (scope, owner, expires_at)
        VALUES (?, ?, ?)
        ON CONFLICT(scope) DO UPDATE SET
          owner = excluded.owner,
          expires_at = excluded.expires_at
        WHERE gateway_leases.expires_at <= ?
      `).bind(scope, owner, expiresAtSeconds, nowSeconds).run();
      return Number(result.meta?.changes ?? 0) > 0;
    },
    async releaseLease(scope, owner) {
      await database.prepare('DELETE FROM gateway_leases WHERE scope = ? AND owner = ?')
        .bind(scope, owner).run();
    },
    async consumeUploadBudgets(budget) {
      const result = await database.prepare(`
        WITH requested(scope, expires_at) AS (
          VALUES (?, ?), (?, ?)
        )
        INSERT INTO gateway_budgets(scope, used, expires_at)
        SELECT scope, 1, expires_at
        FROM requested
        WHERE COALESCE((SELECT used FROM gateway_budgets WHERE scope = ?), 0) < ?
          AND COALESCE((SELECT used FROM gateway_budgets WHERE scope = ?), 0) < ?
        ON CONFLICT(scope) DO UPDATE SET used = gateway_budgets.used + 1
      `).bind(
        budget.dayScope,
        budget.dayExpiresAt,
        budget.weekScope,
        budget.weekExpiresAt,
        budget.dayScope,
        budget.dayLimit,
        budget.weekScope,
        budget.weekLimit,
      ).run();
      return Number(result.meta?.changes ?? 0) === 2;
    },
    async prune(nowSeconds) {
      await database.prepare('DELETE FROM gateway_leases WHERE expires_at <= ?').bind(nowSeconds).run();
      await database.prepare('DELETE FROM gateway_budgets WHERE expires_at <= ?').bind(nowSeconds).run();
    },
  };
}

async function hasCurrentDormitoryPublication(fetchImpl, current) {
  const root = 'https://winter1l.github.io/DimaNow/data/v1';
  const manifestResponse = await fetchImpl(`${root}/manifest.json`, {
    headers: { Accept: 'application/json' },
    redirect: 'error',
  });
  if (!manifestResponse.ok) throw new Error(`manifest ${manifestResponse.status}`);
  const manifest = await readBoundedResponseJson(manifestResponse, 64 * 1024);
  const descriptor = manifest.datasets?.dorm_meal;
  if (!descriptor || descriptor.state !== 'READY') return false;
  if (!/^dorm-meal\/[0-9a-f]{64}\.json$/.test(descriptor.url ?? '')) {
    throw new Error('invalid dormitory descriptor');
  }
  const payloadResponse = await fetchImpl(`${root}/${descriptor.url}`, {
    headers: { Accept: 'application/json' },
    redirect: 'error',
  });
  if (!payloadResponse.ok) throw new Error(`dormitory payload ${payloadResponse.status}`);
  const payload = await readBoundedResponseJson(payloadResponse, 256 * 1024);
  return payload.weekStart === targetDormitoryWeekStart(current);
}

function targetDormitoryWeekStart(current) {
  const kst = new Date(current.getTime() + 9 * 60 * 60 * 1000);
  const day = kst.getUTCDay();
  const offset = day === 0 ? 1 : day === 6 ? 2 : -((day + 6) % 7);
  kst.setUTCDate(kst.getUTCDate() + offset);
  return kst.toISOString().slice(0, 10);
}

class BodyTooLargeError extends Error {}

async function readBoundedRequestBody(request, maxBytes) {
  const declaredLength = Number(request.headers.get('Content-Length'));
  if (Number.isFinite(declaredLength) && declaredLength > maxBytes) throw new BodyTooLargeError();
  if (!request.body) return new Uint8Array();
  const reader = request.body.getReader();
  const chunks = [];
  let total = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      const chunk = value instanceof Uint8Array ? value : new Uint8Array(value);
      if (total + chunk.byteLength > maxBytes) {
        await reader.cancel();
        throw new BodyTooLargeError();
      }
      chunks.push(chunk);
      total += chunk.byteLength;
    }
  } finally {
    reader.releaseLock();
  }
  const result = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    result.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return result;
}

async function readBoundedResponseJson(response, maxBytes) {
  const bytes = await readBoundedRequestBody(response, maxBytes);
  const value = parseJsonObject(bytes, JSON.parse);
  if (!value) throw new Error('invalid JSON response');
  return value;
}

function parseJsonObject(bytes, parser) {
  const text = new TextDecoder('utf-8', { fatal: true }).decode(bytes);
  const value = parser(text);
  return value && typeof value === 'object' && !Array.isArray(value) ? value : null;
}

async function createGitHubInstallationToken(env, fetchImpl, now) {
  const appId = required(env.GITHUB_APP_ID, 'GITHUB_APP_ID');
  const installationId = required(env.GITHUB_INSTALLATION_ID, 'GITHUB_INSTALLATION_ID');
  const privateKey = required(env.GITHUB_APP_PRIVATE_KEY, 'GITHUB_APP_PRIVATE_KEY');
  const nowSeconds = Math.floor(now().getTime() / 1000);
  const header = base64Url(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
  const payload = base64Url(JSON.stringify({
    iat: nowSeconds - 60,
    exp: nowSeconds + 9 * 60,
    iss: appId,
  }));
  const signingInput = `${header}.${payload}`;
  const key = await crypto.subtle.importKey(
    'pkcs8',
    pemToArrayBuffer(privateKey),
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['sign'],
  );
  const signature = await crypto.subtle.sign(
    'RSASSA-PKCS1-v1_5',
    key,
    new TextEncoder().encode(signingInput),
  );
  const jwt = `${signingInput}.${base64UrlBytes(new Uint8Array(signature))}`;
  const response = await fetchImpl(`https://api.github.com/app/installations/${installationId}/access_tokens`, {
    method: 'POST',
    headers: {
      Accept: 'application/vnd.github+json',
      Authorization: `Bearer ${jwt}`,
      'User-Agent': 'DIMA-Now-Meal-Upload',
      'X-GitHub-Api-Version': '2022-11-28',
    },
  });
  if (!response.ok) {
    throw new Error(`GitHub installation token failed: ${response.status}`);
  }
  const result = await response.json();
  return required(result.token, 'GitHub installation token');
}

function hasExpectedImageSignature(bytes, mimeType) {
  if (mimeType === 'image/jpeg') {
    return bytes.length >= 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff;
  }
  if (mimeType === 'image/png') {
    const png = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];
    return bytes.length >= png.length && png.every((value, index) => bytes[index] === value);
  }
  if (mimeType === 'image/webp') {
    return bytes.length >= 12
      && String.fromCharCode(...bytes.slice(0, 4)) === 'RIFF'
      && String.fromCharCode(...bytes.slice(8, 12)) === 'WEBP';
  }
  return false;
}

async function sha256Hex(value) {
  const digest = new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value)));
  return Array.from(digest, (byte) => byte.toString(16).padStart(2, '0')).join('');
}

function pemToArrayBuffer(pem) {
  const base64 = pem
    .replace(/-----BEGIN PRIVATE KEY-----/g, '')
    .replace(/-----END PRIVATE KEY-----/g, '')
    .replace(/\s/g, '');
  if (!base64) throw new Error('GITHUB_APP_PRIVATE_KEY must be a PKCS#8 PEM key');
  const bytes = Uint8Array.from(atob(base64), (character) => character.charCodeAt(0));
  return bytes.buffer;
}

function bytesToBase64(bytes) {
  let binary = '';
  for (let offset = 0; offset < bytes.length; offset += 0x8000) {
    binary += String.fromCharCode(...bytes.subarray(offset, offset + 0x8000));
  }
  return btoa(binary);
}

function base64Url(value) {
  return base64UrlBytes(new TextEncoder().encode(value));
}

function base64UrlBytes(bytes) {
  return bytesToBase64(bytes).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
}

function required(value, name) {
  if (typeof value !== 'string' || value.trim() === '') throw new Error(`${name} is required`);
  return value.trim();
}

function jsonResponse(status, message, extraHeaders = {}) {
  return new Response(JSON.stringify({ message }), {
    status,
    headers: { ...jsonHeaders(), ...extraHeaders },
  });
}

function jsonHeaders() {
  return {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff',
  };
}

export default createWorker();
