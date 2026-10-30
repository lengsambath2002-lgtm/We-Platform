import { createHmac } from 'node:crypto';

import { type APIRequestContext, type Page, expect, test } from '@playwright/test';

const API = process.env.API_URL ?? 'http://localhost:8280';
const PASSWORD = 'E2e-pass-2026';
/** Listed in the API's PLATFORM_SUPER_ADMINS (CI sets root@e2e.test). */
const ROOT = process.env.E2E_SUPER_ADMIN ?? 'root@e2e.test';

async function account(request: APIRequestContext, name: string, email: string) {
  const headers = { 'X-Client': 'mobile' };
  let res = await request.post(`${API}/api/auth/sign-up`, { data: { name, email, password: PASSWORD }, headers });
  if (res.status() === 409) res = await request.post(`${API}/api/auth/sign-in`, { data: { email, password: PASSWORD }, headers });
  expect(res.ok()).toBeTruthy();
  return (await res.json()).data as { accessToken: string; user: { id: string } };
}

async function signIn(page: Page, email: string) {
  await page.goto('/sign-in');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: /sign in/i }).click();
}

/** What an authenticator app shows now for a Base32 key (RFC 6238). */
function totp(key: string, at = Date.now()) {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  let bits = '';
  for (const c of key.replace(/\s/g, '')) bits += alphabet.indexOf(c).toString(2).padStart(5, '0');
  const bytes = Buffer.from((bits.match(/.{8}/g) ?? []).map((b) => parseInt(b, 2)));
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(Math.floor(at / 30_000)));
  const h = createHmac('sha1', bytes).update(counter).digest();
  const o = h[h.length - 1]! & 0x0f;
  return String((h.readUInt32BE(o) & 0x7fffffff) % 1_000_000).padStart(6, '0');
}

test('two-step sign-in: set up with an app, sign in with a recovery code; a super admin turns it off', async ({ page, browser, request }, info) => {
  page.on('dialog', (d) => d.accept());
  const id = `${Date.now().toString(36)}${info.project.name[0]}t`;
  const email = `twostep-${id}@e2e.test`;
  const me = await account(request, 'Two Step', email);
  await account(request, 'Root Admin', ROOT);

  await signIn(page, email);
  await page.waitForURL(/\/onboarding$/);
  await page.goto('/account');
  await page.getByRole('button', { name: 'Set up two-step sign-in' }).click();
  await expect(page.getByRole('img', { name: 'QR code for your authenticator app' })).toBeVisible();
  const key = (await page.locator('code').first().textContent())!;
  await page.getByLabel('6-digit code').fill(totp(key));
  await page.getByRole('button', { name: 'Turn on' }).click();
  const list = page.getByRole('list', { name: 'Recovery codes' }).getByRole('listitem');
  await expect(list).toHaveCount(10);
  const codes = await list.allTextContents();
  expect(codes).toHaveLength(10);
  await page.getByRole('button', { name: 'I saved them' }).click();
  await expect(page.getByText('On since')).toContainText('10 recovery codes left');

  // A fresh browser: the password, then a recovery code.
  const other = await (await browser.newContext()).newPage();
  await signIn(other, email);
  await expect(other.getByText('Enter the 6-digit code from your authenticator app.')).toBeVisible();
  await other.getByRole('button', { name: 'Use a recovery code instead' }).click();
  await other.getByLabel('Code').fill(codes[0]!);
  await other.getByRole('button', { name: 'Verify' }).click();
  await other.waitForURL(/\/onboarding$/);

  const root = await (await browser.newContext()).newPage();
  root.on('dialog', (d) => d.accept());
  await signIn(root, ROOT);
  await root.waitForURL(/\/(overview|onboarding)$/);
  await root.goto(`/admin/users/${me.user.id}`);
  await root.getByRole('button', { name: 'Turn off two-step sign-in' }).click();
  await root.getByRole('alertdialog').getByRole('button', { name: 'Turn off two-step sign-in' }).click();
  await expect(root.getByText('Two-step sign-in turned off for Two Step')).toBeVisible();

  const again = await (await browser.newContext()).newPage();
  await signIn(again, email);
  await again.waitForURL(/\/onboarding$/);
});
