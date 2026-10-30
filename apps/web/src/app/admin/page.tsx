'use client';

import Link from 'next/link';

import { PageHeader } from '@/components/common/page-header';
import { ErrorState, ListSkeleton } from '@/components/common/states';
import { AuditSentence, When } from '@/components/platform/ui';
import { moduleName } from '@/components/shell/nav';
import { Skeleton } from '@/components/ui/skeleton';
import { useT } from '@/i18n/provider';
import { usePlatformAudit, usePlatformStats } from '@/lib/api/platform';

function Stat({ label, value, hint, href }: { label: string; value: number; hint?: string; href?: string }) {
  const body = (
    <>
      <p className="text-sm text-muted-foreground">{label}</p>
      <p className="mt-1 text-2xl font-semibold tabular">{value.toLocaleString()}</p>
      {hint && <p className="mt-1 text-xs text-muted-foreground">{hint}</p>}
    </>
  );
  const cls = 'block rounded-lg border bg-card p-4';
  return href ? <Link href={href} className={`${cls} hover:border-primary/40`}>{body}</Link> : <div className={cls}>{body}</div>;
}

export default function PlatformOverview() {
  const { t } = useT();
  const stats = usePlatformStats();
  const recent = usePlatformAudit('', 0, 5);
  const name = (key: string) => {
    const m = stats.data?.modules.find((x) => x.key === key);
    return m ? moduleName(m) : key;
  };
  const s = stats.data;
  const most = Math.max(1, ...(s?.modules.map((m) => m.organisations) ?? [1]));

  return (
    <>
      <PageHeader title={t('platform.navOverview')} subtitle={t('platform.overviewLead')} />
      {stats.error ? <ErrorState error={stats.error} retry={() => stats.refetch()} /> : !s ? (
        <div className="grid grid-cols-2 gap-3 lg:grid-cols-3">{Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-24" />)}</div>
      ) : (
        <>
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-3">
            <Stat label={t('platform.statOrgs')} value={s.organisations} hint={t('platform.statOrgsNew', { count: s.newOrganisations30d })} href="/admin/organisations" />
            <Stat label={t('platform.statSuspended')} value={s.suspendedOrganisations} hint={t('platform.statSuspendedHint', { count: s.deletedOrganisations })} href="/admin/organisations?status=SUSPENDED" />
            <Stat label={t('platform.statUsers')} value={s.users} hint={t('platform.statUsersNew', { count: s.newUsers30d })} href="/admin/users" />
            <Stat label={t('platform.statActive')} value={s.activeUsers30d} hint={t('platform.statActiveHint')} />
            <Stat label={t('platform.statDisabled')} value={s.disabledUsers} href="/admin/users?status=DISABLED" />
            <Stat label={t('platform.statSuperAdmins')} value={s.superAdmins} href="/admin/users?status=SUPER_ADMIN" />
          </div>

          <div className="mt-6 grid gap-4 lg:grid-cols-2">
            <section className="rounded-lg border bg-card p-4 sm:p-5">
              <h2 className="text-base font-semibold">{t('platform.adoption')}</h2>
              <p className="mb-4 text-sm text-muted-foreground">{t('platform.adoptionLead')}</p>
              <ul className="space-y-3">
                {s.modules.map((m) => (
                  <li key={m.key}>
                    <div className="flex items-baseline justify-between gap-2 text-sm">
                      <span>{moduleName(m)}</span>
                      <span className="text-muted-foreground tabular">{t('platform.adoptionCount', { count: m.organisations })}</span>
                    </div>
                    <div className="mt-1 h-2 rounded-full bg-muted" aria-hidden>
                      <div className="h-2 rounded-full bg-primary" style={{ width: `${(m.organisations / most) * 100}%` }} />
                    </div>
                  </li>
                ))}
              </ul>
            </section>

            <section className="rounded-lg border bg-card p-4 sm:p-5">
              <div className="mb-3 flex items-baseline justify-between gap-2">
                <h2 className="text-base font-semibold">{t('platform.recent')}</h2>
                <Link href="/admin/audit" className="text-sm text-primary hover:underline">{t('platform.seeAll')}</Link>
              </div>
              {recent.error ? <ErrorState error={recent.error} /> : !recent.data ? <ListSkeleton rows={3} /> : recent.data.items.length === 0 ? (
                <p className="text-sm text-muted-foreground">{t('platform.noHistory')}</p>
              ) : (
                <ul className="divide-y">
                  {recent.data.items.map((e) => (
                    <li key={e.id} className="flex items-start justify-between gap-3 py-2.5 text-sm">
                      <AuditSentence e={e} moduleName={name} />
                      <span className="shrink-0 text-xs text-muted-foreground"><When at={e.at} pattern="d MMM HH:mm" /></span>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </div>
        </>
      )}
    </>
  );
}
