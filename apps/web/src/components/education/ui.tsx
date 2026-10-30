'use client';

import { ChevronLeft, ChevronRight } from 'lucide-react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import type { ReactNode } from 'react';

import { ApprovalBadge } from '@/components/common/badges';
import { educationNav } from '@/components/shell/nav';
import { Button } from '@/components/ui/button';
import { currentLocale, labels, tr } from '@/i18n/core';
import { useT } from '@/i18n/provider';
import {
  ASSESSMENT_KINDS, FEE_STATUSES, type FeeStatus, GENDERS, type Grade, MARKS, type Mark, PAY_METHODS, RELATIONSHIPS, STUDENT_STATUSES,
  type StudentStatus, type Subject, type Withdrawal,
} from '@/lib/api/education';
import { khr, usd } from '@/lib/money';
import { useOrg } from '@/lib/org-context';
import { cn } from '@/lib/utils';

export const STUDENT_STATUS_LABEL = labels(STUDENT_STATUSES, (s) => `education.status_${s}`);
export const GENDER_LABEL = labels(GENDERS, (g) => `education.gender_${g}`);
export const RELATIONSHIP_LABEL = labels(RELATIONSHIPS, (r) => `education.rel_${r}`);
export const MARK_LABEL = labels(MARKS, (m) => `education.mark_${m}`);
export const KIND_LABEL = labels(ASSESSMENT_KINDS, (k) => `education.kind_${k}`);
export const FEE_LABEL = labels([...FEE_STATUSES, 'OVERDUE'] as const, (s) => `education.fee_${s}`);
export const METHOD_LABEL = labels(PAY_METHODS, (m) => `education.method_${m}`);
export const WEEKDAY_LABEL = labels(['1', '2', '3', '4', '5', '6'] as const, (d) => `education.weekday_${d}`);
export const WEEKDAY_SHORT = labels(['1', '2', '3', '4', '5', '6'] as const, (d) => `education.weekdayShort_${d}`);

const km = () => currentLocale() === 'km';
/** A student's name in the current language (Khmer when there is one). */
export const studentName = (s: { nameEn: string; nameKm: string | null }) => (km() && s.nameKm ? s.nameKm : s.nameEn);
export const subjectName = (s: Pick<Subject, 'nameEn' | 'nameKm'> | null | undefined) => (s ? (km() && s.nameKm ? s.nameKm : s.nameEn) : '');
export const gradeName = (g: Pick<Grade, 'nameEn' | 'nameKm'> | null | undefined) => (g ? (km() && g.nameKm ? g.nameKm : g.nameEn) : '');

/** A percentage with one decimal, or a dash. */
export const pct = (n: number | null | undefined) => (n == null ? '—' : `${new Intl.NumberFormat(km() ? 'km-KH' : 'en-US', { minimumFractionDigits: 1, maximumFractionDigits: 1 }).format(n)}%`);
/** A score without trailing zeros ("17.5", "18"). */
export const num = (n: number | null | undefined) => (n == null ? '' : String(Number(n)));

/** USD, with the riel equivalent underneath (`inline`: beside it). */
export function Money({ value, className, inline, strong }: { value: number; className?: string; inline?: boolean; strong?: boolean }) {
  return (
    <span className={cn('tabular', inline ? 'inline-flex flex-wrap items-baseline gap-x-1.5' : 'inline-flex flex-col', className)}>
      <span className={cn(strong && 'font-semibold')}>{usd(value)}</span>
      <span className="text-xs font-normal text-muted-foreground">{khr(value)}</span>
    </span>
  );
}

const pill = 'inline-flex items-center whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-medium';

export function StudentBadge({ status }: { status: StudentStatus }) {
  const tone = status === 'ACTIVE' ? 'bg-success/10 text-success' : status === 'GRADUATED' ? 'bg-primary/10 text-primary' : 'bg-muted text-muted-foreground';
  return <span className={cn(pill, tone)}>{STUDENT_STATUS_LABEL[status]}</span>;
}

const markTone: Record<Mark, string> = {
  PRESENT: 'bg-success/10 text-success',
  LATE: 'bg-warning/10 text-warning',
  ABSENT: 'bg-destructive/10 text-destructive',
  EXCUSED: 'bg-info/10 text-info',
};

export function MarkBadge({ mark }: { mark: Mark | null }) {
  if (!mark) return <span className={cn(pill, 'bg-muted text-muted-foreground')}>{tr('education.notMarked')}</span>;
  return <span className={cn(pill, markTone[mark])}>{MARK_LABEL[mark]}</span>;
}

/** The colour a mark button takes when chosen. */
export const MARK_ACTIVE: Record<Mark, string> = {
  PRESENT: 'border-success bg-success/10 text-success',
  LATE: 'border-warning bg-warning/10 text-warning',
  ABSENT: 'border-destructive bg-destructive/10 text-destructive',
  EXCUSED: 'border-info bg-info/10 text-info',
};

const feeTone: Record<FeeStatus | 'OVERDUE', string> = {
  ISSUED: 'bg-primary/10 text-primary',
  PARTIALLY_PAID: 'bg-info/10 text-info',
  PAID: 'bg-success/10 text-success',
  VOID: 'bg-muted text-muted-foreground line-through',
  OVERDUE: 'bg-destructive/10 text-destructive',
};

export function FeeBadge({ status, overdue }: { status: FeeStatus; overdue?: boolean }) {
  const s = overdue ? 'OVERDUE' : status;
  return <span className={cn(pill, feeTone[s])}>{FEE_LABEL[s]}</span>;
}

/** A letter grade (A–F): A–B green, C–D neutral, E amber, F red. */
export function LetterBadge({ letter }: { letter: string | null }) {
  if (!letter) return <span className="text-muted-foreground">—</span>;
  const tone = letter <= 'B' ? 'bg-success/10 text-success' : letter === 'F' ? 'bg-destructive/10 text-destructive' : letter === 'E' ? 'bg-warning/10 text-warning' : 'bg-muted text-foreground';
  return <span className={cn(pill, 'min-w-6 justify-center', tone)}>{letter}</span>;
}

/** A withdrawal request's state: while waiting, the approval's own status. */
export function WithdrawalBadge({ w }: { w: Pick<Withdrawal, 'status' | 'approvalStatus'> }) {
  if (w.status === 'PENDING') return <ApprovalBadge status={w.approvalStatus ?? 'PENDING'} />;
  if (w.status === 'APPROVED' || w.status === 'REJECTED') return <ApprovalBadge status={w.status} />;
  return <span className={cn(pill, 'bg-muted text-muted-foreground')}>{tr('education.withdrawalCancelled')}</span>;
}

/** The tabs on top of every Education page (on phones, the quickest way between its pages). */
export function EduNav() {
  const ctx = useOrg();
  const { t } = useT();
  const path = usePathname();
  return (
    <nav aria-label={t('education.navLabel')} className="-mx-1 mb-5 overflow-x-auto border-b print:hidden">
      <ul className="flex min-w-max gap-1 px-1">
        {educationNav(ctx).map((p) => {
          const href = `/${ctx.slug}/${p.href}`;
          const active = p.exact ? path === href : path === href || path.startsWith(`${href}/`);
          return (
            <li key={p.href}>
              <Link href={href} aria-current={active ? 'page' : undefined}
                className={cn('-mb-px inline-flex h-10 items-center border-b-2 border-transparent px-3 text-sm font-medium text-muted-foreground hover:text-foreground',
                  active && 'border-primary text-foreground')}>
                {p.label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}

/** "21–40 of 75" with previous / next. Hidden when everything fits on one page. */
export function Pager({ page, size, total, onPage }: { page: number; size: number; total: number; onPage: (p: number) => void }) {
  const { t } = useT();
  if (total <= size) return null;
  const pages = Math.ceil(total / size);
  return (
    <div className="mt-3 flex items-center justify-between gap-2 text-sm text-muted-foreground">
      <span className="tabular">{t('education.pageOf', { from: page * size + 1, to: Math.min(total, (page + 1) * size), total })}</span>
      <div className="flex gap-1">
        <Button variant="outline" size="icon-sm" disabled={page === 0} onClick={() => onPage(page - 1)} aria-label={t('education.previousPage')}><ChevronLeft className="size-4" /></Button>
        <Button variant="outline" size="icon-sm" disabled={page + 1 >= pages} onClick={() => onPage(page + 1)} aria-label={t('education.nextPage')}><ChevronRight className="size-4" /></Button>
      </div>
    </div>
  );
}

/** Breadcrumb back to a list page. */
export function Crumb({ href, label, current }: { href: string; label: string; current: string }) {
  return (
    <nav aria-label={tr('education.breadcrumb')} className="mb-2 flex min-w-0 items-center gap-1 text-sm text-muted-foreground print:hidden">
      <Link href={href} className="shrink-0 hover:text-foreground">{label}</Link><ChevronRight className="size-3.5 shrink-0" /><span className="truncate">{current}</span>
    </nav>
  );
}

/** A label and its value in a detail panel. */
export function Field({ label, children, wide }: { label: string; children: ReactNode; wide?: boolean }) {
  return (
    <div className={cn('min-w-0', wide && 'sm:col-span-2')}>
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="mt-1 break-words text-sm">{children}</dd>
    </div>
  );
}

/** A titled section of a page. */
export function Panel({ id, title, action, children, className }: { id: string; title: string; action?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <section aria-labelledby={id} className={cn('min-w-0 space-y-3', className)}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 id={id} className="text-base font-semibold">{title}</h2>
        {action}
      </div>
      {children}
    </section>
  );
}

/** A red line for a form's error that belongs to no field. */
export function ErrorLine({ error }: { error: string | null }) {
  return error ? <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p> : null;
}

/** "(optional)" after a label. */
export function Optional() {
  return <span className="font-normal text-muted-foreground">{tr('common.optional')}</span>;
}
