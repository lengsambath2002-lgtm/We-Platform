import { type APIRequestContext, expect, test } from '@playwright/test';

const API = process.env.API_URL ?? 'http://localhost:8280';
const PASSWORD = 'E2e-pass-2026';

async function signUp(request: APIRequestContext, name: string, email: string) {
  const res = await request.post(`${API}/api/auth/sign-up`, { data: { name, email, password: PASSWORD }, headers: { 'X-Client': 'mobile' } });
  expect(res.ok()).toBeTruthy();
  return (await res.json()).data as { accessToken: string; user: { id: string } };
}

test('a new company is on a Standard trial and moves to Free, which locks HR', async ({ page, request }, info) => {
  page.on('dialog', (d) => d.accept());
  const id = `${Date.now().toString(36)}${info.project.name[0]}b`;
  const owner = await signUp(request, 'Owner Bill', `owner-${id}@e2e.test`);
  const slug = `bill-${id}`;
  const auth = { Authorization: `Bearer ${owner.accessToken}` };
  expect((await request.post(`${API}/api/orgs`, { data: { name: `Bill ${id}`, slug }, headers: auth })).ok()).toBeTruthy();

  await page.goto('/sign-in');
  await page.getByLabel('Email').fill(`owner-${id}@e2e.test`);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL(/\/overview$/);

  await page.goto(`/${slug}/admin/billing`);
  await expect(page.getByText('Trial: 14 days left')).toBeVisible();
  await page.getByRole('button', { name: 'Choose Free' }).click();
  await expect(page.getByText('You are on Free now')).toBeVisible();
  await expect(page.getByText('1 of 5')).toBeVisible();

  await page.goto(`/${slug}/admin/modules`);
  await expect(page.getByRole('switch', { name: /HR & Workforce/ })).toBeDisabled();
  await expect(page.getByRole('link', { name: /Not in your plan/ }).first()).toBeVisible();
});
