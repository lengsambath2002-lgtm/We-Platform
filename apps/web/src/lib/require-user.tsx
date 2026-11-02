'use client';

import { usePathname, useRouter } from 'next/navigation';
import { useEffect, useSyncExternalStore, type ReactNode } from 'react';

import { Skeleton } from '@/components/ui/skeleton';
import { useSession } from '@/lib/auth/session';

const noop = () => () => {};

/**
 * False on the server and during hydration, true after: for placeholders that should not be part of the server HTML.
 * Browser extensions (e.g. one that adds `<div id="painty_root">` to <body>) can sit where the first server-rendered
 * element is; with nothing rendered there, React has nothing to compare them with, so hydration doesn't fail.
 */
export function useHydrated() {
  return useSyncExternalStore(noop, () => true, () => false);
}

/** Shows children only to a signed-in person; others go to sign-in and come back. */
export function RequireUser({ children }: { children: ReactNode }) {
  const { user, ready } = useSession();
  const hydrated = useHydrated();
  const router = useRouter();
  const path = usePathname();
  useEffect(() => {
    if (ready && !user) router.replace(`/sign-in?next=${encodeURIComponent(path)}`);
  }, [ready, user, path, router]);
  if (!ready || !user) {
    if (!hydrated) return null;
    return <div className="p-6"><Skeleton className="h-8 w-48" /><Skeleton className="mt-6 h-64 w-full" /></div>;
  }
  return <>{children}</>;
}
