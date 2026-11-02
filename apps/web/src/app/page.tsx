'use client';

import { useRouter } from 'next/navigation';
import { useEffect } from 'react';

import { ErrorState } from '@/components/common/states';
import { Landing } from '@/components/landing/landing';
import { useOrgs } from '@/lib/api/queries';
import { useSession } from '@/lib/auth/session';
import { useHydrated } from '@/lib/require-user';

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

/** Signed in: on to the last organisation (or onboarding). Everyone else: the landing page. */
export default function Page() {
  const { user, ready } = useSession();
  const hydrated = useHydrated();
  if (!ready) return hydrated ? <div className="min-h-dvh bg-background" /> : null;
  return user ? <Home /> : <Landing />;
}
