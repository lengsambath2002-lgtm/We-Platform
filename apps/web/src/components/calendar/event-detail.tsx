'use client';

import { isSameDay, parseISO } from 'date-fns';
import { Clock, FolderKanban, MapPin, Pencil, StickyNote, Trash2, UserRound, Users } from 'lucide-react';
import Link from 'next/link';
import { useState } from 'react';
import { toast } from 'sonner';

import { PersonAvatar } from '@/components/common/person';
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { orgApi, useMembers, useOrgMutation, useProjects } from '@/lib/api/queries';
import type { CalendarEvent } from '@/lib/api/types';
import { fmt } from '@/lib/format';
import { useOrg } from '@/lib/org-context';

/** "Tue 4 November, 09:00 – 10:00", "All day", or a span over several days. */
export function eventWhen(e: CalendarEvent, allDay: string) {
  const s = parseISO(e.startsAt);
  const end = parseISO(e.endsAt);
  if (e.allDay) return isSameDay(s, end) ? `${fmt(s, 'EEEE d MMMM yyyy')} · ${allDay}` : `${fmt(s, 'd MMM')} – ${fmt(end, 'd MMM yyyy')} · ${allDay}`;
  if (isSameDay(s, end)) return `${fmt(s, 'EEEE d MMMM yyyy')} · ${fmt(s, 'HH:mm')} – ${fmt(end, 'HH:mm')}`;
  return `${fmt(s, 'd MMM HH:mm')} – ${fmt(end, 'd MMM yyyy HH:mm')}`;
}

function Row({ icon: Icon, label, children }: { icon: typeof Clock; label: string; children: React.ReactNode }) {
  return (
    <div className="flex gap-3">
      <Icon className="mt-0.5 size-4 shrink-0 text-muted-foreground" aria-hidden />
      <div className="min-w-0 flex-1">
        <p className="text-xs font-medium text-muted-foreground">{label}</p>
        <div className="text-sm">{children}</div>
      </div>
    </div>
  );
}

/**
 * An event, read-only for attendees and others; its owner can edit it (`onEdit`), and the owner or an
 * Owner / Admin can delete it after confirming.
 */
export function EventDetail({ event, open, onOpenChange, onEdit }: {
  event: CalendarEvent; open: boolean; onOpenChange: (v: boolean) => void; onEdit: () => void;
}) {
  const { t } = useT();
  const { slug, can } = useOrg();
  const members = useMembers(slug);
  const projects = useProjects(slug);
  const [confirm, setConfirm] = useState(false);
  const remove = useOrgMutation(slug, () => orgApi.deleteEvent(slug, event.id));
  const person = (id: string) => {
    const m = members.data?.find((x) => x.userId === id);
    return m ? { id, name: m.name, avatarUrl: m.avatarUrl } : { id, name: t('collab.someone'), avatarUrl: null };
  };
  const owner = person(event.ownerId);
  const attendees = event.attendeeIds.filter((id) => id !== event.ownerId).map(person);
  const project = can('projects.view') && event.projectId ? projects.data?.find((p) => p.id === event.projectId) : undefined;

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle className="break-words pr-6">{event.title}</DialogTitle>
            <DialogDescription className="sr-only">{t('collab.eventDetails')}</DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <Row icon={Clock} label={t('collab.when')}>{eventWhen(event, t('calendar.allDay'))}</Row>
            {event.location && <Row icon={MapPin} label={t('collab.where')}><span className="break-words">{event.location}</span></Row>}
            {event.description && <Row icon={StickyNote} label={t('collab.notes')}><p className="whitespace-pre-wrap break-words">{event.description}</p></Row>}
            {project && (
              <Row icon={FolderKanban} label={t('collab.project')}>
                <Link href={`/${slug}/projects/${project.key}`} className="text-primary hover:underline">
                  <span className="font-mono text-xs">{project.key}</span> {project.name}
                </Link>
              </Row>
            )}
            <Row icon={UserRound} label={t('collab.organiser')}>
              <span className="flex items-center gap-2"><PersonAvatar person={owner} className="size-6" />{owner.name}</span>
            </Row>
            <Row icon={Users} label={t('collab.attendees')}>
              {attendees.length === 0 ? <span className="text-muted-foreground">{t('collab.noAttendees')}</span> : (
                <ul className="mt-1 space-y-1.5">
                  {attendees.map((p) => <li key={p.id} className="flex items-center gap-2"><PersonAvatar person={p} className="size-6" />{p.name}</li>)}
                </ul>
              )}
            </Row>
            {!event.canEdit && <p className="rounded-md bg-muted px-3 py-2 text-xs text-muted-foreground">{t('collab.readOnlyEvent')}</p>}
          </div>
          {(event.canEdit || event.canDelete) && (
            <DialogFooter className="gap-2 sm:justify-between">
              {event.canDelete ? (
                <Button variant="outline" className="text-destructive hover:text-destructive" onClick={() => setConfirm(true)}>
                  <Trash2 className="size-4" />{t('collab.deleteEvent')}
                </Button>
              ) : <span />}
              {event.canEdit && <Button onClick={onEdit}><Pencil className="size-4" />{t('collab.editEvent')}</Button>}
            </DialogFooter>
          )}
        </DialogContent>
      </Dialog>
      <AlertDialog open={confirm} onOpenChange={setConfirm}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('collab.deleteEventTitle', { title: event.title })}</AlertDialogTitle>
            <AlertDialogDescription>{t('collab.deleteEventBody')}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t('common.cancel')}</AlertDialogCancel>
            <AlertDialogAction className="bg-destructive text-white hover:bg-destructive/90" onClick={async () => {
              try { await remove.mutateAsync(undefined); toast.success(t('calendar.eventDeleted')); onOpenChange(false); }
              catch (e) { toast.error(errorMessage(e)); }
            }}>{t('common.delete')}</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
