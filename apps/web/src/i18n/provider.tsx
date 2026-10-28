'use client';

import { Fragment, createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';

import { type Locale, setCurrentLocale, toLocale, translate, type Vars } from './core';
import type { MessageKey } from './messages';

const STORAGE = 'bp-locale';

interface I18n {
  locale: Locale;
  setLocale: (l: Locale) => void;
  t: (key: MessageKey, vars?: Vars) => string;
}

const Ctx = createContext<I18n | null>(null);

function initial(): Locale {
  try {
    const saved = localStorage.getItem(STORAGE);
    if (saved) return toLocale(saved);
  } catch { /* ignore */ }
  return typeof navigator !== 'undefined' ? toLocale(navigator.language) : 'en';
}

/**
 * The UI language. Signed-out pages use the saved or browser language; once signed in,
 * the account's language wins (see SessionProvider). Also sets <html lang>.
 */
export function I18nProvider({ children }: { children: ReactNode }) {
  const [locale, setState] = useState<Locale>('en');
  useEffect(() => { setState(initial()); }, []);

  const setLocale = useCallback((l: Locale) => {
    setState(l);
    try { localStorage.setItem(STORAGE, l); } catch { /* ignore */ }
  }, []);

  setCurrentLocale(locale);
  useEffect(() => { document.documentElement.lang = locale === 'km' ? 'km' : 'en'; }, [locale]);

  const value = useMemo<I18n>(() => ({ locale, setLocale, t: (key, vars) => translate(locale, key, vars) }), [locale, setLocale]);
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useT() {
  const c = useContext(Ctx);
  if (!c) throw new Error('useT outside I18nProvider');
  return c;
}

/** Remounts the pages when the language changes, so text formatted outside React (dates) follows. */
export function LocaleBoundary({ children }: { children: ReactNode }) {
  const { locale } = useT();
  return <Fragment key={locale}>{children}</Fragment>;
}
