'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { applyServerErrors } from '@/components/forms/server-errors';
import { Button } from '@/components/ui/button';
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { orgApi, useOrgMutation } from '@/lib/api/queries';
import type { Project } from '@/lib/api/types';

const schema = z.object({
  name: z.string().trim().min(2, 'Enter a name').max(120),
  description: z.string().max(5000),
  status: z.enum(['ACTIVE', 'ON_HOLD', 'DONE', 'ARCHIVED']),
  visibility: z.enum(['ORG', 'PRIVATE']),
  startDate: z.string(),
  dueDate: z.string(),
}).refine((v) => !v.startDate || !v.dueDate || v.startDate <= v.dueDate, { path: ['dueDate'], message: 'Due date is before the start date' });
type Values = z.infer<typeof schema>;

export function ProjectSettings({ org, project }: { org: string; project: Project }) {
  const [error, setError] = useState<string | null>(null);
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
    try { await save.mutateAsync(v); toast.success('Project saved'); } catch (e) { setError(applyServerErrors(form, e)); }
  });
  const disabled = !project.canManage;

  return (
    <Form {...form}>
      <form onSubmit={onSubmit} className="max-w-[720px] space-y-5 rounded-lg border bg-card p-4 sm:p-6" noValidate>
        {disabled && <p className="rounded-md bg-muted px-3 py-2 text-sm text-muted-foreground">Only the project lead or an organisation admin can change these settings.</p>}
        {error && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}
        <fieldset disabled={disabled} className="space-y-5">
          <FormField control={form.control} name="name" render={({ field }) => (
            <FormItem><FormLabel>Name</FormLabel><FormControl><Input {...field} /></FormControl><FormMessage /></FormItem>
          )} />
          <FormItem>
            <FormLabel>Key</FormLabel>
            <Input value={project.key} readOnly disabled className="font-mono" />
            <FormDescription>Task numbers start with the key ({project.key}-12). It can&apos;t change.</FormDescription>
          </FormItem>
          <FormField control={form.control} name="description" render={({ field }) => (
            <FormItem><FormLabel>Description</FormLabel><FormControl><Textarea rows={4} {...field} /></FormControl><FormMessage /></FormItem>
          )} />
          <div className="grid gap-5 sm:grid-cols-2">
            <FormField control={form.control} name="status" render={({ field }) => (
              <FormItem>
                <FormLabel>Status</FormLabel>
                <Select value={field.value} onValueChange={field.onChange} disabled={disabled}>
                  <FormControl><SelectTrigger className="w-full"><SelectValue /></SelectTrigger></FormControl>
                  <SelectContent>
                    <SelectItem value="ACTIVE">Active</SelectItem><SelectItem value="ON_HOLD">On hold</SelectItem>
                    <SelectItem value="DONE">Done</SelectItem><SelectItem value="ARCHIVED">Archived</SelectItem>
                  </SelectContent>
                </Select>
              </FormItem>
            )} />
            <FormField control={form.control} name="visibility" render={({ field }) => (
              <FormItem>
                <FormLabel>Who can see it</FormLabel>
                <Select value={field.value} onValueChange={field.onChange} disabled={disabled}>
                  <FormControl><SelectTrigger className="w-full"><SelectValue /></SelectTrigger></FormControl>
                  <SelectContent>
                    <SelectItem value="ORG">Everyone in the organisation</SelectItem>
                    <SelectItem value="PRIVATE">Project members only</SelectItem>
                  </SelectContent>
                </Select>
              </FormItem>
            )} />
            <FormField control={form.control} name="startDate" render={({ field }) => (
              <FormItem><FormLabel>Start date</FormLabel><FormControl><Input type="date" {...field} /></FormControl><FormMessage /></FormItem>
            )} />
            <FormField control={form.control} name="dueDate" render={({ field }) => (
              <FormItem><FormLabel>Due date</FormLabel><FormControl><Input type="date" {...field} /></FormControl><FormMessage /></FormItem>
            )} />
          </div>
          {!disabled && (
            <div className="flex gap-2">
              <Button type="submit" disabled={!form.formState.isDirty || form.formState.isSubmitting}>{form.formState.isSubmitting ? 'Saving…' : 'Save changes'}</Button>
              {form.formState.isDirty && <Button type="button" variant="ghost" onClick={() => form.reset()}>Discard</Button>}
            </div>
          )}
        </fieldset>
      </form>
    </Form>
  );
}
