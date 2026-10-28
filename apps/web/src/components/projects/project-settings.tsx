'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { applyServerErrors } from '@/components/forms/server-errors';
import { Button } from '@/components/ui/button';
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { tr } from '@/i18n/core';
import { useT } from '@/i18n/provider';
import { orgApi, useOrgMutation } from '@/lib/api/queries';
import type { Project } from '@/lib/api/types';

const PROJECT_STATUSES = ['ACTIVE', 'ON_HOLD', 'DONE', 'ARCHIVED'] as const;

/** Built when the form mounts, so the messages follow the current language. */
const makeSchema = () => z.object({
  name: z.string().trim().min(2, tr('projects.nameRequired')).max(120),
  description: z.string().max(5000),
  status: z.enum(PROJECT_STATUSES),
  visibility: z.enum(['ORG', 'PRIVATE']),
  startDate: z.string(),
  dueDate: z.string(),
}).refine((v) => !v.startDate || !v.dueDate || v.startDate <= v.dueDate, { path: ['dueDate'], message: tr('projects.dueBeforeStart') });
type Values = z.infer<ReturnType<typeof makeSchema>>;

export function ProjectSettings({ org, project }: { org: string; project: Project }) {
  const { t } = useT();
  const [error, setError] = useState<string | null>(null);
  const schema = useMemo(makeSchema, []);
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    values: {
      name: project.name, description: project.description ?? '', status: project.status, visibility: project.visibility,
      startDate: project.startDate ?? '', dueDate: project.dueDate ?? '',
    },
  });
  const save = useOrgMutation(org, (v: Values) => orgApi.updateProject(org, project.key, {
    name: v.name, description: v.description, status: v.status, visibility: v.visibility,
    startDate: v.startDate || null, dueDate: v.dueDate || null, clearDates: !v.startDate && !v.dueDate,
  }));
  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try { await save.mutateAsync(v); toast.success(t('projects.saved')); } catch (e) { setError(applyServerErrors(form, e)); }
  });
  const disabled = !project.canManage;

  return (
    <Form {...form}>
      <form onSubmit={onSubmit} className="max-w-[720px] space-y-5 rounded-lg border bg-card p-4 sm:p-6" noValidate>
        {disabled && <p className="rounded-md bg-muted px-3 py-2 text-sm text-muted-foreground">{t('projects.onlyLead')}</p>}
        {error && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}
        <fieldset disabled={disabled} className="space-y-5">
          <FormField control={form.control} name="name" render={({ field }) => (
            <FormItem><FormLabel>{t('projects.name')}</FormLabel><FormControl><Input {...field} /></FormControl><FormMessage /></FormItem>
          )} />
          <FormItem>
            <FormLabel>{t('projects.key')}</FormLabel>
            <Input value={project.key} readOnly disabled className="font-mono" />
            <FormDescription>{t('projects.keyHint', { key: project.key })}</FormDescription>
          </FormItem>
          <FormField control={form.control} name="description" render={({ field }) => (
            <FormItem><FormLabel>{t('projects.description')}</FormLabel><FormControl><Textarea rows={4} {...field} /></FormControl><FormMessage /></FormItem>
          )} />
          <div className="grid gap-5 sm:grid-cols-2">
            <FormField control={form.control} name="status" render={({ field }) => (
              <FormItem>
                <FormLabel>{t('projects.status')}</FormLabel>
                <Select value={field.value} onValueChange={field.onChange} disabled={disabled}>
                  <FormControl><SelectTrigger className="w-full"><SelectValue /></SelectTrigger></FormControl>
                  <SelectContent>
                    {PROJECT_STATUSES.map((s) => <SelectItem key={s} value={s}>{t(`projects.${s}`)}</SelectItem>)}
                  </SelectContent>
                </Select>
              </FormItem>
            )} />
            <FormField control={form.control} name="visibility" render={({ field }) => (
              <FormItem>
                <FormLabel>{t('projects.whoCanSee')}</FormLabel>
                <Select value={field.value} onValueChange={field.onChange} disabled={disabled}>
                  <FormControl><SelectTrigger className="w-full"><SelectValue /></SelectTrigger></FormControl>
                  <SelectContent>
                    <SelectItem value="ORG">{t('projects.visOrg')}</SelectItem>
                    <SelectItem value="PRIVATE">{t('projects.visPrivate')}</SelectItem>
                  </SelectContent>
                </Select>
              </FormItem>
            )} />
            <FormField control={form.control} name="startDate" render={({ field }) => (
              <FormItem><FormLabel>{t('projects.startDate')}</FormLabel><FormControl><Input type="date" {...field} /></FormControl><FormMessage /></FormItem>
            )} />
            <FormField control={form.control} name="dueDate" render={({ field }) => (
              <FormItem><FormLabel>{t('projects.dueDate')}</FormLabel><FormControl><Input type="date" {...field} /></FormControl><FormMessage /></FormItem>
            )} />
          </div>
          {!disabled && (
            <div className="flex gap-2">
              <Button type="submit" disabled={!form.formState.isDirty || form.formState.isSubmitting}>{form.formState.isSubmitting ? t('common.saving') : t('projects.saveChanges')}</Button>
              {form.formState.isDirty && <Button type="button" variant="ghost" onClick={() => form.reset()}>{t('common.discard')}</Button>}
            </div>
          )}
        </fieldset>
      </form>
    </Form>
  );
}
