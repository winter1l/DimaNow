import { createStudentMealPublicationWatch, STUDENT_MEAL_WATCH_CRONS } from './meal-publication-watch.mjs';

const MAX_IMAGE_BYTES = 15 * 1024 * 1024;
const RATE_LIMIT_SECONDS = 10 * 60;
// A photographed weekly menu is never this small; tiny or extreme bodies are probes or junk.
const MIN_IMAGE_BYTES = 20 * 1024;
const MIN_IMAGE_SIDE = 320;
const MAX_IMAGE_SIDE = 12000;
const IMAGE_TOO_SMALL_MESSAGE = '식단 사진이 너무 작습니다. 원본 사진을 올려 주세요.';
// Bounded opportunistic cleanup so expired salted address hashes do not wait for the daily prune.
const EXPIRED_LEASE_DELETE_LIMIT = 100;
// Fixed target: only main's publish workflow may be woken, never a repository taken from env or the request.
const DORM_SUBMISSION_DISPATCH_URL = 'https://api.github.com/repos/winter1l/DimaNow/dispatches';
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
    async fetch(request, env, context) {
      const url = new URL(request.url);
      if (url.pathname === '/v1/dormitory-meals') {
        return handleDormitoryMealUpload(request, env, {
          context,
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
        // Reuse the daily 17:07 UTC watch slot for full maintenance, keeping three Cron Triggers.
        // Every other watch slot (at most two hours apart) still drops expired address leases.
        if (new Date(event.scheduledTime).getUTCHours() !== 17) {
          context.waitUntil((async () => {
            const securityStore = securityStoreFactory(env);
            await securityStore.pruneExpiredLeases?.(Math.floor(now().getTime() / 1000));
          })().catch((error) => {
            console.error('Scheduled lease cleanup failed', error instanceof Error ? error.message : error);
          }));
          return;
        }
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
  // Cheap, side-effect-free validation first: nothing below may touch D1 until the request is well formed.
  if (request.method !== 'POST') {
    return jsonResponse(405, 'POST 요청만 지원합니다.', { Allow: 'POST' });
  }
  const mimeType = request.headers.get('Content-Type')?.split(';', 1)[0]?.trim().toLowerCase();
  const expectedExtension = IMAGE_EXTENSIONS.get(mimeType);
  const requestedExtension = request.headers.get('X-Dima-Image-Extension')?.trim().toLowerCase();
  if (!expectedExtension || requestedExtension !== expectedExtension) {
    return jsonResponse(415, '지원하지 않는 식단 이미지 형식입니다.');
  }
  const declaredLength = request.headers.get('Content-Length');
  const contentLength = Number(declaredLength);
  if (declaredLength !== null && Number.isFinite(contentLength)) {
    if (contentLength > MAX_IMAGE_BYTES) return jsonResponse(413, '식단 이미지는 15MB 이하여야 합니다.');
    if (contentLength < MIN_IMAGE_BYTES) return jsonResponse(400, IMAGE_TOO_SMALL_MESSAGE);
  }
  const clientKey = rateLimitClientKey(request.headers.get('CF-Connecting-IP'));
  if (!clientKey) return jsonResponse(400, '업로드 요청을 확인할 수 없습니다.');
  const rateLimitSalt = env.RATE_LIMIT_SALT?.trim();
  if (!rateLimitSalt || rateLimitSalt.length < 32) {
    return jsonResponse(503, '업로드 서비스가 준비되지 않았습니다.');
  }
  let weeklyLimit;
  let dailyLimit;
  try {
    weeklyLimit = positiveLimit(env.UPLOAD_WEEKLY_LIMIT, 40);
    dailyLimit = positiveLimit(env.UPLOAD_DAILY_LIMIT, 20);
  } catch {
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
  // IPv6 clients are keyed by their /64 so one subscriber prefix cannot rotate addresses past the lease.
  const addressHash = await sha256Hex(`${rateLimitSalt}:${clientKey}`);
  const leaseScope = `upload-address:${addressHash}`;
  const releaseLease = async () => {
    try {
      await securityStore.releaseLease(leaseScope, leaseOwner);
    } catch (error) {
      console.error('Dormitory meal lease release failed', error instanceof Error ? error.message : error);
    }
  };

  // The per-client lease remains the first throttle and the first D1 write.
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
    await releaseLease();
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
  const imageProblem = imageSanityProblem(image, mimeType);
  if (imageProblem) return jsonResponse(imageProblem.status, imageProblem.message);

  // The shared daily/weekly budget is consumed only for a body that passed every local check,
  // and is refunded below if the GitHub write does not succeed.
  const windows = uploadBudgetWindows(current);
  const budget = {
    dayScope: `upload-day:${windows.date}`,
    dayLimit: dailyLimit,
    dayExpiresAt: windows.dayExpiresAt,
    weekScope: `upload-week:${windows.weekStart}`,
    weekLimit: weeklyLimit,
    weekExpiresAt: windows.weekExpiresAt,
  };
  try {
    if (!await securityStore.consumeUploadBudgets(budget)) {
      return jsonResponse(429, '현재 익명 식단 처리 한도에 도달했습니다.', {
        'Retry-After': String(Math.max(1, windows.dayExpiresAt - nowSeconds)),
      });
    }
  } catch (error) {
    console.error('Dormitory meal budget admission failed', error instanceof Error ? error.message : error);
    await releaseLease();
    return jsonResponse(503, '업로드 서비스가 준비되지 않았습니다.');
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
    // Best effort: the photo is already stored, and main's scheduled sweep picks it up if this wake-up is lost.
    const dispatch = dispatchDormitorySubmission(dependencies.fetchImpl, env, token).catch((error) => {
      console.warn('Dormitory submission dispatch failed', error instanceof Error ? error.message : 'unknown error');
    });
    if (typeof dependencies.context?.waitUntil === 'function') dependencies.context.waitUntil(dispatch);
    else await dispatch;
    return new Response(JSON.stringify({ submissionId, uploadedAt }), {
      status: 202,
      headers: jsonHeaders(),
    });
  } catch (error) {
    console.error('Dormitory meal upload failed', error instanceof Error ? error.message : error);
    try {
      await securityStore.refundUploadBudgets(budget);
    } catch (refundError) {
      console.error('Dormitory meal budget refund failed', refundError instanceof Error ? refundError.message : refundError);
    }
    await releaseLease();
    return jsonResponse(502, '사진을 올리지 못했습니다. 잠시 후 다시 시도해 주세요.');
  }
}

/**
 * Asks main's publish workflow to sweep pending dormitory submissions. The body carries only the event type:
 * no submission id or other client-derived data reaches the workflow, which ignores client_payload anyway.
 */
async function dispatchDormitorySubmission(fetchImpl, env, token) {
  if (env?.GITHUB_OWNER !== 'winter1l' || env?.GITHUB_REPOSITORY !== 'DimaNow') {
    throw new Error('Unexpected dormitory dispatch repository');
  }
  const response = await fetchImpl(DORM_SUBMISSION_DISPATCH_URL, {
    method: 'POST',
    redirect: 'manual',
    signal: AbortSignal.timeout(10000),
    headers: {
      Accept: 'application/vnd.github+json',
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      'User-Agent': 'DIMA-Now-Meal-Upload',
      'X-GitHub-Api-Version': '2022-11-28',
    },
    body: JSON.stringify({ event_type: 'dorm-submission' }),
  });
  if (response.status !== 204) {
    await response.body?.cancel();
    throw new Error(`Dormitory dispatch failed: ${response.status}`);
  }
}

/**
 * Address key for the per-client lease: the full address for IPv4 (including IPv4-mapped
 * IPv6) and the /64 network prefix for IPv6. Returns null when the address is not parseable.
 */
export function rateLimitClientKey(rawAddress) {
  const address = rawAddress?.trim();
  if (!address || address.length > 64) return null;
  const ipv4 = parseIpv4(address);
  if (ipv4) return `v4:${ipv4.join('.')}`;
  const hextets = parseIpv6(address);
  if (!hextets) return null;
  if (hextets.slice(0, 5).every((value) => value === 0) && hextets[5] === 0xffff) {
    return `v4:${[hextets[6] >> 8, hextets[6] & 0xff, hextets[7] >> 8, hextets[7] & 0xff].join('.')}`;
  }
  return `v6:${hextets.slice(0, 4).map((value) => value.toString(16)).join(':')}::/64`;
}

function parseIpv4(address) {
  const parts = address.split('.');
  if (parts.length !== 4) return null;
  const octets = [];
  for (const part of parts) {
    if (!/^\d{1,3}$/.test(part)) return null;
    const value = Number(part);
    if (value > 255) return null;
    octets.push(value);
  }
  return octets;
}

function parseIpv6(rawAddress) {
  const address = rawAddress.replace(/^\[|\]$/g, '').split('%', 1)[0].toLowerCase();
  if (!/^[0-9a-f:.]+$/.test(address)) return null;
  const halves = address.split('::');
  if (halves.length > 2) return null;
  const parseGroups = (text, allowTrailingIpv4) => {
    if (text === '') return [];
    const groups = [];
    const parts = text.split(':');
    for (let index = 0; index < parts.length; index += 1) {
      const part = parts[index];
      if (allowTrailingIpv4 && index === parts.length - 1 && part.includes('.')) {
        const ipv4 = parseIpv4(part);
        if (!ipv4) return null;
        groups.push((ipv4[0] << 8) | ipv4[1], (ipv4[2] << 8) | ipv4[3]);
      } else {
        if (!/^[0-9a-f]{1,4}$/.test(part)) return null;
        groups.push(parseInt(part, 16));
      }
    }
    return groups;
  };
  if (halves.length === 1) {
    const groups = parseGroups(halves[0], true);
    return groups?.length === 8 ? groups : null;
  }
  const head = parseGroups(halves[0], false);
  const tail = parseGroups(halves[1], true);
  if (!head || !tail || head.length + tail.length > 7) return null;
  return [...head, ...new Array(8 - head.length - tail.length).fill(0), ...tail];
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
      // One D1 batch (a single transaction): drop a bounded set of expired leases, which hold
      // salted address hashes, then atomically claim this client's lease.
      const [, result] = await database.batch([
        expiredLeaseDeleteStatement(database, nowSeconds),
        database.prepare(`
          INSERT INTO gateway_leases (scope, owner, expires_at)
          VALUES (?, ?, ?)
          ON CONFLICT(scope) DO UPDATE SET
            owner = excluded.owner,
            expires_at = excluded.expires_at
          WHERE gateway_leases.expires_at <= ?
        `).bind(scope, owner, expiresAtSeconds, nowSeconds),
      ]);
      return Number(result.meta?.changes ?? 0) > 0;
    },
    async refundUploadBudgets(budget) {
      // Atomic decrement of both windows; never below zero.
      await database.prepare(`
        UPDATE gateway_budgets SET used = used - 1
        WHERE scope IN (?, ?) AND used > 0
      `).bind(budget.dayScope, budget.weekScope).run();
    },
    async pruneExpiredLeases(nowSeconds) {
      await expiredLeaseDeleteStatement(database, nowSeconds).run();
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

function expiredLeaseDeleteStatement(database, nowSeconds) {
  return database.prepare(`
    DELETE FROM gateway_leases
    WHERE scope IN (
      SELECT scope FROM gateway_leases WHERE expires_at <= ? ORDER BY expires_at LIMIT ?
    )
  `).bind(nowSeconds, EXPIRED_LEASE_DELETE_LIMIT);
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
  const repository = env.GITHUB_REPOSITORY ?? 'DimaNow';
  if (!/^[A-Za-z0-9._-]{1,100}$/.test(repository)) throw new Error('GITHUB_REPOSITORY is invalid');
  const response = await fetchImpl(`https://api.github.com/app/installations/${encodeURIComponent(installationId)}/access_tokens`, {
    method: 'POST',
    headers: {
      Accept: 'application/vnd.github+json',
      Authorization: `Bearer ${jwt}`,
      'Content-Type': 'application/json',
      'User-Agent': 'DIMA-Now-Meal-Upload',
      'X-GitHub-Api-Version': '2022-11-28',
    },
    // Down-scope the installation token to this one repository and contents:write, which covers
    // both the submission-branch file write and repository_dispatch, whatever the App is granted.
    body: JSON.stringify({
      repositories: [repository],
      permissions: { contents: 'write' },
    }),
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

/**
 * Header-only sanity checks after the signature matched: a minimum body size and parseable,
 * plausible pixel dimensions. Workers cannot re-encode, so this does not prove the rest of
 * the file decodes; it only rejects tiny probes, degenerate headers and decompression bombs.
 */
function imageSanityProblem(bytes, mimeType) {
  if (bytes.byteLength < MIN_IMAGE_BYTES) return { status: 400, message: IMAGE_TOO_SMALL_MESSAGE };
  const dimensions = imageDimensions(bytes, mimeType);
  if (!dimensions) return { status: 415, message: '사진 파일 형식을 확인해 주세요.' };
  const { width, height } = dimensions;
  if (width < MIN_IMAGE_SIDE || height < MIN_IMAGE_SIDE) {
    return { status: 400, message: IMAGE_TOO_SMALL_MESSAGE };
  }
  if (width > MAX_IMAGE_SIDE || height > MAX_IMAGE_SIDE) {
    return { status: 400, message: '식단 사진의 해상도가 너무 큽니다.' };
  }
  return null;
}

export function imageDimensions(bytes, mimeType) {
  if (mimeType === 'image/jpeg') return jpegDimensions(bytes);
  if (mimeType === 'image/png') return pngDimensions(bytes);
  if (mimeType === 'image/webp') return webpDimensions(bytes);
  return null;
}

function uint16BE(bytes, offset) {
  return (bytes[offset] << 8) | bytes[offset + 1];
}

function uint32BE(bytes, offset) {
  return ((bytes[offset] << 24) >>> 0) + (bytes[offset + 1] << 16) + (bytes[offset + 2] << 8) + bytes[offset + 3];
}

function uint24LE(bytes, offset) {
  return bytes[offset] | (bytes[offset + 1] << 8) | (bytes[offset + 2] << 16);
}

function fourCC(bytes, offset) {
  return String.fromCharCode(bytes[offset], bytes[offset + 1], bytes[offset + 2], bytes[offset + 3]);
}

function pngDimensions(bytes) {
  // Signature, then the mandatory first chunk: length 13, type IHDR, width, height.
  if (bytes.length < 33 || uint32BE(bytes, 8) !== 13 || fourCC(bytes, 12) !== 'IHDR') return null;
  return { width: uint32BE(bytes, 16), height: uint32BE(bytes, 20) };
}

const JPEG_SOF_MARKERS = new Set([0xc0, 0xc1, 0xc2, 0xc3, 0xc5, 0xc6, 0xc7, 0xc9, 0xca, 0xcb, 0xcd, 0xce, 0xcf]);

function jpegDimensions(bytes) {
  let offset = 2;
  while (offset + 3 < bytes.length) {
    if (bytes[offset] !== 0xff) return null;
    let marker = bytes[offset + 1];
    // Fill bytes before a marker.
    while (marker === 0xff && offset + 2 < bytes.length) {
      offset += 1;
      marker = bytes[offset + 1];
    }
    if (marker === 0x01 || (marker >= 0xd0 && marker <= 0xd8)) {
      offset += 2;
      continue;
    }
    // Scan data or end of image before any frame header: no usable dimensions.
    if (marker === 0xd9 || marker === 0xda) return null;
    if (offset + 3 >= bytes.length) return null;
    const length = uint16BE(bytes, offset + 2);
    if (length < 2) return null;
    if (JPEG_SOF_MARKERS.has(marker)) {
      if (length < 8 || offset + 8 >= bytes.length) return null;
      return { width: uint16BE(bytes, offset + 7), height: uint16BE(bytes, offset + 5) };
    }
    offset += 2 + length;
  }
  return null;
}

function webpDimensions(bytes) {
  if (bytes.length < 30) return null;
  const chunk = fourCC(bytes, 12);
  if (chunk === 'VP8 ') {
    // Key frame tag (bit 0 clear), start code 9d 01 2a, then 14-bit width and height.
    if ((bytes[20] & 0x01) !== 0 || bytes[23] !== 0x9d || bytes[24] !== 0x01 || bytes[25] !== 0x2a) return null;
    return {
      width: (bytes[26] | (bytes[27] << 8)) & 0x3fff,
      height: (bytes[28] | (bytes[29] << 8)) & 0x3fff,
    };
  }
  if (chunk === 'VP8L') {
    if (bytes[20] !== 0x2f) return null;
    const bits = (bytes[21] | (bytes[22] << 8) | (bytes[23] << 16) | (bytes[24] << 24)) >>> 0;
    return { width: (bits & 0x3fff) + 1, height: ((bits >>> 14) & 0x3fff) + 1 };
  }
  if (chunk === 'VP8X') {
    return { width: uint24LE(bytes, 24) + 1, height: uint24LE(bytes, 27) + 1 };
  }
  return null;
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
