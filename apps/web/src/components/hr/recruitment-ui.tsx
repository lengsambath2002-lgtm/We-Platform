'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { Star } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { applyServerErrors } from '@/components/forms/server-errors';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { currentLocale, tr } from '@/i18n/core';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { EMPLOYMENT_TYPES, usePeople } from '@/lib/api/hr';
import {
  type CandidateDetail, type Job, type Recommendation, RECOMMENDATIONS, recruitmentApi, SOURCES, type Stage,
} from '@/lib/api/recruitment';
import { useOrgMutation } from '@/lib/api/queries';
import { cn } from '@/lib/utils';

import { ORG_ZONE, orgToday } from './attendance-ui';

const NONE = 'none';
const alert = (msg: string | null) => msg && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{msg}</p>;

/** "Tue 4 Nov, 09:30" for an instant, in the organisation's zone. */
export const orgDateTime = (iso: string) => new Intl.DateTimeFormat(currentLocale() === 'km' ? 'km-KH' : 'en-GB', {
  timeZone: ORG_ZONE, weekday: 'short', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
}).format(new Date(iso));

const STAGE_TONE: Record<Stage, string> = {
  NEW: 'bg-muted text-foreground',
  SCREENING: 'bg-sky-50 text-sky-700 dark:bg-sky-950 dark:text-sky-300',
  INTERVIEW: 'bg-violet-50 text-violet-700 dark:bg-violet-950 dark:text-violet-300',
  OFFER: 'bg-amber-50 text-amber-800 dark:bg-amber-950 dark:text-amber-300',
  HIRED: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300',
  REJECTED: 'bg-destructive/10 text-destructive',
};

export function StageBadge({ stage }: { stage: Stage }) {
  return <span className={cn('inline-flex items-center whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-medium', STAGE_TONE[stage])}>{tr(`hr.stage_${stage}`)}</span>;
}

export function Rating({ value }: { value: number | null }) {
  if (!value) return null;
  return (
    <span className="inline-flex items-center gap-0.5" aria-label={tr('hr.ratingOf', { n: value })}>
      {[1, 2, 3, 4, 5].map((n) => <Star key={n} aria-hidden className={cn('size-3', n <= value ? 'fill-amber-400 text-amber-400' : 'text-muted-foreground/40')} />)}
    </span>
  );
}

const makeJobSchema = (t: ReturnType<typeof useT>['t']) => z.object({
  title: z.string().trim().min(1, t('hr.errJobTitle')).max(120),
  department: z.string().max(80),
  employmentType: z.string(),
  location: z.string().max(120),
  description: z.string().max(10000),
  openings: z.string().regex(/^[1-9]\d{0,2}$/, t('hr.errOpenings')),
  hiringManagerId: z.string(),
  status: z.enum(['OPEN', 'ON_HOLD', 'CLOSED']),
});

/** HR managers: creates or edits an opening. */
export function JobDialog({ org, job, onClose }: { org: string; job: Job | 'new'; onClose: () => void }) {
  const { t } = useT();
  const router = useRouter();
  const schema = useMemo(() => makeJobSchema(t), [t]);
  const people = usePeople(org);
  const j = job === 'new' ? null : job;
  const [err, setErr] = useState<string | null>(null);
  const form = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: {
      title: j?.title ?? '', department: j?.department ?? '', employmentType: j?.employmentType ?? NONE, location: j?.location ?? '',
      description: j?.description ?? '', openings: String(j?.openings ?? 1), hiringManagerId: j?.hiringManager?.id ?? NONE, status: j?.status ?? 'OPEN',
    },
  });
  const save = useOrgMutation(org, (v: unknown) => recruitmentApi.saveJob(org, j?.id ?? null, v));
  const onSubmit = form.handleSubmit(async (v) => {
    setErr(null);
    try {
      const saved = await save.mutateAsync({
        ...v, openings: Number(v.openings), employmentType: v.employmentType === NONE ? null : v.employmentType,
        hiringManagerId: v.hiringManagerId === NONE ? null : v.hiringManagerId,
        department: v.department || null, location: v.location || null, description: v.description || null,
      });
      toast.success(t('hr.jobSaved'));
      onClose();
      if (!j) router.push(`/${org}/hr/recruitment/jobs/${saved.id}`);
    } catch (e) { setErr(applyServerErrors(form, e)); }
  });
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-lg">
        <DialogHeader><DialogTitle>{j ? t('hr.editJob') : t('hr.newJob')}</DialogTitle></DialogHeader>
        <Form {...form}>
          <form onSubmit={onSubmit} className="space-y-4" noValidate>
            {alert(err)}
            <FormField control={form.control} name="title" render={({ field }) => (
              <FormItem><FormLabel>{t('hr.jobTitle')}</FormLabel><FormControl><Input maxLength={120} {...field} /></FormControl><FormMessage /></FormItem>
            )} />
            <div className="grid grid-cols-2 gap-4">
              <FormField control={form.control} name="department" render={({ field }) => (
                <FormItem><FormLabel>{t('hr.department')}</FormLabel><FormControl><Input maxLength={80} {...field} /></FormControl><FormMessage /></FormItem>
              )} />
              <FormField control={form.control} name="location" render={({ field }) => (
                <FormItem><FormLabel>{t('hr.jobLocation')}</FormLabel><FormControl><Input maxLength={120} {...field} /></FormControl><FormMessage /></FormItem>
              )} />
              <FormField control={form.control} name="employmentType" render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('hr.employmentType')}</FormLabel>
                  <Select value={field.value} onValueChange={field.onChange}>
                    <FormControl><SelectTrigger className="w-full"><SelectValue /></SelectTrigger></FormControl>
                    <SelectContent><SelectItem value={NONE}>{t('common.none')}</SelectItem>{EMPLOYMENT_TYPES.map((x) => <SelectItem key={x} value={x}>{t(`hr.emp_${x}`)}</SelectItem>)}</SelectContent>
                  </Select>
                </FormItem>
              )} />
              <FormField control={form.control} name="openings" render={({ field }) => (
                <FormItem><FormLabel>{t('hr.openings')}</FormLabel><FormControl><Input type="number" min={1} max={500} inputMode="numeric" {...field} /></FormControl><FormMessage /></FormItem>
              )} />
            </div>
            <FormField control={form.control} name="hiringManagerId" render={({ field }) => (
              <FormItem>
                <FormLabel>{t('hr.hiringManager')}</FormLabel>
                <Select value={field.value} onValueChange={field.onChange}>
                  <FormControl><SelectTrigger className="w-full"><SelectValue /></SelectTrigger></FormControl>
                  <SelectContent><SelectItem value={NONE}>{t('hr.noHiringManager')}</SelectItem>{(people.data ?? []).map((p) => <SelectItem key={p.person.id} value={p.person.id}>{p.person.name}</SelectItem>)}</SelectContent>
                </Select>
                <FormDescription>{t('hr.hiringManagerHint')}</FormDescription>
              </FormItem>
            )} />
            {j && (
              <FormField control={form.control} name="status" render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('hr.colStatus')}</FormLabel>
                  <Select value={field.value} onValueChange={field.onChange}>
                    <FormControl><SelectTrigger className="w-full"><SelectValue /></SelectTrigger></FormControl>
                    <SelectContent>{(['OPEN', 'ON_HOLD', 'CLOSED'] as const).map((s) => <SelectItem key={s} value={s}>{t(`hr.job_${s}`)}</SelectItem>)}</SelectContent>
                  </Select>
                </FormItem>
              )} />
            )}
            <FormField control={form.control} name="description" render={({ field }) => (
              <FormItem><FormLabel>{t('hr.jobDescription')}</FormLabel><FormControl><Textarea rows={4} maxLength={10000} {...field} /></FormControl><FormMessage /></FormItem>
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

const makeCandidateSchema = (t: ReturnType<typeof useT>['t']) => z.object({
  name: z.string().trim().min(1, t('hr.errCandidateName')).max(120),
  email: z.union([z.literal(''), z.string().trim().email(t('hr.errEmail'))]),
  phone: z.string().regex(/^$|^[0-9+()\-. ]{6,30}$/, t('hr.errPhone')),
  source: z.enum(SOURCES),
  rating: z.string(),
  notes: z.string().max(10000),
});

/** Adds a candidate to an opening, or edits one. */
export function CandidateDialog({ org, jobId, detail, onClose }: { org: string; jobId: string; detail?: CandidateDetail; onClose: () => void }) {
  const { t } = useT();
  const router = useRouter();
  const schema = useMemo(() => makeCandidateSchema(t), [t]);
  const c = detail?.candidate;
  const [err, setErr] = useState<string | null>(null);
  const form = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: { name: c?.name ?? '', email: c?.email ?? '', phone: c?.phone ?? '', source: c?.source ?? 'REFERRAL', rating: c?.rating ? String(c.rating) : NONE, notes: detail?.notes ?? '' },
  });
  const save = useOrgMutation(org, (v: unknown) => (c ? recruitmentApi.updateCandidate(org, c.id, v) : recruitmentApi.addCandidate(org, jobId, v)));
  const onSubmit = form.handleSubmit(async (v) => {
    setErr(null);
    try {
      const d = await save.mutateAsync({ ...v, email: v.email || null, phone: v.phone || null, rating: v.rating === NONE ? null : Number(v.rating), notes: v.notes || null });
      toast.success(t('hr.candidateSaved'));
      onClose();
      if (!c) router.push(`/${org}/hr/recruitment/candidates/${d.candidate.id}`);
    } catch (e) { setErr(applyServerErrors(form, e)); }
  });
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-lg">
        <DialogHeader><DialogTitle>{c ? t('hr.editCandidate') : t('hr.addCandidate')}</DialogTitle></DialogHeader>
        <Form {...form}>
          <form onSubmit={onSubmit} className="space-y-4" noValidate>
            {alert(err)}
            <FormField control={form.control} name="name" render={({ field }) => (
              <FormItem><FormLabel>{t('hr.candidateName')}</FormLabel><FormControl><Input maxLength={120} {...field} /></FormControl><FormMessage /></FormItem>
            )} />
            <div className="grid grid-cols-2 gap-4">
              <FormField control={form.control} name="email" render={({ field }) => (
                <FormItem><FormLabel>{t('hr.candidateEmail')}</FormLabel><FormControl><Input type="email" maxLength={254} {...field} /></FormControl><FormMessage /></FormItem>
              )} />
              <FormField control={form.control} name="phone" render={({ field }) => (
                <FormItem><FormLabel>{t('hr.candidatePhone')}</FormLabel><FormControl><Input type="tel" maxLength={30} {...field} /></FormControl><FormMessage /></FormItem>
              )} />
              <FormField control={form.control} name="source" render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('hr.source')}</FormLabel>
                  <Select value={field.value} onValueChange={field.onChange}>
                    <FormControl><SelectTrigger className="w-full"><SelectValue /></SelectTrigger></FormControl>
                    <SelectContent>{SOURCES.map((s) => <SelectItem key={s} value={s}>{t(`hr.source_${s}`)}</SelectItem>)}</SelectContent>
                  </Select>
                </FormItem>
              )} />
              <FormField control={form.control} name="rating" render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('hr.rating')}</FormLabel>
                  <Select value={field.value} onValueChange={field.onChange}>
                    <FormControl><SelectTrigger className="w-full"><SelectValue /></SelectTrigger></FormControl>
                    <SelectContent><SelectItem value={NONE}>{t('common.none')}</SelectItem>{[5, 4, 3, 2, 1].map((n) => <SelectItem key={n} value={String(n)}>{'★'.repeat(n)}</SelectItem>)}</SelectContent>
                  </Select>
                </FormItem>
              )} />
            </div>
            <FormField control={form.control} name="notes" render={({ field }) => (
              <FormItem><FormLabel>{t('hr.candidateNotes')}</FormLabel><FormControl><Textarea rows={4} maxLength={10000} {...field} /></FormControl><FormMessage /></FormItem>
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

/** Schedules an interview; the time is in the organisation's zone (UTC+7, no daylight saving). */
export function InterviewDialog({ org, candidateId, onClose }: { org: string; candidateId: string; onClose: () => void }) {
  const { t } = useT();
  const people = usePeople(org);
  const [when, setWhen] = useState(`${orgToday()}T09:00`);
  const [minutes, setMinutes] = useState('60');
  const [location, setLocation] = useState('');
  const [panel, setPanel] = useState<string[]>([]);
  const [err, setErr] = useState<string | null>(null);
  const save = useOrgMutation(org, (v: unknown) => recruitmentApi.schedule(org, candidateId, v));
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-lg">
        <DialogHeader><DialogTitle>{t('hr.scheduleInterview')}</DialogTitle><DialogDescription>{t('hr.scheduleInterviewBody')}</DialogDescription></DialogHeader>
        <form className="space-y-4" noValidate onSubmit={async (e) => {
          e.preventDefault();
          setErr(null);
          if (!when) { setErr(t('hr.errWhen')); return; }
          if (panel.length === 0) { setErr(t('hr.errPanel')); return; }
          try {
            await save.mutateAsync({ startsAt: new Date(`${when}:00+07:00`).toISOString(), durationMinutes: Number(minutes), location: location || null, panel });
            toast.success(t('hr.interviewScheduled'));
            onClose();
          } catch (x) { setErr(errorMessage(x)); }
        }}>
          {alert(err)}
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1.5"><Label htmlFor="iv-when">{t('hr.interviewWhen')}</Label><Input id="iv-when" type="datetime-local" value={when} onChange={(e) => setWhen(e.target.value)} /></div>
            <div className="space-y-1.5">
              <Label htmlFor="iv-len">{t('hr.interviewLength')}</Label>
              <Select value={minutes} onValueChange={setMinutes}>
                <SelectTrigger id="iv-len" className="w-full"><SelectValue /></SelectTrigger>
                <SelectContent>{[30, 45, 60, 90, 120].map((m) => <SelectItem key={m} value={String(m)}>{t('hr.mOnly', { m })}</SelectItem>)}</SelectContent>
              </Select>
            </div>
          </div>
          <div className="space-y-1.5"><Label htmlFor="iv-where">{t('hr.interviewWhere')}</Label><Input id="iv-where" maxLength={200} value={location} onChange={(e) => setLocation(e.target.value)} placeholder={t('hr.interviewWhereHint')} /></div>
          <fieldset className="space-y-2">
            <legend className="mb-1 text-sm font-medium">{t('hr.panel')}</legend>
            <div className="max-h-48 space-y-1 overflow-y-auto rounded-md border p-2">
              {(people.data ?? []).map((p) => (
                <label key={p.person.id} className="flex items-center gap-2 rounded px-1 py-1 text-sm hover:bg-muted">
                  <Checkbox checked={panel.includes(p.person.id)} onCheckedChange={(x) => setPanel(x === true ? [...panel, p.person.id] : panel.filter((id) => id !== p.person.id))} />
                  {p.person.name}
                </label>
              ))}
            </div>
          </fieldset>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
            <Button type="submit" disabled={save.isPending}>{t('hr.scheduleInterview')}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

/** A panelist's recommendation and notes. */
export function FeedbackForm({ org, interviewId, current, onDone }: {
  org: string; interviewId: string; current: { recommendation: Recommendation | null; notes: string | null }; onDone?: () => void;
}) {
  const { t } = useT();
  const [rec, setRec] = useState<Recommendation | ''>(current.recommendation ?? '');
  const [notes, setNotes] = useState(current.notes ?? '');
  const [err, setErr] = useState<string | null>(null);
  const save = useOrgMutation(org, (v: unknown) => recruitmentApi.feedback(org, interviewId, v));
  return (
    <form className="space-y-3 rounded-md border bg-muted/30 p-3" noValidate onSubmit={async (e) => {
      e.preventDefault();
      if (!rec) { setErr(t('hr.errRecommendation')); return; }
      setErr(null);
      try { await save.mutateAsync({ recommendation: rec, notes: notes || null }); toast.success(t('hr.feedbackSaved')); onDone?.(); } catch (x) { setErr(errorMessage(x)); }
    }}>
      {alert(err)}
      <div role="radiogroup" aria-label={t('hr.yourRecommendation')} className="flex flex-wrap gap-2">
        {RECOMMENDATIONS.map((r) => (
          <button key={r} type="button" role="radio" aria-checked={rec === r} onClick={() => setRec(r)}
            className={cn('h-8 rounded-md border px-3 text-sm', rec === r ? 'border-primary bg-primary/10 font-medium text-primary' : 'hover:bg-muted')}>
            {t(`hr.rec_${r}`)}
          </button>
        ))}
      </div>
      <Textarea rows={3} maxLength={5000} value={notes} onChange={(e) => setNotes(e.target.value)} aria-label={t('hr.feedbackNotes')} placeholder={t('hr.feedbackNotes')} />
      <Button type="submit" size="sm" disabled={save.isPending}>{t('hr.giveFeedback')}</Button>
    </form>
  );
}

/** Moves a candidate to "Not going ahead", with the reason. */
export function RejectDialog({ org, candidateId, onClose }: { org: string; candidateId: string; onClose: () => void }) {
  const { t } = useT();
  const [reason, setReason] = useState('');
  const [err, setErr] = useState<string | null>(null);
  const move = useOrgMutation(org, (why: string) => recruitmentApi.move(org, candidateId, 'REJECTED', why));
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader><DialogTitle>{t('hr.rejectTitle')}</DialogTitle><DialogDescription>{t('hr.rejectBody')}</DialogDescription></DialogHeader>
        {alert(err)}
        <Label htmlFor="rej-why">{t('hr.rejectReason')}</Label>
        <Textarea id="rej-why" rows={3} maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} />
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button variant="destructive" disabled={move.isPending} onClick={async () => {
            if (!reason.trim()) { setErr(t('hr.errReason')); return; }
            try { await move.mutateAsync(reason.trim()); toast.success(t('hr.candidateRejected')); onClose(); } catch (x) { setErr(errorMessage(x)); }
          }}>{t('hr.reject')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** HR managers: hires a candidate with a start date, optionally inviting their email to the organisation. */
export function HireDialog({ org, detail, onClose }: { org: string; detail: CandidateDetail; onClose: () => void }) {
  const { t } = useT();
  const [start, setStart] = useState('');
  const [invite, setInvite] = useState(!!detail.candidate.email);
  const [err, setErr] = useState<string | null>(null);
  const hire = useOrgMutation(org, () => recruitmentApi.hire(org, detail.candidate.id, start, invite));
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader><DialogTitle>{t('hr.hireTitle', { name: detail.candidate.name })}</DialogTitle><DialogDescription>{t('hr.hireBody')}</DialogDescription></DialogHeader>
        {alert(err)}
        <div className="space-y-1.5"><Label htmlFor="hire-start">{t('hr.startDate')}</Label><Input id="hire-start" type="date" value={start} onChange={(e) => setStart(e.target.value)} /></div>
        <label className="flex items-start gap-2 text-sm">
          <Checkbox checked={invite} disabled={!detail.candidate.email} onCheckedChange={(x) => setInvite(x === true)} className="mt-0.5" />
          <span>{detail.candidate.email ? t('hr.inviteHire', { email: detail.candidate.email }) : t('hr.inviteNeedsEmail')}</span>
        </label>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button disabled={hire.isPending} onClick={async () => {
            if (!start) { setErr(t('hr.errStartDate')); return; }
            try { await hire.mutateAsync(undefined); toast.success(t('hr.hired', { name: detail.candidate.name })); onClose(); } catch (x) { setErr(errorMessage(x)); }
          }}>{t('hr.hire')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
