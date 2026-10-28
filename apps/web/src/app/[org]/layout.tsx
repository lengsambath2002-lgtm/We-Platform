'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useEffect, type ReactNode } from 'react';

import { ErrorState } from '@/components/common/states';
import { AppShell } from '@/components/shell/app-shell';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { ApiError } from '@/lib/api/client';
import { useOrgContext } from '@/lib/api/queries';
import { OrgProvider } from '@/lib/org-context';
import { RequireUser } from '@/lib/require-user';

function OrgFrame({ children }: { children: ReactNode }) {
  const { org } = useParams<{ org: string }>();
  const ctx = useOrgContext(org);

  useEffect(() => {
    if (ctx.data) {
      try {
        localStorage.setItem('bp-last-org', ctx.data.org.slug);
      } catch { /* ignore */ }
    }
  }, [ctx.data]);

  if (ctx.isPending) {
    return (
      <div className="flex min-h-dvh">
        <div className="hidden w-62 border-r lg:block" />
        <div className="flex-1 p-6"><Skeleton className="h-8 w-56" /><Skeleton className="mt-6 h-72 w-full" /></div>
      </div>
    );
  }
  if (ctx.error) {
    const missing = ctx.error instanceof ApiError && ctx.error.status === 404;
    return (
      <div className="mx-auto max-w-md px-4 py-20 text-center">
        {missing ? (
          <>
            <h1 className="text-lg font-semibold">Organisation not found</h1>
            <p className="mt-2 text-sm text-muted-foreground">It doesn&apos;t exist, or you&apos;re not a member of it.</p>
            <Button asChild className="mt-6"><Link href="/">Go to my organisations</Link></Button>
          </>
        ) : (
          <ErrorState error={ctx.error} retry={() => ctx.refetch()} />
        )}
      </div>
    );
  }
  return (
    <OrgProvider value={ctx.data}>
      <AppShell ctx={ctx.data}>{children}</AppShell>
    </OrgProvider>
  );
}

export default function OrgLayout({ children }: { children: ReactNode }) {
  return <RequireUser><OrgFrame>{children}</OrgFrame></RequireUser>;
}
