'use client';

import { createContext, useContext } from 'react';

import type { OrgContext } from '@/lib/api/types';

const Ctx = createContext<OrgContext | null>(null);
export const OrgProvider = Ctx.Provider;

/** The current organisation, my role, permissions and modules (from /api/orgs/{org}/context). */
export function useOrg() {
  const c = useContext(Ctx);
  if (!c) throw new Error('useOrg outside an organisation');
  return { ...c, slug: c.org.slug, can: (p: string) => c.permissions.includes(p) };
}
