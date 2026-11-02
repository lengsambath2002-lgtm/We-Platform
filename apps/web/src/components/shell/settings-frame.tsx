'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import type { ReactNode } from 'react';

import { useT } from '@/i18n/provider';
import { useOrg } from '@/lib/org-context';
import { cn } from '@/lib/utils';

import { isActiveItem, settingsNav } from './nav';

/** Whether this page is one of the settings area's (administration, or an app's settings). */
export function useInSettings() {
  const ctx = useOrg();
  const path = usePathname();
  return settingsNav(ctx).some((i) => isActiveItem(ctx.org.slug, path, i));
}

/**
 * The settings area: a "Settings" title and a grouped menu beside the page (a scrolling row on phones). Wraps the
 * administration pages and each app's settings page, so all settings live in one place.
 */
export function SettingsFrame({ children }: { children: ReactNode }) {
  const ctx = useOrg();
  const { t } = useT();
  const path = usePathname();
  const items = settingsNav(ctx);
  const org = ctx.org.slug;
  return (
    <div>
      <div className="mb-5">
        <p className="text-sm font-medium text-muted-foreground">{t('shell.settingsTitle')}</p>
      </div>
      <div className="grid gap-6 lg:grid-cols-[13rem_minmax(0,1fr)] lg:gap-10">
        <nav aria-label={t('shell.settingsNav')} className="-mx-4 overflow-x-auto px-4 lg:mx-0 lg:overflow-visible lg:px-0">
          <ul className="flex gap-1 border-b pb-2 lg:sticky lg:top-20 lg:flex-col lg:gap-0.5 lg:border-0 lg:pb-0">
            {items.map((i, n) => {
              const active = isActiveItem(org, path, i);
              return (
                <li key={i.href} className="shrink-0">
                  {i.group && <p className={cn('mb-1.5 hidden px-2.5 text-[11px] font-semibold tracking-wider text-muted-foreground/80 uppercase lg:block', n > 0 && 'mt-5')}>{i.group}</p>}
                  <Link href={`/${org}/${i.href}`} aria-current={active ? 'page' : undefined}
                    className={cn('flex h-8 items-center gap-2.5 rounded-md px-2.5 text-sm whitespace-nowrap text-muted-foreground transition-colors hover:bg-muted hover:text-foreground',
                      active && 'bg-accent font-medium text-accent-foreground hover:bg-accent hover:text-accent-foreground')}>
                    <i.icon className={cn('size-4 shrink-0', active && 'text-primary')} aria-hidden />{i.label}
                  </Link>
                </li>
              );
            })}
          </ul>
        </nav>
        <div className="min-w-0">{children}</div>
      </div>
    </div>
  );
}

/** For an app's layout: its settings page inside the settings area, its other pages as they are. */
export function InSettingsWhenThere({ children }: { children: ReactNode }) {
  return useInSettings() ? <SettingsFrame>{children}</SettingsFrame> : <>{children}</>;
}
