'use client';

import { AlertTriangle, Banknote, FilePlus2, Receipt, Wallet } from 'lucide-react';
import Link from 'next/link';
import type { ReactNode } from 'react';

import { ExpenseList, InvoiceList } from '@/components/business/lists';
import { SalesExpensesChart } from '@/components/business/sales-chart';
import { Money } from '@/components/business/ui';
import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { useT } from '@/i18n/provider';
import { useBizOverview } from '@/lib/api/business';
import { useOrg } from '@/lib/org-context';
import { cn } from '@/lib/utils';

function Stat({ href, label, value, note, tone }: { href: string; label: string; value: number; note?: string; tone?: 'danger' }) {
  return (
    <Link href={href} className="rounded-lg border bg-card p-4 hover:bg-muted/40">
      <p className="text-sm text-muted-foreground">{label}</p>
      <Money value={value} className={cn('mt-1 text-2xl', tone === 'danger' && value > 0 && 'text-destructive')} strong />
      {note && <p className="mt-1 text-xs text-muted-foreground">{note}</p>}
    </Link>
  );
}

function Panel({ id, title, action, children }: { id: string; title: string; action?: ReactNode; children: ReactNode }) {
  return (
    <section aria-labelledby={id} className="min-w-0 space-y-3">
      <div className="flex items-center justify-between gap-2">
        <h2 id={id} className="text-base font-semibold">{title}</h2>
        {action}
      </div>
      {children}
    </section>
  );
}

/** The business home: what is owed, this month's sales and expenses, recent and soon-due invoices, expenses waiting. */
export default function BusinessHome() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const { data, error, isPending, refetch } = useBizOverview(slug);
  const base = `/${slug}/business`;

  return (
    <>
      <PageHeader title={t('business.homeTitle')} subtitle={t('business.homeSubtitle')}
        actions={<Button asChild size="sm"><Link href={`${base}/invoices/new`}><FilePlus2 className="size-4" />{t('business.newInvoice')}</Link></Button>} />
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? (
        <div className="space-y-6"><div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">{[0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-24" />)}</div><ListSkeleton rows={4} /></div>
      ) : (
        <div className="space-y-8">
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <Stat href={`${base}/invoices?status=ISSUED`} label={t('business.receivable')} value={data.receivable} note={t('business.openInvoices', { count: data.openInvoices })} />
            <Stat href={`${base}/invoices?status=OVERDUE`} label={t('business.overdue')} value={data.overdue} tone="danger" note={t('business.overdueInvoices', { count: data.overdueInvoices })} />
            <Stat href={`${base}/invoices`} label={t('business.salesThisMonth')} value={data.salesThisMonth} />
            {data.expensesThisMonth !== null
              ? <Stat href={`${base}/expenses?status=APPROVED`} label={t('business.expensesThisMonth')} value={data.expensesThisMonth} />
              : <Stat href={`${base}/expenses`} label={t('business.myWaitingExpenses')} value={data.waitingExpenses.reduce((a, e) => a + e.amount, 0)} />}
          </div>
          <SalesExpensesChart months={data.months} />
          <div className="grid gap-8 lg:grid-cols-2">
            <Panel id="due-h" title={t('business.dueSoon')} action={<Link href={`${base}/invoices`} className="text-sm text-primary hover:underline">{t('business.allInvoices')}</Link>}>
              {data.dueSoon.length === 0
                ? <EmptyState icon={Receipt} title={t('business.nothingDueTitle')} body={t('business.nothingDueBody')} />
                : <InvoiceList org={slug} items={data.dueSoon} />}
            </Panel>
            <Panel id="wait-h" title={can('business.manage') ? t('business.expensesWaiting') : t('business.myExpensesWaiting')}
              action={<Link href={`${base}/expenses`} className="text-sm text-primary hover:underline">{t('business.allExpenses')}</Link>}>
              {data.waitingExpenses.length === 0
                ? <EmptyState icon={Banknote} title={t('business.noWaitingTitle')} body={t('business.noWaitingBody')} />
                : <ExpenseList org={slug} items={data.waitingExpenses} showPerson={can('business.manage')} />}
            </Panel>
          </div>
          <Panel id="recent-h" title={t('business.recentInvoices')}>
            {data.recentInvoices.length === 0 ? (
              <EmptyState icon={Wallet} title={t('business.noInvoicesTitle')} body={t('business.noInvoicesBody')}
                action={<Button asChild size="sm" variant="outline"><Link href={`${base}/invoices/new`}>{t('business.newInvoice')}</Link></Button>} />
            ) : <InvoiceList org={slug} items={data.recentInvoices} />}
          </Panel>
          {data.overdueInvoices > 0 && (
            <p className="flex items-center gap-2 text-sm text-destructive"><AlertTriangle className="size-4" />
              <Link href={`${base}/invoices?status=OVERDUE`} className="hover:underline">{t('business.overdueNotice', { count: data.overdueInvoices })}</Link></p>
          )}
        </div>
      )}
    </>
  );
}
