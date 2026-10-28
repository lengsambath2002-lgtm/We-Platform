'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { useQuery } from '@tanstack/react-query';
import { LinkIcon } from 'lucide-react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { Suspense, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { ErrorState } from '@/components/common/states';
import { applyServerErrors } from '@/components/forms/server-errors';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { useT } from '@/i18n/provider';
import { accountApi } from '@/lib/api/account';
import { ApiError } from '@/lib/api/client';
import { useSession } from '@/lib/auth/session';

const makeSchema = (t: ReturnType<typeof useT>['t']) => z.object({
  password: z.string().min(10, t('auth.passwordMin')).regex(/[A-Za-z]/, t('auth.passwordLetter')).regex(/\d/, t('auth.passwordNumber')),
  repeat: z.string(),
}).refine((v) => v.password === v.repeat, { path: ['repeat'], message: t('settings.passwordsDiffer') });
type Values = z.infer<ReturnType<typeof makeSchema>>;

function LinkInvalid() {
  const { t } = useT();
  return (
    <div className="space-y-4">
      <div role="alert" className="flex gap-3 rounded-md bg-destructive/10 px-3 py-3 text-sm text-destructive">
        <LinkIcon className="mt-0.5 size-4 shrink-0" aria-hidden /><p>{t('settings.resetInvalid')}</p>
      </div>
      <Button asChild className="w-full"><Link href="/forgot-password">{t('settings.requestNewLink')}</Link></Button>
    </div>
  );
}

/** Sets a new password from an emailed link; signs out every device and signs in here. */
function ResetPassword() {
  const { t } = useT();
  const schema = useMemo(() => makeSchema(t), [t]);
  const token = useSearchParams().get('token') ?? '';
  const router = useRouter();
  const { startSession } = useSession();
  const [error, setError] = useState<string | null>(null);
  const [expired, setExpired] = useState(false);
  const check = useQuery({ queryKey: ['reset-token', token], queryFn: () => accountApi.checkResetToken(token), enabled: !!token, retry: false });
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { password: '', repeat: '' } });

  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try {
      startSession(await accountApi.resetPassword(token, v.password));
      toast.success(t('settings.resetDone'));
      router.replace('/');
    } catch (e) {
      if (e instanceof ApiError && e.code === 'INVALID_TOKEN') setExpired(true);
      else setError(applyServerErrors(form, e));
    }
  });

  const body = !token || expired || check.data?.valid === false ? <LinkInvalid />
    : check.error ? <ErrorState error={check.error} retry={() => check.refetch()} />
    : check.isPending ? <p className="text-sm text-muted-foreground" aria-busy="true">{t('settings.checkingLink')}</p>
    : (
      <Form {...form}>
        <form onSubmit={onSubmit} className="space-y-4" noValidate>
          {error && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}
          <FormField control={form.control} name="password" render={({ field }) => (
            <FormItem>
              <FormLabel>{t('settings.newPassword')}</FormLabel>
              <FormControl><Input type="password" autoComplete="new-password" autoFocus {...field} /></FormControl>
              <FormDescription>{t('auth.passwordHint')}</FormDescription>
              <FormMessage />
            </FormItem>
          )} />
          <FormField control={form.control} name="repeat" render={({ field }) => (
            <FormItem>
              <FormLabel>{t('settings.repeatPassword')}</FormLabel>
              <FormControl><Input type="password" autoComplete="new-password" {...field} /></FormControl>
              <FormMessage />
            </FormItem>
          )} />
          <Button type="submit" className="w-full" disabled={form.formState.isSubmitting}>
            {t(form.formState.isSubmitting ? 'common.saving' : 'settings.setPassword')}
          </Button>
        </form>
      </Form>
    );

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-lg">{t('settings.resetTitle')}</CardTitle>
        <CardDescription>{t('settings.resetLead')}</CardDescription>
      </CardHeader>
      <CardContent>
        {body}
        <p className="mt-6 text-center text-sm">
          <Link className="font-medium text-primary hover:underline" href="/sign-in">{t('settings.backToSignIn')}</Link>
        </p>
      </CardContent>
    </Card>
  );
}

export default function Page() {
  return <Suspense><ResetPassword /></Suspense>;
}
