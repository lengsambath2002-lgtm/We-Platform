'use client';

import { SearchX, Users } from 'lucide-react';
import { useSearchParams } from 'next/navigation';
import { Suspense, useState } from 'react';

import { PersonAvatar } from '@/components/common/person';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { ConsoleHeader, Facts, ListPanel, ListRow, Pager, SearchField, Segmented, StatusBadge, SuperAdminBadge, When } from '@/components/platform/ui';
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
      <ConsoleHeader title={t('platform.navUsers')} subtitle={data ? t('platform.total', { count: data.total }) : undefined} />
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <SearchField id="user-search" label={t('common.search')} placeholder={t('platform.searchUsers')} value={q} onChange={(v) => { setQ(v); setPage(0); }} />
        <Segmented label={t('platform.show')} value={status} onChange={(v) => { setStatus(v); setPage(0); }} options={[
          { value: 'all', label: t('platform.all') },
          { value: 'ACTIVE', label: t('platform.statusACTIVE') },
          { value: 'DISABLED', label: t('platform.statusDISABLED') },
          { value: 'SUPER_ADMIN', label: t('platform.superAdmins') },
        ]} />
      </div>

      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={6} /> : data.items.length === 0 ? (
        filtered ? <EmptyState icon={SearchX} title={t('platform.noMatch')} body={t('platform.noMatchBody')} />
          : <EmptyState icon={Users} title={t('platform.noUsers')} />
      ) : (
        <ListPanel footer={<Pager page={page} pages={Math.max(1, Math.ceil(data.total / SIZE))} onPage={setPage} total={data.total} size={SIZE} />}>
          {data.items.map((u) => (
            <ListRow key={u.id} href={`/admin/users/${u.id}`} lead={<PersonAvatar person={u} className="size-9" />} title={u.name} sub={u.email}
              meta={<Facts items={[
                { label: t('platform.colOrgs'), value: u.organisations.toLocaleString() },
                { label: t('platform.colJoined'), value: <When at={u.createdAt} pattern="d MMM yyyy" /> },
                { label: t('platform.colLastSeen'), value: <When at={u.lastSeenAt} pattern="d MMM" /> },
              ]} />}
              aside={<div className="flex flex-wrap justify-end gap-1">{u.superAdmin && <SuperAdminBadge />}<StatusBadge status={u.status} /></div>} />
          ))}
        </ListPanel>
      )}
    </>
  );
}

export default function Page() {
  return <Suspense><Accounts /></Suspense>;
}
