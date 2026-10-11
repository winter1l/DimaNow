import assert from 'node:assert/strict';
import test from 'node:test';
import { createWorker, imageDimensions, rateLimitClientKey } from '../src/index.mjs';

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
    this.consumeCalls = (this.consumeCalls ?? 0) + 1;
    const dayUsed = this.budgets.get(budget.dayScope) ?? 0;
    const weekUsed = this.budgets.get(budget.weekScope) ?? 0;
    if (dayUsed >= budget.dayLimit || weekUsed >= budget.weekLimit) return false;
    this.budgets.set(budget.dayScope, dayUsed + 1);
    this.budgets.set(budget.weekScope, weekUsed + 1);
    this.lastBudgetExpiry = Math.max(budget.dayExpiresAt, budget.weekExpiresAt);
    return true;
  }
  async refundUploadBudgets(budget) {
    for (const scope of [budget.dayScope, budget.weekScope]) {
      const used = this.budgets.get(scope) ?? 0;
      if (used > 0) this.budgets.set(scope, used - 1);
    }
  }
  totalBudgetUsed() {
    return [...this.budgets.values()].reduce((sum, value) => sum + value, 0);
  }
  async pruneExpiredLeases(nowSeconds) {
    this.leasePrunedAt = nowSeconds;
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

const MIN_BYTES = 20 * 1024;

function padded(header, size) {
  const bytes = new Uint8Array(Math.max(size, header.length));
  bytes.set(header);
  return bytes;
}

/** JPEG: SOI, a COM segment filling to `size`, then SOF0 with the given dimensions. */
function jpegImage(width = 1200, height = 1600, size = MIN_BYTES + 1024) {
  const sof = [0xff, 0xc0, 0x00, 0x11, 0x08, height >> 8, height & 0xff, width >> 8, width & 0xff, 0x03,
    0x01, 0x22, 0x00, 0x02, 0x11, 0x01, 0x03, 0x11, 0x01];
  const commentLength = Math.max(2, Math.min(0xffff, size - 2 - 2 - sof.length - 4));
  const comment = [0xff, 0xfe, commentLength >> 8, commentLength & 0xff, ...new Array(commentLength - 2).fill(0x20)];
  return padded([0xff, 0xd8, ...comment, ...sof, 0xff, 0xda, 0x00, 0x02], size);
}

function pngImage(width = 1200, height = 1600, size = MIN_BYTES + 1024) {
  const u32 = (value) => [value >>> 24, (value >>> 16) & 0xff, (value >>> 8) & 0xff, value & 0xff];
  return padded([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, ...u32(13), 0x49, 0x48, 0x44, 0x52,
    ...u32(width), ...u32(height), 8, 2, 0, 0, 0, 0, 0, 0, 0], size);
}

function webpImage(kind, width = 1200, height = 1600, size = MIN_BYTES + 1024) {
  const ascii = (text) => [...text].map((character) => character.charCodeAt(0));
  const header = [...ascii('RIFF'), 0, 0, 0, 0, ...ascii('WEBP'), ...ascii(kind), 0, 0, 0, 0];
  if (kind === 'VP8 ') {
    header.push(0x10, 0x02, 0x00, 0x9d, 0x01, 0x2a, width & 0xff, (width >> 8) & 0x3f, height & 0xff, (height >> 8) & 0x3f);
  } else if (kind === 'VP8L') {
    const bits = ((width - 1) & 0x3fff) | (((height - 1) & 0x3fff) << 14);
    header.push(0x2f, bits & 0xff, (bits >>> 8) & 0xff, (bits >>> 16) & 0xff, (bits >>> 24) & 0xff);
  } else {
    const w = width - 1;
    const h = height - 1;
    header.push(0, 0, 0, 0, w & 0xff, (w >> 8) & 0xff, (w >> 16) & 0xff, h & 0xff, (h >> 8) & 0xff, (h >> 16) & 0xff);
  }
  return padded(header, size);
}

function imageRequest(address = '203.0.113.7', body = jpegImage(), contentType = 'image/jpeg', extension = 'jpg') {
  return new Request('https://upload.example/v1/dormitory-meals', {
    method: 'POST',
    headers: {
      'Content-Type': contentType,
      'X-Dima-Image-Extension': extension,
      'CF-Connecting-IP': address,
    },
    body,
    duplex: 'half',
  });
}

function uploadWorker(security, overrides = {}) {
  return createWorker({
    now: () => new Date('2026-09-02T01:15:00Z'),
    randomUUID: () => crypto.randomUUID(),
    securityStoreFactory: () => security,
    dormitoryPublicationProvider: async () => false,
    githubTokenProvider: async () => 'server-installation-token',
    fetch: async () => new Response('{}', { status: 201 }),
    ...overrides,
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
    body: jpegImage(),
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
    body: pngImage(),
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
  assert.equal(security.consumeCalls, undefined, 'a 409 must not consume the shared budget');
  assert.equal(security.totalBudgetUsed(), 0);
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

test('IPv6 clients share one lease per /64 while IPv4 keeps the full address', async () => {
  assert.equal(rateLimitClientKey('2001:db8:1234:5678::1'), 'v6:2001:db8:1234:5678::/64');
  assert.equal(rateLimitClientKey('2001:0DB8:1234:5678:ffff:eeee:dddd:cccc'), 'v6:2001:db8:1234:5678::/64');
  assert.equal(rateLimitClientKey('2001:db8::'), 'v6:2001:db8:0:0::/64');
  assert.equal(rateLimitClientKey('::ffff:203.0.113.7'), 'v4:203.0.113.7');
  assert.equal(rateLimitClientKey('203.0.113.7'), 'v4:203.0.113.7');
  assert.notEqual(rateLimitClientKey('203.0.113.7'), rateLimitClientKey('203.0.113.8'));
  for (const invalid of ['', 'not-an-ip', '1.2.3', '256.1.1.1', '2001:db8::1::2', '1:2:3:4:5:6:7:8:9', null]) {
    assert.equal(rateLimitClientKey(invalid), null, String(invalid));
  }

  const security = new FakeSecurityStore();
  const worker = uploadWorker(security);
  assert.equal((await worker.fetch(imageRequest('2001:db8:1234:5678::1'), uploadEnv())).status, 202);
  assert.equal((await worker.fetch(imageRequest('2001:db8:1234:5678:abcd::99'), uploadEnv())).status, 429);
  assert.equal((await worker.fetch(imageRequest('2001:db8:1234:5679::1'), uploadEnv())).status, 202);
  assert.equal(security.leases.size, 2);
  assert.equal((await worker.fetch(imageRequest('garbage'), uploadEnv())).status, 400);
});

test('wrong content-type, extension mismatch and tiny declared bodies never reach D1', async () => {
  const worker = createWorker({
    securityStoreFactory: () => { throw new Error('cheap validation must not reach D1'); },
  });
  assert.equal((await worker.fetch(imageRequest('203.0.113.7', jpegImage(), 'image/gif', 'gif'), uploadEnv())).status, 415);
  assert.equal((await worker.fetch(imageRequest('203.0.113.7', jpegImage(), 'image/jpeg', 'png'), uploadEnv())).status, 415);
  const tiny = imageRequest('203.0.113.7', new Uint8Array(1000));
  tiny.headers.set('Content-Length', '1000');
  assert.equal((await worker.fetch(tiny, uploadEnv())).status, 400);
  const huge = imageRequest('203.0.113.7', new Uint8Array(10));
  huge.headers.set('Content-Length', String(15 * 1024 * 1024 + 1));
  assert.equal((await worker.fetch(huge, uploadEnv())).status, 413);
});

test('a rejected image body releases nothing from the shared budget', async () => {
  for (const [body, type, extension, status] of [
    [new Uint8Array([0xff, 0xd8, 0xff, 0xd9]), 'image/jpeg', 'jpg', 400],
    [jpegImage(100, 100), 'image/jpeg', 'jpg', 400],
    [pngImage(13000, 800), 'image/png', 'png', 400],
    [padded([0xff, 0xd8, 0xff, 0xda, 0x00, 0x02], MIN_BYTES + 10), 'image/jpeg', 'jpg', 415],
    [padded([0x89, 0x50, 0x4e, 0x47], MIN_BYTES + 10), 'image/png', 'png', 415],
  ]) {
    const security = new FakeSecurityStore();
    let tokenCalls = 0;
    const worker = uploadWorker(security, {
      githubTokenProvider: async () => { tokenCalls += 1; return 'server-installation-token'; },
      // Exercise the post-read checks even when Content-Length passes the cheap gate.
      bodyReader: async () => body,
    });
    const response = await worker.fetch(imageRequest('203.0.113.7', jpegImage(), type, extension), uploadEnv());
    assert.equal(response.status, status);
    assert.equal(security.consumeCalls, undefined);
    assert.equal(security.totalBudgetUsed(), 0);
    assert.equal(tokenCalls, 0);
  }
});

test('a failed GitHub write refunds the shared budget atomically', async () => {
  const security = new FakeSecurityStore();
  const worker = uploadWorker(security, {
    fetch: async () => new Response('{}', { status: 500 }),
  });
  const env = uploadEnv({ UPLOAD_DAILY_LIMIT: '1', UPLOAD_WEEKLY_LIMIT: '1' });

  assert.equal((await worker.fetch(imageRequest('203.0.113.7'), env)).status, 502);
  assert.equal(security.consumeCalls, 1);
  assert.equal(security.totalBudgetUsed(), 0);
  assert.equal(security.leases.size, 0);

  const healthy = uploadWorker(security);
  assert.equal((await healthy.fetch(imageRequest('203.0.113.8'), env)).status, 202);
  assert.equal(security.totalBudgetUsed(), 2);
});

test('a token failure refunds the budget and an exhausted budget is reported after the body check', async () => {
  const security = new FakeSecurityStore();
  const failing = uploadWorker(security, {
    githubTokenProvider: async () => { throw new Error('token outage'); },
  });
  const env = uploadEnv({ UPLOAD_DAILY_LIMIT: '1', UPLOAD_WEEKLY_LIMIT: '1' });
  assert.equal((await failing.fetch(imageRequest('203.0.113.7'), env)).status, 502);
  assert.equal(security.totalBudgetUsed(), 0);

  const healthy = uploadWorker(security);
  assert.equal((await healthy.fetch(imageRequest('203.0.113.8'), env)).status, 202);
  assert.equal((await healthy.fetch(imageRequest('203.0.113.9'), env)).status, 429);
});

test('image dimensions are parsed from JPEG, PNG and every WebP variant', () => {
  assert.deepEqual(imageDimensions(jpegImage(1200, 1600), 'image/jpeg'), { width: 1200, height: 1600 });
  assert.deepEqual(imageDimensions(pngImage(800, 600), 'image/png'), { width: 800, height: 600 });
  for (const kind of ['VP8 ', 'VP8L', 'VP8X']) {
    assert.deepEqual(imageDimensions(webpImage(kind, 1000, 700), 'image/webp'), { width: 1000, height: 700 }, kind);
  }
  assert.equal(imageDimensions(padded([0xff, 0xd8, 0xff, 0xd9], 64), 'image/jpeg'), null);
  assert.equal(imageDimensions(webpImage('ALPH'), 'image/webp'), null);
});

test('dimension bounds accept ordinary photos and reject tiny or oversized frames', async () => {
  const cases = [
    [webpImage('VP8 ', 1200, 1600), 'image/webp', 'webp', 202],
    [webpImage('VP8L', 320, 320), 'image/webp', 'webp', 202],
    [webpImage('VP8X', 12000, 900), 'image/webp', 'webp', 202],
    [webpImage('VP8X', 12001, 900), 'image/webp', 'webp', 400],
    [pngImage(319, 2000), 'image/png', 'png', 400],
    [jpegImage(1000, 0), 'image/jpeg', 'jpg', 400],
    [jpegImage(4000, 3000, 20 * 1024 - 1), 'image/jpeg', 'jpg', 400],
  ];
  let address = 0;
  for (const [body, type, extension, status] of cases) {
    const security = new FakeSecurityStore();
    const worker = uploadWorker(security);
    const response = await worker.fetch(imageRequest(`198.51.100.${++address}`, body, type, extension), uploadEnv());
    assert.equal(response.status, status, `${type} case ${address}`);
    assert.equal(security.totalBudgetUsed(), status === 202 ? 2 : 0);
  }
});

test('the installation token request is scoped to one repository and contents:write', async () => {
  const keyPair = await crypto.subtle.generateKey(
    { name: 'RSASSA-PKCS1-v1_5', modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' },
    true,
    ['sign', 'verify'],
  );
  const pkcs8 = Buffer.from(await crypto.subtle.exportKey('pkcs8', keyPair.privateKey)).toString('base64');
  const pem = `-----BEGIN PRIVATE KEY-----\n${pkcs8.match(/.{1,64}/g).join('\n')}\n-----END PRIVATE KEY-----`;
  const calls = [];
  const security = new FakeSecurityStore();
  const worker = createWorker({
    now: () => new Date('2026-09-02T01:15:00Z'),
    securityStoreFactory: () => security,
    dormitoryPublicationProvider: async () => false,
    fetch: async (url, init) => {
      calls.push({ url: String(url), init });
      if (String(url).endsWith('/access_tokens')) {
        return Response.json({ token: 'scoped-installation-token' }, { status: 201 });
      }
      return new Response('{}', { status: 201 });
    },
  });

  const response = await worker.fetch(imageRequest(), uploadEnv({
    GITHUB_APP_ID: '4774955',
    GITHUB_INSTALLATION_ID: '157828401',
    GITHUB_APP_PRIVATE_KEY: pem,
    GITHUB_OWNER: 'winter1l',
    GITHUB_REPOSITORY: 'DimaNow',
  }));

  assert.equal(response.status, 202);
  assert.equal(calls[0].url, 'https://api.github.com/app/installations/157828401/access_tokens');
  assert.equal(calls[0].init.method, 'POST');
  assert.equal(calls[0].init.headers['Content-Type'], 'application/json');
  assert.deepEqual(JSON.parse(calls[0].init.body), {
    repositories: ['DimaNow'],
    permissions: { contents: 'write' },
  });
  assert.equal(calls[1].init.headers.Authorization, 'Bearer scoped-installation-token');
});

test('non-maintenance watch slots still drop expired address leases', async () => {
  const security = new FakeSecurityStore();
  const pending = [];
  const worker = createWorker({
    now: () => new Date('2026-09-15T03:07:00Z'),
    securityStoreFactory: () => security,
    githubTokenProvider: async () => 'test-token',
    fetch: async () => Response.json({ datasets: { meal: {
      revision: 0, state: 'WAITING', url: '', sha256: '', lastAttemptAt: '2026-09-15T03:00:00Z',
    } } }),
  });
  worker.scheduled({ cron: '7 1-23/2 * * SUN,TUE,WED,THU,FRI,SAT', scheduledTime: Date.parse('2026-09-15T03:07:00Z') },
    uploadEnv(), { waitUntil: (promise) => pending.push(promise.catch(() => {})) });
  await Promise.all(pending);
  assert.equal(security.leasePrunedAt, Date.parse('2026-09-15T03:07:00Z') / 1000);
  assert.equal(security.prunedAt, undefined);
});

test('the D1 store deletes a bounded set of expired leases in the same batch as the claim', async () => {
  const statements = [];
  const database = {
    prepare(sql) {
      const statement = { sql, bindings: [], bind(...values) { this.bindings = values; return this; },
        async run() { statements.push(this); return { meta: { changes: 1 } }; } };
      return statement;
    },
    async batch(batch) {
      statements.push(...batch);
      return batch.map(() => ({ meta: { changes: 1 } }));
    },
  };
  const worker = createWorker({
    now: () => new Date('2026-09-02T01:15:00Z'),
    dormitoryPublicationProvider: async () => true,
  });
  const response = await worker.fetch(imageRequest(), uploadEnv({ SHUTTLE_REPORTS: database }));
  assert.equal(response.status, 409);
  assert.equal(statements.length, 2);
  assert.match(statements[0].sql, /DELETE FROM gateway_leases[\s\S]*expires_at <= \?[\s\S]*LIMIT \?/);
  assert.deepEqual(statements[0].bindings, [Date.parse('2026-09-02T01:15:00Z') / 1000, 100]);
  assert.match(statements[1].sql, /INSERT INTO gateway_leases/);
  assert.equal(statements.some((statement) => /gateway_budgets/.test(statement.sql)), false);
});
