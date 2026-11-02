'use client';

import { useRouter } from 'next/navigation';
import { useEffect } from 'react';

import { useOrg } from '@/lib/org-context';

/** The org chart moved into Structure ▸ Chart ▸ People; old links land there. */
export default function OrgChartMoved() {
  const { slug } = useOrg();
  const router = useRouter();
  useEffect(() => { router.replace(`/${slug}/structure?view=chart&mode=people`); }, [router, slug]);
  return null;
}
