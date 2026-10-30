import { type APIRequestContext, expect, test } from '@playwright/test';

const API = process.env.API_URL ?? 'http://localhost:8280';
const PASSWORD = 'E2e-pass-2026';

async function signUp(request: APIRequestContext, name: string, email: string) {
  const res = await request.post(`${API}/api/auth/sign-up`, { data: { name, email, password: PASSWORD }, headers: { 'X-Client': 'mobile' } });
  expect(res.ok()).toBeTruthy();
  return (await res.json()).data as { accessToken: string; user: { id: string } };
}

test('a manager going on a trip hands their approvals to a colleague', async ({ page, request }, info) => {
  const id = `${Date.now().toString(36)}${info.project.name[0]}d`;
  const owner = await signUp(request, 'Owner Del', `owner-${id}@e2e.test`);
  const lead = await signUp(request, 'Lead Del', `lead-${id}@e2e.test`);
  const member = await signUp(request, 'Member Del', `member-${id}@e2e.test`);
  const standIn = await signUp(request, 'Stand In Del', `standin-${id}@e2e.test`);
  const slug = `del-${id}`;
  const auth = { Authorization: `Bearer ${owner.accessToken}` };
  expect((await request.post(`${API}/api/orgs`, { data: { name: `Del ${id}`, slug }, headers: auth })).ok()).toBeTruthy();
  for (const [u, email] of [[lead, `lead-${id}`], [member, `member-${id}`], [standIn, `standin-${id}`]] as const) {
    const inv = await request.post(`${API}/api/orgs/${slug}/invitations`, { data: { email: `${email}@e2e.test`, role: 'MEMBER' }, headers: auth });
    const token = ((await inv.json()).data.link as string).split('/').pop()!;
    expect((await request.post(`${API}/api/invitations/${token}/accept`, { headers: { Authorization: `Bearer ${u.accessToken}` } })).ok()).toBeTruthy();
  }
  expect((await request.put(`${API}/api/orgs/${slug}/modules/hr`, { data: { enabled: true }, headers: auth })).ok()).toBeTruthy();
  expect((await request.put(`${API}/api/orgs/${slug}/hr/people/${member.user.id}`, { data: { managerId: lead.user.id }, headers: auth })).ok()).toBeTruthy();

  await page.goto('/sign-in');
  await page.getByLabel('Email').fill(`lead-${id}@e2e.test`);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL(/\/overview$/);

  await page.goto(`/${slug}/approvals`);
  await page.getByRole('button', { name: 'Set an acting approver' }).click();
  const dlg = page.getByRole('dialog');
  await dlg.getByRole('combobox', { name: 'Acting approver' }).click();
  await page.getByRole('option', { name: 'Stand In Del' }).click();
  const end = new Date(Date.now() + 5 * 86400000).toISOString().slice(0, 10);
  await dlg.getByLabel('To (included)').fill(end);
  await dlg.getByLabel('Reason (optional)').fill('Business trip');
  await dlg.getByRole('button', { name: 'Save' }).click();
  await expect(page.getByText('Acting approver set')).toBeVisible();
  await expect(page.getByText('Stand In Del approves for Lead Del')).toBeVisible();

  // The member's leave request now reaches the stand-in too.
  const types = await (await request.get(`${API}/api/orgs/${slug}/hr/leave-types`, { headers: auth })).json();
  const leave = await request.post(`${API}/api/orgs/${slug}/hr/leave`, {
    data: { leaveTypeId: types.data[0].id, startDate: '2026-12-09', endDate: '2026-12-09', halfStart: false, halfEnd: false },
    headers: { Authorization: `Bearer ${member.accessToken}` },
  });
  const approvalId = (await leave.json()).data.approvalId as string;
  const inbox = await (await request.get(`${API}/api/orgs/${slug}/approvals?box=inbox`, { headers: { Authorization: `Bearer ${standIn.accessToken}` } })).json();
  expect(inbox.data.map((a: { id: string }) => a.id)).toContain(approvalId);
});
