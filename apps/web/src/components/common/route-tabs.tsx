'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';

import { useT } from '@/i18n/provider';
import { useOrg } from '@/lib/org-context';
import { cn } from '@/lib/utils';

export interface RouteTab { href: string; label: string; exact?: boolean }

/**
 * Underlined tabs between sibling pages that belong together (each tab is its own address), e.g. "My leave /
 * Workforce" or the Reports hub. `href` is under the organisation. With fewer than two tabs nothing shows.
 */
export function RouteTabs({ tabs, className }: { tabs: RouteTab[]; className?: string }) {
  const { slug } = useOrg();
  const { t } = useT();
  const path = usePathname();
  if (tabs.length < 2) return null;
  // The most specific tab that matches wins (/hr/attendance/board over /hr/attendance).
  const full = (h: string) => `/${slug}/${h}`;
  const active = [...tabs].sort((a, b) => b.href.length - a.href.length)
    .find((tab) => (tab.exact ? path === full(tab.href) : path === full(tab.href) || path.startsWith(`${full(tab.href)}/`)));
  return (
    <nav aria-label={t('shell.pageTabs')} className={cn('-mx-1 mb-6 overflow-x-auto border-b', className)}>
      <ul className="flex gap-1 px-1">
        {tabs.map((tab) => {
          const on = active?.href === tab.href;
          return (
            <li key={tab.href}>
              <Link href={full(tab.href)} aria-current={on ? 'page' : undefined}
                className={cn('relative -mb-px inline-flex h-10 items-center border-b-2 px-3 text-sm font-medium whitespace-nowrap transition-colors',
                  on ? 'border-primary text-foreground' : 'border-transparent text-muted-foreground hover:border-border hover:text-foreground')}>
                {tab.label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
