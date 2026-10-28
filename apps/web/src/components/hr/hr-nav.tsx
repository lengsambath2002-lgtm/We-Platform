'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';

import { hrNav } from '@/components/shell/nav';
import { useT } from '@/i18n/provider';
import { useOrg } from '@/lib/org-context';
import { cn } from '@/lib/utils';

/** The tabs on top of every HR page (on phones, the quickest way between HR's pages). */
export function HrNav() {
  const ctx = useOrg();
  const { t } = useT();
  const path = usePathname();
  return (
    <nav aria-label={t('hr.navLabel')} className="-mx-1 mb-5 overflow-x-auto border-b">
      <ul className="flex min-w-max gap-1 px-1">
        {hrNav(ctx).map((p) => {
          const href = `/${ctx.slug}/${p.href}`;
          const active = p.exact ? path === href : path === href || path.startsWith(`${href}/`);
          return (
            <li key={p.href}>
              <Link href={href} aria-current={active ? 'page' : undefined}
                className={cn('-mb-px inline-flex h-10 items-center border-b-2 border-transparent px-3 text-sm font-medium text-muted-foreground hover:text-foreground',
                  active && 'border-primary text-foreground')}>
                {p.label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
