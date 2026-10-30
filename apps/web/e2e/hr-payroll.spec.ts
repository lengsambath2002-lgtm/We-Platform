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

/** Last month as yyyy-MM. */
function lastMonth() {
  const d = new Date();
  d.setDate(1);
  d.setMonth(d.getMonth() - 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

test('HR sets a salary, prepares a month, adds an allowance, approves and pays it; the employee opens their payslip', async ({ page, request }, info) => {
  page.on('dialog', (d) => d.accept());
  const id = `${Date.now().toString(36)}${info.project.name[0]}p`;
  const owner = await signUp(request, 'Owner Pay', `owner-${id}@e2e.test`);
  const member = await signUp(request, 'Sophea Pay', `member-${id}@e2e.test`);
  const slug = `pay-${id}`;
  const auth = { Authorization: `Bearer ${owner.accessToken}` };
  expect((await request.post(`${API}/api/orgs`, { data: { name: `Pay ${id}`, slug }, headers: auth })).ok()).toBeTruthy();
  const inv = await request.post(`${API}/api/orgs/${slug}/invitations`, { data: { email: `member-${id}@e2e.test`, role: 'MEMBER' }, headers: auth });
  const token = ((await inv.json()).data.link as string).split('/').pop()!;
  expect((await request.post(`${API}/api/invitations/${token}/accept`, { headers: { Authorization: `Bearer ${member.accessToken}` } })).ok()).toBeTruthy();
  expect((await request.put(`${API}/api/orgs/${slug}/modules/hr`, { data: { enabled: true }, headers: auth })).ok()).toBeTruthy();

  // HR sets the member's pay.
  await signIn(page, `owner-${id}@e2e.test`);
  await page.goto(`/${slug}/hr/payroll?tab=people`);
  await page.getByRole('button', { name: 'Pay: Sophea Pay' }).click();
  const pay = page.getByRole('dialog');
  await pay.getByLabel('Monthly salary (USD)').fill('600');
  await pay.getByLabel('Dependants').fill('1');
  await pay.getByLabel('Bank', { exact: true }).fill('ABA');
  await pay.getByLabel('Account number').fill('000 111 222');
  await pay.getByRole('button', { name: 'Save' }).click();
  await expect(page.getByText('Pay saved')).toBeVisible();

  // …prepares last month at 4,100 riel.
  await page.getByRole('tab', { name: 'Pay runs' }).click();
  await page.getByRole('button', { name: 'New pay run' }).click();
  const run = page.getByRole('dialog');
  await run.getByLabel('Month').fill(lastMonth());
  await run.getByLabel('Exchange rate (riel per US dollar)').fill('4100');
  await run.getByRole('button', { name: 'Prepare' }).click();
  await page.waitForURL(/\/hr\/payroll\/[0-9a-f-]+$/);
  const row = page.getByRole('row').filter({ hasText: 'Sophea Pay' });
  await expect(row).toContainText('$580.49');

  // …adds a transport allowance, approves and pays.
  await row.getByRole('button', { name: 'Adjust' }).click();
  const adj = page.getByRole('dialog');
  await adj.getByRole('button', { name: 'Add a line' }).click();
  await adj.getByLabel('Description').fill('Transport');
  await adj.getByLabel('Amount (USD)').fill('20');
  await adj.getByRole('button', { name: 'Save' }).click();
  await expect(page.getByText('Adjustments saved')).toBeVisible();
  await expect(row).toContainText('$620.00');
  await page.getByRole('button', { name: 'Approve' }).click();
  await expect(page.getByText('Pay run approved')).toBeVisible();
  await page.getByRole('button', { name: 'Mark paid' }).click();
  await expect(page.getByText('Marked paid; payslips sent')).toBeVisible();
  await expect(page.getByText('Paid', { exact: true }).first()).toBeVisible();

  // The member finds it under My payslips.
  await page.context().clearCookies();
  await signIn(page, `member-${id}@e2e.test`);
  await page.goto(`/${slug}/hr/payslips`);
  await page.getByRole('link', { name: /Net pay/ }).first().click();
  const slip = page.getByRole('article', { name: 'Payslip' });
  await expect(slip).toContainText('Sophea Pay');
  await expect(slip).toContainText('Transport');
  await expect(slip).toContainText('Tax on Salary');
  await expect(page.getByRole('button', { name: 'Print / Save as PDF' })).toBeVisible();
  // …and has no access to payroll itself.
  await page.goto(`/${slug}/hr/payroll`);
  await expect(page.getByText("You don't have access")).toBeVisible();
});
