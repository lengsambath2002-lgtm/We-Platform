'use client';

import type { ReactNode } from 'react';

import { RetailNav } from '@/components/retail/retail-nav';
import { useOrg } from '@/lib/org-context';

/** Retail's tabs, once the module is on and the person may see it (the shops page explains the other cases). */
export default function RetailLayout({ children }: { children: ReactNode }) {
  const { can, modules } = useOrg();
  const on = modules.some((m) => m.key === 'retail' && m.available && m.enabled);
  return (
    <>
      {on && can('retail.view') && <RetailNav />}
      {children}
    </>
  );
}
