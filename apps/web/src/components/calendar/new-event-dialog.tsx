'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { format, parseISO } from 'date-fns';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { applyServerErrors } from '@/components/forms/server-errors';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { useT } from '@/i18n/provider';
import { collabApi } from '@/lib/api/collab';
import { orgApi, useMembers, useOrgMutation, useProjects } from '@/lib/api/queries';
import type { CalendarEvent } from '@/lib/api/types';
import { useOrg } from '@/lib/org-context';

const makeSchema = (t: ReturnType<typeof useT>['t']) => z.object({
  title: z.string().trim().min(1, t('calendar.errTitle')).max(200),
  date: z.string().min(1, t('calendar.errDate')),
  allDay: z.boolean(),
  start: z.string(),
  end: z.string(),
  location: z.string().max(200),
  description: z.string().max(5000),
  attendeeIds: z.array(z.string()),
  projectId: z.string(),
}).refine((v) => v.allDay || (v.start && v.end && v.start < v.end), { path: ['end'], message: t('calendar.errEndsBeforeStart') });
type Values = z.infer<ReturnType<typeof makeSchema>>;

const NO_PROJECT = 'none';

/** The form's values for an existing event (in the viewer's local time). */
function fromEvent(e: CalendarEvent): Values {
  const s = parseISO(e.startsAt);
  return {
    title: e.title, date: format(s, 'yyyy-MM-dd'), allDay: e.allDay, start: e.allDay ? '09:00' : format(s, 'HH:mm'),
    end: e.allDay ? '10:00' : format(parseISO(e.endsAt), 'HH:mm'), location: e.location ?? '', description: e.description ?? '',
    attendeeIds: e.attendeeIds, projectId: e.projectId ?? NO_PROJECT,
  };
}

/**
 * Creates an event, or with `event` edits it (its owner only). Times are entered in the viewer's local
 * time and sent as instants. The optional project is one of the projects the person can see.
 */
export function NewEventDialog({ org, date, event, open, onOpenChange }: {
  org: string; date: string; event?: CalendarEvent; open: boolean; onOpenChange: (v: boolean) => void;
}) {
  const { t } = useT();
  const { can } = useOrg();
  const schema = useMemo(() => makeSchema(t), [t]);
  const members = useMembers(org);
  const projectsOn = can('projects.view');
  const projects = useProjects(org);
  const [error, setError] = useState<string | null>(null);
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    values: event ? fromEvent(event)
      : { title: '', date, allDay: false, start: '09:00', end: '10:00', location: '', description: '', attendeeIds: [], projectId: NO_PROJECT },
  });
  const allDay = form.watch('allDay');
  const save = useOrgMutation(org, (v: Values) => {
    const startsAt = new Date(`${v.date}T${v.allDay ? '00:00' : v.start}`);
    const endsAt = v.allDay ? new Date(startsAt.getTime() + 86_400_000 - 60_000) : new Date(`${v.date}T${v.end}`);
    const body = {
      title: v.title, description: v.description || null, location: v.location || null,
      startsAt: startsAt.toISOString(), endsAt: endsAt.toISOString(), allDay: v.allDay,
      projectId: v.projectId === NO_PROJECT ? null : v.projectId, attendeeIds: v.attendeeIds,
    };
    return event ? collabApi.updateEvent(org, event.id, body) : orgApi.createEvent(org, body);
  });
  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try { await save.mutateAsync(v); toast.success(t(event ? 'collab.eventUpdated' : 'calendar.eventAdded')); onOpenChange(false); } catch (e) { setError(applyServerErrors(form, e)); }
  });
  // Projects I can see and that are not archived, plus the one already linked.
  const projectOptions = (projects.data ?? []).filter((p) => p.status !== 'ARCHIVED' || p.id === event?.projectId);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{t(event ? 'collab.editEvent' : 'calendar.newEvent')}</DialogTitle>
          <DialogDescription>{t('calendar.dialogBody')}</DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={onSubmit} className="space-y-4" noValidate>
            {error && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}
            <FormField control={form.control} name="title" render={({ field }) => (
              <FormItem><FormLabel>{t('calendar.eventTitle')}</FormLabel><FormControl><Input autoFocus {...field} /></FormControl><FormMessage /></FormItem>
            )} />
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
              <FormField control={form.control} name="date" render={({ field }) => (
                <FormItem className="col-span-2 sm:col-span-1"><FormLabel>{t('calendar.date')}</FormLabel><FormControl><Input type="date" {...field} /></FormControl><FormMessage /></FormItem>
              )} />
              {!allDay && (
                <>
                  <FormField control={form.control} name="start" render={({ field }) => (
                    <FormItem><FormLabel>{t('calendar.starts')}</FormLabel><FormControl><Input type="time" {...field} /></FormControl></FormItem>
                  )} />
                  <FormField control={form.control} name="end" render={({ field }) => (
                    <FormItem><FormLabel>{t('calendar.ends')}</FormLabel><FormControl><Input type="time" {...field} /></FormControl><FormMessage /></FormItem>
                  )} />
                </>
              )}
            </div>
            <FormField control={form.control} name="allDay" render={({ field }) => (
              <FormItem className="flex items-center gap-2"><FormControl><Checkbox checked={field.value} onCheckedChange={(v) => field.onChange(v === true)} /></FormControl><FormLabel className="!mt-0">{t('calendar.allDay')}</FormLabel></FormItem>
            )} />
            <FormField control={form.control} name="location" render={({ field }) => (
              <FormItem><FormLabel>{t('calendar.location')} <span className="font-normal text-muted-foreground">{t('common.optional')}</span></FormLabel><FormControl><Input {...field} /></FormControl></FormItem>
            )} />
            <FormField control={form.control} name="description" render={({ field }) => (
              <FormItem><FormLabel>{t('calendar.notes')} <span className="font-normal text-muted-foreground">{t('common.optional')}</span></FormLabel><FormControl><Textarea rows={2} {...field} /></FormControl></FormItem>
            )} />
            {projectsOn && projectOptions.length > 0 && (
              <FormField control={form.control} name="projectId" render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('collab.project')} <span className="font-normal text-muted-foreground">{t('common.optional')}</span></FormLabel>
                  <Select value={field.value} onValueChange={field.onChange}>
                    <FormControl><SelectTrigger className="w-full"><SelectValue /></SelectTrigger></FormControl>
                    <SelectContent>
                      <SelectItem value={NO_PROJECT}>{t('collab.noProject')}</SelectItem>
                      {projectOptions.map((p) => <SelectItem key={p.id} value={p.id}><span className="font-mono text-xs text-muted-foreground">{p.key}</span> {p.name}</SelectItem>)}
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )} />
            )}
            <FormField control={form.control} name="attendeeIds" render={({ field }) => (
              <FormItem>
                <FormLabel>{t('calendar.invite')}</FormLabel>
                <div className="max-h-36 space-y-1 overflow-y-auto rounded-md border p-2">
                  {(members.data ?? []).filter((m) => m.status === 'ACTIVE').map((m) => (
                    <label key={m.userId} className="flex items-center gap-2 text-sm">
                      <Checkbox checked={field.value.includes(m.userId)}
                        onCheckedChange={(v) => field.onChange(v ? [...field.value, m.userId] : field.value.filter((x) => x !== m.userId))} />
                      {m.name}
                    </label>
                  ))}
                </div>
              </FormItem>
            )} />
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>{t('common.cancel')}</Button>
              <Button type="submit" disabled={form.formState.isSubmitting}>
                {form.formState.isSubmitting ? (event ? t('common.saving') : t('calendar.adding')) : event ? t('collab.saveEvent') : t('calendar.addEvent')}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
