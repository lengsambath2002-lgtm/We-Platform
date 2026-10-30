'use client';

import { Plus, SearchX, UserRound } from 'lucide-react';
import Link from 'next/link';
import { useState } from 'react';

import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton, PermissionDenied } from '@/components/common/states';
import { CustomerDialog } from '@/components/service/dialogs';
import { Pager } from '@/components/service/ui';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useT } from '@/i18n/provider';
import { useSvcCustomers } from '@/lib/api/service';
import { useOrg } from '@/lib/org-context';

import { AssetTabs } from '../assets/tabs';

const SIZE = 25;

/** Service customers with their sites, assets and open tickets (kept in this module). */
export default function CustomersPage() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const [q, setQ] = useState('');
  const [archived, setArchived] = useState(false);
  const [page, setPage] = useState(0);
  const [adding, setAdding] = useState(false);
  const manage = can('service.manage');
  const { data, error, isPending, refetch } = useSvcCustomers(slug, { q: q.trim() || undefined, archived, page, size: SIZE }, manage);
  if (!manage) return <PermissionDenied />;
  const add = <Button size="sm" onClick={() => setAdding(true)}><Plus className="size-4" />{t('service.newCustomer')}</Button>;
  return (
    <>
      <PageHeader title={t('service.customersTitle')} subtitle={data ? t('service.count', { count: data.total }) : undefined} actions={add} />
      <AssetTabs />
      <div className="mb-4 flex flex-wrap items-end gap-3">
        <div className="w-full space-y-1 sm:w-72">
          <Label htmlFor="c-q" className="text-xs text-muted-foreground">{t('common.search')}</Label>
          <Input id="c-q" type="search" value={q} placeholder={t('service.searchCustomers')} onChange={(e) => { setQ(e.target.value); setPage(0); }} />
        </div>
        <Button size="sm" variant={archived ? 'secondary' : 'ghost'} onClick={() => { setArchived(!archived); setPage(0); }}>{archived ? t('service.showCurrent') : t('service.showArchived')}</Button>
      </div>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={5} />
        : data.items.length === 0 ? (q || archived
          ? <EmptyState icon={SearchX} title={t('service.noMatchTitle')} body={t('service.noMatchBody')} />
          : <EmptyState icon={UserRound} title={t('service.noCustomersTitle')} body={t('service.noCustomersBody')} action={add} />)
          : (
            <>
              <ul className="divide-y rounded-lg border bg-card">
                {data.items.map((c) => (
                  <li key={c.id}><Link href={`/${slug}/service/customers/${c.id}`} className="flex flex-wrap items-center justify-between gap-3 px-4 py-3 hover:bg-muted/50">
                    <span className="min-w-0"><span className="block truncate font-medium">{c.name}</span>
                      <span className="block truncate text-xs text-muted-foreground">{[c.contactName, c.phone, c.address].filter(Boolean).join(' · ')}</span></span>
                    <span className="text-xs text-muted-foreground">{t('service.customerCounts', { sites: c.sites, assets: c.assets, tickets: c.openTickets })}</span>
                  </Link></li>
                ))}
              </ul>
              <Pager page={page} size={SIZE} total={data.total} onPage={setPage} />
            </>
          )}
      {adding && <CustomerDialog org={slug} open onOpenChange={setAdding} />}
    </>
  );
}
