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

test('owner creates an organisation, a project and a task; a member requests approval; the lead approves', async ({ page, request, browser }, info) => {
  const id = `${Date.now().toString(36)}${info.project.name[0]}`;
  const owner = { name: 'Owner Test', email: `owner-${id}@e2e.test` };
  const member = { name: 'Member Test', email: `member-${id}@e2e.test` };
  await signUp(request, owner.name, owner.email);
  const m = await signUp(request, member.name, member.email);

  // Journey 1: first sign-in leads to onboarding; create the organisation.
  await signIn(page, owner.email);
  await page.waitForURL(/\/onboarding/);
  await page.getByLabel('Organisation name').fill(`Org ${id}`);
  await page.getByRole('button', { name: /create/i }).click();
  await page.waitForURL(/\/overview$/);
  const slug = new URL(page.url()).pathname.split('/')[1]!;

  // Invite the member through the dialog and accept with the link it shows.
  await page.goto(`/${slug}/admin/members`);
  await page.getByRole('button', { name: 'Invite' }).first().click();
  await page.getByLabel('Email').fill(member.email);
  await page.getByRole('button', { name: 'Create invitation' }).click();
  const link = await page.getByLabel('Invitation link').inputValue();
  const token = link.split('/').pop()!;
  const accept = await request.post(`${API}/api/invitations/${token}/accept`, { headers: { Authorization: `Bearer ${m.accessToken}` } });
  expect(accept.ok()).toBeTruthy();
  await page.getByRole('button', { name: 'Done' }).click();

  // Journey 2: create a project with the member in it.
  await page.goto(`/${slug}/projects?new=1`);
  await page.getByLabel('Name').fill('Launch plan');
  await page.getByRole('button', { name: /create project/i }).click();
  await page.waitForURL(new RegExp(`/${slug}/projects/[A-Z0-9]+$`));
  const key = page.url().split('/').pop()!;
  await page.goto(`/${slug}/projects/${key}?view=members`);
  await page.getByLabel('Person to add').click();
  await page.getByRole('option', { name: member.name }).click();
  await page.getByRole('button', { name: 'Add', exact: true }).click();
  await page.getByRole('button', { name: 'Save members' }).click();
  await expect(page.getByText('Members saved')).toBeVisible();

  // Journey 3: create a task assigned to the member, then move it with the card menu.
  await page.goto(`/${slug}/projects/${key}`);
  await page.getByRole('button', { name: 'New task' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Title').fill('Write the launch email');
  await dialog.getByLabel('Assignee').click();
  await page.getByRole('option', { name: member.name }).click();
  await dialog.getByRole('button', { name: 'Create', exact: true }).click();
  await expect(page.getByText(`${key}-1 created`)).toBeVisible();
  await page.getByRole('button', { name: `Move ${key}-1` }).click();
  await page.getByRole('menuitem', { name: 'In progress' }).click();
  await expect(page.getByRole('region', { name: 'In progress' }).getByText('Write the launch email')).toBeVisible();

  // Journey 4: the member opens My Tasks, comments with a mention and requests approval.
  await page.context().clearCookies();
  await signIn(page, member.email);
  await page.waitForURL(/\/overview$/);
  await page.goto(`/${slug}/my-tasks`);
  await page.getByText('Write the launch email').click();
  await page.waitForURL(/\/tasks\//);
  await page.getByLabel('Comment', { exact: true }).fill('Draft ready @Own');
  await page.getByRole('listbox', { name: 'Mention' }).getByRole('button', { name: owner.name }).click();
  await page.getByRole('button', { name: 'Comment', exact: true }).click();
  await expect(page.locator('li', { hasText: 'Draft ready' }).getByText(`@${owner.name}`)).toBeVisible();
  await page.getByRole('button', { name: 'Request approval' }).click();
  await page.getByLabel('Workflow').click();
  await page.getByRole('option', { name: 'Project lead sign-off' }).click();
  await page.getByRole('button', { name: 'Send request' }).click();
  await expect(page.getByText('Approval requested')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Withdraw' })).toBeVisible();
  // The requester cannot approve their own request.
  await expect(page.getByRole('button', { name: 'Approve' })).toHaveCount(0);

  // Journey 5: the owner (project lead) finds it in the inbox and approves.
  await page.context().clearCookies();
  await signIn(page, owner.email);
  await page.waitForURL(/\/overview$/);
  await page.goto(`/${slug}/approvals`);
  await page.getByText(`${key}-1 Write the launch email`).click();
  await page.getByRole('button', { name: 'Approve' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Approve' }).click();
  await expect(page.getByText('Approved', { exact: true }).first()).toBeVisible();
  await page.goto(`/${slug}/approvals`);
  await expect(page.getByText('Nothing waits for your decision')).toBeVisible();
});

test('a non-member gets "not found" for another organisation', async ({ page, request }, info) => {
  const id = `${Date.now().toString(36)}${info.project.name[0]}x`;
  const a = await signUp(request, 'Alpha', `alpha-${id}@e2e.test`);
  await request.post(`${API}/api/orgs`, { data: { name: `Alpha ${id}`, slug: `alpha-${id}` }, headers: { Authorization: `Bearer ${a.accessToken}` } });
  await signUp(request, 'Beta', `beta-${id}@e2e.test`);
  await signIn(page, `beta-${id}@e2e.test`);
  await page.waitForURL(/\/onboarding/);
  await page.goto(`/alpha-${id}/overview`);
  await expect(page.getByText(/not found|doesn.t exist|no access/i).first()).toBeVisible();
});
