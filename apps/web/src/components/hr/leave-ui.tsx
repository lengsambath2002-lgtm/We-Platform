'use client';

import { parseISO } from 'date-fns';
import { CalendarClock, ChevronRight } from 'lucide-react';
import Link from 'next/link';

import { ApprovalBadge } from '@/components/common/badges';
import { PersonAvatar } from '@/components/common/person';
import { currentLocale, tr } from '@/i18n/core';
import type { Balance, Leave, LeaveType } from '@/lib/api/hr';
import { fmt } from '@/lib/format';
import { cn } from '@/lib/utils';

/** A leave type's name in the current language. */
export const typeName = (t: Pick<LeaveType, 'nameEn' | 'nameKm'>) => (currentLocale() === 'km' && t.nameKm ? t.nameKm : t.nameEn);

/** "2.5 days", "1 day" in the current language. */
export const daysText = (n: number) => tr('hr.days', { count: n });

/** "Mon 3 Nov – Wed 5 Nov 2026" (with "afternoon" / "morning" for half days). */
export function rangeText(l: { startDate: string; endDate: string; halfStart: boolean; halfEnd: boolean }) {
  const s = parseISO(l.startDate);
  const e = parseISO(l.endDate);
  const sameYear = s.getFullYear() === e.getFullYear();
  if (l.startDate === l.endDate) {
    const half = l.halfStart ? ` (${tr('hr.afternoon')})` : l.halfEnd ? ` (${tr('hr.morning')})` : '';
    return `${fmt(s, 'EEE d MMM yyyy')}${half}`;
  }
  const from = `${fmt(s, sameYear ? 'EEE d MMM' : 'EEE d MMM yyyy')}${l.halfStart ? ` (${tr('hr.afternoon')})` : ''}`;
  const to = `${fmt(e, 'EEE d MMM yyyy')}${l.halfEnd ? ` (${tr('hr.morning')})` : ''}`;
  return `${from} – ${to}`;
}

/** The leave's state: the approval's own status while it is open, "Cancelled" after a cancellation. */
export function LeaveBadge({ leave }: { leave: Pick<Leave, 'status' | 'approvalStatus'> }) {
  if (leave.status === 'CANCELLED') {
    return <span className="inline-flex items-center rounded-full bg-muted px-2 py-0.5 text-xs font-medium text-muted-foreground">{tr('hr.status_CANCELLED')}</span>;
  }
  return <ApprovalBadge status={leave.status === 'PENDING' ? (leave.approvalStatus ?? 'PENDING') : leave.status} />;
}

/** One card per leave type: what is left (or "Unlimited"), with used and pending underneath. */
export function BalanceCards({ balances }: { balances: Balance[] }) {
  return (
    <ul className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
      {balances.map((b) => (
        <li key={b.type.id} className="rounded-lg border bg-card p-4">
          <div className="flex items-start justify-between gap-2">
            <p className="min-w-0 truncate text-sm font-medium">{typeName(b.type)}</p>
            <span className="shrink-0 text-xs text-muted-foreground">{tr(b.type.paid ? 'hr.paid' : 'hr.unpaid')}</span>
          </div>
          {b.remaining === null ? (
            <p className="mt-2 text-2xl font-semibold tabular">{tr('hr.unlimited')}</p>
          ) : (
            <p className="mt-2 text-2xl font-semibold tabular">
              {b.remaining}<span className="ml-1 text-sm font-normal text-muted-foreground">{tr('hr.ofDaysLeft', { total: b.allowance ?? 0 })}</span>
            </p>
          )}
          <p className="mt-1 text-xs text-muted-foreground">
            {tr('hr.usedPending', { used: b.used, pending: b.pending })}
            {b.adjusted !== 0 && ` · ${tr('hr.adjustedBy', { days: b.adjusted > 0 ? `+${b.adjusted}` : b.adjusted })}`}
          </p>
          {b.allowance !== null && b.allowance > 0 && (
            <div className="mt-3 flex h-1.5 overflow-hidden rounded-full bg-muted" aria-hidden>
              <span className="bg-primary" style={{ width: `${Math.min(100, (b.used / b.allowance) * 100)}%` }} />
              <span className="bg-primary/40" style={{ width: `${Math.min(100, (b.pending / b.allowance) * 100)}%` }} />
            </div>
          )}
        </li>
      ))}
    </ul>
  );
}

/** Leave requests as rows that wrap on phones; each opens its page. `showPerson` for lists of everyone's. */
export function LeaveList({ org, leaves, showPerson }: { org: string; leaves: Leave[]; showPerson?: boolean }) {
  return (
    <ul className="divide-y rounded-lg border bg-card">
      {leaves.map((l) => (
        <li key={l.id}>
          <Link href={`/${org}/hr/leave/${l.id}`} className="flex items-center gap-3 px-4 py-3 hover:bg-muted/50">
            {showPerson ? <PersonAvatar person={l.person} className="size-8" /> : (
              <span className="grid size-8 shrink-0 place-items-center rounded-md bg-muted text-muted-foreground"><CalendarClock className="size-4" aria-hidden /></span>
            )}
            <span className="min-w-0 flex-1">
              <span className="block truncate text-sm font-medium">
                {showPerson ? `${l.person.name} · ` : ''}{typeName(l.type)}
              </span>
              <span className="block text-xs text-muted-foreground">{rangeText(l)} · {daysText(l.days)}</span>
            </span>
            <span className="flex shrink-0 items-center gap-1">
              <LeaveBadge leave={l} />
              <ChevronRight className={cn('size-4 text-muted-foreground', 'hidden sm:block')} aria-hidden />
            </span>
          </Link>
        </li>
      ))}
    </ul>
  );
}
