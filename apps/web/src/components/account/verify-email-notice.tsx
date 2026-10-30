'use client';

import { MailCheck } from 'lucide-react';
import { useState } from 'react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { useT } from '@/i18n/provider';
import { accountApi } from '@/lib/api/account';
import { errorMessage } from '@/lib/api/client';
import { useSession } from '@/lib/auth/session';

/** Until the account opens the emailed link: what it can't do yet, and "send the link again". */
export function VerifyEmailNotice() {
  const { t } = useT();
  const { user } = useSession();
  const [busy, setBusy] = useState(false);
  if (!user || user.emailVerified) return null;
  return (
    <div role="status" className="mb-4 flex flex-wrap items-center gap-x-3 gap-y-2 rounded-md border border-sky-200 bg-sky-50 px-3 py-2 text-sm text-sky-950 dark:border-sky-900 dark:bg-sky-950 dark:text-sky-100">
      <MailCheck className="size-4 shrink-0" aria-hidden />
      <span className="flex-1">{t('auth.verifyBanner', { email: user.email })}</span>
      <Button size="sm" variant="outline" disabled={busy} onClick={async () => {
        setBusy(true);
        try { await accountApi.resendVerification(); toast.success(t('auth.verifySent', { email: user.email })); } catch (e) { toast.error(errorMessage(e)); } finally { setBusy(false); }
      }}>{t('auth.verifyResend')}</Button>
    </div>
  );
}
