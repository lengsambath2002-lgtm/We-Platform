import { tr } from '@/i18n/core';
import type { ActivityItem } from '@/lib/api/types';
import { day } from '@/lib/format';
import { usd } from '@/lib/money';

const s = (v: unknown) => (v === null || v === undefined ? '' : String(v));
const n = (v: unknown) => Number(v ?? 0);
const date = (v: unknown) => (s(v) ? day(s(v)) : '');
const METHODS = ['CASH', 'BANK_TRANSFER', 'KHQR', 'CARD', 'OTHER'] as const;
const method = (v: unknown) => (METHODS.includes(v as never) ? tr(`education.method_${v as (typeof METHODS)[number]}`) : s(v));
const RELS = ['MOTHER', 'FATHER', 'GUARDIAN', 'GRANDPARENT', 'SIBLING', 'OTHER'] as const;
const rel = (v: unknown) => (RELS.includes(v as never) ? tr(`education.rel_${v as (typeof RELS)[number]}`) : s(v));
const DAYS = ['1', '2', '3', '4', '5', '6'] as const;
const weekday = (v: unknown) => (DAYS.includes(s(v) as never) ? tr(`education.weekday_${s(v) as (typeof DAYS)[number]}`) : s(v));
const time = (v: unknown) => s(v).slice(0, 5);

/** A plain sentence for an Education activity entry (student history, invoice history, audit log), in the current language. */
export function describeEducation(a: ActivityItem): string {
  const d = a.data;
  switch (a.verb) {
    case 'education.settings.updated': return tr('education.actSettings', { pass: s(d.passMark) });
    case 'education.year.created': return tr('education.actYearCreated', { name: s(d.name) });
    case 'education.year.updated': return tr('education.actYearUpdated', { name: s(d.name) });
    case 'education.term.created': return tr('education.actTermCreated', { name: s(d.name), year: s(d.year) });
    case 'education.term.updated': return tr('education.actTermUpdated', { name: s(d.name), year: s(d.year) });
    case 'education.term.deleted': return tr('education.actTermDeleted', { name: s(d.name), year: s(d.year) });
    case 'education.grade.created': return tr('education.actGradeCreated', { name: s(d.name) });
    case 'education.grade.updated': return tr('education.actGradeUpdated', { name: s(d.name) });
    case 'education.subject.created': return tr('education.actSubjectCreated', { name: s(d.name) });
    case 'education.subject.updated': return tr('education.actSubjectUpdated', { name: s(d.name) });
    case 'education.teacher.added': return tr('education.actTeacherAdded', { name: s(d.name) });
    case 'education.teacher.updated': return tr('education.actTeacherUpdated', { name: s(d.name) });
    case 'education.guardian.created': return tr('education.actGuardianCreated', { name: s(d.name) });
    case 'education.guardian.updated': return tr('education.actGuardianUpdated', { name: s(d.name) });
    case 'education.student.registered': return d.class ? tr('education.actRegisteredIn', { name: s(d.name), number: s(d.number), class: s(d.class) })
      : tr('education.actRegistered', { name: s(d.name), number: s(d.number) });
    case 'education.student.updated': return tr('education.actStudentUpdated', { name: s(d.name) });
    case 'education.student.guardian_added': return tr('education.actGuardianAdded', { name: s(d.guardian), relationship: rel(d.relationship) });
    case 'education.student.guardian_removed': return tr('education.actGuardianRemoved', { name: s(d.guardian) });
    case 'education.student.graduated': return tr('education.actGraduated', { name: s(d.name), date: date(d.date) });
    case 'education.student.billed': return tr('education.actBilledStudent', { number: s(d.number), description: s(d.description), amount: usd(s(d.amount)) });
    case 'education.student.withdrawn': return tr('education.actWithdrawn', { name: s(d.name), date: date(d.date) });
    case 'education.withdrawal.requested': return tr('education.actWithdrawalRequested', { date: date(d.date), reason: s(d.reason) });
    case 'education.withdrawal.self_approved': return tr('education.actWithdrawalSelfApproved');
    case 'education.withdrawal.rejected': return tr('education.actWithdrawalRejected');
    case 'education.withdrawal.cancelled': return tr('education.actWithdrawalCancelled');
    case 'education.class.created': return tr('education.actClassCreated', { name: s(d.name) });
    case 'education.class.updated': return tr('education.actClassUpdated', { name: s(d.name) });
    case 'education.class.enrolled': return tr('education.actEnrolled', { count: n(d.count), name: s(d.name), students: s(d.students) });
    case 'education.class.unenrolled': return tr('education.actUnenrolled', { student: s(d.student), name: s(d.name) });
    case 'education.course.created': return tr('education.actCourseCreated', { subject: s(d.subject), class: s(d.class) });
    case 'education.course.updated': return tr('education.actCourseUpdated', { subject: s(d.subject), class: s(d.class), teacher: s(d.teacher) });
    case 'education.course.deleted': return tr('education.actCourseDeleted', { subject: s(d.subject), class: s(d.class) });
    case 'education.period.added': return tr('education.actPeriodAdded', { subject: s(d.subject), class: s(d.class), day: weekday(d.weekday), start: time(d.start), end: time(d.end) });
    case 'education.period.updated': return tr('education.actPeriodUpdated', { subject: s(d.subject), class: s(d.class), day: weekday(d.weekday), start: time(d.start), end: time(d.end) });
    case 'education.period.removed': return tr('education.actPeriodRemoved', { subject: s(d.subject), class: s(d.class), day: weekday(d.weekday), start: time(d.start) });
    case 'education.attendance.taken': return tr('education.actAttendance', { class: s(d.class), date: date(d.date), present: n(d.present), late: n(d.late), absent: n(d.absent), excused: n(d.excused) });
    case 'education.assessment.created': return tr('education.actAssessmentCreated', { name: s(d.name), course: s(d.course) });
    case 'education.assessment.updated': return tr('education.actAssessmentUpdated', { name: s(d.name), course: s(d.course) });
    case 'education.assessment.deleted': return tr('education.actAssessmentDeleted', { name: s(d.name), course: s(d.course) });
    case 'education.scores.entered': return tr('education.actScores', { count: n(d.count), name: s(d.name), course: s(d.course) });
    case 'education.fee.created': return tr('education.actFeeCreated', { name: s(d.name), amount: usd(s(d.amount)) });
    case 'education.fee.updated': return tr('education.actFeeUpdated', { name: s(d.name), amount: usd(s(d.amount)) });
    case 'education.fee.billed': return tr('education.actFeeBilled', { name: s(d.name), count: n(d.count) });
    case 'education.invoice.created': return tr('education.actInvoiceCreated', { number: s(d.number), student: s(d.student), amount: usd(s(d.amount)) });
    case 'education.invoice.payment': return tr('education.actPayment', { amount: usd(s(d.amount)), method: method(d.method) });
    case 'education.invoice.paid': return tr('education.actInvoicePaid', { number: s(d.number) });
    case 'education.invoice.payment_removed': return tr('education.actPaymentRemoved', { amount: usd(s(d.amount)) });
    case 'education.invoice.voided': return tr('education.actInvoiceVoided', { number: s(d.number), reason: s(d.reason) });
    default: return a.verb;
  }
}
