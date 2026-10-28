'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { ArrowLeft, Monitor, Moon, Sun } from 'lucide-react';
import Link from 'next/link';
import { useTheme } from 'next-themes';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { ConnectedAppsSection } from '@/components/account/connected-apps-section';
import { DevicesSection } from '@/components/account/devices-section';
import { OrgsSection } from '@/components/account/orgs-section';
import { PasswordSection } from '@/components/account/password-section';
import { PersonAvatar } from '@/components/common/person';
import { applyServerErrors } from '@/components/forms/server-errors';
import { LanguageSwitch } from '@/components/shell/language-switch';
import { Button } from '@/components/ui/button';
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { useT } from '@/i18n/provider';
import { http } from '@/lib/api/client';
import type { User } from '@/lib/api/types';
import { useSession } from '@/lib/auth/session';
import { RequireUser } from '@/lib/require-user';
import { cn } from '@/lib/utils';

const makeSchema = (t: ReturnType<typeof useT>['t']) => z.object({ name: z.string().trim().min(2, t('account.nameMin')).max(80) });
type Values = z.infer<ReturnType<typeof makeSchema>>;

function Account() {
  const { t } = useT();
  const schema = useMemo(() => makeSchema(t), [t]);
  const { user, setUser, signOut } = useSession();
  const { theme, setTheme } = useTheme();
  const [error, setError] = useState<string | null>(null);
  const form = useForm<Values>({ resolver: zodResolver(schema), values: { name: user!.name } });
  let back = '/';
  try { const o = localStorage.getItem('bp-last-org'); if (o) back = `/${o}/overview`; } catch { /* ignore */ }

  return (
    <main className="mx-auto max-w-[720px] px-4 py-8 sm:px-6">
      <Link href={back} className="mb-6 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="size-4" />{t('common.back')}</Link>
      <div className="mb-6 flex items-center gap-4">
        <PersonAvatar person={user!} className="size-14 text-base" />
        <div><h1 className="text-xl font-semibold tracking-tight">{user!.name}</h1><p className="text-sm text-muted-foreground">{user!.email}</p></div>
      </div>

      <section className="rounded-lg border bg-card p-4 sm:p-6">
        <h2 className="mb-4 text-base font-semibold">{t('account.profile')}</h2>
        <Form {...form}>
          <form noValidate className="space-y-5" onSubmit={form.handleSubmit(async (v) => {
            setError(null);
            try { setUser(await http.patch<User>('/me', v)); toast.success(t('common.saved')); } catch (e) { setError(applyServerErrors(form, e)); }
          })}>
            {error && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}
            <FormField control={form.control} name="name" render={({ field }) => (
              <FormItem><FormLabel>{t('account.name')}</FormLabel><FormControl><Input autoComplete="name" {...field} /></FormControl><FormMessage /></FormItem>
            )} />
            <FormItem><FormLabel>{t('account.email')}</FormLabel><Input value={user!.email} readOnly disabled /><FormDescription>{t('account.emailHint')}</FormDescription></FormItem>
            <Button type="submit" disabled={!form.formState.isDirty || form.formState.isSubmitting}>{t(form.formState.isSubmitting ? 'common.saving' : 'common.save')}</Button>
          </form>
        </Form>
      </section>

      <PasswordSection />
      <DevicesSection />
      <ConnectedAppsSection />
      <OrgsSection />

      <section className="mt-4 rounded-lg border bg-card p-4 sm:p-6">
        <h2 className="mb-1 text-base font-semibold">{t('common.language')}</h2>
        <p className="mb-3 text-sm text-muted-foreground">{t('account.languageHint')}</p>
        <LanguageSwitch />
      </section>

      <section className="mt-4 rounded-lg border bg-card p-4 sm:p-6">
        <h2 className="mb-3 text-base font-semibold">{t('account.appearance')}</h2>
        <div role="radiogroup" aria-label={t('shell.theme')} className="grid grid-cols-3 gap-2 sm:w-96">
          {([['light', 'shell.themeLight', Sun], ['dark', 'shell.themeDark', Moon], ['system', 'shell.themeDevice', Monitor]] as const).map(([v, label, Icon]) => (
            <button key={v} type="button" role="radio" aria-checked={theme === v} onClick={() => setTheme(v)}
              className={cn('flex flex-col items-center gap-1 rounded-md border px-3 py-3 text-sm hover:bg-muted', theme === v && 'border-primary bg-primary/5 text-primary')}>
              <Icon className="size-4" />{t(label)}
            </button>
          ))}
        </div>
      </section>

      <section className="mt-4 flex flex-wrap items-center justify-between gap-3 rounded-lg border bg-card p-4 sm:p-6">
        <div><h2 className="text-base font-semibold">{t('account.signOut')}</h2><p className="text-sm text-muted-foreground">{t('account.signOutHint')}</p></div>
        <Button variant="outline" onClick={() => signOut().then(() => { window.location.href = '/sign-in'; })}>{t('account.signOut')}</Button>
      </section>
    </main>
  );
}

export default function Page() {
  return <RequireUser><Account /></RequireUser>;
}
