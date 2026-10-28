import { type APIRequestContext, type Page, expect, test } from '@playwright/test';

const API = process.env.API_URL ?? 'http://localhost:8280';
const PASSWORD = 'E2e-pass-2026';

async function signUp(request: APIRequestContext, name: string, email: string) {
  const res = await request.post(`${API}/api/auth/sign-up`, { data: { name, email, password: PASSWORD }, headers: { 'X-Client': 'mobile' } });
  expect(res.ok()).toBeTruthy();
  return (await res.json()).data as { accessToken: string; user: { id: string } };
}

async function signIn(page: Page, email: string) {
  await page.goto('/sign-in');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: /sign in/i }).click();
}

/** Monday of the second week of March next year: a future, same-year working week. */
function nextMarch() {
  const y = new Date().getFullYear() + 1;
  const d = new Date(Date.UTC(y, 2, 8));
  while (d.getUTCDay() !== 1) d.setUTCDate(d.getUTCDate() + 1);
  const iso = (x: Date) => x.toISOString().slice(0, 10);
  const end = new Date(d);
  end.setUTCDate(end.getUTCDate() + 2);
  return { year: y, start: iso(d), end: iso(end) };
}

test('an admin switches HR on, a member requests leave, their manager approves it from Approvals, the balance updates', async ({ page, request }, info) => {
  const id = `${Date.now().toString(36)}${info.project.name[0]}h`;
  const owner = await signUp(request, 'Owner Hr', `owner-${id}@e2e.test`);
  const manager = await signUp(request, 'Manager Hr', `manager-${id}@e2e.test`);
  const member = await signUp(request, 'Member Hr', `member-${id}@e2e.test`);
  const slug = `hr-${id}`;
  const auth = { Authorization: `Bearer ${owner.accessToken}` };
  expect((await request.post(`${API}/api/orgs`, { data: { name: `HR ${id}`, slug }, headers: auth })).ok()).toBeTruthy();
  for (const [who, email] of [[manager, `manager-${id}@e2e.test`], [member, `member-${id}@e2e.test`]] as const) {
    const inv = await request.post(`${API}/api/orgs/${slug}/invitations`, { data: { email, role: 'MEMBER' }, headers: auth });
    const token = ((await inv.json()).data.link as string).split('/').pop()!;
    expect((await request.post(`${API}/api/invitations/${token}/accept`, { headers: { Authorization: `Bearer ${who.accessToken}` } })).ok()).toBeTruthy();
  }

  // The owner switches HR on in Admin → Modules; the module page now leads to HR.
  await signIn(page, `owner-${id}@e2e.test`);
  await page.waitForURL(/\/overview$/);
  await page.goto(`/${slug}/admin/modules`);
  await page.getByRole('switch', { name: 'Use HR & Workforce' }).click();
  await expect(page.getByText('HR & Workforce switched on')).toBeVisible();
  await page.goto(`/${slug}/m/hr`);
  await page.waitForURL(new RegExp(`/${slug}/hr$`));
  await expect(page.getByRole('heading', { name: 'My leave' })).toBeVisible();
  // HR sets the member's manager.
  const set = await request.put(`${API}/api/orgs/${slug}/hr/people/${member.user.id}`, {
    data: { managerId: manager.user.id, title: 'Accountant', department: 'Finance' }, headers: auth,
  });
  expect(set.ok()).toBeTruthy();

  // The member asks for three days of annual leave.
  const { year, start, end } = nextMarch();
  await page.context().clearCookies();
  await signIn(page, `member-${id}@e2e.test`);
  await page.waitForURL(/\/overview$/);
  await page.goto(`/${slug}/hr`);
  await page.getByRole('button', { name: 'Request leave' }).first().click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Leave type').click();
  await page.getByRole('option', { name: /Annual leave/ }).click();
  await dialog.getByLabel('First day', { exact: true }).fill(start);
  await dialog.getByLabel('Last day', { exact: true }).fill(end);
  await expect(dialog.getByText('Takes 3 days')).toBeVisible();
  await dialog.getByLabel('Reason').fill('Family wedding');
  await dialog.getByRole('button', { name: 'Send request' }).click();
  await page.waitForURL(/\/hr\/leave\//);
  await expect(page.getByText('Pending').first()).toBeVisible();
  await expect(page.getByRole('button', { name: 'Withdraw' })).toBeVisible();

  // The manager decides from the Approvals inbox.
  await page.context().clearCookies();
  await signIn(page, `manager-${id}@e2e.test`);
  await page.waitForURL(/\/overview$/);
  await page.goto(`/${slug}/approvals`);
  await page.getByText(/Annual leave · Member Hr/).click();
  await expect(page.getByText('Family wedding').first()).toBeVisible();
  await page.getByRole('button', { name: 'Approve' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Approve' }).click();
  await expect(page.getByText('Approved', { exact: true }).first()).toBeVisible();
  await page.getByRole('link', { name: 'Open the leave request' }).click();
  await page.waitForURL(/\/hr\/leave\//);
  await expect(page.getByText('Approved', { exact: true }).first()).toBeVisible();

  // The member sees 15 of 18 days left for that year.
  await page.context().clearCookies();
  await signIn(page, `member-${id}@e2e.test`);
  await page.waitForURL(/\/overview$/);
  await page.goto(`/${slug}/hr`);
  await page.getByLabel('Year').click();
  await page.getByRole('option', { name: String(year) }).click();
  const annual = page.getByRole('listitem').filter({ hasText: 'Annual leave' }).first();
  await expect(annual).toContainText('15');
  await expect(annual).toContainText('of 18 days left');
  await expect(annual).toContainText('3 used');
});
