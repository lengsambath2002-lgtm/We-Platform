'use client';

import { Building2, SearchX } from 'lucide-react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Suspense, useState } from 'react';

import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { Pager, StatusBadge, When } from '@/components/platform/ui';
import { moduleName } from '@/components/shell/nav';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useT } from '@/i18n/provider';
import { type OrgStatus, usePlatformOrgs, usePlatformStats } from '@/lib/api/platform';

const SIZE = 25;
const STATUSES: OrgStatus[] = ['ACTIVE', 'SUSPENDED', 'DELETED'];

function Organisations() {
  const { t } = useT();
  const params = useSearchParams();
  const [q, setQ] = useState('');
  const [status, setStatus] = useState(() => (STATUSES as string[]).includes(params.get('status') ?? '') ? params.get('status')! : 'all');
  const [page, setPage] = useState(0);
  const { data, error, isPending, refetch } = usePlatformOrgs(q.trim(), status, page, SIZE);
  const stats = usePlatformStats();
  const name = (key: string) => {
    const m = stats.data?.modules.find((x) => x.key === key);
    return m ? moduleName(m) : key;
  };
  const filtered = !!q.trim() || status !== 'all';

  return (
    <>
      <PageHeader title={t('platform.navOrgs')} subtitle={data ? t('platform.total', { count: data.total }) : t('platform.orgsLead')} />
      <div className="mb-4 flex flex-wrap items-end gap-3">
        <div className="min-w-0 flex-1 space-y-1.5 sm:max-w-xs">
          <Label htmlFor="org-search">{t('common.search')}</Label>
          <Input id="org-search" type="search" placeholder={t('platform.searchOrgs')} value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="org-status">{t('platform.show')}</Label>
          <Select value={status} onValueChange={(v) => { setStatus(v); setPage(0); }}>
            <SelectTrigger id="org-status" className="w-40"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="all">{t('platform.all')}</SelectItem>
              {STATUSES.map((s) => <SelectItem key={s} value={s}>{t(`platform.status${s}`)}</SelectItem>)}
            </SelectContent>
          </Select>
        </div>
      </div>

      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={6} /> : data.items.length === 0 ? (
        filtered ? <EmptyState icon={SearchX} title={t('platform.noMatch')} body={t('platform.noMatchBody')} />
          : <EmptyState icon={Building2} title={t('platform.noOrgs')} />
      ) : (
        <>
          <div className="overflow-x-auto rounded-lg border bg-card">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('platform.colOrg')}</TableHead>
                  <TableHead>{t('platform.colStatus')}</TableHead>
                  <TableHead className="text-right">{t('platform.colMembers')}</TableHead>
                  <TableHead>{t('platform.colOwners')}</TableHead>
                  <TableHead>{t('platform.colModules')}</TableHead>
                  <TableHead>{t('platform.colCreated')}</TableHead>
                  <TableHead>{t('platform.colLastActivity')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.items.map((o) => (
                  <TableRow key={o.id}>
                    <TableCell className="min-w-48">
                      <Link href={`/admin/organisations/${o.id}`} className="font-medium text-primary hover:underline">{o.name}</Link>
                      <p className="text-xs text-muted-foreground">/{o.slug}</p>
                    </TableCell>
                    <TableCell><StatusBadge status={o.status} /></TableCell>
                    <TableCell className="text-right tabular">{o.members}</TableCell>
                    <TableCell className="max-w-56">
                      {o.owners.length === 0 ? <span className="text-muted-foreground">{t('common.none')}</span> : o.owners.map((p) => (
                        <Link key={p.id} href={`/admin/users/${p.id}`} className="block truncate text-sm hover:underline">{p.name}</Link>
                      ))}
                    </TableCell>
                    <TableCell className="max-w-64 text-sm text-muted-foreground">{o.modules.map(name).join(', ') || t('common.none')}</TableCell>
                    <TableCell><When at={o.createdAt} /></TableCell>
                    <TableCell><When at={o.lastActivityAt} /></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          <Pager page={page} pages={Math.max(1, Math.ceil(data.total / SIZE))} onPage={setPage} />
        </>
      )}
    </>
  );
}

export default function Page() {
  return <Suspense><Organisations /></Suspense>;
}
