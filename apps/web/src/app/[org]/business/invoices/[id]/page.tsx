'use client';

import { Ban, Banknote, Pencil, Printer, Send, Trash2 } from 'lucide-react';
import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useState } from 'react';
import { toast } from 'sonner';

import { PaymentDialog } from '@/components/business/dialogs';
import { DocumentLines } from '@/components/business/document-view';
import { Crumb, Field, InvoiceBadge, METHOD_LABEL } from '@/components/business/ui';
import { ErrorState, ListSkeleton } from '@/components/common/states';
import { ActivityTimeline } from '@/components/tasks/activity';
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { useT } from '@/i18n/provider';
import { bizApi, useInvoice, useInvoiceHistory } from '@/lib/api/business';
import { errorMessage } from '@/lib/api/client';
import { useOrgMutation } from '@/lib/api/queries';
import { day } from '@/lib/format';
import { usd } from '@/lib/money';
import { useOrg } from '@/lib/org-context';

/** One invoice: its lines and totals, payments, history, and what the caller may do (issue, pay, print, void). */
export default function InvoicePage() {
  const { slug } = useOrg();
  const { t } = useT();
  const router = useRouter();
  const { id } = useParams<{ id: string }>();
  const { data: inv, error, isPending, refetch } = useInvoice(slug, id);
  const history = useInvoiceHistory(slug, id);
  const [confirm, setConfirm] = useState<'issue' | 'delete' | null>(null);
  const [paying, setPaying] = useState(false);
  const [voiding, setVoiding] = useState(false);
  const [reason, setReason] = useState('');
  const issue = useOrgMutation(slug, () => bizApi.issueInvoice(slug, id));
  const del = useOrgMutation(slug, () => bizApi.deleteInvoice(slug, id));
  const voidIt = useOrgMutation(slug, () => bizApi.voidInvoice(slug, id, reason.trim()));
  const removePayment = useOrgMutation(slug, (paymentId: string) => bizApi.removePayment(slug, id, paymentId));
  if (error) return <ErrorState error={error} retry={() => refetch()} />;
  if (isPending) return <ListSkeleton />;
  const base = `/${slug}/business`;
  const title = inv.number ?? t('business.draftInvoice');

  const run = async (fn: () => Promise<unknown>, done: string) => {
    try { await fn(); toast.success(done); } catch (e) { toast.error(errorMessage(e)); }
  };

  return (
    <div className="mx-auto max-w-5xl">
      <Crumb href={`${base}/invoices`} label={t('business.navInvoices')} current={title} />
      <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h1 className="text-xl font-semibold tracking-tight">{title}</h1>
          <p className="mt-1 flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
            <InvoiceBadge status={inv.status} overdue={inv.overdue} />
            <Link href={`${base}/customers/${inv.customer.id}`} className="hover:text-foreground hover:underline">{inv.customer.name}</Link>
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          {inv.canIssue && <Button size="sm" onClick={() => setConfirm('issue')}><Send className="size-4" />{t('business.issue')}</Button>}
          {inv.canPay && <Button size="sm" onClick={() => setPaying(true)}><Banknote className="size-4" />{t('business.recordPayment')}</Button>}
          {inv.canEdit && <Button size="sm" variant="outline" asChild><Link href={`${base}/invoices/${id}/edit`}><Pencil className="size-4" />{t('common.edit')}</Link></Button>}
          <Button size="sm" variant="outline" asChild><Link href={`${base}/invoices/${id}/print`}><Printer className="size-4" />{t('business.print')}</Link></Button>
          {inv.canVoid && <Button size="sm" variant="outline" className="text-destructive" onClick={() => setVoiding(true)}><Ban className="size-4" />{t('business.void')}</Button>}
          {inv.canDelete && <Button size="sm" variant="ghost" className="text-destructive" onClick={() => setConfirm('delete')}><Trash2 className="size-4" />{t('common.delete')}</Button>}
        </div>
      </div>
      {inv.status === 'VOID' && (
        <p role="status" className="mb-4 rounded-md bg-muted px-3 py-2 text-sm">{t('business.voidedBecause', { reason: inv.voidReason ?? '' })}</p>
      )}
      <div className="grid gap-6 lg:grid-cols-[1fr_300px]">
        <div className="min-w-0 space-y-6">
          <DocumentLines lines={inv.lines} vatEnabled={inv.vatEnabled} vatRate={inv.vatRate} subtotal={inv.subtotal} discountTotal={inv.discountTotal}
            vatTotal={inv.vatTotal} total={inv.total} paid={inv.status === 'DRAFT' ? undefined : inv.paid} balance={inv.status === 'DRAFT' || inv.status === 'VOID' ? undefined : inv.balance} />
          {inv.notes && (
            <section aria-labelledby="notes-h" className="rounded-lg border bg-card p-4">
              <h2 id="notes-h" className="mb-1 text-sm font-semibold">{t('business.notesTerms')}</h2>
              <p className="whitespace-pre-wrap break-words text-sm">{inv.notes}</p>
            </section>
          )}
          {inv.payments.length > 0 && (
            <section aria-labelledby="pay-h" className="space-y-2">
              <h2 id="pay-h" className="text-base font-semibold">{t('business.payments')}</h2>
              <ul className="divide-y rounded-lg border bg-card">
                {inv.payments.map((p) => (
                  <li key={p.id} className="flex items-center justify-between gap-3 px-4 py-3 text-sm">
                    <span className="min-w-0">
                      <span className="block font-medium tabular">{usd(p.amount)} · {METHOD_LABEL[p.method]}</span>
                      <span className="block truncate text-xs text-muted-foreground">{day(p.paidOn)}{p.reference ? ` · ${p.reference}` : ''}{p.recordedBy ? ` · ${p.recordedBy.name}` : ''}</span>
                    </span>
                    {inv.canRemovePayments && (
                      <Button size="sm" variant="ghost" className="text-destructive" disabled={removePayment.isPending}
                        onClick={() => run(() => removePayment.mutateAsync(p.id), t('business.paymentRemoved'))}>{t('common.remove')}</Button>
                    )}
                  </li>
                ))}
              </ul>
            </section>
          )}
          <section aria-labelledby="hist-h" className="space-y-3">
            <h2 id="hist-h" className="text-base font-semibold">{t('business.history')}</h2>
            {history.error ? <ErrorState error={history.error} retry={() => history.refetch()} /> : history.isPending ? <ListSkeleton rows={2} />
              : <ActivityTimeline items={history.data} />}
          </section>
        </div>
        <aside className="h-fit rounded-lg border bg-card p-4">
          <dl className="grid gap-4">
            <Field label={t('business.customer')}><Link href={`${base}/customers/${inv.customer.id}`} className="text-primary hover:underline">{inv.customer.name}</Link></Field>
            <Field label={t('business.issueDate')}>{inv.issueDate ? day(inv.issueDate) : t('business.whenIssued')}</Field>
            <Field label={t('business.dueDate')}>{inv.dueDate ? <span className={inv.overdue ? 'text-destructive' : undefined}>{day(inv.dueDate)}</span> : t('business.fromTerms')}</Field>
            {inv.quoteId && <Field label={t('business.fromQuote')}><Link href={`${base}/quotes/${inv.quoteId}`} className="text-primary hover:underline">{inv.quoteNumber}</Link></Field>}
            {inv.createdBy && <Field label={t('business.createdBy')}>{inv.createdBy.name}</Field>}
          </dl>
        </aside>
      </div>

      <AlertDialog open={confirm !== null} onOpenChange={(v) => !v && setConfirm(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{confirm === 'issue' ? t('business.issueTitle') : t('business.deleteDraftTitle')}</AlertDialogTitle>
            <AlertDialogDescription>{confirm === 'issue' ? t('business.issueBody', { total: usd(inv.total) }) : t('business.deleteDraftBody')}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t('common.cancel')}</AlertDialogCancel>
            {confirm === 'issue'
              ? <AlertDialogAction onClick={() => run(() => issue.mutateAsync(undefined), t('business.issued'))}>{t('business.issue')}</AlertDialogAction>
              : <AlertDialogAction className="bg-destructive text-white hover:bg-destructive/90" onClick={() => run(async () => { await del.mutateAsync(undefined); router.push(`${base}/invoices`); }, t('business.deleted'))}>{t('common.delete')}</AlertDialogAction>}
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
      {paying && <PaymentDialog org={slug} invoiceId={id} balance={inv.balance} open onOpenChange={setPaying} />}
      <Dialog open={voiding} onOpenChange={(v) => { if (!v) { setVoiding(false); setReason(''); } }}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{t('business.voidTitle', { number: title })}</DialogTitle>
            <DialogDescription>{t('business.voidBody')}</DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="void-reason">{t('business.voidReason')}</Label>
            <Textarea id="void-reason" rows={2} value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setVoiding(false)}>{t('common.back')}</Button>
            <Button variant="destructive" disabled={!reason.trim() || voidIt.isPending}
              onClick={() => run(async () => { await voidIt.mutateAsync(undefined); setVoiding(false); }, t('business.voided'))}>{t('business.void')}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
