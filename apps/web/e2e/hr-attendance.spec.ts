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
  await page.waitForURL(/\/overview$/);
}

/** yyyy-MM-dd in the organisation's zone, `days` from today. */
function orgDay(days: number) {
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Phnom_Penh' }).format(new Date());
  const d = new Date(`${today}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

/** The latest weekday before today (organisation's zone). */
function lastWeekday() {
  for (let i = 1; ; i++) {
    const d = orgDay(-i);
    const w = new Date(`${d}T00:00:00Z`).getUTCDay();
    if (w !== 0 && w !== 6) return d;
  }
}

test('a member clocks in and out and asks for overtime; their manager approves it; HR corrects a missed day', async ({ page, request }, info) => {
  const id = `${Date.now().toString(36)}${info.project.name[0]}a`;
  const owner = await signUp(request, 'Owner Att', `owner-${id}@e2e.test`);
  const manager = await signUp(request, 'Manager Att', `manager-${id}@e2e.test`);
  const member = await signUp(request, 'Member Att', `member-${id}@e2e.test`);
  const slug = `att-${id}`;
  const auth = { Authorization: `Bearer ${owner.accessToken}` };
  expect((await request.post(`${API}/api/orgs`, { data: { name: `Att ${id}`, slug }, headers: auth })).ok()).toBeTruthy();
  for (const [who, email] of [[manager, `manager-${id}@e2e.test`], [member, `member-${id}@e2e.test`]] as const) {
    const inv = await request.post(`${API}/api/orgs/${slug}/invitations`, { data: { email, role: 'MEMBER' }, headers: auth });
    const token = ((await inv.json()).data.link as string).split('/').pop()!;
    expect((await request.post(`${API}/api/invitations/${token}/accept`, { headers: { Authorization: `Bearer ${who.accessToken}` } })).ok()).toBeTruthy();
  }
  expect((await request.put(`${API}/api/orgs/${slug}/modules/hr`, { data: { enabled: true }, headers: auth })).ok()).toBeTruthy();
  expect((await request.put(`${API}/api/orgs/${slug}/hr/people/${member.user.id}`, {
    data: { managerId: manager.user.id, startDate: orgDay(-365) }, headers: auth,
  })).ok()).toBeTruthy();

  // The member clocks in from HR home, then out.
  await signIn(page, `member-${id}@e2e.test`);
  await page.goto(`/${slug}/hr`);
  await page.getByRole('button', { name: 'Clock in' }).click();
  await expect(page.getByText(/Clocked in at \d\d:\d\d/)).toBeVisible();
  await page.getByRole('button', { name: 'Clock out' }).click();
  await expect(page.getByText('Done for today')).toBeVisible();

  // …and asks for 2 h 30 min of overtime on the last weekday.
  const day = lastWeekday();
  await page.getByRole('navigation', { name: 'HR pages' }).getByRole('link', { name: 'Attendance', exact: true }).click();
  await page.waitForURL(new RegExp(`/${slug}/hr/attendance$`));
  await expect(page.getByRole('heading', { name: 'My month' })).toBeVisible();
  await page.getByRole('button', { name: 'Request overtime' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Day worked').fill(day);
  await dialog.getByLabel('Hours').fill('2');
  await dialog.getByLabel('Minutes').fill('30');
  await dialog.getByLabel('What was the extra work?').fill('Month-end stock count');
  await dialog.getByRole('button', { name: 'Request overtime' }).click();
  await expect(page.getByText('Overtime request sent')).toBeVisible();
  await expect(page.getByText('Month-end stock count')).toBeVisible();

  // The manager approves it from Approvals.
  await page.context().clearCookies();
  await signIn(page, `manager-${id}@e2e.test`);
  await page.goto(`/${slug}/approvals`);
  await page.getByText(/Overtime · Member Att/).click();
  await page.getByRole('button', { name: 'Approve' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Approve' }).click();
  await expect(page.getByText('Approved', { exact: true }).first()).toBeVisible();

  // HR sees the member at work today, opens their month and fills in a missed day.
  await page.context().clearCookies();
  await signIn(page, `owner-${id}@e2e.test`);
  await page.goto(`/${slug}/hr/attendance/board`);
  await expect(page.getByRole('heading', { name: 'Team attendance' })).toBeVisible();
  const row = page.getByRole('row').filter({ hasText: 'Member Att' });
  await expect(row.getByText(/Present|Late/)).toBeVisible();
  await row.getByRole('link', { name: /Member Att/ }).click();
  await expect(page.getByRole('heading', { name: 'Attendance: Member Att' })).toBeVisible();
  await page.goto(`/${slug}/hr/attendance/${member.user.id}?month=${day.slice(0, 7)}`);
  const missed = page.getByRole('row').filter({ has: page.getByText('Absent', { exact: true }) }).first();
  await missed.getByRole('button', { name: /^Correct / }).click();
  const fix = page.getByRole('dialog');
  await fix.getByLabel('Clock-in').fill('08:30');
  await fix.getByLabel('Clock-out').fill('17:30');
  await fix.getByLabel('Reason').fill('Forgot to clock in');
  await fix.getByRole('button', { name: 'Save' }).click();
  await expect(page.getByText('Day corrected')).toBeVisible();
  await expect(page.getByText('Corrected by HR: Forgot to clock in').first()).toBeVisible();
  // The approved overtime is on the timesheet.
  await expect(page.getByText('Approved overtime')).toBeVisible();
  await expect(page.getByText('2 h 30 min').first()).toBeVisible();
});
