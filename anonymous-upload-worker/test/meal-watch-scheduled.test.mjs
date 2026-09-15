import assert from 'node:assert/strict';
import test from 'node:test';
import { createWorker } from '../src/index.mjs';
import { STUDENT_MEAL_WATCH_CRONS } from '../src/meal-publication-watch.mjs';

test('scheduled Monday watch reaches authenticated collection without a user request', async () => {
  const pending = [];
  const dispatches = [];
  const worker = createWorker({
    now: () => new Date('2026-09-14T01:37:00Z'),
    githubTokenProvider: async () => 'test-token',
    fetch: async (url, init) => {
      if (url.endsWith('/manifest.json')) return Response.json({ datasets: { meal: {
        revision: 0, state: 'WAITING', url: '', sha256: '', lastAttemptAt: '2026-09-14T01:00:13Z',
      } } });
      dispatches.push({ url, init });
      return new Response(null, { status: 204 });
    },
    reportStoreFactory: () => { throw new Error('Morning watch must not start daily maintenance'); },
  });
  worker.scheduled({ cron: STUDENT_MEAL_WATCH_CRONS[0], scheduledTime: Date.parse('2026-09-14T01:37:00Z') },
    { GITHUB_OWNER: 'winter1l', GITHUB_REPOSITORY: 'DimaNow' }, { waitUntil: (promise) => pending.push(promise) });
  await Promise.all(pending);
  assert.equal(dispatches.length, 1);
  assert.equal(dispatches[0].url, 'https://api.github.com/repos/winter1l/DimaNow/dispatches');
  assert.equal(JSON.parse(dispatches[0].init.body).event_type, 'student-meal-publication-watch');
});

test('daily watch slot also preserves gateway maintenance', async () => {
  const pending = [];
  const pruned = [];
  const worker = createWorker({
    now: () => new Date('2026-09-14T17:07:00Z'),
    fetch: async () => Response.json({ datasets: { meal: {
      revision: 0, state: 'WAITING', url: '', sha256: '', lastAttemptAt: '2026-09-14T17:00:00Z',
    } } }),
    reportStoreFactory: () => ({ prune: async (day) => pruned.push(day) }),
    securityStoreFactory: () => ({ prune: async (seconds) => pruned.push(seconds) }),
  });
  worker.scheduled({ cron: STUDENT_MEAL_WATCH_CRONS[1], scheduledTime: Date.parse('2026-09-14T17:07:00Z') },
    {}, { waitUntil: (promise) => pending.push(promise) });
  await Promise.all(pending);
  assert.deepEqual(pruned, ['2026-09-08', Date.parse('2026-09-14T17:07:00Z') / 1000]);
});
