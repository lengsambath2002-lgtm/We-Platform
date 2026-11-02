'use client';

import { parseISO } from 'date-fns';
import { AlertTriangle, ArrowLeft, Ban, Building2, PlayCircle, RotateCcw, Trash2 } from 'lucide-react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { toast } from 'sonner';

import { PersonAvatar } from '@/components/common/person';
import { ErrorState } from '@/components/common/states';
import { SubscriptionCard } from '@/components/platform/subscription-card';
import { ConfirmAction, OrgMark, Panel, Facts, ListPanel, ListRow, Prop, StatusBadge, TargetHistory, When } from '@/components/platform/ui';
import { MODULE_ICON, moduleName } from '@/components/shell/nav';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { Switch } from '@/components/ui/switch';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { platformApi, usePlatformMutation, usePlatformOrg } from '@/lib/api/platform';
import { fmt } from '@/lib/format';
import { cn } from '@/lib/utils';

export default function PlatformOrg() {
  const { t } = useT();
  const { id } = useParams<{ id: string }>();
  const { data, error, refetch } = usePlatformOrg(id);
  const toggle = usePlatformMutation((v: { key: string; on: boolean }) => platformApi.setOrgModule(id, v.key, v.on));
  const act = usePlatformMutation((fn: () => Promise<unknown>) => fn());

  const back = <Link href="/admin/organisations" className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="size-4" />{t('platform.navOrgs')}</Link>;
  if (error) return <>{back}<ErrorState error={error} retry={() => refetch()} /></>;
  if (!data) return <>{back}<Skeleton className="h-32 w-full rounded-xl" /><div className="mt-6 grid gap-6 lg:grid-cols-3"><Skeleton className="h-72 rounded-xl lg:col-span-2" /><Skeleton className="h-72 rounded-xl" /></div></>;
  const { org } = data;
  const day = (iso: string) => fmt(parseISO(iso), 'd MMM yyyy');
  const enabled = data.modules.filter((m) => m.enabled).length;

  return (
    <>
      {back}
      <section className="mb-6 rounded-xl border bg-card p-5 shadow-xs sm:p-6">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="flex min-w-0 items-center gap-4">
            <OrgMark name={org.name} className="size-14 rounded-xl text-lg" />
            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-2">
                <h1 className="truncate text-2xl font-semibold tracking-tight">{org.name}</h1>
                <StatusBadge status={org.status} />
              </div>
              <p className="mt-0.5 text-sm text-muted-foreground">/{org.slug}</p>
            </div>
          </div>
          <div className="flex flex-wrap gap-2">
            {org.status === 'ACTIVE' && (
              <ConfirmAction destructive trigger={<Button variant="outline" className="text-destructive hover:text-destructive"><Ban className="size-4" />{t('platform.suspend')}</Button>}
                title={t('platform.suspendTitle', { name: org.name })} body={t('platform.suspendBody')}
                reason={{ label: t('platform.suspendReason'), hint: t('platform.suspendReasonHint') }}
                confirm={t('platform.suspend')} busy={t('platform.suspending')}
                run={async (reason) => { await act.mutateAsync(() => platformApi.suspendOrg(id, reason)); toast.success(t('platform.suspended', { name: org.name })); }} />
            )}
            {org.status === 'SUSPENDED' && (
              <Button disabled={act.isPending} onClick={async () => {
                try { await act.mutateAsync(() => platformApi.reactivateOrg(id)); toast.success(t('platform.reactivated', { name: org.name })); } catch (e) { toast.error(errorMessage(e)); }
              }}><PlayCircle className="size-4" />{t('platform.reactivate')}</Button>
            )}
            {org.status === 'DELETED' && (
              <ConfirmAction trigger={<Button><RotateCcw className="size-4" />{t('platform.restore')}</Button>}
                title={t('platform.restoreTitle', { name: org.name })} body={t('platform.restoreBody')} confirm={t('platform.restore')}
                run={async () => { await act.mutateAsync(() => platformApi.restoreOrg(id)); toast.success(t('platform.restored', { name: org.name })); }} />
            )}
          </div>
        </div>
        <dl className="mt-5 grid grid-cols-2 gap-4 border-t pt-5 sm:grid-cols-4">
          <div><dt className="text-xs text-muted-foreground">{t('platform.colMembers')}</dt><dd className="mt-1 text-lg font-semibold tabular">{org.members.toLocaleString()}</dd></div>
          <div><dt className="text-xs text-muted-foreground">{t('platform.colModules')}</dt><dd className="mt-1 text-lg font-semibold tabular">{enabled} / {data.modules.length}</dd></div>
          <div><dt className="text-xs text-muted-foreground">{t('platform.createdOn')}</dt><dd className="mt-1 text-sm font-medium"><When at={org.createdAt} /></dd></div>
          <div><dt className="text-xs text-muted-foreground">{t('platform.colLastActivity')}</dt><dd className="mt-1 text-sm font-medium"><When at={org.lastActivityAt} /></dd></div>
        </dl>
      </section>

      {data.suspendedAt && (
        <div role="status" className="mb-6 flex gap-3 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-200">
          <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden />
          <div>
            <p className="font-medium">{t('platform.suspendedOn', { date: day(data.suspendedAt), name: data.suspendedBy?.name ?? t('platform.someone') })}</p>
            {data.suspendedReason && <p className="mt-1">{t('platform.suspendedWhy')}: {data.suspendedReason}</p>}
          </div>
        </div>
      )}
      {data.deletedAt && (
        <div role="status" className="mb-6 flex gap-3 rounded-xl border bg-muted p-4 text-sm">
          <Trash2 className="mt-0.5 size-4 shrink-0 text-muted-foreground" aria-hidden />
          {t('platform.deletedOn', { date: day(data.deletedAt), name: data.deletedBy?.name ?? t('platform.someone') })}
        </div>
      )}

      <div className="grid items-start gap-6 lg:grid-cols-3">
        <div className="min-w-0 space-y-6 lg:col-span-2">
          <SubscriptionCard key={JSON.stringify(data.subscription)} orgId={id} sub={data.subscription} />

          <Panel title={t('platform.colModules')} lead={t('platform.orgModulesLead')}>
            <ul className="grid gap-2 sm:grid-cols-2">
              {data.modules.map((m) => {
                const label = moduleName(m);
                const Icon = MODULE_ICON[m.key] ?? Building2;
                return (
                  <li key={m.key} className={cn('flex items-center gap-3 rounded-lg border p-3 transition-colors', m.enabled ? 'border-primary/25 bg-accent/40' : 'bg-card')}>
                    <span className={cn('grid size-9 shrink-0 place-items-center rounded-lg', m.enabled ? 'bg-primary/10 text-primary' : 'bg-muted text-muted-foreground')}>
                      <Icon className="size-4" aria-hidden />
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium">{label}</p>
                      {!m.available && <p className="text-xs text-muted-foreground">{t('platform.moduleUnavailable')}</p>}
                    </div>
                    <Switch aria-label={label} checked={m.enabled} disabled={toggle.isPending || org.status === 'DELETED' || (!m.available && !m.enabled)}
                      onCheckedChange={async (on) => {
                        try {
                          await toggle.mutateAsync({ key: m.key, on });
                          toast.success(t(on ? 'platform.moduleOn' : 'platform.moduleOff', { module: label }));
                        } catch (e) { toast.error(errorMessage(e)); }
                      }} />
                  </li>
                );
              })}
            </ul>
          </Panel>

          <div>
            <h2 className="mb-3 text-base font-semibold">{t('platform.members')} <span className="font-normal text-muted-foreground tabular">· {data.members.length}</span></h2>
            <ListPanel>
              {data.members.map((m) => (
                <ListRow key={m.userId} href={`/admin/users/${m.userId}`} lead={<PersonAvatar person={{ name: m.name, avatarUrl: null }} className="size-9" />}
                  title={m.name} sub={m.email}
                  meta={<Facts items={[{ label: t('platform.colJoined'), value: <When at={m.joinedAt} /> }]} />}
                  aside={<div className="flex flex-wrap justify-end gap-1">
                    {m.disabled && <Badge className="bg-destructive/10 text-destructive">{t('platform.memberDisabled')}</Badge>}
                    {m.status !== 'ACTIVE' && <Badge variant="outline">{t('platform.memberSuspended')}</Badge>}
                    <Badge variant="outline" className="font-medium">{t(`org.role_${m.role as 'OWNER' | 'ADMIN' | 'MEMBER' | 'GUEST'}`)}</Badge>
                  </div>} />
              ))}
            </ListPanel>
          </div>

          <TargetHistory target={id} />
        </div>

        <Panel title={t('platform.details')} className="lg:sticky lg:top-8">
          <dl className="-my-2.5 divide-y">
            <Prop label={t('platform.webAddress')}>/{org.slug}</Prop>
            <Prop label={t('platform.createdOn')}><When at={org.createdAt} /></Prop>
            <Prop label={t('platform.createdBy')}>
              {data.createdBy ? <Link href={`/admin/users/${data.createdBy.id}`} className="text-primary hover:underline">{data.createdBy.name}</Link> : t('common.none')}
            </Prop>
            <Prop label={t('platform.colMembers')}><span className="tabular">{org.members}</span></Prop>
            <Prop label={t('platform.colLastActivity')}><When at={org.lastActivityAt} pattern="d MMM yyyy HH:mm" /></Prop>
            <Prop label={t('platform.colOwners')}>
              {org.owners.length === 0 ? t('common.none') : org.owners.map((p, i) => (
                <span key={p.id}>{i > 0 && ', '}<Link href={`/admin/users/${p.id}`} className="text-primary hover:underline">{p.name}</Link></span>
              ))}
            </Prop>
          </dl>
        </Panel>
      </div>
    </>
  );
}
