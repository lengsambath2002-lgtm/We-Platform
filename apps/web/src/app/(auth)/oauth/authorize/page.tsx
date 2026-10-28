'use client';

import { useMutation, useQuery } from '@tanstack/react-query';
import { Building2, Loader2, Mail, ShieldAlert, ShieldCheck, UserRound } from 'lucide-react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { Suspense, useEffect, useMemo, useRef } from 'react';

import { PersonAvatar } from '@/components/common/person';
import { ErrorState } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { type AuthorizeParams, ssoApi } from '@/lib/api/sso';
import { useSession } from '@/lib/auth/session';

/** Leaves for the client (a full navigation: it is another site). */
const go = (url: string) => window.location.replace(url);

function Waiting({ text }: { text: string }) {
  return (
    <Card>
      <CardContent className="flex items-center gap-3 py-8 text-sm text-muted-foreground" role="status" aria-live="polite">
        <Loader2 className="size-4 animate-spin" aria-hidden />{text}
      </CardContent>
    </Card>
  );
}

/**
 * The platform's authorize page (contract §2): checks the request with the API, sends a visitor who isn't
 * signed in through sign-in and back, asks for consent the first time, and otherwise continues at once.
 * An unknown app or return address shows an error here and never redirects.
 */
function Authorize() {
  const { t } = useT();
  const router = useRouter();
  const search = useSearchParams();
  const { user, ready, signOut } = useSession();
  const query = search.toString();
  const params = useMemo<AuthorizeParams>(() => Object.fromEntries(new URLSearchParams(query)), [query]);
  const here = `/oauth/authorize?${query}`;

  const check = useQuery({
    queryKey: ['oidc', 'check', query, user?.id ?? null],
    queryFn: () => ssoApi.check(params),
    enabled: ready,
    retry: false,
    staleTime: Infinity,
    gcTime: 0,
  });
  const decide = useMutation({
    mutationFn: (approve: boolean) => ssoApi.decide(params, approve),
    onSuccess: (r) => go(r.redirectUrl),
  });
  const started = useRef(false);
  const outcome = check.data?.outcome;

  useEffect(() => {
    if (!check.data) return;
    if (outcome === 'REDIRECT' && check.data.redirectUrl) go(check.data.redirectUrl);
    if (outcome === 'SIGN_IN') router.replace(`/sign-in?next=${encodeURIComponent(here)}`);
    if (outcome === 'CONTINUE' && !started.current) {
      started.current = true;
      decide.mutate(true);
    }
  }, [check.data, outcome, here, router, decide]);

  if (!ready || check.isPending) return <Waiting text={t('sso.checking')} />;
  if (check.error) return <ErrorState error={check.error} retry={() => check.refetch()} />;

  const app = check.data.clientName ?? '';
  if (outcome === 'INVALID') {
    return (
      <Card>
        <CardHeader>
          <ShieldAlert className="mb-2 size-8 text-destructive" aria-hidden />
          <CardTitle className="text-lg">{t('sso.invalidTitle')}</CardTitle>
          <CardDescription>{t(check.data.error === 'invalid_redirect_uri' ? 'sso.invalidRedirect' : 'sso.invalidClient')}</CardDescription>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">{t('sso.invalidHint')}</p>
          <Button asChild variant="outline" className="mt-6 w-full"><Link href="/">{t('sso.goHome')}</Link></Button>
        </CardContent>
      </Card>
    );
  }
  if (outcome !== 'CONSENT' || !user) {
    if (decide.error) return <ErrorState error={decide.error} retry={() => decide.mutate(true)} />;
    return <Waiting text={t('sso.continuing', { app })} />;
  }

  const sees = [
    ...(check.data.scopes.includes('profile') ? [{ icon: UserRound, text: t('sso.seeProfile') }] : []),
    ...(check.data.scopes.includes('email') ? [{ icon: Mail, text: t('sso.seeEmail') }] : []),
    ...(check.data.scopes.includes('orgs') ? [{ icon: Building2, text: t('sso.seeOrgs') }] : []),
  ];
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-lg">{t('sso.consentTitle', { app })}</CardTitle>
        <CardDescription>{t('sso.consentLead', { app, name: user.name, email: user.email })}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-5">
        <div className="flex items-center gap-3 rounded-md border px-3 py-2">
          <PersonAvatar person={user} className="size-9" />
          <div className="min-w-0">
            <p className="truncate text-sm font-medium">{user.name}</p>
            <p className="truncate text-xs text-muted-foreground">{user.email}</p>
          </div>
        </div>
        <div>
          <p className="mb-2 text-sm font-medium">{t('sso.willSee', { app })}</p>
          <ul className="space-y-2 text-sm">
            {sees.map(({ icon: Icon, text }) => (
              <li key={text} className="flex items-center gap-2"><Icon className="size-4 shrink-0 text-muted-foreground" aria-hidden />{text}</li>
            ))}
          </ul>
          <p className="mt-3 flex items-center gap-2 text-xs text-muted-foreground"><ShieldCheck className="size-4 shrink-0" aria-hidden />{t('sso.neverPassword')}</p>
        </div>
        {decide.error && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{errorMessage(decide.error)}</p>}
        <div className="grid grid-cols-2 gap-2">
          <Button variant="outline" disabled={decide.isPending} onClick={() => decide.mutate(false)}>{t('sso.cancel')}</Button>
          <Button disabled={decide.isPending} onClick={() => decide.mutate(true)}>{t(decide.isPending ? 'sso.allowing' : 'sso.allow')}</Button>
        </div>
        <p className="text-xs text-muted-foreground">{t('sso.revokeHint')}</p>
        <button type="button" className="text-sm font-medium text-primary hover:underline"
          onClick={() => signOut().then(() => router.replace(`/sign-in?next=${encodeURIComponent(here)}`))}>
          {t('sso.notYou')}
        </button>
      </CardContent>
    </Card>
  );
}

export default function Page() {
  return <Suspense><Authorize /></Suspense>;
}
