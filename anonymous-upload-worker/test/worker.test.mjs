import assert from 'node:assert/strict';
import test from 'node:test';
import { createWorker } from '../src/index.mjs';

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

test('only the dormitory upload route is served', async () => {
  const worker = createWorker({
    securityStoreFactory: () => { throw new Error('unknown routes must not reach D1'); },
  });
  for (const [method, path] of [
    ['GET', '/'],
    ['POST', '/v1/unknown'],
    ['GET', '/v1/dormitory-meals/extra'],
    ['DELETE', '/v1'],
  ]) {
    const response = await worker.fetch(new Request(`https://upload.example${path}`, {
      method,
      headers: { 'CF-Connecting-IP': '203.0.113.7', 'Content-Type': 'application/json' },
      body: method === 'GET' ? undefined : '{}',
    }), uploadEnv());
    assert.equal(response.status, 404, `${method} ${path}`);
    assert.deepEqual(await response.json(), { message: '요청한 경로가 없습니다.' });
  }
});

test('scheduled maintenance prunes only gateway admission state', async () => {
  const security = new FakeSecurityStore();
  const worker = createWorker({
    now: () => new Date('2026-09-02T09:55:00Z'),
    securityStoreFactory: () => security,
  });
  const pending = [];

  worker.scheduled({}, uploadEnv(), { waitUntil: (promise) => pending.push(promise) });
  await Promise.all(pending);

  assert.equal(security.prunedAt, Date.parse('2026-09-02T09:55:00Z') / 1000);
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
