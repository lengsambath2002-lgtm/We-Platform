'use client';

import { useRouter } from 'next/navigation';
import { useEffect } from 'react';

import { useOrg } from '@/lib/org-context';

/** The employee directory is the organisation's People page; old links land there. Profiles stay under HR. */
export default function EmployeesMoved() {
  const { slug } = useOrg();
  const router = useRouter();
  useEffect(() => { router.replace(`/${slug}/teams`); }, [router, slug]);
  return null;
}
