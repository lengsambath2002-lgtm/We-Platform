'use client';

import { CircleDollarSign, Paperclip, Pencil, Send, Trash2 } from 'lucide-react';
import { useParams, useRouter } from 'next/navigation';
import { useState } from 'react';
import { toast } from 'sonner';

import { RecordApprovals } from '@/components/approvals/record-approvals';
import { ExpenseDialog } from '@/components/business/expense-dialog';
import { categoryName, Crumb, ExpenseBadge, Field, Money } from '@/components/business/ui';
import { PersonAvatar } from '@/components/common/person';
import { ErrorState, ListSkeleton } from '@/components/common/states';
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Button } from '@/components/ui/button';
import { useT } from '@/i18n/provider';
import { bizApi, downloadReceipt, useExpense } from '@/lib/api/business';
import { errorMessage } from '@/lib/api/client';
import { useOrgMutation } from '@/lib/api/queries';
import { bytes, day } from '@/lib/format';
import { usd } from '@/lib/money';
import { useOrg } from '@/lib/org-context';

/** One expense: what, when, how much, its receipt and its approval (decided, withdrawn or resubmitted from its panel). */
export default function ExpensePage() {
  const { slug } = useOrg();
  const { t } = useT();
  const router = useRouter();
  const { id } = useParams<{ id: string }>();
  const { data: e, error, isPending, refetch } = useExpense(slug, id);
  const [editing, setEditing] = useState(false);
  const [confirm, setConfirm] = useState<'paid' | 'delete' | null>(null);
  const submit = useOrgMutation(slug, () => bizApi.submitExpense(slug, id));
  const paid = useOrgMutation(slug, () => bizApi.expensePaid(slug, id));
  const del = useOrgMutation(slug, () => bizApi.deleteExpense(slug, id));
  if (error) return <ErrorState error={error} retry={() => refetch()} />;
  if (isPending) return <ListSkeleton />;
  const run = async (fn: () => Promise<unknown>, done: string) => {
    try { await fn(); toast.success(done); } catch (x) { toast.error(errorMessage(x)); }
  };
  const title = `${categoryName(e.category)} · ${usd(e.amount)}`;

  return (
    <div className="mx-auto max-w-4xl">
      <Crumb href={`/${slug}/business/expenses`} label={t('business.navExpenses')} current={title} />
      <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h1 className="break-words text-xl font-semibold tracking-tight">{title}</h1>
          <p className="mt-1 flex flex-wrap items-center gap-2 text-sm text-muted-foreground"><ExpenseBadge expense={e} /> {day(e.spentOn)}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          {e.canSubmit && <Button size="sm" disabled={submit.isPending} onClick={() => run(() => submit.mutateAsync(undefined), t('business.expenseSubmitted'))}><Send className="size-4" />{t('business.submitForApproval')}</Button>}
          {e.canMarkPaid && <Button size="sm" onClick={() => setConfirm('paid')}><CircleDollarSign className="size-4" />{t('business.markPaid')}</Button>}
          {e.canEdit && <Button size="sm" variant="outline" onClick={() => setEditing(true)}><Pencil className="size-4" />{t('common.edit')}</Button>}
          {e.canDelete && <Button size="sm" variant="ghost" className="text-destructive" onClick={() => setConfirm('delete')}><Trash2 className="size-4" />{t('common.delete')}</Button>}
        </div>
      </div>
      <div className="grid gap-6 md:grid-cols-[1fr_300px]">
        <section className="h-fit rounded-lg border bg-card p-4">
          <dl className="grid gap-4 sm:grid-cols-2">
            {e.person && <Field label={t('business.spentBy')}><span className="inline-flex items-center gap-2"><PersonAvatar person={e.person} />{e.person.name}</span></Field>}
            <Field label={t('business.amount')}><Money value={e.amount} strong /></Field>
            <Field label={t('business.category')}>{categoryName(e.category)}</Field>
            <Field label={t('business.vatIncluded')}>{e.vatAmount != null ? usd(e.vatAmount) : '—'}</Field>
            <Field label={t('business.supplier')}>{e.supplier ?? '—'}</Field>
            <Field label={t('business.spentOn')}>{day(e.spentOn)}</Field>
            {e.paidAt && <Field label={t('business.paidAt')}>{day(e.paidAt)}</Field>}
            {e.notes && <Field label={t('business.notes')} wide><span className="whitespace-pre-wrap">{e.notes}</span></Field>}
            <Field label={t('business.receipt')} wide>
              {e.receipt ? (
                <button type="button" className="inline-flex max-w-full items-center gap-2 text-primary hover:underline"
                  onClick={() => downloadReceipt(slug, e.id, e.receipt!.name).catch((x: Error) => toast.error(x.message))}>
                  <Paperclip className="size-4 shrink-0" /><span className="truncate">{e.receipt.name}</span><span className="shrink-0 text-xs text-muted-foreground">{bytes(e.receipt.size)}</span>
                </button>
              ) : <span className="text-muted-foreground">{t('business.noReceipt')}</span>}
            </Field>
          </dl>
        </section>
        <aside className="space-y-3">
          {e.status === 'DRAFT' && !e.approvalId ? (
            <p className="rounded-lg border bg-card p-4 text-sm text-muted-foreground">{t('business.draftExpenseNote')}</p>
          ) : <RecordApprovals org={slug} recordId={e.id} canRequest={false} moduleKey="business" recordType="expense" />}
        </aside>
      </div>
      {editing && <ExpenseDialog org={slug} expense={e} open onOpenChange={setEditing} />}
      <AlertDialog open={confirm !== null} onOpenChange={(v) => !v && setConfirm(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{confirm === 'paid' ? t('business.markPaidTitle') : t('business.deleteExpenseTitle')}</AlertDialogTitle>
            <AlertDialogDescription>{confirm === 'paid' ? t('business.markPaidBody', { amount: usd(e.amount), name: e.person?.name ?? '' }) : t('business.deleteExpenseBody')}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t('common.cancel')}</AlertDialogCancel>
            {confirm === 'paid'
              ? <AlertDialogAction onClick={() => run(() => paid.mutateAsync(undefined), t('business.markedPaid'))}>{t('business.markPaid')}</AlertDialogAction>
              : <AlertDialogAction className="bg-destructive text-white hover:bg-destructive/90"
                onClick={() => run(async () => { await del.mutateAsync(undefined); router.push(`/${slug}/business/expenses`); }, t('business.deleted'))}>{t('common.delete')}</AlertDialogAction>}
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
