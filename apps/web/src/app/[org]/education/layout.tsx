'use client';

import { PowerOff } from 'lucide-react';
import Link from 'next/link';
import type { ReactNode } from 'react';

import { EmptyState, PermissionDenied } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { InSettingsWhenThere } from '@/components/shell/settings-frame';
import { useT } from '@/i18n/provider';
import { useOrg } from '@/lib/org-context';

/**
 * Every Education page: switched off → says so (with the switch for admins); members who neither run the
 * school nor teach → no access; else the tabs.
 */
export default function EducationLayout({ children }: { children: ReactNode }) {
  const { slug, can, modules } = useOrg();
  const { t } = useT();
  const on = modules.some((m) => m.key === 'education' && m.enabled);
  if (!on) {
    return (
      <EmptyState icon={PowerOff} title={t('common.moduleOffTitle')} body={t('education.moduleOffBody')}
        action={can('org.modules.manage') && <Button asChild size="sm"><Link href={`/${slug}/admin/modules`}>{t('modules.manageModules')}</Link></Button>} />
    );
  }
  if (!can('education.view')) return <PermissionDenied />;
  return <InSettingsWhenThere>{children}</InSettingsWhenThere>;
}
