import { type APIRequestContext, expect, test } from '@playwright/test';

const API = process.env.API_URL ?? 'http://localhost:8280';
const PASSWORD = 'E2e-pass-2026';

async function signUp(request: APIRequestContext, name: string, email: string) {
  const res = await request.post(`${API}/api/auth/sign-up`, { data: { name, email, password: PASSWORD }, headers: { 'X-Client': 'mobile' } });
  expect(res.ok()).toBeTruthy();
  return (await res.json()).data as { accessToken: string; user: { id: string } };
}

test('HR sees the dashboard and downloads a report; a member sees the org chart', async ({ page, request }, info) => {
  const id = `${Date.now().toString(36)}${info.project.name[0]}i`;
  const owner = await signUp(request, 'Owner Ins', `owner-${id}@e2e.test`);
  const member = await signUp(request, 'Visal Ins', `member-${id}@e2e.test`);
  const slug = `ins-${id}`;
  const auth = { Authorization: `Bearer ${owner.accessToken}` };
  expect((await request.post(`${API}/api/orgs`, { data: { name: `Ins ${id}`, slug }, headers: auth })).ok()).toBeTruthy();
  const inv = await request.post(`${API}/api/orgs/${slug}/invitations`, { data: { email: `member-${id}@e2e.test`, role: 'MEMBER' }, headers: auth });
  const token = ((await inv.json()).data.link as string).split('/').pop()!;
  expect((await request.post(`${API}/api/invitations/${token}/accept`, { headers: { Authorization: `Bearer ${member.accessToken}` } })).ok()).toBeTruthy();
  expect((await request.put(`${API}/api/orgs/${slug}/modules/hr`, { data: { enabled: true }, headers: auth })).ok()).toBeTruthy();
  expect((await request.put(`${API}/api/orgs/${slug}/hr/people/${member.user.id}`, { data: { managerId: owner.user.id, department: 'Sales' }, headers: auth })).ok()).toBeTruthy();

  await page.goto('/sign-in');
  await page.getByLabel('Email').fill(`owner-${id}@e2e.test`);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL(/\/overview$/);
  await page.goto(`/${slug}/hr/dashboard`);
  await expect(page.getByText('Headcount')).toBeVisible();
  await expect(page.getByText('People by department')).toBeVisible();
  const download = page.waitForEvent('download');
  await page.getByRole('button', { name: 'CSV' }).first().click();
  expect((await download).suggestedFilename()).toBe('employees.csv');

  await page.context().clearCookies();
  await page.goto('/sign-in');
  await page.getByLabel('Email').fill(`member-${id}@e2e.test`);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL(/\/overview$/);
  await page.goto(`/${slug}/hr/org-chart`);
  const tree = page.getByRole('list', { name: 'Org chart' });
  await expect(tree.getByRole('link', { name: /Owner Ins/ })).toBeVisible();
  await expect(tree.getByRole('listitem').filter({ hasText: 'Owner Ins' }).getByRole('link', { name: /Visal Ins/ })).toBeVisible();
  await page.goto(`/${slug}/hr/dashboard`);
  await expect(page.getByText("You don't have access")).toBeVisible();
});
