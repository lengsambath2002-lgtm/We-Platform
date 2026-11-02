import { type APIRequestContext, expect, test } from '@playwright/test';

const API = process.env.API_URL ?? 'http://localhost:8280';
const PASSWORD = 'E2e-pass-2026';

async function signUp(request: APIRequestContext, name: string, email: string) {
  const res = await request.post(`${API}/api/auth/sign-up`, { data: { name, email, password: PASSWORD }, headers: { 'X-Client': 'mobile' } });
  expect(res.ok()).toBeTruthy();
  return (await res.json()).data as { accessToken: string; user: { id: string } };
}

test('an owner builds branch → department, adds a position and places a member', async ({ page, request }, info) => {
  page.on('dialog', (d) => d.accept());
  const id = `${Date.now().toString(36)}${info.project.name[0]}s`;
  const owner = await signUp(request, 'Owner Str', `owner-${id}@e2e.test`);
  const member = await signUp(request, 'Visal Str', `member-${id}@e2e.test`);
  const slug = `str-${id}`;
  const auth = { Authorization: `Bearer ${owner.accessToken}` };
  expect((await request.post(`${API}/api/orgs`, { data: { name: `Str ${id}`, slug }, headers: auth })).ok()).toBeTruthy();
  const inv = await request.post(`${API}/api/orgs/${slug}/invitations`, { data: { email: `member-${id}@e2e.test`, role: 'MEMBER' }, headers: auth });
  const token = ((await inv.json()).data.link as string).split('/').pop()!;
  expect((await request.post(`${API}/api/invitations/${token}/accept`, { headers: { Authorization: `Bearer ${member.accessToken}` } })).ok()).toBeTruthy();

  await page.goto('/sign-in');
  await page.getByLabel('Email').fill(`owner-${id}@e2e.test`);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL(/\/overview$/);

  // A branch, and a department under it.
  await page.goto(`/${slug}/structure`);
  await expect(page.getByText('No units yet')).toBeVisible();
  await page.getByRole('button', { name: 'New unit' }).first().click();
  await page.getByRole('dialog').getByLabel('Name').fill('Phnom Penh');
  await page.getByRole('dialog').getByRole('button', { name: 'Save' }).click();
  await expect(page.getByRole('heading', { name: 'Phnom Penh' })).toBeVisible();
  await page.getByRole('button', { name: 'Add sub-unit' }).click();
  await page.getByRole('dialog').getByLabel('Name').fill('Sales');
  await page.getByRole('dialog').getByRole('button', { name: 'Save' }).click();
  await expect(page.getByText('Unit saved')).toBeVisible();

  // A position in the department.
  await page.getByRole('list', { name: 'Units' }).getByRole('button', { name: /Sales/ }).click();
  await expect(page.getByRole('heading', { name: 'Sales' })).toBeVisible();
  await page.getByRole('button', { name: 'Add position' }).click();
  await page.getByRole('dialog').getByLabel('Title').fill('Sales Officer');
  await page.getByRole('dialog').getByLabel('Headcount').fill('2');
  await page.getByRole('dialog').getByRole('button', { name: 'Save' }).click();
  await expect(page.getByText('Position saved')).toBeVisible();

  // The member is placed from Members & roles; the position brings its unit.
  await page.goto(`/${slug}/admin/members`);
  await page.getByRole('button', { name: 'Place Visal Str' }).click();
  const dlg = page.getByRole('dialog');
  await dlg.getByRole('combobox', { name: 'Position' }).first().click();
  await page.getByRole('option', { name: /Sales Officer/ }).click();
  await dlg.getByRole('button', { name: 'Save' }).click();
  await expect(page.getByText('Saved where Visal Str works')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Place Visal Str' })).toContainText('Sales Officer · Sales');

  // The structure shows the filled position and who sits where.
  await page.goto(`/${slug}/structure`);
  await page.getByRole('tab', { name: 'Positions' }).click();
  const officer = page.getByRole('list', { name: 'Positions' }).getByRole('listitem').filter({ hasText: 'Sales Officer' });
  await expect(officer).toContainText('1/2');
  await expect(officer).toContainText('1 open');
  await page.goto(`/${slug}/teams`);
  await expect(page.getByRole('list', { name: 'People' }).getByRole('listitem').filter({ hasText: 'Visal Str' })).toContainText('Phnom Penh › Sales');
  await page.goto(`/${slug}/structure`);

  // The owner adds themselves to the branch from the unit itself, as also working there.
  await page.getByRole('tab', { name: 'Units' }).click();
  await page.getByRole('list', { name: 'Units' }).getByRole('button', { name: /Phnom Penh/ }).click();
  await page.getByRole('button', { name: 'Add people' }).first().click();
  const add = page.getByRole('dialog');
  await expect(add.getByRole('checkbox', { name: /Visal Str/ })).toBeEnabled();
  await add.getByRole('checkbox', { name: /Owner Str/ }).click();
  await add.getByRole('button', { name: 'Add 1 person' }).click();
  await expect(page.getByText('1 person added to Phnom Penh')).toBeVisible();
  await expect(page.getByRole('region', { name: 'Phnom Penh' }).getByText('Owner Str')).toBeVisible();

  // The chart shows the branch with the department under it.
  await page.getByRole('tab', { name: 'Chart' }).click();
  await expect(page.getByRole('button', { name: 'Open Phnom Penh' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Open Sales' })).toBeVisible();
});
