'use client';

import { parseISO } from 'date-fns';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import type { ReactNode } from 'react';

import { serviceNav } from '@/components/shell/nav';
import { Button } from '@/components/ui/button';
import { labels, tr } from '@/i18n/core';
import { useT } from '@/i18n/provider';
import {
  ASSET_STATUSES, type AssetStatus, CATEGORIES, type SlaState, TICKET_STATUSES, type TicketStatus, WO_STATUSES, type WoStatus,
} from '@/lib/api/service';
import { fmt } from '@/lib/format';
import { khr, usd } from '@/lib/money';
import { useOrg } from '@/lib/org-context';
import { cn } from '@/lib/utils';

export const TICKET_LABEL = labels(TICKET_STATUSES, (s) => `service.ticket_${s}`);
export const WO_LABEL = labels(WO_STATUSES, (s) => `service.wo_${s}`);
export const CATEGORY_LABEL = labels(CATEGORIES, (c) => `service.cat_${c}`);
export const ASSET_LABEL = labels(ASSET_STATUSES, (s) => `service.asset_${s}`);
export const SLA_LABEL = labels(['ON_TRACK', 'AT_RISK', 'BREACHED', 'MET'] as const, (s) => `service.sla_${s}`);

const pill = 'inline-flex items-center whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-medium';

const ticketTone: Record<TicketStatus, string> = {
  NEW: 'bg-primary/10 text-primary',
  TRIAGED: 'bg-info/10 text-info',
  IN_PROGRESS: 'bg-primary/10 text-primary',
  WAITING: 'bg-warning/10 text-warning',
  RESOLVED: 'bg-success/10 text-success',
  CLOSED: 'bg-muted text-muted-foreground',
};

export function TicketBadge({ status }: { status: TicketStatus }) {
  return <span className={cn(pill, ticketTone[status])}>{TICKET_LABEL[status]}</span>;
}

const woTone: Record<WoStatus, string> = {
  DRAFT: 'bg-muted text-muted-foreground',
  SCHEDULED: 'bg-primary/10 text-primary',
  IN_PROGRESS: 'bg-info/10 text-info',
  ON_HOLD: 'bg-warning/10 text-warning',
  COMPLETED: 'bg-success/10 text-success',
  CANCELLED: 'bg-muted text-muted-foreground line-through',
};

export function WoBadge({ status }: { status: WoStatus }) {
  return <span className={cn(pill, woTone[status])}>{WO_LABEL[status]}</span>;
}

const slaTone: Record<SlaState, string> = {
  ON_TRACK: 'bg-muted text-muted-foreground',
  AT_RISK: 'bg-warning/10 text-warning',
  BREACHED: 'bg-destructive/10 text-destructive',
  MET: 'bg-success/10 text-success',
};

/** The SLA state the server derived (breached and at risk stand out; on track stays quiet). */
export function SlaBadge({ sla, className }: { sla: SlaState; className?: string }) {
  return <span className={cn(pill, slaTone[sla], className)} title={tr('service.slaTitle')}>{SLA_LABEL[sla]}</span>;
}

export function AssetBadge({ status }: { status: AssetStatus }) {
  const tone = status === 'ACTIVE' ? 'bg-success/10 text-success' : status === 'IN_REPAIR' ? 'bg-warning/10 text-warning' : 'bg-muted text-muted-foreground';
  return <span className={cn(pill, tone)}>{ASSET_LABEL[status]}</span>;
}

/** USD, with the riel equivalent underneath (`inline`: beside it). */
export function Money({ value, className, inline, strong }: { value: number; className?: string; inline?: boolean; strong?: boolean }) {
  return (
    <span className={cn('tabular', inline ? 'inline-flex flex-wrap items-baseline gap-x-1.5' : 'inline-flex flex-col', className)}>
      <span className={cn(strong && 'font-semibold')}>{usd(value)}</span>
      <span className="text-xs font-normal text-muted-foreground">{khr(value)}</span>
    </span>
  );
}

/** "Thu 30 Oct, 09:00–11:00" (same day) or both ends in full. */
export function windowText(start: string | null, end: string | null) {
  if (!start) return tr('service.notScheduled');
  const a = parseISO(start);
  if (!end) return fmt(a, 'EEE d MMM, HH:mm');
  const b = parseISO(end);
  return a.toDateString() === b.toDateString() ? `${fmt(a, 'EEE d MMM, HH:mm')}–${fmt(b, 'HH:mm')}` : `${fmt(a, 'd MMM HH:mm')} – ${fmt(b, 'd MMM HH:mm')}`;
}

/** "4 h", "2 days", "45 min" for SLA targets in minutes. */
export function minutesText(m: number) {
  if (m % 1440 === 0) return tr('service.daysN', { n: m / 1440 });
  if (m % 60 === 0) return tr('service.hoursN', { n: m / 60 });
  return tr('service.minutesN', { n: m });
}

/** The tabs on top of every service page (on phones, the quickest way between its pages). */
export function ServiceNav() {
  const ctx = useOrg();
  const { t } = useT();
  const path = usePathname();
  return (
    <nav aria-label={t('service.navLabel')} className="-mx-1 mb-5 overflow-x-auto border-b">
      <ul className="flex min-w-max gap-1 px-1">
        {serviceNav(ctx).map((p) => {
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
      <span className="tabular">{t('service.pageOf', { from: page * size + 1, to: Math.min(total, (page + 1) * size), total })}</span>
      <div className="flex gap-1">
        <Button variant="outline" size="icon-sm" disabled={page === 0} onClick={() => onPage(page - 1)} aria-label={t('service.previousPage')}><ChevronLeft className="size-4" /></Button>
        <Button variant="outline" size="icon-sm" disabled={page + 1 >= pages} onClick={() => onPage(page + 1)} aria-label={t('service.nextPage')}><ChevronRight className="size-4" /></Button>
      </div>
    </div>
  );
}

/** Breadcrumb back to a list page. */
export function Crumb({ href, label, current }: { href: string; label: string; current: string }) {
  return (
    <nav aria-label={tr('service.breadcrumb')} className="mb-2 flex min-w-0 items-center gap-1 text-sm text-muted-foreground">
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

/** A titled section of a page with an optional action on the right. */
export function Panel({ id, title, action, children, className }: { id: string; title: string; action?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <section aria-labelledby={id} className={cn('min-w-0 space-y-3', className)}>
      <div className="flex items-center justify-between gap-2">
        <h2 id={id} className="text-base font-semibold">{title}</h2>
        {action}
      </div>
      {children}
    </section>
  );
}

/** A compact number that links somewhere (dashboard stats). */
export function Stat({ href, label, value, note, tone }: { href: string; label: string; value: ReactNode; note?: string; tone?: 'danger' | 'warning' }) {
  return (
    <Link href={href} className="rounded-lg border bg-card p-4 hover:bg-muted/40">
      <p className="text-sm text-muted-foreground">{label}</p>
      <p className={cn('mt-1 text-2xl font-semibold tabular', tone === 'danger' && 'text-destructive', tone === 'warning' && 'text-warning')}>{value}</p>
      {note && <p className="mt-1 text-xs text-muted-foreground">{note}</p>}
    </Link>
  );
}

/** A `datetime-local` value from an ISO instant (local time), and back. */
export const toLocalInput = (iso: string | null) => {
  if (!iso) return '';
  const d = new Date(iso);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
};
export const fromLocalInput = (v: string) => (v ? new Date(v).toISOString() : null);

/** Today as yyyy-MM-dd (local). */
export const todayIso = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};
