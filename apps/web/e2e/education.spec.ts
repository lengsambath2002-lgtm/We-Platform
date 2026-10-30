import { type APIRequestContext, type Page, expect, test } from '@playwright/test';

const API = process.env.API_URL ?? 'http://localhost:8280';
const PASSWORD = 'E2e-pass-2026';

async function signIn(page: Page, email: string) {
  await page.goto('/sign-in');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: /sign in/i }).click();
}

async function signUp(request: APIRequestContext, name: string, email: string) {
  const res = await request.post(`${API}/api/auth/sign-up`, { data: { name, email, password: PASSWORD }, headers: { 'X-Client': 'mobile' } });
  expect(res.ok()).toBeTruthy();
  const data = (await res.json()).data;
  return { token: data.accessToken as string, id: data.user.id as string };
}

const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
const shift = (days: number) => { const d = new Date(); d.setDate(d.getDate() + days); return iso(d); };

test('student registration, then the teacher takes attendance and enters results that reach the report card', async ({ page, request }, info) => {
  test.setTimeout(300_000); // a long journey; the dev server compiles each page on first visit
  const id = `${Date.now().toString(36)}${info.project.name[0]}e`;
  const ownerEmail = `owner-${id}@e2e.test`;
  const teacherEmail = `teacher-${id}@e2e.test`;
  const owner = await signUp(request, 'Owner School', ownerEmail);
  const teacher = await signUp(request, 'Teacher Sophea', teacherEmail);
  const slug = `edu-${id}`;
  const auth = (token: string) => ({ Authorization: `Bearer ${token}` });
  expect((await request.post(`${API}/api/orgs`, { data: { name: `School ${id}`, slug }, headers: auth(owner.token) })).ok()).toBeTruthy();
  const inv = await request.post(`${API}/api/orgs/${slug}/invitations`, { data: { email: teacherEmail, role: 'MEMBER' }, headers: auth(owner.token) });
  const link = (await inv.json()).data.link as string;
  expect((await request.post(`${API}/api/invitations/${link.slice(link.lastIndexOf('/') + 1)}/accept`, { headers: auth(teacher.token) })).ok()).toBeTruthy();

  // The owner switches Education on; the module page leads to the education home, which asks for the year.
  await signIn(page, ownerEmail);
  await page.waitForURL(/\/overview$/);
  await page.goto(`/${slug}/admin/modules`);
  await page.getByRole('switch', { name: 'Use Education' }).click();
  await expect(page.getByText('Education switched on')).toBeVisible();
  await page.goto(`/${slug}/m/education`);
  await page.waitForURL(new RegExp(`/${slug}/education$`));
  await expect(page.getByText('No current academic year', { exact: true })).toBeVisible();

  // The academic structure, a teacher, a class and a course (through the API, as an admin would in Settings).
  const e = `${API}/api/orgs/${slug}/education`;
  const year = (await (await request.post(`${e}/years`, { headers: auth(owner.token),
    data: { name: '2026–2027', startsOn: shift(-60), endsOn: shift(300), current: true } })).json()).data;
  await request.post(`${e}/years/${year.id}/terms`, { headers: auth(owner.token), data: { name: 'Semester 1', startsOn: shift(-60), endsOn: shift(30) } });
  const t = (await (await request.post(`${e}/teachers`, { headers: auth(owner.token), data: { userId: teacher.id } })).json()).data;
  const grades = (await (await request.get(`${e}/grades`, { headers: auth(owner.token) })).json()).data as { id: string; nameEn: string }[];
  const subjects = (await (await request.get(`${e}/subjects`, { headers: auth(owner.token) })).json()).data as { id: string; code: string }[];
  const cls = (await (await request.post(`${e}/classes`, { headers: auth(owner.token),
    data: { yearId: year.id, gradeId: grades.find((g) => g.nameEn === 'Grade 7')!.id, name: '7A', homeroomTeacherId: t.id } })).json()).data;
  expect((await request.post(`${e}/classes/${cls.summary.id}/courses`, { headers: auth(owner.token),
    data: { subjectId: subjects.find((s) => s.code === 'MATH')!.id, teacherId: t.id } })).ok()).toBeTruthy();

  // Registration: the student, a guardian and the class; the student gets STU-0001.
  await page.goto(`/${slug}/education/students/new`);
  await page.getByLabel('Name (English)').fill('Sok Dara');
  await page.getByLabel('Date of birth').fill('2014-03-01');
  await page.getByLabel(/^Class/).click();
  await page.getByRole('option', { name: /7A/ }).click();
  await page.getByLabel('Full name').fill('Chan Sreymom');
  await page.getByLabel('Phone').fill('+855 12 345 678');
  await page.getByRole('button', { name: 'Register', exact: true }).click();
  await page.waitForURL(/\/education\/students\/[0-9a-f-]{36}$/);
  await expect(page.getByRole('heading', { name: /Sok Dara/ })).toBeVisible();
  await expect(page.getByText(/STU-0001/).first()).toBeVisible();
  await expect(page.getByText('Chan Sreymom')).toBeVisible();
  await expect(page.getByText(/registered Sok Dara as STU-0001 in class 7A/)).toBeVisible();

  // The teacher signs in: only their class; no fees.
  await page.context().clearCookies();
  await signIn(page, teacherEmail);
  await page.waitForURL(/\/overview$/);
  await page.goto(`/${slug}/education`);
  await expect(page.getByRole('heading', { name: 'Education', exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Fees' })).toHaveCount(0);

  // Attendance: Sok Dara absent today.
  await page.goto(`/${slug}/education/attendance`);
  await expect(page.getByRole('heading', { name: 'Register of 7A' })).toBeVisible();
  await page.getByRole('radiogroup', { name: 'Attendance of Sok Dara' }).getByRole('radio', { name: 'Absent' }).click();
  await page.getByRole('button', { name: 'Save attendance' }).click();
  await expect(page.getByText('Attendance saved')).toBeVisible();
  await expect(page.getByText(/Taken by Teacher Sophea/)).toBeVisible();

  // An exam, then a score in the gradebook: the server computes 70/80 = 87.5 %.
  await page.goto(`/${slug}/education/exams`);
  await page.getByRole('button', { name: 'New assessment' }).first().click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Term').click();
  await page.getByRole('option', { name: 'Semester 1' }).click();
  await dialog.getByLabel('Name', { exact: true }).fill('Midterm');
  await dialog.getByLabel('Max score').fill('80');
  await dialog.getByLabel('Weight (%)').fill('100');
  await dialog.getByRole('button', { name: 'Save' }).click();
  await expect(page.getByText('Assessment saved')).toBeVisible();
  await page.goto(`/${slug}/education/gradebook`);
  await page.getByLabel('Score of Sok Dara on Midterm').fill('70');
  await page.getByRole('button', { name: 'Save scores' }).click();
  await expect(page.getByText('Scores saved')).toBeVisible();
  await expect(page.getByText('87.5%').first()).toBeVisible();

  // The report card shows the result, the letter and today's absence.
  await page.goto(`/${slug}/education/gradebook?class=${cls.summary.id}`);
  await page.getByRole('link', { name: 'Report card of Sok Dara' }).click();
  const card = page.getByRole('article', { name: 'Report card for printing' });
  await expect(card.getByText('Student report card')).toBeVisible();
  await expect(card.getByText('87.5%').first()).toBeVisible();
  await expect(card.getByText(/0 present, 0 late, 1 absent/)).toBeVisible();
  await expect(page.getByRole('button', { name: 'Print / Save as PDF' })).toBeVisible();
});
