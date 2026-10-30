import { type APIRequestContext, type Page, expect, test } from '@playwright/test';

const API = process.env.API_URL ?? 'http://localhost:8280';
const PASSWORD = 'E2e-pass-2026';

async function signUp(request: APIRequestContext, name: string, email: string) {
  const res = await request.post(`${API}/api/auth/sign-up`, { data: { name, email, password: PASSWORD }, headers: { 'X-Client': 'mobile' } });
  expect(res.ok()).toBeTruthy();
  return (await res.json()).data as { accessToken: string; user: { id: string } };
}

async function signIn(page: Page, email: string) {
  await page.context().clearCookies();
  await page.goto('/sign-in');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL(/\/overview$/);
}

async function rateAll(page: Page, stars: number) {
  await page.getByRole('radiogroup').first().waitFor();
  for (const group of await page.getByRole('radiogroup').all()) {
    await group.getByRole('radio', { name: `${stars} out of 5` }).click();
  }
}

test('a review goes from self-assessment to the manager and back; training is signed up for and completed', async ({ page, request }, info) => {
  page.on('dialog', (d) => d.accept());
  const id = `${Date.now().toString(36)}${info.project.name[0]}d`;
  const owner = await signUp(request, 'Owner Dev', `owner-${id}@e2e.test`);
  const manager = await signUp(request, 'Manager Dev', `manager-${id}@e2e.test`);
  const member = await signUp(request, 'Kanha Dev', `member-${id}@e2e.test`);
  const slug = `dev-${id}`;
  const auth = { Authorization: `Bearer ${owner.accessToken}` };
  expect((await request.post(`${API}/api/orgs`, { data: { name: `Dev ${id}`, slug }, headers: auth })).ok()).toBeTruthy();
  for (const [who, email] of [[manager, `manager-${id}@e2e.test`], [member, `member-${id}@e2e.test`]] as const) {
    const inv = await request.post(`${API}/api/orgs/${slug}/invitations`, { data: { email, role: 'MEMBER' }, headers: auth });
    const token = ((await inv.json()).data.link as string).split('/').pop()!;
    expect((await request.post(`${API}/api/invitations/${token}/accept`, { headers: { Authorization: `Bearer ${who.accessToken}` } })).ok()).toBeTruthy();
  }
  expect((await request.put(`${API}/api/orgs/${slug}/modules/hr`, { data: { enabled: true }, headers: auth })).ok()).toBeTruthy();
  expect((await request.put(`${API}/api/orgs/${slug}/hr/people/${member.user.id}`, { data: { managerId: manager.user.id }, headers: auth })).ok()).toBeTruthy();

  // HR creates and launches a cycle with the default questions, and publishes a course.
  await signIn(page, `owner-${id}@e2e.test`);
  await page.goto(`/${slug}/hr/reviews?tab=cycles`);
  await page.getByRole('button', { name: 'New review cycle' }).click();
  await page.getByRole('dialog').getByLabel('Due').fill(new Date(Date.now() + 30 * 86400000).toISOString().slice(0, 10));
  await page.getByRole('dialog').getByRole('button', { name: 'Save' }).click();
  await page.waitForURL(/\/hr\/reviews\/cycles\//);
  await page.getByRole('button', { name: 'Launch' }).click();
  await expect(page.getByText('Review cycle launched')).toBeVisible();
  await expect(page.getByRole('row').filter({ hasText: 'Kanha Dev' })).toContainText('Manager Dev');
  await page.goto(`/${slug}/hr/training`);
  await page.getByRole('button', { name: 'New course' }).click();
  await page.getByRole('dialog').getByLabel('Title').fill('Customer service basics');
  await page.getByRole('dialog').getByRole('button', { name: 'Save' }).click();
  await page.waitForURL(/\/hr\/training\/[0-9a-f-]+$/);

  // The member assesses themselves and signs up for the course.
  await signIn(page, `member-${id}@e2e.test`);
  await page.goto(`/${slug}/hr/reviews`);
  await page.getByRole('link', { name: /annual review/ }).click();
  await rateAll(page, 4);
  await page.getByRole('button', { name: 'Send to reviewer' }).click();
  await expect(page.getByText('Self-assessment sent')).toBeVisible();
  await page.goto(`/${slug}/hr/training`);
  await page.getByRole('link', { name: /Customer service basics/ }).click();
  await page.getByRole('button', { name: 'Sign up' }).click();
  await expect(page.getByText('You are enrolled on this course.')).toBeVisible();

  // The manager reviews and shares.
  await signIn(page, `manager-${id}@e2e.test`);
  await page.goto(`/${slug}/hr/reviews?tab=to-review`);
  await page.getByRole('link', { name: /Kanha Dev/ }).click();
  await rateAll(page, 5);
  await page.getByRole('button', { name: 'Share with the person' }).click();
  await expect(page.getByText('Review shared')).toBeVisible();

  // The member reads and acknowledges it; HR marks the course completed.
  await signIn(page, `member-${id}@e2e.test`);
  await page.goto(`/${slug}/hr/reviews`);
  await page.getByRole('link', { name: /annual review/ }).click();
  await expect(page.getByText('Overall rating')).toBeVisible();
  await page.getByRole('button', { name: 'Acknowledge' }).click();
  await expect(page.getByText('Acknowledged', { exact: true })).toBeVisible();
  await signIn(page, `owner-${id}@e2e.test`);
  await page.goto(`/${slug}/hr/training`);
  await page.getByRole('link', { name: /Customer service basics/ }).click();
  await page.getByRole('button', { name: 'Completed' }).click();
  await expect(page.getByText('Marked completed')).toBeVisible();
});
