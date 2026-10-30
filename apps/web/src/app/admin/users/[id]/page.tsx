'use client';

import { parseISO } from 'date-fns';
import { ArrowLeft, KeyRound, LogOut, Smartphone, ShieldCheck, ShieldOff, UserCheck, UserX } from 'lucide-react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { toast } from 'sonner';

import { PersonAvatar } from '@/components/common/person';
import { ErrorState } from '@/components/common/states';
import { ConfirmAction, StatusBadge, SuperAdminBadge, TargetHistory, When } from '@/components/platform/ui';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { platformApi, usePlatformMutation, usePlatformUser } from '@/lib/api/platform';
import { useSession } from '@/lib/auth/session';
import { fmt } from '@/lib/format';

export default function PlatformUser() {
  const { t } = useT();
  const { id } = useParams<{ id: string }>();
  const { user: me } = useSession();
  const { data, error, refetch } = usePlatformUser(id);
  const act = usePlatformMutation((fn: () => Promise<unknown>) => fn());

  const back = <Link href="/admin/users" className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="size-4" />{t('platform.navUsers')}</Link>;
  if (error) return <>{back}<ErrorState error={error} retry={() => refetch()} /></>;
  if (!data) return <>{back}<Skeleton className="h-8 w-64" /><Skeleton className="mt-6 h-64 w-full" /></>;
  const { user } = data;
  const self = me?.id === user.id;
  const disabled = user.status === 'DISABLED';
  const run = (fn: () => Promise<unknown>, done: string) => async () => { await act.mutateAsync(fn); toast.success(done); };

  return (
    <>
      {back}
      <div className="mb-6 flex flex-wrap items-start justify-between gap-4">
        <div className="flex min-w-0 items-center gap-4">
          <PersonAvatar person={user} className="size-14 text-base" />
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="truncate text-xl font-semibold tracking-tight">{user.name}</h1>
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

      {data.disabledAt && (
        <div role="status" className="mb-4 rounded-lg border border-destructive/30 bg-destructive/5 p-4 text-sm">
          <p className="font-medium text-destructive">{t('platform.disabledOn', { date: fmt(parseISO(data.disabledAt), 'd MMM yyyy') })}</p>
          {data.disabledReason && <p className="mt-1">{t('platform.suspendedWhy')}: {data.disabledReason}</p>}
        </div>
      )}

      <div className="grid gap-4 lg:grid-cols-3">
        <section className="rounded-lg border bg-card p-4 sm:p-5">
          <h2 className="mb-3 text-base font-semibold">{t('platform.accountDetails')}</h2>
          <dl className="space-y-3 text-sm">
            <div><dt className="text-muted-foreground">{t('platform.colJoined')}</dt><dd><When at={user.createdAt} /></dd></div>
            <div><dt className="text-muted-foreground">{t('platform.colLastSeen')}</dt><dd><When at={user.lastSeenAt} pattern="d MMM yyyy HH:mm" /></dd></div>
            <div><dt className="text-muted-foreground">{t('platform.devices')}</dt><dd className="tabular">{data.signedInDevices}</dd></div>
            <div><dt className="text-muted-foreground">{t('account.twoStep')}</dt><dd>{t(data.twoStep ? 'account.twoStepOn' : 'platform.twoStepOffShort')}</dd></div>
            <div><dt className="text-muted-foreground">{t('platform.language')}</dt><dd>{data.locale === 'km' ? 'ខ្មែរ' : 'English'}</dd></div>
          </dl>
        </section>

        <section className="rounded-lg border bg-card p-4 sm:p-5 lg:col-span-2">
          <h2 className="text-base font-semibold">{t('platform.colOrgs')}</h2>
          <p className="mb-3 text-sm text-muted-foreground">{t('platform.userOrgsLead')}</p>
          {data.organisations.length === 0 ? <p className="text-sm text-muted-foreground">{t('platform.noUserOrgs')}</p> : (
            <ul className="divide-y">
              {data.organisations.map((o) => (
                <li key={o.id} className="flex flex-wrap items-center justify-between gap-2 py-2.5">
                  <div className="min-w-0">
                    <Link href={`/admin/organisations/${o.id}`} className="font-medium text-primary hover:underline">{o.name}</Link>
                    <p className="text-xs text-muted-foreground">/{o.slug} · {t(`org.role_${o.role as 'OWNER' | 'ADMIN' | 'MEMBER' | 'GUEST'}`)}</p>
                  </div>
                  <StatusBadge status={o.orgStatus} />
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      <TargetHistory target={id} />
    </>
  );
}
