'use client';

// Education: types, query hooks and calls. Results and money are computed by the API (USD numbers, percents with one decimal).
import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { http } from './client';
import type { Page } from './collab';
import type { ActivityItem, ApprovalStatus, PersonRef } from './types';

export type StudentStatus = 'ACTIVE' | 'WITHDRAWN' | 'GRADUATED';
export const STUDENT_STATUSES: StudentStatus[] = ['ACTIVE', 'WITHDRAWN', 'GRADUATED'];
export type Gender = 'FEMALE' | 'MALE' | 'OTHER';
export const GENDERS: Gender[] = ['FEMALE', 'MALE', 'OTHER'];
export type Relationship = 'MOTHER' | 'FATHER' | 'GUARDIAN' | 'GRANDPARENT' | 'SIBLING' | 'OTHER';
export const RELATIONSHIPS: Relationship[] = ['MOTHER', 'FATHER', 'GUARDIAN', 'GRANDPARENT', 'SIBLING', 'OTHER'];
export type Mark = 'PRESENT' | 'ABSENT' | 'LATE' | 'EXCUSED';
export const MARKS: Mark[] = ['PRESENT', 'LATE', 'ABSENT', 'EXCUSED'];
export type AssessmentKind = 'EXAM' | 'QUIZ' | 'ASSIGNMENT' | 'PROJECT' | 'ORAL';
export const ASSESSMENT_KINDS: AssessmentKind[] = ['EXAM', 'QUIZ', 'ASSIGNMENT', 'PROJECT', 'ORAL'];
export type FeeStatus = 'ISSUED' | 'PARTIALLY_PAID' | 'PAID' | 'VOID';
export const FEE_STATUSES: FeeStatus[] = ['ISSUED', 'PARTIALLY_PAID', 'PAID', 'VOID'];
export type PayMethod = 'CASH' | 'BANK_TRANSFER' | 'KHQR' | 'CARD' | 'OTHER';
export const PAY_METHODS: PayMethod[] = ['CASH', 'BANK_TRANSFER', 'KHQR', 'CARD', 'OTHER'];
export type WithdrawalStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';
/** 1 = Monday … 6 = Saturday. */
export const WEEKDAYS = [1, 2, 3, 4, 5, 6] as const;

export interface EduSettings { schoolName: string | null; principalName: string | null; address: string | null; phone: string | null; passMark: number; orgName: string }
export interface Term { id: string; yearId: string; name: string; startsOn: string; endsOn: string }
export interface Year { id: string; name: string; startsOn: string; endsOn: string; current: boolean; terms: Term[] }
export interface Grade { id: string; nameEn: string; nameKm: string | null; position: number; active: boolean }
export interface Subject { id: string; code: string; nameEn: string; nameKm: string | null; active: boolean }
export interface TeacherRef { id: string; userId: string; name: string; avatarUrl: string | null }
export interface Teacher {
  id: string; userId: string; name: string; email: string | null; avatarUrl: string | null; phone: string | null;
  specialty: string | null; active: boolean; homerooms: string[]; courses: number;
}
export interface ClassRef { id: string; name: string; yearId: string }
export interface StudentRef { id: string; number: string; nameEn: string; nameKm: string | null; status: StudentStatus }
export interface Guardian { id: string; name: string; phone: string | null; email: string | null; occupation: string | null; address: string | null; children: StudentRef[] }
export interface GuardianOf { id: string; name: string; phone: string | null; email: string | null; occupation: string | null; address: string | null; relationship: Relationship; primary: boolean }
export interface StudentSummary {
  id: string; number: string; nameEn: string; nameKm: string | null; gender: Gender; dateOfBirth: string; status: StudentStatus;
  currentClass: ClassRef | null; gradeName: string | null; primaryGuardian: string | null; guardianPhone: string | null;
  /** unpaid fees; null unless admin */
  balance: number | null;
}
export interface StudentView {
  id: string; number: string; nameEn: string; nameKm: string | null; gender: Gender; dateOfBirth: string; admittedOn: string;
  address: string | null; medicalNotes: string | null; status: StudentStatus; leftOn: string | null; leaveReason: string | null; createdAt: string;
}
export interface EnrollmentView { classRef: ClassRef | null; yearName: string; gradeName: string | null; enrolledOn: string; leftOn: string | null }
export interface Counts { present: number; late: number; absent: number; excused: number; rate: number | null }
export interface Withdrawal {
  id: string; student: StudentRef; effectiveOn: string; reason: string; status: WithdrawalStatus; approvalId: string | null;
  approvalStatus: ApprovalStatus | null; requestedBy: PersonRef | null; createdAt: string; decidedAt: string | null;
}
export interface StudentDetail {
  student: StudentView; guardians: GuardianOf[]; currentClass: ClassRef | null; gradeName: string | null; enrollments: EnrollmentView[];
  attendance: Counts; balance: number | null; invoices: InvoiceSummary[] | null; withdrawals: Withdrawal[];
  canEdit: boolean; canRequestWithdrawal: boolean; canGraduate: boolean;
}
export interface ClassSummary {
  id: string; name: string; yearId: string; yearName: string; gradeId: string; gradeName: string | null; homeroom: TeacherRef | null;
  room: string | null; capacity: number | null; students: number; courses: number; mine: boolean;
}
export interface Course { id: string; classRef: ClassRef; subject: Subject; teacher: TeacherRef; periods: number; mine: boolean }
export interface ClassDetail { summary: ClassSummary; students: StudentSummary[]; courses: Course[]; canManage: boolean; canTakeAttendance: boolean }
export interface PeriodView {
  id: string; classRef: ClassRef; courseId: string; subject: Subject; teacher: TeacherRef; weekday: number; startsAt: string; endsAt: string;
  room: string | null; canEdit: boolean;
}
export interface AttendanceRow { student: StudentRef; status: Mark | null; note: string | null }
export interface AttendanceSheet {
  classRef: ClassRef; day: string; marked: boolean; rows: AttendanceRow[]; counts: Counts; canEdit: boolean;
  recordedBy: PersonRef | null; recordedAt: string | null;
}
export interface AttendanceSummary { classRef: ClassRef; from: string; to: string; days: number; rows: { student: StudentRef; counts: Counts }[]; total: Counts }
export interface CourseRef { id: string; classRef: ClassRef; subject: Subject; teacher: TeacherRef }
export interface Assessment {
  id: string; course: CourseRef; term: Term; name: string; kind: AssessmentKind; heldOn: string; maxScore: number; weight: number;
  graded: number; students: number; canEdit: boolean;
}
export interface GradebookRow { student: StudentRef; scores: Record<string, number>; result: number | null; letter: string | null }
export interface Gradebook { course: CourseRef; term: Term; assessments: Assessment[]; rows: GradebookRow[]; totalWeight: number; classAverage: number | null; canEdit: boolean }
export interface ResultRow { student: StudentRef; results: Record<string, number>; average: number | null; letter: string | null; rank: number | null; passed: boolean | null }
export interface ClassResults { classRef: ClassRef; term: Term; courses: CourseRef[]; rows: ResultRow[]; passMark: number }
export interface ReportCard {
  school: EduSettings; student: StudentView; classRef: ClassRef; gradeName: string | null; homeroom: string | null; yearName: string; term: Term;
  lines: { subject: Subject; teacher: string | null; result: number | null; letter: string | null }[];
  average: number | null; letter: string | null; rank: number | null; classSize: number; passed: boolean | null; attendance: Counts;
}
export interface FeeItem {
  id: string; yearId: string; yearName: string; gradeId: string | null; gradeName: string | null; nameEn: string; nameKm: string | null;
  amount: number; dueOn: string; active: boolean; billed: number; collected: number; outstanding: number;
}
export interface InvoiceSummary {
  id: string; number: string; student: StudentRef; description: string; amount: number; paid: number; balance: number;
  issuedOn: string; dueOn: string; status: FeeStatus; overdue: boolean;
}
export interface Payment { id: string; paidOn: string; amount: number; method: PayMethod; reference: string | null; recordedBy: PersonRef | null; createdAt: string }
export interface Invoice {
  summary: InvoiceSummary; feeItemId: string | null; payments: Payment[]; voidReason: string | null; voidedAt: string | null;
  createdBy: PersonRef | null; createdAt: string; canPay: boolean; canVoid: boolean; canRemovePayment: boolean;
}
export interface WeekRate { week: string; present: number; late: number; absent: number; excused: number; rate: number | null }
export interface Overview {
  admin: boolean; currentYear: Year | null; activeStudents: number; teachers: number; classes: number; attendanceToday: number | null;
  classesMarkedToday: number; unmarkedToday: ClassSummary[]; weeks: WeekRate[]; outstanding: number | null; overdue: number | null;
  overdueInvoices: number | null; upcoming: Assessment[]; pendingWithdrawals: Withdrawal[];
}
export interface Reports {
  from: string; to: string; term: Term | null;
  attendance: { classRef: ClassRef; gradeName: string | null; days: number; counts: Counts }[];
  results: { classRef: ClassRef; gradeName: string | null; students: number; graded: number; average: number | null; passRate: number | null }[] | null;
  fees: { gradeId: string | null; gradeName: string | null; invoices: number; billed: number; collected: number; outstanding: number; overdue: number }[] | null;
}

export interface StudentFilter { q?: string; status?: string; classId?: string; gradeId?: string; page?: number; size?: number }
export interface InvoiceFilter { q?: string; status?: string; studentId?: string; feeItemId?: string; page?: number; size?: number }

const e = (org: string) => `/orgs/${encodeURIComponent(org)}/education`;
const qs = (o: object) => {
  const p = new URLSearchParams();
  Object.entries(o).forEach(([k, v]) => v !== undefined && v !== null && v !== '' && v !== false && p.set(k, String(v)));
  const s = p.toString();
  return s ? `?${s}` : '';
};
const k = (org: string, ...rest: unknown[]) => ['org', org, 'education', ...rest] as const;

export const useEduOverview = (org: string) => useQuery({ queryKey: k(org, 'overview'), queryFn: () => http.get<Overview>(`${e(org)}/overview`) });
export const useEduReports = (org: string, f: { from?: string; to?: string; termId?: string }) =>
  useQuery({ queryKey: k(org, 'reports', f), queryFn: () => http.get<Reports>(`${e(org)}/reports${qs(f)}`), placeholderData: keepPreviousData });
export const useEduSettings = (org: string) => useQuery({ queryKey: k(org, 'settings'), queryFn: () => http.get<EduSettings>(`${e(org)}/settings`) });
export const useYears = (org: string) => useQuery({ queryKey: k(org, 'years'), queryFn: () => http.get<Year[]>(`${e(org)}/years`) });
export const useGrades = (org: string) => useQuery({ queryKey: k(org, 'grades'), queryFn: () => http.get<Grade[]>(`${e(org)}/grades`) });
export const useSubjects = (org: string) => useQuery({ queryKey: k(org, 'subjects'), queryFn: () => http.get<Subject[]>(`${e(org)}/subjects`) });
export const useTeachers = (org: string) => useQuery({ queryKey: k(org, 'teachers'), queryFn: () => http.get<Teacher[]>(`${e(org)}/teachers`) });
export const useGuardians = (org: string, f: { q?: string; page?: number; size?: number }) =>
  useQuery({ queryKey: k(org, 'guardians', f), queryFn: () => http.get<Page<Guardian>>(`${e(org)}/guardians${qs(f)}`), placeholderData: keepPreviousData });
export const useStudents = (org: string, f: StudentFilter) =>
  useQuery({ queryKey: k(org, 'students', f), queryFn: () => http.get<Page<StudentSummary>>(`${e(org)}/students${qs(f)}`), placeholderData: keepPreviousData });
export const useStudent = (org: string, id: string) =>
  useQuery({ queryKey: k(org, 'student', id), queryFn: () => http.get<StudentDetail>(`${e(org)}/students/${id}`) });
export const useStudentHistory = (org: string, id: string) =>
  useQuery({ queryKey: k(org, 'student', id, 'history'), queryFn: () => http.get<ActivityItem[]>(`${e(org)}/students/${id}/activity`) });
export const useClasses = (org: string, yearId?: string) =>
  useQuery({ queryKey: k(org, 'classes', yearId ?? ''), queryFn: () => http.get<ClassSummary[]>(`${e(org)}/classes${qs({ yearId })}`) });
export const useClass = (org: string, id: string) =>
  useQuery({ queryKey: k(org, 'class', id), queryFn: () => http.get<ClassDetail>(`${e(org)}/classes/${id}`), enabled: !!id });
export const useCourses = (org: string, classId?: string) =>
  useQuery({ queryKey: k(org, 'courses', classId ?? ''), queryFn: () => http.get<Course[]>(`${e(org)}/courses${qs({ classId })}`) });
export const useTimetable = (org: string, f: { classId?: string; teacherId?: string }) =>
  useQuery({ queryKey: k(org, 'timetable', f), queryFn: () => http.get<PeriodView[]>(`${e(org)}/timetable${qs(f)}`), placeholderData: keepPreviousData });
export const useAttendance = (org: string, classId: string, day: string) =>
  useQuery({ queryKey: k(org, 'attendance', classId, day), queryFn: () => http.get<AttendanceSheet>(`${e(org)}/classes/${classId}/attendance?day=${day}`), enabled: !!classId && !!day });
export const useAttendanceSummary = (org: string, classId: string, from: string, to: string) =>
  useQuery({ queryKey: k(org, 'attendance-summary', classId, from, to), enabled: !!classId,
    queryFn: () => http.get<AttendanceSummary>(`${e(org)}/classes/${classId}/attendance/summary?from=${from}&to=${to}`) });
export const useAssessments = (org: string, f: { courseId?: string; termId?: string }) =>
  useQuery({ queryKey: k(org, 'assessments', f), queryFn: () => http.get<Assessment[]>(`${e(org)}/assessments${qs(f)}`), placeholderData: keepPreviousData });
export const useGradebook = (org: string, courseId: string, termId: string) =>
  useQuery({ queryKey: k(org, 'gradebook', courseId, termId), enabled: !!courseId && !!termId,
    queryFn: () => http.get<Gradebook>(`${e(org)}/courses/${courseId}/gradebook?termId=${termId}`) });
export const useClassResults = (org: string, classId: string, termId: string) =>
  useQuery({ queryKey: k(org, 'results', classId, termId), enabled: !!classId && !!termId,
    queryFn: () => http.get<ClassResults>(`${e(org)}/classes/${classId}/results?termId=${termId}`) });
export const useReportCard = (org: string, studentId: string, termId: string) =>
  useQuery({ queryKey: k(org, 'report-card', studentId, termId), enabled: !!termId,
    queryFn: () => http.get<ReportCard>(`${e(org)}/students/${studentId}/report-card?termId=${termId}`) });
export const useFeeItems = (org: string, yearId?: string) =>
  useQuery({ queryKey: k(org, 'fee-items', yearId ?? ''), queryFn: () => http.get<FeeItem[]>(`${e(org)}/fee-items${qs({ yearId })}`) });
export const useFeeInvoices = (org: string, f: InvoiceFilter) =>
  useQuery({ queryKey: k(org, 'invoices', f), queryFn: () => http.get<Page<InvoiceSummary>>(`${e(org)}/invoices${qs(f)}`), placeholderData: keepPreviousData });
export const useFeeInvoice = (org: string, id: string) =>
  useQuery({ queryKey: k(org, 'invoice', id), queryFn: () => http.get<Invoice>(`${e(org)}/invoices/${id}`) });
export const useFeeInvoiceHistory = (org: string, id: string) =>
  useQuery({ queryKey: k(org, 'invoice', id, 'history'), queryFn: () => http.get<ActivityItem[]>(`${e(org)}/invoices/${id}/activity`) });

export const eduApi = {
  saveSettings: (org: string, body: unknown) => http.put<EduSettings>(`${e(org)}/settings`, body),
  saveYear: (org: string, id: string | null, body: unknown) => (id ? http.put<Year>(`${e(org)}/years/${id}`, body) : http.post<Year>(`${e(org)}/years`, body)),
  saveTerm: (org: string, yearId: string, id: string | null, body: unknown) =>
    (id ? http.put<Term>(`${e(org)}/terms/${id}`, body) : http.post<Term>(`${e(org)}/years/${yearId}/terms`, body)),
  deleteTerm: (org: string, id: string) => http.delete<void>(`${e(org)}/terms/${id}`),
  saveGrade: (org: string, id: string | null, body: unknown) => (id ? http.put<Grade>(`${e(org)}/grades/${id}`, body) : http.post<Grade>(`${e(org)}/grades`, body)),
  saveSubject: (org: string, id: string | null, body: unknown) =>
    (id ? http.put<Subject>(`${e(org)}/subjects/${id}`, body) : http.post<Subject>(`${e(org)}/subjects`, body)),
  saveTeacher: (org: string, id: string | null, body: unknown) =>
    (id ? http.put<Teacher>(`${e(org)}/teachers/${id}`, body) : http.post<Teacher>(`${e(org)}/teachers`, body)),
  saveGuardian: (org: string, id: string | null, body: unknown) =>
    (id ? http.put<Guardian>(`${e(org)}/guardians/${id}`, body) : http.post<Guardian>(`${e(org)}/guardians`, body)),
  register: (org: string, body: unknown) => http.post<StudentDetail>(`${e(org)}/students`, body),
  updateStudent: (org: string, id: string, body: unknown) => http.put<StudentDetail>(`${e(org)}/students/${id}`, body),
  linkGuardian: (org: string, id: string, body: unknown) => http.post<StudentDetail>(`${e(org)}/students/${id}/guardians`, body),
  unlinkGuardian: (org: string, id: string, guardianId: string) => http.delete<StudentDetail>(`${e(org)}/students/${id}/guardians/${guardianId}`),
  graduate: (org: string, id: string, leftOn: string) => http.post<StudentDetail>(`${e(org)}/students/${id}/graduate`, { leftOn }),
  requestWithdrawal: (org: string, id: string, body: unknown) => http.post<Withdrawal>(`${e(org)}/students/${id}/withdrawal`, body),
  saveClass: (org: string, id: string | null, body: unknown) =>
    (id ? http.put<ClassDetail>(`${e(org)}/classes/${id}`, body) : http.post<ClassDetail>(`${e(org)}/classes`, body)),
  enroll: (org: string, id: string, studentIds: string[]) => http.post<ClassDetail>(`${e(org)}/classes/${id}/enrollments`, { studentIds }),
  unenroll: (org: string, id: string, studentId: string) => http.delete<ClassDetail>(`${e(org)}/classes/${id}/enrollments/${studentId}`),
  addCourse: (org: string, classId: string, body: unknown) => http.post<Course>(`${e(org)}/classes/${classId}/courses`, body),
  changeTeacher: (org: string, id: string, teacherId: string) => http.put<Course>(`${e(org)}/courses/${id}`, { teacherId }),
  removeCourse: (org: string, id: string) => http.delete<void>(`${e(org)}/courses/${id}`),
  savePeriod: (org: string, id: string | null, body: unknown) =>
    (id ? http.put<PeriodView>(`${e(org)}/timetable/${id}`, body) : http.post<PeriodView>(`${e(org)}/timetable`, body)),
  deletePeriod: (org: string, id: string) => http.delete<void>(`${e(org)}/timetable/${id}`),
  takeAttendance: (org: string, classId: string, body: unknown) => http.put<AttendanceSheet>(`${e(org)}/classes/${classId}/attendance`, body),
  saveAssessment: (org: string, courseId: string, id: string | null, body: unknown) =>
    (id ? http.put<Assessment>(`${e(org)}/assessments/${id}`, body) : http.post<Assessment>(`${e(org)}/courses/${courseId}/assessments`, body)),
  deleteAssessment: (org: string, id: string) => http.delete<void>(`${e(org)}/assessments/${id}`),
  saveScores: (org: string, id: string, scores: { studentId: string; score: string | null }[]) =>
    http.put<Gradebook>(`${e(org)}/assessments/${id}/scores`, { scores }),
  saveFeeItem: (org: string, id: string | null, body: unknown) =>
    (id ? http.put<FeeItem>(`${e(org)}/fee-items/${id}`, body) : http.post<FeeItem>(`${e(org)}/fee-items`, body)),
  bill: (org: string, id: string) => http.post<{ created: number; skipped: number }>(`${e(org)}/fee-items/${id}/bill`),
  createInvoice: (org: string, body: unknown) => http.post<Invoice>(`${e(org)}/invoices`, body),
  pay: (org: string, id: string, body: unknown) => http.post<Invoice>(`${e(org)}/invoices/${id}/payments`, body),
  removePayment: (org: string, id: string, paymentId: string) => http.delete<Invoice>(`${e(org)}/invoices/${id}/payments/${paymentId}`),
  voidInvoice: (org: string, id: string, reason: string) => http.post<Invoice>(`${e(org)}/invoices/${id}/void`, { reason }),
};

/** Today as yyyy-MM-dd in the browser's time zone. */
export function isoToday(offsetDays = 0): string {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** "08:00:00" → "08:00". */
export const hhmm = (t: string) => t.slice(0, 5);
