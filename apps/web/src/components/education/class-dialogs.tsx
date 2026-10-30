'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { applyServerErrors } from '@/components/forms/server-errors';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import {
  type Assessment, ASSESSMENT_KINDS, type AssessmentKind, type ClassSummary, type Course, eduApi, hhmm, isoToday, type PeriodView, type Term,
  useGrades, useStudents, useSubjects, useTeachers, useYears, WEEKDAYS,
} from '@/lib/api/education';
import { useOrgMutation } from '@/lib/api/queries';

import { FormDialog } from './form-dialog';
import { ErrorLine, gradeName, KIND_LABEL, Optional, studentName, subjectName, WEEKDAY_LABEL } from './ui';

type T = ReturnType<typeof useT>['t'];
const NONE = '__none';

// ── Class ─────────────────────────────────────────────────────────────────────────

const classSchema = (t: T) => z.object({
  yearId: z.string().min(1, t('education.errYear')),
  gradeId: z.string().min(1, t('education.errGrade')),
  name: z.string().trim().min(1, t('education.errName')).max(40),
  homeroomTeacherId: z.string(),
  room: z.string().max(40),
  capacity: z.string().refine((v) => !v.trim() || (/^\d{1,3}$/.test(v.trim()) && Number(v) >= 1 && Number(v) <= 200), t('education.errCapacity')),
});
type ClassValues = z.infer<ReturnType<typeof classSchema>>;

export function ClassDialog({ org, cls, yearId, onClose, onSaved }: {
  org: string; cls?: ClassSummary; yearId?: string; onClose: () => void; onSaved?: (id: string) => void;
}) {
  const { t } = useT();
  const schema = useMemo(() => classSchema(t), [t]);
  const [error, setError] = useState<string | null>(null);
  const years = useYears(org);
  const grades = useGrades(org);
  const teachers = useTeachers(org);
  const form = useForm<ClassValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      yearId: cls?.yearId ?? yearId ?? '', gradeId: cls?.gradeId ?? '', name: cls?.name ?? '', homeroomTeacherId: cls?.homeroom?.id ?? NONE,
      room: cls?.room ?? '', capacity: cls?.capacity ? String(cls.capacity) : '',
    },
  });
  const save = useOrgMutation(org, (v: ClassValues) => eduApi.saveClass(org, cls?.id ?? null, {
    yearId: v.yearId, gradeId: v.gradeId, name: v.name.trim(), homeroomTeacherId: v.homeroomTeacherId === NONE ? null : v.homeroomTeacherId,
    room: v.room.trim() || null, capacity: v.capacity.trim() ? Number(v.capacity) : null,
  }));
  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try { const d = await save.mutateAsync(v); toast.success(t('education.classSaved')); onClose(); onSaved?.(d.summary.id); } catch (e) { setError(applyServerErrors(form, e)); }
  });
  return (
    <FormDialog open onOpenChange={(o) => !o && onClose()} title={cls ? t('education.editClass') : t('education.newClass')}
      description={t('education.classBody')} form={form} onSubmit={onSubmit} error={error}>
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField control={form.control} name="yearId" render={({ field }) => (
          <FormItem>
            <FormLabel>{t('education.academicYear')}</FormLabel>
            <Select value={field.value} onValueChange={field.onChange} disabled={!!cls}>
              <FormControl><SelectTrigger className="w-full"><SelectValue placeholder={t('education.chooseYear')} /></SelectTrigger></FormControl>
              <SelectContent>{(years.data ?? []).map((y) => <SelectItem key={y.id} value={y.id}>{y.name}</SelectItem>)}</SelectContent>
            </Select>
            <FormMessage />
          </FormItem>
        )} />
        <FormField control={form.control} name="gradeId" render={({ field }) => (
          <FormItem>
            <FormLabel>{t('education.gradeLevel')}</FormLabel>
            <Select value={field.value} onValueChange={field.onChange}>
              <FormControl><SelectTrigger className="w-full"><SelectValue placeholder={t('education.chooseGrade')} /></SelectTrigger></FormControl>
              <SelectContent>{(grades.data ?? []).filter((g) => g.active).map((g) => <SelectItem key={g.id} value={g.id}>{gradeName(g)}</SelectItem>)}</SelectContent>
            </Select>
            <FormMessage />
          </FormItem>
        )} />
      </div>
      <div className="grid gap-4 sm:grid-cols-3">
        <FormField control={form.control} name="name" render={({ field }) => (
          <FormItem><FormLabel>{t('education.className')}</FormLabel><FormControl><Input placeholder="7A" {...field} /></FormControl><FormMessage /></FormItem>
        )} />
        <FormField control={form.control} name="room" render={({ field }) => (
          <FormItem><FormLabel>{t('education.room')} <Optional /></FormLabel><FormControl><Input {...field} /></FormControl><FormMessage /></FormItem>
        )} />
        <FormField control={form.control} name="capacity" render={({ field }) => (
          <FormItem><FormLabel>{t('education.capacity')} <Optional /></FormLabel><FormControl><Input inputMode="numeric" {...field} /></FormControl><FormMessage /></FormItem>
        )} />
      </div>
      <FormField control={form.control} name="homeroomTeacherId" render={({ field }) => (
        <FormItem>
          <FormLabel>{t('education.homeroomTeacher')}</FormLabel>
          <Select value={field.value} onValueChange={field.onChange}>
            <FormControl><SelectTrigger className="w-full"><SelectValue /></SelectTrigger></FormControl>
            <SelectContent>
              <SelectItem value={NONE}>{t('education.noHomeroom')}</SelectItem>
              {(teachers.data ?? []).filter((x) => x.active).map((x) => <SelectItem key={x.id} value={x.id}>{x.name}</SelectItem>)}
            </SelectContent>
          </Select>
          <FormMessage />
        </FormItem>
      )} />
    </FormDialog>
  );
}

// ── Enrol students ────────────────────────────────────────────────────────────────

/** Adds active students to a class: search, tick, add. Students already in another class this year are refused by the API. */
export function EnrollDialog({ org, classId, className, onClose }: { org: string; classId: string; className: string; onClose: () => void }) {
  const { t } = useT();
  const [q, setQ] = useState('');
  const [picked, setPicked] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const found = useStudents(org, { q: q.trim() || undefined, status: 'ACTIVE', size: 50 });
  const save = useOrgMutation(org, (ids: string[]) => eduApi.enroll(org, classId, ids));
  const list = (found.data?.items ?? []).filter((s) => s.currentClass?.id !== classId);
  const submit = async () => {
    setError(null);
    try { await save.mutateAsync(picked); toast.success(t('education.studentsAdded', { count: picked.length })); onClose(); } catch (e) { setError(errorMessage(e)); }
  };
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{t('education.addStudentsTo', { name: className })}</DialogTitle>
          <DialogDescription>{t('education.addStudentsBody')}</DialogDescription>
        </DialogHeader>
        <ErrorLine error={error} />
        <Input type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder={t('education.searchStudents')} aria-label={t('education.searchStudents')} />
        <ul className="max-h-72 divide-y overflow-y-auto rounded-md border">
          {list.map((s) => (
            <li key={s.id}>
              <label className="flex cursor-pointer items-center gap-3 px-3 py-2 text-sm hover:bg-muted/50">
                <Checkbox checked={picked.includes(s.id)} onCheckedChange={(c) => setPicked((p) => (c === true ? [...p, s.id] : p.filter((x) => x !== s.id)))} />
                <span className="min-w-0 flex-1"><span className="block truncate font-medium">{studentName(s)}</span>
                  <span className="block text-xs text-muted-foreground">{s.number}{s.currentClass ? ` · ${t('education.inClass', { name: s.currentClass.name })}` : ''}</span></span>
              </label>
            </li>
          ))}
          {found.data && list.length === 0 && <li className="px-3 py-3 text-sm text-muted-foreground">{t('education.noStudentsFound')}</li>}
        </ul>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button onClick={submit} disabled={picked.length === 0 || save.isPending}>{t('education.addCount', { count: picked.length })}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

// ── Course ────────────────────────────────────────────────────────────────────────

const courseSchema = (t: T) => z.object({
  subjectId: z.string().min(1, t('education.errSubject')),
  teacherId: z.string().min(1, t('education.errTeacher')),
});
type CourseValues = z.infer<ReturnType<typeof courseSchema>>;

/** Adds a subject to a class with its teacher, or (with `course`) gives the course to another teacher. */
export function CourseDialog({ org, classId, course, taken, onClose }: { org: string; classId: string; course?: Course; taken: string[]; onClose: () => void }) {
  const { t } = useT();
  const schema = useMemo(() => courseSchema(t), [t]);
  const [error, setError] = useState<string | null>(null);
  const subjects = useSubjects(org);
  const teachers = useTeachers(org);
  const form = useForm<CourseValues>({ resolver: zodResolver(schema), defaultValues: { subjectId: course?.subject.id ?? '', teacherId: course?.teacher.id ?? '' } });
  const save = useOrgMutation(org, (v: CourseValues) => (course ? eduApi.changeTeacher(org, course.id, v.teacherId) : eduApi.addCourse(org, classId, v)));
  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try { await save.mutateAsync(v); toast.success(t('education.courseSaved')); onClose(); } catch (e) { setError(applyServerErrors(form, e)); }
  });
  return (
    <FormDialog open onOpenChange={(o) => !o && onClose()} title={course ? t('education.changeTeacher') : t('education.addCourse')}
      description={course ? subjectName(course.subject) : t('education.courseBody')} form={form} onSubmit={onSubmit} error={error}>
      {!course && (
        <FormField control={form.control} name="subjectId" render={({ field }) => (
          <FormItem>
            <FormLabel>{t('education.subject')}</FormLabel>
            <Select value={field.value} onValueChange={field.onChange}>
              <FormControl><SelectTrigger className="w-full"><SelectValue placeholder={t('education.chooseSubject')} /></SelectTrigger></FormControl>
              <SelectContent>{(subjects.data ?? []).filter((s) => s.active && !taken.includes(s.id)).map((s) => <SelectItem key={s.id} value={s.id}>{s.code} · {subjectName(s)}</SelectItem>)}</SelectContent>
            </Select>
            <FormMessage />
          </FormItem>
        )} />
      )}
      <FormField control={form.control} name="teacherId" render={({ field }) => (
        <FormItem>
          <FormLabel>{t('education.teacher')}</FormLabel>
          <Select value={field.value} onValueChange={field.onChange}>
            <FormControl><SelectTrigger className="w-full"><SelectValue placeholder={t('education.chooseTeacher')} /></SelectTrigger></FormControl>
            <SelectContent>{(teachers.data ?? []).filter((x) => x.active).map((x) => <SelectItem key={x.id} value={x.id}>{x.name}</SelectItem>)}</SelectContent>
          </Select>
          <FormMessage />
        </FormItem>
      )} />
    </FormDialog>
  );
}

// ── Timetable period ──────────────────────────────────────────────────────────────

const TIME = /^([01]\d|2[0-3]):[0-5]\d$/;
const periodSchema = (t: T) => z.object({
  courseId: z.string().min(1, t('education.errCourse')),
  weekday: z.string(),
  startsAt: z.string().refine((v) => TIME.test(v), t('education.errTime')),
  endsAt: z.string().refine((v) => TIME.test(v), t('education.errTime')),
  room: z.string().max(40),
}).refine((v) => !TIME.test(v.startsAt) || !TIME.test(v.endsAt) || v.endsAt > v.startsAt, { path: ['endsAt'], message: t('education.errEndBeforeStart') });
type PeriodValues = z.infer<ReturnType<typeof periodSchema>>;

/** A weekly lesson; the API refuses a class, teacher or room that is already booked then. */
export function PeriodDialog({ org, period, courses, weekday, onClose }: {
  org: string; period?: PeriodView; courses: Course[]; weekday?: number; onClose: () => void;
}) {
  const { t } = useT();
  const schema = useMemo(() => periodSchema(t), [t]);
  const [error, setError] = useState<string | null>(null);
  const form = useForm<PeriodValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      courseId: period?.courseId ?? (courses.length === 1 ? courses[0].id : ''), weekday: String(period?.weekday ?? weekday ?? 1),
      startsAt: period ? hhmm(period.startsAt) : '07:00', endsAt: period ? hhmm(period.endsAt) : '08:00', room: period?.room ?? '',
    },
  });
  const save = useOrgMutation(org, (v: PeriodValues) => eduApi.savePeriod(org, period?.id ?? null, {
    courseId: v.courseId, weekday: Number(v.weekday), startsAt: v.startsAt, endsAt: v.endsAt, room: v.room.trim() || null,
  }));
  const remove = useOrgMutation(org, () => eduApi.deletePeriod(org, period!.id));
  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try { await save.mutateAsync(v); toast.success(t('education.lessonSaved')); onClose(); } catch (e) { setError(applyServerErrors(form, e)); }
  });
  return (
    <FormDialog open onOpenChange={(o) => !o && onClose()} title={period ? t('education.editLesson') : t('education.addLesson')}
      description={t('education.lessonBody')} form={form} onSubmit={onSubmit} error={error}>
      <FormField control={form.control} name="courseId" render={({ field }) => (
        <FormItem>
          <FormLabel>{t('education.course')}</FormLabel>
          <Select value={field.value} onValueChange={field.onChange}>
            <FormControl><SelectTrigger className="w-full"><SelectValue placeholder={t('education.chooseCourse')} /></SelectTrigger></FormControl>
            <SelectContent>{courses.map((c) => <SelectItem key={c.id} value={c.id}>{c.classRef.name} · {subjectName(c.subject)} · {c.teacher.name}</SelectItem>)}</SelectContent>
          </Select>
          <FormMessage />
        </FormItem>
      )} />
      <div className="grid grid-cols-3 gap-4">
        <FormField control={form.control} name="weekday" render={({ field }) => (
          <FormItem className="col-span-3 sm:col-span-1">
            <FormLabel>{t('education.weekday')}</FormLabel>
            <Select value={field.value} onValueChange={field.onChange}>
              <FormControl><SelectTrigger className="w-full"><SelectValue /></SelectTrigger></FormControl>
              <SelectContent>{WEEKDAYS.map((d) => <SelectItem key={d} value={String(d)}>{WEEKDAY_LABEL[String(d) as '1']}</SelectItem>)}</SelectContent>
            </Select>
          </FormItem>
        )} />
        <FormField control={form.control} name="startsAt" render={({ field }) => (
          <FormItem><FormLabel>{t('education.from')}</FormLabel><FormControl><Input type="time" step={300} {...field} /></FormControl><FormMessage /></FormItem>
        )} />
        <FormField control={form.control} name="endsAt" render={({ field }) => (
          <FormItem><FormLabel>{t('education.to')}</FormLabel><FormControl><Input type="time" step={300} {...field} /></FormControl><FormMessage /></FormItem>
        )} />
      </div>
      <FormField control={form.control} name="room" render={({ field }) => (
        <FormItem><FormLabel>{t('education.room')} <Optional /></FormLabel><FormControl><Input {...field} /></FormControl><FormMessage /></FormItem>
      )} />
      {period && (
        <Button type="button" variant="ghost" size="sm" className="text-destructive" disabled={remove.isPending}
          onClick={async () => { try { await remove.mutateAsync(undefined); toast.success(t('education.lessonRemoved')); onClose(); } catch (e) { setError(errorMessage(e)); } }}>
          {t('education.removeLesson')}
        </Button>
      )}
    </FormDialog>
  );
}

// ── Assessment ────────────────────────────────────────────────────────────────────

const DEC = /^\d{1,4}(\.\d{1,2})?$/;
const assessmentSchema = (t: T) => z.object({
  courseId: z.string().min(1, t('education.errCourse')),
  termId: z.string().min(1, t('education.errTerm')),
  name: z.string().trim().min(1, t('education.errName')).max(80),
  kind: z.enum(ASSESSMENT_KINDS as [AssessmentKind, ...AssessmentKind[]]),
  heldOn: z.string().min(1, t('education.errDate')),
  maxScore: z.string().refine((v) => DEC.test(v.trim()) && Number(v) > 0 && Number(v) <= 1000, t('education.errMaxScore')),
  weight: z.string().refine((v) => DEC.test(v.trim()) && Number(v) > 0 && Number(v) <= 100, t('education.errWeight')),
});
type AssessmentValues = z.infer<ReturnType<typeof assessmentSchema>>;

export function AssessmentDialog({ org, assessment, courses, terms, courseId, termId, onClose }: {
  org: string; assessment?: Assessment; courses: Course[]; terms: Term[]; courseId?: string; termId?: string; onClose: () => void;
}) {
  const { t } = useT();
  const schema = useMemo(() => assessmentSchema(t), [t]);
  const [error, setError] = useState<string | null>(null);
  const form = useForm<AssessmentValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      courseId: assessment?.course.id ?? courseId ?? (courses.length === 1 ? courses[0].id : ''), termId: assessment?.term.id ?? termId ?? '',
      name: assessment?.name ?? '', kind: assessment?.kind ?? 'EXAM', heldOn: assessment?.heldOn ?? isoToday(),
      maxScore: assessment ? String(assessment.maxScore) : '100', weight: assessment ? String(assessment.weight) : '',
    },
  });
  const save = useOrgMutation(org, (v: AssessmentValues) => eduApi.saveAssessment(org, v.courseId, assessment?.id ?? null, {
    termId: v.termId, name: v.name.trim(), kind: v.kind, heldOn: v.heldOn, maxScore: v.maxScore.trim(), weight: v.weight.trim(),
  }));
  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try { await save.mutateAsync(v); toast.success(t('education.assessmentSaved')); onClose(); } catch (e) { setError(applyServerErrors(form, e)); }
  });
  return (
    <FormDialog open onOpenChange={(o) => !o && onClose()} title={assessment ? t('education.editAssessment') : t('education.newAssessment')}
      description={t('education.assessmentBody')} form={form} onSubmit={onSubmit} error={error}>
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField control={form.control} name="courseId" render={({ field }) => (
          <FormItem>
            <FormLabel>{t('education.course')}</FormLabel>
            <Select value={field.value} onValueChange={field.onChange} disabled={!!assessment}>
              <FormControl><SelectTrigger className="w-full"><SelectValue placeholder={t('education.chooseCourse')} /></SelectTrigger></FormControl>
              <SelectContent>{courses.map((c) => <SelectItem key={c.id} value={c.id}>{c.classRef.name} · {subjectName(c.subject)}</SelectItem>)}</SelectContent>
            </Select>
            <FormMessage />
          </FormItem>
        )} />
        <FormField control={form.control} name="termId" render={({ field }) => (
          <FormItem>
            <FormLabel>{t('education.term')}</FormLabel>
            <Select value={field.value} onValueChange={field.onChange}>
              <FormControl><SelectTrigger className="w-full"><SelectValue placeholder={t('education.chooseTerm')} /></SelectTrigger></FormControl>
              <SelectContent>{terms.map((x) => <SelectItem key={x.id} value={x.id}>{x.name}</SelectItem>)}</SelectContent>
            </Select>
            <FormMessage />
          </FormItem>
        )} />
      </div>
      <div className="grid gap-4 sm:grid-cols-[2fr_1fr]">
        <FormField control={form.control} name="name" render={({ field }) => (
          <FormItem><FormLabel>{t('education.assessmentName')}</FormLabel><FormControl><Input placeholder={t('education.assessmentPlaceholder')} {...field} /></FormControl><FormMessage /></FormItem>
        )} />
        <FormField control={form.control} name="kind" render={({ field }) => (
          <FormItem>
            <FormLabel>{t('education.kind')}</FormLabel>
            <Select value={field.value} onValueChange={field.onChange}>
              <FormControl><SelectTrigger className="w-full"><SelectValue /></SelectTrigger></FormControl>
              <SelectContent>{ASSESSMENT_KINDS.map((k) => <SelectItem key={k} value={k}>{KIND_LABEL[k]}</SelectItem>)}</SelectContent>
            </Select>
          </FormItem>
        )} />
      </div>
      <div className="grid grid-cols-3 gap-4">
        <FormField control={form.control} name="heldOn" render={({ field }) => (
          <FormItem className="col-span-3 sm:col-span-1"><FormLabel>{t('education.heldOn')}</FormLabel><FormControl><Input type="date" {...field} /></FormControl><FormMessage /></FormItem>
        )} />
        <FormField control={form.control} name="maxScore" render={({ field }) => (
          <FormItem className="col-span-3 sm:col-span-1"><FormLabel>{t('education.maxScore')}</FormLabel><FormControl><Input inputMode="decimal" {...field} /></FormControl><FormMessage /></FormItem>
        )} />
        <FormField control={form.control} name="weight" render={({ field }) => (
          <FormItem className="col-span-3 sm:col-span-1"><FormLabel>{t('education.weightPct')}</FormLabel><FormControl><Input inputMode="decimal" {...field} /></FormControl>
            <FormDescription>{t('education.weightHint')}</FormDescription><FormMessage /></FormItem>
        )} />
      </div>
    </FormDialog>
  );
}

/** Small helper so pages can show a class's grade with the class name. */
export const classLabel = (c: Pick<ClassSummary, 'name' | 'gradeName'>) => (c.gradeName ? `${c.name} · ${c.gradeName}` : c.name);
