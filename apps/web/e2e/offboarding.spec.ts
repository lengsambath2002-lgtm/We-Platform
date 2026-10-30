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

const today = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Phnom_Penh' }).format(new Date());

test('an employee resigns; HR accepts, works through the checklist and completes it', async ({ browser, request }, info) => {
  const id = `${Date.now().toString(36)}${info.project.name[0]}o`;
  const owner = await signUp(request, 'Owner Off', `owner-${id}@e2e.test`);
  const member = await signUp(request, 'Rithy Off', `member-${id}@e2e.test`);
  const slug = `off-${id}`;
  const auth = { Authorization: `Bearer ${owner.accessToken}` };
  expect((await request.post(`${API}/api/orgs`, { data: { name: `Off ${id}`, slug }, headers: auth })).ok()).toBeTruthy();
  const inv = await request.post(`${API}/api/orgs/${slug}/invitations`, { data: { email: `member-${id}@e2e.test`, role: 'MEMBER' }, headers: auth });
  const token = ((await inv.json()).data.link as string).split('/').pop()!;
  expect((await request.post(`${API}/api/invitations/${token}/accept`, { headers: { Authorization: `Bearer ${member.accessToken}` } })).ok()).toBeTruthy();
  expect((await request.put(`${API}/api/orgs/${slug}/modules/hr`, { data: { enabled: true }, headers: auth })).ok()).toBeTruthy();
  // A short template keeps the walk-through brief.
  expect((await request.put(`${API}/api/orgs/${slug}/hr/offboarding/template`, {
    data: { items: [{ title: 'Return laptop', dueDays: 0 }, { title: 'Remove email access', dueDays: 0 }] }, headers: auth,
  })).ok()).toBeTruthy();

  // The employee resigns from their own profile.
  const mine = await (await browser.newContext()).newPage();
  await signIn(mine, `member-${id}@e2e.test`);
  await mine.goto(`/${slug}/hr/people/${member.user.id}`);
  await mine.getByRole('button', { name: 'Resign' }).click();
  await mine.getByRole('dialog').getByLabel('Last working day').fill(today());
  await mine.getByRole('dialog').getByLabel('Notes').fill('Going back to school');
  await mine.getByRole('dialog').getByRole('button', { name: 'Resign' }).click();
  await expect(mine.getByText('Resignation received')).toBeVisible();

  // HR accepts it, ticks the checklist and completes the departure.
  const page = await (await browser.newContext()).newPage();
  await signIn(page, `owner-${id}@e2e.test`);
  await page.goto(`/${slug}/hr/offboarding`);
  await page.getByRole('link', { name: /Rithy Off/ }).click();
  await page.getByRole('button', { name: 'Accept the resignation' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Accept' }).click();
  await expect(page.getByText('Resignation accepted')).toBeVisible();
  await page.getByRole('checkbox', { name: 'Return laptop' }).click();
  await expect(page.getByText('1 of 2 done')).toBeVisible();
  await page.getByRole('checkbox', { name: 'Remove email access' }).click();
  await expect(page.getByText('2 of 2 done')).toBeVisible();
  page.on('dialog', (d) => d.accept());
  await page.getByRole('button', { name: 'Complete: they have left' }).click();
  await expect(page.getByText('Departure completed')).toBeVisible();
  await expect(page.getByText('Left', { exact: true })).toBeVisible();

  // Their access has ended.
  const ctx = await request.get(`${API}/api/orgs/${slug}/context`, { headers: { Authorization: `Bearer ${member.accessToken}` } });
  expect(ctx.status()).toBe(404);
});
