import { type APIRequestContext, type Page, expect, test } from '@playwright/test';

const API = process.env.API_URL ?? 'http://localhost:8280';
const PASSWORD = 'E2e-pass-2026';
/** Listed in the API's PLATFORM_SUPER_ADMINS (CI sets root@e2e.test). */
const ROOT = process.env.E2E_SUPER_ADMIN ?? 'root@e2e.test';

/** Signs up (or, when the account exists from an earlier run, signs in); returns the access token. */
async function account(request: APIRequestContext, name: string, email: string) {
  const headers = { 'X-Client': 'mobile' };
  let res = await request.post(`${API}/api/auth/sign-up`, { data: { name, email, password: PASSWORD }, headers });
  if (res.status() === 409) res = await request.post(`${API}/api/auth/sign-in`, { data: { email, password: PASSWORD }, headers });
  expect(res.ok()).toBeTruthy();
  return (await res.json()).data.accessToken as string;
}

async function signIn(page: Page, email: string) {
  await page.goto('/sign-in');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: /sign in/i }).click();
}

test('a super admin suspends an organisation and disables an account; their members are locked out until undone', async ({ page, browser, request }, info) => {
  const id = `${Date.now().toString(36)}${info.project.name[0]}p`;
  await account(request, 'Root Admin', ROOT);
  const ownerEmail = `owner-${id}@e2e.test`;
  const token = await account(request, 'Owner Plat', ownerEmail);
  const slug = `plat-${id}`;
  expect((await request.post(`${API}/api/orgs`, { data: { name: `Plat ${id}`, slug }, headers: { Authorization: `Bearer ${token}` } })).ok()).toBeTruthy();
  const spammer = `spam-${id}@e2e.test`;
  await account(request, `Spammer ${id}`, spammer);

  // The console is in the super admin's account menu.
  await signIn(page, ROOT);
  await page.waitForURL(/\/(overview|onboarding)$/);
  await page.goto('/admin');
  await expect(page.getByRole('heading', { name: 'Overview' })).toBeVisible();
  await expect(page.getByText('Module adoption')).toBeVisible();
  const tabs = page.getByRole('navigation', { name: 'Platform admin' });

  // Suspend the organisation, with a reason.
  await tabs.getByRole('link', { name: 'Organisations', exact: true }).click();
  await page.getByLabel('Search').fill(slug);
  await page.getByRole('link', { name: `Plat ${id}` }).click();
  await page.getByRole('button', { name: 'Suspend' }).click();
  const dialog = page.getByRole('alertdialog');
  await dialog.getByLabel(/Reason/).fill('Unpaid subscription');
  await dialog.getByRole('button', { name: 'Suspend' }).click();
  await expect(page.getByText(/^Suspended \d.* by /)).toBeVisible();
  await expect(page.getByText('Reason: Unpaid subscription')).toBeVisible();

  // Its owner can't open it…
  const owner = await (await browser.newContext()).newPage();
  await signIn(owner, ownerEmail);
  await expect(owner.getByRole('heading', { name: 'Organisation suspended' })).toBeVisible();

  // …until it is reactivated.
  await page.getByRole('button', { name: 'Reactivate' }).click();
  await expect(page.getByText(`Plat ${id} is active again`)).toBeVisible();
  await owner.goto(`/${slug}/overview`);
  await expect(owner.getByRole('heading', { name: 'Organisation suspended' })).toBeHidden();

  // Disable an account: it can't sign in.
  await tabs.getByRole('link', { name: 'Users', exact: true }).click();
  await page.getByLabel('Search').fill(spammer);
  await page.getByRole('link', { name: `Spammer ${id}` }).click();
  await page.getByRole('button', { name: 'Disable account' }).click();
  await dialog.getByLabel(/Reason/).fill('Spam sign-ups');
  await dialog.getByRole('button', { name: 'Disable account' }).click();
  await expect(page.getByText(/^Disabled \d/)).toBeVisible();

  const spam = await (await browser.newContext()).newPage();
  await signIn(spam, spammer);
  await expect(spam.getByText(/This account is disabled/)).toBeVisible();

  // Everything is in the platform audit log.
  await tabs.getByRole('link', { name: 'Audit log', exact: true }).click();
  await expect(page.getByText(`disabled Spammer ${id}’s account`, { exact: false })).toBeVisible();
  await expect(page.getByText('reactivated').first()).toBeVisible();
});

test('someone who is not a super admin finds no console', async ({ page, request }, info) => {
  const email = `plain-${Date.now().toString(36)}${info.project.name[0]}@e2e.test`;
  await account(request, 'Plain Person', email);
  expect((await request.get(`${API}/api/platform/stats`)).status()).toBe(401);
  await signIn(page, email);
  await page.waitForURL(/\/onboarding$/);
  await page.goto('/admin');
  await expect(page.getByText('This page is for platform administrators.')).toBeVisible();
});
