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

test('HR opens a job; the hiring manager adds a candidate and interviews them; HR hires and onboarding starts', async ({ page, request }, info) => {
  page.on('dialog', (d) => d.accept());
  const id = `${Date.now().toString(36)}${info.project.name[0]}r`;
  const owner = await signUp(request, 'Owner Rec', `owner-${id}@e2e.test`);
  const manager = await signUp(request, 'Bopha Manager', `manager-${id}@e2e.test`);
  const slug = `rec-${id}`;
  const auth = { Authorization: `Bearer ${owner.accessToken}` };
  expect((await request.post(`${API}/api/orgs`, { data: { name: `Rec ${id}`, slug }, headers: auth })).ok()).toBeTruthy();
  const inv = await request.post(`${API}/api/orgs/${slug}/invitations`, { data: { email: `manager-${id}@e2e.test`, role: 'MEMBER' }, headers: auth });
  const token = ((await inv.json()).data.link as string).split('/').pop()!;
  expect((await request.post(`${API}/api/invitations/${token}/accept`, { headers: { Authorization: `Bearer ${manager.accessToken}` } })).ok()).toBeTruthy();
  expect((await request.put(`${API}/api/orgs/${slug}/modules/hr`, { data: { enabled: true }, headers: auth })).ok()).toBeTruthy();

  // HR opens a position with Bopha as hiring manager.
  await signIn(page, `owner-${id}@e2e.test`);
  await page.goto(`/${slug}/hr/recruitment`);
  await page.getByRole('button', { name: 'New opening' }).click();
  const job = page.getByRole('dialog');
  await job.getByLabel('Job title').fill('Sales officer');
  await job.getByLabel('Department').fill('Sales');
  await job.getByLabel('Hiring manager').click();
  await page.getByRole('option', { name: 'Bopha Manager' }).click();
  await job.getByRole('button', { name: 'Save' }).click();
  await page.waitForURL(/\/hr\/recruitment\/jobs\//);
  await expect(page.getByRole('heading', { name: 'Sales officer' })).toBeVisible();

  // The hiring manager sees Recruitment, adds a candidate and schedules an interview with themselves.
  await page.context().clearCookies();
  await signIn(page, `manager-${id}@e2e.test`);
  await page.goto(`/${slug}/hr/recruitment`);
  await page.getByRole('link', { name: /Sales officer/ }).click();
  await page.getByRole('button', { name: 'Add candidate' }).click();
  const cand = page.getByRole('dialog');
  await cand.getByLabel('Name').fill('Chenda Lim');
  await cand.getByLabel('Email').fill(`chenda-${id}@e2e.test`);
  await cand.getByRole('button', { name: 'Save' }).click();
  await page.waitForURL(/\/hr\/recruitment\/candidates\//);
  await page.getByRole('button', { name: 'Schedule interview' }).click();
  const iv = page.getByRole('dialog');
  await iv.getByLabel('Bopha Manager').check();
  await iv.getByRole('button', { name: 'Schedule interview' }).click();
  await expect(page.getByText('Interview scheduled')).toBeVisible();
  await expect(page.getByText('Interview', { exact: true }).first()).toBeVisible();
  await page.getByRole('radio', { name: 'Strong yes' }).click();
  await page.getByRole('button', { name: 'Give feedback' }).click();
  await expect(page.getByText('Feedback saved')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Hire' })).toHaveCount(0);

  // HR hires, inviting the candidate; onboarding starts.
  await page.context().clearCookies();
  await signIn(page, `owner-${id}@e2e.test`);
  await page.goto(`/${slug}/hr/recruitment`);
  await page.getByRole('link', { name: /Sales officer/ }).click();
  await page.getByRole('link', { name: /Chenda Lim/ }).click();
  await expect(page.getByText('Strong yes')).toBeVisible();
  await page.getByRole('button', { name: 'Hire' }).click();
  const hire = page.getByRole('dialog');
  await hire.getByLabel('Start date').fill(new Date(Date.now() + 14 * 86400000).toISOString().slice(0, 10));
  await hire.getByRole('button', { name: 'Hire' }).click();
  await expect(page.getByText('Chenda Lim is hired')).toBeVisible();
  await expect(page.getByText("Invited; they haven't joined yet.")).toBeVisible();
  const step = page.getByRole('checkbox', { name: 'Sign the employment contract' });
  await step.click();
  await expect(step).toBeChecked();
  await page.goto(`/${slug}/hr/recruitment?tab=hires`);
  await expect(page.getByText('1 of 6 steps')).toBeVisible();
});
