'use client';

import { Banknote, Plus, SearchX } from 'lucide-react';
import { useSearchParams } from 'next/navigation';
import { Suspense, useState } from 'react';

import { ExpenseDialog } from '@/components/business/expense-dialog';
import { ExpenseList } from '@/components/business/lists';
import { EXPENSE_LABEL, Pager } from '@/components/business/ui';
import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { useT } from '@/i18n/provider';
import { EXPENSE_STATUSES, useExpenses } from '@/lib/api/business';
import { useOrg } from '@/lib/org-context';

const SIZE = 25;
const ALL = 'all';

/** Expenses: members see their own; admins everyone's (or only their own). Filter by status. */
function Expenses() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const manage = can('business.manage');
  const params = useSearchParams();
  const initial = params.get('status');
  const [status, setStatus] = useState<string>(EXPENSE_STATUSES.includes(initial as never) ? initial! : ALL);
  const [whose, setWhose] = useState<'all' | 'mine'>('all');
  const [page, setPage] = useState(0);
  const [adding, setAdding] = useState(false);
  const { data, error, isPending, refetch } = useExpenses(slug, { status: status === ALL ? undefined : status, mine: manage && whose === 'mine', page, size: SIZE });
  const filtered = status !== ALL || whose !== 'all';

  return (
    <>
      <PageHeader title={t('business.expensesTitle')} subtitle={manage ? t('business.expensesSubtitleAdmin') : t('business.expensesSubtitle')}
        actions={<Button size="sm" onClick={() => setAdding(true)}><Plus className="size-4" />{t('business.newExpense')}</Button>} />
      <div className="mb-4 grid grid-cols-2 gap-3 sm:flex sm:items-end">
        <div className="space-y-1">
          <Label htmlFor="e-s" className="text-xs text-muted-foreground">{t('business.status')}</Label>
          <Select value={status} onValueChange={(v) => { setStatus(v); setPage(0); }}>
            <SelectTrigger id="e-s" className="w-full sm:w-40"><SelectValue /></SelectTrigger>
            <SelectContent><SelectItem value={ALL}>{t('business.anyStatus')}</SelectItem>{EXPENSE_STATUSES.map((s) => <SelectItem key={s} value={s}>{EXPENSE_LABEL[s]}</SelectItem>)}</SelectContent>
          </Select>
        </div>
        {manage && (
          <div className="space-y-1">
            <Label htmlFor="e-w" className="text-xs text-muted-foreground">{t('business.whose')}</Label>
            <Select value={whose} onValueChange={(v) => { setWhose(v as 'all' | 'mine'); setPage(0); }}>
              <SelectTrigger id="e-w" className="w-full sm:w-40"><SelectValue /></SelectTrigger>
              <SelectContent><SelectItem value="all">{t('business.everyone')}</SelectItem><SelectItem value="mine">{t('business.onlyMine')}</SelectItem></SelectContent>
            </Select>
          </div>
        )}
      </div>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={6} />
        : data.items.length === 0 ? (filtered
          ? <EmptyState icon={SearchX} title={t('business.noMatchTitle')} body={t('business.noMatchBody')} />
          : <EmptyState icon={Banknote} title={t('business.noExpensesTitle')} body={t('business.noExpensesBody')}
            action={<Button size="sm" variant="outline" onClick={() => setAdding(true)}>{t('business.newExpense')}</Button>} />)
          : <><ExpenseList org={slug} items={data.items} showPerson={manage} /><Pager page={page} size={SIZE} total={data.total} onPage={setPage} /></>}
      {adding && <ExpenseDialog org={slug} open onOpenChange={setAdding} />}
    </>
  );
}

export default function Page() {
  return <Suspense><Expenses /></Suspense>;
}
