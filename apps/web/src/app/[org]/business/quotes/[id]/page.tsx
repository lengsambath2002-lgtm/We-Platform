'use client';

import { Check, FileOutput, Pencil, Send, Trash2, X } from 'lucide-react';
import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useState } from 'react';
import { toast } from 'sonner';

import { DocumentLines } from '@/components/business/document-view';
import { Crumb, Field, QuoteBadge } from '@/components/business/ui';
import { ErrorState, ListSkeleton } from '@/components/common/states';
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Button } from '@/components/ui/button';
import { useT } from '@/i18n/provider';
import { bizApi, useQuote } from '@/lib/api/business';
import { errorMessage } from '@/lib/api/client';
import { useOrgMutation } from '@/lib/api/queries';
import { day } from '@/lib/format';
import { useOrg } from '@/lib/org-context';

/** One quote: lines and totals, and its next step (mark sent, accepted or declined; convert to an invoice). */
export default function QuotePage() {
  const { slug } = useOrg();
  const { t } = useT();
  const router = useRouter();
  const { id } = useParams<{ id: string }>();
  const { data: q, error, isPending, refetch } = useQuote(slug, id);
  const [deleting, setDeleting] = useState(false);
  const mark = useOrgMutation(slug, (a: 'send' | 'accept' | 'decline') => bizApi.markQuote(slug, id, a));
  const convert = useOrgMutation(slug, () => bizApi.convertQuote(slug, id));
  const del = useOrgMutation(slug, () => bizApi.deleteQuote(slug, id));
  if (error) return <ErrorState error={error} retry={() => refetch()} />;
  if (isPending) return <ListSkeleton />;
  const base = `/${slug}/business`;
  const run = async (fn: () => Promise<unknown>, done: string) => {
    try { await fn(); toast.success(done); } catch (e) { toast.error(errorMessage(e)); }
  };

  return (
    <div className="mx-auto max-w-5xl">
      <Crumb href={`${base}/quotes`} label={t('business.navQuotes')} current={q.number} />
      <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h1 className="text-xl font-semibold tracking-tight">{q.number}</h1>
          <p className="mt-1 flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
            <QuoteBadge status={q.status} expired={q.expired} />
            <Link href={`${base}/customers/${q.customer.id}`} className="hover:text-foreground hover:underline">{q.customer.name}</Link>
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          {q.canConvert && (
            <Button size="sm" disabled={convert.isPending} onClick={() => run(async () => { const inv = await convert.mutateAsync(undefined); router.push(`${base}/invoices/${inv.id}`); }, t('business.converted'))}>
              <FileOutput className="size-4" />{t('business.convert')}
            </Button>
          )}
          {q.canSend && <Button size="sm" variant="outline" disabled={mark.isPending} onClick={() => run(() => mark.mutateAsync('send'), t('business.quoteSent'))}><Send className="size-4" />{t('business.markSent')}</Button>}
          {q.canDecide && !q.expired && <Button size="sm" variant="outline" disabled={mark.isPending} onClick={() => run(() => mark.mutateAsync('accept'), t('business.quoteAccepted'))}><Check className="size-4" />{t('business.markAccepted')}</Button>}
          {q.canDecide && <Button size="sm" variant="outline" disabled={mark.isPending} onClick={() => run(() => mark.mutateAsync('decline'), t('business.quoteDeclined'))}><X className="size-4" />{t('business.markDeclined')}</Button>}
          {q.canEdit && <Button size="sm" variant="outline" asChild><Link href={`${base}/quotes/${id}/edit`}><Pencil className="size-4" />{t('common.edit')}</Link></Button>}
          {q.canDelete && <Button size="sm" variant="ghost" className="text-destructive" onClick={() => setDeleting(true)}><Trash2 className="size-4" />{t('common.delete')}</Button>}
        </div>
      </div>
      <div className="grid gap-6 lg:grid-cols-[1fr_300px]">
        <div className="min-w-0 space-y-6">
          <DocumentLines lines={q.lines} vatEnabled={q.vatEnabled} vatRate={q.vatRate} subtotal={q.subtotal} discountTotal={q.discountTotal} vatTotal={q.vatTotal} total={q.total} />
          {q.notes && (
            <section aria-labelledby="notes-h" className="rounded-lg border bg-card p-4">
              <h2 id="notes-h" className="mb-1 text-sm font-semibold">{t('business.notesTerms')}</h2>
              <p className="whitespace-pre-wrap break-words text-sm">{q.notes}</p>
            </section>
          )}
        </div>
        <aside className="h-fit rounded-lg border bg-card p-4">
          <dl className="grid gap-4">
            <Field label={t('business.customer')}><Link href={`${base}/customers/${q.customer.id}`} className="text-primary hover:underline">{q.customer.name}</Link></Field>
            <Field label={t('business.quoteDate')}>{day(q.issueDate)}</Field>
            <Field label={t('business.validUntil')}>{q.validUntil ? <span className={q.expired ? 'text-warning' : undefined}>{day(q.validUntil)}</span> : '—'}</Field>
            {q.invoiceId && <Field label={t('business.invoice')}><Link href={`${base}/invoices/${q.invoiceId}`} className="text-primary hover:underline">{q.invoiceNumber ?? t('business.draftInvoice')}</Link></Field>}
            {q.createdBy && <Field label={t('business.createdBy')}>{q.createdBy.name}</Field>}
          </dl>
        </aside>
      </div>
      <AlertDialog open={deleting} onOpenChange={setDeleting}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('business.deleteQuoteTitle', { number: q.number })}</AlertDialogTitle>
            <AlertDialogDescription>{t('business.deleteQuoteBody')}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t('common.cancel')}</AlertDialogCancel>
            <AlertDialogAction className="bg-destructive text-white hover:bg-destructive/90"
              onClick={() => run(async () => { await del.mutateAsync(undefined); router.push(`${base}/quotes`); }, t('business.deleted'))}>{t('common.delete')}</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
