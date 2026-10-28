import { type Page, expect, test } from '@playwright/test';

const API = process.env.API_URL ?? 'http://localhost:8280';
const PASSWORD = 'E2e-pass-2026';

async function signIn(page: Page, email: string) {
  await page.goto('/sign-in');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: /sign in/i }).click();
}

test('an admin switches Business on, adds a customer and a product, issues an invoice, records the payment and prints it', async ({ page, request }, info) => {
  const id = `${Date.now().toString(36)}${info.project.name[0]}b`;
  const email = `owner-${id}@e2e.test`;
  const res = await request.post(`${API}/api/auth/sign-up`, { data: { name: 'Owner Biz', email, password: PASSWORD }, headers: { 'X-Client': 'mobile' } });
  expect(res.ok()).toBeTruthy();
  const token = (await res.json()).data.accessToken as string;
  const slug = `biz-${id}`;
  expect((await request.post(`${API}/api/orgs`, { data: { name: `Biz ${id}`, slug }, headers: { Authorization: `Bearer ${token}` } })).ok()).toBeTruthy();

  // The owner switches General Business on in Admin → Modules; the module page now leads to the business home.
  await signIn(page, email);
  await page.waitForURL(/\/overview$/);
  await page.goto(`/${slug}/admin/modules`);
  await page.getByRole('switch', { name: 'Use General Business' }).click();
  await expect(page.getByText('General Business switched on')).toBeVisible();
  await page.goto(`/${slug}/m/business`);
  await page.waitForURL(new RegExp(`/${slug}/business$`));
  await expect(page.getByRole('heading', { name: 'Business', exact: true })).toBeVisible();

  // A customer.
  await page.goto(`/${slug}/business/customers`);
  await page.getByRole('button', { name: 'New customer' }).first().click();
  let dialog = page.getByRole('dialog');
  await dialog.getByLabel('Company name').fill('Angkor Coffee Co.');
  await dialog.getByLabel('Phone').fill('+855 12 345 678');
  await dialog.getByLabel('VAT / TIN number').fill('K001-901234567');
  await dialog.getByRole('button', { name: 'Save' }).click();
  await expect(page.getByText('Customer saved')).toBeVisible();
  await expect(page.getByRole('link', { name: /Angkor Coffee Co\./ }).first()).toBeVisible();

  // A product at $25.00 with VAT.
  await page.goto(`/${slug}/business/products`);
  await page.getByRole('button', { name: 'New product' }).first().click();
  dialog = page.getByRole('dialog');
  await dialog.getByLabel('Name (English)').fill('Coffee beans 1 kg');
  await dialog.getByLabel('Unit', { exact: true }).fill('kg');
  await dialog.getByLabel('Unit price (USD)').fill('25.00');
  await dialog.getByRole('button', { name: 'Save' }).click();
  await expect(page.getByText('Product saved')).toBeVisible();

  // A draft invoice: 2 kg → $50.00 + VAT 10% = $55.00, shown live while typing.
  await page.goto(`/${slug}/business/invoices/new`);
  await page.getByLabel('Customer', { exact: true }).click();
  await page.getByRole('option', { name: 'Angkor Coffee Co.' }).click();
  await page.getByLabel('Product', { exact: true }).click();
  await page.getByRole('option', { name: /Coffee beans 1 kg/ }).click();
  await page.getByLabel('Qty').fill('2');
  await expect(page.getByText('$55.00').first()).toBeVisible();
  await page.getByRole('button', { name: 'Save draft' }).click();
  await page.waitForURL(/\/business\/invoices\/[0-9a-f-]{36}$/);
  await expect(page.getByRole('heading', { name: 'Draft invoice' })).toBeVisible();

  // Issue it: it gets INV-0001.
  await page.getByRole('button', { name: 'Issue' }).click();
  await page.getByRole('alertdialog').getByRole('button', { name: 'Issue' }).click();
  await expect(page.getByRole('heading', { name: 'INV-0001' })).toBeVisible();
  await expect(page.getByText('Issued', { exact: true }).first()).toBeVisible();

  // Record the payment (the amount starts at the balance): paid.
  await page.getByRole('button', { name: 'Record payment' }).click();
  dialog = page.getByRole('dialog');
  await expect(dialog.getByLabel('Amount (USD)')).toHaveValue('55.00');
  await dialog.getByLabel('Method').click();
  await page.getByRole('option', { name: 'KHQR' }).click();
  await dialog.getByRole('button', { name: 'Record payment' }).click();
  await expect(page.getByText('Paid in full', { exact: true })).toBeVisible();
  await expect(page.getByText('Paid', { exact: true }).first()).toBeVisible();
  await expect(page.getByText(/recorded a payment of \$55\.00 \(KHQR\)/)).toBeVisible();

  // The print view: the company, the customer, the totals in USD and riel, and the browser's print button.
  await page.getByRole('link', { name: 'Print' }).click();
  await page.waitForURL(/\/print$/);
  const sheet = page.getByRole('article', { name: 'Invoice for printing' });
  await expect(sheet.getByText('INVOICE', { exact: true })).toBeVisible();
  await expect(sheet.getByText('INV-0001')).toBeVisible();
  await expect(sheet.getByText('Angkor Coffee Co.')).toBeVisible();
  await expect(sheet.getByText('225,500 ៛')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Print / Save as PDF' })).toBeVisible();

  // The business home counts the sale.
  await page.goto(`/${slug}/business`);
  await expect(page.getByText('Sales this month')).toBeVisible();
  await expect(page.getByRole('link', { name: 'INV-0001' }).first()).toBeVisible();
});
