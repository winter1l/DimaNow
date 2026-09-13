import assert from 'node:assert/strict';
import test from 'node:test';
import { buildShuttleReportEvents, createWorker } from '../src/index.mjs';

class FakeRateLimit {
  constructor(success = true) {
    this.success = success;
    this.keys = [];
  }
  async limit({ key }) {
    this.keys.push(key);
    return { success: this.success };
  }
}

function shuttleEnv(overrides = {}) {
  return {
    SHUTTLE_REPORT_HMAC_KEY: 'test-secret-at-least-32-characters',
    RATE_LIMIT_SALT: 'test-rate-limit-salt-at-least-32-characters',
    SHUTTLE_GLOBAL_RATE_LIMIT: new FakeRateLimit(),
    SHUTTLE_REPORT_RATE_LIMIT: new FakeRateLimit(),
    SHUTTLE_EVENT_RATE_LIMIT: new FakeRateLimit(),
    ...overrides,
  };
}

async function issueReporterToken(worker, env, address = '203.0.113.7') {
  const response = await worker.fetch(new Request('https://upload.example/v1/shuttle-reporter-token', {
    method: 'POST',
    headers: { 'CF-Connecting-IP': address },
  }), env);
  assert.equal(response.status, 201);
  return (await response.json()).token;
}

class FakeReportStore {
  constructor() { this.rows = new Map(); }
  async upsert(report) {
    const key = `${report.serviceDate}|${report.runId}|${report.stopCallId}|${report.reporterHash}`;
    const inserted = !this.rows.has(key);
    this.rows.set(key, report);
    return inserted;
  }
  async remove(key) { this.rows.delete(`${key.serviceDate}|${key.runId}|${key.stopCallId}|${key.reporterHash}`); }
  async aggregates(serviceDate, scheduleRevision, reporterHash = null) {
    const groups = new Map();
    for (const row of this.rows.values()) {
      if (row.serviceDate !== serviceDate || row.scheduleRevision !== scheduleRevision) continue;
      const key = `${row.runId}|${row.stopCallId}|${row.stopSequence}`;
      const current = groups.get(key) ?? { runId: row.runId, stopCallId: row.stopCallId, stopSequence: row.stopSequence, count: 0, reportedByYou: false };
      current.count += 1;
      current.reportedByYou ||= row.reporterHash === reporterHash;
      groups.set(key, current);
    }
    return [...groups.values()];
  }
}

class FakeSecurityStore {
  constructor() {
    this.leases = new Map();
    this.budgets = new Map();
    this.releases = [];
  }
  async claimLease(scope, owner, nowSeconds, expiresAtSeconds) {
    const existing = this.leases.get(scope);
    if (existing && existing.expiresAtSeconds > nowSeconds) return false;
    this.leases.set(scope, { owner, expiresAtSeconds });
    return true;
  }
  async releaseLease(scope, owner) {
    this.releases.push({ scope, owner });
    if (this.leases.get(scope)?.owner === owner) this.leases.delete(scope);
  }
  async consumeUploadBudgets(budget) {
    const dayUsed = this.budgets.get(budget.dayScope) ?? 0;
    const weekUsed = this.budgets.get(budget.weekScope) ?? 0;
    if (dayUsed >= budget.dayLimit || weekUsed >= budget.weekLimit) return false;
    this.budgets.set(budget.dayScope, dayUsed + 1);
    this.budgets.set(budget.weekScope, weekUsed + 1);
    this.lastBudgetExpiry = Math.max(budget.dayExpiresAt, budget.weekExpiresAt);
    return true;
  }
  async prune(nowSeconds) {
    this.prunedAt = nowSeconds;
  }
}

function uploadEnv(overrides = {}) {
  return {
    RATE_LIMIT_SALT: 'test-rate-limit-salt-at-least-32-characters',
    UPLOAD_DAILY_LIMIT: '20',
    UPLOAD_WEEKLY_LIMIT: '40',
    ...overrides,
  };
}

function imageRequest(address = '203.0.113.7', body = new Uint8Array([0xff, 0xd8, 0xff, 0xd9])) {
  return new Request('https://upload.example/v1/dormitory-meals', {
    method: 'POST',
    headers: {
      'Content-Type': 'image/jpeg',
      'X-Dima-Image-Extension': 'jpg',
      'CF-Connecting-IP': address,
    },
    body,
    duplex: 'half',
  });
}

test('worker keeps boarding calls with stable loop sequence IDs and field additions separate', () => {
  const departures = [
    { serviceDay: 'MONDAY', routeId: 'B-evening', stopId: 'yein', originZone: 'YEIN', destinationZone: 'MAIN', departureTime: '18:40', arrivalTime: '18:45' },
    { serviceDay: 'MONDAY', routeId: 'A-evening', stopId: 'one-room', originZone: 'ONE_ROOM', destinationZone: 'MAIN', departureTime: '18:50', arrivalTime: '18:55' },
    { serviceDay: 'MONDAY', routeId: 'A-evening', stopId: 'stadium-stop', originZone: 'MAIN', destinationZone: 'YEIN', departureTime: '18:55', arrivalTime: '19:00' },
    { serviceDay: 'MONDAY', routeId: 'B-evening', stopId: 'stadium-stop', originZone: 'MAIN', destinationZone: 'YEIN', departureTime: '18:55', arrivalTime: '19:00' },
  ];

  const events = buildShuttleReportEvents(departures);
  const evening = events.filter((event) => event.runId === 'evening-loop-monday-1850');

  assert.deepEqual(evening.map((event) => [event.stopSequence, event.stopId, event.expectedTime]), [
    [0, 'yein', '18:40'],
    [2, 'one-room', '18:50'],
    [3, 'stadium-stop', '18:55'],
  ]);
  const fieldOverrides = events.filter((event) => event.runId.startsWith('field_override-'));
  assert.equal(fieldOverrides.length, 20);
  assert.equal(fieldOverrides.some((event) => event.runId === 'field_override-monday-1430-one_room'), true);
});

test('published evening departures with intermediate waiting time are retained', () => {
  const departures = [
    { routeId: 'B-evening', stopId: 'yein', serviceDay: 'TUESDAY', originZone: 'YEIN', destinationZone: 'MAIN', departureTime: '19:00', arrivalTime: '19:05' },
    { routeId: 'A-evening', stopId: 'one-room', serviceDay: 'TUESDAY', originZone: 'ONE_ROOM', destinationZone: 'MAIN', departureTime: '19:20', arrivalTime: '19:25' },
    { routeId: 'A-evening', stopId: 'stadium-stop', serviceDay: 'TUESDAY', originZone: 'MAIN', destinationZone: 'YEIN', departureTime: '19:25', arrivalTime: '19:30' },
  ];
  const events = buildShuttleReportEvents(departures).filter(event => event.runId === 'evening-loop-tuesday-1920');
  assert.deepEqual(events.map(event => [event.stopSequence, event.expectedTime]), [[0, '19:00'], [2, '19:20'], [3, '19:25']]);
});

test('last published evening departure survives without a complete following loop', () => {
  const events = buildShuttleReportEvents([
    { routeId: 'B-evening', stopId: 'yein', serviceDay: 'TUESDAY', originZone: 'YEIN', destinationZone: 'MAIN', departureTime: '21:55', arrivalTime: '22:00' },
  ]);
  assert.equal(events.some(event => event.stopId === 'yein' && event.expectedTime === '21:55' && event.serviceDay === 'TUESDAY'), true);
});

test('shuttle report is anonymous, idempotent per server-issued reporter identity, visible, and revocable', async () => {
  const store = new FakeReportStore();
  const worker = createWorker({
    now: () => new Date('2026-09-02T09:55:00Z'),
    scheduleProvider: async () => ({
      revision: 9,
      events: [{
        runId: 'evening-loop-wednesday-1850',
        stopCallId: 'evening-loop-wednesday-1850:3',
        stopSequence: 3,
        serviceDay: 'WEDNESDAY',
        expectedTime: '18:55',
      }],
    }),
    reportStoreFactory: () => store,
  });
  const env = shuttleEnv();
  const reporterToken = await issueReporterToken(worker, env);
  const body = JSON.stringify({
    serviceDate: '2026-09-02',
    scheduleRevision: 9,
    runId: 'evening-loop-wednesday-1850',
    stopCallId: 'evening-loop-wednesday-1850:3',
  });
  const request = () => new Request('https://upload.example/v1/shuttle-reports', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Dima-Reporter': reporterToken, 'CF-Connecting-IP': '203.0.113.7' },
    body,
  });

  assert.equal((await worker.fetch(request(), env)).status, 201);
  assert.equal((await worker.fetch(request(), env)).status, 200);
  const list = await worker.fetch(new Request('https://upload.example/v1/shuttle-reports?serviceDate=2026-09-02&scheduleRevision=9', {
    headers: { 'X-Dima-Reporter': reporterToken, 'CF-Connecting-IP': '203.0.113.7' },
  }), env);
  assert.deepEqual((await list.json()).reports, [{
    runId: 'evening-loop-wednesday-1850',
    stopCallId: 'evening-loop-wednesday-1850:3',
    stopSequence: 3,
    count: 1,
    reportedByYou: true,
  }]);
  const saved = [...store.rows.values()][0];
  assert.equal(saved.reporterHash.length, 64);
  assert.equal(JSON.stringify(saved).includes(reporterToken), false);

  const removal = await worker.fetch(new Request('https://upload.example/v1/shuttle-reports', {
    method: 'DELETE',
    headers: { 'Content-Type': 'application/json', 'X-Dima-Reporter': reporterToken, 'CF-Connecting-IP': '203.0.113.7' },
    body,
  }), env);
  assert.equal(removal.status, 204);
  assert.equal(store.rows.size, 0);
});

test('shuttle report rejects a stop call that is not in the current schedule', async () => {
  const worker = createWorker({
    now: () => new Date('2026-09-02T09:55:00Z'),
    scheduleProvider: async () => ({ revision: 9, events: [] }),
    reportStoreFactory: () => new FakeReportStore(),
  });
  const env = shuttleEnv();
  const reporterToken = await issueReporterToken(worker, env);
  const response = await worker.fetch(new Request('https://upload.example/v1/shuttle-reports', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Dima-Reporter': reporterToken, 'CF-Connecting-IP': '203.0.113.7' },
    body: JSON.stringify({ serviceDate: '2026-09-02', scheduleRevision: 9, runId: 'invented', stopCallId: 'invented:0' }),
  }), env);

  assert.equal(response.status, 409);
});

test('arrival-only loop calls cannot be reported but the published departure can', async () => {
  const departures = [
    { serviceDay: 'TUESDAY', routeId: 'B-evening', stopId: 'yein', originZone: 'YEIN', destinationZone: 'MAIN', departureTime: '18:40', arrivalTime: '18:45' },
    { serviceDay: 'TUESDAY', routeId: 'A-evening', stopId: 'one-room', originZone: 'ONE_ROOM', destinationZone: 'MAIN', departureTime: '18:50', arrivalTime: '18:55' },
    { serviceDay: 'TUESDAY', routeId: 'A-evening', stopId: 'stadium-stop', originZone: 'MAIN', destinationZone: 'YEIN', departureTime: '18:55', arrivalTime: '19:00' },
  ];
  const store = new FakeReportStore();
  let now = new Date('2026-09-08T09:46:00Z');
  const worker = createWorker({
    now: () => now,
    scheduleProvider: async () => ({ revision: 9, events: buildShuttleReportEvents(departures) }),
    reportStoreFactory: () => store,
  });
  const env = shuttleEnv();
  const reporterToken = await issueReporterToken(worker, env);
  const request = (sequence) => new Request('https://upload.example/v1/shuttle-reports', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Dima-Reporter': reporterToken, 'CF-Connecting-IP': '203.0.113.7' },
    body: JSON.stringify({ serviceDate: '2026-09-08', scheduleRevision: 9, runId: 'evening-loop-tuesday-1850', stopCallId: `evening-loop-tuesday-1850:${sequence}` }),
  });
  assert.equal((await worker.fetch(request(1), env)).status, 409);
  assert.equal(store.rows.size, 0);
  now = new Date('2026-09-08T09:55:00Z');
  assert.equal((await worker.fetch(request(3), env)).status, 201);
  now = new Date('2026-09-08T10:01:00Z');
  assert.equal((await worker.fetch(request(4), env)).status, 409);
});

test('an unissued shuttle reporter token is rejected before schedule or D1 work', async () => {
  let scheduleCalls = 0;
  let storeCalls = 0;
  const worker = createWorker({
    now: () => new Date('2026-09-02T09:55:00Z'),
    scheduleProvider: async () => { scheduleCalls += 1; return { revision: 9, events: [] }; },
    reportStoreFactory: () => { storeCalls += 1; return new FakeReportStore(); },
  });
  const response = await worker.fetch(new Request('https://upload.example/v1/shuttle-reports', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'CF-Connecting-IP': '203.0.113.7',
      'X-Dima-Reporter': 'caller_minted_token_123456789012345',
    },
    body: JSON.stringify({ serviceDate: '2026-09-02', scheduleRevision: 9, runId: 'invented', stopCallId: 'invented:0' }),
  }), shuttleEnv());

  assert.equal(response.status, 401);
  assert.equal(scheduleCalls, 0);
  assert.equal(storeCalls, 0);
});

test('event-wide limiter caps reports independently of reporter identity', async () => {
  let scheduleCalls = 0;
  let storeCalls = 0;
  const worker = createWorker({
    now: () => new Date('2026-09-02T09:55:00Z'),
    scheduleProvider: async () => { scheduleCalls += 1; return { revision: 9, events: [] }; },
    reportStoreFactory: () => { storeCalls += 1; return new FakeReportStore(); },
  });
  const env = shuttleEnv({ SHUTTLE_EVENT_RATE_LIMIT: new FakeRateLimit(false) });
  const reporterToken = await issueReporterToken(worker, env);
  const response = await worker.fetch(new Request('https://upload.example/v1/shuttle-reports', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'CF-Connecting-IP': '203.0.113.7',
      'X-Dima-Reporter': reporterToken,
    },
    body: JSON.stringify({ serviceDate: '2026-09-02', scheduleRevision: 9, runId: 'run-1', stopCallId: 'run-1:0' }),
  }), env);

  assert.equal(response.status, 429);
  assert.equal(scheduleCalls, 0);
  assert.equal(storeCalls, 0);
});

test('missing event limiter fails closed before schedule or D1 work', async () => {
  let scheduleCalls = 0;
  let storeCalls = 0;
  const worker = createWorker({
    now: () => new Date('2026-09-02T09:55:00Z'),
    scheduleProvider: async () => { scheduleCalls += 1; return { revision: 9, events: [] }; },
    reportStoreFactory: () => { storeCalls += 1; return new FakeReportStore(); },
  });
  const env = shuttleEnv({ SHUTTLE_EVENT_RATE_LIMIT: undefined });
  const reporterToken = await issueReporterToken(worker, env);
  const response = await worker.fetch(new Request('https://upload.example/v1/shuttle-reports', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'CF-Connecting-IP': '203.0.113.7',
      'X-Dima-Reporter': reporterToken,
    },
    body: JSON.stringify({ serviceDate: '2026-09-02', scheduleRevision: 9, runId: 'run-1', stopCallId: 'run-1:0' }),
  }), env);

  assert.equal(response.status, 503);
  assert.equal(scheduleCalls, 0);
  assert.equal(storeCalls, 0);
});

test('shuttle mutations require JSON before reading the body or schedule', async () => {
  let bodyReads = 0;
  let scheduleCalls = 0;
  const worker = createWorker({
    now: () => new Date('2026-09-02T09:55:00Z'),
    bodyReader: async () => { bodyReads += 1; return new Uint8Array(); },
    scheduleProvider: async () => { scheduleCalls += 1; return { revision: 9, events: [] }; },
  });
  const env = shuttleEnv();
  const reporterToken = await issueReporterToken(worker, env);
  const response = await worker.fetch(new Request('https://upload.example/v1/shuttle-reports', {
    method: 'POST',
    headers: {
      'Content-Type': 'text/plain',
      'CF-Connecting-IP': '203.0.113.7',
      'X-Dima-Reporter': reporterToken,
    },
    body: '{}',
  }), env);

  assert.equal(response.status, 415);
  assert.equal(bodyReads, 0);
  assert.equal(scheduleCalls, 0);
});

test('shuttle mutation JSON is capped and parsed exactly once', async () => {
  let parseCalls = 0;
  let scheduleCalls = 0;
  const store = new FakeReportStore();
  const worker = createWorker({
    now: () => new Date('2026-09-02T09:55:00Z'),
    jsonParser: (value) => { parseCalls += 1; return JSON.parse(value); },
    scheduleProvider: async () => {
      scheduleCalls += 1;
      return {
        revision: 9,
        events: [{
          runId: 'run-1', stopCallId: 'run-1:0', stopSequence: 0,
          serviceDay: 'WEDNESDAY', stopId: 'stadium-stop', expectedTime: '18:55',
        }],
      };
    },
    reportStoreFactory: () => store,
  });
  const env = shuttleEnv();
  const reporterToken = await issueReporterToken(worker, env);
  const accepted = await worker.fetch(new Request('https://upload.example/v1/shuttle-reports', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'CF-Connecting-IP': '203.0.113.7',
      'X-Dima-Reporter': reporterToken,
    },
    body: JSON.stringify({ serviceDate: '2026-09-02', scheduleRevision: 9, runId: 'run-1', stopCallId: 'run-1:0' }),
  }), env);
  assert.equal(accepted.status, 201);
  assert.equal(parseCalls, 1);

  const oversized = await worker.fetch(new Request('https://upload.example/v1/shuttle-reports', {
    method: 'DELETE',
    headers: {
      'Content-Type': 'application/json',
      'CF-Connecting-IP': '203.0.113.7',
      'X-Dima-Reporter': reporterToken,
    },
    body: JSON.stringify({ padding: 'x'.repeat(9 * 1024) }),
  }), env);
  assert.equal(oversized.status, 413);
  assert.equal(parseCalls, 1);
  assert.equal(scheduleCalls, 1);
});

test('fresh schedule cache prevents revision probes from causing repeated origin fetches', async () => {
  let scheduleCalls = 0;
  let storeCalls = 0;
  const worker = createWorker({
    now: () => new Date('2026-09-02T09:55:00Z'),
    scheduleProvider: async () => { scheduleCalls += 1; return { revision: 9, events: [] }; },
    reportStoreFactory: () => { storeCalls += 1; return new FakeReportStore(); },
  });
  const env = shuttleEnv();
  const reporterToken = await issueReporterToken(worker, env);
  const request = () => new Request(
    'https://upload.example/v1/shuttle-reports?serviceDate=2026-09-02&scheduleRevision=999',
    { headers: { 'CF-Connecting-IP': '203.0.113.7', 'X-Dima-Reporter': reporterToken } },
  );

  assert.equal((await worker.fetch(request(), env)).status, 409);
  assert.equal((await worker.fetch(request(), env)).status, 409);
  assert.equal(scheduleCalls, 1);
  assert.equal(storeCalls, 0);
});

test('retention pruning runs from the scheduled handler instead of every request', async () => {
  const pruned = [];
  const store = { prune: async (oldestDate) => { pruned.push(oldestDate); } };
  const worker = createWorker({
    now: () => new Date('2026-09-02T09:55:00Z'),
    reportStoreFactory: () => store,
    securityStoreFactory: () => new FakeSecurityStore(),
  });
  const pending = [];

  worker.scheduled({}, shuttleEnv(), { waitUntil: (promise) => pending.push(promise) });
  await Promise.all(pending);

  assert.deepEqual(pruned, ['2026-08-26']);
});

test('mandatory route limits reject every shuttle method before schedule or D1 work', async () => {
  for (const method of ['GET', 'POST', 'DELETE']) {
    let scheduleCalls = 0;
    let storeCalls = 0;
    const worker = createWorker({
      scheduleProvider: async () => { scheduleCalls += 1; return { revision: 9, events: [] }; },
      reportStoreFactory: () => { storeCalls += 1; return new FakeReportStore(); },
    });
    const url = method === 'GET'
      ? 'https://upload.example/v1/shuttle-reports?serviceDate=2026-09-02&scheduleRevision=9'
      : 'https://upload.example/v1/shuttle-reports';
    const request = new Request(url, {
      method,
      headers: {
        'CF-Connecting-IP': '203.0.113.7',
        'Content-Type': 'application/json',
        'X-Dima-Reporter': 'untrusted_local_token_1234567890',
      },
      body: method === 'GET' ? undefined : '{}',
    });
    const response = await worker.fetch(request, {
      SHUTTLE_REPORT_HMAC_KEY: 'test-secret-at-least-32-characters',
      RATE_LIMIT_SALT: 'test-rate-limit-salt-at-least-32-characters',
      SHUTTLE_GLOBAL_RATE_LIMIT: new FakeRateLimit(true),
      SHUTTLE_REPORT_RATE_LIMIT: new FakeRateLimit(false),
    });

    assert.equal(response.status, 429, method);
    assert.equal(scheduleCalls, 0, method);
    assert.equal(storeCalls, 0, method);
  }
});

test('anonymous image upload uses only the server-side GitHub token', async () => {
  const calls = [];
  const security = new FakeSecurityStore();
  const worker = createWorker({
    now: () => new Date('2026-08-31T01:15:00Z'),
    randomUUID: () => 'submission-123',
    githubTokenProvider: async () => 'server-installation-token',
    securityStoreFactory: () => security,
    dormitoryPublicationProvider: async () => false,
    fetch: async (url, init) => {
      calls.push({ url: String(url), init });
      return new Response('{}', { status: 201 });
    },
  });
  const env = uploadEnv({
    GITHUB_APP_ID: '4774955',
    GITHUB_INSTALLATION_ID: '157828401',
    GITHUB_APP_PRIVATE_KEY: 'server-only',
  });
  const request = new Request('https://upload.example/v1/dormitory-meals', {
    method: 'POST',
    headers: {
      'Content-Type': 'image/jpeg',
      'X-Dima-Image-Extension': 'jpg',
      'CF-Connecting-IP': '203.0.113.7',
    },
    body: new Uint8Array([0xff, 0xd8, 0xff, 0xd9]),
  });

  const response = await worker.fetch(request, env);
  const payload = await response.json();

  assert.equal(response.status, 202);
  assert.equal(payload.submissionId, 'submission-123');
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, 'https://api.github.com/repos/winter1l/DimaNow/contents/dorm-submissions/submission-123.jpg');
  assert.equal(calls[0].init.headers.Authorization, 'Bearer server-installation-token');
  assert.equal(request.headers.get('Authorization'), null);
});

test('a second anonymous submission from the same address is rate limited', async () => {
  const security = new FakeSecurityStore();
  const worker = createWorker({
    now: () => new Date('2026-08-31T01:15:00Z'),
    randomUUID: () => 'submission-123',
    githubTokenProvider: async () => 'server-installation-token',
    securityStoreFactory: () => security,
    dormitoryPublicationProvider: async () => false,
    fetch: async () => new Response('{}', { status: 201 }),
  });
  const env = uploadEnv({
    GITHUB_APP_ID: '4774955',
    GITHUB_INSTALLATION_ID: '157828401',
    GITHUB_APP_PRIVATE_KEY: 'server-only',
  });
  const makeRequest = () => new Request('https://upload.example/v1/dormitory-meals', {
    method: 'POST',
    headers: {
      'Content-Type': 'image/png',
      'X-Dima-Image-Extension': 'png',
      'CF-Connecting-IP': '203.0.113.7',
    },
    body: new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
  });

  assert.equal((await worker.fetch(makeRequest(), env)).status, 202);
  assert.equal((await worker.fetch(makeRequest(), env)).status, 429);
});

test('parallel anonymous uploads acquire one atomic address lease and make one GitHub write', async () => {
  const security = new FakeSecurityStore();
  let sequence = 0;
  let githubWrites = 0;
  const worker = createWorker({
    now: () => new Date('2026-09-02T01:15:00Z'),
    randomUUID: () => `submission-${++sequence}`,
    securityStoreFactory: () => security,
    dormitoryPublicationProvider: async () => false,
    githubTokenProvider: async () => 'server-installation-token',
    fetch: async () => { githubWrites += 1; return new Response('{}', { status: 201 }); },
  });
  const env = uploadEnv();

  const responses = await Promise.all([
    worker.fetch(imageRequest(), env),
    worker.fetch(imageRequest(), env),
  ]);

  assert.deepEqual(responses.map((response) => response.status).sort(), [202, 429]);
  assert.equal(githubWrites, 1);
});

test('a failed upload cannot release a newer owner lease', async () => {
  const security = new FakeSecurityStore();
  const worker = createWorker({
    now: () => new Date('2026-09-02T01:15:00Z'),
    randomUUID: () => 'original-owner',
    securityStoreFactory: () => security,
    dormitoryPublicationProvider: async () => false,
    githubTokenProvider: async () => {
      const scope = [...security.leases.keys()].at(0);
      security.leases.set(scope, { owner: 'newer-owner', expiresAtSeconds: Number.MAX_SAFE_INTEGER });
      throw new Error('fixture failure');
    },
  });

  const response = await worker.fetch(imageRequest(), uploadEnv());
  const surviving = [...security.leases.values()].at(0);

  assert.equal(response.status, 502);
  assert.equal(surviving.owner, 'newer-owner');
  assert.equal(security.releases.at(0).owner, 'original-owner');
});

test('global daily and weekly budgets reject other addresses before GitHub credentials', async () => {
  const security = new FakeSecurityStore();
  let tokenCalls = 0;
  const worker = createWorker({
    now: () => new Date('2026-09-02T01:15:00Z'),
    securityStoreFactory: () => security,
    dormitoryPublicationProvider: async () => false,
    githubTokenProvider: async () => { tokenCalls += 1; return 'server-installation-token'; },
    fetch: async () => new Response('{}', { status: 201 }),
  });
  const env = uploadEnv({ UPLOAD_DAILY_LIMIT: '1', UPLOAD_WEEKLY_LIMIT: '1' });

  assert.equal((await worker.fetch(imageRequest('203.0.113.7'), env)).status, 202);
  assert.equal((await worker.fetch(imageRequest('203.0.113.8'), env)).status, 429);
  assert.equal(tokenCalls, 1);
});

test('a daily rejection does not consume the remaining weekly admission', async () => {
  const security = new FakeSecurityStore();
  let current = new Date('2026-09-02T01:15:00Z');
  const worker = createWorker({
    now: () => current,
    randomUUID: () => crypto.randomUUID(),
    securityStoreFactory: () => security,
    dormitoryPublicationProvider: async () => false,
    githubTokenProvider: async () => 'server-installation-token',
    fetch: async () => new Response('{}', { status: 201 }),
  });
  const env = uploadEnv({ UPLOAD_DAILY_LIMIT: '1', UPLOAD_WEEKLY_LIMIT: '2' });

  assert.equal((await worker.fetch(imageRequest('203.0.113.7'), env)).status, 202);
  assert.equal((await worker.fetch(imageRequest('203.0.113.8'), env)).status, 429);
  current = new Date('2026-09-03T01:15:00Z');
  assert.equal((await worker.fetch(imageRequest('203.0.113.9'), env)).status, 202);
});

test('an address lease rejection happens before the request body is read', async () => {
  const security = new FakeSecurityStore();
  security.claimLease = async () => false;
  let bodyReads = 0;
  const worker = createWorker({
    now: () => new Date('2026-09-02T01:15:00Z'),
    securityStoreFactory: () => security,
    bodyReader: async () => { bodyReads += 1; return new Uint8Array([0xff, 0xd8, 0xff, 0xd9]); },
    dormitoryPublicationProvider: async () => false,
  });

  const response = await worker.fetch(imageRequest(), uploadEnv());

  assert.equal(response.status, 429);
  assert.equal(bodyReads, 0);
});

test('an already published target week is rejected before body or GitHub work', async () => {
  const security = new FakeSecurityStore();
  let bodyReads = 0;
  let tokenCalls = 0;
  const worker = createWorker({
    now: () => new Date('2026-09-02T01:15:00Z'),
    securityStoreFactory: () => security,
    dormitoryPublicationProvider: async () => true,
    bodyReader: async () => { bodyReads += 1; return new Uint8Array([0xff, 0xd8, 0xff, 0xd9]); },
    githubTokenProvider: async () => { tokenCalls += 1; return 'server-installation-token'; },
    fetch: async () => new Response('{}', { status: 201 }),
  });

  const response = await worker.fetch(imageRequest(), uploadEnv());

  assert.equal(response.status, 409);
  assert.equal(bodyReads, 0);
  assert.equal(tokenCalls, 0);
});

test('chunked oversized image stops without draining later body chunks', async () => {
  const security = new FakeSecurityStore();
  let producedBytes = 0;
  let step = 0;
  const stream = new ReadableStream({
    pull(controller) {
      step += 1;
      if (step === 1) {
        const chunk = new Uint8Array(15 * 1024 * 1024);
        chunk.set([0xff, 0xd8, 0xff]);
        producedBytes += chunk.byteLength;
        controller.enqueue(chunk);
      } else if (step === 2) {
        producedBytes += 1;
        controller.enqueue(new Uint8Array([0]));
      } else if (step === 3) {
        const chunk = new Uint8Array(5 * 1024 * 1024);
        producedBytes += chunk.byteLength;
        controller.enqueue(chunk);
      } else {
        const chunk = new Uint8Array(5 * 1024 * 1024);
        producedBytes += chunk.byteLength;
        controller.enqueue(chunk);
        controller.close();
      }
    },
  });
  const worker = createWorker({
    now: () => new Date('2026-09-02T01:15:00Z'),
    securityStoreFactory: () => security,
    dormitoryPublicationProvider: async () => false,
    githubTokenProvider: async () => 'server-installation-token',
  });

  const response = await worker.fetch(imageRequest('203.0.113.7', stream), uploadEnv());

  assert.equal(response.status, 413);
  assert.equal(producedBytes <= 20 * 1024 * 1024 + 1, true);
});

test('non-image content is rejected before GitHub is called', async () => {
  let called = false;
  const worker = createWorker({
    githubTokenProvider: async () => 'server-installation-token',
    fetch: async () => { called = true; return new Response('{}', { status: 201 }); },
  });
  const response = await worker.fetch(new Request('https://upload.example/v1/dormitory-meals', {
    method: 'POST',
    headers: { 'Content-Type': 'text/plain', 'CF-Connecting-IP': '203.0.113.9' },
    body: 'not an image',
  }), {});

  assert.equal(response.status, 415);
  assert.equal(called, false);
});
