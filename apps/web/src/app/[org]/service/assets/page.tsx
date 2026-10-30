'use client';

import { Cpu, Plus, SearchX } from 'lucide-react';
import Link from 'next/link';
import { useState } from 'react';

import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton, PermissionDenied } from '@/components/common/states';
import { AssetDialog } from '@/components/service/dialogs';
import { ASSET_LABEL, AssetBadge, Pager } from '@/components/service/ui';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useT } from '@/i18n/provider';
import { ASSET_STATUSES, useAssets } from '@/lib/api/service';
import { day } from '@/lib/format';
import { useOrg } from '@/lib/org-context';

import { AssetTabs } from './tabs';

const SIZE = 25;
const ALL = 'all';

/** Assets at customers: serial, model, warranty, status and the next maintenance. */
export default function AssetsPage() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const [q, setQ] = useState('');
  const [status, setStatus] = useState<string>(ALL);
  const [page, setPage] = useState(0);
  const [adding, setAdding] = useState(false);
  const manage = can('service.manage');
  const { data, error, isPending, refetch } = useAssets(slug, { q: q.trim() || undefined, status: status === ALL ? undefined : status, page, size: SIZE }, manage);
  if (!manage) return <PermissionDenied />;
  const add = <Button size="sm" onClick={() => setAdding(true)}><Plus className="size-4" />{t('service.newAsset')}</Button>;
  return (
    <>
      <PageHeader title={t('service.assetsTitle')} subtitle={data ? t('service.count', { count: data.total }) : undefined} actions={add} />
      <AssetTabs />
      <div className="mb-4 grid grid-cols-[1fr_auto] gap-3 sm:flex sm:items-end">
        <div className="space-y-1 sm:w-72">
          <Label htmlFor="a-q" className="text-xs text-muted-foreground">{t('common.search')}</Label>
          <Input id="a-q" type="search" value={q} placeholder={t('service.searchAssets')} onChange={(e) => { setQ(e.target.value); setPage(0); }} />
        </div>
        <div className="space-y-1">
          <Label htmlFor="a-s" className="text-xs text-muted-foreground">{t('service.status')}</Label>
          <Select value={status} onValueChange={(v) => { setStatus(v); setPage(0); }}>
            <SelectTrigger id="a-s" className="w-36"><SelectValue /></SelectTrigger>
            <SelectContent><SelectItem value={ALL}>{t('service.anyStatus')}</SelectItem>{ASSET_STATUSES.map((s) => <SelectItem key={s} value={s}>{ASSET_LABEL[s]}</SelectItem>)}</SelectContent>
          </Select>
        </div>
      </div>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={5} />
        : data.items.length === 0 ? (q || status !== ALL
          ? <EmptyState icon={SearchX} title={t('service.noMatchTitle')} body={t('service.noMatchBody')} />
          : <EmptyState icon={Cpu} title={t('service.noAssetsTitle')} body={t('service.noAssetsBody')} action={add} />)
          : (
            <>
              <div className="hidden rounded-lg border bg-card md:block">
                <Table>
                  <TableHeader><TableRow>
                    <TableHead>{t('service.assetName')}</TableHead><TableHead>{t('service.serial')}</TableHead><TableHead>{t('service.customer')}</TableHead>
                    <TableHead>{t('service.status')}</TableHead><TableHead>{t('service.warranty')}</TableHead><TableHead>{t('service.nextMaintenance')}</TableHead>
                  </TableRow></TableHeader>
                  <TableBody>
                    {data.items.map((a) => (
                      <TableRow key={a.id}>
                        <TableCell className="font-medium"><Link href={`/${slug}/service/assets/${a.id}`} className="text-primary hover:underline">{a.name}</Link>
                          {a.model && <span className="block text-xs font-normal text-muted-foreground">{a.model}</span>}</TableCell>
                        <TableCell className="tabular">{a.serial ?? '—'}</TableCell>
                        <TableCell className="max-w-48 truncate">{a.customer?.name}{a.site ? ` · ${a.site.name}` : ''}</TableCell>
                        <TableCell><AssetBadge status={a.status} /></TableCell>
                        <TableCell className="whitespace-nowrap">{a.warrantyEnd ? (a.underWarranty ? t('service.warrantyUntil', { date: day(a.warrantyEnd) }) : <span className="text-muted-foreground">{t('service.warrantyEnded', { date: day(a.warrantyEnd) })}</span>) : '—'}</TableCell>
                        <TableCell className="whitespace-nowrap">{a.nextMaintenance ? day(a.nextMaintenance) : '—'}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
              <ul className="divide-y rounded-lg border bg-card md:hidden">
                {data.items.map((a) => (
                  <li key={a.id}><Link href={`/${slug}/service/assets/${a.id}`} className="flex items-start justify-between gap-3 px-4 py-3 hover:bg-muted/50">
                    <span className="min-w-0"><span className="block truncate text-sm font-medium">{a.name}</span>
                      <span className="block truncate text-xs text-muted-foreground">{[a.serial, a.customer?.name].filter(Boolean).join(' · ')}</span></span>
                    <AssetBadge status={a.status} />
                  </Link></li>
                ))}
              </ul>
              <Pager page={page} size={SIZE} total={data.total} onPage={setPage} />
            </>
          )}
      {adding && <AssetDialog org={slug} open onOpenChange={setAdding} />}
    </>
  );
}
