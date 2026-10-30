'use client';

import { Plus, Star, Trash2 } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { tr } from '@/i18n/core';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { type Answer, type Course, type Cycle, type ReviewStatus, developmentApi } from '@/lib/api/development';
import { usePeople } from '@/lib/api/hr';
import { useOrgMutation } from '@/lib/api/queries';
import { cn } from '@/lib/utils';

const alert = (msg: string | null) => msg && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{msg}</p>;

const REVIEW_TONE: Record<ReviewStatus, string> = {
  SELF: 'bg-muted text-foreground',
  MANAGER: 'bg-sky-50 text-sky-700 dark:bg-sky-950 dark:text-sky-300',
  DONE: 'bg-amber-50 text-amber-800 dark:bg-amber-950 dark:text-amber-300',
  ACKNOWLEDGED: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300',
};

export function ReviewBadge({ status }: { status: ReviewStatus }) {
  return <span className={cn('inline-flex items-center whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-medium', REVIEW_TONE[status])}>{tr(`hr.review_${status}`)}</span>;
}

/** Five stars to pick from (a radio group); read-only when `onChange` is absent. */
export function Stars({ value, onChange, label }: { value: number | null; onChange?: (n: number) => void; label: string }) {
  return (
    <div role={onChange ? 'radiogroup' : 'img'} aria-label={onChange ? label : tr('hr.ratingOf', { n: value ?? 0 })} className="inline-flex gap-0.5">
      {[1, 2, 3, 4, 5].map((n) => {
        const star = <Star aria-hidden className={cn('size-5', value && n <= value ? 'fill-amber-400 text-amber-400' : 'text-muted-foreground/40')} />;
        return onChange ? (
          <button key={n} type="button" role="radio" aria-checked={value === n} aria-label={tr('hr.ratingOf', { n })} onClick={() => onChange(n)} className="rounded p-0.5 hover:bg-muted">{star}</button>
        ) : <span key={n}>{star}</span>;
      })}
    </div>
  );
}

/** Ratings and comments for each of a cycle's questions, editable or read-only. */
export function AnswersForm({ questions, answers, onChange }: { questions: string[]; answers: Answer[]; onChange?: (a: Answer[]) => void }) {
  const { t } = useT();
  return (
    <ol className="space-y-4">
      {questions.map((q, i) => {
        const a = answers[i] ?? { rating: null, comment: null };
        const set = (patch: Partial<Answer>) => onChange?.(questions.map((_, j) => (j === i ? { ...a, ...patch } : (answers[j] ?? { rating: null, comment: null }))));
        return (
          <li key={i} className="space-y-1.5">
            <p className="text-sm font-medium">{i + 1}. {q}</p>
            <Stars value={a.rating} onChange={onChange ? (n) => set({ rating: n }) : undefined} label={q} />
            {onChange ? <Textarea rows={2} maxLength={2000} value={a.comment ?? ''} onChange={(e) => set({ comment: e.target.value })} aria-label={t('hr.commentOn', { question: q })} placeholder={t('hr.commentHint')} />
              : a.comment && <p className="whitespace-pre-line break-words text-sm text-muted-foreground">{a.comment}</p>}
          </li>
        );
      })}
    </ol>
  );
}

/** HR managers: creates or edits a review cycle's period, due date and questions (while a draft). */
export function CycleDialog({ org, cycle, onClose }: { org: string; cycle: Cycle | 'new'; onClose: () => void }) {
  const { t } = useT();
  const router = useRouter();
  const c = cycle === 'new' ? null : cycle;
  const year = new Date().getFullYear();
  const [name, setName] = useState(c?.name ?? t('hr.cycleDefaultName', { year }));
  const [start, setStart] = useState(c?.periodStart ?? `${year}-01-01`);
  const [end, setEnd] = useState(c?.periodEnd ?? `${year}-12-31`);
  const [due, setDue] = useState(c?.dueDate ?? '');
  const [questions, setQuestions] = useState<string[]>(c?.questions ?? [t('hr.q1'), t('hr.q2'), t('hr.q3'), t('hr.q4')]);
  const [err, setErr] = useState<string | null>(null);
  const save = useOrgMutation(org, (v: unknown) => developmentApi.saveCycle(org, c?.id ?? null, v));
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-lg">
        <DialogHeader><DialogTitle>{c ? t('hr.editCycle') : t('hr.newCycle')}</DialogTitle><DialogDescription>{t('hr.cycleBody')}</DialogDescription></DialogHeader>
        {alert(err)}
        <div className="space-y-4">
          <div className="space-y-1.5"><Label htmlFor="cy-name">{t('hr.cycleName')}</Label><Input id="cy-name" maxLength={120} value={name} onChange={(e) => setName(e.target.value)} /></div>
          <div className="grid grid-cols-3 gap-3">
            <div className="space-y-1.5"><Label htmlFor="cy-start">{t('hr.periodStart')}</Label><Input id="cy-start" type="date" value={start} onChange={(e) => setStart(e.target.value)} /></div>
            <div className="space-y-1.5"><Label htmlFor="cy-end">{t('hr.periodEnd')}</Label><Input id="cy-end" type="date" value={end} onChange={(e) => setEnd(e.target.value)} /></div>
            <div className="space-y-1.5"><Label htmlFor="cy-due">{t('hr.dueDate')}</Label><Input id="cy-due" type="date" value={due} onChange={(e) => setDue(e.target.value)} /></div>
          </div>
          <fieldset className="space-y-2">
            <legend className="mb-1 text-sm font-medium">{t('hr.questions')}</legend>
            {questions.map((q, i) => (
              <div key={i} className="flex gap-2">
                <Input aria-label={t('hr.questionN', { n: i + 1 })} maxLength={300} value={q} onChange={(e) => setQuestions(questions.map((x, j) => (j === i ? e.target.value : x)))} />
                <Button type="button" variant="ghost" size="icon" className="size-9 shrink-0" aria-label={t('common.remove')} disabled={questions.length <= 1} onClick={() => setQuestions(questions.filter((_, j) => j !== i))}><Trash2 className="size-4" /></Button>
              </div>
            ))}
            <Button type="button" variant="outline" size="sm" disabled={questions.length >= 20} onClick={() => setQuestions([...questions, ''])}><Plus className="size-4" />{t('hr.addQuestion')}</Button>
          </fieldset>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button disabled={save.isPending} onClick={async () => {
            if (!name.trim() || !start || !end || !due) { setErr(t('hr.errCycle')); return; }
            if (questions.some((q) => !q.trim())) { setErr(t('hr.errQuestions')); return; }
            setErr(null);
            try {
              const saved = await save.mutateAsync({ name: name.trim(), periodStart: start, periodEnd: end, dueDate: due, questions: questions.map((q) => q.trim()) });
              toast.success(t('hr.cycleSaved'));
              onClose();
              if (!c) router.push(`/${org}/hr/reviews/cycles/${saved.id}`);
            } catch (e) { setErr(errorMessage(e)); }
          }}>{t('common.save')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** HR managers: creates or edits a course. */
export function CourseDialog({ org, course, onClose }: { org: string; course: Course | 'new'; onClose: () => void }) {
  const { t } = useT();
  const router = useRouter();
  const c = course === 'new' ? null : course;
  const [v, setV] = useState({
    title: c?.title ?? '', description: c?.description ?? '', provider: c?.provider ?? '', location: c?.location ?? '',
    startsOn: c?.startsOn ?? '', endsOn: c?.endsOn ?? '', hours: c?.hours ? String(c.hours) : '', capacity: c?.capacity ? String(c.capacity) : '',
    selfEnrol: c?.selfEnrol ?? true, open: (c?.status ?? 'OPEN') === 'OPEN',
  });
  const [err, setErr] = useState<string | null>(null);
  const save = useOrgMutation(org, (body: unknown) => developmentApi.saveCourse(org, c?.id ?? null, body));
  const text = (key: keyof typeof v, label: string, type = 'text') => (
    <div className="space-y-1.5"><Label htmlFor={`co-${key}`}>{label}</Label><Input id={`co-${key}`} type={type} value={String(v[key])} onChange={(e) => setV({ ...v, [key]: e.target.value })} /></div>
  );
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-lg">
        <DialogHeader><DialogTitle>{c ? t('hr.editCourse') : t('hr.newCourse')}</DialogTitle></DialogHeader>
        {alert(err)}
        <div className="space-y-4">
          {text('title', t('hr.courseTitle'))}
          <div className="grid grid-cols-2 gap-3">
            {text('provider', t('hr.provider'))}
            {text('location', t('hr.jobLocation'))}
            {text('startsOn', t('hr.startsOnLabel'), 'date')}
            {text('endsOn', t('hr.endsOnLabel'), 'date')}
            {text('hours', t('hr.hours'), 'number')}
            {text('capacity', t('hr.capacity'), 'number')}
          </div>
          <div className="space-y-1.5"><Label htmlFor="co-desc">{t('hr.jobDescription')}</Label><Textarea id="co-desc" rows={3} maxLength={5000} value={v.description} onChange={(e) => setV({ ...v, description: e.target.value })} /></div>
          <label className="flex items-center gap-2 text-sm"><Checkbox checked={v.selfEnrol} onCheckedChange={(x) => setV({ ...v, selfEnrol: x === true })} />{t('hr.selfEnrol')}</label>
          {c && <label className="flex items-center gap-2 text-sm"><Checkbox checked={v.open} onCheckedChange={(x) => setV({ ...v, open: x === true })} />{t('hr.courseOpen')}</label>}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button disabled={save.isPending} onClick={async () => {
            if (!v.title.trim()) { setErr(t('hr.errCourseTitle')); return; }
            setErr(null);
            try {
              const saved = await save.mutateAsync({
                title: v.title.trim(), description: v.description || null, provider: v.provider || null, location: v.location || null,
                startsOn: v.startsOn || null, endsOn: v.endsOn || null, hours: v.hours ? Number(v.hours) : null, capacity: v.capacity ? Number(v.capacity) : null,
                selfEnrol: v.selfEnrol, status: v.open ? 'OPEN' : 'CLOSED',
              });
              toast.success(t('hr.courseSaved'));
              onClose();
              if (!c) router.push(`/${org}/hr/training/${saved.course.id}`);
            } catch (e) { setErr(errorMessage(e)); }
          }}>{t('common.save')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** HR managers: enrols people on a course. */
export function EnrolDialog({ org, courseId, already, onClose }: { org: string; courseId: string; already: string[]; onClose: () => void }) {
  const { t } = useT();
  const people = usePeople(org);
  const [chosen, setChosen] = useState<string[]>([]);
  const [err, setErr] = useState<string | null>(null);
  const enrol = useOrgMutation(org, (ids: string[]) => developmentApi.enrol(org, courseId, ids));
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader><DialogTitle>{t('hr.enrolPeople')}</DialogTitle></DialogHeader>
        {alert(err)}
        <div className="max-h-72 space-y-1 overflow-y-auto rounded-md border p-2">
          {(people.data ?? []).filter((p) => !already.includes(p.person.id)).map((p) => (
            <label key={p.person.id} className="flex items-center gap-2 rounded px-1 py-1 text-sm hover:bg-muted">
              <Checkbox checked={chosen.includes(p.person.id)} onCheckedChange={(x) => setChosen(x === true ? [...chosen, p.person.id] : chosen.filter((id) => id !== p.person.id))} />
              {p.person.name}
            </label>
          ))}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button disabled={enrol.isPending || chosen.length === 0} onClick={async () => {
            try { await enrol.mutateAsync(chosen); toast.success(t('hr.enrolled')); onClose(); } catch (e) { setErr(errorMessage(e)); }
          }}>{t('hr.enrol')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
