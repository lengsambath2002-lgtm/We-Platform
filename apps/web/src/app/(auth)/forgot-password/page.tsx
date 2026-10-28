'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { MailCheck } from 'lucide-react';
import Link from 'next/link';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import { applyServerErrors } from '@/components/forms/server-errors';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { useT } from '@/i18n/provider';
import { accountApi } from '@/lib/api/account';
import { ApiError } from '@/lib/api/client';

const makeSchema = (t: ReturnType<typeof useT>['t']) => z.object({ email: z.string().trim().email(t('auth.emailInvalid')) });
type Values = z.infer<ReturnType<typeof makeSchema>>;

/** Asks for a reset link. The API answers the same whether or not the email has an account. */
export default function ForgotPassword() {
  const { t } = useT();
  const schema = useMemo(() => makeSchema(t), [t]);
  const [sentTo, setSentTo] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { email: '' } });

  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try {
      await accountApi.forgotPassword(v.email);
      setSentTo(v.email);
    } catch (e) {
      setError(e instanceof ApiError && e.code === 'TOO_MANY_REQUESTS' ? t('settings.tooMany') : applyServerErrors(form, e));
    }
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-lg">{sentTo ? t('settings.forgotSentTitle') : t('settings.forgotTitle')}</CardTitle>
        {!sentTo && <CardDescription>{t('settings.forgotLead')}</CardDescription>}
      </CardHeader>
      <CardContent>
        {sentTo ? (
          <div role="status" className="flex gap-3 rounded-md bg-muted px-3 py-3 text-sm">
            <MailCheck className="mt-0.5 size-4 shrink-0 text-primary" aria-hidden />
            <p>{t('settings.forgotSent', { email: sentTo })}</p>
          </div>
        ) : (
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
              <Button type="submit" className="w-full" disabled={form.formState.isSubmitting}>
                {t(form.formState.isSubmitting ? 'settings.sending' : 'settings.sendLink')}
              </Button>
            </form>
          </Form>
        )}
        <p className="mt-6 text-center text-sm">
          <Link className="font-medium text-primary hover:underline" href="/sign-in">{t('settings.backToSignIn')}</Link>
        </p>
      </CardContent>
    </Card>
  );
}
