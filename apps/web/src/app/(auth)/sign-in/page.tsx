'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { Suspense, useEffect, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import { applyServerErrors } from '@/components/forms/server-errors';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useT } from '@/i18n/provider';
import { ApiError } from '@/lib/api/client';
import { useSession } from '@/lib/auth/session';
import { safeNext } from '@/lib/next-path';

const makeSchema = (t: ReturnType<typeof useT>['t']) => z.object({
  email: z.string().trim().email(t('auth.emailInvalid')),
  password: z.string().min(1, t('auth.passwordRequired')),
});
type Values = z.infer<ReturnType<typeof makeSchema>>;

/** The second step: a code from the authenticator app, or a recovery code. */
function TwoStep({ challenge, onRestart }: { challenge: string; onRestart: () => void }) {
  const { t } = useT();
  const { completeTwoStep } = useSession();
  const [recovery, setRecovery] = useState(false);
  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  return (
    <form className="space-y-4" noValidate onSubmit={async (e) => {
      e.preventDefault();
      setBusy(true); setError(null);
      try {
        await completeTwoStep(challenge, code);
      } catch (x) {
        if (x instanceof ApiError && x.code === 'INVALID_TOKEN') onRestart();
        else if (x instanceof ApiError && x.code === 'ACCOUNT_LOCKED') setError(t('auth.accountLocked'));
        else setError(x instanceof ApiError && x.fields[0]?.message ? x.fields[0].message : t('common.errGeneric'));
      } finally { setBusy(false); }
    }}>
      <p className="text-sm text-muted-foreground">{t(recovery ? 'auth.twoStepRecoveryLead' : 'auth.twoStepLead')}</p>
      {error && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}
      <div className="space-y-1.5">
        <Label htmlFor="two-step-code">{t('auth.twoStepCode')}</Label>
        <Input id="two-step-code" key={String(recovery)} autoFocus autoComplete="one-time-code" inputMode={recovery ? 'text' : 'numeric'} maxLength={recovery ? 20 : 6}
          value={code} onChange={(e) => setCode(recovery ? e.target.value : e.target.value.replace(/\D/g, ''))} />
      </div>
      <Button type="submit" className="w-full" disabled={busy || (recovery ? code.trim().length < 10 : code.length !== 6)}>{t('auth.twoStepVerify')}</Button>
      <button type="button" className="w-full text-center text-sm font-medium text-primary hover:underline" onClick={() => { setRecovery(!recovery); setCode(''); setError(null); }}>
        {t(recovery ? 'auth.twoStepUseApp' : 'auth.twoStepUseRecovery')}
      </button>
    </form>
  );
}

function SignIn() {
  const { t } = useT();
  const schema = useMemo(() => makeSchema(t), [t]);
  const { signIn, user, ready } = useSession();
  const router = useRouter();
  const next = safeNext(useSearchParams().get('next'));
  const [error, setError] = useState<string | null>(null);
  const [challenge, setChallenge] = useState<string | null>(null);
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { email: '', password: '' } });

  useEffect(() => {
    if (ready && user) router.replace(next ?? '/');
  }, [ready, user, next, router]);

  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try {
      setChallenge(await signIn(v.email, v.password));
    } catch (e) {
      if (e instanceof ApiError && e.code === 'INVALID_CREDENTIALS') setError(t('auth.wrongCredentials'));
      else if (e instanceof ApiError && e.code === 'ACCOUNT_LOCKED') setError(t('auth.accountLocked'));
      else if (e instanceof ApiError && e.code === 'ACCOUNT_DISABLED') setError(t('common.errAccountDisabled'));
      else setError(applyServerErrors(form, e));
    }
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-lg">{t(challenge ? 'auth.twoStepTitle' : 'auth.signIn')}</CardTitle>
        {!challenge && <CardDescription>{t('auth.welcomeBack')}</CardDescription>}
      </CardHeader>
      <CardContent>
        {challenge ? <TwoStep challenge={challenge} onRestart={() => { setChallenge(null); setError(t('auth.twoStepExpired')); }} /> : <>
        <Form {...form}>
          <form onSubmit={onSubmit} className="space-y-4" noValidate>
            {error && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}
            <FormField control={form.control} name="email" render={({ field }) => (
              <FormItem>
                <FormLabel>{t('auth.email')}</FormLabel>
                <FormControl><Input type="email" autoComplete="email" autoFocus {...field} /></FormControl>
                <FormMessage />
              </FormItem>
            )} />
            <FormField control={form.control} name="password" render={({ field }) => (
              <FormItem>
                <div className="flex items-center justify-between gap-2">
                  <FormLabel>{t('auth.password')}</FormLabel>
                  <Link href="/forgot-password" className="text-xs font-medium text-primary hover:underline">{t('settings.forgotLink')}</Link>
                </div>
                <FormControl><Input type="password" autoComplete="current-password" {...field} /></FormControl>
                <FormMessage />
              </FormItem>
            )} />
            <Button type="submit" className="w-full" disabled={form.formState.isSubmitting}>
              {t(form.formState.isSubmitting ? 'auth.signingIn' : 'auth.signIn')}
            </Button>
          </form>
        </Form>
        <p className="mt-6 text-center text-sm text-muted-foreground">
          {t('auth.newHere')} <Link className="font-medium text-primary hover:underline" href={`/sign-up${next ? `?next=${encodeURIComponent(next)}` : ''}`}>{t('auth.createAnAccount')}</Link>
        </p>
        </>}
      </CardContent>
    </Card>
  );
}

export default function Page() {
  return <Suspense><SignIn /></Suspense>;
}
