'use client';

import { Archive, ArchiveRestore, ArrowLeft, Pencil, Plus } from 'lucide-react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useState } from 'react';
import { toast } from 'sonner';

import { ErrorState, ListSkeleton } from '@/components/common/states';
import { OrderDialog, PoBadge, SupplierDialog } from '@/components/retail/purchasing-ui';
import { Button } from '@/components/ui/button';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { purchasingApi, useOrders, useSupplier } from '@/lib/api/purchasing';
import { useOrgMutation } from '@/lib/api/queries';
import { day } from '@/lib/format';
import { usd } from '@/lib/money';
import { useOrg } from '@/lib/org-context';

/** A supplier: their details, what is owed, and their orders. */
export default function SupplierPage() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const { id } = useParams<{ id: string }>();
  const { data: s, error, isPending, refetch } = useSupplier(slug, id);
  const orders = useOrders(slug, '', id);
  const [dialog, setDialog] = useState<'edit' | 'order' | null>(null);
  const archive = useOrgMutation(slug, (v: boolean) => purchasingApi.archiveSupplier(slug, id, v));
  if (error) return <ErrorState error={error} retry={() => refetch()} />;
  if (isPending) return <ListSkeleton rows={5} />;
  const manage = can('retail.manage');
  return (
    <>
      <Link href={`/${slug}/retail/suppliers`} className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="size-4" />{t('retail.suppliersTitle')}</Link>
      <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">{s.name}{s.archived && <span className="ml-2 rounded-full bg-muted px-2 py-0.5 text-xs font-normal">{t('retail.archived')}</span>}</h1>
          <p className="mt-1 text-sm text-muted-foreground">{[s.contactName, s.phone, s.email].filter(Boolean).join(' · ')}</p>
        </div>
        {manage && (
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" size="sm" onClick={() => setDialog('edit')}><Pencil className="size-4" />{t('common.edit')}</Button>
            {!s.archived && <Button size="sm" onClick={() => setDialog('order')}><Plus className="size-4" />{t('retail.newOrder')}</Button>}
            <Button variant="ghost" size="sm" onClick={async () => {
              try { await archive.mutateAsync(!s.archived); toast.success(t(s.archived ? 'retail.restored' : 'retail.archivedDone')); } catch (e) { toast.error(errorMessage(e)); }
            }}>{s.archived ? <ArchiveRestore className="size-4" /> : <Archive className="size-4" />}{t(s.archived ? 'retail.restore' : 'retail.archive')}</Button>
          </div>
        )}
      </div>
      <div className="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
        {[[t('retail.owed'), usd(s.owed)], [t('retail.spentThisYear'), usd(s.spentThisYear)], [t('retail.openOrders'), String(s.openOrders)], [t('retail.paymentTerms'), t('retail.termsDays', { count: s.paymentTermsDays })]].map(([l, v]) => (
          <div key={l} className="rounded-lg border bg-card p-3"><p className="text-xs text-muted-foreground">{l}</p><p className="text-lg font-semibold tabular">{v}</p></div>
        ))}
      </div>
      {(s.address || s.taxId || s.notes) && (
        <section className="mb-4 rounded-lg border bg-card p-4 text-sm">
          {s.address && <p>{s.address}</p>}{s.taxId && <p className="text-muted-foreground">{t('retail.taxId')}: {s.taxId}</p>}{s.notes && <p className="mt-2 whitespace-pre-line">{s.notes}</p>}
        </section>
      )}
      <section aria-labelledby="so-h" className="rounded-lg border bg-card p-4">
        <h2 id="so-h" className="mb-3 text-base font-semibold">{t('retail.ordersTitle')}</h2>
        {orders.isPending ? <ListSkeleton rows={2} /> : (orders.data ?? []).length === 0 ? <p className="text-sm text-muted-foreground">{t('retail.noOrders')}</p> : (
          <ul className="divide-y">{orders.data!.map((o) => (
            <li key={o.id}>
              <Link href={`/${slug}/retail/purchase-orders/${o.id}`} className="flex flex-wrap items-center justify-between gap-2 py-2 hover:bg-muted/50">
                <span className="text-sm font-medium">{o.number ?? t('retail.draftOrder')}{o.orderDate && <span className="ml-2 font-normal text-muted-foreground">{day(o.orderDate)}</span>}</span>
                <span className="flex items-center gap-3"><span className="text-sm tabular">{usd(o.total)}</span><PoBadge status={o.status} /></span>
              </Link>
            </li>
          ))}</ul>
        )}
      </section>
      {dialog === 'edit' && <SupplierDialog org={slug} supplier={s} onClose={() => setDialog(null)} />}
      {dialog === 'order' && <OrderDialog org={slug} supplierId={s.id} onClose={() => setDialog(null)} />}
    </>
  );
}
