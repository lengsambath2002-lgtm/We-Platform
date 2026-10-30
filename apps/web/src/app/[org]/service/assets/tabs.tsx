'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';

import { useT } from '@/i18n/provider';
import { useOrg } from '@/lib/org-context';
import { cn } from '@/lib/utils';

/** Assets and the customers (with their sites) they belong to. */
export function AssetTabs() {
  const { slug } = useOrg();
  const { t } = useT();
  const path = usePathname();
  const tabs = [{ href: `/${slug}/service/assets`, label: t('service.navAssets') }, { href: `/${slug}/service/customers`, label: t('service.customersAndSites') }];
  return (
    <div role="tablist" aria-label={t('service.navAssets')} className="mb-4 inline-flex rounded-md border bg-card p-0.5">
      {tabs.map((x) => (
        <Link key={x.href} href={x.href} role="tab" aria-selected={path === x.href}
          className={cn('rounded px-3 py-1.5 text-sm text-muted-foreground', path === x.href && 'bg-muted font-medium text-foreground')}>{x.label}</Link>
      ))}
    </div>
  );
}
