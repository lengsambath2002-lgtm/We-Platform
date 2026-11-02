'use client';

import { useState } from 'react';

import { Button } from '@/components/ui/button';
import { useT } from '@/i18n/provider';
import { ApiError } from '@/lib/api/client';
import { useSession } from '@/lib/auth/session';
import { firebaseCode, googleEnabled, googleIdToken } from '@/lib/firebase/google';

/** "Continue with Google"; hands a two-step challenge back to the sign-in page. Hidden without Firebase config. */
export function GoogleButton({ onChallenge, onError, onSignedIn }: {
  onChallenge: (challenge: string) => void;
  onError: (message: string | null) => void;
  /** After a sign-in without two-step (the sign-in page redirects by itself). */
  onSignedIn?: () => void;
}) {
  const { t, locale } = useT();
  const { signInWithGoogle } = useSession();
  const [busy, setBusy] = useState(false);
  if (!googleEnabled()) return null;
  return (
    <>
      <div className="my-4 flex items-center gap-3 text-xs text-muted-foreground"><span className="h-px flex-1 bg-border" />{t('auth.or')}<span className="h-px flex-1 bg-border" /></div>
      <Button type="button" variant="outline" className="w-full" disabled={busy} onClick={async () => {
        setBusy(true); onError(null);
        try {
          const challenge = await signInWithGoogle(await googleIdToken(locale));
          if (challenge) onChallenge(challenge);
          else onSignedIn?.();
        } catch (e) {
          const code = firebaseCode(e);
          if (code === 'auth/popup-closed-by-user' || code === 'auth/cancelled-popup-request') onError(null);
          else if (code === 'auth/popup-blocked') onError(t('auth.googleBlocked'));
          else if (code) onError(t('auth.googleUnavailable'));
          else if (e instanceof ApiError && e.code === 'ACCOUNT_DISABLED') onError(t('common.errAccountDisabled'));
          else if (e instanceof ApiError && (e.code === 'CONFLICT' || e.code === 'EMAIL_NOT_VERIFIED')) onError(e.message);
          else onError(t('auth.googleUnavailable'));
        } finally { setBusy(false); }
      }}>
        <svg aria-hidden viewBox="0 0 48 48" className="size-4"><path fill="#FFC107" d="M43.6 20.5H42V20H24v8h11.3C33.7 32.7 29.2 36 24 36c-6.6 0-12-5.4-12-12s5.4-12 12-12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.3-.1-2.4-.4-3.5z" /><path fill="#FF3D00" d="m6.3 14.7 6.6 4.8C14.7 15.1 19 12 24 12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 16.3 4 9.7 8.3 6.3 14.7z" /><path fill="#4CAF50" d="M24 44c5.2 0 9.9-2 13.4-5.2l-6.2-5.2C29.2 35.1 26.7 36 24 36c-5.2 0-9.6-3.3-11.3-7.9l-6.5 5C9.5 39.6 16.2 44 24 44z" /><path fill="#1976D2" d="M43.6 20.5H42V20H24v8h11.3c-.8 2.2-2.2 4.2-4.1 5.6l6.2 5.2C37 39.2 44 34 44 24c0-1.3-.1-2.4-.4-3.5z" /></svg>
        {t('auth.continueGoogle')}
      </Button>
    </>
  );
}
