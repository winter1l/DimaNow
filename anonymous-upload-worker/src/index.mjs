const MAX_IMAGE_BYTES = 15 * 1024 * 1024;
const MAX_SHUTTLE_JSON_BYTES = 8 * 1024;
const RATE_LIMIT_SECONDS = 10 * 60;
const REPORTER_TOKEN_SECONDS = 24 * 60 * 60;
const SHUTTLE_SCHEDULE_CACHE_MILLIS = 60 * 1000;
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
  const cachedScheduleProvider = createCachedScheduleProvider(scheduleProvider, now);
  const reportStoreFactory = dependencies.reportStoreFactory
    ?? ((env) => createD1ReportStore(env.SHUTTLE_REPORTS));
  const securityStoreFactory = dependencies.securityStoreFactory
    ?? ((env) => createD1SecurityStore(env.SHUTTLE_REPORTS));
  const dormitoryPublicationProvider = dependencies.dormitoryPublicationProvider
    ?? ((_env, current) => hasCurrentDormitoryPublication(fetchImpl, current));
  const bodyReader = dependencies.bodyReader ?? readBoundedRequestBody;

  return {
    async fetch(request, env) {
      const url = new URL(request.url);
      if (url.pathname === '/v1/shuttle-reporter-token') {
        return handleShuttleReporterToken(request, env, { now });
      }
      if (url.pathname === '/v1/shuttle-reports') {
        return handleShuttleReports(request, url, env, {
          now,
          scheduleProvider: cachedScheduleProvider,
          reportStoreFactory,
          jsonParser: dependencies.jsonParser ?? JSON.parse,
          bodyReader,
        });
      }
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
    scheduled(_event, env, context) {
      context.waitUntil((async () => {
        const store = reportStoreFactory(env);
        await store.prune?.(dateDaysBefore(kstDate(now()), 7));
        const securityStore = securityStoreFactory(env);
        await securityStore.prune?.(Math.floor(now().getTime() / 1000));
      })().catch((error) => {
        console.error('Scheduled gateway maintenance failed', error instanceof Error ? error.message : error);
      }));
    },
  };
}

function createCachedScheduleProvider(provider, now) {
  let cached = null;
  let inFlight = null;
  return async (env) => {
    const currentMillis = now().getTime();
    if (cached && currentMillis - cached.loadedAtMillis < SHUTTLE_SCHEDULE_CACHE_MILLIS) return cached.schedule;
    if (!inFlight) {
      inFlight = Promise.resolve(provider(env)).then((schedule) => {
        if (!Number.isSafeInteger(schedule?.revision) || schedule.revision <= 0 || !Array.isArray(schedule.events)) {
          throw new Error('invalid shuttle schedule');
        }
        cached = { schedule, loadedAtMillis: now().getTime() };
        return schedule;
      }).finally(() => { inFlight = null; });
    }
    return inFlight;
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

async function handleShuttleReporterToken(request, env, dependencies) {
  if (request.method !== 'POST') {
    return jsonResponse(405, 'POST 요청만 지원합니다.', { Allow: 'POST' });
  }
  const routeAdmission = await enforceShuttleRouteLimits(request, env, 'TOKEN');
  if (routeAdmission instanceof Response) return routeAdmission;
  const hmacKey = env.SHUTTLE_REPORT_HMAC_KEY?.trim();
  if (!hmacKey || hmacKey.length < 32) {
    return jsonResponse(503, '셔틀 신고 서비스가 준비되지 않았습니다.');
  }
  const expiresAt = Math.floor(dependencies.now().getTime() / 1000) + REPORTER_TOKEN_SECONDS;
  const subject = await hmacSha256Hex(hmacKey, `reporter:${routeAdmission.clientAddress}`);
  const unsigned = `v1.${expiresAt}.${subject}`;
  const signature = await hmacSha256Hex(hmacKey, unsigned);
  return new Response(JSON.stringify({ token: `${unsigned}.${signature}`, expiresAt }), {
    status: 201,
    headers: jsonHeaders(),
  });
}

async function enforceShuttleRouteLimits(request, env, routeKey) {
  const clientAddress = request.headers.get('CF-Connecting-IP')?.trim();
  if (!clientAddress) return jsonResponse(400, '셔틀 요청을 확인할 수 없습니다.');
  const rateLimitSalt = env.RATE_LIMIT_SALT?.trim();
  if (!rateLimitSalt || rateLimitSalt.length < 32 || !env.SHUTTLE_GLOBAL_RATE_LIMIT || !env.SHUTTLE_REPORT_RATE_LIMIT) {
    return jsonResponse(503, '셔틀 신고 서비스가 준비되지 않았습니다.');
  }
  const globalRate = await env.SHUTTLE_GLOBAL_RATE_LIMIT.limit({ key: routeKey });
  if (!globalRate.success) {
    return jsonResponse(429, '잠시 후 다시 시도해 주세요.', { 'Retry-After': '60' });
  }
  const clientKey = await sha256Hex(`${rateLimitSalt}:${clientAddress}`);
  const clientRate = await env.SHUTTLE_REPORT_RATE_LIMIT.limit({ key: `${routeKey}:${clientKey}` });
  if (!clientRate.success) {
    return jsonResponse(429, '잠시 후 다시 시도해 주세요.', { 'Retry-After': '60' });
  }
  return { clientAddress };
}

async function handleShuttleReports(request, url, env, dependencies) {
  if (!['GET', 'POST', 'DELETE'].includes(request.method)) {
    return jsonResponse(405, 'GET, POST, DELETE 요청만 지원합니다.', { Allow: 'GET, POST, DELETE' });
  }
  const routeAdmission = await enforceShuttleRouteLimits(request, env, request.method);
  if (routeAdmission instanceof Response) return routeAdmission;
  const current = dependencies.now();
  let requestBody = null;
  if (request.method !== 'GET') {
    const contentType = request.headers.get('Content-Type')?.split(';', 1)[0]?.trim().toLowerCase();
    if (contentType !== 'application/json') {
      return jsonResponse(415, 'JSON 요청만 지원합니다.');
    }
    try {
      const bytes = await dependencies.bodyReader(request, MAX_SHUTTLE_JSON_BYTES);
      requestBody = parseJsonObject(bytes, dependencies.jsonParser);
    } catch (error) {
      if (error instanceof BodyTooLargeError) return jsonResponse(413, '신고 내용이 너무 큽니다.');
      return jsonResponse(400, '신고 내용을 확인해 주세요.');
    }
    if (!requestBody) return jsonResponse(400, '신고 내용을 확인해 주세요.');
  }

  const serviceDate = request.method === 'GET' ? url.searchParams.get('serviceDate') : requestBody.serviceDate;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(serviceDate ?? '') || serviceDate !== kstDate(current)) {
    return jsonResponse(400, '오늘 운행만 확인할 수 있습니다.');
  }
  const scheduleRevision = Number(request.method === 'GET'
    ? url.searchParams.get('scheduleRevision')
    : requestBody.scheduleRevision);
  if (!Number.isSafeInteger(scheduleRevision) || scheduleRevision <= 0) {
    return jsonResponse(400, '시간표 버전을 확인해 주세요.');
  }

  const hmacKey = env.SHUTTLE_REPORT_HMAC_KEY?.trim();
  if (!hmacKey || hmacKey.length < 32) return jsonResponse(503, '셔틀 신고 서비스가 준비되지 않았습니다.');
  const reporterToken = request.headers.get('X-Dima-Reporter')?.trim();
  let reporterHash = null;
  if (reporterToken) {
    reporterHash = await verifyReporterToken(reporterToken, hmacKey, routeAdmission.clientAddress, current);
    if (!reporterHash) return jsonResponse(401, '셔틀 신고 신원을 다시 발급해 주세요.');
  } else if (request.method !== 'GET') {
    return jsonResponse(401, '셔틀 신고 신원을 다시 발급해 주세요.');
  }

  const runId = request.method === 'GET' ? null : boundedIdentifier(requestBody.runId, 120);
  const stopCallId = request.method === 'GET' ? null : boundedIdentifier(requestBody.stopCallId, 160);
  if (request.method !== 'GET' && (!runId || !stopCallId)) {
    return jsonResponse(400, '운행 정보를 확인해 주세요.');
  }
  if (request.method === 'POST') {
    if (!env.SHUTTLE_EVENT_RATE_LIMIT) return jsonResponse(503, '셔틀 신고 서비스가 준비되지 않았습니다.');
    const eventRate = await env.SHUTTLE_EVENT_RATE_LIMIT.limit({
      key: `${serviceDate}:${scheduleRevision}:${runId}:${stopCallId}`,
    });
    if (!eventRate.success) {
      return jsonResponse(429, '이 운행의 신고가 잠시 제한되었습니다.', { 'Retry-After': '60' });
    }
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
  let store;
  try {
    store = dependencies.reportStoreFactory(env);
  } catch {
    return jsonResponse(503, '셔틀 신고 서비스가 준비되지 않았습니다.');
  }
  if (request.method === 'GET') {
    const reports = await store.aggregates(serviceDate, scheduleRevision, reporterHash);
    return new Response(JSON.stringify({ serviceDate, scheduleRevision, reports }), {
      status: 200,
      headers: jsonHeaders(),
    });
  }

  const event = schedule.events.find((candidate) => candidate.runId === runId && candidate.stopCallId === stopCallId);
  if (!event || event.serviceDay !== dayOfWeekName(serviceDate)) {
    return jsonResponse(409, '현재 시간표에 없는 운행입니다.');
  }
  if (request.method === 'POST' && !isReportWindowOpen(current, serviceDate, event, schedule.events)) {
    return jsonResponse(409, '현재 신고할 수 있는 운행이 아닙니다.');
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

export function buildShuttleReportEvents(departures) {
  const scheduleDepartures = withFieldShuttleOverrides(departures);
  const standalone = scheduleDepartures
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
      const firstLeg = scheduleDepartures.filter((candidate) => candidate.serviceDay === oneRoom.serviceDay
        && candidate.routeId === 'B-evening'
        && candidate.originZone === 'YEIN'
        && candidate.destinationZone === 'MAIN'
        && candidate.arrivalTime && candidate.arrivalTime < oneRoom.departureTime)
        .sort((left, right) => right.arrivalTime.localeCompare(left.arrivalTime))[0];
      const firstMainTime = firstLeg?.arrivalTime;
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
      })).filter((event) => [0, 2, 3].includes(event.stopSequence));
    });
  const remaining = standalone.filter(event => !evening.some(call =>
    call.serviceDay === event.serviceDay && call.stopId === event.stopId && call.expectedTime === event.expectedTime));
  return [...remaining, ...evening];
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

async function verifyReporterToken(token, hmacKey, clientAddress, current) {
  const parts = token?.split('.') ?? [];
  if (parts.length !== 4 || parts[0] !== 'v1' || !/^\d{10}$/.test(parts[1]) ||
      !/^[0-9a-f]{64}$/.test(parts[2]) || !/^[0-9a-f]{64}$/.test(parts[3])) return null;
  const expiresAt = Number(parts[1]);
  const nowSeconds = Math.floor(current.getTime() / 1000);
  if (!Number.isSafeInteger(expiresAt) || expiresAt <= nowSeconds || expiresAt > nowSeconds + REPORTER_TOKEN_SECONDS) {
    return null;
  }
  const subject = await hmacSha256Hex(hmacKey, `reporter:${clientAddress}`);
  if (!constantTimeEqual(parts[2], subject)) return null;
  const signature = await hmacSha256Hex(hmacKey, `${parts[0]}.${parts[1]}.${parts[2]}`);
  return constantTimeEqual(parts[3], signature) ? subject : null;
}

function constantTimeEqual(left, right) {
  if (left.length !== right.length) return false;
  let difference = 0;
  for (let index = 0; index < left.length; index += 1) {
    difference |= left.charCodeAt(index) ^ right.charCodeAt(index);
  }
  return difference === 0;
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
