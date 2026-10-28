'use client';

import { AppWindow, Unlink } from 'lucide-react';
import { useState } from 'react';
import { toast } from 'sonner';

import { ErrorState, ListSkeleton } from '@/components/common/states';
import {
  AlertDialog, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Button } from '@/components/ui/button';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { type ConnectedApp, useConnectedApps, useRevokeApp } from '@/lib/api/sso';
import { day } from '@/lib/format';

/** Apps the person allowed to sign them in with this account (single sign-on); remove one to be asked again. */
export function ConnectedAppsSection() {
  const { t } = useT();
  const apps = useConnectedApps();
  const revoke = useRevokeApp();
  const [removing, setRemoving] = useState<ConnectedApp | null>(null);
  const [error, setError] = useState<string | null>(null);
  const close = () => { setError(null); setRemoving(null); };

  return (
    <section className="mt-4 rounded-lg border bg-card p-4 sm:p-6" aria-labelledby="apps-title">
      <h2 id="apps-title" className="mb-1 text-base font-semibold">{t('sso.appsTitle')}</h2>
      <p className="mb-4 text-sm text-muted-foreground">{t('sso.appsLead')}</p>
      {apps.error ? <ErrorState error={apps.error} retry={() => apps.refetch()} /> : apps.isPending ? <ListSkeleton rows={1} /> : apps.data.length === 0 ? (
        <p className="rounded-md border border-dashed px-3 py-4 text-sm text-muted-foreground">{t('sso.appsEmpty')}</p>
      ) : (
        <ul className="divide-y rounded-md border">
          {apps.data.map((a) => (
            <li key={a.clientId} className="flex items-center gap-3 px-3 py-3">
              <AppWindow className="size-5 shrink-0 text-muted-foreground" aria-hidden />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium">{a.name}</p>
                <p className="text-xs text-muted-foreground">{[t('sso.allowedOn', { date: day(a.grantedAt) }), t('sso.seesSummary')].join(' · ')}</p>
              </div>
              <Button variant="ghost" size="sm" className="shrink-0" aria-label={t('sso.revokeOf', { app: a.name })} onClick={() => setRemoving(a)}>
                <Unlink className="size-4" /><span className="hidden sm:inline">{t('sso.revoke')}</span>
              </Button>
            </li>
          ))}
        </ul>
      )}
      <AlertDialog open={!!removing} onOpenChange={(v) => { if (!v) close(); }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('sso.revokeTitle', { app: removing?.name ?? '' })}</AlertDialogTitle>
            <AlertDialogDescription>{t('sso.revokeBody', { app: removing?.name ?? '' })}</AlertDialogDescription>
          </AlertDialogHeader>
          {error && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}
          <AlertDialogFooter>
            <AlertDialogCancel>{t('common.cancel')}</AlertDialogCancel>
            <Button variant="destructive" disabled={revoke.isPending} onClick={async () => {
              if (!removing) return;
              setError(null);
              try {
                await revoke.mutateAsync(removing.clientId);
                toast.success(t('sso.revoked', { app: removing.name }));
                close();
              } catch (e) { setError(errorMessage(e)); }
            }}>{t(revoke.isPending ? 'sso.revoking' : 'sso.revoke')}</Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </section>
  );
}
