import { type APIRequestContext, expect, test } from '@playwright/test';

const API = process.env.API_URL ?? 'http://localhost:8280';
const PASSWORD = 'E2e-pass-2026';

async function signUp(request: APIRequestContext, name: string, email: string) {
  const res = await request.post(`${API}/api/auth/sign-up`, { data: { name, email, password: PASSWORD }, headers: { 'X-Client': 'mobile' } });
  expect(res.ok()).toBeTruthy();
  return (await res.json()).data as { accessToken: string; user: { id: string } };
}

test('an owner imports people with units, positions and managers from a CSV', async ({ page, request }, info) => {
  const id = `${Date.now().toString(36)}${info.project.name[0]}m`;
  const owner = await signUp(request, 'Owner Imp', `owner-${id}@e2e.test`);
  const slug = `imp-${id}`;
  const auth = { Authorization: `Bearer ${owner.accessToken}` };
  expect((await request.post(`${API}/api/orgs`, { data: { name: `Imp ${id}`, slug }, headers: auth })).ok()).toBeTruthy();

  await page.goto('/sign-in');
  await page.getByLabel('Email').fill(`owner-${id}@e2e.test`);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL(/\/overview$/);

  await page.goto(`/${slug}/admin/members`);
  await page.getByRole('link', { name: 'Import people' }).click();
  await expect(page.getByRole('heading', { name: 'Import people' })).toBeVisible();

  const csv = [
    'Name,Email,Unit,Unit types,Position,Manager email,2nd approver email,Unit head',
    `Bo Lead,lead-${id}@e2e.test,Mobile Center > Mobile,Center > Team,Team Lead,owner-${id}@e2e.test,,yes`,
    `"Ly, Dev",dev-${id}@e2e.test,Mobile Center > Mobile,,Engineer I,lead-${id}@e2e.test,owner-${id}@e2e.test,`,
  ].join('\n');
  await page.getByLabel('Choose CSV file').setInputFiles({ name: 'people.csv', mimeType: 'text/csv', buffer: Buffer.from(csv) });
  await expect(page.getByText('2 new accounts')).toBeVisible();
  await expect(page.getByText('New units: Mobile Center, Mobile Center › Mobile')).toBeVisible();
  await page.getByRole('button', { name: 'Import 2 people' }).click();
  await expect(page.getByRole('status')).toContainText('Imported: 2 new accounts');

  await page.getByRole('link', { name: 'View the structure' }).click();
  await page.getByRole('tab', { name: 'People' }).click();
  await expect(page.getByRole('row').filter({ hasText: 'Ly, Dev' })).toContainText('Mobile Center › Mobile');
});
