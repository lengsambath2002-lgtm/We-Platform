import { type APIRequestContext, expect, test } from '@playwright/test';

const API = process.env.API_URL ?? 'http://localhost:8280';
const PASSWORD = 'E2e-pass-2026';

async function signUp(request: APIRequestContext, name: string, email: string) {
  const res = await request.post(`${API}/api/auth/sign-up`, { data: { name, email, password: PASSWORD }, headers: { 'X-Client': 'mobile' } });
  expect(res.ok()).toBeTruthy();
  return (await res.json()).data as { accessToken: string; user: { id: string } };
}

test('a lead becomes a customer with a quote and is won; the owner reads the finance report', async ({ page, request }, info) => {
  const id = `${Date.now().toString(36)}${info.project.name[0]}c`;
  const owner = await signUp(request, 'Owner Crm', `owner-${id}@e2e.test`);
  const slug = `crm-${id}`;
  const auth = { Authorization: `Bearer ${owner.accessToken}` };
  expect((await request.post(`${API}/api/orgs`, { data: { name: `Crm ${id}`, slug }, headers: auth })).ok()).toBeTruthy();
  expect((await request.put(`${API}/api/orgs/${slug}/modules/business`, { data: { enabled: true }, headers: auth })).ok()).toBeTruthy();

  await page.goto('/sign-in');
  await page.getByLabel('Email').fill(`owner-${id}@e2e.test`);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL(/\/overview$/);

  // A new lead.
  await page.goto(`/${slug}/business/deals`);
  await page.getByRole('button', { name: 'New deal' }).click();
  const dlg = page.getByRole('dialog');
  await dlg.getByLabel('What is the deal?').fill('Office fit-out');
  await dlg.getByLabel('Company').fill('Mekong Coffee');
  await dlg.getByLabel('Contact').fill('Mr. Sok');
  await dlg.getByLabel('Value (USD)').fill('1200');
  await dlg.getByRole('button', { name: 'Save' }).click();
  await page.waitForURL(/\/business\/deals\/[0-9a-f-]+$/);
  await expect(page.getByRole('heading', { name: 'Office fit-out' })).toBeVisible();

  // A logged call, then a quote: the lead becomes a customer.
  await page.getByLabel('What happened?').fill('Asked for a site visit');
  await page.getByRole('button', { name: 'Log it' }).click();
  await expect(page.getByText('Activity logged')).toBeVisible();
  await page.getByRole('button', { name: 'Start quote' }).click();
  await page.waitForURL(/\/business\/quotes\//);
  await expect(page.getByText('Mekong Coffee').first()).toBeVisible();
  await page.goBack();
  await expect(page.getByText('Proposal').first()).toBeVisible();
  await page.getByRole('button', { name: 'Won', exact: true }).click();
  await expect(page.getByText('Deal won')).toBeVisible();

  // The pipeline shows the win; the finance report opens.
  await page.goto(`/${slug}/business/deals`);
  await expect(page.getByText('Won this month')).toBeVisible();
  await expect(page.getByText('100%')).toBeVisible();
  await page.goto(`/${slug}/business/reports`);
  await expect(page.getByRole('heading', { name: 'Profit and loss' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'What customers owe, by age' })).toBeVisible();
});
