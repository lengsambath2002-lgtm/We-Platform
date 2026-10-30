import { type APIRequestContext, expect, test } from '@playwright/test';

const API = process.env.API_URL ?? 'http://localhost:8280';
const PASSWORD = 'E2e-pass-2026';

async function signUp(request: APIRequestContext, name: string, email: string) {
  const res = await request.post(`${API}/api/auth/sign-up`, { data: { name, email, password: PASSWORD }, headers: { 'X-Client': 'mobile' } });
  expect(res.ok()).toBeTruthy();
  return (await res.json()).data as { accessToken: string; user: { id: string } };
}

test('an owner creates a Branch HR role and grants it for one branch', async ({ page, request }, info) => {
  const id = `${Date.now().toString(36)}${info.project.name[0]}r`;
  const owner = await signUp(request, 'Owner Rol', `owner-${id}@e2e.test`);
  const member = await signUp(request, 'Dara Rol', `member-${id}@e2e.test`);
  const slug = `rol-${id}`;
  const auth = { Authorization: `Bearer ${owner.accessToken}` };
  expect((await request.post(`${API}/api/orgs`, { data: { name: `Rol ${id}`, slug }, headers: auth })).ok()).toBeTruthy();
  const inv = await request.post(`${API}/api/orgs/${slug}/invitations`, { data: { email: `member-${id}@e2e.test`, role: 'MEMBER' }, headers: auth });
  const token = ((await inv.json()).data.link as string).split('/').pop()!;
  expect((await request.post(`${API}/api/invitations/${token}/accept`, { headers: { Authorization: `Bearer ${member.accessToken}` } })).ok()).toBeTruthy();
  expect((await request.put(`${API}/api/orgs/${slug}/modules/hr`, { data: { enabled: true }, headers: auth })).ok()).toBeTruthy();
  expect((await request.post(`${API}/api/orgs/${slug}/structure/units`, { data: { name: 'North', kind: 'BRANCH' }, headers: auth })).ok()).toBeTruthy();

  await page.goto('/sign-in');
  await page.getByLabel('Email').fill(`owner-${id}@e2e.test`);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL(/\/overview$/);

  await page.goto(`/${slug}/admin/roles`);
  await expect(page.getByText('No custom roles yet')).toBeVisible();
  await page.getByRole('button', { name: 'New role' }).click();
  const dlg = page.getByRole('dialog');
  await dlg.getByLabel('Name', { exact: true }).fill('Branch HR');
  await dlg.getByRole('checkbox', { name: /HR manager/ }).click();
  await dlg.getByRole('button', { name: 'Save' }).click();
  await expect(page.getByText('Role saved')).toBeVisible();

  await page.getByRole('region', { name: 'Branch HR' }).getByRole('button', { name: 'Grant' }).click();
  const g = page.getByRole('dialog');
  await g.getByRole('combobox', { name: 'Member' }).click();
  await page.getByRole('option', { name: 'Dara Rol' }).click();
  await g.getByRole('combobox', { name: 'Applies to' }).click();
  await page.getByRole('option', { name: 'North' }).click();
  await g.getByRole('button', { name: 'Grant' }).click();
  await expect(page.getByText('Granted Branch HR to Dara Rol')).toBeVisible();
  await expect(page.getByRole('region', { name: 'Branch HR' })).toContainText('In North');

  // The member now manages HR for that branch's people (hr.team), not organisation-wide.
  const ctx = await request.get(`${API}/api/orgs/${slug}/context`, { headers: { Authorization: `Bearer ${member.accessToken}` } });
  const perms = (await ctx.json()).data.permissions as string[];
  expect(perms).toContain('hr.team');
  expect(perms).not.toContain('hr.manage');

  await page.goto(`/${slug}/admin/members`);
  await expect(page.getByRole('link', { name: 'Branch HR' })).toBeVisible();
});
