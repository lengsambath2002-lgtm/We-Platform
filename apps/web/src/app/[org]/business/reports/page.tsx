'use client';

import { parseISO } from 'date-fns';
import { Download } from 'lucide-react';
import { useState } from 'react';
import { toast } from 'sonner';

import { ReportsTabs } from '@/components/common/merged-tabs';
import { PageHeader } from '@/components/common/page-header';
import { ErrorState, ListSkeleton, PermissionDenied } from '@/components/common/states';
import { categoryName } from '@/components/business/ui';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableFooter, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { crmApi, useFinanceReport } from '@/lib/api/crm';
import { fmt } from '@/lib/format';
import { usd } from '@/lib/money';
import { useOrg } from '@/lib/org-context';
import { cn } from '@/lib/utils';

/** Owners and Admins: a year's profit and loss by month, what customers owe by age, sales by customer, expenses by category. */
export default function FinanceReports() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const thisYear = new Date().getFullYear();
  const [year, setYear] = useState(thisYear);
  const manage = can('business.manage');
  const { data: r, error, isPending, refetch } = useFinanceReport(slug, year, manage);
  if (!manage) return <PermissionDenied />;
  const csv = (which: 'months' | 'aging' | 'customers' | 'expenses') => (
    <Button variant="outline" size="sm" onClick={() => crmApi.downloadReport(slug, which, year).catch((e) => toast.error(errorMessage(e)))}><Download className="size-4" />CSV</Button>
  );
  const head = (id: string, title: string, which: 'months' | 'aging' | 'customers' | 'expenses', body?: string) => (
    <div className="mb-3 flex flex-wrap items-start justify-between gap-2"><div><h2 id={id} className="text-base font-semibold">{title}</h2>{body && <p className="text-sm text-muted-foreground">{body}</p>}</div>{csv(which)}</div>
  );
  const money = (n: number, cls?: string) => <TableCell className={cn('text-right tabular', cls)}>{usd(n)}</TableCell>;

  return (
    <>
      <ReportsTabs />
      <PageHeader title={t('business.reportsTitle')} subtitle={t('business.reportsSubtitle')}
        actions={<Select value={String(year)} onValueChange={(v) => setYear(Number(v))}><SelectTrigger className="w-28" aria-label={t('business.year')}><SelectValue /></SelectTrigger>
          <SelectContent>{[thisYear, thisYear - 1, thisYear - 2].map((y) => <SelectItem key={y} value={String(y)}>{y}</SelectItem>)}</SelectContent></Select>} />
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={8} /> : (
        <div className="space-y-6">
          <section aria-labelledby="pl-h" className="rounded-lg border bg-card p-4">
            {head('pl-h', t('business.profitLoss'), 'months', t('business.profitLossBody'))}
            <div className="overflow-x-auto">
              <Table>
                <TableHeader><TableRow><TableHead>{t('business.month')}</TableHead><TableHead className="text-right">{t('business.sales')}</TableHead><TableHead className="text-right">{t('business.collected')}</TableHead><TableHead className="text-right">{t('business.expensesCol')}</TableHead><TableHead className="text-right">{t('business.profit')}</TableHead></TableRow></TableHeader>
                <TableBody>{r.months.map((m) => (
                  <TableRow key={m.month}><TableCell>{fmt(parseISO(m.month), 'MMMM')}</TableCell>{money(m.sales)}{money(m.collected)}{money(m.expenses)}{money(m.profit, m.profit < 0 ? 'text-destructive' : undefined)}</TableRow>
                ))}</TableBody>
                <TableFooter><TableRow><TableCell className="font-medium">{t('business.total')}</TableCell>{money(r.totals.sales)}{money(r.totals.collected)}{money(r.totals.expenses)}{money(r.totals.profit, cn('font-semibold', r.totals.profit < 0 && 'text-destructive'))}</TableRow></TableFooter>
              </Table>
            </div>
          </section>

          <section aria-labelledby="ag-h" className="rounded-lg border bg-card p-4">
            {head('ag-h', t('business.agingTitle'), 'aging', t('business.agingBody'))}
            <div className="mb-3 grid grid-cols-2 gap-3 sm:grid-cols-6">
              {([['agingCurrent', r.aging.current], ['aging1', r.aging.days1to30], ['aging31', r.aging.days31to60], ['aging61', r.aging.days61to90], ['aging90', r.aging.over90], ['total', r.aging.total]] as const).map(([k, v]) => (
                <div key={k} className="rounded-md bg-muted/40 p-2"><p className="text-xs text-muted-foreground">{t(`business.${k}`)}</p><p className={cn('font-semibold tabular', k === 'aging90' && v > 0 && 'text-destructive')}>{usd(v)}</p></div>
              ))}
            </div>
            {r.agingByCustomer.length > 0 && (
              <div className="overflow-x-auto">
                <Table>
                  <TableHeader><TableRow><TableHead>{t('business.customer')}</TableHead><TableHead className="text-right">{t('business.agingCurrent')}</TableHead><TableHead className="text-right">{t('business.aging1')}</TableHead><TableHead className="text-right">{t('business.aging31')}</TableHead><TableHead className="text-right">{t('business.aging61')}</TableHead><TableHead className="text-right">{t('business.aging90')}</TableHead><TableHead className="text-right">{t('business.total')}</TableHead></TableRow></TableHeader>
                  <TableBody>{r.agingByCustomer.map((a) => (
                    <TableRow key={a.customerId}><TableCell>{a.customer}</TableCell>{money(a.aging.current)}{money(a.aging.days1to30)}{money(a.aging.days31to60)}{money(a.aging.days61to90)}{money(a.aging.over90, a.aging.over90 > 0 ? 'text-destructive' : undefined)}{money(a.aging.total, 'font-medium')}</TableRow>
                  ))}</TableBody>
                </Table>
              </div>
            )}
          </section>

          <div className="grid gap-6 lg:grid-cols-2">
            <section aria-labelledby="cs-h" className="rounded-lg border bg-card p-4">
              {head('cs-h', t('business.salesByCustomer'), 'customers')}
              {r.customers.length === 0 ? <p className="text-sm text-muted-foreground">{t('business.nothingThisYear')}</p> : (
                <Table>
                  <TableHeader><TableRow><TableHead>{t('business.customer')}</TableHead><TableHead className="text-right">{t('business.invoiced')}</TableHead><TableHead className="text-right">{t('business.paidCol')}</TableHead></TableRow></TableHeader>
                  <TableBody>{r.customers.map((c) => <TableRow key={c.customerId}><TableCell>{c.customer}</TableCell>{money(c.invoiced)}{money(c.paid)}</TableRow>)}</TableBody>
                </Table>
              )}
            </section>
            <section aria-labelledby="ec-h" className="rounded-lg border bg-card p-4">
              {head('ec-h', t('business.expensesByCategory'), 'expenses')}
              {r.categories.length === 0 ? <p className="text-sm text-muted-foreground">{t('business.nothingThisYear')}</p> : (
                <Table>
                  <TableHeader><TableRow><TableHead>{t('business.category')}</TableHead><TableHead className="text-right">{t('business.total')}</TableHead></TableRow></TableHeader>
                  <TableBody>{r.categories.map((c) => <TableRow key={c.categoryId}><TableCell>{categoryName(c)}</TableCell>{money(c.total)}</TableRow>)}</TableBody>
                </Table>
              )}
            </section>
          </div>
        </div>
      )}
    </>
  );
}
