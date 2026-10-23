import test from 'node:test';
import assert from 'node:assert/strict';
import { createWorker } from '../src/index.mjs';
import { feedbackIssue, feedbackReport, FEEDBACK_ISSUES_URL } from '../src/feedback.mjs';

class MemoryStore {
  constructor() {
    this.leases = new Map();
    this.budgets = new Map();
  }
  async claimLease(scope, owner, nowSeconds, expiresAtSeconds) {
    const existing = this.leases.get(scope);
    if (existing && existing.expiresAtSeconds > nowSeconds) return false;
    this.leases.set(scope, { owner, expiresAtSeconds });
    return true;
  }
  async releaseLease(scope, owner) {
    if (this.leases.get(scope)?.owner === owner) this.leases.delete(scope);
  }
  async consumeUploadBudgets(budget) {
    const day = this.budgets.get(budget.dayScope) ?? 0;
    const week = this.budgets.get(budget.weekScope) ?? 0;
    if (day >= budget.dayLimit || week >= budget.weekLimit) return false;
    this.budgets.set(budget.dayScope, day + 1);
    this.budgets.set(budget.weekScope, week + 1);
    return true;
  }
  async refundUploadBudgets(budget) {
    for (const scope of [budget.dayScope, budget.weekScope]) {
      const used = this.budgets.get(scope) ?? 0;
      if (used > 0) this.budgets.set(scope, used - 1);
    }
  }
}

class MemoryBucket {
  constructor() { this.objects = new Map(); }
  async put(key, value, options = {}) { this.objects.set(key, { bytes: value, httpMetadata: options.httpMetadata }); }
  async get(key) { const object = this.objects.get(key); return object ? { body: object.bytes, httpMetadata: object.httpMetadata } : null; }
  async list({ prefix = '' } = {}) {
    return { objects: [...this.objects.entries()].filter(([key]) => key.startsWith(prefix)).map(([key, o]) => ({ key, size: o.bytes.length })), truncated: false };
  }
  async delete(keys) { for (const key of [].concat(keys)) this.objects.delete(key); }
}

/** A PNG header that says 400×800, padded; enough for the signature and size checks. */
function pngBase64(width = 400, height = 800) {
  const bytes = new Uint8Array(2048);
  bytes.set([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 13, 0x49, 0x48, 0x44, 0x52]);
  new DataView(bytes.buffer).setUint32(16, width);
  new DataView(bytes.buffer).setUint32(20, height);
  return Buffer.from(bytes).toString('base64');
}

const env = {
  RATE_LIMIT_SALT: 'x'.repeat(40),
  GITHUB_OWNER: 'winter1l',
  GITHUB_REPOSITORY: 'DimaNow',
  FEEDBACK_DAILY_LIMIT: '2',
  FEEDBACK_WEEKLY_LIMIT: '10',
};

function feedbackRequest(body, address = '203.0.113.7') {
  return new Request('https://upload.example/v1/feedback', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'CF-Connecting-IP': address },
    body: JSON.stringify(body),
  });
}

function worker(store, calls, status = 201) {
  let minute = 0;
  return createWorker({
    now: () => new Date(Date.parse('2026-10-24T01:00:00Z') + minute++ * 60_000),
    securityStoreFactory: () => store,
    githubTokenProvider: async (_env, permissions) => {
      calls.permissions = permissions;
      return 'issues-token';
    },
    fetch: async (url, init) => {
      calls.requests.push({ url, init });
      return new Response(JSON.stringify({ number: 42, html_url: 'https://github.com/winter1l/DimaNow/issues/42' }), { status });
    },
  });
}

test('an in-app report becomes one issue with an issues-only token', async () => {
  const calls = { requests: [] };
  const response = await worker(new MemoryStore(), calls).fetch(feedbackRequest({
    category: 'bug', message: '시간표 가져오기에서 멈춰요\n@someone 확인 부탁', appVersion: '2.1', androidSdk: 36,
  }), env);

  assert.equal(response.status, 201);
  assert.deepEqual(await response.json(), { message: '제보를 올렸습니다.', number: 42, url: 'https://github.com/winter1l/DimaNow/issues/42' });
  assert.deepEqual(calls.permissions, { issues: 'write' });
  assert.equal(calls.requests.length, 1);
  assert.equal(calls.requests[0].url, FEEDBACK_ISSUES_URL);
  const sent = JSON.parse(calls.requests[0].init.body);
  assert.equal(sent.title, '[앱 오류] 시간표 가져오기에서 멈춰요');
  assert.match(sent.body, /```text\n시간표 가져오기에서 멈춰요\n@someone 확인 부탁\n```/);
  assert.match(sent.body, /앱 버전: 2\.1/);
  assert.match(sent.body, /Android SDK: 36/);
});

test('the same address waits ten minutes and the daily budget caps reports', async () => {
  const store = new MemoryStore();
  const calls = { requests: [] };
  const app = worker(store, calls);
  const body = { category: 'question', message: '셔틀 시간이 달라요' };
  assert.equal((await app.fetch(feedbackRequest(body), env)).status, 201);
  assert.equal((await app.fetch(feedbackRequest(body), env)).status, 429);
  assert.equal((await app.fetch(feedbackRequest(body, '198.51.100.9'), env)).status, 201);
  const overBudget = await app.fetch(feedbackRequest(body, '192.0.2.44'), env);
  assert.equal(overBudget.status, 429);
  assert.equal(calls.requests.length, 2);
});

test('a refused issue gives the address and the budget back', async () => {
  const store = new MemoryStore();
  const calls = { requests: [] };
  const response = await worker(store, calls, 403).fetch(feedbackRequest({ category: 'idea', message: '다크 모드 위젯이 있으면 좋겠어요' }), env);
  assert.equal(response.status, 502);
  assert.equal(store.leases.size, 0);
  assert.equal([...store.budgets.values()].reduce((sum, used) => sum + used, 0), 0);
});

test('bodies that are not a report never reach GitHub', async () => {
  const calls = { requests: [] };
  const app = worker(new MemoryStore(), calls);
  for (const body of [
    { category: 'spam', message: '다섯 글자 넘음' },
    { category: 'bug', message: '짧음' },
    { category: 'bug', message: 'x'.repeat(2001) },
    { category: 'bug' },
  ]) {
    assert.equal((await app.fetch(feedbackRequest(body), env)).status, 400, JSON.stringify(body).slice(0, 40));
  }
  const notJson = await app.fetch(new Request('https://upload.example/v1/feedback', {
    method: 'POST', headers: { 'Content-Type': 'text/plain', 'CF-Connecting-IP': '203.0.113.7' }, body: 'hi',
  }), env);
  assert.equal(notJson.status, 415);
  assert.equal(calls.requests.length, 0);
});

test('the issue keeps the student text inert and names no one', () => {
  const issue = feedbackIssue(feedbackReport({ category: 'bug', message: '@winter1l ```끝``` ![x](https://example.com/a.png)\u0007', appVersion: 'bad version!', androidSdk: 7 }));
  assert.equal(issue.title.includes('@winter1l'), false);
  assert.match(issue.body, /````text\n@winter1l ```끝``` !\[x\]\(https:\/\/example\.com\/a\.png\)\n````/);
  assert.match(issue.body, /앱 버전: 보내지 않음/);
  assert.match(issue.body, /Android SDK: 보내지 않음/);
});

test('an 오류 report keeps its screenshots privately and links them, with the log folded', async () => {
  const calls = { requests: [] };
  const bucket = new MemoryBucket();
  const response = await worker(new MemoryStore(), calls).fetch(feedbackRequest({
    category: 'bug',
    message: '식단 화면이 비어 있어요',
    log: '10-24 00:44:49.123 W/DimaNow: <token>',
    images: [{ type: 'image/png', data: pngBase64() }, { type: 'image/png', data: pngBase64(800, 400) }],
  }), { ...env, DORM_PHOTOS: bucket });

  assert.equal(response.status, 201);
  const keys = [...bucket.objects.keys()];
  assert.equal(keys.length, 2);
  assert.ok(keys.every((key) => /^feedback\/[A-Za-z0-9_-]+\.png$/.test(key)));
  const sent = JSON.parse(calls.requests[0].init.body);
  for (const key of keys) assert.match(sent.body, new RegExp(`https://upload\\.example/v1/feedback-images/${key.slice('feedback/'.length).replace('.', '\\.')}`));
  assert.match(sent.body, /<details><summary>앱 로그/);

  const served = await worker(new MemoryStore(), { requests: [] }).fetch(
    new Request(`https://upload.example/v1/feedback-images/${keys[0].slice('feedback/'.length)}`), { ...env, DORM_PHOTOS: bucket });
  assert.equal(served.status, 200);
  assert.equal(served.headers.get('Content-Type'), 'image/png');
  const missing = await worker(new MemoryStore(), { requests: [] }).fetch(
    new Request('https://upload.example/v1/feedback-images/../submissions/x.png'), { ...env, DORM_PHOTOS: bucket });
  assert.equal(missing.status, 404);
});

test('a refused issue removes the stored screenshots', async () => {
  const bucket = new MemoryBucket();
  const response = await worker(new MemoryStore(), { requests: [] }, 500).fetch(feedbackRequest({
    category: 'bug', message: '화면이 멈춰요', images: [{ type: 'image/png', data: pngBase64() }],
  }), { ...env, DORM_PHOTOS: bucket });
  assert.equal(response.status, 502);
  assert.equal(bucket.objects.size, 0);
});

test('screenshots and logs belong to 오류 reports only and must be real images', () => {
  assert.equal(feedbackReport({ category: 'question', message: '질문이 있어요', log: 'x' }), null);
  assert.equal(feedbackReport({ category: 'idea', message: '제안이 있어요', images: [{ type: 'image/png', data: pngBase64() }] }), null);
  const helpers = {
    hasExpectedImageSignature: (bytes) => bytes[0] === 0x89,
    imageDimensions: (bytes) => ({ width: new DataView(bytes.buffer).getUint32(16), height: new DataView(bytes.buffer).getUint32(20) }),
  };
  assert.equal(feedbackReport({ category: 'bug', message: '오류가 있어요', images: [{ type: 'image/png', data: Buffer.from('hello').toString('base64') }] }, helpers), null);
  assert.equal(feedbackReport({ category: 'bug', message: '오류가 있어요', images: Array(4).fill({ type: 'image/png', data: pngBase64() }) }, helpers), null);
  assert.equal(feedbackReport({ category: 'bug', message: '오류가 있어요', images: [{ type: 'image/png', data: pngBase64(10, 10) }] }, helpers), null);
  assert.equal(feedbackReport({ category: 'bug', message: '오류가 있어요', images: [{ type: 'image/png', data: pngBase64() }] }, helpers).images.length, 1);
});

test('a chunked report without Content-Length is cut off at the size limit', async () => {
  const store = new MemoryStore();
  const calls = {};
  const chunk = new Uint8Array(1024 * 1024).fill(0x61);
  let sent = 0;
  const body = new ReadableStream({
    pull(controller) {
      if (sent >= 12) return controller.close();
      sent += 1;
      controller.enqueue(chunk);
    },
  });
  const request = new Request('https://upload.example/v1/feedback', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'CF-Connecting-IP': '203.0.113.9' },
    body,
    duplex: 'half',
  });
  const response = await worker(store, calls).fetch(request, env, {});
  assert.equal(response.status, 413);
  assert.ok(sent < 12, 'the stream is not read to the end');
});
