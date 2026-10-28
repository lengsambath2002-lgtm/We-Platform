'use client';

import { ChevronLeft, ChevronRight } from 'lucide-react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import type { ReactNode } from 'react';

import { ApprovalBadge } from '@/components/common/badges';
import { businessNav } from '@/components/shell/nav';
import { Button } from '@/components/ui/button';
import { currentLocale, labels, tr } from '@/i18n/core';
import { useT } from '@/i18n/provider';
import {
  type Category, EXPENSE_STATUSES, type Expense, INVOICE_STATUSES, type InvoiceStatus, PAYMENT_METHODS, type Product, QUOTE_STATUSES, type QuoteStatus,
} from '@/lib/api/business';
import { khr, usd } from '@/lib/money';
import { useOrg } from '@/lib/org-context';
import { cn } from '@/lib/utils';

export const QUOTE_LABEL = labels([...QUOTE_STATUSES, 'EXPIRED'] as const, (s) => `business.quote_${s}`);
export const INVOICE_LABEL = labels([...INVOICE_STATUSES, 'OVERDUE'] as const, (s) => `business.invoice_${s}`);
export const EXPENSE_LABEL = labels(EXPENSE_STATUSES, (s) => `business.expense_${s}`);
export const METHOD_LABEL = labels(PAYMENT_METHODS, (m) => `business.method_${m}`);

/** A product's or category's name in the current language. */
export const productName = (p: Pick<Product, 'nameEn' | 'nameKm'>) => (currentLocale() === 'km' && p.nameKm ? p.nameKm : p.nameEn);
export const categoryName = (c: Pick<Category, 'nameEn' | 'nameKm'> | null) => (c ? (currentLocale() === 'km' && c.nameKm ? c.nameKm : c.nameEn) : '');

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

const invoiceTone: Record<InvoiceStatus | 'OVERDUE', string> = {
  DRAFT: 'bg-muted text-muted-foreground',
  ISSUED: 'bg-primary/10 text-primary',
  PARTIALLY_PAID: 'bg-info/10 text-info',
  PAID: 'bg-success/10 text-success',
  VOID: 'bg-muted text-muted-foreground line-through',
  OVERDUE: 'bg-destructive/10 text-destructive',
};

/** The invoice's state; an unpaid invoice past its due date shows "Overdue". */
export function InvoiceBadge({ status, overdue }: { status: InvoiceStatus; overdue?: boolean }) {
  const s = overdue ? 'OVERDUE' : status;
  return <span className={cn(pill, invoiceTone[s])}>{INVOICE_LABEL[s]}</span>;
}

const quoteTone: Record<QuoteStatus | 'EXPIRED', string> = {
  DRAFT: 'bg-muted text-muted-foreground',
  SENT: 'bg-primary/10 text-primary',
  ACCEPTED: 'bg-success/10 text-success',
  DECLINED: 'bg-destructive/10 text-destructive',
  EXPIRED: 'bg-warning/10 text-warning',
};

export function QuoteBadge({ status, expired }: { status: QuoteStatus; expired?: boolean }) {
  const s = expired ? 'EXPIRED' : status;
  return <span className={cn(pill, quoteTone[s])}>{QUOTE_LABEL[s]}</span>;
}

/** The expense's state: while waiting, the approval's own status (e.g. "Changes requested"). */
export function ExpenseBadge({ expense }: { expense: Pick<Expense, 'status' | 'approvalStatus'> }) {
  if (expense.status === 'PENDING') return <ApprovalBadge status={expense.approvalStatus ?? 'PENDING'} />;
  if (expense.status === 'APPROVED' || expense.status === 'REJECTED') return <ApprovalBadge status={expense.status} />;
  return <span className={cn(pill, expense.status === 'PAID' ? 'bg-success/10 text-success' : 'bg-muted text-muted-foreground')}>{EXPENSE_LABEL[expense.status]}</span>;
}

/** The tabs on top of every business page (on phones, the quickest way between its pages). */
export function BizNav() {
  const ctx = useOrg();
  const { t } = useT();
  const path = usePathname();
  return (
    <nav aria-label={t('business.navLabel')} className="-mx-1 mb-5 overflow-x-auto border-b print:hidden">
      <ul className="flex min-w-max gap-1 px-1">
        {businessNav(ctx).map((p) => {
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
      <span className="tabular">{t('business.pageOf', { from: page * size + 1, to: Math.min(total, (page + 1) * size), total })}</span>
      <div className="flex gap-1">
        <Button variant="outline" size="icon-sm" disabled={page === 0} onClick={() => onPage(page - 1)} aria-label={t('business.previousPage')}><ChevronLeft className="size-4" /></Button>
        <Button variant="outline" size="icon-sm" disabled={page + 1 >= pages} onClick={() => onPage(page + 1)} aria-label={t('business.nextPage')}><ChevronRight className="size-4" /></Button>
      </div>
    </div>
  );
}

/** Breadcrumb back to a list page. */
export function Crumb({ href, label, current }: { href: string; label: string; current: string }) {
  return (
    <nav aria-label={tr('business.breadcrumb')} className="mb-2 flex min-w-0 items-center gap-1 text-sm text-muted-foreground print:hidden">
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
