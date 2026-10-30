import { type APIRequestContext, expect, test } from '@playwright/test';

const API = process.env.API_URL ?? 'http://localhost:8280';
const PASSWORD = 'E2e-pass-2026';

async function signUp(request: APIRequestContext, name: string, email: string) {
  const res = await request.post(`${API}/api/auth/sign-up`, { data: { name, email, password: PASSWORD }, headers: { 'X-Client': 'mobile' } });
  expect(res.ok()).toBeTruthy();
  return (await res.json()).data as { accessToken: string; user: { id: string } };
}

const inDays = (n: number) => new Date(Date.now() + n * 86400000).toISOString().slice(0, 10);

test('HR keeps personal details, a contract and a document; the dashboard warns before they expire', async ({ page, request }, info) => {
  const id = `${Date.now().toString(36)}${info.project.name[0]}e`;
  const owner = await signUp(request, 'Owner Rec', `owner-${id}@e2e.test`);
  const member = await signUp(request, 'Sophea Rec', `member-${id}@e2e.test`);
  const slug = `rec-${id}`;
  const auth = { Authorization: `Bearer ${owner.accessToken}` };
  expect((await request.post(`${API}/api/orgs`, { data: { name: `Rec ${id}`, slug }, headers: auth })).ok()).toBeTruthy();
  const inv = await request.post(`${API}/api/orgs/${slug}/invitations`, { data: { email: `member-${id}@e2e.test`, role: 'MEMBER' }, headers: auth });
  const token = ((await inv.json()).data.link as string).split('/').pop()!;
  expect((await request.post(`${API}/api/invitations/${token}/accept`, { headers: { Authorization: `Bearer ${member.accessToken}` } })).ok()).toBeTruthy();
  expect((await request.put(`${API}/api/orgs/${slug}/modules/hr`, { data: { enabled: true }, headers: auth })).ok()).toBeTruthy();

  await page.goto('/sign-in');
  await page.getByLabel('Email').fill(`owner-${id}@e2e.test`);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL(/\/overview$/);
  await page.goto(`/${slug}/hr/people/${member.user.id}`);

  // Personal details.
  await page.getByRole('region', { name: 'Personal details' }).getByRole('button', { name: 'Edit' }).click();
  let dlg = page.getByRole('dialog');
  await dlg.getByLabel('Nickname').fill('Phea');
  await dlg.getByLabel('ID number').fill('N7654321');
  await dlg.getByLabel('ID expires').fill(inDays(12));
  await dlg.getByRole('button', { name: 'Save' }).click();
  await expect(page.getByText('Personal details saved')).toBeVisible();
  await expect(page.getByRole('region', { name: 'Personal details' })).toContainText('N7654321');

  // A fixed-term contract ending soon.
  await page.getByRole('button', { name: 'Add contract' }).click();
  dlg = page.getByRole('dialog');
  await dlg.getByRole('combobox', { name: 'Kind' }).click();
  await page.getByRole('option', { name: 'Fixed-term (FDC)' }).click();
  await dlg.getByLabel('Starts').fill(inDays(-340));
  await dlg.getByLabel('Ends', { exact: true }).fill(inDays(20));
  await dlg.getByRole('button', { name: 'Save' }).click();
  await expect(page.getByText('Contract saved')).toBeVisible();
  await expect(page.getByRole('region', { name: 'Contracts' })).toContainText('Fixed-term (FDC)');

  // A document.
  await page.getByRole('button', { name: 'Add document' }).click();
  dlg = page.getByRole('dialog');
  await dlg.getByLabel('File').setInputFiles({ name: 'degree.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4 test') });
  await dlg.getByRole('combobox', { name: 'Kind' }).click();
  await page.getByRole('option', { name: 'Certificate' }).click();
  await dlg.getByRole('button', { name: 'Upload' }).click();
  await expect(page.getByText('Document added')).toBeVisible();
  await expect(page.getByRole('region', { name: 'Documents' })).toContainText('degree');

  // The dashboard warns about the contract and the ID.
  await page.goto(`/${slug}/hr/dashboard`);
  const soon = page.getByRole('region', { name: 'Expiring soon' });
  await expect(soon).toContainText('Contract ends');
  await expect(soon).toContainText('ID expires');
});
