'use client';

import { Archive, ArchiveRestore, MapPin, Pencil, Plus, Receipt, Trash2 } from 'lucide-react';
import { useParams } from 'next/navigation';
import { useState } from 'react';
import { toast } from 'sonner';

import { ErrorState, ListSkeleton, PermissionDenied } from '@/components/common/states';
import { AssetDialog, CustomerDialog, SiteDialog } from '@/components/service/dialogs';
import { TicketList, WorkOrderList } from '@/components/service/lists';
import { AssetBadge, Crumb, Field, Panel } from '@/components/service/ui';
import { Button } from '@/components/ui/button';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { useOrgMutation } from '@/lib/api/queries';
import { type Site, svcApi, useSvcCustomer } from '@/lib/api/service';
import { useOrg } from '@/lib/org-context';
import Link from 'next/link';

/** One service customer: contact, sites, assets, tickets and work orders. */
export default function CustomerPage() {
  const { slug, can, modules } = useOrg();
  const { t } = useT();
  const { id } = useParams<{ id: string }>();
  const { data, error, isPending, refetch } = useSvcCustomer(slug, id);
  const [dialog, setDialog] = useState<'edit' | 'site' | 'asset' | Site | null>(null);
  const archive = useOrgMutation(slug, (v: boolean) => svcApi.archiveCustomer(slug, id, v));
  const removeSite = useOrgMutation(slug, (siteId: string) => svcApi.removeSite(slug, siteId));
  if (!can('service.manage')) return <PermissionDenied />;
  if (error) return <ErrorState error={error} retry={() => refetch()} />;
  if (isPending) return <ListSkeleton />;
  const c = data.customer;
  const run = async (fn: () => Promise<unknown>, done: string) => { try { await fn(); toast.success(done); } catch (e) { toast.error(errorMessage(e)); } };
  return (
    <div className="mx-auto max-w-6xl">
      <Crumb href={`/${slug}/service/customers`} label={t('service.customersAndSites')} current={c.name} />
      <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h1 className="break-words text-xl font-semibold tracking-tight">{c.name}</h1>
          {c.archived && <p className="mt-1 text-sm text-muted-foreground">{t('service.archived')}</p>}
        </div>
        <div className="flex flex-wrap gap-2">
          <Button size="sm" variant="outline" onClick={() => setDialog('edit')}><Pencil className="size-4" />{t('common.edit')}</Button>
          {/* The same customer in General Business: deals, quotes and invoices. */}
          {modules.some((m) => m.key === 'business' && m.enabled) && can('business.view') && (
            <Button size="sm" variant="outline" asChild><Link href={`/${slug}/business/customers/${c.id}`}><Receipt className="size-4" />{t('service.salesRecords')}</Link></Button>
          )}
          <Button size="sm" variant="ghost" onClick={() => run(() => archive.mutateAsync(!c.archived), c.archived ? t('service.restored') : t('service.archivedDone'))}>
            {c.archived ? <><ArchiveRestore className="size-4" />{t('service.restore')}</> : <><Archive className="size-4" />{t('service.archive')}</>}</Button>
        </div>
      </div>
      <div className="grid gap-6 md:grid-cols-[1fr_300px]">
        <div className="min-w-0 space-y-8">
          <Panel id="c-sites" title={t('service.sites')} action={<Button size="sm" variant="outline" onClick={() => setDialog('site')}><Plus className="size-4" />{t('service.newSite')}</Button>}>
            {data.sites.length === 0 ? <p className="text-sm text-muted-foreground">{t('service.noSites')}</p> : (
              <ul className="divide-y rounded-lg border bg-card">
                {data.sites.map((s) => (
                  <li key={s.id} className="flex items-center gap-3 px-4 py-3 text-sm">
                    <MapPin className="size-4 shrink-0 text-muted-foreground" />
                    <span className="min-w-0 flex-1"><span className="block font-medium">{s.name}</span>{s.address && <span className="block truncate text-xs text-muted-foreground">{s.address}</span>}</span>
                    <Button size="icon-sm" variant="ghost" aria-label={t('service.editSiteOf', { name: s.name })} onClick={() => setDialog(s)}><Pencil className="size-4" /></Button>
                    <Button size="icon-sm" variant="ghost" aria-label={t('service.removeSiteOf', { name: s.name })} onClick={() => run(() => removeSite.mutateAsync(s.id), t('service.siteRemoved'))}><Trash2 className="size-4" /></Button>
                  </li>
                ))}
              </ul>
            )}
          </Panel>
          <Panel id="c-assets" title={t('service.navAssets')} action={<Button size="sm" variant="outline" onClick={() => setDialog('asset')}><Plus className="size-4" />{t('service.newAsset')}</Button>}>
            {data.assets.length === 0 ? <p className="text-sm text-muted-foreground">{t('service.noCustomerAssets')}</p> : (
              <ul className="divide-y rounded-lg border bg-card">
                {data.assets.map((a) => (
                  <li key={a.id}><Link href={`/${slug}/service/assets/${a.id}`} className="flex items-center justify-between gap-3 px-4 py-3 text-sm hover:bg-muted/50">
                    <span className="min-w-0"><span className="block truncate font-medium">{a.name}</span><span className="block truncate text-xs text-muted-foreground">{[a.serial, a.site?.name].filter(Boolean).join(' · ')}</span></span>
                    <AssetBadge status={a.status} /></Link></li>
                ))}
              </ul>
            )}
          </Panel>
          <Panel id="c-tickets" title={t('service.tickets')}>
            {data.tickets.length === 0 ? <p className="text-sm text-muted-foreground">{t('service.noCustomerTickets')}</p> : <TicketList org={slug} items={data.tickets} hideCustomer />}
          </Panel>
          <Panel id="c-wo" title={t('service.workOrders')}>
            {data.workOrders.length === 0 ? <p className="text-sm text-muted-foreground">{t('service.noCustomerWorkOrders')}</p> : <WorkOrderList org={slug} items={data.workOrders} />}
          </Panel>
        </div>
        <aside className="h-fit rounded-lg border bg-card p-4">
          <dl className="grid gap-4">
            <Field label={t('service.contactName')}>{c.contactName ?? '—'}</Field>
            <Field label={t('service.phone')}>{c.phone ? <a className="text-primary hover:underline" href={`tel:${c.phone}`}>{c.phone}</a> : '—'}</Field>
            <Field label={t('service.email')}>{c.email ?? '—'}</Field>
            <Field label={t('service.address')}>{c.address ?? '—'}</Field>
            {c.notes && <Field label={t('service.notes')}><span className="whitespace-pre-wrap">{c.notes}</span></Field>}
          </dl>
        </aside>
      </div>
      {dialog === 'edit' && <CustomerDialog org={slug} customer={c} open onOpenChange={() => setDialog(null)} />}
      {dialog === 'site' && <SiteDialog org={slug} customerId={c.id} open onOpenChange={() => setDialog(null)} />}
      {dialog === 'asset' && <AssetDialog org={slug} customerId={c.id} open onOpenChange={() => setDialog(null)} />}
      {dialog && typeof dialog === 'object' && <SiteDialog org={slug} customerId={c.id} site={dialog} open onOpenChange={() => setDialog(null)} />}
    </div>
  );
}
