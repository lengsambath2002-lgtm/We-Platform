'use client';

import { useRouter } from 'next/navigation';
import { useEffect } from 'react';

import { ErrorState } from '@/components/common/states';
import { useOrgs } from '@/lib/api/queries';
import { RequireUser } from '@/lib/require-user';

function Home() {
  const router = useRouter();
  const orgs = useOrgs();
  useEffect(() => {
    if (!orgs.data) return;
    let last: string | null = null;
    try {
      last = localStorage.getItem('bp-last-org');
    } catch { /* ignore */ }
    const pick = orgs.data.find((o) => o.slug === last) ?? orgs.data[0];
    router.replace(pick ? `/${pick.slug}/overview` : '/onboarding');
  }, [orgs.data, router]);
  if (orgs.error) return <div className="p-6"><ErrorState error={orgs.error} retry={() => orgs.refetch()} /></div>;
  return null;
}

export default function Page() {
  return <RequireUser><Home /></RequireUser>;
}
