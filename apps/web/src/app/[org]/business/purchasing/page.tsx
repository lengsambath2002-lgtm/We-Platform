'use client';

import Link from 'next/link';

import { PageHeader } from '@/components/common/page-header';
import { ErrorState, ListSkeleton, PermissionDenied } from '@/components/common/states';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useT } from '@/i18n/provider';
import { usePurchasingOverview } from '@/lib/api/purchasing';
import { usd } from '@/lib/money';
import { useOrg } from '@/lib/org-context';
import { cn } from '@/lib/utils';

/** Purchasing at a glance: what is owed to suppliers, deliveries awaited or not yet in VirtualBiz stock, spend by supplier. */
export default function PurchasingHome() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const view = can('retail.view');
  const { data: d, error, isPending, refetch } = usePurchasingOverview(slug, view);
  if (!view) return <PermissionDenied />;
  const tile = (label: string, value: string, href: string, warn?: boolean) => (
    <Link href={href} className="block rounded-lg border bg-card p-4 hover:border-primary/40">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className={cn('mt-0.5 text-2xl font-semibold tabular', warn && 'text-destructive')}>{value}</p>
    </Link>
  );
  return (
    <>
      <PageHeader title={t('retail.purchasingTitle')} subtitle={t('retail.purchasingSubtitle')} />
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={5} /> : (
        <>
          <div className="mb-6 grid grid-cols-2 gap-3 lg:grid-cols-5">
            {tile(t('retail.owedToSuppliers'), usd(d.owed), `/${slug}/retail/purchase-orders`)}
            {tile(t('retail.overdue'), usd(d.overdue), `/${slug}/retail/purchase-orders`, d.overdue > 0)}
            {tile(t('retail.awaitingDelivery'), String(d.awaitingDelivery), `/${slug}/retail/purchase-orders?status=ORDERED`)}
            {tile(t('retail.notInStock'), String(d.notInStock), `/${slug}/retail/purchase-orders`, d.notInStock > 0)}
            {tile(t('retail.orderedThisMonth'), usd(d.orderedThisMonth), `/${slug}/retail/purchase-orders`)}
          </div>
          <section aria-labelledby="spend-h" className="rounded-lg border bg-card p-4">
            <h2 id="spend-h" className="mb-3 text-base font-semibold">{t('retail.spendThisYear')}</h2>
            {d.spendThisYear.length === 0 ? <p className="text-sm text-muted-foreground">{t('retail.noSpend')}</p> : (
              <div className="overflow-x-auto">
                <Table>
                  <TableHeader><TableRow><TableHead>{t('retail.supplier')}</TableHead><TableHead className="text-right">{t('retail.orders')}</TableHead><TableHead className="text-right">{t('retail.ordered')}</TableHead><TableHead className="text-right">{t('retail.paid')}</TableHead><TableHead className="text-right">{t('retail.owed')}</TableHead></TableRow></TableHeader>
                  <TableBody>{d.spendThisYear.map((s) => (
                    <TableRow key={s.supplierId}>
                      <TableCell><Link className="text-primary hover:underline" href={`/${slug}/retail/suppliers/${s.supplierId}`}>{s.supplierName}</Link></TableCell>
                      <TableCell className="text-right tabular">{s.orders}</TableCell>
                      <TableCell className="text-right tabular">{usd(s.ordered)}</TableCell>
                      <TableCell className="text-right tabular">{usd(s.paid)}</TableCell>
                      <TableCell className="text-right font-medium tabular">{usd(s.owed)}</TableCell>
                    </TableRow>
                  ))}</TableBody>
                </Table>
              </div>
            )}
          </section>
        </>
      )}
    </>
  );
}
