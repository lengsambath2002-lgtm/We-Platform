'use client';

import { ArrowRight, Ban, Building2, CheckCircle2, type LucideIcon, Receipt, ShieldCheck, UserCheck, Users, UserX, Wallet } from 'lucide-react';
import Link from 'next/link';

import { ErrorState, ListSkeleton } from '@/components/common/states';
import { AuditRow, ConsoleHeader, Delta, Panel, StatCard } from '@/components/platform/ui';
import { MODULE_ICON, moduleName } from '@/components/shell/nav';
import { Skeleton } from '@/components/ui/skeleton';
import { useT } from '@/i18n/provider';
import { usePlatformInvoices } from '@/lib/api/billing';
import { usePlatformAudit, usePlatformStats } from '@/lib/api/platform';
import { usd } from '@/lib/money';
import { cn } from '@/lib/utils';

const pct = (n: number, of: number) => (of > 0 ? Math.round((n / of) * 100) : 0);

function Meter({ value, className }: { value: number; className?: string }) {
  return (
    <div className={cn('h-1.5 overflow-hidden rounded-full bg-muted', className)} aria-hidden>
      <div className="h-full rounded-full bg-primary transition-[width] duration-500" style={{ width: `${Math.min(100, Math.max(0, value))}%` }} />
    </div>
  );
}

function AttentionRow({ href, icon: Icon, label, value, detail, urgent }: {
  href: string; icon: LucideIcon; label: string; value: string; detail?: string; urgent?: boolean;
}) {
  return (
    <li>
      <Link href={href} className="group -mx-2 flex items-center gap-3 rounded-lg px-2 py-2.5 transition-colors hover:bg-muted/60">
        <span className={cn('grid size-9 shrink-0 place-items-center rounded-lg border',
          urgent ? 'border-amber-200 bg-amber-50 text-amber-700 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-300' : 'bg-muted text-muted-foreground')}>
          <Icon className="size-4" aria-hidden />
        </span>
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-medium">{label}</p>
          {detail && <p className={cn('truncate text-xs', urgent ? 'text-amber-700 dark:text-amber-300' : 'text-muted-foreground')}>{detail}</p>}
        </div>
        <span className="text-sm font-semibold tabular">{value}</span>
        <ArrowRight className="size-4 text-muted-foreground transition-transform group-hover:translate-x-0.5" aria-hidden />
      </Link>
    </li>
  );
}

export default function PlatformOverview() {
  const { t } = useT();
  const stats = usePlatformStats();
  const recent = usePlatformAudit('', 0, 6);
  const open = usePlatformInvoices('OPEN');
  const name = (key: string) => {
    const m = stats.data?.modules.find((x) => x.key === key);
    return m ? moduleName(m) : key;
  };
  const s = stats.data;
  const liveOrgs = s ? Math.max(0, s.organisations - s.suspendedOrganisations - s.deletedOrganisations) : 0;

  const invoices = open.data ?? [];
  const toCollect = invoices.reduce((sum, i) => sum + Number(i.amount), 0);
  const overdue = invoices.filter((i) => i.overdue).length;
  const claims = invoices.filter((i) => i.claimedAt).length;

  return (
    <>
      <ConsoleHeader title={t('platform.navOverview')} subtitle={t('platform.overviewLead')} />
      {stats.error ? <ErrorState error={stats.error} retry={() => stats.refetch()} /> : !s ? (
        <>
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">{Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-36 rounded-xl" />)}</div>
          <div className="mt-6 grid gap-6 lg:grid-cols-3"><Skeleton className="h-80 rounded-xl lg:col-span-2" /><Skeleton className="h-80 rounded-xl" /></div>
        </>
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <StatCard label={t('platform.statOrgs')} value={s.organisations} icon={Building2} href="/admin/organisations"
              hint={<Delta>{t('platform.statOrgsNew', { count: s.newOrganisations30d })}</Delta>} />
            <StatCard label={t('platform.statUsers')} value={s.users} icon={Users} href="/admin/users"
              hint={<Delta>{t('platform.statUsersNew', { count: s.newUsers30d })}</Delta>} />
            <StatCard label={t('platform.statActive')} value={s.activeUsers30d} icon={UserCheck}
              hint={t('platform.activeShare', { pct: pct(s.activeUsers30d, s.users) })}
              footer={<Meter value={pct(s.activeUsers30d, s.users)} />} />
            <StatCard label={t('platform.statSuspended')} value={s.suspendedOrganisations} icon={Ban} href="/admin/organisations?status=SUSPENDED"
              hint={t('platform.statSuspendedHint', { count: s.deletedOrganisations })} />
          </div>

          <div className="mt-6 grid items-start gap-6 lg:grid-cols-3">
            <Panel className="lg:col-span-2" title={t('platform.adoption')} lead={t('platform.adoptionLead')}>
              <ul className="grid gap-x-8 gap-y-5 sm:grid-cols-2">
                {s.modules.map((m) => {
                  const Icon = MODULE_ICON[m.key] ?? Building2;
                  const share = pct(m.organisations, liveOrgs);
                  return (
                    <li key={m.key} className="flex items-start gap-3">
                      <span className="grid size-9 shrink-0 place-items-center rounded-lg border bg-muted/60 text-muted-foreground"><Icon className="size-4" aria-hidden /></span>
                      <div className="min-w-0 flex-1">
                        <div className="flex items-baseline justify-between gap-2">
                          <span className="truncate text-sm font-medium">{moduleName(m)}</span>
                          <span className="text-sm font-semibold tabular">{share}%</span>
                        </div>
                        <Meter value={share} className="mt-2" />
                        <p className="mt-1.5 text-xs text-muted-foreground tabular">{t('platform.adoptionCount', { count: m.organisations })}</p>
                      </div>
                    </li>
                  );
                })}
              </ul>
            </Panel>

            <Panel title={t('platform.attention')} lead={t('platform.attentionLead')}>
              {open.error ? <ErrorState error={open.error} /> : !open.data ? <ListSkeleton rows={3} /> : (
                <>
                  <ul>
                    <AttentionRow href="/admin/invoices" icon={Wallet} label={t('platform.attnInvoices')} value={usd(toCollect)}
                      detail={overdue > 0 ? t('platform.attnOverdue', { count: overdue }) : t('platform.total', { count: invoices.length })} urgent={overdue > 0} />
                    <AttentionRow href="/admin/invoices" icon={Receipt} label={t('platform.attnClaims')} value={claims.toLocaleString()} urgent={claims > 0} />
                    <AttentionRow href="/admin/organisations?status=SUSPENDED" icon={Ban} label={t('platform.statSuspended')} value={s.suspendedOrganisations.toLocaleString()} />
                    <AttentionRow href="/admin/users?status=DISABLED" icon={UserX} label={t('platform.statDisabled')} value={s.disabledUsers.toLocaleString()} />
                    <AttentionRow href="/admin/users?status=SUPER_ADMIN" icon={ShieldCheck} label={t('platform.statSuperAdmins')} value={s.superAdmins.toLocaleString()} />
                  </ul>
                  {overdue === 0 && claims === 0 && (
                    <p className="mt-3 flex items-center gap-2 rounded-lg bg-muted/60 px-3 py-2 text-xs text-muted-foreground">
                      <CheckCircle2 className="size-4 text-emerald-600 dark:text-emerald-400" aria-hidden />{t('platform.allClear')}
                    </p>
                  )}
                </>
              )}
            </Panel>
          </div>

          <Panel className="mt-6" title={t('platform.recent')}
            action={<Link href="/admin/audit" className="inline-flex items-center gap-1 text-sm font-medium text-primary hover:underline">{t('platform.seeAll')}<ArrowRight className="size-3.5" aria-hidden /></Link>}>
            {recent.error ? <ErrorState error={recent.error} /> : !recent.data ? <ListSkeleton rows={3} /> : recent.data.items.length === 0 ? (
              <p className="text-sm text-muted-foreground">{t('platform.noHistory')}</p>
            ) : (
              <ul>{recent.data.items.map((e) => <AuditRow key={e.id} e={e} moduleName={name} />)}</ul>
            )}
          </Panel>
        </>
      )}
    </>
  );
}
