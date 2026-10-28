'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { PRIORITIES, PRIORITY_LABEL, STATUSES, STATUS_LABEL } from '@/components/common/badges';
import { applyServerErrors } from '@/components/forms/server-errors';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { tr } from '@/i18n/core';
import { useT } from '@/i18n/provider';
import { orgApi, useOrgMutation } from '@/lib/api/queries';
import type { Project, TaskStatus } from '@/lib/api/types';

import { useAssignees } from './use-assignees';

/** Built when the dialog mounts, so the messages follow the current language. */
const makeSchema = () => z.object({
  title: z.string().trim().min(1, tr('tasks.enterTitle')).max(200),
  description: z.string().max(20000).optional(),
  status: z.enum(['TODO', 'IN_PROGRESS', 'IN_REVIEW', 'DONE']),
  priority: z.enum(['LOW', 'MEDIUM', 'HIGH', 'URGENT']),
  assigneeId: z.string(),
  dueDate: z.string().optional(),
});
type Values = z.infer<ReturnType<typeof makeSchema>>;

export function NewTaskDialog({ org, project, open, onOpenChange, status = 'TODO', parentId }: {
  org: string; project: Project; open: boolean; onOpenChange: (v: boolean) => void; status?: TaskStatus; parentId?: string;
}) {
  const { t } = useT();
  const assignees = useAssignees(org, project);
  const [error, setError] = useState<string | null>(null);
  const schema = useMemo(makeSchema, []);
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    values: { title: '', description: '', status, priority: 'MEDIUM', assigneeId: 'none', dueDate: '' },
  });
  const create = useOrgMutation(org, (v: Values) => orgApi.createTask(org, project.key, {
    title: v.title, description: v.description || null, status: v.status, priority: v.priority,
    assigneeId: v.assigneeId === 'none' ? null : v.assigneeId, dueDate: v.dueDate || null, parentId: parentId ?? null,
  }));

  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try {
      const task = await create.mutateAsync(v);
      toast.success(t('tasks.created', { key: task.key }));
      form.reset();
      onOpenChange(false);
    } catch (e) {
      setError(applyServerErrors(form, e));
    }
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{parentId ? t('tasks.newSubtask') : t('tasks.newTaskIn', { project: project.name })}</DialogTitle>
          <DialogDescription className="sr-only">{t('tasks.newTaskBody')}</DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={onSubmit} className="space-y-4" noValidate>
            {error && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}
            <FormField control={form.control} name="title" render={({ field }) => (
              <FormItem><FormLabel>{t('tasks.title')}</FormLabel><FormControl><Input autoFocus placeholder={t('tasks.titlePlaceholder')} {...field} /></FormControl><FormMessage /></FormItem>
            )} />
            <FormField control={form.control} name="description" render={({ field }) => (
              <FormItem><FormLabel>{t('tasks.description')} <span className="font-normal text-muted-foreground">{t('common.optional')}</span></FormLabel><FormControl><Textarea rows={3} {...field} /></FormControl><FormMessage /></FormItem>
            )} />
            <div className="grid gap-4 sm:grid-cols-2">
              <FormField control={form.control} name="assigneeId" render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('tasks.assignee')}</FormLabel>
                  <Select value={field.value} onValueChange={field.onChange}>
                    <FormControl><SelectTrigger className="w-full"><SelectValue /></SelectTrigger></FormControl>
                    <SelectContent>
                      <SelectItem value="none">{t('common.unassigned')}</SelectItem>
                      {assignees.map((p) => <SelectItem key={p.id} value={p.id}>{p.name}</SelectItem>)}
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )} />
              <FormField control={form.control} name="dueDate" render={({ field }) => (
                <FormItem><FormLabel>{t('tasks.dueDate')}</FormLabel><FormControl><Input type="date" {...field} /></FormControl><FormMessage /></FormItem>
              )} />
              <FormField control={form.control} name="priority" render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('tasks.priority')}</FormLabel>
                  <Select value={field.value} onValueChange={field.onChange}>
                    <FormControl><SelectTrigger className="w-full"><SelectValue /></SelectTrigger></FormControl>
                    <SelectContent>{PRIORITIES.map((p) => <SelectItem key={p} value={p}>{PRIORITY_LABEL[p]}</SelectItem>)}</SelectContent>
                  </Select>
                </FormItem>
              )} />
              <FormField control={form.control} name="status" render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('tasks.status')}</FormLabel>
                  <Select value={field.value} onValueChange={field.onChange}>
                    <FormControl><SelectTrigger className="w-full"><SelectValue /></SelectTrigger></FormControl>
                    <SelectContent>{STATUSES.map((s) => <SelectItem key={s} value={s}>{STATUS_LABEL[s]}</SelectItem>)}</SelectContent>
                  </Select>
                </FormItem>
              )} />
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>{t('common.cancel')}</Button>
              <Button type="submit" disabled={form.formState.isSubmitting}>{form.formState.isSubmitting ? t('common.creating') : t('common.create')}</Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
