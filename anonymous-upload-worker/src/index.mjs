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
  const scheduleProvider = dependencies.scheduleProvider
    ?? (() => loadCurrentShuttleSchedule(fetchImpl));
  const reportStoreFactory = dependencies.reportStoreFactory
    ?? ((env) => createD1ReportStore(env.SHUTTLE_REPORTS));

  return {
    async fetch(request, env) {
      const url = new URL(request.url);
      if (url.pathname === '/v1/shuttle-reports') {
        return handleShuttleReports(request, url, env, {
          now,
          scheduleProvider,
          reportStoreFactory,
        });
      }
      if (url.pathname !== '/v1/dormitory-meals') {
        return jsonResponse(404, '요청한 경로가 없습니다.');
      }
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

      const image = new Uint8Array(await request.arrayBuffer());
      if (image.byteLength === 0 || image.byteLength > MAX_IMAGE_BYTES) {
        return jsonResponse(image.byteLength === 0 ? 400 : 413, image.byteLength === 0
          ? '식단 사진이 비어 있습니다.'
          : '식단 이미지는 15MB 이하여야 합니다.');
      }
      if (!hasExpectedImageSignature(image, mimeType)) {
        return jsonResponse(415, '사진 파일 형식을 확인해 주세요.');
      }

      const clientAddress = request.headers.get('CF-Connecting-IP')?.trim();
      if (!clientAddress) {
        return jsonResponse(400, '업로드 요청을 확인할 수 없습니다.');
      }
      if (!env.RATE_LIMIT) {
        return jsonResponse(503, '업로드 서비스가 준비되지 않았습니다.');
      }

      const rateLimitKey = `upload:${await sha256Hex(`${env.RATE_LIMIT_SALT ?? ''}:${clientAddress}`)}`;
      if (await env.RATE_LIMIT.get(rateLimitKey)) {
        return jsonResponse(429, '잠시 후 다시 시도해 주세요.', { 'Retry-After': String(RATE_LIMIT_SECONDS) });
      }
      await env.RATE_LIMIT.put(rateLimitKey, 'pending', { expirationTtl: RATE_LIMIT_SECONDS });

      const submissionId = randomUUID();
      const uploadedAt = now().toISOString();
      try {
        const token = await githubTokenProvider(env);
        const owner = env.GITHUB_OWNER ?? 'winter1l';
        const repository = env.GITHUB_REPOSITORY ?? 'DimaNow';
        const branch = env.GITHUB_SUBMISSION_BRANCH ?? 'dorm-submissions';
        const path = `dorm-submissions/${submissionId}.${expectedExtension}`;
        const response = await fetchImpl(
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
        if (!response.ok) {
          throw new Error(`GitHub content upload failed: ${response.status}`);
        }
        await env.RATE_LIMIT.put(rateLimitKey, submissionId, { expirationTtl: RATE_LIMIT_SECONDS });
        return new Response(JSON.stringify({ submissionId, uploadedAt }), {
          status: 202,
          headers: jsonHeaders(),
        });
      } catch (error) {
        await env.RATE_LIMIT.delete(rateLimitKey);
        console.error('Dormitory meal upload failed', error instanceof Error ? error.message : error);
        return jsonResponse(502, '사진을 올리지 못했습니다. 잠시 후 다시 시도해 주세요.');
      }
    },
  };
}

async function handleShuttleReports(request, url, env, dependencies) {
  if (!['GET', 'POST', 'DELETE'].includes(request.method)) {
    return jsonResponse(405, 'GET, POST, DELETE 요청만 지원합니다.', { Allow: 'GET, POST, DELETE' });
  }
  let store;
  try {
    store = dependencies.reportStoreFactory(env);
  } catch {
    return jsonResponse(503, '셔틀 신고 서비스가 준비되지 않았습니다.');
  }
  const current = dependencies.now();
  const serviceDate = request.method === 'GET'
    ? url.searchParams.get('serviceDate')
    : (await readJsonObject(request))?.serviceDate;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(serviceDate ?? '') || serviceDate !== kstDate(current)) {
    return jsonResponse(400, '오늘 운행만 확인할 수 있습니다.');
  }
  const requestBody = request.method === 'GET' ? null : await readJsonObjectFromClone(request);
  if (request.method !== 'GET' && !requestBody) {
    return jsonResponse(400, '신고 내용을 확인해 주세요.');
  }
  const scheduleRevision = Number(request.method === 'GET'
    ? url.searchParams.get('scheduleRevision')
    : requestBody.scheduleRevision);
  if (!Number.isSafeInteger(scheduleRevision) || scheduleRevision <= 0) {
    return jsonResponse(400, '시간표 버전을 확인해 주세요.');
  }

  let schedule;
  try {
    schedule = await dependencies.scheduleProvider(env);
  } catch (error) {
    console.error('Shuttle schedule validation failed', error instanceof Error ? error.message : error);
    return jsonResponse(503, '셔틀 시간표를 확인하지 못했습니다.');
  }
  if (schedule.revision !== scheduleRevision) {
    return jsonResponse(409, '셔틀 시간표가 갱신되었습니다. 다시 확인해 주세요.');
  }
  await store.prune?.(dateDaysBefore(serviceDate, 7));
  if (request.method === 'GET') {
    const reporterToken = request.headers.get('X-Dima-Reporter')?.trim();
    let reporterHash = null;
    if (reporterToken) {
      if (!/^[A-Za-z0-9_-]{20,128}$/.test(reporterToken)) return jsonResponse(400, '앱 식별 정보를 확인해 주세요.');
      const hmacKey = env.SHUTTLE_REPORT_HMAC_KEY?.trim();
      if (!hmacKey || hmacKey.length < 32) return jsonResponse(503, '셔틀 신고 서비스가 준비되지 않았습니다.');
      reporterHash = await hmacSha256Hex(hmacKey, reporterToken);
    }
    const reports = await store.aggregates(serviceDate, scheduleRevision, reporterHash);
    return new Response(JSON.stringify({ serviceDate, scheduleRevision, reports }), {
      status: 200,
      headers: jsonHeaders(),
    });
  }

  const runId = boundedIdentifier(requestBody.runId, 120);
  const stopCallId = boundedIdentifier(requestBody.stopCallId, 160);
  if (!runId || !stopCallId) return jsonResponse(400, '운행 정보를 확인해 주세요.');
  const event = schedule.events.find((candidate) => candidate.runId === runId && candidate.stopCallId === stopCallId);
  if (!event || event.serviceDay !== dayOfWeekName(serviceDate)) {
    return jsonResponse(409, '현재 시간표에 없는 운행입니다.');
  }
  if (request.method === 'POST' && !isReportWindowOpen(current, serviceDate, event, schedule.events)) {
    return jsonResponse(409, '현재 신고할 수 있는 운행이 아닙니다.');
  }
  const reporterToken = request.headers.get('X-Dima-Reporter')?.trim();
  if (!/^[A-Za-z0-9_-]{20,128}$/.test(reporterToken ?? '')) {
    return jsonResponse(400, '앱 식별 정보를 확인해 주세요.');
  }
  const hmacKey = env.SHUTTLE_REPORT_HMAC_KEY?.trim();
  if (!hmacKey || hmacKey.length < 32) return jsonResponse(503, '셔틀 신고 서비스가 준비되지 않았습니다.');
  const reporterHash = await hmacSha256Hex(hmacKey, reporterToken);
  if (request.method === 'POST' && env.SHUTTLE_REPORT_RATE_LIMIT) {
    const rate = await env.SHUTTLE_REPORT_RATE_LIMIT.limit({ key: reporterHash });
    if (!rate.success) return jsonResponse(429, '잠시 후 다시 시도해 주세요.', { 'Retry-After': '60' });
  }
  const key = { serviceDate, scheduleRevision, runId, stopCallId, reporterHash };
  if (request.method === 'DELETE') {
    await store.remove(key);
    return new Response(null, { status: 204 });
  }
  const inserted = await store.upsert({
    ...key,
    stopSequence: event.stopSequence,
    createdAt: current.toISOString(),
  });
  return new Response(JSON.stringify({ reported: true, alreadyReported: !inserted }), {
    status: inserted ? 201 : 200,
    headers: jsonHeaders(),
  });
}

function createD1ReportStore(database) {
  if (!database) throw new Error('SHUTTLE_REPORTS D1 binding is required');
  return {
    async upsert(report) {
      const result = await database.prepare(`
        INSERT OR IGNORE INTO shuttle_reports
          (service_date, schedule_revision, run_id, stop_call_id, stop_sequence, reporter_hash, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
      `).bind(
        report.serviceDate,
        report.scheduleRevision,
        report.runId,
        report.stopCallId,
        report.stopSequence,
        report.reporterHash,
        report.createdAt,
      ).run();
      return Number(result.meta?.changes ?? 0) > 0;
    },
    async remove(key) {
      await database.prepare(`
        DELETE FROM shuttle_reports
        WHERE service_date = ? AND schedule_revision = ? AND run_id = ? AND stop_call_id = ? AND reporter_hash = ?
      `).bind(key.serviceDate, key.scheduleRevision, key.runId, key.stopCallId, key.reporterHash).run();
    },
    async aggregates(serviceDate, scheduleRevision, reporterHash) {
      const result = await database.prepare(`
        SELECT run_id, stop_call_id, stop_sequence, COUNT(*) AS report_count,
          MAX(CASE WHEN reporter_hash = ? THEN 1 ELSE 0 END) AS reported_by_you
        FROM shuttle_reports
        WHERE service_date = ? AND schedule_revision = ?
        GROUP BY run_id, stop_call_id, stop_sequence
        ORDER BY run_id, stop_sequence
      `).bind(reporterHash ?? '', serviceDate, scheduleRevision).all();
      return result.results.map((row) => ({
        runId: row.run_id,
        stopCallId: row.stop_call_id,
        stopSequence: Number(row.stop_sequence),
        count: Number(row.report_count),
        reportedByYou: Number(row.reported_by_you) === 1,
      }));
    },
    async prune(oldestDate) {
      await database.prepare('DELETE FROM shuttle_reports WHERE service_date < ?').bind(oldestDate).run();
    },
  };
}

async function loadCurrentShuttleSchedule(fetchImpl) {
  const root = 'https://winter1l.github.io/DimaNow/data/v1';
  const manifestResponse = await fetchImpl(`${root}/manifest.json`, { headers: { Accept: 'application/json' } });
  if (!manifestResponse.ok) throw new Error(`manifest ${manifestResponse.status}`);
  const manifest = await manifestResponse.json();
  const descriptor = manifest.datasets?.shuttle;
  if (!descriptor || descriptor.state !== 'READY' || !/^shuttle\/[0-9a-f]{64}\.json$/.test(descriptor.url)) {
    throw new Error('shuttle descriptor unavailable');
  }
  const payloadResponse = await fetchImpl(`${root}/${descriptor.url}`, { headers: { Accept: 'application/json' } });
  if (!payloadResponse.ok) throw new Error(`shuttle payload ${payloadResponse.status}`);
  const payload = await payloadResponse.json();
  if (!Array.isArray(payload.departures)) throw new Error('invalid shuttle payload');
  return { revision: Number(descriptor.revision), events: buildShuttleReportEvents(payload.departures) };
}

export function buildShuttleReportEvents(departures) {
  const scheduleDepartures = withFieldShuttleOverrides(departures);
  const daytime = scheduleDepartures
    .filter((departure) => !String(departure.routeId).endsWith('-evening'))
    .map((departure) => {
      const pattern = ({ A: 'day_a', B: 'day_b', 'A-field-extra': 'field_override', C: 'sunday' })[departure.routeId] ?? 'other';
      const runId = `${pattern}-${departure.serviceDay.toLowerCase()}-${departure.departureTime.replace(':', '')}-${departure.originZone.toLowerCase()}`;
      return {
        runId,
        stopCallId: `${runId}:0`,
        stopSequence: 0,
        serviceDay: departure.serviceDay,
        stopId: departure.stopId,
        expectedTime: departure.departureTime,
      };
    });
  const evening = scheduleDepartures
    .filter((departure) => departure.routeId === 'A-evening' && departure.originZone === 'ONE_ROOM' && departure.destinationZone === 'MAIN')
    .flatMap((oneRoom) => {
      const firstMainTime = subtractMinutes(oneRoom.departureTime, 5);
      const firstLeg = scheduleDepartures.find((candidate) => candidate.serviceDay === oneRoom.serviceDay
        && candidate.routeId === 'B-evening'
        && candidate.originZone === 'YEIN'
        && candidate.destinationZone === 'MAIN'
        && candidate.arrivalTime === firstMainTime);
      const finalLeg = scheduleDepartures.find((candidate) => candidate.serviceDay === oneRoom.serviceDay
        && String(candidate.routeId).endsWith('-evening')
        && candidate.originZone === 'MAIN'
        && candidate.destinationZone === 'YEIN'
        && candidate.departureTime === oneRoom.arrivalTime);
      if (!firstLeg || !finalLeg?.arrivalTime) return [];
      const runId = `evening-loop-${oneRoom.serviceDay.toLowerCase()}-${oneRoom.departureTime.replace(':', '')}`;
      return [
        ['yein', firstLeg.departureTime],
        ['stadium-stop', firstMainTime],
        ['one-room', oneRoom.departureTime],
        ['stadium-stop', oneRoom.arrivalTime],
        ['yein', finalLeg.arrivalTime],
      ].map(([stopId, expectedTime], stopSequence) => ({
        runId,
        stopCallId: `${runId}:${stopSequence}`,
        stopSequence,
        serviceDay: oneRoom.serviceDay,
        stopId,
        expectedTime,
      }));
    });
  return [...daytime, ...evening];
}

function withFieldShuttleOverrides(departures) {
  const serviceDays = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'];
  const departureTimes = ['14:30', '15:30', '16:30', '17:30'];
  const eventKey = (departure) => [
    departure.routeId,
    departure.stopId,
    departure.direction,
    departure.serviceDay,
    departure.departureTime,
    departure.originZone,
    departure.destinationZone ?? '',
  ].join('|');
  const existing = new Set(departures.map(eventKey));
  const additions = [];
  for (const serviceDay of serviceDays) {
    for (const departureTime of departureTimes) {
      const departure = {
        serviceDay,
        routeId: 'A-field-extra',
        stopId: 'one-room',
        direction: 'TO_MAIN',
        originZone: 'ONE_ROOM',
        destinationZone: 'MAIN',
        departureTime,
        arrivalTime: null,
      };
      if (!existing.has(eventKey(departure))) additions.push(departure);
    }
  }
  return [...departures, ...additions];
}

function isReportWindowOpen(now, serviceDate, event, events) {
  const expectedAt = kstInstant(serviceDate, event.expectedTime);
  if (now < expectedAt) return false;
  const nextSameStop = events
    .filter((candidate) => candidate.serviceDay === event.serviceDay
      && candidate.stopId === event.stopId
      && candidate.stopCallId !== event.stopCallId
      && candidate.expectedTime > event.expectedTime)
    .sort((left, right) => left.expectedTime.localeCompare(right.expectedTime))[0];
  const fifteenMinutesLater = new Date(expectedAt.getTime() + 15 * 60 * 1000);
  const closesAt = nextSameStop
    ? new Date(Math.min(fifteenMinutesLater.getTime(), kstInstant(serviceDate, nextSameStop.expectedTime).getTime()))
    : fifteenMinutesLater;
  return now < closesAt;
}

function kstInstant(date, time) { return new Date(`${date}T${time}:00+09:00`); }
function kstDate(date) { return new Date(date.getTime() + 9 * 60 * 60 * 1000).toISOString().slice(0, 10); }
function dateDaysBefore(date, days) {
  const value = new Date(`${date}T00:00:00Z`);
  value.setUTCDate(value.getUTCDate() - days);
  return value.toISOString().slice(0, 10);
}
function dayOfWeekName(date) {
  return ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'][new Date(`${date}T00:00:00Z`).getUTCDay()];
}
function subtractMinutes(time, minutes) {
  const [hour, minute] = time.split(':').map(Number);
  const total = (hour * 60 + minute - minutes + 24 * 60) % (24 * 60);
  return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`;
}
function boundedIdentifier(value, max) {
  return typeof value === 'string' && value.length <= max && /^[a-z0-9_:-]+$/.test(value) ? value : null;
}
async function readJsonObject(request) {
  const value = await readJsonObjectFromClone(request);
  return value;
}
async function readJsonObjectFromClone(request) {
  try {
    const value = await request.clone().json();
    return value && typeof value === 'object' && !Array.isArray(value) ? value : null;
  } catch {
    return null;
  }
}

async function hmacSha256Hex(secret, value) {
  const key = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(secret),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign'],
  );
  const bytes = new Uint8Array(await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(value)));
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
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
