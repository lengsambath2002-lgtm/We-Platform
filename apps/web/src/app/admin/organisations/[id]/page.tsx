'use client';

import { parseISO } from 'date-fns';
import { ArrowLeft, Ban, PlayCircle, RotateCcw } from 'lucide-react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { toast } from 'sonner';

import { ErrorState } from '@/components/common/states';
import { ConfirmAction, StatusBadge, TargetHistory, When } from '@/components/platform/ui';
import { moduleName } from '@/components/shell/nav';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { Switch } from '@/components/ui/switch';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { platformApi, usePlatformMutation, usePlatformOrg } from '@/lib/api/platform';
import { fmt } from '@/lib/format';

export default function PlatformOrg() {
  const { t } = useT();
  const { id } = useParams<{ id: string }>();
  const { data, error, refetch } = usePlatformOrg(id);
  const toggle = usePlatformMutation((v: { key: string; on: boolean }) => platformApi.setOrgModule(id, v.key, v.on));
  const act = usePlatformMutation((fn: () => Promise<unknown>) => fn());

  const back = <Link href="/admin/organisations" className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="size-4" />{t('platform.navOrgs')}</Link>;
  if (error) return <>{back}<ErrorState error={error} retry={() => refetch()} /></>;
  if (!data) return <>{back}<Skeleton className="h-8 w-64" /><Skeleton className="mt-6 h-64 w-full" /></>;
  const { org } = data;
  const day = (iso: string) => fmt(parseISO(iso), 'd MMM yyyy');

  return (
    <>
      {back}
      <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="truncate text-xl font-semibold tracking-tight">{org.name}</h1>
            <StatusBadge status={org.status} />
          </div>
          <p className="mt-1 text-sm text-muted-foreground">/{org.slug}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          {org.status === 'ACTIVE' && (
            <ConfirmAction destructive trigger={<Button variant="outline"><Ban className="size-4" />{t('platform.suspend')}</Button>}
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

      {data.suspendedAt && (
        <div role="status" className="mb-4 rounded-lg border border-amber-300 bg-amber-50 p-4 text-sm text-amber-900 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-200">
          <p className="font-medium">{t('platform.suspendedOn', { date: day(data.suspendedAt), name: data.suspendedBy?.name ?? t('platform.someone') })}</p>
          {data.suspendedReason && <p className="mt-1">{t('platform.suspendedWhy')}: {data.suspendedReason}</p>}
        </div>
      )}
      {data.deletedAt && (
        <div role="status" className="mb-4 rounded-lg border bg-muted p-4 text-sm">
          {t('platform.deletedOn', { date: day(data.deletedAt), name: data.deletedBy?.name ?? t('platform.someone') })}
        </div>
      )}

      <div className="grid gap-4 lg:grid-cols-3">
        <section className="rounded-lg border bg-card p-4 sm:p-5">
          <h2 className="mb-3 text-base font-semibold">{t('platform.details')}</h2>
          <dl className="space-y-3 text-sm">
            <div><dt className="text-muted-foreground">{t('platform.webAddress')}</dt><dd>/{org.slug}</dd></div>
            <div><dt className="text-muted-foreground">{t('platform.createdOn')}</dt><dd><When at={org.createdAt} /></dd></div>
            <div>
              <dt className="text-muted-foreground">{t('platform.createdBy')}</dt>
              <dd>{data.createdBy ? <Link href={`/admin/users/${data.createdBy.id}`} className="text-primary hover:underline">{data.createdBy.name}</Link> : t('common.none')}</dd>
            </div>
            <div><dt className="text-muted-foreground">{t('platform.colMembers')}</dt><dd className="tabular">{org.members}</dd></div>
            <div><dt className="text-muted-foreground">{t('platform.colLastActivity')}</dt><dd><When at={org.lastActivityAt} pattern="d MMM yyyy HH:mm" /></dd></div>
          </dl>
        </section>

        <section className="rounded-lg border bg-card p-4 sm:p-5 lg:col-span-2">
          <h2 className="text-base font-semibold">{t('platform.colModules')}</h2>
          <p className="mb-3 text-sm text-muted-foreground">{t('platform.orgModulesLead')}</p>
          <ul className="divide-y">
            {data.modules.map((m) => {
              const label = moduleName(m);
              return (
                <li key={m.key} className="flex items-center justify-between gap-3 py-2.5">
                  <div className="min-w-0">
                    <p className="text-sm font-medium">{label}</p>
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
        </section>
      </div>

      <section className="mt-4 rounded-lg border bg-card p-4 sm:p-5">
        <h2 className="mb-3 text-base font-semibold">{t('platform.members')}</h2>
        <div className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('platform.colPerson')}</TableHead>
                <TableHead>{t('platform.colRole')}</TableHead>
                <TableHead>{t('platform.colJoined')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.members.map((m) => (
                <TableRow key={m.userId}>
                  <TableCell className="min-w-56">
                    <Link href={`/admin/users/${m.userId}`} className="font-medium text-primary hover:underline">{m.name}</Link>
                    <p className="text-xs text-muted-foreground">{m.email}</p>
                  </TableCell>
                  <TableCell>
                    <div className="flex flex-wrap items-center gap-1">
                      <span className="text-sm">{t(`org.role_${m.role as 'OWNER' | 'ADMIN' | 'MEMBER' | 'GUEST'}`)}</span>
                      {m.disabled && <Badge className="bg-destructive/10 text-destructive">{t('platform.memberDisabled')}</Badge>}
                      {m.status !== 'ACTIVE' && <Badge variant="outline">{t('platform.memberSuspended')}</Badge>}
                    </div>
                  </TableCell>
                  <TableCell><When at={m.joinedAt} /></TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      </section>

      <TargetHistory target={id} />
    </>
  );
}
