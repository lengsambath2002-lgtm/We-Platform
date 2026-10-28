'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { applyServerErrors } from '@/components/forms/server-errors';
import { Button } from '@/components/ui/button';
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { useT } from '@/i18n/provider';
import { accountApi } from '@/lib/api/account';
import { ApiError } from '@/lib/api/client';

const makeSchema = (t: ReturnType<typeof useT>['t']) => z.object({
  currentPassword: z.string().min(1, t('auth.passwordRequired')),
  newPassword: z.string().min(10, t('auth.passwordMin')).regex(/[A-Za-z]/, t('auth.passwordLetter')).regex(/\d/, t('auth.passwordNumber')),
  repeat: z.string(),
}).refine((v) => v.newPassword === v.repeat, { path: ['repeat'], message: t('settings.passwordsDiffer') });
type Values = z.infer<ReturnType<typeof makeSchema>>;

/** Change password (current + new, same rules as sign-up); the API signs out the other devices. */
export function PasswordSection() {
  const { t } = useT();
  const schema = useMemo(() => makeSchema(t), [t]);
  const qc = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { currentPassword: '', newPassword: '', repeat: '' } });

  return (
    <section className="mt-4 rounded-lg border bg-card p-4 sm:p-6" aria-labelledby="password-title">
      <h2 id="password-title" className="mb-1 text-base font-semibold">{t('settings.passwordTitle')}</h2>
      <p className="mb-4 text-sm text-muted-foreground">{t('settings.passwordLead')}</p>
      <Form {...form}>
        <form noValidate className="space-y-4 sm:max-w-sm" onSubmit={form.handleSubmit(async (v) => {
          setError(null);
          try {
            await accountApi.changePassword(v.currentPassword, v.newPassword);
            form.reset();
            toast.success(t('settings.passwordChanged'));
            await qc.invalidateQueries({ queryKey: ['me', 'sessions'] });
          } catch (e) {
            setError(e instanceof ApiError && e.code === 'ACCOUNT_LOCKED' ? t('auth.accountLocked') : applyServerErrors(form, e));
          }
        })}>
          {error && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}
          <FormField control={form.control} name="currentPassword" render={({ field }) => (
            <FormItem>
              <div className="flex items-center justify-between gap-2">
                <FormLabel>{t('settings.currentPassword')}</FormLabel>
                <Link href="/forgot-password" className="text-xs font-medium text-primary hover:underline">{t('settings.forgotLink')}</Link>
              </div>
              <FormControl><Input type="password" autoComplete="current-password" {...field} /></FormControl>
              <FormMessage />
            </FormItem>
          )} />
          <FormField control={form.control} name="newPassword" render={({ field }) => (
            <FormItem>
              <FormLabel>{t('settings.newPassword')}</FormLabel>
              <FormControl><Input type="password" autoComplete="new-password" {...field} /></FormControl>
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
          <Button type="submit" disabled={form.formState.isSubmitting}>{t(form.formState.isSubmitting ? 'common.saving' : 'settings.changePassword')}</Button>
        </form>
      </Form>
    </section>
  );
}
