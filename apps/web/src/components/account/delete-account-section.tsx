'use client';

import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';

import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useT } from '@/i18n/provider';
import { accountApi } from '@/lib/api/account';
import { ApiError, errorMessage } from '@/lib/api/client';
import { useSession } from '@/lib/auth/session';

/** "Delete my account": organisations to hand over first, then the password (and a code) to confirm. */
export function DeleteAccountSection() {
  const { t } = useT();
  const { signOut } = useSession();
  const blockers = useQuery({ queryKey: ['me', 'delete-blockers'], queryFn: accountApi.deletionBlockers });
  const twoStep = useQuery({ queryKey: ['me', 'two-step'], queryFn: accountApi.twoStepStatus });
  const [open, setOpen] = useState(false);
  const [password, setPassword] = useState('');
  const [code, setCode] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const blocked = blockers.data ?? [];
  const needsCode = twoStep.data?.enabled ?? false;
  const word = t('account.deleteWord');

  return (
    <section className="mt-4 rounded-lg border border-destructive/30 bg-card p-4 sm:p-6" aria-labelledby="delete-account-title">
      <h2 id="delete-account-title" className="mb-1 text-base font-semibold text-destructive">{t('account.deleteTitle')}</h2>
      <p className="mb-3 text-sm text-muted-foreground">{t('account.deleteLead')}</p>
      {blocked.length > 0 && <p role="status" className="mb-3 rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-900 dark:bg-amber-950 dark:text-amber-200">{t('account.deleteBlocked', { orgs: blocked.join(', ') })}</p>}
      <Button variant="destructive" disabled={blocked.length > 0 || !blockers.data} onClick={() => setOpen(true)}>{t('account.deleteTitle')}</Button>
      {open && (
        <Dialog open onOpenChange={(o) => !o && setOpen(false)}>
          <DialogContent className="sm:max-w-sm">
            <form noValidate onSubmit={async (e) => {
              e.preventDefault();
              setBusy(true); setError(null);
              try {
                await accountApi.deleteAccount(password, needsCode ? code : null);
                await signOut();
                window.location.href = '/sign-in?deleted=1';
              } catch (x) {
                setError(x instanceof ApiError && x.fields[0]?.message ? x.fields[0].message : x instanceof ApiError && x.code === 'INVALID_STATE' ? x.message : errorMessage(x));
                setBusy(false);
              }
            }}>
              <DialogHeader>
                <DialogTitle>{t('account.deleteConfirmTitle')}</DialogTitle>
                <DialogDescription>{t('account.deleteConfirmBody')}</DialogDescription>
              </DialogHeader>
              <div className="my-4 space-y-3">
                <div className="space-y-1.5"><Label htmlFor="del-pw">{t('auth.password')}</Label><Input id="del-pw" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} /></div>
                {needsCode && <div className="space-y-1.5"><Label htmlFor="del-code">{t('account.twoStepCodeOrRecovery')}</Label><Input id="del-code" autoComplete="one-time-code" maxLength={20} value={code} onChange={(e) => setCode(e.target.value)} /></div>}
                <div className="space-y-1.5"><Label htmlFor="del-word">{t('account.deleteType', { word })}</Label><Input id="del-word" value={confirm} onChange={(e) => setConfirm(e.target.value)} /></div>
                {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
              </div>
              <DialogFooter>
                <Button type="button" variant="outline" onClick={() => setOpen(false)}>{t('common.cancel')}</Button>
                <Button type="submit" variant="destructive" disabled={busy || !password || confirm.trim() !== word || (needsCode && !code.trim())}>{t('account.deleteForever')}</Button>
              </DialogFooter>
            </form>
          </DialogContent>
        </Dialog>
      )}
    </section>
  );
}
