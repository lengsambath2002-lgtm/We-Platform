'use client';

import { ClipboardList, Plus } from 'lucide-react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { Suspense, useState } from 'react';

import { PurchasingTabs } from '@/components/common/merged-tabs';
import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton, PermissionDenied } from '@/components/common/states';
import { OrderDialog, PoBadge } from '@/components/business/purchasing-ui';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useT } from '@/i18n/provider';
import { PO_STATUSES, useOrders } from '@/lib/api/purchasing';
import { day } from '@/lib/format';
import { usd } from '@/lib/money';
import { useOrg } from '@/lib/org-context';
import { cn } from '@/lib/utils';

const ALL = 'all';

/** Purchase orders, newest first, filtered by status (`?status=`). */
function Orders() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const params = useSearchParams();
  const router = useRouter();
  const status = (PO_STATUSES as string[]).includes(params.get('status') ?? '') ? params.get('status')! : ALL;
  const { data, error, isPending, refetch } = useOrders(slug, status === ALL ? '' : status);
  const [creating, setCreating] = useState(false);
  if (!(can('business.view') || can('retail.view'))) return <PermissionDenied />;
  return (
    <>
      <PurchasingTabs />
      <PageHeader title={t('retail.ordersTitle')} subtitle={t('retail.ordersSubtitle')}
        actions={(can('business.manage') || can('retail.manage')) && <Button size="sm" onClick={() => setCreating(true)}><Plus className="size-4" />{t('retail.newOrder')}</Button>} />
      <div className="mb-4">
        <Select value={status} onValueChange={(v) => router.replace(`/${slug}/business/purchase-orders${v === ALL ? '' : `?status=${v}`}`, { scroll: false })}>
          <SelectTrigger className="w-44" aria-label={t('retail.status')}><SelectValue /></SelectTrigger>
          <SelectContent><SelectItem value={ALL}>{t('retail.anyStatus')}</SelectItem>{PO_STATUSES.map((s) => <SelectItem key={s} value={s}>{t(`retail.po_${s}`)}</SelectItem>)}</SelectContent>
        </Select>
      </div>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={5} />
        : data.length === 0 ? <EmptyState icon={ClipboardList} title={t('retail.noOrders')} body={t('retail.noOrdersBody')} /> : (
          <div className="overflow-x-auto rounded-lg border bg-card">
            <Table>
              <TableHeader><TableRow><TableHead>{t('retail.order')}</TableHead><TableHead>{t('retail.supplier')}</TableHead><TableHead>{t('retail.status')}</TableHead><TableHead>{t('retail.expected')}</TableHead><TableHead className="text-right">{t('retail.total')}</TableHead><TableHead className="text-right">{t('retail.owed')}</TableHead></TableRow></TableHeader>
              <TableBody>{data.map((o) => (
                <TableRow key={o.id}>
                  <TableCell><Link className="font-medium text-primary hover:underline" href={`/${slug}/business/purchase-orders/${o.id}`}>{o.number ?? t('retail.draftOrder')}</Link>{o.orderDate && <span className="block text-xs text-muted-foreground">{day(o.orderDate)}</span>}</TableCell>
                  <TableCell>{o.supplierName}{o.shopName && <span className="block text-xs text-muted-foreground">{o.shopName}</span>}</TableCell>
                  <TableCell><PoBadge status={o.status} /></TableCell>
                  <TableCell className="text-sm">{o.expectedDate ? day(o.expectedDate) : ''}</TableCell>
                  <TableCell className="text-right tabular">{usd(o.total)}</TableCell>
                  <TableCell className={cn('text-right tabular', o.overdue && 'font-medium text-destructive')}>{o.balance > 0 ? usd(o.balance) : ''}{o.overdue && <span className="block text-xs">{t('retail.overdue')}</span>}</TableCell>
                </TableRow>
              ))}</TableBody>
            </Table>
          </div>
        )}
      {creating && <OrderDialog org={slug} onClose={() => setCreating(false)} />}
    </>
  );
}

export default function Page() {
  return <Suspense><Orders /></Suspense>;
}
