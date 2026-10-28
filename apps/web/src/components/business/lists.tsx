'use client';

import Link from 'next/link';

import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useT } from '@/i18n/provider';
import type { Expense, InvoiceSummary, QuoteSummary } from '@/lib/api/business';
import { day } from '@/lib/format';
import { usd } from '@/lib/money';

import { categoryName, ExpenseBadge, InvoiceBadge, Money, QuoteBadge } from './ui';

/** Invoices: a table on wide screens, cards on phones; each opens its page. */
export function InvoiceList({ org, items, hideCustomer }: { org: string; items: InvoiceSummary[]; hideCustomer?: boolean }) {
  const { t } = useT();
  const href = (i: InvoiceSummary) => `/${org}/business/invoices/${i.id}`;
  return (
    <>
      <div className="hidden rounded-lg border bg-card md:block">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t('business.number')}</TableHead>
              {!hideCustomer && <TableHead>{t('business.customer')}</TableHead>}
              <TableHead>{t('business.status')}</TableHead>
              <TableHead>{t('business.issueDate')}</TableHead>
              <TableHead>{t('business.dueDate')}</TableHead>
              <TableHead className="text-right">{t('business.total')}</TableHead>
              <TableHead className="text-right">{t('business.balance')}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {items.map((i) => (
              <TableRow key={i.id}>
                <TableCell className="font-medium"><Link href={href(i)} className="text-primary hover:underline">{i.number ?? t('business.draftInvoice')}</Link></TableCell>
                {!hideCustomer && <TableCell className="max-w-56 truncate">{i.customer.name}</TableCell>}
                <TableCell><InvoiceBadge status={i.status} overdue={i.overdue} /></TableCell>
                <TableCell className="whitespace-nowrap">{i.issueDate ? day(i.issueDate) : '—'}</TableCell>
                <TableCell className="whitespace-nowrap">{i.dueDate ? day(i.dueDate) : '—'}</TableCell>
                <TableCell className="text-right tabular">{usd(i.total)}</TableCell>
                <TableCell className="text-right">{i.status === 'DRAFT' || i.status === 'VOID' ? <span className="text-muted-foreground">—</span> : <Money value={i.balance} className="items-end" />}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
      <ul className="divide-y rounded-lg border bg-card md:hidden">
        {items.map((i) => (
          <li key={i.id}>
            <Link href={href(i)} className="flex items-start justify-between gap-3 px-4 py-3 hover:bg-muted/50">
              <span className="min-w-0">
                <span className="block text-sm font-medium">{i.number ?? t('business.draftInvoice')}</span>
                {!hideCustomer && <span className="block truncate text-sm">{i.customer.name}</span>}
                <span className="block text-xs text-muted-foreground">{i.dueDate ? t('business.dueOn', { date: day(i.dueDate) }) : t('business.notIssued')}</span>
              </span>
              <span className="flex shrink-0 flex-col items-end gap-1">
                <span className="text-sm font-medium tabular">{usd(i.total)}</span>
                <InvoiceBadge status={i.status} overdue={i.overdue} />
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </>
  );
}

export function QuoteList({ org, items, hideCustomer }: { org: string; items: QuoteSummary[]; hideCustomer?: boolean }) {
  const { t } = useT();
  const href = (q: QuoteSummary) => `/${org}/business/quotes/${q.id}`;
  return (
    <>
      <div className="hidden rounded-lg border bg-card md:block">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t('business.number')}</TableHead>
              {!hideCustomer && <TableHead>{t('business.customer')}</TableHead>}
              <TableHead>{t('business.status')}</TableHead>
              <TableHead>{t('business.quoteDate')}</TableHead>
              <TableHead>{t('business.validUntil')}</TableHead>
              <TableHead className="text-right">{t('business.total')}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {items.map((q) => (
              <TableRow key={q.id}>
                <TableCell className="font-medium"><Link href={href(q)} className="text-primary hover:underline">{q.number}</Link></TableCell>
                {!hideCustomer && <TableCell className="max-w-56 truncate">{q.customer.name}</TableCell>}
                <TableCell><QuoteBadge status={q.status} expired={q.expired} /></TableCell>
                <TableCell className="whitespace-nowrap">{day(q.issueDate)}</TableCell>
                <TableCell className="whitespace-nowrap">{q.validUntil ? day(q.validUntil) : '—'}</TableCell>
                <TableCell className="text-right"><Money value={q.total} className="items-end" /></TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
      <ul className="divide-y rounded-lg border bg-card md:hidden">
        {items.map((q) => (
          <li key={q.id}>
            <Link href={href(q)} className="flex items-start justify-between gap-3 px-4 py-3 hover:bg-muted/50">
              <span className="min-w-0">
                <span className="block text-sm font-medium">{q.number}</span>
                {!hideCustomer && <span className="block truncate text-sm">{q.customer.name}</span>}
                <span className="block text-xs text-muted-foreground">{day(q.issueDate)}</span>
              </span>
              <span className="flex shrink-0 flex-col items-end gap-1">
                <span className="text-sm font-medium tabular">{usd(q.total)}</span>
                <QuoteBadge status={q.status} expired={q.expired} />
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </>
  );
}

/** Expenses as rows that wrap on phones; `showPerson` for lists of everyone's. */
export function ExpenseList({ org, items, showPerson }: { org: string; items: Expense[]; showPerson?: boolean }) {
  return (
    <ul className="divide-y rounded-lg border bg-card">
      {items.map((e) => (
        <li key={e.id}>
          <Link href={`/${org}/business/expenses/${e.id}`} className="flex items-start justify-between gap-3 px-4 py-3 hover:bg-muted/50">
            <span className="min-w-0">
              <span className="block truncate text-sm font-medium">{categoryName(e.category)}{e.supplier ? ` · ${e.supplier}` : ''}</span>
              <span className="block truncate text-xs text-muted-foreground">{day(e.spentOn)}{showPerson && e.person ? ` · ${e.person.name}` : ''}</span>
            </span>
            <span className="flex shrink-0 flex-col items-end gap-1">
              <span className="text-sm font-medium tabular">{usd(e.amount)}</span>
              <ExpenseBadge expense={e} />
            </span>
          </Link>
        </li>
      ))}
    </ul>
  );
}
