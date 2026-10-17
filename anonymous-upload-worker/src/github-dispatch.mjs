// D-100: the Worker wakes main's publish workflow with workflow_dispatch, so the GitHub App needs only
// Actions: write (no Contents write). The target is one of the workflow's own choice inputs; no client value
// is ever forwarded, and the fixed URL names this repository and workflow only.
export const PUBLISH_WORKFLOW_DISPATCH_URL =
  'https://api.github.com/repos/winter1l/DimaNow/actions/workflows/publish-data.yml/dispatches';

export const DISPATCH_TARGETS = new Set([
  'dorm',
  'meal-watch',
  'report-wrong-menu',
  'report-not-dormitory',
  'report-offensive',
  'report-other',
]);

export async function dispatchPublishWorkflow(fetchImpl, token, target, userAgent) {
  if (!DISPATCH_TARGETS.has(target)) throw new Error('Unsupported publish workflow target');
  if (typeof token !== 'string' || !token) throw new Error('GitHub installation token unavailable');
  const response = await fetchImpl(PUBLISH_WORKFLOW_DISPATCH_URL, {
    method: 'POST',
    redirect: 'manual',
    signal: AbortSignal.timeout(10000),
    headers: {
      Accept: 'application/vnd.github+json',
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      'User-Agent': userAgent,
      'X-GitHub-Api-Version': '2022-11-28',
    },
    body: JSON.stringify({ ref: 'main', inputs: { dataset: target } }),
  });
  if (response.status !== 204) {
    await response.body?.cancel();
    throw new Error(`Publish workflow dispatch failed: ${response.status}`);
  }
}
