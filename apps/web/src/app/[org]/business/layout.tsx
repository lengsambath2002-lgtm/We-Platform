'use client';

import { PowerOff } from 'lucide-react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import type { ReactNode } from 'react';

import { EmptyState, PermissionDenied } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { InSettingsWhenThere } from '@/components/shell/settings-frame';
import { useT } from '@/i18n/provider';
import { useOrg } from '@/lib/org-context';

/** Every business page (and purchasing, for Retail too): switched off → says so (with the switch for admins); guests → no access; else the tabs. */
export default function BusinessLayout({ children }: { children: ReactNode }) {
  const { slug, can, modules } = useOrg();
  const { t } = useT();
  const path = usePathname();
  const enabled = (key: string) => modules.some((m) => m.key === key && m.enabled);
  // Purchasing belongs here, and organisations on Retail & Commerce (without General Business) use it too.
  const buying = /\/business\/(purchasing|purchase-orders|suppliers)(\/|$)/.test(path) && enabled('retail');
  const on = enabled('business') || buying;
  if (!on) {
    return (
      <EmptyState icon={PowerOff} title={t('common.moduleOffTitle')} body={t('business.moduleOffBody')}
        action={can('org.modules.manage') && <Button asChild size="sm"><Link href={`/${slug}/admin/modules`}>{t('modules.manageModules')}</Link></Button>} />
    );
  }
  if (!can('business.view') && !(buying && can('retail.view'))) return <PermissionDenied />;
  return <InSettingsWhenThere>{children}</InSettingsWhenThere>;
}
