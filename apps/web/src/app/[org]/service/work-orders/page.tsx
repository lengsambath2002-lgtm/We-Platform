'use client';

import { ClipboardList, FilePlus2, SearchX } from 'lucide-react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Suspense, useState } from 'react';

import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { WorkOrderList } from '@/components/service/lists';
import { Pager, WO_LABEL } from '@/components/service/ui';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { useT } from '@/i18n/provider';
import { WO_STATUSES, useStaff, useWorkOrders } from '@/lib/api/service';
import { useOrg } from '@/lib/org-context';

const SIZE = 25;
const ALL = 'all';
const STATUS_FILTERS = ['OPEN', ...WO_STATUSES] as const;

/** Work orders, soonest first (technicians: only theirs, "My work orders"); filter by status and technician. */
function WorkOrders() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const params = useSearchParams();
  const manage = can('service.manage');
  const [status, setStatus] = useState<string>(STATUS_FILTERS.includes(params.get('status') as never) ? params.get('status')! : 'OPEN');
  const [tech, setTech] = useState<string>(ALL);
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const staff = useStaff(slug, manage);
  const { data, error, isPending, refetch } = useWorkOrders(slug, {
    q: q.trim() || undefined, status: status === ALL ? undefined : status, technician: tech === ALL ? undefined : tech, page, size: SIZE,
  });
  const filtered = status !== 'OPEN' || tech !== ALL || !!q.trim();
  const create = manage && <Button size="sm" asChild><Link href={`/${slug}/service/work-orders/new`}><FilePlus2 className="size-4" />{t('service.newWorkOrder')}</Link></Button>;

  return (
    <>
      <PageHeader title={manage ? t('service.workOrdersTitle') : t('service.myWorkOrdersTitle')} subtitle={data ? t('service.count', { count: data.total }) : undefined} actions={create} />
      <div className="mb-4 grid grid-cols-2 gap-3 sm:flex sm:flex-wrap sm:items-end">
        <div className="col-span-2 space-y-1 sm:w-72">
          <Label htmlFor="w-q" className="text-xs text-muted-foreground">{t('common.search')}</Label>
          <Input id="w-q" type="search" value={q} placeholder={t('service.searchWorkOrders')} onChange={(e) => { setQ(e.target.value); setPage(0); }} />
        </div>
        <div className="space-y-1">
          <Label htmlFor="w-s" className="text-xs text-muted-foreground">{t('service.status')}</Label>
          <Select value={status} onValueChange={(v) => { setStatus(v); setPage(0); }}>
            <SelectTrigger id="w-s" className="w-40 max-w-full"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>{t('service.anyStatus')}</SelectItem>
              {STATUS_FILTERS.map((s) => <SelectItem key={s} value={s}>{s === 'OPEN' ? t('service.openOnly') : WO_LABEL[s]}</SelectItem>)}
            </SelectContent>
          </Select>
        </div>
        {manage && (
          <div className="space-y-1">
            <Label htmlFor="w-t" className="text-xs text-muted-foreground">{t('service.technician')}</Label>
            <Select value={tech} onValueChange={(v) => { setTech(v); setPage(0); }}>
              <SelectTrigger id="w-t" className="w-44 max-w-full"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>{t('service.anyone')}</SelectItem>
                {(staff.data ?? []).filter((s) => s.role === 'TECHNICIAN').map((s) => <SelectItem key={s.id} value={s.person.id}>{s.person.name}</SelectItem>)}
              </SelectContent>
            </Select>
          </div>
        )}
      </div>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={6} />
        : data.items.length === 0 ? (filtered
          ? <EmptyState icon={SearchX} title={t('service.noMatchTitle')} body={t('service.noMatchBody')} />
          : <EmptyState icon={ClipboardList} title={t('service.noWorkOrdersTitle')} body={manage ? t('service.noWorkOrdersBody') : t('service.noWorkOrdersBodyTech')} action={create} />)
          : <><WorkOrderList org={slug} items={data.items} /><Pager page={page} size={SIZE} total={data.total} onPage={setPage} /></>}
    </>
  );
}

export default function Page() {
  return <Suspense><WorkOrders /></Suspense>;
}
