'use client';

import { Building2, SearchX } from 'lucide-react';
import { useSearchParams } from 'next/navigation';
import { Suspense, useState } from 'react';

import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { ConsoleHeader, Facts, ListPanel, ListRow, ModuleChips, OrgMark, Pager, SearchField, Segmented, StatusBadge, When } from '@/components/platform/ui';
import { moduleName } from '@/components/shell/nav';
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
      <ConsoleHeader title={t('platform.navOrgs')} subtitle={data ? t('platform.total', { count: data.total }) : t('platform.orgsLead')} />
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <SearchField id="org-search" label={t('common.search')} placeholder={t('platform.searchOrgs')} value={q} onChange={(v) => { setQ(v); setPage(0); }} />
        <Segmented label={t('platform.show')} value={status} onChange={(v) => { setStatus(v); setPage(0); }}
          options={[{ value: 'all', label: t('platform.all') }, ...STATUSES.map((s) => ({ value: s, label: t(`platform.status${s}`) }))]} />
      </div>

      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={6} /> : data.items.length === 0 ? (
        filtered ? <EmptyState icon={SearchX} title={t('platform.noMatch')} body={t('platform.noMatchBody')} />
          : <EmptyState icon={Building2} title={t('platform.noOrgs')} />
      ) : (
        <ListPanel footer={<Pager page={page} pages={Math.max(1, Math.ceil(data.total / SIZE))} onPage={setPage} total={data.total} size={SIZE} />}>
          {data.items.map((o) => (
            <ListRow key={o.id} href={`/admin/organisations/${o.id}`} lead={<OrgMark name={o.name} />} title={o.name}
              sub={<>/{o.slug}{o.owners.length > 0 && <> · {o.owners.map((p) => p.name).join(', ')}</>}</>}
              meta={<ModuleChips names={o.modules.map(name)} max={3} />} metaClassName="md:w-72 lg:w-96"
              aside={<>
                <div className="hidden w-56 xl:block">
                  <Facts items={[
                    { label: t('platform.colMembers'), value: o.members.toLocaleString() },
                    { label: t('platform.colLastActivity'), value: <When at={o.lastActivityAt} pattern="d MMM" /> },
                  ]} />
                </div>
                <StatusBadge status={o.status} />
              </>} />
          ))}
        </ListPanel>
      )}
    </>
  );
}

export default function Page() {
  return <Suspense><Organisations /></Suspense>;
}
