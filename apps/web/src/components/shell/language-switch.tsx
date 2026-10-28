'use client';

import { useCallback } from 'react';
import { toast } from 'sonner';

import { type Locale, LOCALES } from '@/i18n/core';
import { useT } from '@/i18n/provider';
import { errorMessage, http } from '@/lib/api/client';
import type { User } from '@/lib/api/types';
import { useSession } from '@/lib/auth/session';
import { cn } from '@/lib/utils';

/** The language's own name, so people can find theirs whatever language is showing. */
export const LOCALE_NAME: Record<Locale, string> = { en: 'English', km: 'ខ្មែរ' };

/**
 * Switches the UI language now and, when signed in, saves it on the account
 * (the session re-applies `user.locale`, so the user must be updated too).
 */
export function useChangeLocale() {
  const { locale, setLocale } = useT();
  const { user, setUser } = useSession();
  return useCallback(async (next: Locale) => {
    if (next === locale) return;
    const before = locale;
    setLocale(next);
    if (!user) return;
    try {
      setUser(await http.patch<User>('/me', { locale: next }));
    } catch (e) {
      setLocale(before);
      toast.error(errorMessage(e));
    }
  }, [locale, setLocale, user, setUser]);
}

/** A two-option control: English / ខ្មែរ. */
export function LanguageSwitch({ className }: { className?: string }) {
  const { t, locale } = useT();
  const change = useChangeLocale();
  return (
    <div role="radiogroup" aria-label={t('common.language')} className={cn('inline-flex rounded-md border p-0.5', className)}>
      {LOCALES.map((l) => (
        <button key={l} type="button" role="radio" aria-checked={locale === l} lang={l} onClick={() => void change(l)}
          className={cn('rounded px-3 py-1 text-sm text-muted-foreground hover:text-foreground',
            locale === l && 'bg-primary/10 font-medium text-primary hover:text-primary')}>
          {LOCALE_NAME[l]}
        </button>
      ))}
    </div>
  );
}
