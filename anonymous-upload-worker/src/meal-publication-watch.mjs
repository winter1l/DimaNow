const DATA_ROOT = 'https://winter1l.github.io/DimaNow/data/v1';
const DISPATCH_URL = 'https://api.github.com/repos/winter1l/DimaNow/dispatches';
const DAY_MILLIS = 86400000;
const MANIFEST_MAX_BYTES = 64 * 1024;
const MEAL_MAX_BYTES = 512 * 1024;
// Keep in sync with the Kotlin student meal contract. Only explicit closure labels
// are complete with one line; an incomplete ordinary meal still needs collection.
const CLOSURE_LABELS = new Set([
  '휴무', '휴일', '공휴일', '대체공휴일', '미운영', '운영안함', '휴관',
  '추석', '추석공휴일', '추석연휴', '설날', '설연휴',
]);

// Cloudflare numbers Sunday as 1; named days avoid confusing GitHub's Sunday=0 syntax.
export const STUDENT_MEAL_WATCH_CRONS = Object.freeze([
  '7,37 0-4 * * MON',
  '7 5-23/2 * * MON',
  '7 1-23/2 * * SUN,TUE,WED,THU,FRI,SAT',
]);

/** Called only by the Worker's scheduled handler; it adds no public HTTP route. */
export function createStudentMealPublicationWatch(dependencies = {}) {
  const fetchImpl = dependencies.fetch ?? fetch;
  const now = dependencies.now ?? (() => new Date());
  const githubTokenProvider = dependencies.githubTokenProvider;

  return async (env) => {
    const current = now();
    if (!(current instanceof Date) || !Number.isFinite(current.getTime())) throw new Error('Invalid watch clock');
    const kst = new Date(current.getTime() + 9 * 60 * 60 * 1000);
    const day = kst.getUTCDay();
    const monday = new Date(Date.UTC(kst.getUTCFullYear(), kst.getUTCMonth(), kst.getUTCDate())
      - ((day + 6) % 7) * DAY_MILLIS);
    const weekStart = monday.toISOString().slice(0, 10);
    const manifest = parseJson(await readPublicBytes(fetchImpl, `${DATA_ROOT}/manifest.json`, MANIFEST_MAX_BYTES));
    requireSchema(manifest, 'manifest');
    const descriptor = manifest.datasets?.meal;
    validateDescriptor(descriptor, current);

    if (descriptor.url) {
      const bytes = await readPublicBytes(fetchImpl, `${DATA_ROOT}/${descriptor.url}`, MEAL_MAX_BYTES);
      const digest = await crypto.subtle.digest('SHA-256', bytes);
      const sha256 = Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, '0')).join('');
      if (sha256 !== descriptor.sha256) throw new Error('Student meal hash mismatch');
      const payload = parseJson(bytes);
      validateMeal(payload);
      if (isCompleteWeek(payload, monday)) {
        return { status: 'skipped', reason: 'current-week-complete' };
      }
    }

    const morningWatch = day === 1 && kst.getUTCHours() >= 9 && kst.getUTCHours() < 14;
    const quietMillis = (morningWatch ? 25 : 110) * 60 * 1000;
    if (descriptor.lastAttemptAt && current.getTime() - Date.parse(descriptor.lastAttemptAt) < quietMillis) {
      return { status: 'skipped', reason: 'recent-attempt' };
    }
    if (env?.GITHUB_OWNER !== 'winter1l' || env?.GITHUB_REPOSITORY !== 'DimaNow') {
      throw new Error('Unexpected meal watch repository');
    }
    if (typeof githubTokenProvider !== 'function') throw new Error('GitHub token provider required');
    const token = await githubTokenProvider(env);
    if (typeof token !== 'string' || !token) throw new Error('GitHub installation token unavailable');
    const response = await fetchImpl(DISPATCH_URL, {
      method: 'POST',
      redirect: 'manual',
      signal: AbortSignal.timeout(10000),
      headers: {
        Accept: 'application/vnd.github+json',
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
        'User-Agent': 'DIMA-Now-Meal-Publication-Watch',
        'X-GitHub-Api-Version': '2022-11-28',
      },
      body: JSON.stringify({
        event_type: 'student-meal-publication-watch',
        client_payload: { week_start: weekStart },
      }),
    });
    if (response.status !== 204) {
      await response.body?.cancel();
      throw new Error(`Student meal dispatch failed: ${response.status}`);
    }
    return { status: 'dispatched', weekStart };
  };
}

async function readPublicBytes(fetchImpl, url, maxBytes) {
  // workerd does not support redirect:error. Manual mode leaves 3xx responses
  // unfollowed, and the status check below rejects them before reading any data.
  const response = await fetchImpl(url, { redirect: 'manual', cache: 'no-store', signal: AbortSignal.timeout(10000) });
  if (!response.ok || response.redirected) {
    await response.body?.cancel();
    throw new Error(`Student meal public fetch failed: ${response.status}`);
  }
  const declared = response.headers.get('content-length');
  if (declared !== null && (!/^\d+$/.test(declared) || Number(declared) > maxBytes)) {
    await response.body?.cancel();
    throw new Error('Student meal public response exceeds byte limit');
  }
  if (!response.body) throw new Error('Student meal public response is empty');
  const reader = response.body.getReader();
  const chunks = [];
  let size = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > maxBytes) {
        await reader.cancel();
        throw new Error('Student meal public response exceeds byte limit');
      }
      chunks.push(value);
    }
  } finally {
    reader.releaseLock();
  }
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
  return bytes;
}

function parseJson(bytes) {
  return JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes));
}

function requireSchema(value, kind) {
  if (!value || typeof value !== 'object' || Array.isArray(value)
      || (value.schemaVersion !== undefined && value.schemaVersion !== 1)) {
    throw new Error(`Invalid student meal ${kind} schema`);
  }
}

function validateDescriptor(descriptor, current) {
  if (!descriptor || typeof descriptor !== 'object' || Array.isArray(descriptor)
      || !Number.isSafeInteger(descriptor.revision) || descriptor.revision < 0
      || !['READY', 'WAITING', 'NEEDS_REVIEW'].includes(descriptor.state)) {
    throw new Error('Invalid student meal descriptor');
  }
  const unpublished = descriptor.revision === 0 && descriptor.url === '' && descriptor.sha256 === ''
    && descriptor.state !== 'READY';
  if (!unpublished && (typeof descriptor.sha256 !== 'string' || !/^[0-9a-f]{64}$/.test(descriptor.sha256)
      || descriptor.url !== `meal/${descriptor.sha256}.json`)) {
    throw new Error('Invalid student meal content address');
  }
  if (typeof descriptor.lastAttemptAt !== 'string'
      || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(?:Z|[+-]\d{2}:\d{2})$/.test(descriptor.lastAttemptAt)
      || !Number.isFinite(Date.parse(descriptor.lastAttemptAt))
      || Date.parse(descriptor.lastAttemptAt) > current.getTime() + 5 * 60 * 1000) {
    throw new Error('Invalid student meal attempt time');
  }
}

function dateMillis(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return NaN;
  const millis = Date.parse(`${value}T00:00:00Z`);
  return Number.isFinite(millis) && new Date(millis).toISOString().slice(0, 10) === value ? millis : NaN;
}

function validateMeal(payload) {
  requireSchema(payload, 'payload');
  const start = dateMillis(payload.weekStart);
  const end = dateMillis(payload.weekEnd);
  if (!Number.isFinite(start) || new Date(start).getUTCDay() !== 1 || end !== start + 6 * DAY_MILLIS
      || !Array.isArray(payload.days) || payload.days.length > 7) throw new Error('Invalid student meal week');
  const seen = new Set();
  for (const day of payload.days) {
    const date = dateMillis(day?.date);
    if (!Number.isFinite(date) || date < start || date > end || seen.has(day.date)
        || !Array.isArray(day.menuLines) || day.menuLines.length > 100
        || !day.menuLines.every((line) => typeof line === 'string' && line.length <= 2000)) {
      throw new Error('Invalid student meal day');
    }
    seen.add(day.date);
  }
}

function isCompleteWeek(payload, monday) {
  if (payload.weekStart !== monday.toISOString().slice(0, 10) || payload.days.length !== 5) return false;
  const dates = payload.days.map((day) => day.date).sort();
  return dates.every((date, index) => date === new Date(monday.getTime() + index * DAY_MILLIS).toISOString().slice(0, 10))
    && payload.days.every((day) => day.menuLines.every((line) => line.trim().length > 0)
      && (day.menuLines.length >= 2
        || (day.menuLines.length === 1 && CLOSURE_LABELS.has(day.menuLines[0].replace(/\s+/g, '')))));
}
