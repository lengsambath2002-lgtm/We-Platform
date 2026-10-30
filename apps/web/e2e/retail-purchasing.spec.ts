import { type APIRequestContext, expect, test } from '@playwright/test';

const API = process.env.API_URL ?? 'http://localhost:8280';
const PASSWORD = 'E2e-pass-2026';

async function signUp(request: APIRequestContext, name: string, email: string) {
  const res = await request.post(`${API}/api/auth/sign-up`, { data: { name, email, password: PASSWORD }, headers: { 'X-Client': 'mobile' } });
  expect(res.ok()).toBeTruthy();
  return (await res.json()).data as { accessToken: string; user: { id: string } };
}

test('an owner adds a supplier, orders, records a delivery and a payment', async ({ page, request }, info) => {
  page.on('dialog', (d) => d.accept());
  const id = `${Date.now().toString(36)}${info.project.name[0]}p`;
  const owner = await signUp(request, 'Owner Shop', `owner-${id}@e2e.test`);
  const slug = `shop-${id}`;
  const auth = { Authorization: `Bearer ${owner.accessToken}` };
  expect((await request.post(`${API}/api/orgs`, { data: { name: `Shop ${id}`, slug }, headers: auth })).ok()).toBeTruthy();
  expect((await request.put(`${API}/api/orgs/${slug}/modules/retail`, { data: { enabled: true }, headers: auth })).ok()).toBeTruthy();

  await page.goto('/sign-in');
  await page.getByLabel('Email').fill(`owner-${id}@e2e.test`);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL(/\/overview$/);

  // A supplier.
  await page.goto(`/${slug}/retail/suppliers`);
  await page.getByRole('button', { name: 'New supplier' }).click();
  await page.getByRole('dialog').getByLabel('Name').fill('Phnom Penh Wholesale');
  await page.getByRole('dialog').getByRole('button', { name: 'Save' }).click();
  await page.waitForURL(/\/retail\/suppliers\/[0-9a-f-]+$/);

  // An order from them: 10 × 3.50.
  await page.getByRole('button', { name: 'New purchase order' }).click();
  const dlg = page.getByRole('dialog');
  await dlg.getByLabel('Item').fill('Coffee beans 1 kg');
  await dlg.getByLabel('Qty').fill('10');
  await dlg.getByLabel('Unit cost (USD)').fill('3.50');
  await expect(dlg.getByText('$35.00')).toBeVisible();
  await dlg.getByRole('button', { name: 'Save' }).click();
  await page.waitForURL(/\/retail\/purchase-orders\/[0-9a-f-]+$/);
  await page.getByRole('button', { name: 'Place order' }).click();
  await expect(page.getByRole('heading', { name: 'PO-0001' })).toBeVisible();

  // Everything arrives; it waits to be put into VirtualBiz stock, then is ticked off.
  await page.getByRole('button', { name: 'Record delivery' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Record delivery' }).click();
  await expect(page.getByText('Delivery recorded')).toBeVisible();
  await expect(page.getByText('Received', { exact: true }).first()).toBeVisible();
  await page.getByRole('checkbox', { name: 'In stock' }).click();
  await expect(page.getByRole('checkbox', { name: 'In stock' })).toBeChecked();

  // Paid in full.
  await page.getByRole('button', { name: 'Record payment' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Record payment' }).click();
  await expect(page.getByText('Payment recorded')).toBeVisible();
  await page.goto(`/${slug}/retail/purchasing`);
  await expect(page.getByText('Owed to suppliers')).toBeVisible();
  await expect(page.getByRole('row').filter({ hasText: 'Phnom Penh Wholesale' })).toContainText('$35.00');
});
