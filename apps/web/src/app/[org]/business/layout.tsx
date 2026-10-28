'use client';

import { PowerOff } from 'lucide-react';
import Link from 'next/link';
import type { ReactNode } from 'react';

import { BizNav } from '@/components/business/ui';
import { EmptyState, PermissionDenied } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { useT } from '@/i18n/provider';
import { useOrg } from '@/lib/org-context';

/** Every business page: switched off → says so (with the switch for admins); guests → no access; else the tabs. */
export default function BusinessLayout({ children }: { children: ReactNode }) {
  const { slug, can, modules } = useOrg();
  const { t } = useT();
  const on = modules.some((m) => m.key === 'business' && m.enabled);
  if (!on) {
    return (
      <EmptyState icon={PowerOff} title={t('common.moduleOffTitle')} body={t('business.moduleOffBody')}
        action={can('org.modules.manage') && <Button asChild size="sm"><Link href={`/${slug}/admin/modules`}>{t('modules.manageModules')}</Link></Button>} />
    );
  }
  if (!can('business.view')) return <PermissionDenied />;
  return (
    <>
      <BizNav />
      {children}
    </>
  );
}
