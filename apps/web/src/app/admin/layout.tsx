'use client';

import { ArrowLeft, Building2, LayoutDashboard, type LucideIcon, Receipt, ScrollText, ShieldCheck, Tags, Users } from 'lucide-react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import type { ReactNode } from 'react';

import { PersonAvatar } from '@/components/common/person';
import { Button } from '@/components/ui/button';
import type { MessageKey } from '@/i18n/messages';
import { useT } from '@/i18n/provider';
import { useSession } from '@/lib/auth/session';
import { RequireUser } from '@/lib/require-user';
import { cn } from '@/lib/utils';

type NavItem = { href: string; label: MessageKey; icon: LucideIcon; exact?: boolean };

const GROUPS: { title?: MessageKey; items: NavItem[] }[] = [
  { items: [{ href: '/admin', label: 'platform.navOverview', icon: LayoutDashboard, exact: true }] },
  {
    title: 'platform.navGroupCustomers',
    items: [
      { href: '/admin/organisations', label: 'platform.navOrgs', icon: Building2 },
      { href: '/admin/users', label: 'platform.navUsers', icon: Users },
    ],
  },
  {
    title: 'platform.navGroupBilling',
    items: [
      { href: '/admin/plans', label: 'platform.navPlans', icon: Tags },
      { href: '/admin/invoices', label: 'platform.navInvoices', icon: Receipt },
    ],
  },
  { title: 'platform.navGroupSecurity', items: [{ href: '/admin/audit', label: 'platform.navAudit', icon: ScrollText }] },
];

const ALL = GROUPS.flatMap((g) => g.items);
const isActive = (path: string, n: NavItem) => (n.exact ? path === n.href : path === n.href || path.startsWith(`${n.href}/`));

function Brand() {
  const { t } = useT();
  return (
    <div className="flex items-center gap-2.5">
      <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-primary text-primary-foreground shadow-sm">
        <ShieldCheck className="size-4" aria-hidden />
      </span>
      <div className="min-w-0 leading-tight">
        <p className="truncate text-sm font-semibold">{t('platform.title')}</p>
        <p className="truncate text-xs text-muted-foreground">Business Platform</p>
      </div>
    </div>
  );
}

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
    <div className="min-h-dvh bg-background lg:flex">
      {/* Desktop: a fixed column with grouped navigation and who is signed in. */}
      <aside className="sticky top-0 hidden h-dvh w-62 shrink-0 flex-col border-r bg-sidebar lg:flex">
        <div className="flex h-16 items-center border-b px-4"><Brand /></div>
        <nav className="flex-1 overflow-y-auto px-3 py-4" aria-label={t('platform.title')}>
          {GROUPS.map((g, gi) => (
            <div key={g.title ?? gi} className={cn(gi > 0 && 'mt-6')}>
              {g.title && <p className="mb-1.5 px-2.5 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground/80">{t(g.title)}</p>}
              <ul className="space-y-0.5">
                {g.items.map((n) => {
                  const active = isActive(path, n);
                  return (
                    <li key={n.href}>
                      <Link href={n.href} aria-current={active ? 'page' : undefined}
                        className={cn('group flex h-9 items-center gap-3 rounded-md px-2.5 text-sm font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground',
                          active && 'bg-accent text-accent-foreground hover:bg-accent hover:text-accent-foreground')}>
                        <n.icon className={cn('size-[18px] shrink-0', active ? 'text-primary' : 'text-muted-foreground group-hover:text-foreground')} aria-hidden />
                        {t(n.label)}
                      </Link>
                    </li>
                  );
                })}
              </ul>
            </div>
          ))}
        </nav>
        <div className="space-y-1 border-t p-3">
          <div className="flex items-center gap-3 rounded-md px-2 py-2">
            <PersonAvatar person={user!} className="size-8" />
            <div className="min-w-0 flex-1 leading-tight">
              <p className="truncate text-sm font-medium">{user!.name}</p>
              <p className="truncate text-xs text-muted-foreground">{user!.email}</p>
            </div>
          </div>
          <Link href="/" className="flex h-9 items-center gap-3 rounded-md px-2.5 text-sm text-muted-foreground transition-colors hover:bg-muted hover:text-foreground">
            <ArrowLeft className="size-4" aria-hidden />{t('platform.backToApp')}
          </Link>
        </div>
      </aside>

      <div className="min-w-0 flex-1">
        {/* Phones and tablets: brand and a scrolling row of the same sections. */}
        <header className="sticky top-0 z-30 border-b bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/80 lg:hidden">
          <div className="flex h-14 items-center gap-3 px-4">
            <Brand />
            <Link href="/" className="ml-auto inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
              <ArrowLeft className="size-4" aria-hidden />{t('platform.backToApp')}
            </Link>
          </div>
          <nav className="flex gap-1 overflow-x-auto px-3 pb-2" aria-label={t('platform.title')}>
            {ALL.map((n) => {
              const active = isActive(path, n);
              return (
                <Link key={n.href} href={n.href} aria-current={active ? 'page' : undefined}
                  className={cn('inline-flex shrink-0 items-center gap-1.5 rounded-md px-2.5 py-1.5 text-sm text-muted-foreground hover:bg-muted hover:text-foreground',
                    active && 'bg-accent font-medium text-accent-foreground')}>
                  <n.icon className="size-4" aria-hidden />{t(n.label)}
                </Link>
              );
            })}
          </nav>
        </header>
        <main className="mx-auto w-full max-w-[1280px] px-4 py-6 sm:px-6 lg:px-8 lg:py-8">{children}</main>
      </div>
    </div>
  );
}

export default function AdminLayout({ children }: { children: ReactNode }) {
  return <RequireUser><Console>{children}</Console></RequireUser>;
}
