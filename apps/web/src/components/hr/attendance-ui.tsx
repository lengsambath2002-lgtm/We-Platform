'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { parseISO } from 'date-fns';
import { Clock, LogIn, LogOut, Pencil } from 'lucide-react';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { ApprovalBadge } from '@/components/common/badges';
import { PersonAvatar } from '@/components/common/person';
import { ErrorState, ListSkeleton } from '@/components/common/states';
import { applyServerErrors } from '@/components/forms/server-errors';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Textarea } from '@/components/ui/textarea';
import { currentLocale, tr } from '@/i18n/core';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import {
  type AttendanceDay, type DayStatus, type Overtime, type Timesheet, type WorkSchedule, attendanceApi, useAttendanceToday,
} from '@/lib/api/hr';
import { useOrgMutation } from '@/lib/api/queries';
import { fmt } from '@/lib/format';
import { cn } from '@/lib/utils';

/** Attendance is kept in the organisation's time zone, whatever the viewer's device says. */
export const ORG_ZONE = 'Asia/Phnom_Penh';

/** "08:05" for an instant, in the organisation's zone. */
export const orgTime = (iso: string | null) => (iso
  ? new Intl.DateTimeFormat('en-GB', { timeZone: ORG_ZONE, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).format(new Date(iso))
  : '');

/** Today as yyyy-MM-dd in the organisation's zone. */
export const orgToday = () => new Intl.DateTimeFormat('en-CA', { timeZone: ORG_ZONE }).format(new Date());

/** "08:00" from the API's "08:00:00". */
export const hhmm = (t: string) => t.slice(0, 5);

/** "2 h 30 min", "45 min", "3 h"; "—" for none. */
export function duration(minutes: number) {
  if (!minutes) return tr('common.none');
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return h === 0 ? tr('hr.mOnly', { m }) : m === 0 ? tr('hr.hOnly', { h }) : tr('hr.hm', { h, m });
}

export const weekdays = (days: number[]) => days.map((d) => tr(`hr.weekday_${d as 1 | 2 | 3 | 4 | 5 | 6 | 7}`)).join(', ');

const TONE: Record<DayStatus, string> = {
  PRESENT: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300',
  LATE: 'bg-amber-50 text-amber-800 dark:bg-amber-950 dark:text-amber-300',
  ABSENT: 'bg-destructive/10 text-destructive',
  LEAVE: 'bg-sky-50 text-sky-700 dark:bg-sky-950 dark:text-sky-300',
  HOLIDAY: 'bg-violet-50 text-violet-700 dark:bg-violet-950 dark:text-violet-300',
  DAY_OFF: 'bg-muted text-muted-foreground',
  NOT_IN: 'bg-muted text-foreground',
  UPCOMING: 'bg-transparent text-muted-foreground',
};

export function DayBadge({ status }: { status: DayStatus }) {
  return (
    <span className={cn('inline-flex items-center whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-medium', TONE[status])}>
      {tr(`hr.day_${status}`)}
    </span>
  );
}

/** Today's clock-in and clock-out, with the person's schedule; the main way into attendance. */
export function TodayCard({ org }: { org: string }) {
  const { t } = useT();
  const { data, error, isPending, refetch } = useAttendanceToday(org);
  const [note, setNote] = useState('');
  const clock = useOrgMutation(org, (inOut: 'in' | 'out') => (inOut === 'in' ? attendanceApi.clockIn(org, note.trim() || null) : attendanceApi.clockOut(org, note.trim() || null)));
  if (error) return <ErrorState error={error} retry={() => refetch()} />;
  if (isPending) return <ListSkeleton rows={1} />;
  const { day, schedule } = data;
  const act = async (inOut: 'in' | 'out') => {
    try {
      const r = await clock.mutateAsync(inOut);
      setNote('');
      toast.success(inOut === 'in' ? t('hr.clockedInAt', { time: orgTime(r.day.clockIn) }) : t('hr.clockedOutAt', { time: orgTime(r.day.clockOut) }));
    } catch (e) { toast.error(errorMessage(e)); }
  };
  return (
    <section aria-labelledby="today-att-h" className="rounded-lg border bg-card p-4">
      <div className="mb-3 flex items-start justify-between gap-2">
        <div className="min-w-0">
          <h2 id="today-att-h" className="text-sm font-semibold">{t('hr.today')}</h2>
          <p className="truncate text-xs text-muted-foreground">{t('hr.scheduleLine', { name: schedule.name, start: hhmm(schedule.startTime), end: hhmm(schedule.endTime) })}</p>
        </div>
        <DayBadge status={day.status} />
      </div>
      {(day.clockIn || day.clockOut) && (
        <dl className="mb-3 grid grid-cols-2 gap-2 text-sm">
          <div><dt className="text-xs text-muted-foreground">{t('hr.colIn')}</dt><dd className="tabular">{orgTime(day.clockIn)}{day.lateMinutes > 0 && <span className="ml-1 text-xs text-amber-700 dark:text-amber-300">({t('hr.lateBy', { time: duration(day.lateMinutes) })})</span>}</dd></div>
          <div><dt className="text-xs text-muted-foreground">{t('hr.colOut')}</dt><dd className="tabular">{orgTime(day.clockOut) || t('common.none')}</dd></div>
        </dl>
      )}
      {data.canClockIn || data.canClockOut ? (
        <div className="space-y-2">
          <Input value={note} maxLength={300} onChange={(e) => setNote(e.target.value)} placeholder={t('hr.clockNoteHint')} aria-label={t('hr.clockNote')} />
          {data.canClockIn ? (
            <Button className="w-full" disabled={clock.isPending} onClick={() => act('in')}><LogIn className="size-4" />{t(clock.isPending ? 'hr.clockingIn' : 'hr.clockIn')}</Button>
          ) : (
            <Button className="w-full" variant="outline" disabled={clock.isPending} onClick={() => act('out')}><LogOut className="size-4" />{t(clock.isPending ? 'hr.clockingOut' : 'hr.clockOut')}</Button>
          )}
        </div>
      ) : <p className="flex items-center gap-2 text-sm text-muted-foreground"><Clock className="size-4" />{t('hr.doneForToday')}</p>}
    </section>
  );
}

/** The month's totals above a timesheet. */
export function TimesheetTotals({ sheet }: { sheet: Timesheet }) {
  const { t } = useT();
  const x = sheet.totals;
  const cell = (label: string, value: string, hint?: string) => (
    <div className="rounded-lg border bg-card p-3">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="mt-0.5 text-lg font-semibold tabular">{value}</p>
      {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
    </div>
  );
  return (
    <div className="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-3">
      {cell(t('hr.totPresent'), String(x.presentDays))}
      {cell(t('hr.totLate'), String(x.lateDays))}
      {cell(t('hr.totAbsent'), String(x.absentDays))}
      {cell(t('hr.totLeave'), String(x.leaveDays))}
      {cell(t('hr.totWorked'), duration(x.workedMinutes), x.expectedMinutes ? t('hr.totOf', { expected: duration(x.expectedMinutes) }) : undefined)}
      {cell(t('hr.totOvertime'), duration(x.overtimeMinutes))}
    </div>
  );
}

/** One row per day of the month; `onCorrect` (HR) adds a "Correct" button to past days. */
export function TimesheetTable({ sheet, onCorrect }: { sheet: Timesheet; onCorrect?: (d: AttendanceDay) => void }) {
  const { t } = useT();
  const today = orgToday();
  const holiday = (d: AttendanceDay) => (currentLocale() === 'km' && d.holidayKm ? d.holidayKm : d.holidayEn);
  return (
    <div className="overflow-x-auto rounded-lg border bg-card">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>{t('hr.colDay')}</TableHead>
            <TableHead>{t('hr.colStatus')}</TableHead>
            <TableHead>{t('hr.colIn')}</TableHead>
            <TableHead>{t('hr.colOut')}</TableHead>
            <TableHead className="text-right">{t('hr.colWorked')}</TableHead>
            <TableHead className="text-right">{t('hr.colOvertime')}</TableHead>
            {onCorrect && <TableHead className="w-12"><span className="sr-only">{t('hr.correct')}</span></TableHead>}
          </TableRow>
        </TableHeader>
        <TableBody>
          {sheet.days.map((d) => (
            <TableRow key={d.date} className={cn(d.date === today && 'bg-primary/5', (d.status === 'DAY_OFF' || d.status === 'UPCOMING') && 'text-muted-foreground')}>
              <TableCell className="whitespace-nowrap tabular">{fmt(parseISO(d.date), 'EEE d')}</TableCell>
              <TableCell className="min-w-40">
                <DayBadge status={d.status} />
                {d.holidayEn && <span className="ml-2 text-xs text-muted-foreground">{holiday(d)}</span>}
                {d.lateMinutes > 0 && <span className="ml-2 text-xs text-amber-700 dark:text-amber-300">{t('hr.lateBy', { time: duration(d.lateMinutes) })}</span>}
                {d.corrected && <p className="mt-0.5 text-xs text-muted-foreground">{t('hr.correctedByHr', { reason: d.editReason ?? '' })}</p>}
                {d.note && <p className="mt-0.5 break-words text-xs text-muted-foreground">{d.note}</p>}
              </TableCell>
              <TableCell className="tabular">{orgTime(d.clockIn)}</TableCell>
              <TableCell className="tabular">{orgTime(d.clockOut)}</TableCell>
              <TableCell className="text-right tabular">{d.workedMinutes ? duration(d.workedMinutes) : ''}</TableCell>
              <TableCell className="text-right tabular">{d.overtimeMinutes ? duration(d.overtimeMinutes) : ''}</TableCell>
              {onCorrect && (
                <TableCell>
                  {d.date <= today && (
                    <Button variant="ghost" size="icon" className="size-8" aria-label={t('hr.correctTitle', { date: fmt(parseISO(d.date), 'd MMM') })} onClick={() => onCorrect(d)}>
                      <Pencil className="size-4" />
                    </Button>
                  )}
                </TableCell>
              )}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}

const makeCorrectSchema = (t: ReturnType<typeof useT>['t']) => z.object({
  clockIn: z.string(),
  clockOut: z.string(),
  note: z.string().max(300),
  reason: z.string().trim().min(1, t('hr.errReason')).max(500),
}).refine((v) => !v.clockIn || !v.clockOut || v.clockOut > v.clockIn, { path: ['clockOut'], message: t('hr.errOutBeforeIn') });
type CorrectValues = z.infer<ReturnType<typeof makeCorrectSchema>>;

/** HR managers: sets the times of one person's day, or (clock-in empty) removes it; a reason is required. */
export function CorrectDayDialog({ org, userId, day, onClose }: { org: string; userId: string; day: AttendanceDay; onClose: () => void }) {
  const { t } = useT();
  const schema = useMemo(() => makeCorrectSchema(t), [t]);
  const [error, setError] = useState<string | null>(null);
  const form = useForm<CorrectValues>({
    resolver: zodResolver(schema),
    defaultValues: { clockIn: orgTime(day.clockIn), clockOut: orgTime(day.clockOut), note: day.note ?? '', reason: '' },
  });
  const save = useOrgMutation(org, (v: CorrectValues) => attendanceApi.correct(org, userId, day.date,
    { clockIn: v.clockIn || null, clockOut: v.clockIn && v.clockOut ? v.clockOut : null, note: v.note || null, reason: v.reason }));
  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try { await save.mutateAsync(v); toast.success(t('hr.dayCorrected')); onClose(); } catch (e) { setError(applyServerErrors(form, e)); }
  });
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t('hr.correctTitle', { date: fmt(parseISO(day.date), 'EEE d MMM yyyy') })}</DialogTitle>
          <DialogDescription>{t('hr.correctBody')}</DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={onSubmit} className="space-y-4" noValidate>
            {error && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}
            <div className="grid grid-cols-2 gap-4">
              <FormField control={form.control} name="clockIn" render={({ field }) => (
                <FormItem><FormLabel>{t('hr.inTime')}</FormLabel><FormControl><Input type="time" {...field} /></FormControl><FormMessage /></FormItem>
              )} />
              <FormField control={form.control} name="clockOut" render={({ field }) => (
                <FormItem><FormLabel>{t('hr.outTime')}</FormLabel><FormControl><Input type="time" {...field} /></FormControl><FormMessage /></FormItem>
              )} />
            </div>
            <FormField control={form.control} name="note" render={({ field }) => (
              <FormItem><FormLabel>{t('hr.clockNote')}</FormLabel><FormControl><Input maxLength={300} {...field} /></FormControl><FormMessage /></FormItem>
            )} />
            <FormField control={form.control} name="reason" render={({ field }) => (
              <FormItem><FormLabel>{t('hr.correctReason')}</FormLabel><FormControl><Textarea maxLength={500} {...field} /></FormControl><FormMessage /></FormItem>
            )} />
            <DialogFooter>
              <Button type="button" variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
              <Button type="submit" disabled={form.formState.isSubmitting}>{t(form.formState.isSubmitting ? 'common.saving' : 'common.save')}</Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}

const makeOvertimeSchema = (t: ReturnType<typeof useT>['t']) => z.object({
  workDate: z.string().min(1, t('hr.errOtFuture')).refine((d) => d <= orgToday(), t('hr.errOtFuture')),
  hours: z.coerce.number().int().min(0).max(12),
  minutes: z.coerce.number().int().min(0).max(59),
  reason: z.string().trim().min(1, t('hr.errOtReason')).max(1000),
}).refine((v) => { const m = v.hours * 60 + v.minutes; return m >= 15 && m <= 720; }, { path: ['minutes'], message: t('hr.errOtLength') });
type OvertimeValues = z.input<ReturnType<typeof makeOvertimeSchema>>;

/** Asks for overtime on a past day; it goes to the person's approver like leave. */
export function OvertimeDialog({ org, onClose }: { org: string; onClose: () => void }) {
  const { t } = useT();
  const schema = useMemo(() => makeOvertimeSchema(t), [t]);
  const [error, setError] = useState<string | null>(null);
  const form = useForm<OvertimeValues>({ resolver: zodResolver(schema), defaultValues: { workDate: orgToday(), hours: 1, minutes: 0, reason: '' } });
  const send = useOrgMutation(org, (v: { workDate: string; minutes: number; reason: string }) => attendanceApi.requestOvertime(org, v));
  const onSubmit = form.handleSubmit(async (raw) => {
    const v = schema.parse(raw);
    setError(null);
    try {
      await send.mutateAsync({ workDate: v.workDate, minutes: v.hours * 60 + v.minutes, reason: v.reason });
      toast.success(t('hr.otSent'));
      onClose();
    } catch (e) { setError(applyServerErrors(form, e)); }
  });
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t('hr.requestOvertime')}</DialogTitle>
          <DialogDescription>{t('hr.overtimeBody')}</DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={onSubmit} className="space-y-4" noValidate>
            {error && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}
            <FormField control={form.control} name="workDate" render={({ field }) => (
              <FormItem><FormLabel>{t('hr.otDate')}</FormLabel><FormControl><Input type="date" max={orgToday()} {...field} /></FormControl><FormMessage /></FormItem>
            )} />
            <div className="grid grid-cols-2 gap-4">
              <FormField control={form.control} name="hours" render={({ field }) => (
                <FormItem><FormLabel>{t('hr.otHours')}</FormLabel><FormControl><Input type="number" min={0} max={12} inputMode="numeric" {...field} value={String(field.value ?? '')} /></FormControl><FormMessage /></FormItem>
              )} />
              <FormField control={form.control} name="minutes" render={({ field }) => (
                <FormItem><FormLabel>{t('hr.otMinutes')}</FormLabel><FormControl><Input type="number" min={0} max={59} step={5} inputMode="numeric" {...field} value={String(field.value ?? '')} /></FormControl><FormMessage /></FormItem>
              )} />
            </div>
            <FormField control={form.control} name="reason" render={({ field }) => (
              <FormItem><FormLabel>{t('hr.otReason')}</FormLabel><FormControl><Textarea maxLength={1000} {...field} /></FormControl><FormMessage /></FormItem>
            )} />
            <DialogFooter>
              <Button type="button" variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
              <Button type="submit" disabled={form.formState.isSubmitting}>{t('hr.requestOvertime')}</Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}

/** Overtime requests, newest first; `onWithdraw` adds the button to the person's own waiting requests. */
export function OvertimeList({ items, showPerson, onWithdraw }: { items: Overtime[]; showPerson?: boolean; onWithdraw?: (o: Overtime) => void }) {
  const { t } = useT();
  return (
    <ul className="divide-y rounded-lg border bg-card">
      {items.map((o) => (
        <li key={o.id} className="flex flex-wrap items-start gap-3 px-4 py-3">
          {showPerson && <PersonAvatar person={o.person} className="mt-0.5 size-7" />}
          <div className="min-w-0 flex-1">
            <p className="text-sm font-medium">
              {showPerson && <>{o.person.name} · </>}{fmt(parseISO(o.workDate), 'EEE d MMM yyyy')} · <span className="tabular">{duration(o.minutes)}</span>
            </p>
            {o.reason && <p className="break-words text-sm text-muted-foreground">{o.reason}</p>}
          </div>
          <div className="flex items-center gap-2">
            <ApprovalBadge status={o.status === 'PENDING' ? (o.approvalStatus ?? 'PENDING') : o.status} />
            {onWithdraw && o.canWithdraw && <Button variant="outline" size="sm" onClick={() => onWithdraw(o)}>{t('hr.withdraw')}</Button>}
          </div>
        </li>
      ))}
    </ul>
  );
}

const DAYS = [1, 2, 3, 4, 5, 6, 7] as const;

const makeScheduleSchema = (t: ReturnType<typeof useT>['t']) => z.object({
  name: z.string().trim().min(1, t('hr.errScheduleName')).max(60),
  startTime: z.string().min(1),
  endTime: z.string().min(1),
  breakMinutes: z.coerce.number().int().min(0).max(240),
  graceMinutes: z.coerce.number().int().min(0).max(120),
  workDays: z.array(z.number()).min(1, t('hr.errDays')),
  makeDefault: z.boolean(),
}).refine((v) => v.endTime > v.startTime, { path: ['endTime'], message: t('hr.errEndBeforeStartTime') });
type ScheduleValues = z.input<ReturnType<typeof makeScheduleSchema>>;

/** HR managers: creates or edits a work schedule. */
export function ScheduleDialog({ org, schedule, onClose }: { org: string; schedule: WorkSchedule | 'new'; onClose: () => void }) {
  const { t } = useT();
  const schema = useMemo(() => makeScheduleSchema(t), [t]);
  const [error, setError] = useState<string | null>(null);
  const s = schedule === 'new' ? null : schedule;
  const form = useForm<ScheduleValues>({
    resolver: zodResolver(schema),
    defaultValues: s
      ? { name: s.name, startTime: hhmm(s.startTime), endTime: hhmm(s.endTime), breakMinutes: s.breakMinutes, graceMinutes: s.graceMinutes, workDays: s.workDays, makeDefault: s.isDefault }
      : { name: '', startTime: '08:00', endTime: '17:00', breakMinutes: 60, graceMinutes: 10, workDays: [1, 2, 3, 4, 5], makeDefault: false },
  });
  const save = useOrgMutation(org, (v: unknown) => attendanceApi.saveSchedule(org, s?.id ?? null, v));
  const onSubmit = form.handleSubmit(async (raw) => {
    setError(null);
    try { await save.mutateAsync(schema.parse(raw)); toast.success(t('hr.scheduleSaved')); onClose(); } catch (e) { setError(applyServerErrors(form, e)); }
  });
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader><DialogTitle>{s ? t('hr.editSchedule', { name: s.name }) : t('hr.newSchedule')}</DialogTitle></DialogHeader>
        <Form {...form}>
          <form onSubmit={onSubmit} className="space-y-4" noValidate>
            {error && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}
            <FormField control={form.control} name="name" render={({ field }) => (
              <FormItem><FormLabel>{t('hr.scheduleName')}</FormLabel><FormControl><Input maxLength={60} {...field} /></FormControl><FormMessage /></FormItem>
            )} />
            <div className="grid grid-cols-2 gap-4">
              <FormField control={form.control} name="startTime" render={({ field }) => (
                <FormItem><FormLabel>{t('hr.startTime')}</FormLabel><FormControl><Input type="time" {...field} /></FormControl><FormMessage /></FormItem>
              )} />
              <FormField control={form.control} name="endTime" render={({ field }) => (
                <FormItem><FormLabel>{t('hr.endTime')}</FormLabel><FormControl><Input type="time" {...field} /></FormControl><FormMessage /></FormItem>
              )} />
              <FormField control={form.control} name="breakMinutes" render={({ field }) => (
                <FormItem><FormLabel>{t('hr.breakMinutes')}</FormLabel><FormControl><Input type="number" min={0} max={240} inputMode="numeric" {...field} value={String(field.value ?? '')} /></FormControl><FormMessage /></FormItem>
              )} />
              <FormField control={form.control} name="graceMinutes" render={({ field }) => (
                <FormItem><FormLabel>{t('hr.graceMinutes')}</FormLabel><FormControl><Input type="number" min={0} max={120} inputMode="numeric" {...field} value={String(field.value ?? '')} /></FormControl><FormMessage /></FormItem>
              )} />
            </div>
            <FormField control={form.control} name="workDays" render={({ field }) => (
              <FormItem>
                <FormLabel>{t('hr.workDays')}</FormLabel>
                <div className="flex flex-wrap gap-2" role="group" aria-label={t('hr.workDays')}>
                  {DAYS.map((d) => {
                    const on = field.value.includes(d);
                    return (
                      <button key={d} type="button" aria-pressed={on} onClick={() => field.onChange(on ? field.value.filter((x) => x !== d) : [...field.value, d].sort())}
                        className={cn('h-9 min-w-12 rounded-md border px-2 text-sm', on ? 'border-primary bg-primary/10 font-medium text-primary' : 'text-muted-foreground hover:bg-muted')}>
                        {t(`hr.weekday_${d}`)}
                      </button>
                    );
                  })}
                </div>
                <FormMessage />
              </FormItem>
            )} />
            <FormField control={form.control} name="makeDefault" render={({ field }) => (
              <FormItem className="flex items-center gap-2">
                <FormControl><Checkbox checked={field.value} disabled={s?.isDefault} onCheckedChange={(x) => field.onChange(x === true)} /></FormControl>
                <FormLabel className="!mt-0 font-normal">{t('hr.makeDefault')}</FormLabel>
              </FormItem>
            )} />
            <DialogFooter>
              <Button type="button" variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
              <Button type="submit" disabled={form.formState.isSubmitting}>{t(form.formState.isSubmitting ? 'common.saving' : 'common.save')}</Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
