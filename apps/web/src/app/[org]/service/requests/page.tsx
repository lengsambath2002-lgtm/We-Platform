'use client';

import { useRouter } from 'next/navigation';
import { useEffect } from 'react';

import { useOrg } from '@/lib/org-context';

/** New service requests are the Tickets inbox's "New" view; old links land there. */
export default function RequestsMoved() {
  const { slug } = useOrg();
  const router = useRouter();
  useEffect(() => { router.replace(`/${slug}/service/tickets?view=new`); }, [router, slug]);
  return null;
}
