'use client';

import { useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useState } from 'react';

import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { tr } from '@/i18n/core';
import type { MessageKey } from '@/i18n/messages';
import { useT } from '@/i18n/provider';
import { ApiError, errorMessage, http } from '@/lib/api/client';
import type { OrgSummary } from '@/lib/api/types';
import { useSession } from '@/lib/auth/session';

interface Preview { orgName: string; email: string; role: string; expired: boolean }

const ROLES = ['OWNER', 'ADMIN', 'MEMBER', 'GUEST'];
const roleName = (role: string) => (ROLES.includes(role) ? tr(`auth.role${role}` as MessageKey) : role.toLowerCase());

export default function InvitePage() {
  const { t } = useT();
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
      setError(e instanceof ApiError && e.code === 'FORBIDDEN' ? t('auth.inviteWrongEmail', { email: preview.data?.email ?? '' }) : errorMessage(e));
      setBusy(false);
    }
  };

  return (
    <div className="grid min-h-dvh place-items-center px-4">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle className="text-lg">{preview.data ? t('auth.joinOrg', { org: preview.data.orgName }) : t('auth.invitation')}</CardTitle>
          {preview.data && <CardDescription>{t('auth.invitedAs', { role: roleName(preview.data.role), email: preview.data.email })}</CardDescription>}
        </CardHeader>
        <CardContent className="space-y-4">
          {preview.isPending && <p className="text-sm text-muted-foreground">{t('auth.checkingInvite')}</p>}
          {preview.error && <p role="alert" className="text-sm text-destructive">{t('auth.inviteInvalid')}</p>}
          {preview.data?.expired && <p role="alert" className="text-sm text-destructive">{t('auth.inviteExpired')}</p>}
          {error && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}
          {preview.data && !preview.data.expired && ready && (user ? (
            <Button className="w-full" onClick={accept} disabled={busy}>{busy ? t('auth.joining') : t('auth.joinAs', { name: user.name })}</Button>
          ) : (
            <div className="grid gap-2">
              <Button asChild><Link href={`/sign-up?next=${encodeURIComponent(here)}`}>{t('auth.createAnAccount')}</Link></Button>
              <Button asChild variant="outline"><Link href={`/sign-in?next=${encodeURIComponent(here)}`}>{t('auth.haveAccountAlready')}</Link></Button>
            </div>
          ))}
        </CardContent>
      </Card>
    </div>
  );
}
