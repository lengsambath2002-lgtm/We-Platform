'use client';

import { parseISO } from 'date-fns';
import { ArrowLeft, KeyRound, LogOut, Smartphone, ShieldCheck, ShieldOff, UserCheck, UserX } from 'lucide-react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { toast } from 'sonner';

import { PersonAvatar } from '@/components/common/person';
import { ErrorState } from '@/components/common/states';
import { ConfirmAction, ListPanel, ListRow, OrgMark, Panel, Prop, StatusBadge, SuperAdminBadge, TargetHistory, When } from '@/components/platform/ui';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { platformApi, usePlatformMutation, usePlatformUser } from '@/lib/api/platform';
import { useSession } from '@/lib/auth/session';
import { fmt } from '@/lib/format';
import { cn } from '@/lib/utils';

export default function PlatformUser() {
  const { t } = useT();
  const { id } = useParams<{ id: string }>();
  const { user: me } = useSession();
  const { data, error, refetch } = usePlatformUser(id);
  const act = usePlatformMutation((fn: () => Promise<unknown>) => fn());

  const back = <Link href="/admin/users" className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="size-4" />{t('platform.navUsers')}</Link>;
  if (error) return <>{back}<ErrorState error={error} retry={() => refetch()} /></>;
  if (!data) return <>{back}<Skeleton className="h-40 w-full rounded-xl" /><div className="mt-6 grid gap-6 lg:grid-cols-3"><Skeleton className="h-72 rounded-xl lg:col-span-2" /><Skeleton className="h-72 rounded-xl" /></div></>;
  const { user } = data;
  const self = me?.id === user.id;
  const disabled = user.status === 'DISABLED';
  const run = (fn: () => Promise<unknown>, done: string) => async () => { await act.mutateAsync(fn); toast.success(done); };

  return (
    <>
      {back}
      <section className="mb-6 rounded-xl border bg-card p-5 shadow-xs sm:p-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex min-w-0 items-center gap-4">
          <PersonAvatar person={user} className="size-14 text-base" />
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="truncate text-2xl font-semibold tracking-tight">{user.name}</h1>
              <StatusBadge status={user.status} />
              {user.superAdmin && <SuperAdminBadge />}
            </div>
            <p className="truncate text-sm text-muted-foreground">{user.email}</p>
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <ConfirmAction trigger={<Button variant="outline" disabled={data.signedInDevices === 0}><LogOut className="size-4" />{t('platform.signOutAll')}</Button>}
            title={t('platform.signOutAllTitle', { name: user.name })} body={t('platform.signOutAllBody')} confirm={t('platform.signOutAll')}
            run={run(() => platformApi.signOutUser(id), t('platform.signedOutAll', { name: user.name }))} />
          {!disabled && (
            <ConfirmAction trigger={<Button variant="outline"><KeyRound className="size-4" />{t('platform.sendReset')}</Button>}
              title={t('platform.sendResetTitle', { name: user.name })} body={t('platform.sendResetBody')} confirm={t('platform.sendReset')}
              run={run(() => platformApi.sendPasswordReset(id), t('platform.resetSent', { email: user.email }))} />
          )}
          {data.twoStep && !self && (
            <ConfirmAction trigger={<Button variant="outline"><Smartphone className="size-4" />{t('platform.resetTwoStep')}</Button>}
              title={t('platform.resetTwoStepTitle', { name: user.name })} body={t('platform.resetTwoStepBody')} confirm={t('platform.resetTwoStep')}
              run={run(() => platformApi.resetTwoStep(id), t('platform.twoStepReset', { name: user.name }))} />
          )}
          {!self && (user.superAdmin ? (
            <ConfirmAction destructive trigger={<Button variant="outline"><ShieldOff className="size-4" />{t('platform.removeSuperAdmin')}</Button>}
              title={t('platform.removeSuperAdminTitle', { name: user.name })} body={t('platform.removeSuperAdminBody')} confirm={t('platform.removeSuperAdmin')}
              run={run(() => platformApi.setSuperAdmin(id, false), t('platform.removedSuperAdmin', { name: user.name }))} />
          ) : !disabled && (
            <ConfirmAction trigger={<Button variant="outline"><ShieldCheck className="size-4" />{t('platform.makeSuperAdmin')}</Button>}
              title={t('platform.makeSuperAdminTitle', { name: user.name })} body={t('platform.makeSuperAdminBody')} confirm={t('platform.makeSuperAdmin')}
              run={run(() => platformApi.setSuperAdmin(id, true), t('platform.madeSuperAdmin', { name: user.name }))} />
          ))}
          {!self && !user.superAdmin && (disabled ? (
            <Button disabled={act.isPending} onClick={async () => {
              try { await run(() => platformApi.enableUser(id), t('platform.enabled', { name: user.name }))(); } catch (e) { toast.error(errorMessage(e)); }
            }}><UserCheck className="size-4" />{t('platform.enable')}</Button>
          ) : (
            <ConfirmAction destructive trigger={<Button variant="destructive"><UserX className="size-4" />{t('platform.disable')}</Button>}
              title={t('platform.disableTitle', { name: user.name })} body={t('platform.disableBody')}
              reason={{ label: t('platform.disableReason'), hint: t('platform.disableReasonHint') }}
              confirm={t('platform.disable')} busy={t('platform.disabling')}
              run={(reason) => run(() => platformApi.disableUser(id, reason), t('platform.disabled', { name: user.name }))()} />
          ))}
        </div>
      </div>
      <dl className="mt-5 grid grid-cols-2 gap-4 border-t pt-5 sm:grid-cols-4">
        <div><dt className="text-xs text-muted-foreground">{t('platform.colOrgs')}</dt><dd className="mt-1 text-lg font-semibold tabular">{data.organisations.length}</dd></div>
        <div><dt className="text-xs text-muted-foreground">{t('platform.devices')}</dt><dd className="mt-1 text-lg font-semibold tabular">{data.signedInDevices}</dd></div>
        <div><dt className="text-xs text-muted-foreground">{t('platform.colJoined')}</dt><dd className="mt-1 text-sm font-medium"><When at={user.createdAt} /></dd></div>
        <div><dt className="text-xs text-muted-foreground">{t('platform.colLastSeen')}</dt><dd className="mt-1 text-sm font-medium"><When at={user.lastSeenAt} /></dd></div>
      </dl>
      </section>

      {data.disabledAt && (
        <div role="status" className="mb-6 flex gap-3 rounded-xl border border-destructive/30 bg-destructive/5 p-4 text-sm">
          <UserX className="mt-0.5 size-4 shrink-0 text-destructive" aria-hidden />
          <div>
            <p className="font-medium text-destructive">{t('platform.disabledOn', { date: fmt(parseISO(data.disabledAt), 'd MMM yyyy') })}</p>
            {data.disabledReason && <p className="mt-1">{t('platform.suspendedWhy')}: {data.disabledReason}</p>}
          </div>
        </div>
      )}

      <div className="grid items-start gap-6 lg:grid-cols-3">
        <div className="min-w-0 space-y-6 lg:col-span-2">
          <div>
            <h2 className="text-base font-semibold">{t('platform.colOrgs')} <span className="font-normal text-muted-foreground tabular">· {data.organisations.length}</span></h2>
            <p className="mb-3 text-sm text-muted-foreground">{t('platform.userOrgsLead')}</p>
            {data.organisations.length === 0 ? (
              <div className="rounded-xl border border-dashed p-6 text-center text-sm text-muted-foreground">{t('platform.noUserOrgs')}</div>
            ) : (
              <ListPanel>
                {data.organisations.map((o) => (
                  <ListRow key={o.id} href={`/admin/organisations/${o.id}`} lead={<OrgMark name={o.name} />} title={o.name} sub={`/${o.slug}`}
                    aside={<>
                      <Badge variant="outline" className="font-medium">{t(`org.role_${o.role as 'OWNER' | 'ADMIN' | 'MEMBER' | 'GUEST'}`)}</Badge>
                      <StatusBadge status={o.orgStatus} />
                    </>} />
                ))}
              </ListPanel>
            )}
          </div>
          <TargetHistory target={id} />
        </div>

        <Panel title={t('platform.accountDetails')} className="lg:sticky lg:top-8">
          <dl className="-my-2.5 divide-y">
            <Prop label={t('platform.colJoined')}><When at={user.createdAt} /></Prop>
            <Prop label={t('platform.colLastSeen')}><When at={user.lastSeenAt} pattern="d MMM yyyy HH:mm" /></Prop>
            <Prop label={t('platform.devices')}><span className="tabular">{data.signedInDevices}</span></Prop>
            <Prop label={t('account.twoStep')}>
              <span className="inline-flex items-center gap-1.5">
                <span className={cn('size-1.5 rounded-full', data.twoStep ? 'bg-emerald-500' : 'bg-muted-foreground/50')} aria-hidden />
                {t(data.twoStep ? 'account.twoStepOn' : 'platform.twoStepOffShort')}
              </span>
            </Prop>
            <Prop label={t('platform.language')}>{data.locale === 'km' ? 'ខ្មែរ' : 'English'}</Prop>
          </dl>
        </Panel>
      </div>
    </>
  );
}
