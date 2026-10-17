import assert from 'node:assert/strict';
import test from 'node:test';
import { createWorker } from '../src/index.mjs';
import { STUDENT_MEAL_WATCH_CRONS } from '../src/meal-publication-watch.mjs';

test('scheduled Monday watch reaches authenticated collection without a user request', async () => {
  const pending = [];
  const dispatches = [];
  const leasePrunes = [];
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
    securityStoreFactory: () => ({
      prune: async () => { throw new Error('Morning watch must not start daily maintenance'); },
      pruneExpiredLeases: async (seconds) => leasePrunes.push(seconds),
    }),
  });
  worker.scheduled({ cron: STUDENT_MEAL_WATCH_CRONS[0], scheduledTime: Date.parse('2026-09-14T01:37:00Z') },
    { GITHUB_OWNER: 'winter1l', GITHUB_REPOSITORY: 'DimaNow' }, { waitUntil: (promise) => pending.push(promise) });
  await Promise.all(pending);
  assert.equal(dispatches.length, 1);
  assert.equal(dispatches[0].url, 'https://api.github.com/repos/winter1l/DimaNow/actions/workflows/publish-data.yml/dispatches');
  assert.deepEqual(JSON.parse(dispatches[0].init.body).inputs, { dataset: 'meal-watch' });
  assert.deepEqual(leasePrunes, [Date.parse('2026-09-14T01:37:00Z') / 1000]);
});

test('daily watch slot also preserves gateway maintenance', async () => {
  const pending = [];
  const pruned = [];
  const worker = createWorker({
    now: () => new Date('2026-09-14T17:07:00Z'),
    fetch: async () => Response.json({ datasets: { meal: {
      revision: 0, state: 'WAITING', url: '', sha256: '', lastAttemptAt: '2026-09-14T17:00:00Z',
    } } }),
    securityStoreFactory: () => ({ prune: async (seconds) => pruned.push(seconds) }),
  });
  worker.scheduled({ cron: STUDENT_MEAL_WATCH_CRONS[1], scheduledTime: Date.parse('2026-09-14T17:07:00Z') },
    {}, { waitUntil: (promise) => pending.push(promise) });
  await Promise.all(pending);
  assert.deepEqual(pruned, [Date.parse('2026-09-14T17:07:00Z') / 1000]);
});
