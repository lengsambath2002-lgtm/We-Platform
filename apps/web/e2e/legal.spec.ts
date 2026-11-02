import { expect, test } from '@playwright/test';

test('sign-up links to the Terms of Service and the Privacy Policy, readable in Khmer', async ({ page }) => {
  await page.goto('/sign-up');
  await expect(page.getByText('By creating an account you agree to the')).toBeVisible();
  await page.getByRole('navigation', { name: 'Legal' }).getByRole('link', { name: 'Terms of Service' }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Terms of Service' })).toBeVisible();
  await expect(page.getByRole('heading', { name: '3. Plans, invoices and payment' })).toBeVisible();

  await page.getByRole('link', { name: 'Privacy Policy' }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Privacy Policy' })).toBeVisible();
  await page.getByRole('radio', { name: 'ខ្មែរ' }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'គោលការណ៍ឯកជនភាព' })).toBeVisible();
  await page.getByRole('radio', { name: 'English' }).click();
});
