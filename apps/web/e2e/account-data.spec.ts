import { type APIRequestContext, type Page, expect, test } from '@playwright/test';

const API = process.env.API_URL ?? 'http://localhost:8280';
const PASSWORD = 'E2e-pass-2026';

async function signUp(request: APIRequestContext, name: string, email: string) {
  const res = await request.post(`${API}/api/auth/sign-up`, { data: { name, email, password: PASSWORD }, headers: { 'X-Client': 'mobile' } });
  expect(res.ok()).toBeTruthy();
  return (await res.json()).data as { accessToken: string };
}

async function signIn(page: Page, email: string) {
  await page.goto('/sign-in');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL(/\/(overview|onboarding)$/);
}

test('an owner downloads all the organisation’s data; a member deletes their account', async ({ page, browser, request }, info) => {
  const id = `${Date.now().toString(36)}${info.project.name[0]}x`;
  const owner = await signUp(request, 'Owner Export', `owner-${id}@e2e.test`);
  const slug = `exp-${id}`;
  const auth = { Authorization: `Bearer ${owner.accessToken}` };
  expect((await request.post(`${API}/api/orgs`, { data: { name: `Export ${id}`, slug }, headers: auth })).ok()).toBeTruthy();
  const member = await signUp(request, 'Leaving Member', `leaving-${id}@e2e.test`);
  const inv = await (await request.post(`${API}/api/orgs/${slug}/invitations`, { data: { email: `leaving-${id}@e2e.test`, role: 'MEMBER' }, headers: auth })).json();
  const token = (inv.data.link as string).split('/').pop();
  expect((await request.post(`${API}/api/invitations/${token}/accept`, { headers: { Authorization: `Bearer ${member.accessToken}` } })).ok()).toBeTruthy();

  await signIn(page, `owner-${id}@e2e.test`);
  await page.goto(`/${slug}/admin/settings`);
  const download = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Download export' }).click();
  expect((await download).suggestedFilename()).toMatch(new RegExp(`^${slug}-export-\\d{4}-\\d{2}-\\d{2}\\.zip$`));

  const leaving = await (await browser.newContext()).newPage();
  await signIn(leaving, `leaving-${id}@e2e.test`);
  await leaving.goto('/account');
  await leaving.getByRole('button', { name: 'Delete my account' }).click();
  const dialog = leaving.getByRole('dialog');
  await dialog.getByLabel('Password').fill(PASSWORD);
  await dialog.getByLabel('Type DELETE to confirm').fill('DELETE');
  await dialog.getByRole('button', { name: 'Delete my account' }).click();
  await expect(leaving.getByText('Your account was deleted.')).toBeVisible();
  await leaving.getByLabel('Email').fill(`leaving-${id}@e2e.test`);
  await leaving.getByLabel('Password').fill(PASSWORD);
  await leaving.getByRole('button', { name: /sign in/i }).click();
  await expect(leaving.getByRole('alert')).toBeVisible();

  // The owner can't: they hand the organisation over first.
  await page.goto('/account');
  await expect(page.getByText(`First hand these organisations to another owner, or delete them: Export ${id}`)).toBeVisible();
  await expect(page.getByRole('button', { name: 'Delete my account' })).toBeDisabled();
});
