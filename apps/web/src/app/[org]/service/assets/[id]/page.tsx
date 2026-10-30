'use client';

import { CalendarPlus, ClipboardPlus, Pencil } from 'lucide-react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useState } from 'react';

import { ErrorState, ListSkeleton, PermissionDenied } from '@/components/common/states';
import { AssetDialog, PlanDialog } from '@/components/service/dialogs';
import { TicketList, WorkOrderList } from '@/components/service/lists';
import { AssetBadge, Crumb, Field, Panel } from '@/components/service/ui';
import { Button } from '@/components/ui/button';
import { useT } from '@/i18n/provider';
import { useAsset } from '@/lib/api/service';
import { day } from '@/lib/format';
import { useOrg } from '@/lib/org-context';

/** One asset: details, warranty, maintenance plans and its history of work orders and tickets. */
export default function AssetPage() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const { id } = useParams<{ id: string }>();
  const { data, error, isPending, refetch } = useAsset(slug, id);
  const [dialog, setDialog] = useState<'edit' | 'plan' | null>(null);
  if (!can('service.manage')) return <PermissionDenied />;
  if (error) return <ErrorState error={error} retry={() => refetch()} />;
  if (isPending) return <ListSkeleton />;
  const a = data.asset;
  return (
    <div className="mx-auto max-w-6xl">
      <Crumb href={`/${slug}/service/assets`} label={t('service.navAssets')} current={a.name} />
      <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h1 className="break-words text-xl font-semibold tracking-tight">{a.name}</h1>
          <p className="mt-1 flex flex-wrap items-center gap-2 text-sm text-muted-foreground"><AssetBadge status={a.status} />{a.serial && <span className="tabular">{a.serial}</span>}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button size="sm" variant="outline" asChild><Link href={`/${slug}/service/work-orders/new`}><ClipboardPlus className="size-4" />{t('service.newWorkOrder')}</Link></Button>
          <Button size="sm" variant="outline" onClick={() => setDialog('plan')}><CalendarPlus className="size-4" />{t('service.newPlan')}</Button>
          <Button size="sm" variant="ghost" onClick={() => setDialog('edit')}><Pencil className="size-4" />{t('common.edit')}</Button>
        </div>
      </div>
      <div className="grid gap-6 md:grid-cols-[1fr_300px]">
        <div className="min-w-0 space-y-8">
          <Panel id="a-wo" title={t('service.workOrderHistory')}>
            {data.workOrders.length === 0 ? <p className="text-sm text-muted-foreground">{t('service.noAssetWorkOrders')}</p> : <WorkOrderList org={slug} items={data.workOrders} />}
          </Panel>
          <Panel id="a-plans" title={t('service.maintenancePlans')}>
            {data.plans.length === 0 ? <p className="text-sm text-muted-foreground">{t('service.noAssetPlans')}</p> : (
              <ul className="divide-y rounded-lg border bg-card">
                {data.plans.map((p) => (
                  <li key={p.id} className="flex flex-wrap items-center justify-between gap-2 px-4 py-3 text-sm">
                    <span className="font-medium">{p.title}</span>
                    <span className="text-muted-foreground">{t(p.intervalUnit === 'DAYS' ? 'service.everyDays' : 'service.everyMonths', { n: p.intervalCount })} · {t('service.dueOn', { date: day(p.nextDue) })}</span>
                  </li>
                ))}
              </ul>
            )}
          </Panel>
          <Panel id="a-tk" title={t('service.tickets')}>
            {data.tickets.length === 0 ? <p className="text-sm text-muted-foreground">{t('service.noAssetTickets')}</p> : <TicketList org={slug} items={data.tickets} hideCustomer />}
          </Panel>
        </div>
        <aside className="h-fit rounded-lg border bg-card p-4">
          <dl className="grid gap-4">
            <Field label={t('service.customer')}>{a.customer ? <Link className="text-primary hover:underline" href={`/${slug}/service/customers/${a.customer.id}`}>{a.customer.name}</Link> : '—'}</Field>
            <Field label={t('service.site')}>{a.site?.name ?? '—'}</Field>
            <Field label={t('service.model')}>{[a.manufacturer, a.model].filter(Boolean).join(' · ') || '—'}</Field>
            <Field label={t('service.installedOn')}>{a.installedOn ? day(a.installedOn) : '—'}</Field>
            <Field label={t('service.warranty')}>{a.warrantyEnd ? (a.underWarranty ? t('service.warrantyUntil', { date: day(a.warrantyEnd) }) : t('service.warrantyEnded', { date: day(a.warrantyEnd) })) : '—'}</Field>
            {a.notes && <Field label={t('service.notes')}><span className="whitespace-pre-wrap">{a.notes}</span></Field>}
          </dl>
        </aside>
      </div>
      {dialog === 'edit' && <AssetDialog org={slug} asset={a} open onOpenChange={() => setDialog(null)} />}
      {dialog === 'plan' && <PlanDialog org={slug} assetId={a.id} open onOpenChange={() => setDialog(null)} />}
    </div>
  );
}
