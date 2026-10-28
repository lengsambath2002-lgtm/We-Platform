'use client';

import { LogOut, Monitor, Smartphone } from 'lucide-react';
import { toast } from 'sonner';

import { ErrorState, ListSkeleton } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { useT } from '@/i18n/provider';
import { describeAgent, type DeviceSession, useDeviceSessions, useSignOutDevice, useSignOutOtherDevices } from '@/lib/api/account';
import { errorMessage } from '@/lib/api/client';
import { useSession } from '@/lib/auth/session';
import { ago, day } from '@/lib/format';

function useDeviceName() {
  const { t } = useT();
  return (s: DeviceSession) => {
    const { browser, os } = describeAgent(s.userAgent);
    return browser && os ? t('settings.deviceOn', { browser, os }) : browser ?? os ?? t('settings.unknownDevice');
  };
}

/** Where the account is signed in; sign out one device or every other one. */
export function DevicesSection() {
  const { t } = useT();
  const sessions = useDeviceSessions();
  const signOutOne = useSignOutDevice();
  const signOutOthers = useSignOutOtherDevices();
  const { signOut } = useSession();
  const name = useDeviceName();
  const others = sessions.data?.filter((s) => !s.current).length ?? 0;

  return (
    <section className="mt-4 rounded-lg border bg-card p-4 sm:p-6" aria-labelledby="devices-title">
      <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 id="devices-title" className="mb-1 text-base font-semibold">{t('settings.devicesTitle')}</h2>
          <p className="text-sm text-muted-foreground">{t('settings.devicesLead')}</p>
        </div>
        {others > 0 && (
          <Button variant="outline" size="sm" disabled={signOutOthers.isPending} onClick={() => signOutOthers.mutateAsync()
            .then((r) => toast.success(t('settings.signedOutOthers', { count: r.count })))
            .catch((e) => toast.error(errorMessage(e)))}>
            {t('settings.signOutOthers')}
          </Button>
        )}
      </div>
      {sessions.error ? <ErrorState error={sessions.error} retry={() => sessions.refetch()} /> : sessions.isPending ? <ListSkeleton rows={2} /> : (
        <>
          <ul className="divide-y rounded-md border">
            {sessions.data.map((s) => {
              const Icon = describeAgent(s.userAgent).mobile ? Smartphone : Monitor;
              return (
                <li key={s.id} className="flex items-center gap-3 px-3 py-3">
                  <Icon className="size-5 shrink-0 text-muted-foreground" aria-hidden />
                  <div className="min-w-0 flex-1">
                    <p className="flex flex-wrap items-center gap-2 text-sm font-medium">
                      <span className="truncate">{name(s)}</span>
                      {s.current && <span className="rounded bg-primary/10 px-1.5 py-0.5 text-[11px] font-medium text-primary">{t('settings.thisDevice')}</span>}
                    </p>
                    <p className="text-xs text-muted-foreground">
                      {[s.ip, t('settings.signedInOn', { date: day(s.signedInAt) }), t('settings.lastActive', { ago: ago(s.lastUsedAt) })].filter(Boolean).join(' · ')}
                    </p>
                  </div>
                  <Button variant="ghost" size="sm" className="shrink-0" aria-label={t('settings.signOutDeviceOf', { device: name(s) })}
                    disabled={signOutOne.isPending}
                    onClick={() => s.current
                      ? signOut().then(() => { window.location.href = '/sign-in'; })
                      : signOutOne.mutateAsync(s.id).then(() => toast.success(t('settings.signedOutDevice'))).catch((e) => toast.error(errorMessage(e)))}>
                    <LogOut className="size-4" /><span className="hidden sm:inline">{t('settings.signOutDevice')}</span>
                  </Button>
                </li>
              );
            })}
          </ul>
          {others === 0 && <p className="mt-3 text-sm text-muted-foreground">{t('settings.noOtherDevices')}</p>}
        </>
      )}
    </section>
  );
}
