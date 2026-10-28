import { type APIRequestContext, type Page, expect, test } from '@playwright/test';

/**
 * The rest of the shared core, one area per test: documents, reports, notifications, comments,
 * archived projects, the audit log, account security and the language switch. Setup goes through the
 * API; the feature itself through the UI.
 */

const API = process.env.API_URL ?? 'http://localhost:8280';
const PASSWORD = 'E2e-pass-2026';

type Session = { token: string; id: string; email: string; name: string };

async function signUp(request: APIRequestContext, name: string, email: string): Promise<Session> {
  const res = await request.post(`${API}/api/auth/sign-up`, { data: { name, email, password: PASSWORD }, headers: { 'X-Client': 'mobile' } });
  expect(res.ok()).toBeTruthy();
  const data = (await res.json()).data;
  return { token: data.accessToken, id: data.user.id, email, name };
}

async function api(request: APIRequestContext, who: Session, method: string, path: string, data?: unknown) {
  const res = await request.fetch(`${API}/api${path}`, { method, data, headers: { Authorization: `Bearer ${who.token}` } });
  expect(res.ok(), `${method} ${path} → ${res.status()}`).toBeTruthy();
  return (await res.json()).data;
}

async function signIn(page: Page, email: string, password = PASSWORD) {
  await page.goto('/sign-in');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: /sign in/i }).click();
}

/** An organisation with an owner (project lead), a member, a project WRK and a task assigned to the member. */
async function workspace(request: APIRequestContext, tag: string) {
  const owner = await signUp(request, 'Owner Work', `owner-${tag}@e2e.test`);
  const member = await signUp(request, 'Member Work', `member-${tag}@e2e.test`);
  const slug = `w-${tag}`;
  await api(request, owner, 'POST', '/orgs', { name: `Work ${tag}`, slug });
  const invite = await api(request, owner, 'POST', `/orgs/${slug}/invitations`, { email: member.email, role: 'MEMBER' });
  await api(request, member, 'POST', `/invitations/${invite.link.split('/').pop()}/accept`);
  await api(request, owner, 'POST', `/orgs/${slug}/projects`, { name: 'Workspace', key: 'WRK', members: [{ userId: member.id, role: 'MEMBER' }] });
  const task = await api(request, owner, 'POST', `/orgs/${slug}/projects/WRK/tasks`, { title: 'Prepare the budget', assigneeId: member.id });
  return { owner, member, slug, task };
}

const tagOf = (name: string) => `${Date.now().toString(36)}${name[0]}${Math.random().toString(36).slice(2, 5)}`;

test('documents: create a folder, upload, find and download a file', async ({ page, request }, info) => {
  const { owner, slug } = await workspace(request, tagOf(info.project.name));
  await signIn(page, owner.email);
  await page.waitForURL(/\/overview$/);
  await page.goto(`/${slug}/documents`);

  await page.getByRole('button', { name: 'New folder' }).click();
  await page.getByRole('dialog').getByRole('textbox').first().fill('Contracts');
  await page.getByRole('dialog').getByRole('button', { name: /create/i }).click();
  await page.getByRole('link', { name: 'Contracts' }).or(page.getByRole('button', { name: 'Contracts' })).first().click();
  await expect(page).toHaveURL(/folder=/);

  await page.locator('input[type=file]').first().setInputFiles({ name: 'offer.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4 e2e') });
  await expect(page.getByText('offer.pdf').first()).toBeVisible();

  await page.goto(`/${slug}/documents`);
  await page.getByPlaceholder('Search documents').fill('offer');
  await expect(page.getByText('offer.pdf').first()).toBeVisible();
  // Opening the file shows its details, versions and history.
  await page.getByRole('button', { name: 'offer.pdf' }).first().click();
  const details = page.getByRole('dialog', { name: 'offer.pdf' });
  await expect(details.getByRole('button', { name: 'Download version 1' })).toBeVisible();
  const download = page.waitForEvent('download');
  await details.getByRole('button', { name: 'Download', exact: true }).click();
  expect((await download).suggestedFilename()).toBe('offer.pdf');
});

test('reports: charts load and a table downloads as CSV', async ({ page, request }, info) => {
  const { owner, slug } = await workspace(request, tagOf(info.project.name));
  await signIn(page, owner.email);
  await page.waitForURL(/\/overview$/);
  await page.goto(`/${slug}/reports`);
  await expect(page.getByRole('heading', { name: 'Reports', level: 1 })).toBeVisible();
  const download = page.waitForEvent('download');
  await page.getByRole('button', { name: /as CSV$/ }).first().click();
  expect((await download).suggestedFilename()).toMatch(/\.csv$/);
});

test('notifications, comments and the audit log', async ({ page, request }, info) => {
  const { owner, member, slug, task } = await workspace(request, tagOf(info.project.name));

  // The member was assigned a task: it is on the notifications page; mark all read.
  await signIn(page, member.email);
  await page.waitForURL(/\/overview$/);
  await page.goto(`/${slug}/notifications`);
  await expect(page.getByText('Assigned to you').first()).toBeVisible();
  await page.getByRole('button', { name: 'Mark all as read' }).click();
  await expect(page.getByRole('button', { name: 'Mark all as read' })).toHaveCount(0);

  // Comment, then edit it: it shows "edited".
  await page.goto(`/${slug}/tasks/${task.id}`);
  await page.getByLabel('Comment', { exact: true }).fill('First thoughts');
  await page.getByRole('button', { name: 'Comment', exact: true }).click();
  const comment = page.locator('li', { hasText: 'First thoughts' });
  await expect(comment).toBeVisible();
  await comment.getByRole('button', { name: /comment actions|more/i }).click();
  await page.getByRole('menuitem', { name: /edit/i }).click();
  await page.getByLabel('Edit comment').fill('Second thoughts');
  await page.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(page.locator('li', { hasText: 'Second thoughts' }).getByText('edited')).toBeVisible();

  // Members can't open the audit log; the owner sees the comment there.
  await page.goto(`/${slug}/admin/audit`);
  await expect(page.getByText("You don't have access")).toBeVisible();
  await page.context().clearCookies();
  await signIn(page, owner.email);
  await page.waitForURL(/\/overview$/);
  await page.goto(`/${slug}/admin/audit`);
  await expect(page.getByRole('heading', { name: 'Audit log' })).toBeVisible();
  await expect(page.getByText(/edited a comment/).filter({ visible: true })).toBeVisible();
});

test('an archived project is read-only until restored', async ({ page, request }, info) => {
  const { owner, slug } = await workspace(request, tagOf(info.project.name));
  await signIn(page, owner.email);
  await page.waitForURL(/\/overview$/);
  await page.goto(`/${slug}/projects/WRK?view=settings`);
  await page.getByRole('button', { name: /^archive/i }).first().click();
  await page.getByRole('alertdialog').getByRole('button', { name: /archive/i }).click();
  await expect(page.getByText(/This project is archived/).first()).toBeVisible();
  await page.goto(`/${slug}/projects/WRK`);
  await expect(page.getByRole('button', { name: 'New task' })).toHaveCount(0);
  await page.getByRole('button', { name: /restore/i }).first().click();
  await expect(page.getByRole('button', { name: 'New task' })).toBeVisible();
});

test('change password, sign in with the new one; Khmer on the sign-in page', async ({ page, request }, info) => {
  const tag = tagOf(info.project.name);
  const me = await signUp(request, 'Secure Person', `secure-${tag}@e2e.test`);
  await signIn(page, me.email);
  await page.waitForURL(/\/onboarding/);
  await page.goto('/account');
  await page.getByLabel('Current password').fill(PASSWORD);
  await page.getByLabel('New password', { exact: true }).fill('Newer-pass-2027');
  await page.getByLabel(/confirm|repeat|again/i).fill('Newer-pass-2027');
  await page.getByRole('button', { name: 'Change password' }).click();
  await expect(page.getByText(/password (was )?changed/i).first()).toBeVisible();

  await page.context().clearCookies();
  await signIn(page, me.email, 'Newer-pass-2027');
  await page.waitForURL(/\/onboarding/);

  await page.context().clearCookies();
  await page.goto('/sign-in');
  await page.getByRole('radio', { name: 'ខ្មែរ' }).click();
  await expect(page.getByRole('button', { name: 'ចូលគណនី' })).toBeVisible();
  await page.getByRole('radio', { name: 'English' }).click();
});
