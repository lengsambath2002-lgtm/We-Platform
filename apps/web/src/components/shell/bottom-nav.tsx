'use client';

import { BadgeCheck, CalendarDays, CheckSquare, LayoutDashboard } from 'lucide-react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';

import { useT } from '@/i18n/provider';
import { useApprovals } from '@/lib/api/queries';
import type { OrgContext } from '@/lib/api/types';
import { cn } from '@/lib/utils';

/** Phones: the four places people go most, one thumb away. The rest is in the menu. */
export function BottomNav({ ctx }: { ctx: OrgContext }) {
  const { t } = useT();
  const org = ctx.org.slug;
  const path = usePathname();
  const inbox = useApprovals(org, 'inbox');
  const items = [
    { href: 'overview', label: t('shell.overview'), icon: LayoutDashboard },
    ...(ctx.permissions.includes('projects.view') ? [{ href: 'my-tasks', label: t('shell.myTasks'), icon: CheckSquare }] : []),
    { href: 'approvals', label: t('shell.approvals'), icon: BadgeCheck, badge: inbox.data?.length ?? 0 },
    { href: 'calendar', label: t('shell.calendar'), icon: CalendarDays },
  ];
  return (
    <nav aria-label={t('shell.quickNav')} className="fixed inset-x-0 bottom-0 z-30 grid border-t bg-background pb-[env(safe-area-inset-bottom)] lg:hidden"
      style={{ gridTemplateColumns: `repeat(${items.length}, minmax(0, 1fr))` }}>
      {items.map((i) => {
        const href = `/${org}/${i.href}`;
        const active = path === href || path.startsWith(`${href}/`);
        return (
          <Link key={i.href} href={href} aria-current={active ? 'page' : undefined}
            className={cn('relative flex h-14 flex-col items-center justify-center gap-0.5 text-[11px] font-medium text-muted-foreground', active && 'text-primary')}>
            <i.icon className="size-5" aria-hidden />
            {i.label}
            {'badge' in i && (i.badge ?? 0) > 0 && (
              <span className="absolute right-[calc(50%-18px)] top-1.5 grid min-w-4 place-items-center rounded-full bg-destructive px-1 text-[10px] leading-4 text-white">{i.badge}</span>
            )}
          </Link>
        );
      })}
    </nav>
  );
}
