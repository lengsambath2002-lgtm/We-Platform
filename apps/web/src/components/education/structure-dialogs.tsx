'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { applyServerErrors } from '@/components/forms/server-errors';
import { Checkbox } from '@/components/ui/checkbox';
import { FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { useT } from '@/i18n/provider';
import { eduApi, type Grade, type Subject, type Term, type Year } from '@/lib/api/education';
import { useOrgMutation } from '@/lib/api/queries';

import { FormDialog } from './form-dialog';
import { Optional } from './ui';

type T = ReturnType<typeof useT>['t'];
const opt = (v: string) => (v.trim() ? v.trim() : null);

// ── Academic year ─────────────────────────────────────────────────────────────────

const yearSchema = (t: T) => z.object({
  name: z.string().trim().min(1, t('education.errName')).max(40),
  startsOn: z.string().min(1, t('education.errDate')),
  endsOn: z.string().min(1, t('education.errDate')),
  current: z.boolean(),
}).refine((v) => !v.startsOn || !v.endsOn || v.endsOn > v.startsOn, { path: ['endsOn'], message: t('education.errEndBeforeStart') });
type YearValues = z.infer<ReturnType<typeof yearSchema>>;

export function YearDialog({ org, year, onClose }: { org: string; year?: Year; onClose: () => void }) {
  const { t } = useT();
  const schema = useMemo(() => yearSchema(t), [t]);
  const [error, setError] = useState<string | null>(null);
  const form = useForm<YearValues>({
    resolver: zodResolver(schema),
    defaultValues: { name: year?.name ?? '', startsOn: year?.startsOn ?? '', endsOn: year?.endsOn ?? '', current: year?.current ?? true },
  });
  const save = useOrgMutation(org, (v: YearValues) => eduApi.saveYear(org, year?.id ?? null, { ...v, name: v.name.trim() }));
  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try { await save.mutateAsync(v); toast.success(t('education.yearSaved')); onClose(); } catch (e) { setError(applyServerErrors(form, e)); }
  });
  return (
    <FormDialog open onOpenChange={(o) => !o && onClose()} title={year ? t('education.editYear') : t('education.newYear')}
      description={t('education.yearBody')} form={form} onSubmit={onSubmit} error={error}>
      <FormField control={form.control} name="name" render={({ field }) => (
        <FormItem><FormLabel>{t('education.yearName')}</FormLabel><FormControl><Input autoFocus placeholder="2026–2027" {...field} /></FormControl><FormMessage /></FormItem>
      )} />
      <div className="grid grid-cols-2 gap-4">
        <FormField control={form.control} name="startsOn" render={({ field }) => (
          <FormItem><FormLabel>{t('education.startsOn')}</FormLabel><FormControl><Input type="date" {...field} /></FormControl><FormMessage /></FormItem>
        )} />
        <FormField control={form.control} name="endsOn" render={({ field }) => (
          <FormItem><FormLabel>{t('education.endsOn')}</FormLabel><FormControl><Input type="date" {...field} /></FormControl><FormMessage /></FormItem>
        )} />
      </div>
      <FormField control={form.control} name="current" render={({ field }) => (
        <FormItem className="flex items-start gap-2 space-y-0">
          <FormControl><Checkbox checked={field.value} onCheckedChange={(c) => field.onChange(c === true)} disabled={year?.current} /></FormControl>
          <div><FormLabel>{t('education.currentYear')}</FormLabel><FormDescription>{t('education.currentYearHint')}</FormDescription></div>
        </FormItem>
      )} />
    </FormDialog>
  );
}

// ── Term ──────────────────────────────────────────────────────────────────────────

const termSchema = (t: T) => z.object({
  name: z.string().trim().min(1, t('education.errName')).max(40),
  startsOn: z.string().min(1, t('education.errDate')),
  endsOn: z.string().min(1, t('education.errDate')),
}).refine((v) => !v.startsOn || !v.endsOn || v.endsOn >= v.startsOn, { path: ['endsOn'], message: t('education.errEndBeforeStart') });
type TermValues = z.infer<ReturnType<typeof termSchema>>;

export function TermDialog({ org, year, term, onClose }: { org: string; year: Year; term?: Term; onClose: () => void }) {
  const { t } = useT();
  const schema = useMemo(() => termSchema(t), [t]);
  const [error, setError] = useState<string | null>(null);
  const form = useForm<TermValues>({
    resolver: zodResolver(schema),
    defaultValues: { name: term?.name ?? '', startsOn: term?.startsOn ?? '', endsOn: term?.endsOn ?? '' },
  });
  const save = useOrgMutation(org, (v: TermValues) => eduApi.saveTerm(org, year.id, term?.id ?? null, { ...v, name: v.name.trim() }));
  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try { await save.mutateAsync(v); toast.success(t('education.termSaved')); onClose(); } catch (e) { setError(applyServerErrors(form, e)); }
  });
  return (
    <FormDialog open onOpenChange={(o) => !o && onClose()} title={term ? t('education.editTerm') : t('education.newTerm')}
      description={t('education.termBody', { year: year.name })} form={form} onSubmit={onSubmit} error={error}>
      <FormField control={form.control} name="name" render={({ field }) => (
        <FormItem><FormLabel>{t('education.termName')}</FormLabel><FormControl><Input autoFocus placeholder={t('education.termPlaceholder')} {...field} /></FormControl><FormMessage /></FormItem>
      )} />
      <div className="grid grid-cols-2 gap-4">
        <FormField control={form.control} name="startsOn" render={({ field }) => (
          <FormItem><FormLabel>{t('education.startsOn')}</FormLabel><FormControl><Input type="date" min={year.startsOn} max={year.endsOn} {...field} /></FormControl><FormMessage /></FormItem>
        )} />
        <FormField control={form.control} name="endsOn" render={({ field }) => (
          <FormItem><FormLabel>{t('education.endsOn')}</FormLabel><FormControl><Input type="date" min={year.startsOn} max={year.endsOn} {...field} /></FormControl><FormMessage /></FormItem>
        )} />
      </div>
    </FormDialog>
  );
}

// ── Grade level ───────────────────────────────────────────────────────────────────

const gradeSchema = (t: T) => z.object({
  nameEn: z.string().trim().min(1, t('education.errName')).max(60),
  nameKm: z.string().max(60),
  position: z.string().refine((v) => /^\d{1,2}$/.test(v.trim()), t('education.errPosition')),
  active: z.boolean(),
});
type GradeValues = z.infer<ReturnType<typeof gradeSchema>>;

export function GradeDialog({ org, grade, next, onClose }: { org: string; grade?: Grade; next: number; onClose: () => void }) {
  const { t } = useT();
  const schema = useMemo(() => gradeSchema(t), [t]);
  const [error, setError] = useState<string | null>(null);
  const form = useForm<GradeValues>({
    resolver: zodResolver(schema),
    defaultValues: { nameEn: grade?.nameEn ?? '', nameKm: grade?.nameKm ?? '', position: String(grade?.position ?? next), active: grade?.active ?? true },
  });
  const save = useOrgMutation(org, (v: GradeValues) => eduApi.saveGrade(org, grade?.id ?? null, {
    nameEn: v.nameEn.trim(), nameKm: opt(v.nameKm), position: Number(v.position), active: v.active,
  }));
  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try { await save.mutateAsync(v); toast.success(t('education.gradeSaved')); onClose(); } catch (e) { setError(applyServerErrors(form, e)); }
  });
  return (
    <FormDialog open onOpenChange={(o) => !o && onClose()} title={grade ? t('education.editGrade') : t('education.newGrade')}
      form={form} onSubmit={onSubmit} error={error}>
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField control={form.control} name="nameEn" render={({ field }) => (
          <FormItem><FormLabel>{t('education.nameEn')}</FormLabel><FormControl><Input autoFocus {...field} /></FormControl><FormMessage /></FormItem>
        )} />
        <FormField control={form.control} name="nameKm" render={({ field }) => (
          <FormItem><FormLabel>{t('education.nameKm')} <Optional /></FormLabel><FormControl><Input lang="km" {...field} /></FormControl><FormMessage /></FormItem>
        )} />
      </div>
      <FormField control={form.control} name="position" render={({ field }) => (
        <FormItem><FormLabel>{t('education.position')}</FormLabel><FormControl><Input inputMode="numeric" className="w-24" {...field} /></FormControl>
          <FormDescription>{t('education.positionHint')}</FormDescription><FormMessage /></FormItem>
      )} />
      <FormField control={form.control} name="active" render={({ field }) => (
        <FormItem className="flex items-center gap-2 space-y-0">
          <FormControl><Checkbox checked={field.value} onCheckedChange={(c) => field.onChange(c === true)} /></FormControl>
          <FormLabel>{t('education.inUse')}</FormLabel>
        </FormItem>
      )} />
    </FormDialog>
  );
}

// ── Subject ───────────────────────────────────────────────────────────────────────

const subjectSchema = (t: T) => z.object({
  code: z.string().refine((v) => /^[A-Za-z0-9-]{1,12}$/.test(v.trim()), t('education.errCode')),
  nameEn: z.string().trim().min(1, t('education.errName')).max(80),
  nameKm: z.string().max(80),
  active: z.boolean(),
});
type SubjectValues = z.infer<ReturnType<typeof subjectSchema>>;

export function SubjectDialog({ org, subject, onClose }: { org: string; subject?: Subject; onClose: () => void }) {
  const { t } = useT();
  const schema = useMemo(() => subjectSchema(t), [t]);
  const [error, setError] = useState<string | null>(null);
  const form = useForm<SubjectValues>({
    resolver: zodResolver(schema),
    defaultValues: { code: subject?.code ?? '', nameEn: subject?.nameEn ?? '', nameKm: subject?.nameKm ?? '', active: subject?.active ?? true },
  });
  const save = useOrgMutation(org, (v: SubjectValues) => eduApi.saveSubject(org, subject?.id ?? null, {
    code: v.code.trim().toUpperCase(), nameEn: v.nameEn.trim(), nameKm: opt(v.nameKm), active: v.active,
  }));
  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try { await save.mutateAsync(v); toast.success(t('education.subjectSaved')); onClose(); } catch (e) { setError(applyServerErrors(form, e)); }
  });
  return (
    <FormDialog open onOpenChange={(o) => !o && onClose()} title={subject ? t('education.editSubject') : t('education.newSubject')}
      form={form} onSubmit={onSubmit} error={error}>
      <FormField control={form.control} name="code" render={({ field }) => (
        <FormItem><FormLabel>{t('education.code')}</FormLabel><FormControl><Input autoFocus className="w-32 uppercase" {...field} /></FormControl><FormMessage /></FormItem>
      )} />
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField control={form.control} name="nameEn" render={({ field }) => (
          <FormItem><FormLabel>{t('education.nameEn')}</FormLabel><FormControl><Input {...field} /></FormControl><FormMessage /></FormItem>
        )} />
        <FormField control={form.control} name="nameKm" render={({ field }) => (
          <FormItem><FormLabel>{t('education.nameKm')} <Optional /></FormLabel><FormControl><Input lang="km" {...field} /></FormControl><FormMessage /></FormItem>
        )} />
      </div>
      <FormField control={form.control} name="active" render={({ field }) => (
        <FormItem className="flex items-center gap-2 space-y-0">
          <FormControl><Checkbox checked={field.value} onCheckedChange={(c) => field.onChange(c === true)} /></FormControl>
          <FormLabel>{t('education.inUse')}</FormLabel>
        </FormItem>
      )} />
    </FormDialog>
  );
}
