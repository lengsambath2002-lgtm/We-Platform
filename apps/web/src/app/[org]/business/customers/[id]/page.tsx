'use client';

import { Archive, ArchiveRestore, FilePlus2, Pencil } from 'lucide-react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useState } from 'react';
import { toast } from 'sonner';

import { CustomerDialog } from '@/components/business/dialogs';
import { InvoiceList, QuoteList } from '@/components/business/lists';
import { Crumb, Field, Money } from '@/components/business/ui';
import { ErrorState, ListSkeleton } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { useT } from '@/i18n/provider';
import { bizApi, useCustomer } from '@/lib/api/business';
import { errorMessage } from '@/lib/api/client';
import { useOrgMutation } from '@/lib/api/queries';
import { usd } from '@/lib/money';
import { useOrg } from '@/lib/org-context';

/** One customer: details, what they owe, and their quotes and invoices. */
export default function CustomerPage() {
  const { slug } = useOrg();
  const { t } = useT();
  const { id } = useParams<{ id: string }>();
  const { data, error, isPending, refetch } = useCustomer(slug, id);
  const [editing, setEditing] = useState(false);
  const archive = useOrgMutation(slug, (a: boolean) => bizApi.archiveCustomer(slug, id, a));
  if (error) return <ErrorState error={error} retry={() => refetch()} />;
  if (isPending) return <ListSkeleton />;
  const c = data.customer;
  const base = `/${slug}/business`;

  return (
    <div className="mx-auto max-w-5xl">
      <Crumb href={`${base}/customers`} label={t('business.navCustomers')} current={c.name} />
      <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h1 className="break-words text-xl font-semibold tracking-tight">{c.name}</h1>
          <p className="mt-1 text-sm text-muted-foreground">{t(`business.kind_${c.kind}`)}{c.archived ? ` · ${t('business.archived')}` : ''}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          {!c.archived && <Button size="sm" asChild><Link href={`${base}/invoices/new?customer=${c.id}`}><FilePlus2 className="size-4" />{t('business.newInvoice')}</Link></Button>}
          <Button size="sm" variant="outline" onClick={() => setEditing(true)}><Pencil className="size-4" />{t('common.edit')}</Button>
          {data.canArchive && (
            <Button size="sm" variant="outline" disabled={archive.isPending} onClick={async () => {
              try { await archive.mutateAsync(!c.archived); toast.success(t(c.archived ? 'business.restored' : 'business.archivedDone')); } catch (e) { toast.error(errorMessage(e)); }
            }}>{c.archived ? <><ArchiveRestore className="size-4" />{t('business.restore')}</> : <><Archive className="size-4" />{t('business.archive')}</>}</Button>
          )}
        </div>
      </div>
      <div className="mb-6 grid gap-3 sm:grid-cols-3">
        <div className="rounded-lg border bg-card p-4"><p className="text-sm text-muted-foreground">{t('business.owes')}</p><Money value={c.balance} className="mt-1 text-xl" strong /></div>
        <div className="rounded-lg border bg-card p-4"><p className="text-sm text-muted-foreground">{t('business.overdue')}</p><Money value={data.overdue} className={data.overdue > 0 ? 'mt-1 text-xl text-destructive' : 'mt-1 text-xl'} strong /></div>
        <div className="rounded-lg border bg-card p-4"><p className="text-sm text-muted-foreground">{t('business.invoicedReceived')}</p><Money value={data.invoiced} className="mt-1 text-xl" strong /><p className="text-xs text-muted-foreground">{t('business.receivedAmount', { amount: usd(data.received) })}</p></div>
      </div>
      <div className="grid gap-6 lg:grid-cols-[1fr_300px]">
        <div className="min-w-0 space-y-6">
          <section aria-labelledby="inv-h" className="space-y-3">
            <h2 id="inv-h" className="text-base font-semibold">{t('business.navInvoices')}</h2>
            {data.invoices.length === 0 ? <p className="text-sm text-muted-foreground">{t('business.noCustomerInvoices')}</p> : <InvoiceList org={slug} items={data.invoices} hideCustomer />}
          </section>
          <section aria-labelledby="q-h" className="space-y-3">
            <h2 id="q-h" className="text-base font-semibold">{t('business.navQuotes')}</h2>
            {data.quotes.length === 0 ? <p className="text-sm text-muted-foreground">{t('business.noCustomerQuotes')}</p> : <QuoteList org={slug} items={data.quotes} hideCustomer />}
          </section>
        </div>
        <aside className="rounded-lg border bg-card p-4">
          <dl className="grid gap-4">
            {c.contactName && <Field label={t('business.contactPerson')}>{c.contactName}</Field>}
            <Field label={t('business.email')}>{c.email ? <a href={`mailto:${c.email}`} className="text-primary hover:underline">{c.email}</a> : '—'}</Field>
            <Field label={t('business.phone')}>{c.phone ? <a href={`tel:${c.phone}`} className="text-primary hover:underline">{c.phone}</a> : '—'}</Field>
            <Field label={t('business.address')}><span className="whitespace-pre-wrap">{c.address ?? '—'}</span></Field>
            <Field label={t('business.taxId')}>{c.taxId ?? '—'}</Field>
            {c.notes && <Field label={t('business.notes')}><span className="whitespace-pre-wrap">{c.notes}</span></Field>}
          </dl>
        </aside>
      </div>
      {editing && <CustomerDialog org={slug} customer={c} open onOpenChange={setEditing} />}
    </div>
  );
}
