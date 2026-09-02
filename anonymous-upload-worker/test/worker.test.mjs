import assert from 'node:assert/strict';
import test from 'node:test';
import { buildShuttleReportEvents, createWorker } from '../src/index.mjs';

class FakeKv {
  constructor() { this.values = new Map(); }
  async get(key) { return this.values.get(key) ?? null; }
  async put(key, value) { this.values.set(key, value); }
  async delete(key) { this.values.delete(key); }
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

test('worker derives the same five-stop evening loop and keeps field additions separate', () => {
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
    [1, 'stadium-stop', '18:45'],
    [2, 'one-room', '18:50'],
    [3, 'stadium-stop', '18:55'],
    [4, 'yein', '19:00'],
  ]);
  const fieldOverrides = events.filter((event) => event.runId.startsWith('field_override-'));
  assert.equal(fieldOverrides.length, 20);
  assert.equal(fieldOverrides.some((event) => event.runId === 'field_override-monday-1430-one_room'), true);
});

test('shuttle report is anonymous, idempotent per install, visible to others, and revocable', async () => {
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
  const env = { SHUTTLE_REPORT_HMAC_KEY: 'test-secret-at-least-32-characters' };
  const body = JSON.stringify({
    serviceDate: '2026-09-02',
    scheduleRevision: 9,
    runId: 'evening-loop-wednesday-1850',
    stopCallId: 'evening-loop-wednesday-1850:3',
  });
  const request = () => new Request('https://upload.example/v1/shuttle-reports', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Dima-Reporter': 'install_token_12345678901234567890' },
    body,
  });

  assert.equal((await worker.fetch(request(), env)).status, 201);
  assert.equal((await worker.fetch(request(), env)).status, 200);
  const list = await worker.fetch(new Request('https://upload.example/v1/shuttle-reports?serviceDate=2026-09-02&scheduleRevision=9', {
    headers: { 'X-Dima-Reporter': 'install_token_12345678901234567890' },
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
  assert.equal(JSON.stringify(saved).includes('install_token_12345678901234567890'), false);

  const removal = await worker.fetch(new Request('https://upload.example/v1/shuttle-reports', {
    method: 'DELETE',
    headers: { 'Content-Type': 'application/json', 'X-Dima-Reporter': 'install_token_12345678901234567890' },
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
  const response = await worker.fetch(new Request('https://upload.example/v1/shuttle-reports', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Dima-Reporter': 'install_token_12345678901234567890' },
    body: JSON.stringify({ serviceDate: '2026-09-02', scheduleRevision: 9, runId: 'invented', stopCallId: 'invented:0' }),
  }), { SHUTTLE_REPORT_HMAC_KEY: 'test-secret-at-least-32-characters' });

  assert.equal(response.status, 409);
});

test('anonymous image upload uses only the server-side GitHub token', async () => {
  const calls = [];
  const worker = createWorker({
    now: () => new Date('2026-08-31T01:15:00Z'),
    randomUUID: () => 'submission-123',
    githubTokenProvider: async () => 'server-installation-token',
    fetch: async (url, init) => {
      calls.push({ url: String(url), init });
      return new Response('{}', { status: 201 });
    },
  });
  const env = {
    RATE_LIMIT: new FakeKv(),
    GITHUB_APP_ID: '4774955',
    GITHUB_INSTALLATION_ID: '157828401',
    GITHUB_APP_PRIVATE_KEY: 'server-only',
  };
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
  const worker = createWorker({
    now: () => new Date('2026-08-31T01:15:00Z'),
    randomUUID: () => 'submission-123',
    githubTokenProvider: async () => 'server-installation-token',
    fetch: async () => new Response('{}', { status: 201 }),
  });
  const env = {
    RATE_LIMIT: new FakeKv(),
    GITHUB_APP_ID: '4774955',
    GITHUB_INSTALLATION_ID: '157828401',
    GITHUB_APP_PRIVATE_KEY: 'server-only',
  };
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
  }), { RATE_LIMIT: new FakeKv() });

  assert.equal(response.status, 415);
  assert.equal(called, false);
});
