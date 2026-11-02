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
  return (await res.json()).data.accessToken as string;
}

async function signIn(page: Page, email: string) {
  await page.goto('/sign-in');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL(/\/(overview|onboarding)$/);
}

test('choosing Standard issues an invoice; the owner says they paid and a super admin records it', async ({ page, browser, request }, info) => {
  page.on('dialog', (d) => d.accept());
  const id = `${Date.now().toString(36)}${info.project.name[0]}i`;
  await account(request, 'Root Admin', ROOT);
  const token = await account(request, 'Owner Inv', `owner-${id}@e2e.test`);
  const slug = `inv-${id}`;
  expect((await request.post(`${API}/api/orgs`, { data: { name: `Inv ${id}`, slug }, headers: { Authorization: `Bearer ${token}` } })).ok()).toBeTruthy();

  await signIn(page, `owner-${id}@e2e.test`);
  await page.goto(`/${slug}/admin/billing`);
  await page.getByRole('button', { name: 'Choose Standard' }).click();
  await expect(page.getByText('You are on Standard now')).toBeVisible();
  const row = page.getByRole('link', { name: /INV-\d{4}-\d{5}/ });
  await expect(row).toContainText('To pay');
  const number = (await row.textContent())!.match(/INV-\d{4}-\d{5}/)![0];
  await row.click();

  await expect(page.getByRole('heading', { name: number })).toBeVisible();
  await expect(page.getByText('Bill to')).toBeVisible();
  await page.getByLabel('Transfer reference').fill(`ABA-${id}`);
  await page.getByRole('button', { name: 'We paid' }).click();
  await expect(page.getByText(`You told us you paid (reference ABA-${id})`)).toBeVisible();

  const root = await (await browser.newContext()).newPage();
  root.on('dialog', (d) => d.accept());
  await signIn(root, ROOT);
  await root.goto('/admin/invoices');
  const line = root.getByRole('listitem').filter({ hasText: number });
  await expect(line).toContainText(`Says paid: ABA-${id}`);
  await line.getByRole('button', { name: 'Record payment' }).click();
  await root.getByRole('dialog').getByRole('button', { name: 'Record payment' }).click();
  await expect(root.getByText('Payment recorded')).toBeVisible();
  await expect(root.getByRole('listitem').filter({ hasText: number })).toHaveCount(0);

  await page.reload();
  await expect(page.getByText(/^Paid /)).toBeVisible();
});
