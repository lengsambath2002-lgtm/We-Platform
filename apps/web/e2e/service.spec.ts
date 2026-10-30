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
}

/** yyyy-MM-ddT09:00 tomorrow, for a datetime-local input. */
function tomorrowAt(hour: number) {
  const d = new Date();
  d.setDate(d.getDate() + 1);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(hour)}:00`;
}

test('a dispatcher takes a request, sends a work order to a technician, who completes it with the customer’s sign-off', async ({ page, request }, info) => {
  test.setTimeout(300_000); // two people, a dozen pages compiled on first visit by the dev server
  const id = `${Date.now().toString(36)}${info.project.name[0]}s`;
  const owner = { name: 'Owner Service', email: `owner-${id}@e2e.test` };
  const tech = { name: `Sok Tech ${id}`, email: `tech-${id}@e2e.test` };
  const o = await signUp(request, owner.name, owner.email);
  const t = await signUp(request, tech.name, tech.email);
  const auth = (token: string) => ({ Authorization: `Bearer ${token}` });
  const slug = `svc-${id}`;
  expect((await request.post(`${API}/api/orgs`, { data: { name: `Service ${id}`, slug }, headers: auth(o.accessToken) })).ok()).toBeTruthy();
  const inv = await (await request.post(`${API}/api/orgs/${slug}/invitations`, { data: { email: tech.email, role: 'MEMBER' }, headers: auth(o.accessToken) })).json();
  const token = (inv.data.link as string).split('/').pop();
  expect((await request.post(`${API}/api/invitations/${token}/accept`, { headers: auth(t.accessToken) })).ok()).toBeTruthy();

  // The owner switches Service & Operations on; the module page now leads to the service dashboard.
  await signIn(page, owner.email);
  await page.waitForURL(/\/overview$/);
  await page.goto(`/${slug}/admin/modules`);
  await page.getByRole('switch', { name: 'Use Service & Operations' }).click();
  await expect(page.getByText('Service & Operations switched on')).toBeVisible();
  await page.goto(`/${slug}/m/service`);
  await page.waitForURL(new RegExp(`/${slug}/service$`));
  await expect(page.getByRole('heading', { name: 'Service & Operations', exact: true })).toBeVisible();
  await expect(page.getByText('SLA breached')).toBeVisible({ timeout: 30_000 });

  // The technician joins the service staff.
  await page.goto(`/${slug}/service/technicians`);
  await page.getByRole('button', { name: 'Add to staff' }).first().click();
  let dialog = page.getByRole('dialog');
  await dialog.getByLabel('Member').click();
  await page.getByRole('option', { name: tech.name }).click();
  await dialog.getByLabel('Skills').fill('Air conditioning');
  await dialog.getByRole('button', { name: 'Save' }).click();
  await expect(page.getByText('Staff saved')).toBeVisible();
  await expect(page.getByText(tech.name)).toBeVisible();

  // A customer.
  await page.goto(`/${slug}/service/customers`);
  await page.getByRole('button', { name: 'New customer' }).first().click();
  dialog = page.getByRole('dialog');
  await dialog.getByLabel('Name').fill('Angkor Clinic');
  await dialog.getByLabel('Phone').fill('+855 12 345 678');
  await dialog.getByRole('button', { name: 'Save' }).click();
  await expect(page.getByText('Customer saved')).toBeVisible();

  // A service request: TCK-0001, on track.
  await page.goto(`/${slug}/service/requests`);
  await page.getByRole('button', { name: 'New request' }).first().click();
  dialog = page.getByRole('dialog');
  await dialog.getByLabel('What is wrong').fill('Air conditioner not cooling');
  await dialog.getByLabel('Priority').click();
  await page.getByRole('option', { name: 'High' }).click();
  await dialog.getByLabel('Customer').click();
  await page.getByRole('option', { name: 'Angkor Clinic' }).click();
  await dialog.getByRole('button', { name: 'Create ticket' }).click();
  await page.waitForURL(/\/service\/tickets\/[0-9a-f-]{36}$/);
  await expect(page.getByRole('heading', { name: 'TCK-0001 · Air conditioner not cooling' })).toBeVisible({ timeout: 30_000 });
  await expect(page.getByText('On track').first()).toBeVisible();
  await page.getByRole('button', { name: 'Move to Triaged' }).click();
  await expect(page.getByText('Moved to Triaged')).toBeVisible();

  // A work order from the ticket: the technician, tomorrow 09:00–11:00, a checklist, labour and a part.
  await page.getByRole('link', { name: 'Create work order' }).click();
  await page.waitForURL(/\/work-orders\/new\?ticket=/);
  await expect(page.getByText('From ticket TCK-0001')).toBeVisible({ timeout: 30_000 });
  await page.getByLabel('Starts').fill(tomorrowAt(9));
  await page.getByLabel('Ends').fill(tomorrowAt(11));
  await page.getByRole('checkbox', { name: tech.name }).click();
  await page.getByLabel('Checklist').fill('Check refrigerant pressure\nClean filters');
  await page.getByRole('button', { name: 'Add labour' }).click();
  await page.getByLabel('Hours').fill('1.5');
  await page.getByRole('button', { name: 'Add part' }).click();
  await page.getByLabel('Description', { exact: true }).nth(1).fill('Capacitor 35µF');
  await page.getByLabel('Qty').fill('2');
  await page.getByLabel('Unit price (USD)').nth(1).fill('10.00');
  // $22.50 labour + $20.00 parts, shown live with riel.
  await expect(page.getByText('$42.50').first()).toBeVisible();
  await expect(page.getByText('174,300 ៛')).toBeVisible();
  await page.getByRole('button', { name: 'Create work order' }).click();
  await page.waitForURL(/\/service\/work-orders\/[0-9a-f-]{36}$/);
  await expect(page.getByRole('heading', { name: 'WO-0001 · Air conditioner not cooling' })).toBeVisible({ timeout: 30_000 });
  await page.getByRole('button', { name: 'Schedule' }).click();
  await page.getByRole('alertdialog').getByRole('button', { name: 'Schedule' }).click();
  await expect(page.getByText('Scheduled', { exact: true }).first()).toBeVisible();

  // Bookings stay in VirtualBiz.
  await page.goto(`/${slug}/service/bookings`);
  await expect(page.getByText('Bookings live in VirtualBiz')).toBeVisible({ timeout: 30_000 });

  // The technician: My work orders, start, tick the checklist, complete with the sign-off.
  await page.context().clearCookies();
  await signIn(page, tech.email);
  await page.waitForURL(/\/overview$/);
  await page.goto(`/${slug}/service/work-orders`);
  await expect(page.getByRole('heading', { name: 'My work orders' })).toBeVisible({ timeout: 30_000 });
  await page.getByRole('link', { name: /WO-0001/ }).first().click();
  await page.waitForURL(/\/service\/work-orders\/[0-9a-f-]{36}$/);
  await expect(page.getByRole('button', { name: 'Cancel work order' })).toHaveCount(0);
  await page.getByRole('button', { name: 'Start' }).click();
  await expect(page.getByText('In progress', { exact: true }).first()).toBeVisible();
  await page.getByRole('checkbox', { name: 'Check refrigerant pressure' }).click();
  await expect(page.getByText('Ticked').first()).toBeVisible();
  await page.getByRole('checkbox', { name: 'Clean filters' }).click();
  await expect(page.getByText('2/2')).toBeVisible();
  await page.getByRole('button', { name: 'Complete', exact: true }).click();
  dialog = page.getByRole('dialog');
  await dialog.getByLabel('What was done').fill('Replaced the capacitor and cleaned the filters.');
  await dialog.getByLabel('Signed off by (customer name)').fill('Dr. Chan Dara');
  await dialog.getByRole('button', { name: 'Complete' }).click();
  await expect(page.getByText('Signed off by Dr. Chan Dara', { exact: true })).toBeVisible({ timeout: 15_000 });
  await expect(page.getByText(/completed it, signed off by Dr\. Chan Dara/)).toBeVisible();
  // Technicians don't see the dispatcher's pages.
  await page.goto(`/${slug}/service/assets`);
  await expect(page.getByText("You don't have access")).toBeVisible({ timeout: 30_000 });
});
