'use client';

import { ArrowLeft, ExternalLink, PackageCheck, Pencil, Send, Trash2, Wallet, X } from 'lucide-react';
import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useState } from 'react';
import { toast } from 'sonner';

import { ErrorState, ListSkeleton } from '@/components/common/states';
import { CancelOrderDialog, OrderDialog, PayDialog, PoBadge, ReceiveDialog } from '@/components/business/purchasing-ui';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Table, TableBody, TableCell, TableFooter, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { purchasingApi, useOrder } from '@/lib/api/purchasing';
import { useOrgMutation } from '@/lib/api/queries';
import { useRetailShops, virtualbiz } from '@/lib/api/sso';
import { day } from '@/lib/format';
import { usd } from '@/lib/money';
import { useOrg } from '@/lib/org-context';

const qty = (n: number) => String(Number(n));

/**
 * One purchase order: its lines and what has arrived, placing and cancelling it, deliveries (each put into stock
 * in VirtualBiz, then ticked off) and payments to the supplier.
 */
export default function OrderPage() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const router = useRouter();
  const { id } = useParams<{ id: string }>();
  const { data: o, error, isPending, refetch } = useOrder(slug, id);
  const shops = useRetailShops(slug);
  const [dialog, setDialog] = useState<'edit' | 'receive' | 'pay' | 'cancel' | null>(null);
  const run = useOrgMutation(slug, (fn: () => Promise<unknown>) => fn());
  const act = async (fn: () => Promise<unknown>, done?: string) => {
    try { await run.mutateAsync(fn); if (done) toast.success(done); return true; } catch (e) { toast.error(errorMessage(e)); return false; }
  };
  if (error) return <ErrorState error={error} retry={() => refetch()} />;
  if (isPending) return <ListSkeleton rows={6} />;
  const s = o.summary;
  const manage = (can('business.manage') || can('retail.manage'));
  const base = shops.data?.virtualbizUrl;
  const shopId = o.shopBusinessId ?? (shops.data?.shops.length === 1 ? shops.data.shops[0].businessId : null);

  return (
    <>
      <Link href={`/${slug}/business/purchase-orders`} className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="size-4" />{t('retail.ordersTitle')}</Link>
      <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="flex flex-wrap items-center gap-2"><h1 className="text-xl font-semibold tracking-tight">{s.number ?? t('retail.draftOrder')}</h1><PoBadge status={s.status} /></div>
          <p className="mt-1 text-sm text-muted-foreground">
            <Link className="text-primary hover:underline" href={`/${slug}/business/suppliers/${s.supplierId}`}>{s.supplierName}</Link>
            {s.shopName && <> · {s.shopName}</>}{s.orderDate && <> · {t('retail.orderedOn', { date: day(s.orderDate) })}</>}
            {s.expectedDate && <> · {t('retail.expectedOn', { date: day(s.expectedDate) })}</>}
          </p>
        </div>
        {manage && (
          <div className="flex flex-wrap gap-2">
            {o.canEdit && <Button variant="outline" size="sm" onClick={() => setDialog('edit')}><Pencil className="size-4" />{t('common.edit')}</Button>}
            {o.canEdit && <Button size="sm" onClick={() => act(() => purchasingApi.place(slug, s.id), t('retail.orderPlaced'))}><Send className="size-4" />{t('retail.placeOrder')}</Button>}
            {o.canEdit && <Button variant="ghost" size="sm" aria-label={t('common.delete')} onClick={async () => {
              if (window.confirm(t('retail.deleteDraftConfirm')) && await act(() => purchasingApi.deleteOrder(slug, s.id), t('retail.orderDeleted'))) router.replace(`/${slug}/business/purchase-orders`);
            }}><Trash2 className="size-4" /></Button>}
            {o.canReceive && <Button size="sm" onClick={() => setDialog('receive')}><PackageCheck className="size-4" />{t('retail.recordDelivery')}</Button>}
            {o.canPay && <Button variant="outline" size="sm" onClick={() => setDialog('pay')}><Wallet className="size-4" />{t('retail.recordPayment')}</Button>}
            {s.status === 'ORDERED' && s.paid === 0 && <Button variant="ghost" size="sm" onClick={() => setDialog('cancel')}><X className="size-4" />{t('retail.cancelOrder')}</Button>}
          </div>
        )}
      </div>
      {o.cancelReason && <p role="status" className="mb-4 rounded-md bg-muted px-3 py-2 text-sm">{t('retail.cancelledBecause', { reason: o.cancelReason })}</p>}

      <div className="grid gap-4 lg:grid-cols-3">
        <div className="space-y-4 lg:col-span-2">
          <section className="overflow-x-auto rounded-lg border bg-card">
            <Table>
              <TableHeader><TableRow><TableHead>{t('retail.item')}</TableHead><TableHead className="text-right">{t('retail.qty')}</TableHead><TableHead className="text-right">{t('retail.receivedCol')}</TableHead><TableHead className="text-right">{t('retail.unitCost')}</TableHead><TableHead className="text-right">{t('retail.amount')}</TableHead></TableRow></TableHeader>
              <TableBody>{o.lines.map((l) => (
                <TableRow key={l.id}>
                  <TableCell>{l.description}{l.sku && <span className="block text-xs text-muted-foreground">{l.sku}</span>}</TableCell>
                  <TableCell className="text-right tabular">{qty(l.quantity)}</TableCell>
                  <TableCell className="text-right tabular">{qty(l.received)}</TableCell>
                  <TableCell className="text-right tabular">{usd(l.unitCost)}</TableCell>
                  <TableCell className="text-right tabular">{usd(l.amount)}</TableCell>
                </TableRow>
              ))}</TableBody>
              <TableFooter><TableRow><TableCell colSpan={4} className="font-medium">{t('retail.total')}</TableCell><TableCell className="text-right font-semibold tabular">{usd(s.total)}</TableCell></TableRow></TableFooter>
            </Table>
          </section>

          <section aria-labelledby="dl-h" className="rounded-lg border bg-card p-4">
            <h2 id="dl-h" className="text-base font-semibold">{t('retail.deliveries')}</h2>
            <p className="mb-3 text-sm text-muted-foreground">{t('retail.deliveriesBody')}</p>
            {o.receipts.length === 0 ? <p className="text-sm text-muted-foreground">{t('retail.noDeliveries')}</p> : (
              <ul className="space-y-3">
                {o.receipts.map((r) => (
                  <li key={r.id} className="rounded-md border p-3">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <p className="text-sm font-medium">{day(r.receivedOn)} · {r.by.name}</p>
                      <div className="flex flex-wrap items-center gap-3">
                        {base && shopId && !r.stockRecorded && (
                          <Button asChild variant="outline" size="sm"><a href={virtualbiz.inventory(base, shopId)} target="_blank" rel="noopener noreferrer"><ExternalLink className="size-4" />{t('retail.putIntoStock')}</a></Button>
                        )}
                        <label className="flex items-center gap-2 text-sm">
                          <Checkbox checked={r.stockRecorded} disabled={!manage} onCheckedChange={(x) => act(() => purchasingApi.stockRecorded(slug, r.id, x === true))} />
                          {t('retail.inStock')}
                        </label>
                      </div>
                    </div>
                    <ul className="mt-2 text-sm text-muted-foreground">{r.lines.map((x) => <li key={x.lineId}>{qty(x.quantity)} × {x.description}{x.sku ? ` (${x.sku})` : ''}</li>)}</ul>
                    {r.note && <p className="mt-1 text-sm">{r.note}</p>}
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>

        <aside className="space-y-4">
          <section className="rounded-lg border bg-card p-4">
            <h2 className="mb-2 text-sm font-semibold">{t('retail.payments')}</h2>
            <dl className="mb-3 space-y-1 text-sm">
              <div className="flex justify-between"><dt>{t('retail.total')}</dt><dd className="tabular">{usd(s.total)}</dd></div>
              <div className="flex justify-between"><dt>{t('retail.paid')}</dt><dd className="tabular">{usd(s.paid)}</dd></div>
              <div className="flex justify-between font-semibold"><dt>{t('retail.owed')}</dt><dd className={s.overdue ? 'tabular text-destructive' : 'tabular'}>{usd(s.balance)}</dd></div>
              {s.dueDate && s.balance > 0 && <p className="text-xs text-muted-foreground">{t('retail.dueOn', { date: day(s.dueDate) })}</p>}
            </dl>
            {o.payments.length > 0 && (
              <ul className="divide-y text-sm">{o.payments.map((p) => (
                <li key={p.id} className="flex justify-between gap-2 py-1.5"><span>{day(p.paidOn)} · {t(`retail.method_${p.method as 'CASH'}`)}{p.reference ? ` · ${p.reference}` : ''}</span><span className="tabular">{usd(p.amount)}</span></li>
              ))}</ul>
            )}
          </section>
          {o.notes && <section className="rounded-lg border bg-card p-4"><h2 className="mb-1 text-sm font-semibold">{t('retail.notes')}</h2><p className="whitespace-pre-line text-sm">{o.notes}</p></section>}
        </aside>
      </div>
      {dialog === 'edit' && <OrderDialog org={slug} order={o} onClose={() => setDialog(null)} />}
      {dialog === 'receive' && <ReceiveDialog org={slug} order={o} onClose={() => setDialog(null)} />}
      {dialog === 'pay' && <PayDialog org={slug} order={o} onClose={() => setDialog(null)} />}
      {dialog === 'cancel' && <CancelOrderDialog org={slug} order={o} onClose={() => setDialog(null)} />}
    </>
  );
}
