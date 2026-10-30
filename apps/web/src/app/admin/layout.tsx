'use client';

import { ArrowLeft, ShieldCheck } from 'lucide-react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import type { ReactNode } from 'react';

import { Button } from '@/components/ui/button';
import type { MessageKey } from '@/i18n/messages';
import { useT } from '@/i18n/provider';
import { useSession } from '@/lib/auth/session';
import { RequireUser } from '@/lib/require-user';
import { cn } from '@/lib/utils';

const NAV: { href: string; label: MessageKey; exact?: boolean }[] = [
  { href: '/admin', label: 'platform.navOverview', exact: true },
  { href: '/admin/organisations', label: 'platform.navOrgs' },
  { href: '/admin/users', label: 'platform.navUsers' },
  { href: '/admin/plans', label: 'platform.navPlans' },
  { href: '/admin/audit', label: 'platform.navAudit' },
];

/** The platform console's frame. The API refuses everyone but super admins; this only hides the pages. */
function Console({ children }: { children: ReactNode }) {
  const { t } = useT();
  const { user } = useSession();
  const path = usePathname();
  if (!user!.superAdmin) {
    return (
      <div className="mx-auto max-w-md px-4 py-20 text-center">
        <h1 className="text-lg font-semibold">{t('common.notFoundTitle')}</h1>
        <p className="mt-2 text-sm text-muted-foreground">{t('platform.notSuperAdmin')}</p>
        <Button asChild className="mt-6"><Link href="/">{t('platform.backToApp')}</Link></Button>
      </div>
    );
  }
  return (
    <div className="min-h-dvh bg-background">
      <header className="sticky top-0 z-30 border-b bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/80">
        <div className="mx-auto flex h-14 max-w-6xl items-center gap-3 px-4 sm:px-6">
          <span className="grid size-8 place-items-center rounded-md bg-primary text-primary-foreground"><ShieldCheck className="size-4" aria-hidden /></span>
          <span className="font-semibold">{t('platform.title')}</span>
          <Link href="/" className="ml-auto inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
            <ArrowLeft className="size-4" />{t('platform.backToApp')}
          </Link>
        </div>
        <nav className="mx-auto flex max-w-6xl gap-1 overflow-x-auto px-2 sm:px-4" aria-label={t('platform.title')}>
          {NAV.map((n) => {
            const active = n.exact ? path === n.href : path.startsWith(n.href);
            return (
              <Link key={n.href} href={n.href} aria-current={active ? 'page' : undefined}
                className={cn('whitespace-nowrap border-b-2 px-3 py-2.5 text-sm text-muted-foreground hover:text-foreground',
                  active ? 'border-primary font-medium text-foreground' : 'border-transparent')}>
                {t(n.label)}
              </Link>
            );
          })}
        </nav>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-6 sm:px-6">{children}</main>
    </div>
  );
}

export default function AdminLayout({ children }: { children: ReactNode }) {
  return <RequireUser><Console>{children}</Console></RequireUser>;
}
