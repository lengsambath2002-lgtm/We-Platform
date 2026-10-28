'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { applyServerErrors } from '@/components/forms/server-errors';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { orgApi, useMembers, useOrgMutation } from '@/lib/api/queries';

const schema = z.object({
  title: z.string().trim().min(1, 'Enter a title').max(200),
  date: z.string().min(1, 'Choose a date'),
  allDay: z.boolean(),
  start: z.string(),
  end: z.string(),
  location: z.string().max(200),
  description: z.string().max(5000),
  attendeeIds: z.array(z.string()),
}).refine((v) => v.allDay || (v.start && v.end && v.start < v.end), { path: ['end'], message: 'Ends before it starts' });
type Values = z.infer<typeof schema>;

/** Times are entered in the viewer's local time and sent as instants. */
export function NewEventDialog({ org, date, open, onOpenChange }: { org: string; date: string; open: boolean; onOpenChange: (v: boolean) => void }) {
  const members = useMembers(org);
  const [error, setError] = useState<string | null>(null);
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    values: { title: '', date, allDay: false, start: '09:00', end: '10:00', location: '', description: '', attendeeIds: [] },
  });
  const allDay = form.watch('allDay');
  const create = useOrgMutation(org, (v: Values) => {
    const startsAt = new Date(`${v.date}T${v.allDay ? '00:00' : v.start}`);
    const endsAt = v.allDay ? new Date(startsAt.getTime() + 86_400_000 - 60_000) : new Date(`${v.date}T${v.end}`);
    return orgApi.createEvent(org, {
      title: v.title, description: v.description || null, location: v.location || null,
      startsAt: startsAt.toISOString(), endsAt: endsAt.toISOString(), allDay: v.allDay, projectId: null, attendeeIds: v.attendeeIds,
    });
  });
  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try { await create.mutateAsync(v); toast.success('Event added'); onOpenChange(false); } catch (e) { setError(applyServerErrors(form, e)); }
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>New event</DialogTitle>
          <DialogDescription>People you invite get a notification and see it on their calendar.</DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={onSubmit} className="space-y-4" noValidate>
            {error && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}
            <FormField control={form.control} name="title" render={({ field }) => (
              <FormItem><FormLabel>Title</FormLabel><FormControl><Input autoFocus {...field} /></FormControl><FormMessage /></FormItem>
            )} />
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
              <FormField control={form.control} name="date" render={({ field }) => (
                <FormItem className="col-span-2 sm:col-span-1"><FormLabel>Date</FormLabel><FormControl><Input type="date" {...field} /></FormControl><FormMessage /></FormItem>
              )} />
              {!allDay && (
                <>
                  <FormField control={form.control} name="start" render={({ field }) => (
                    <FormItem><FormLabel>Starts</FormLabel><FormControl><Input type="time" {...field} /></FormControl></FormItem>
                  )} />
                  <FormField control={form.control} name="end" render={({ field }) => (
                    <FormItem><FormLabel>Ends</FormLabel><FormControl><Input type="time" {...field} /></FormControl><FormMessage /></FormItem>
                  )} />
                </>
              )}
            </div>
            <FormField control={form.control} name="allDay" render={({ field }) => (
              <FormItem className="flex items-center gap-2"><FormControl><Checkbox checked={field.value} onCheckedChange={(v) => field.onChange(v === true)} /></FormControl><FormLabel className="!mt-0">All day</FormLabel></FormItem>
            )} />
            <FormField control={form.control} name="location" render={({ field }) => (
              <FormItem><FormLabel>Location <span className="font-normal text-muted-foreground">(optional)</span></FormLabel><FormControl><Input {...field} /></FormControl></FormItem>
            )} />
            <FormField control={form.control} name="description" render={({ field }) => (
              <FormItem><FormLabel>Notes <span className="font-normal text-muted-foreground">(optional)</span></FormLabel><FormControl><Textarea rows={2} {...field} /></FormControl></FormItem>
            )} />
            <FormField control={form.control} name="attendeeIds" render={({ field }) => (
              <FormItem>
                <FormLabel>Invite</FormLabel>
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
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button>
              <Button type="submit" disabled={form.formState.isSubmitting}>{form.formState.isSubmitting ? 'Adding…' : 'Add event'}</Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
