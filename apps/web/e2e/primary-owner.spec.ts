import { type APIRequestContext, expect, test } from '@playwright/test';

const API = process.env.API_URL ?? 'http://localhost:8280';
const PASSWORD = 'E2e-pass-2026';

async function signUp(request: APIRequestContext, name: string, email: string) {
  const res = await request.post(`${API}/api/auth/sign-up`, { data: { name, email, password: PASSWORD }, headers: { 'X-Client': 'mobile' } });
  expect(res.ok()).toBeTruthy();
  return (await res.json()).data as { accessToken: string; user: { id: string } };
}

test('the primary owner hands the role over in Settings', async ({ page, request }, info) => {
  const id = `${Date.now().toString(36)}${info.project.name[0]}w`;
  const owner = await signUp(request, 'Founder Prim', `owner-${id}@e2e.test`);
  const partner = await signUp(request, 'Partner Prim', `partner-${id}@e2e.test`);
  const slug = `prim-${id}`;
  const auth = { Authorization: `Bearer ${owner.accessToken}` };
  expect((await request.post(`${API}/api/orgs`, { data: { name: `Prim ${id}`, slug }, headers: auth })).ok()).toBeTruthy();
  const inv = await request.post(`${API}/api/orgs/${slug}/invitations`, { data: { email: `partner-${id}@e2e.test`, role: 'ADMIN' }, headers: auth });
  const token = ((await inv.json()).data.link as string).split('/').pop()!;
  expect((await request.post(`${API}/api/invitations/${token}/accept`, { headers: { Authorization: `Bearer ${partner.accessToken}` } })).ok()).toBeTruthy();

  await page.goto('/sign-in');
  await page.getByLabel('Email').fill(`owner-${id}@e2e.test`);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL(/\/overview$/);

  await page.goto(`/${slug}/admin/settings`);
  await expect(page.getByRole('button', { name: 'Delete organisation' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Leave' })).toBeDisabled();
  await page.getByRole('combobox', { name: 'New primary owner' }).click();
  await page.getByRole('option', { name: 'Partner Prim' }).click();
  page.on('dialog', (d) => d.accept());
  await page.getByRole('button', { name: 'Hand over' }).click();
  await expect(page.getByText('Partner Prim is now the primary owner')).toBeVisible();
  await expect(page.getByText('Only the primary owner can delete the organisation.')).toBeVisible();
});
