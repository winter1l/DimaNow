const MAX_IMAGE_BYTES = 15 * 1024 * 1024;
const RATE_LIMIT_SECONDS = 10 * 60;
const IMAGE_EXTENSIONS = new Map([
  ['image/jpeg', 'jpg'],
  ['image/png', 'png'],
  ['image/webp', 'webp'],
]);

export function createWorker(dependencies = {}) {
  const fetchImpl = dependencies.fetch ?? fetch;
  const now = dependencies.now ?? (() => new Date());
  const randomUUID = dependencies.randomUUID ?? (() => crypto.randomUUID());
  const githubTokenProvider = dependencies.githubTokenProvider
    ?? ((env) => createGitHubInstallationToken(env, fetchImpl, now));

  return {
    async fetch(request, env) {
      const url = new URL(request.url);
      if (url.pathname !== '/v1/dormitory-meals') {
        return jsonResponse(404, '요청한 경로가 없습니다.');
      }
      if (request.method !== 'POST') {
        return jsonResponse(405, 'POST 요청만 지원합니다.', { Allow: 'POST' });
      }

      const mimeType = request.headers.get('Content-Type')?.split(';', 1)[0]?.trim().toLowerCase();
      const expectedExtension = IMAGE_EXTENSIONS.get(mimeType);
      const requestedExtension = request.headers.get('X-Dima-Image-Extension')?.trim().toLowerCase();
      if (!expectedExtension || requestedExtension !== expectedExtension) {
        return jsonResponse(415, '지원하지 않는 식단 이미지 형식입니다.');
      }

      const contentLength = Number(request.headers.get('Content-Length'));
      if (Number.isFinite(contentLength) && contentLength > MAX_IMAGE_BYTES) {
        return jsonResponse(413, '식단 이미지는 15MB 이하여야 합니다.');
      }

      const image = new Uint8Array(await request.arrayBuffer());
      if (image.byteLength === 0 || image.byteLength > MAX_IMAGE_BYTES) {
        return jsonResponse(image.byteLength === 0 ? 400 : 413, image.byteLength === 0
          ? '식단 사진이 비어 있습니다.'
          : '식단 이미지는 15MB 이하여야 합니다.');
      }
      if (!hasExpectedImageSignature(image, mimeType)) {
        return jsonResponse(415, '사진 파일 형식을 확인해 주세요.');
      }

      const clientAddress = request.headers.get('CF-Connecting-IP')?.trim();
      if (!clientAddress) {
        return jsonResponse(400, '업로드 요청을 확인할 수 없습니다.');
      }
      if (!env.RATE_LIMIT) {
        return jsonResponse(503, '업로드 서비스가 준비되지 않았습니다.');
      }

      const rateLimitKey = `upload:${await sha256Hex(`${env.RATE_LIMIT_SALT ?? ''}:${clientAddress}`)}`;
      if (await env.RATE_LIMIT.get(rateLimitKey)) {
        return jsonResponse(429, '잠시 후 다시 시도해 주세요.', { 'Retry-After': String(RATE_LIMIT_SECONDS) });
      }
      await env.RATE_LIMIT.put(rateLimitKey, 'pending', { expirationTtl: RATE_LIMIT_SECONDS });

      const submissionId = randomUUID();
      const uploadedAt = now().toISOString();
      try {
        const token = await githubTokenProvider(env);
        const owner = env.GITHUB_OWNER ?? 'winter1l';
        const repository = env.GITHUB_REPOSITORY ?? 'DimaNow';
        const branch = env.GITHUB_SUBMISSION_BRANCH ?? 'dorm-submissions';
        const path = `dorm-submissions/${submissionId}.${expectedExtension}`;
        const response = await fetchImpl(
          `https://api.github.com/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repository)}/contents/${path}`,
          {
            method: 'PUT',
            headers: {
              Accept: 'application/vnd.github+json',
              Authorization: `Bearer ${token}`,
              'Content-Type': 'application/json',
              'User-Agent': 'DIMA-Now-Meal-Upload',
              'X-GitHub-Api-Version': '2022-11-28',
            },
            body: JSON.stringify({
              message: `dormitory meal submission ${submissionId}`,
              content: bytesToBase64(image),
              branch,
            }),
          },
        );
        if (!response.ok) {
          throw new Error(`GitHub content upload failed: ${response.status}`);
        }
        await env.RATE_LIMIT.put(rateLimitKey, submissionId, { expirationTtl: RATE_LIMIT_SECONDS });
        return new Response(JSON.stringify({ submissionId, uploadedAt }), {
          status: 202,
          headers: jsonHeaders(),
        });
      } catch (error) {
        await env.RATE_LIMIT.delete(rateLimitKey);
        console.error('Dormitory meal upload failed', error instanceof Error ? error.message : error);
        return jsonResponse(502, '사진을 올리지 못했습니다. 잠시 후 다시 시도해 주세요.');
      }
    },
  };
}

async function createGitHubInstallationToken(env, fetchImpl, now) {
  const appId = required(env.GITHUB_APP_ID, 'GITHUB_APP_ID');
  const installationId = required(env.GITHUB_INSTALLATION_ID, 'GITHUB_INSTALLATION_ID');
  const privateKey = required(env.GITHUB_APP_PRIVATE_KEY, 'GITHUB_APP_PRIVATE_KEY');
  const nowSeconds = Math.floor(now().getTime() / 1000);
  const header = base64Url(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
  const payload = base64Url(JSON.stringify({
    iat: nowSeconds - 60,
    exp: nowSeconds + 9 * 60,
    iss: appId,
  }));
  const signingInput = `${header}.${payload}`;
  const key = await crypto.subtle.importKey(
    'pkcs8',
    pemToArrayBuffer(privateKey),
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['sign'],
  );
  const signature = await crypto.subtle.sign(
    'RSASSA-PKCS1-v1_5',
    key,
    new TextEncoder().encode(signingInput),
  );
  const jwt = `${signingInput}.${base64UrlBytes(new Uint8Array(signature))}`;
  const response = await fetchImpl(`https://api.github.com/app/installations/${installationId}/access_tokens`, {
    method: 'POST',
    headers: {
      Accept: 'application/vnd.github+json',
      Authorization: `Bearer ${jwt}`,
      'User-Agent': 'DIMA-Now-Meal-Upload',
      'X-GitHub-Api-Version': '2022-11-28',
    },
  });
  if (!response.ok) {
    throw new Error(`GitHub installation token failed: ${response.status}`);
  }
  const result = await response.json();
  return required(result.token, 'GitHub installation token');
}

function hasExpectedImageSignature(bytes, mimeType) {
  if (mimeType === 'image/jpeg') {
    return bytes.length >= 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff;
  }
  if (mimeType === 'image/png') {
    const png = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];
    return bytes.length >= png.length && png.every((value, index) => bytes[index] === value);
  }
  if (mimeType === 'image/webp') {
    return bytes.length >= 12
      && String.fromCharCode(...bytes.slice(0, 4)) === 'RIFF'
      && String.fromCharCode(...bytes.slice(8, 12)) === 'WEBP';
  }
  return false;
}

async function sha256Hex(value) {
  const digest = new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value)));
  return Array.from(digest, (byte) => byte.toString(16).padStart(2, '0')).join('');
}

function pemToArrayBuffer(pem) {
  const base64 = pem
    .replace(/-----BEGIN PRIVATE KEY-----/g, '')
    .replace(/-----END PRIVATE KEY-----/g, '')
    .replace(/\s/g, '');
  if (!base64) throw new Error('GITHUB_APP_PRIVATE_KEY must be a PKCS#8 PEM key');
  const bytes = Uint8Array.from(atob(base64), (character) => character.charCodeAt(0));
  return bytes.buffer;
}

function bytesToBase64(bytes) {
  let binary = '';
  for (let offset = 0; offset < bytes.length; offset += 0x8000) {
    binary += String.fromCharCode(...bytes.subarray(offset, offset + 0x8000));
  }
  return btoa(binary);
}

function base64Url(value) {
  return base64UrlBytes(new TextEncoder().encode(value));
}

function base64UrlBytes(bytes) {
  return bytesToBase64(bytes).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
}

function required(value, name) {
  if (typeof value !== 'string' || value.trim() === '') throw new Error(`${name} is required`);
  return value.trim();
}

function jsonResponse(status, message, extraHeaders = {}) {
  return new Response(JSON.stringify({ message }), {
    status,
    headers: { ...jsonHeaders(), ...extraHeaders },
  });
}

function jsonHeaders() {
  return {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff',
  };
}

export default createWorker();
