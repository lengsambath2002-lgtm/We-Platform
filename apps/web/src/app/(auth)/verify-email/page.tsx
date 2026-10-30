'use client';

import { useQuery } from '@tanstack/react-query';
import { CircleCheck, LinkIcon } from 'lucide-react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Suspense, useEffect } from 'react';

import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { useT } from '@/i18n/provider';
import { accountApi } from '@/lib/api/account';
import { http } from '@/lib/api/client';
import type { User } from '@/lib/api/types';
import { useSession } from '@/lib/auth/session';

/** Opens the emailed "confirm your email" link. Works signed in or not: the link is the proof. */
function VerifyEmail() {
  const { t } = useT();
  const token = useSearchParams().get('token') ?? '';
  const { user, setUser } = useSession();
  // Once per token: the link works once, so a remount must not send it again.
  const verify = useQuery({ queryKey: ['verify-email', token], queryFn: () => accountApi.verifyEmail(token).then(() => true), enabled: !!token, retry: false, staleTime: Infinity, gcTime: Infinity });
  const signedIn = !!user;
  useEffect(() => {
    if (verify.data && signedIn) http.get<User>('/me').then(setUser).catch(() => {});
  }, [verify.data, signedIn, setUser]);

  const body = !token || verify.error ? (
    <div className="space-y-4">
      <div role="alert" className="flex gap-3 rounded-md bg-destructive/10 px-3 py-3 text-sm text-destructive">
        <LinkIcon className="mt-0.5 size-4 shrink-0" aria-hidden /><p>{t('auth.verifyInvalid')}</p>
      </div>
      <Button asChild className="w-full"><Link href={signedIn ? '/' : '/sign-in'}>{t(signedIn ? 'auth.verifyContinue' : 'auth.signIn')}</Link></Button>
    </div>
  ) : verify.isPending ? <p className="text-sm text-muted-foreground" aria-busy="true">{t('auth.verifying')}</p> : (
    <div className="space-y-4">
      <p className="flex gap-3 rounded-md bg-emerald-50 px-3 py-3 text-sm text-emerald-900 dark:bg-emerald-950 dark:text-emerald-100">
        <CircleCheck className="mt-0.5 size-4 shrink-0" aria-hidden />{t('auth.verifyDone')}
      </p>
      <Button asChild className="w-full"><Link href={signedIn ? '/' : '/sign-in'}>{t(signedIn ? 'auth.verifyContinue' : 'auth.signIn')}</Link></Button>
    </div>
  );

  return (
    <Card>
      <CardHeader><CardTitle className="text-lg">{t('auth.verifyTitle')}</CardTitle></CardHeader>
      <CardContent>{body}</CardContent>
    </Card>
  );
}

export default function Page() {
  return <Suspense><VerifyEmail /></Suspense>;
}
