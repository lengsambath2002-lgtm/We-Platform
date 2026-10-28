'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { useRouter } from 'next/navigation';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { applyServerErrors } from '@/components/forms/server-errors';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { tr } from '@/i18n/core';
import { useT } from '@/i18n/provider';
import { orgApi, useOrgMutation } from '@/lib/api/queries';

/** Built when the dialog mounts, so the messages follow the current language. */
const makeSchema = () => z.object({
  name: z.string().trim().min(2, tr('projects.nameRequired')).max(120),
  key: z.string().regex(/^[A-Z][A-Z0-9]{1,9}$/, tr('projects.keyFormat')),
  description: z.string().max(5000).optional(),
  visibility: z.enum(['ORG', 'PRIVATE']),
  dueDate: z.string().optional(),
});
type Values = z.infer<ReturnType<typeof makeSchema>>;

const keyFrom = (name: string) => {
  const words = name.toUpperCase().replace(/[^A-Z0-9 ]/g, '').split(/\s+/).filter(Boolean);
  const k = words.length > 1 ? words.map((w) => w[0]).join('') : (words[0] ?? '').slice(0, 4);
  return /^[A-Z]/.test(k) ? k.slice(0, 10) : '';
};

export function NewProjectDialog({ org, open, onOpenChange }: { org: string; open: boolean; onOpenChange: (v: boolean) => void }) {
  const { t } = useT();
  const router = useRouter();
  const [keyTouched, setKeyTouched] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const schema = useMemo(makeSchema, []);
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { name: '', key: '', description: '', visibility: 'ORG', dueDate: '' } });
  const create = useOrgMutation(org, (v: Values) => orgApi.createProject(org, { ...v, dueDate: v.dueDate || null, description: v.description || null }));

  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try {
      const p = await create.mutateAsync(v);
      toast.success(t('projects.created', { name: p.name }));
      onOpenChange(false);
      form.reset();
      router.push(`/${org}/projects/${p.key}`);
    } catch (e) {
      setError(applyServerErrors(form, e));
    }
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{t('projects.newProject')}</DialogTitle>
          <DialogDescription>{t('projects.newProjectBody')}</DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={onSubmit} className="space-y-4" noValidate>
            {error && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}
            <div className="grid gap-4 sm:grid-cols-[1fr_130px]">
              <FormField control={form.control} name="name" render={({ field }) => (
                <FormItem><FormLabel>{t('projects.name')}</FormLabel><FormControl><Input autoFocus {...field} onChange={(e) => { field.onChange(e); if (!keyTouched) form.setValue('key', keyFrom(e.target.value)); }} /></FormControl><FormMessage /></FormItem>
              )} />
              <FormField control={form.control} name="key" render={({ field }) => (
                <FormItem><FormLabel>{t('projects.key')}</FormLabel><FormControl><Input className="font-mono uppercase" {...field} onChange={(e) => { setKeyTouched(true); field.onChange(e.target.value.toUpperCase()); }} /></FormControl><FormMessage /></FormItem>
              )} />
            </div>
            <FormField control={form.control} name="description" render={({ field }) => (
              <FormItem><FormLabel>{t('projects.description')} <span className="font-normal text-muted-foreground">{t('common.optional')}</span></FormLabel><FormControl><Textarea rows={3} {...field} /></FormControl><FormMessage /></FormItem>
            )} />
            <div className="grid gap-4 sm:grid-cols-2">
              <FormField control={form.control} name="visibility" render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('projects.whoCanSee')}</FormLabel>
                  <Select value={field.value} onValueChange={field.onChange}>
                    <FormControl><SelectTrigger className="w-full"><SelectValue /></SelectTrigger></FormControl>
                    <SelectContent>
                      <SelectItem value="ORG">{t('projects.visOrg')}</SelectItem>
                      <SelectItem value="PRIVATE">{t('projects.visPrivate')}</SelectItem>
                    </SelectContent>
                  </Select>
                  <FormDescription>{t('projects.guestsHint')}</FormDescription>
                </FormItem>
              )} />
              <FormField control={form.control} name="dueDate" render={({ field }) => (
                <FormItem><FormLabel>{t('projects.dueDate')} <span className="font-normal text-muted-foreground">{t('common.optional')}</span></FormLabel><FormControl><Input type="date" {...field} /></FormControl><FormMessage /></FormItem>
              )} />
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>{t('common.cancel')}</Button>
              <Button type="submit" disabled={form.formState.isSubmitting}>{form.formState.isSubmitting ? t('common.creating') : t('projects.createProject')}</Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
