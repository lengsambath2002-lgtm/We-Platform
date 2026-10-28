'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import { applyServerErrors } from '@/components/forms/server-errors';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { http } from '@/lib/api/client';
import type { OrgSummary } from '@/lib/api/types';
import { RequireUser } from '@/lib/require-user';

const schema = z.object({
  name: z.string().trim().min(2, 'Enter a name').max(120),
  slug: z.string().regex(/^[a-z0-9]([a-z0-9-]{1,38}[a-z0-9])$/, '3–40 lowercase letters, numbers or dashes'),
});

const slugify = (s: string) => s.toLowerCase().normalize('NFKD').replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 40);

function CreateOrg() {
  const router = useRouter();
  const qc = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const [slugTouched, setSlugTouched] = useState(false);
  const form = useForm<z.infer<typeof schema>>({ resolver: zodResolver(schema), defaultValues: { name: '', slug: '' } });

  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try {
      const org = await http.post<OrgSummary>('/orgs', v);
      await qc.invalidateQueries({ queryKey: ['orgs'] });
      router.replace(`/${org.slug}/overview`);
    } catch (e) {
      setError(applyServerErrors(form, e));
    }
  });

  return (
    <div className="grid min-h-dvh place-items-center px-4 py-10">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle className="text-lg">Create an organisation</CardTitle>
          <CardDescription>Your company, school or shop. You can invite your team next.</CardDescription>
        </CardHeader>
        <CardContent>
          <Form {...form}>
            <form onSubmit={onSubmit} className="space-y-4" noValidate>
              {error && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}
              <FormField control={form.control} name="name" render={({ field }) => (
                <FormItem>
                  <FormLabel>Organisation name</FormLabel>
                  <FormControl>
                    <Input autoFocus {...field} onChange={(e) => {
                      field.onChange(e);
                      if (!slugTouched) form.setValue('slug', slugify(e.target.value));
                    }} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )} />
              <FormField control={form.control} name="slug" render={({ field }) => (
                <FormItem>
                  <FormLabel>Web address</FormLabel>
                  <FormControl><Input {...field} onChange={(e) => { setSlugTouched(true); field.onChange(e.target.value.toLowerCase()); }} /></FormControl>
                  <FormDescription>business-platform/<b>{form.watch('slug') || 'your-org'}</b></FormDescription>
                  <FormMessage />
                </FormItem>
              )} />
              <Button type="submit" className="w-full" disabled={form.formState.isSubmitting}>
                {form.formState.isSubmitting ? 'Creating…' : 'Create organisation'}
              </Button>
            </form>
          </Form>
        </CardContent>
      </Card>
    </div>
  );
}

export default function Page() {
  return <RequireUser><CreateOrg /></RequireUser>;
}
