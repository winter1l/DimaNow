import assert from 'node:assert/strict';
import test from 'node:test';
import { createWorker } from '../src/index.mjs';

class FakeKv {
  constructor() { this.values = new Map(); }
  async get(key) { return this.values.get(key) ?? null; }
  async put(key, value) { this.values.set(key, value); }
  async delete(key) { this.values.delete(key); }
}

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
