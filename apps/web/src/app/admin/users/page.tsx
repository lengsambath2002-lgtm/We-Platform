'use client';

import { SearchX, Users } from 'lucide-react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Suspense, useState } from 'react';

import { PageHeader } from '@/components/common/page-header';
import { PersonAvatar } from '@/components/common/person';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { Pager, StatusBadge, SuperAdminBadge, When } from '@/components/platform/ui';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useT } from '@/i18n/provider';
import { usePlatformUsers } from '@/lib/api/platform';

const SIZE = 25;
const FILTERS = ['ACTIVE', 'DISABLED', 'SUPER_ADMIN'] as const;

function Accounts() {
  const { t } = useT();
  const params = useSearchParams();
  const [q, setQ] = useState('');
  const [status, setStatus] = useState(() => (FILTERS as readonly string[]).includes(params.get('status') ?? '') ? params.get('status')! : 'all');
  const [page, setPage] = useState(0);
  const { data, error, isPending, refetch } = usePlatformUsers(q.trim(), status, page, SIZE);
  const filtered = !!q.trim() || status !== 'all';

  return (
    <>
      <PageHeader title={t('platform.navUsers')} subtitle={data ? t('platform.total', { count: data.total }) : undefined} />
      <div className="mb-4 flex flex-wrap items-end gap-3">
        <div className="min-w-0 flex-1 space-y-1.5 sm:max-w-xs">
          <Label htmlFor="user-search">{t('common.search')}</Label>
          <Input id="user-search" type="search" placeholder={t('platform.searchUsers')} value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="user-status">{t('platform.show')}</Label>
          <Select value={status} onValueChange={(v) => { setStatus(v); setPage(0); }}>
            <SelectTrigger id="user-status" className="w-44"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="all">{t('platform.all')}</SelectItem>
              <SelectItem value="ACTIVE">{t('platform.statusACTIVE')}</SelectItem>
              <SelectItem value="DISABLED">{t('platform.statusDISABLED')}</SelectItem>
              <SelectItem value="SUPER_ADMIN">{t('platform.superAdmins')}</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>

      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={6} /> : data.items.length === 0 ? (
        filtered ? <EmptyState icon={SearchX} title={t('platform.noMatch')} body={t('platform.noMatchBody')} />
          : <EmptyState icon={Users} title={t('platform.noUsers')} />
      ) : (
        <>
          <div className="overflow-x-auto rounded-lg border bg-card">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('platform.colPerson')}</TableHead>
                  <TableHead>{t('platform.colStatus')}</TableHead>
                  <TableHead className="text-right">{t('platform.colOrgs')}</TableHead>
                  <TableHead>{t('platform.colJoined')}</TableHead>
                  <TableHead>{t('platform.colLastSeen')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.items.map((u) => (
                  <TableRow key={u.id}>
                    <TableCell className="min-w-56">
                      <div className="flex items-center gap-3">
                        <PersonAvatar person={u} className="size-8" />
                        <div className="min-w-0">
                          <Link href={`/admin/users/${u.id}`} className="block truncate font-medium text-primary hover:underline">{u.name}</Link>
                          <p className="truncate text-xs text-muted-foreground">{u.email}</p>
                        </div>
                      </div>
                    </TableCell>
                    <TableCell><div className="flex flex-wrap gap-1"><StatusBadge status={u.status} />{u.superAdmin && <SuperAdminBadge />}</div></TableCell>
                    <TableCell className="text-right tabular">{u.organisations}</TableCell>
                    <TableCell><When at={u.createdAt} /></TableCell>
                    <TableCell><When at={u.lastSeenAt} /></TableCell>
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
  return <Suspense><Accounts /></Suspense>;
}
