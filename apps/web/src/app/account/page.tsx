'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { ArrowLeft, Monitor, Moon, Sun } from 'lucide-react';
import Link from 'next/link';
import { useTheme } from 'next-themes';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { PersonAvatar } from '@/components/common/person';
import { applyServerErrors } from '@/components/forms/server-errors';
import { Button } from '@/components/ui/button';
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { http } from '@/lib/api/client';
import type { User } from '@/lib/api/types';
import { useSession } from '@/lib/auth/session';
import { RequireUser } from '@/lib/require-user';
import { cn } from '@/lib/utils';

const schema = z.object({ name: z.string().trim().min(2, 'At least 2 characters').max(80), locale: z.enum(['en', 'km']) });
type Values = z.infer<typeof schema>;

function Account() {
  const { user, setUser, signOut } = useSession();
  const { theme, setTheme } = useTheme();
  const [error, setError] = useState<string | null>(null);
  const form = useForm<Values>({ resolver: zodResolver(schema), values: { name: user!.name, locale: user!.locale === 'km' ? 'km' : 'en' } });
  let back = '/';
  try { const o = localStorage.getItem('bp-last-org'); if (o) back = `/${o}/overview`; } catch { /* ignore */ }

  return (
    <main className="mx-auto max-w-[720px] px-4 py-8 sm:px-6">
      <Link href={back} className="mb-6 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="size-4" />Back</Link>
      <div className="mb-6 flex items-center gap-4">
        <PersonAvatar person={user!} className="size-14 text-base" />
        <div><h1 className="text-xl font-semibold tracking-tight">{user!.name}</h1><p className="text-sm text-muted-foreground">{user!.email}</p></div>
      </div>

      <section className="rounded-lg border bg-card p-4 sm:p-6">
        <h2 className="mb-4 text-base font-semibold">Profile</h2>
        <Form {...form}>
          <form noValidate className="space-y-5" onSubmit={form.handleSubmit(async (v) => {
            setError(null);
            try { setUser(await http.patch<User>('/me', v)); toast.success('Saved'); } catch (e) { setError(applyServerErrors(form, e)); }
          })}>
            {error && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}
            <FormField control={form.control} name="name" render={({ field }) => (
              <FormItem><FormLabel>Name</FormLabel><FormControl><Input autoComplete="name" {...field} /></FormControl><FormMessage /></FormItem>
            )} />
            <FormItem><FormLabel>Email</FormLabel><Input value={user!.email} readOnly disabled /><FormDescription>Your sign-in address.</FormDescription></FormItem>
            <FormField control={form.control} name="locale" render={({ field }) => (
              <FormItem>
                <FormLabel>Language</FormLabel>
                <Select value={field.value} onValueChange={field.onChange}>
                  <FormControl><SelectTrigger className="w-full sm:w-60"><SelectValue /></SelectTrigger></FormControl>
                  <SelectContent><SelectItem value="en">English</SelectItem><SelectItem value="km">ខ្មែរ (Khmer)</SelectItem></SelectContent>
                </Select>
                <FormDescription>Used for emails and notifications. The Khmer screens are being translated.</FormDescription>
              </FormItem>
            )} />
            <Button type="submit" disabled={!form.formState.isDirty || form.formState.isSubmitting}>{form.formState.isSubmitting ? 'Saving…' : 'Save'}</Button>
          </form>
        </Form>
      </section>

      <section className="mt-4 rounded-lg border bg-card p-4 sm:p-6">
        <h2 className="mb-3 text-base font-semibold">Appearance</h2>
        <div role="radiogroup" aria-label="Theme" className="grid grid-cols-3 gap-2 sm:w-96">
          {([['light', 'Light', Sun], ['dark', 'Dark', Moon], ['system', 'Device', Monitor]] as const).map(([v, label, Icon]) => (
            <button key={v} type="button" role="radio" aria-checked={theme === v} onClick={() => setTheme(v)}
              className={cn('flex flex-col items-center gap-1 rounded-md border px-3 py-3 text-sm hover:bg-muted', theme === v && 'border-primary bg-primary/5 text-primary')}>
              <Icon className="size-4" />{label}
            </button>
          ))}
        </div>
      </section>

      <section className="mt-4 flex flex-wrap items-center justify-between gap-3 rounded-lg border bg-card p-4 sm:p-6">
        <div><h2 className="text-base font-semibold">Sign out</h2><p className="text-sm text-muted-foreground">Signs out on this device.</p></div>
        <Button variant="outline" onClick={() => signOut().then(() => { window.location.href = '/sign-in'; })}>Sign out</Button>
      </section>
    </main>
  );
}

export default function Page() {
  return <RequireUser><Account /></RequireUser>;
}
