import { createHash, randomBytes } from 'node:crypto';

import { type APIRequestContext, expect, test } from '@playwright/test';

/**
 * Single sign-on for VirtualBiz (docs/integrations/virtualbiz-sso.md) with the local development client
 * `virtualbiz-local` (the API runs with PLATFORM_ENV=local): sign-in, consent, the redirect with code + state,
 * the code exchange, and no consent screen the second time. The client's callback is intercepted.
 */

const API = process.env.API_URL ?? 'http://localhost:8280';
const PASSWORD = 'E2e-pass-2026';
const CLIENT = 'virtualbiz-local';
const SECRET = 'local-dev-secret';
const CALLBACK = 'http://localhost:3000/platform/callback';

const b64url = (b: Buffer) => b.toString('base64url');

function authorizeUrl(extra: Record<string, string> = {}) {
  const verifier = b64url(randomBytes(32));
  const state = b64url(randomBytes(12));
  const q = new URLSearchParams({
    response_type: 'code', client_id: CLIENT, redirect_uri: CALLBACK, scope: 'openid email profile orgs', state,
    nonce: b64url(randomBytes(12)), code_challenge: b64url(createHash('sha256').update(verifier).digest()),
    code_challenge_method: 'S256', ...extra,
  });
  return { url: `/oauth/authorize?${q}`, state, verifier };
}

async function signUp(request: APIRequestContext, name: string, email: string) {
  const res = await request.post(`${API}/api/auth/sign-up`, { data: { name, email, password: PASSWORD }, headers: { 'X-Client': 'mobile' } });
  expect(res.ok()).toBeTruthy();
}

test('VirtualBiz sign-in: consent the first time, a code and the state come back, the second time no consent', async ({ page, request }, info) => {
  const id = `${Date.now().toString(36)}${info.project.name[0]}o`;
  const email = `sso-${id}@e2e.test`;
  await signUp(request, 'Sophea Sso', email);
  // The client's callback page is not running here: answer it and keep the URL.
  const callbacks: string[] = [];
  await page.route(`${CALLBACK}**`, (route) => {
    callbacks.push(route.request().url());
    return route.fulfill({ status: 200, contentType: 'text/html', body: '<p>VirtualBiz callback</p>' });
  });

  // Not signed in: through sign-in and back to the authorize page.
  const first = authorizeUrl();
  await page.goto(first.url);
  await page.waitForURL(/\/sign-in\?next=/);
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: /sign in/i }).click();

  // The consent screen: the app, who, and what it will see.
  await expect(page.getByText('Sign in to VirtualBiz')).toBeVisible();
  await expect(page.getByText(`VirtualBiz wants to sign you in as Sophea Sso (${email})`)).toBeVisible();
  await expect(page.getByText('Your organisations and your role in each')).toBeVisible();
  await page.getByRole('button', { name: 'Allow' }).click();
  await page.waitForURL(/localhost:3000\/platform\/callback/);
  const back = new URL(callbacks.at(-1)!);
  expect(back.searchParams.get('state')).toBe(first.state);
  const code = back.searchParams.get('code')!;
  expect(code.length).toBeGreaterThanOrEqual(43);

  // VirtualBiz's server exchanges the code.
  const token = await request.post(`${API}/api/oidc/token`, {
    form: { grant_type: 'authorization_code', code, redirect_uri: CALLBACK, code_verifier: first.verifier },
    headers: { Authorization: `Basic ${Buffer.from(`${CLIENT}:${SECRET}`).toString('base64')}` },
  });
  expect(token.status()).toBe(200);
  const body = await token.json();
  expect(body.token_type).toBe('Bearer');
  const claims = JSON.parse(Buffer.from(body.id_token.split('.')[1], 'base64url').toString());
  expect(claims.email).toBe(email);
  expect(claims.aud).toBe(CLIENT);

  // The second time: remembered, straight back with a new code.
  const second = authorizeUrl();
  await page.goto(second.url);
  await page.waitForURL(/localhost:3000\/platform\/callback/);
  const again = new URL(callbacks.at(-1)!);
  expect(again.searchParams.get('state')).toBe(second.state);
  expect(again.searchParams.get('code')).toBeTruthy();
  expect(again.searchParams.get('code')).not.toBe(code);

  // The account page lists VirtualBiz under Connected apps.
  await page.goto('/account');
  await expect(page.getByRole('heading', { name: 'Connected apps' })).toBeVisible();
  await expect(page.getByText('VirtualBiz', { exact: true })).toBeVisible();
});

test('an unknown app or return address shows an error and never redirects', async ({ page }) => {
  let redirected = false;
  await page.route('https://evil.example/**', (route) => { redirected = true; return route.abort(); });
  await page.goto(authorizeUrl({ redirect_uri: 'https://evil.example/platform/callback' }).url);
  await expect(page.getByText('This sign-in link isn’t valid')).toBeVisible();
  await expect(page.getByText(/return address isn’t registered/)).toBeVisible();
  expect(page.url()).toContain('/oauth/authorize');
  expect(redirected).toBe(false);
});
