import { expect, test } from '@playwright/test';

// The Google popup itself needs a real Google account; the API side is covered by FirebaseSignInTest.
test('sign-in and sign-up offer “Continue with Google” when Firebase is configured', async ({ page }) => {
  test.skip(!process.env.NEXT_PUBLIC_FIREBASE_API_KEY && !process.env.E2E_FIREBASE, 'Built without Firebase config');
  for (const path of ['/sign-in', '/sign-up']) {
    await page.goto(path);
    await expect(page.getByRole('button', { name: 'Continue with Google' })).toBeVisible();
  }
});
