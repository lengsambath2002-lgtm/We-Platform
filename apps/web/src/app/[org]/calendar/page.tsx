'use client';

import {
  addDays, addMonths, eachDayOfInterval, endOfMonth, endOfWeek, format, isSameDay, isSameMonth, isToday, parseISO, startOfDay, startOfMonth, startOfWeek,
} from 'date-fns';
import { CalendarDays, CalendarX2, ChevronLeft, ChevronRight, Clock, Flag, FolderKanban, MapPin, Palmtree, Plus, Users } from 'lucide-react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { Suspense, useEffect, useMemo, useState } from 'react';
import { toast } from 'sonner';

import { EventDetail } from '@/components/calendar/event-detail';
import { NewEventDialog } from '@/components/calendar/new-event-dialog';
import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/label';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { tr } from '@/i18n/core';
import { useT } from '@/i18n/provider';
import { useCalendar } from '@/lib/api/queries';
import type { CalendarEvent, Deadline, Period } from '@/lib/api/types';
import { fmt } from '@/lib/format';
import { useOrg } from '@/lib/org-context';
import { cn } from '@/lib/utils';

const iso = (d: Date) => format(d, 'yyyy-MM-dd');
type Item = { kind: 'event'; e: CalendarEvent } | { kind: 'deadline'; d: Deadline } | { kind: 'period'; p: Period };
const keyOf = (it: Item) => (it.kind === 'event' ? it.e.id : it.kind === 'deadline' ? it.d.id : `${it.p.id}`);

/** Periods (approved leave) show on the working days they cover, like they are counted. */
function periodOn(p: Period, day: Date) {
  const d = iso(day);
  return p.start <= d && p.end >= d && day.getDay() !== 0 && day.getDay() !== 6;
}

function itemsOn(day: Date, events: CalendarEvent[], deadlines: Deadline[], periods: Period[] = []): Item[] {
  return [
    ...deadlines.filter((d) => isSameDay(parseISO(d.date), day)).map((d) => ({ kind: 'deadline' as const, d })),
    ...periods.filter((p) => periodOn(p, day)).map((p) => ({ kind: 'period' as const, p })),
    ...events.filter((e) => parseISO(e.startsAt) <= addDays(day, 1) && parseISO(e.endsAt) >= day
      && (isSameDay(parseISO(e.startsAt), day) || (parseISO(e.startsAt) < day && parseISO(e.endsAt) > day)))
      .sort((a, b) => a.startsAt.localeCompare(b.startsAt)).map((e) => ({ kind: 'event' as const, e })),
  ];
}

/** A deadline links to its record; an event opens its details (`onOpen`). */
function Chip({ item, compact, onOpen }: { org: string; item: Item; compact?: boolean; onOpen: (e: CalendarEvent) => void }) {
  if (item.kind === 'period') {
    const { p } = item;
    const label = p.kind === 'leave' ? tr('hr.calAway', { name: p.title }) : p.title;
    return (
      <Link href={p.link} className="flex items-center gap-1 truncate rounded bg-muted px-1.5 py-0.5 text-xs text-foreground hover:underline" title={label}>
        <Palmtree className="size-3 shrink-0 text-muted-foreground" aria-hidden /><span className="truncate">{compact ? p.title : label}</span>
      </Link>
    );
  }
  if (item.kind === 'deadline') {
    const { d } = item;
    return (
      <Link href={d.link} className={cn('flex items-center gap-1 truncate rounded px-1.5 py-0.5 text-xs hover:underline',
        d.status === 'DONE' ? 'text-muted-foreground line-through' : 'bg-warning/10 text-warning')} title={tr('calendar.isDue', { key: d.key, title: d.title })}>
        <Flag className="size-3 shrink-0" aria-hidden /><span className="truncate">{compact ? d.key : `${d.key} ${d.title}`}</span>
      </Link>
    );
  }
  const { e } = item;
  return (
    <button type="button" onClick={() => onOpen(e)} aria-label={tr('collab.openEvent', { title: e.title })}
      className={cn('items-center gap-1 truncate rounded bg-primary/10 px-1.5 py-0.5 text-left text-xs text-primary hover:bg-primary/20', compact ? 'flex w-full' : 'inline-flex max-w-full')} title={e.title}>
      {!e.allDay && <span className="tabular">{fmt(parseISO(e.startsAt), 'HH:mm')}</span>}<span className="truncate">{e.title}</span>
    </button>
  );
}

/** `?date=yyyy-MM-dd` opens that day; `&event=id` also opens the event (links from notifications and the audit log). */
function CalendarPage() {
  const { slug, can } = useOrg();
  const { t, locale } = useT();
  const params = useSearchParams();
  const router = useRouter();
  const linked = /^\d{4}-\d{2}-\d{2}$/.test(params.get('date') ?? '') ? parseISO(params.get('date')!) : null;
  const [month, setMonth] = useState(() => startOfMonth(linked ?? new Date()));
  const [view, setView] = useState<'month' | 'agenda'>('month');
  const [mine, setMine] = useState(false);
  const [selected, setSelected] = useState(() => linked ?? startOfDay(new Date()));
  const [creating, setCreating] = useState<string | null>(null);
  const [openId, setOpenId] = useState<string | null>(() => params.get('event'));
  const [editing, setEditing] = useState<CalendarEvent | null>(null);
  const gridStart = startOfWeek(month, { weekStartsOn: 1 });
  const gridEnd = endOfWeek(endOfMonth(month), { weekStartsOn: 1 });
  const { data, error, isPending, refetch } = useCalendar(slug, iso(gridStart), iso(gridEnd), mine);
  const days = useMemo(() => eachDayOfInterval({ start: gridStart, end: gridEnd }), [gridStart.getTime(), gridEnd.getTime()]); // eslint-disable-line react-hooks/exhaustive-deps
  const events = data?.events ?? [];
  const deadlines = data?.deadlines ?? [];
  const periods = data?.periods ?? [];
  const agenda = days.filter((d) => isSameMonth(d, month)).map((d) => ({ d, items: itemsOn(d, events, deadlines, periods) })).filter((x) => x.items.length);
  const dayItems = itemsOn(selected, events, deadlines, periods);
  const canCreate = can('calendar.create');
  const opened = openId ? events.find((e) => e.id === openId) : undefined;
  const open = (e: CalendarEvent) => setOpenId(e.id);
  const close = () => {
    setOpenId(null);
    if (params.get('event')) router.replace(`/${slug}/calendar`, { scroll: false });
  };
  // A linked event that isn't there (deleted, or no longer visible) says so once.
  useEffect(() => {
    if (openId && data && !data.events.some((e) => e.id === openId)) {
      toast.error(tr('collab.eventGone'));
      setOpenId(null);
    }
  }, [openId, data]);

  return (
    <>
      <PageHeader title={t('calendar.title')} subtitle={t('calendar.subtitle')}
        actions={canCreate && <Button size="sm" onClick={() => setCreating(iso(selected))}><Plus className="size-4" />{t('calendar.newEvent')}</Button>} />
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <div className="flex items-center gap-1">
          <Button variant="outline" size="icon" aria-label={t('calendar.prevMonth')} onClick={() => setMonth((m) => addMonths(m, -1))}><ChevronLeft className="size-4" /></Button>
          <Button variant="outline" size="sm" onClick={() => { setMonth(startOfMonth(new Date())); setSelected(startOfDay(new Date())); }}>{t('common.today')}</Button>
          <Button variant="outline" size="icon" aria-label={t('calendar.nextMonth')} onClick={() => setMonth((m) => addMonths(m, 1))}><ChevronRight className="size-4" /></Button>
        </div>
        <h2 className="min-w-36 text-base font-semibold" aria-live="polite">{fmt(month, 'MMMM yyyy')}</h2>
        <Tabs value={view} onValueChange={(v) => setView(v as 'month' | 'agenda')}>
          <TabsList><TabsTrigger value="month">{t('calendar.month')}</TabsTrigger><TabsTrigger value="agenda">{t('calendar.agenda')}</TabsTrigger></TabsList>
        </Tabs>
        <div className="flex items-center gap-2">
          <Checkbox id="mine" checked={mine} onCheckedChange={(v) => setMine(v === true)} /><Label htmlFor="mine">{t('calendar.onlyMine')}</Label>
        </div>
      </div>

      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={6} /> : view === 'agenda' ? (
        agenda.length === 0 ? <EmptyState icon={CalendarX2} title={t('calendar.nothingIn', { month: fmt(month, 'MMMM') })} body={t('calendar.nothingInBody')} />
          : (
            <ol className="divide-y rounded-lg border bg-card">
              {agenda.map(({ d, items }) => (
                <li key={iso(d)} className="flex gap-4 px-4 py-3">
                  <div className={cn('w-14 shrink-0 text-center', isToday(d) && 'text-primary')}>
                    <div className="text-xs uppercase text-muted-foreground">{fmt(d, locale === 'km' ? 'EEEE' : 'EEE')}</div>
                    <div className="text-xl font-semibold tabular">{fmt(d, 'd')}</div>
                  </div>
                  <ul className="min-w-0 flex-1 space-y-1">{items.map((it) => <li key={keyOf(it)}><Chip org={slug} item={it} onOpen={open} /></li>)}</ul>
                </li>
              ))}
            </ol>
          )
      ) : (
        <div className="grid gap-4 lg:grid-cols-[1fr_300px]">
          <div className="overflow-hidden rounded-lg border bg-card">
            <div className="grid grid-cols-7 border-b text-center text-xs font-medium text-muted-foreground">
              {days.slice(0, 7).map((d) => (
                <div key={iso(d)} className="py-2">
                  {/* Khmer short day names are single letters; spell them out where there is room. */}
                  <span className={locale === 'km' ? 'sm:hidden' : undefined}>{fmt(d, 'EEE')}</span>
                  {locale === 'km' && <span className="hidden sm:inline">{fmt(d, 'EEEE')}</span>}
                </div>
              ))}
            </div>
            <div className="grid grid-cols-7">
              {days.map((d) => {
                const items = itemsOn(d, events, deadlines, periods);
                // The whole cell selects the day; its chips sit above that button and open what they show.
                return (
                  <div key={iso(d)}
                    className={cn('relative min-h-14 border-b border-r p-1 text-left align-top hover:bg-muted/50 sm:min-h-24 [&:nth-child(7n)]:border-r-0',
                      !isSameMonth(d, month) && 'bg-muted/30 text-muted-foreground', isSameDay(d, selected) && 'ring-2 ring-inset ring-primary')}>
                    <button type="button" onClick={() => setSelected(d)} aria-pressed={isSameDay(d, selected)}
                      aria-label={t('calendar.dayItems', { date: fmt(d, 'EEEE d MMMM'), count: items.length })}
                      className="absolute inset-0 focus-visible:outline-2 focus-visible:outline-ring" />
                    <span className={cn('pointer-events-none relative inline-grid size-6 place-items-center rounded-full text-xs tabular', isToday(d) && 'bg-primary font-semibold text-primary-foreground')}>{fmt(d, 'd')}</span>
                    <span className="relative mt-0.5 hidden space-y-0.5 sm:block">
                      {items.slice(0, 3).map((it) => <span key={keyOf(it)} className="block"><Chip org={slug} item={it} compact onOpen={open} /></span>)}
                      {items.length > 3 && <span className="pointer-events-none block px-1.5 text-[11px] text-muted-foreground">{t('calendar.more', { count: items.length - 3 })}</span>}
                    </span>
                    {items.length > 0 && <span className="pointer-events-none relative mx-auto mt-1 block size-1.5 rounded-full bg-primary sm:hidden" aria-hidden />}
                  </div>
                );
              })}
            </div>
          </div>
          <section aria-labelledby="day-h" className="rounded-lg border bg-card p-4">
            <div className="mb-3 flex items-center justify-between">
              <h2 id="day-h" className="text-sm font-semibold">{fmt(selected, 'EEEE d MMMM')}</h2>
              {canCreate && <Button variant="ghost" size="icon" className="size-7" aria-label={t('calendar.addOnDay')} onClick={() => setCreating(iso(selected))}><Plus className="size-4" /></Button>}
            </div>
            {dayItems.length === 0 ? <p className="text-sm text-muted-foreground">{t('calendar.nothingOnDay')}</p> : (
              <ul className="space-y-3">
                {dayItems.map((it) => it.kind !== 'event' ? (
                  <li key={keyOf(it)}><Chip org={slug} item={it} onOpen={open} /></li>
                ) : (
                  <li key={it.e.id}>
                    <button type="button" onClick={() => open(it.e)} aria-label={t('collab.openEvent', { title: it.e.title })}
                      className="w-full rounded-md border p-2.5 text-left hover:bg-muted/50 focus-visible:outline-2 focus-visible:outline-ring">
                      <span className="block text-sm font-medium">{it.e.title}</span>
                      <span className="mt-1 flex items-center gap-1 text-xs text-muted-foreground"><Clock className="size-3" />
                        {it.e.allDay ? t('calendar.allDay') : `${fmt(parseISO(it.e.startsAt), 'HH:mm')} – ${fmt(parseISO(it.e.endsAt), 'HH:mm')}`}</span>
                      {it.e.location && <span className="mt-0.5 flex items-center gap-1 text-xs text-muted-foreground"><MapPin className="size-3" />{it.e.location}</span>}
                      <span className="mt-0.5 flex items-center gap-3 text-xs text-muted-foreground">
                        {it.e.attendeeIds.length > 0 && <span className="flex items-center gap-1"><Users className="size-3" />{it.e.attendeeIds.length}</span>}
                        {it.e.projectId && <FolderKanban className="size-3" aria-label={t('collab.project')} />}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      )}
      {creating && <NewEventDialog org={slug} date={creating} open onOpenChange={(v) => !v && setCreating(null)} />}
      {opened && !editing && <EventDetail event={opened} open onOpenChange={(v) => !v && close()} onEdit={() => setEditing(opened)} />}
      {editing && <NewEventDialog org={slug} date={iso(parseISO(editing.startsAt))} event={editing} open onOpenChange={(v) => !v && setEditing(null)} />}
      {!isPending && !error && events.length === 0 && deadlines.length === 0 && periods.length === 0 && view === 'month' && (
        <p className="mt-3 flex items-center gap-2 text-sm text-muted-foreground"><CalendarDays className="size-4" />{t('calendar.noEventsMonth')}</p>
      )}
    </>
  );
}

export default function Page() {
  return <Suspense><CalendarPage /></Suspense>;
}
