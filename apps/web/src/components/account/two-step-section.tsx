'use client';

import { useQuery, useQueryClient } from '@tanstack/react-query';
import { parseISO } from 'date-fns';
import { Copy, Download, ShieldCheck } from 'lucide-react';
import { useState } from 'react';
import { toast } from 'sonner';

import { QrCode } from '@/components/common/qr-code';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useT } from '@/i18n/provider';
import { type TwoStepSetup, accountApi } from '@/lib/api/account';
import { ApiError, errorMessage } from '@/lib/api/client';
import { fmt } from '@/lib/format';

const key = ['me', 'two-step'] as const;

/** The API's field message for a wrong code or password, else the usual message. */
const why = (e: unknown) => (e instanceof ApiError && e.fields[0]?.message) || errorMessage(e);

function RecoveryCodes({ codes, onDone }: { codes: string[]; onDone: () => void }) {
  const { t } = useT();
  const text = codes.join('\n');
  return (
    <div className="space-y-3">
      <p className="text-sm">{t('account.twoStepCodesLead')}</p>
      <ul className="grid grid-cols-2 gap-x-6 gap-y-1 rounded-md border bg-muted/40 p-3 font-mono text-sm sm:max-w-sm" aria-label={t('account.twoStepCodes')}>
        {codes.map((c) => <li key={c}>{c}</li>)}
      </ul>
      <div className="flex flex-wrap gap-2">
        <Button variant="outline" size="sm" onClick={() => navigator.clipboard.writeText(text).then(() => toast.success(t('org.copied')))}><Copy className="size-4" />{t('account.twoStepCopy')}</Button>
        <Button variant="outline" size="sm" onClick={() => {
          const a = document.createElement('a');
          a.href = URL.createObjectURL(new Blob([`Business Platform recovery codes\n\n${text}\n`], { type: 'text/plain' }));
          a.download = 'business-platform-recovery-codes.txt';
          a.click();
          URL.revokeObjectURL(a.href);
        }}><Download className="size-4" />{t('account.twoStepDownload')}</Button>
        <Button size="sm" onClick={onDone}>{t('account.twoStepSaved')}</Button>
      </div>
    </div>
  );
}

function Setup({ setup, onEnabled, onCancel }: { setup: TwoStepSetup; onEnabled: (codes: string[]) => void; onCancel: () => void }) {
  const { t } = useT();
  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  return (
    <form className="space-y-4" noValidate onSubmit={async (e) => {
      e.preventDefault();
      setBusy(true); setError(null);
      try { onEnabled((await accountApi.enableTwoStep(code)).codes); } catch (x) { setError(why(x)); } finally { setBusy(false); }
    }}>
      <ol className="list-decimal space-y-1 pl-5 text-sm">
        <li>{t('account.twoStepStep1')}</li>
        <li>{t('account.twoStepStep2')}</li>
      </ol>
      <div className="flex flex-wrap items-center gap-4">
        <QrCode value={setup.uri} label={t('account.twoStepQr')} />
        <div className="space-y-1 text-sm">
          <p className="text-muted-foreground">{t('account.twoStepManual')}</p>
          <code className="block break-all rounded bg-muted px-2 py-1 font-mono text-xs">{setup.secret.match(/.{1,4}/g)?.join(' ')}</code>
        </div>
      </div>
      <div className="space-y-1.5 sm:max-w-xs">
        <Label htmlFor="ts-code">{t('account.twoStepCode')}</Label>
        <Input id="ts-code" inputMode="numeric" autoComplete="one-time-code" maxLength={6} value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))} />
        {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
      </div>
      <div className="flex gap-2">
        <Button type="submit" disabled={busy || code.length !== 6}>{t('account.twoStepTurnOn')}</Button>
        <Button type="button" variant="ghost" onClick={onCancel}>{t('common.cancel')}</Button>
      </div>
    </form>
  );
}

/** The password (and, to turn it off, a code) confirms a change. */
function Confirm({ off, onClose, onCodes }: { off: boolean; onClose: () => void; onCodes: (c: string[]) => void }) {
  const { t } = useT();
  const qc = useQueryClient();
  const [password, setPassword] = useState('');
  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-sm">
        <form noValidate onSubmit={async (e) => {
          e.preventDefault();
          setBusy(true); setError(null);
          try {
            if (off) { await accountApi.disableTwoStep(password, code); toast.success(t('account.twoStepOffDone')); onClose(); }
            else { onCodes((await accountApi.newRecoveryCodes(password)).codes); onClose(); }
            await qc.invalidateQueries({ queryKey: key });
          } catch (x) { setError(why(x)); } finally { setBusy(false); }
        }}>
          <DialogHeader>
            <DialogTitle>{t(off ? 'account.twoStepOff' : 'account.twoStepNewCodes')}</DialogTitle>
            <DialogDescription>{t(off ? 'account.twoStepOffLead' : 'account.twoStepNewCodesLead')}</DialogDescription>
          </DialogHeader>
          <div className="my-4 space-y-3">
            <div className="space-y-1.5"><Label htmlFor="ts-pw">{t('auth.password')}</Label><Input id="ts-pw" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} /></div>
            {off && <div className="space-y-1.5"><Label htmlFor="ts-off-code">{t('account.twoStepCodeOrRecovery')}</Label><Input id="ts-off-code" autoComplete="one-time-code" maxLength={20} value={code} onChange={(e) => setCode(e.target.value)} /></div>}
            {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
            <Button type="submit" variant={off ? 'destructive' : 'default'} disabled={busy || !password || (off && !code.trim())}>{t(off ? 'account.twoStepOff' : 'account.twoStepNewCodes')}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

/** Two-step sign-in: a code from an authenticator app after the password. */
export function TwoStepSection() {
  const { t } = useT();
  const qc = useQueryClient();
  const status = useQuery({ queryKey: key, queryFn: accountApi.twoStepStatus });
  const [setup, setSetup] = useState<TwoStepSetup | null>(null);
  const [codes, setCodes] = useState<string[] | null>(null);
  const [confirm, setConfirm] = useState<'off' | 'codes' | null>(null);
  const s = status.data;

  return (
    <section className="mt-4 rounded-lg border bg-card p-4 sm:p-6" aria-labelledby="two-step-title">
      <h2 id="two-step-title" className="mb-1 flex items-center gap-2 text-base font-semibold">
        {t('account.twoStep')}{s?.enabled && <span className="inline-flex items-center gap-1 rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-medium text-emerald-800 dark:bg-emerald-950 dark:text-emerald-200"><ShieldCheck className="size-3" />{t('account.twoStepOn')}</span>}
      </h2>
      <p className="mb-4 text-sm text-muted-foreground">{t('account.twoStepLead')}</p>
      {codes ? <RecoveryCodes codes={codes} onDone={() => setCodes(null)} />
        : setup ? <Setup setup={setup} onCancel={() => setSetup(null)} onEnabled={async (c) => { setSetup(null); setCodes(c); toast.success(t('account.twoStepOnDone')); await qc.invalidateQueries({ queryKey: key }); }} />
        : !s ? null
        : s.enabled ? (
          <div className="space-y-3">
            <p className="text-sm">{t('account.twoStepSince', { date: fmt(parseISO(s.enabledAt!), 'd MMM yyyy'), count: s.recoveryCodesLeft })}</p>
            <div className="flex flex-wrap gap-2">
              <Button variant="outline" onClick={() => setConfirm('codes')}>{t('account.twoStepNewCodes')}</Button>
              <Button variant="outline" onClick={() => setConfirm('off')}>{t('account.twoStepOff')}</Button>
            </div>
          </div>
        ) : (
          <Button onClick={async () => { try { setSetup(await accountApi.setupTwoStep()); } catch (e) { toast.error(errorMessage(e)); } }}>{t('account.twoStepSetUp')}</Button>
        )}
      {confirm && <Confirm off={confirm === 'off'} onClose={() => setConfirm(null)} onCodes={setCodes} />}
    </section>
  );
}
