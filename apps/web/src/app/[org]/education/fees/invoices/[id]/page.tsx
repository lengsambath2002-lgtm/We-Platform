'use client';

import { Ban, Banknote, Trash2 } from 'lucide-react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useState } from 'react';
import { toast } from 'sonner';

import { PaymentDialog, VoidDialog } from '@/components/education/fee-dialogs';
import { Crumb, FeeBadge, Field, METHOD_LABEL, Money, Panel, studentName } from '@/components/education/ui';
import { ErrorState, ListSkeleton, PermissionDenied } from '@/components/common/states';
import { ActivityTimeline } from '@/components/tasks/activity';
import { Button } from '@/components/ui/button';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { eduApi, useFeeInvoice, useFeeInvoiceHistory } from '@/lib/api/education';
import { useOrgMutation } from '@/lib/api/queries';
import { day } from '@/lib/format';
import { usd } from '@/lib/money';
import { useOrg } from '@/lib/org-context';

/** A fee invoice: amount, paid and balance (USD with riel), payments, voiding, and its history. */
export default function FeeInvoicePage() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const { id } = useParams<{ id: string }>();
  const { data, error, isPending, refetch } = useFeeInvoice(slug, id);
  const history = useFeeInvoiceHistory(slug, id);
  const [open, setOpen] = useState<'pay' | 'void' | null>(null);
  const removePayment = useOrgMutation(slug, (paymentId: string) => eduApi.removePayment(slug, id, paymentId));
  if (!can('education.manage')) return <PermissionDenied />;
  if (error) return <ErrorState error={error} retry={() => refetch()} />;
  if (isPending) return <ListSkeleton />;
  const s = data.summary;

  return (
    <>
      <Crumb href={`/${slug}/education/fees`} label={t('education.feesTitle')} current={s.number} />
      <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="flex flex-wrap items-center gap-2 text-xl font-semibold tracking-tight">{s.number} <FeeBadge status={s.status} overdue={s.overdue} /></h1>
          <p className="mt-1 text-sm text-muted-foreground">{s.description} · <Link href={`/${slug}/education/students/${s.student.id}`} className="text-primary hover:underline">{studentName(s.student)} ({s.student.number})</Link></p>
        </div>
        <div className="flex flex-wrap gap-2">
          {data.canPay && <Button size="sm" onClick={() => setOpen('pay')}><Banknote className="size-4" />{t('education.recordPayment')}</Button>}
          {data.canVoid && <Button size="sm" variant="outline" onClick={() => setOpen('void')}><Ban className="size-4" />{t('education.void')}</Button>}
        </div>
      </div>
      <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
        <div className="min-w-0 space-y-8">
          <Panel id="pay-h" title={t('education.payments')}>
            {data.payments.length === 0 ? <p className="rounded-lg border border-dashed px-4 py-6 text-center text-sm text-muted-foreground">{t('education.noPayments')}</p> : (
              <ul className="divide-y rounded-lg border bg-card">
                {data.payments.map((p) => (
                  <li key={p.id} className="flex items-center justify-between gap-3 px-4 py-3">
                    <span className="min-w-0"><span className="block font-medium">{day(p.paidOn)} · {METHOD_LABEL[p.method]}</span>
                      <span className="block truncate text-xs text-muted-foreground">{[p.reference, p.recordedBy?.name].filter(Boolean).join(' · ')}</span></span>
                    <span className="flex items-center gap-2"><Money value={p.amount} className="items-end" />
                      {data.canRemovePayment && <Button variant="ghost" size="icon-sm" aria-label={t('education.removePayment', { amount: usd(p.amount) })}
                        onClick={async () => { try { await removePayment.mutateAsync(p.id); toast.success(t('education.paymentRemoved')); } catch (e) { toast.error(errorMessage(e)); } }}>
                        <Trash2 className="size-4" /></Button>}</span>
                  </li>
                ))}
              </ul>
            )}
          </Panel>
          <Panel id="ih-h" title={t('education.history')}>
            {history.error ? <ErrorState error={history.error} retry={() => history.refetch()} /> : history.isPending ? <ListSkeleton rows={2} /> : <ActivityTimeline items={history.data} />}
          </Panel>
        </div>
        <aside className="space-y-4">
          <dl className="grid gap-4 rounded-lg border bg-card p-4">
            <Field label={t('education.amount')}><Money value={s.amount} strong /></Field>
            <Field label={t('education.paid')}><Money value={s.paid} /></Field>
            <Field label={t('education.balance')}><Money value={s.balance} strong className={s.overdue ? 'text-destructive' : undefined} /></Field>
            <Field label={t('education.issuedOn')}>{day(s.issuedOn)}</Field>
            <Field label={t('education.dueOn')}>{day(s.dueOn)}</Field>
            {data.voidReason && <Field label={t('education.voidReason')}>{data.voidReason}</Field>}
          </dl>
        </aside>
      </div>
      {open === 'pay' && <PaymentDialog org={slug} invoiceId={id} balance={s.balance} onClose={() => setOpen(null)} />}
      {open === 'void' && <VoidDialog org={slug} invoiceId={id} number={s.number} onClose={() => setOpen(null)} />}
    </>
  );
}
