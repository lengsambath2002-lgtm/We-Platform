'use client';

import { useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useState } from 'react';

import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { ApiError, errorMessage, http } from '@/lib/api/client';
import type { OrgSummary } from '@/lib/api/types';
import { useSession } from '@/lib/auth/session';

interface Preview { orgName: string; email: string; role: string; expired: boolean }

export default function InvitePage() {
  const { token } = useParams<{ token: string }>();
  const { user, ready } = useSession();
  const router = useRouter();
  const qc = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const preview = useQuery({ queryKey: ['invite', token], queryFn: () => http.get<Preview>(`/invitations/${token}`), retry: false });
  const here = `/invite/${token}`;

  const accept = async () => {
    setBusy(true);
    setError(null);
    try {
      const org = await http.post<OrgSummary>(`/invitations/${token}/accept`);
      await qc.invalidateQueries({ queryKey: ['orgs'] });
      router.replace(`/${org.slug}/overview`);
    } catch (e) {
      setError(e instanceof ApiError && e.code === 'FORBIDDEN' ? `This invitation is for ${preview.data?.email}. Sign in with that email.` : errorMessage(e));
      setBusy(false);
    }
  };

  return (
    <div className="grid min-h-dvh place-items-center px-4">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle className="text-lg">{preview.data ? `Join ${preview.data.orgName}` : 'Invitation'}</CardTitle>
          {preview.data && <CardDescription>You were invited as {preview.data.role.toLowerCase()} ({preview.data.email}).</CardDescription>}
        </CardHeader>
        <CardContent className="space-y-4">
          {preview.isPending && <p className="text-sm text-muted-foreground">Checking the invitation…</p>}
          {preview.error && <p role="alert" className="text-sm text-destructive">This invitation is no longer valid. Ask for a new one.</p>}
          {preview.data?.expired && <p role="alert" className="text-sm text-destructive">This invitation has expired. Ask for a new one.</p>}
          {error && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}
          {preview.data && !preview.data.expired && ready && (user ? (
            <Button className="w-full" onClick={accept} disabled={busy}>{busy ? 'Joining…' : `Join as ${user.name}`}</Button>
          ) : (
            <div className="grid gap-2">
              <Button asChild><Link href={`/sign-up?next=${encodeURIComponent(here)}`}>Create an account</Link></Button>
              <Button asChild variant="outline"><Link href={`/sign-in?next=${encodeURIComponent(here)}`}>I already have an account</Link></Button>
            </div>
          ))}
        </CardContent>
      </Card>
    </div>
  );
}
