'use client';

import { PowerOff } from 'lucide-react';
import Link from 'next/link';
import type { ReactNode } from 'react';

import { EmptyState, PermissionDenied } from '@/components/common/states';
import { ServiceNav } from '@/components/service/ui';
import { Button } from '@/components/ui/button';
import { useT } from '@/i18n/provider';
import { useOrg } from '@/lib/org-context';

/** Every service page: switched off → says so (with the switch for admins); no service role → no access; else the tabs. */
export default function ServiceLayout({ children }: { children: ReactNode }) {
  const { slug, can, modules } = useOrg();
  const { t } = useT();
  const on = modules.some((m) => m.key === 'service' && m.enabled);
  if (!on) {
    return (
      <EmptyState icon={PowerOff} title={t('common.moduleOffTitle')} body={t('service.moduleOffBody')}
        action={can('org.modules.manage') && <Button asChild size="sm"><Link href={`/${slug}/admin/modules`}>{t('modules.manageModules')}</Link></Button>} />
    );
  }
  if (!can('service.view')) return <PermissionDenied />;
  return (
    <>
      <ServiceNav />
      {children}
    </>
  );
}
