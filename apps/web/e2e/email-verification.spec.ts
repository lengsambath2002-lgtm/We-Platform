import { expect, test } from '@playwright/test';

const API = process.env.API_URL ?? 'http://localhost:8280';
const PASSWORD = 'E2e-pass-2026';

test('a new account is asked to confirm its email and can send the link again; a used link is refused', async ({ page, request }, info) => {
  const id = `${Date.now().toString(36)}${info.project.name[0]}v`;
  const email = `verify-${id}@e2e.test`;
  expect((await request.post(`${API}/api/auth/sign-up`, { data: { name: 'Verify Me', email, password: PASSWORD }, headers: { 'X-Client': 'mobile' } })).ok()).toBeTruthy();

  await page.goto('/sign-in');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL(/\/onboarding$/);
  await expect(page.getByText(`we sent a link to ${email}`)).toBeVisible();
  await page.getByRole('button', { name: 'Send the link again' }).click();
  await expect(page.getByText(`Sent to ${email}`)).toBeVisible();

  await page.goto('/verify-email?token=not-a-real-link');
  await expect(page.getByText('This link has expired or was already used')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Continue' })).toBeVisible();
});
