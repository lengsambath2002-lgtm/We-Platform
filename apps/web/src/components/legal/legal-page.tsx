'use client';

import { ArrowLeft } from 'lucide-react';
import Link from 'next/link';

import { LanguageSwitch } from '@/components/shell/language-switch';
import { useT } from '@/i18n/provider';

import { LEGAL_VERSION, en, km } from './content';

/** The Terms of Service or the Privacy Policy, in the reader's language; public, printable. */
export function LegalPage({ doc }: { doc: 'terms' | 'privacy' }) {
  const { t, locale } = useT();
  const d = (locale === 'km' ? km : en)[doc];
  return (
    <main className="mx-auto max-w-[720px] px-4 py-8 sm:px-6 print:py-0">
      <div className="mb-6 flex items-center justify-between gap-4 print:hidden">
        <Link href="/" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="size-4" />Business Platform</Link>
        <LanguageSwitch />
      </div>
      <article className="space-y-6">
        <header>
          <h1 className="text-2xl font-semibold tracking-tight">{d.title}</h1>
          <p className="mt-1 text-sm text-muted-foreground">{t('auth.legalUpdated', { date: LEGAL_VERSION })}</p>
          <p className="mt-4 leading-relaxed">{d.lead}</p>
        </header>
        {d.sections.map((s) => (
          <section key={s.title} className="space-y-2">
            <h2 className="text-base font-semibold">{s.title}</h2>
            {s.body.map((p) => <p key={p} className="leading-relaxed text-muted-foreground">{p}</p>)}
          </section>
        ))}
      </article>
      <nav className="mt-10 flex gap-4 border-t pt-4 text-sm print:hidden" aria-label={t('auth.legalNav')}>
        <Link href="/terms" className="text-primary hover:underline">{(locale === 'km' ? km : en).terms.title}</Link>
        <Link href="/privacy" className="text-primary hover:underline">{(locale === 'km' ? km : en).privacy.title}</Link>
      </nav>
    </main>
  );
}

/** “Terms · Privacy” links, for the sign-in pages’ footer. */
export function LegalLinks() {
  const { t } = useT();
  return (
    <nav className="mt-6 flex justify-center gap-4 text-xs text-muted-foreground" aria-label={t('auth.legalNav')}>
      <Link href="/terms" className="hover:text-foreground hover:underline">{t('auth.terms')}</Link>
      <Link href="/privacy" className="hover:text-foreground hover:underline">{t('auth.privacy')}</Link>
    </nav>
  );
}
