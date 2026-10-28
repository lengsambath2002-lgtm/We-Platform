'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { Suspense, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import { applyServerErrors } from '@/components/forms/server-errors';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { useT } from '@/i18n/provider';
import { useSession } from '@/lib/auth/session';
import { safeNext } from '@/lib/next-path';

const makeSchema = (t: ReturnType<typeof useT>['t']) => z.object({
  name: z.string().trim().min(2, t('auth.nameRequired')).max(80),
  email: z.string().trim().email(t('auth.emailInvalid')),
  password: z.string().min(10, t('auth.passwordMin'))
    .regex(/[A-Za-z]/, t('auth.passwordLetter')).regex(/\d/, t('auth.passwordNumber')),
});
type Values = z.infer<ReturnType<typeof makeSchema>>;

function SignUp() {
  const { t } = useT();
  const schema = useMemo(() => makeSchema(t), [t]);
  const { signUp } = useSession();
  const router = useRouter();
  const next = safeNext(useSearchParams().get('next'));
  const [error, setError] = useState<string | null>(null);
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { name: '', email: '', password: '' } });

  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try {
      // The API stores the request's language (Accept-Language) on the new account.
      await signUp(v.name, v.email, v.password);
      router.replace(next ?? '/onboarding');
    } catch (e) {
      setError(applyServerErrors(form, e));
    }
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-lg">{t('auth.createYourAccount')}</CardTitle>
        <CardDescription>{t('auth.signUpLead')}</CardDescription>
      </CardHeader>
      <CardContent>
        <Form {...form}>
          <form onSubmit={onSubmit} className="space-y-4" noValidate>
            {error && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}
            <FormField control={form.control} name="name" render={({ field }) => (
              <FormItem><FormLabel>{t('auth.fullName')}</FormLabel><FormControl><Input autoComplete="name" autoFocus {...field} /></FormControl><FormMessage /></FormItem>
            )} />
            <FormField control={form.control} name="email" render={({ field }) => (
              <FormItem><FormLabel>{t('auth.workEmail')}</FormLabel><FormControl><Input type="email" autoComplete="email" {...field} /></FormControl><FormMessage /></FormItem>
            )} />
            <FormField control={form.control} name="password" render={({ field }) => (
              <FormItem>
                <FormLabel>{t('auth.password')}</FormLabel>
                <FormControl><Input type="password" autoComplete="new-password" {...field} /></FormControl>
                <FormDescription>{t('auth.passwordHint')}</FormDescription>
                <FormMessage />
              </FormItem>
            )} />
            <Button type="submit" className="w-full" disabled={form.formState.isSubmitting}>
              {form.formState.isSubmitting ? t('common.creating') : t('auth.createAccount')}
            </Button>
          </form>
        </Form>
        <p className="mt-6 text-center text-sm text-muted-foreground">
          {t('auth.haveAccount')} <Link className="font-medium text-primary hover:underline" href={`/sign-in${next ? `?next=${encodeURIComponent(next)}` : ''}`}>{t('auth.signIn')}</Link>
        </p>
      </CardContent>
    </Card>
  );
}

export default function Page() {
  return <Suspense><SignUp /></Suspense>;
}
