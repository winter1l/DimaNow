import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import test from 'node:test';
import { createStudentMealPublicationWatch } from '../src/meal-publication-watch.mjs';

const ROOT = 'https://winter1l.github.io/DimaNow/data/v1';
const ENV = { GITHUB_OWNER: 'winter1l', GITHUB_REPOSITORY: 'DimaNow' };
function meal(weekStart = '2026-09-07') {
  const monday = Date.parse(`${weekStart}T00:00:00Z`);
  const date = (offset) => new Date(monday + offset * 86400000).toISOString().slice(0, 10);
  return { weekStart, weekEnd: date(6), days: Array.from({ length: 5 }, (_, i) => ({
    date: date(i), menuLines: ['쌀밥', '국'], hours: '11:00 ~ 14:00',
  })) };
}
function fixture(options = {}) {
  const body = JSON.stringify(options.payload ?? meal());
  const sha256 = createHash('sha256').update(body).digest('hex');
  const descriptor = { revision: 8, state: 'NEEDS_REVIEW', lastAttemptAt: '2026-09-14T01:00:13Z',
    url: `meal/${sha256}.json`, sha256, ...options.descriptor };
  const manifest = { datasets: { meal: descriptor }, ...options.manifest };
  const calls = [];
  let tokens = 0;
  const watch = createStudentMealPublicationWatch({
    now: () => new Date(options.now ?? '2026-09-14T01:37:00Z'),
    githubTokenProvider: async (env) => { assert.equal(env, ENV); tokens++; return 'test-installation-token'; },
    fetch: async (url, init = {}) => {
      // Match workerd: redirect:error throws before any HTTP request is made.
      if (init.redirect === 'error') throw new TypeError('Unsupported Worker redirect mode');
      calls.push({ url, init });
      if (url === `${ROOT}/manifest.json`) return options.manifestResponse?.()
        ?? Response.json(manifest);
      if (url === `${ROOT}/${descriptor.url}`) return options.mealResponse?.()
        ?? new Response(body);
      if (url === 'https://api.github.com/repos/winter1l/DimaNow/dispatches') {
        return new Response(null, { status: options.dispatchStatus ?? 204 });
      }
      throw new Error(`Unexpected URL ${url}`);
    },
  });
  return { watch: () => watch(ENV), calls, tokenCount: () => tokens };
}

test('10:00 collection failure and missing week dispatches authenticated collection at 10:37 KST', async () => {
  const f = fixture();
  assert.deepEqual(await f.watch(), { status: 'dispatched', weekStart: '2026-09-14' });
  const dispatch = f.calls.at(-1);
  assert.equal(dispatch.init.method, 'POST');
  assert.equal(dispatch.init.headers.Authorization, 'Bearer test-installation-token');
  assert.deepEqual(JSON.parse(dispatch.init.body), {
    event_type: 'student-meal-publication-watch', client_payload: { week_start: '2026-09-14' },
  });
  assert.equal(f.tokenCount(), 1);
});

test('a verified complete current week skips OCR dispatch even after a later failed correction', async () => {
  const f = fixture({ payload: meal('2026-09-14') });
  assert.deepEqual(await f.watch(), { status: 'skipped', reason: 'current-week-complete' });
  assert.equal(f.tokenCount(), 0);
});

test('the published Chuseok week is complete with single-line holiday rows', async () => {
  const payload = meal('2026-09-21');
  payload.days[3].menuLines = ['추석 공휴일'];
  payload.days[4].menuLines = ['추석'];
  const f = fixture({ payload, now: '2026-09-21T04:37:00Z' });
  assert.deepEqual(await f.watch(), { status: 'skipped', reason: 'current-week-complete' });
  assert.equal(f.tokenCount(), 0);
});

for (const label of ['추석\u00a0공휴일', '추석\u3000연휴', '\ufeff추석\ufeff']) {
  test(`holiday whitespace matches the Kotlin contract (${JSON.stringify(label)})`, async () => {
    const payload = meal('2026-09-14');
    payload.days[3].menuLines = [label];
    assert.equal((await fixture({ payload }).watch()).reason, 'current-week-complete');
  });
}

for (const label of ['밥', '추석특식', '휴무 여부 미정', '추석\u00a0특식', '휴무\u3000여부 미정', ' ']) {
  test(`a single incomplete menu line (${label}) does not suppress collection`, async () => {
    const payload = meal('2026-09-14');
    payload.days[3].menuLines = [label];
    assert.equal((await fixture({ payload }).watch()).status, 'dispatched');
  });
}

test('recent collection attempt suppresses duplicate work during the Monday morning window', async () => {
  const f = fixture({ now: '2026-09-14T01:24:00Z' });
  assert.deepEqual(await f.watch(), { status: 'skipped', reason: 'recent-attempt' });
  assert.equal(f.tokenCount(), 0);
});

test('outside Monday morning a missing week uses a 110-minute quiet period', async () => {
  const f = fixture({ now: '2026-09-14T05:37:00Z', descriptor: { lastAttemptAt: '2026-09-14T04:00:00Z' } });
  assert.deepEqual(await f.watch(), { status: 'skipped', reason: 'recent-attempt' });
  const due = fixture({ now: '2026-09-14T05:50:00Z', descriptor: { lastAttemptAt: '2026-09-14T04:00:00Z' } });
  assert.equal((await due.watch()).status, 'dispatched');
});

test('KST Monday begins on UTC Sunday and invalidates the previous week', async () => {
  const f = fixture({ now: '2026-09-13T15:07:00Z', descriptor: { lastAttemptAt: '2026-09-13T12:00:00Z' } });
  assert.deepEqual(await f.watch(), { status: 'dispatched', weekStart: '2026-09-14' });
});

test('incomplete current week continues collecting', async () => {
  const payload = meal('2026-09-14');
  payload.days.pop();
  assert.equal((await fixture({ payload }).watch()).status, 'dispatched');
});

for (const [name, options] of [
  ['invalid JSON', { manifestResponse: () => new Response('{broken') }],
  ['unsupported manifest schema', { manifest: { schemaVersion: 2 } }],
  ['null manifest schema', { manifest: { schemaVersion: null } }],
  ['invalid attempt time', { descriptor: { lastAttemptAt: 'not-a-date' } }],
  ['future attempt time', { descriptor: { lastAttemptAt: '2027-01-01T00:00:00Z' } }],
  ['external meal URL', { descriptor: { url: 'https://evil.invalid/meal.json' } }],
  ['mismatched content hash', { mealResponse: () => new Response('{}') }],
  ['unsupported meal schema', { payload: { ...meal(), schemaVersion: 2 } }],
  ['duplicate meal dates', { payload: { ...meal(), days: Array(5).fill(meal().days[0]) } }],
  ['declared oversized manifest', { manifestResponse: () => new Response('{}', { headers: { 'Content-Length': '1000000' } }) }],
  ['streamed oversized meal', { mealResponse: () => new Response(new ReadableStream({
    start(controller) { controller.enqueue(new Uint8Array(1100000)); controller.close(); },
  })) }],
  ['redirected manifest', { manifestResponse: () => new Response(null, { status: 302, headers: { Location: 'https://evil.invalid/' } }) }],
]) {
  test(`${name} fails closed before requesting a GitHub token`, async () => {
    const f = fixture(options);
    await assert.rejects(f.watch());
    assert.equal(f.tokenCount(), 0);
  });
}

test('GitHub rejection is observable as failure rather than a successful dispatch', async () => {
  await assert.rejects(fixture({ dispatchStatus: 403 }).watch(), /403/);
});

test('GitHub redirects are rejected without following the authenticated request', async () => {
  const f = fixture({ dispatchStatus: 302 });
  await assert.rejects(f.watch(), /302/);
  assert.equal(f.calls.at(-1).init.redirect, 'manual');
});

test('an unpublished initial descriptor can request the first meal collection', async () => {
  const f = fixture({ descriptor: { revision: 0, state: 'WAITING', url: '', sha256: '' } });
  assert.equal((await f.watch()).status, 'dispatched');
  assert.equal(f.calls.filter((call) => call.url.startsWith(`${ROOT}/meal/`)).length, 0);
});

test('Sunday in KST retains the current week rather than preparing next Monday early', async () => {
  const f = fixture({ now: '2026-09-13T14:37:00Z', descriptor: { lastAttemptAt: '2026-09-13T12:00:00Z' } });
  assert.deepEqual(await f.watch(), { status: 'skipped', reason: 'current-week-complete' });
});

test('an empty menu line cannot suppress a needed collection', async () => {
  const payload = meal('2026-09-14');
  payload.days[2].menuLines = ['쌀밥', ' '];
  assert.equal((await fixture({ payload }).watch()).status, 'dispatched');
});

test('public fetches bypass cache and prohibit redirects', async () => {
  const f = fixture({ payload: meal('2026-09-14') });
  await f.watch();
  for (const { init } of f.calls) {
    assert.equal(init.redirect, 'manual');
    assert.equal(init.cache, 'no-store');
    assert.ok(init.signal instanceof AbortSignal);
  }
});
