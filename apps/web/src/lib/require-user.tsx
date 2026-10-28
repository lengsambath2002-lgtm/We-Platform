'use client';

import { usePathname, useRouter } from 'next/navigation';
import { useEffect, type ReactNode } from 'react';

import { Skeleton } from '@/components/ui/skeleton';
import { useSession } from '@/lib/auth/session';

/** Shows children only to a signed-in person; others go to sign-in and come back. */
export function RequireUser({ children }: { children: ReactNode }) {
  const { user, ready } = useSession();
  const router = useRouter();
  const path = usePathname();
  useEffect(() => {
    if (ready && !user) router.replace(`/sign-in?next=${encodeURIComponent(path)}`);
  }, [ready, user, path, router]);
  if (!ready || !user) {
    return <div className="p-6"><Skeleton className="h-8 w-48" /><Skeleton className="mt-6 h-64 w-full" /></div>;
  }
  return <>{children}</>;
}
