'use client';

import { PowerOff } from 'lucide-react';
import Link from 'next/link';
import type { ReactNode } from 'react';

import { EmptyState, PermissionDenied } from '@/components/common/states';
import { HrNav } from '@/components/hr/hr-nav';
import { Button } from '@/components/ui/button';
import { useT } from '@/i18n/provider';
import { useOrg } from '@/lib/org-context';

/** Every HR page: switched off → says so (with the switch for admins); guests → no access; else the HR tabs. */
export default function HrLayout({ children }: { children: ReactNode }) {
  const { slug, can, modules } = useOrg();
  const { t } = useT();
  const on = modules.some((m) => m.key === 'hr' && m.enabled);
  if (!on) {
    return (
      <EmptyState icon={PowerOff} title={t('common.moduleOffTitle')} body={t('hr.moduleOffBody')}
        action={can('org.modules.manage') && <Button asChild size="sm"><Link href={`/${slug}/admin/modules`}>{t('modules.manageModules')}</Link></Button>} />
    );
  }
  if (!can('hr.view')) return <PermissionDenied />;
  return (
    <>
      <HrNav />
      {children}
    </>
  );
}
