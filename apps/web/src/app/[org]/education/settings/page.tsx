'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { Pencil, Plus, Trash2 } from 'lucide-react';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { GradeDialog, SubjectDialog, TermDialog, YearDialog } from '@/components/education/structure-dialogs';
import { ErrorLine, gradeName, num, Optional, Panel, subjectName } from '@/components/education/ui';
import { applyServerErrors } from '@/components/forms/server-errors';
import { PageHeader } from '@/components/common/page-header';
import { ErrorState, ListSkeleton, PermissionDenied } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import {
  eduApi, type EduSettings, type Grade, type Subject, type Term, useEduSettings, useGrades, useSubjects, useYears, type Year,
} from '@/lib/api/education';
import { useOrgMutation } from '@/lib/api/queries';
import { day } from '@/lib/format';
import { useOrg } from '@/lib/org-context';
import { cn } from '@/lib/utils';

type T = ReturnType<typeof useT>['t'];
const opt = (v: string) => (v.trim() ? v.trim() : null);

const schoolSchema = (t: T) => z.object({
  schoolName: z.string().max(200),
  principalName: z.string().max(120),
  address: z.string().max(500),
  phone: z.string().refine((v) => /^$|^[0-9+()\-. ]{6,30}$/.test(v.trim()), t('education.errPhone')),
  passMark: z.string().refine((v) => /^\d{1,3}(\.\d{1,2})?$/.test(v.trim()) && Number(v) <= 100, t('education.errPassMark')),
});
type SchoolValues = z.infer<ReturnType<typeof schoolSchema>>;

function SchoolForm({ org, settings }: { org: string; settings: EduSettings }) {
  const { t } = useT();
  const schema = useMemo(() => schoolSchema(t), [t]);
  const [error, setError] = useState<string | null>(null);
  const form = useForm<SchoolValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      schoolName: settings.schoolName ?? '', principalName: settings.principalName ?? '', address: settings.address ?? '',
      phone: settings.phone ?? '', passMark: num(settings.passMark),
    },
  });
  const save = useOrgMutation(org, (v: SchoolValues) => eduApi.saveSettings(org, {
    schoolName: opt(v.schoolName), principalName: opt(v.principalName), address: opt(v.address), phone: opt(v.phone), passMark: v.passMark.trim(),
  }));
  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try { await save.mutateAsync(v); toast.success(t('education.settingsSaved')); } catch (e) { setError(applyServerErrors(form, e)); }
  });
  return (
    <Form {...form}>
      <form onSubmit={onSubmit} className="space-y-4 rounded-lg border bg-card p-4 sm:p-6" noValidate>
        <ErrorLine error={error} />
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField control={form.control} name="schoolName" render={({ field }) => (
            <FormItem><FormLabel>{t('education.schoolName')} <Optional /></FormLabel><FormControl><Input placeholder={settings.orgName} {...field} /></FormControl><FormMessage /></FormItem>
          )} />
          <FormField control={form.control} name="principalName" render={({ field }) => (
            <FormItem><FormLabel>{t('education.principal')} <Optional /></FormLabel><FormControl><Input {...field} /></FormControl><FormMessage /></FormItem>
          )} />
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField control={form.control} name="address" render={({ field }) => (
            <FormItem><FormLabel>{t('education.address')} <Optional /></FormLabel><FormControl><Input {...field} /></FormControl><FormMessage /></FormItem>
          )} />
          <FormField control={form.control} name="phone" render={({ field }) => (
            <FormItem><FormLabel>{t('education.phone')} <Optional /></FormLabel><FormControl><Input type="tel" {...field} /></FormControl><FormMessage /></FormItem>
          )} />
        </div>
        <FormField control={form.control} name="passMark" render={({ field }) => (
          <FormItem><FormLabel>{t('education.passMarkPct')}</FormLabel><FormControl><Input inputMode="decimal" className="w-28" {...field} /></FormControl>
            <FormDescription>{t('education.passMarkHint')}</FormDescription><FormMessage /></FormItem>
        )} />
        <div className="flex justify-end"><Button type="submit" disabled={form.formState.isSubmitting}>{form.formState.isSubmitting ? t('common.saving') : t('common.save')}</Button></div>
      </form>
    </Form>
  );
}

type Open = { kind: 'year'; year?: Year } | { kind: 'term'; year: Year; term?: Term } | { kind: 'grade'; grade?: Grade } | { kind: 'subject'; subject?: Subject } | null;

/** Academic structure and school details (admins): years with their terms, grade levels, subjects, report-card details and the pass mark. */
export default function EducationSettings() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const settings = useEduSettings(slug);
  const years = useYears(slug);
  const grades = useGrades(slug);
  const subjects = useSubjects(slug);
  const [open, setOpen] = useState<Open>(null);
  const removeTerm = useOrgMutation(slug, (id: string) => eduApi.deleteTerm(slug, id));
  if (!can('education.manage')) return <PermissionDenied />;
  const failed = settings.error ?? years.error ?? grades.error ?? subjects.error;

  return (
    <>
      <PageHeader title={t('education.settingsTitle')} subtitle={t('education.settingsSubtitle')} />
      {failed ? <ErrorState error={failed} retry={() => { settings.refetch(); years.refetch(); grades.refetch(); subjects.refetch(); }} />
        : settings.isPending || years.isPending || grades.isPending || subjects.isPending ? <ListSkeleton rows={6} /> : (
          <div className="max-w-4xl space-y-10">
            <Panel id="yr-h" title={t('education.academicYears')} action={<Button size="sm" variant="outline" onClick={() => setOpen({ kind: 'year' })}><Plus className="size-4" />{t('education.newYear')}</Button>}>
              {years.data!.length === 0 ? <p className="rounded-lg border border-dashed px-4 py-6 text-center text-sm text-muted-foreground">{t('education.noYearBody')}</p> : (
                <ul className="space-y-3">
                  {years.data!.map((y) => (
                    <li key={y.id} className={cn('rounded-lg border bg-card p-4', y.current && 'border-primary')}>
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <div>
                          <p className="font-medium">{y.name} {y.current && <span className="ml-1 rounded-full bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary">{t('education.current')}</span>}</p>
                          <p className="text-sm text-muted-foreground">{day(y.startsOn)} – {day(y.endsOn)}</p>
                        </div>
                        <div className="flex gap-1">
                          <Button size="sm" variant="ghost" onClick={() => setOpen({ kind: 'term', year: y })}><Plus className="size-4" />{t('education.newTerm')}</Button>
                          <Button size="icon-sm" variant="ghost" aria-label={t('education.editName', { name: y.name })} onClick={() => setOpen({ kind: 'year', year: y })}><Pencil className="size-4" /></Button>
                        </div>
                      </div>
                      {y.terms.length === 0 ? <p className="mt-2 text-sm text-muted-foreground">{t('education.noTermsBody')}</p> : (
                        <ul className="mt-3 divide-y rounded-md border">
                          {y.terms.map((x) => (
                            <li key={x.id} className="flex items-center justify-between gap-2 px-3 py-2 text-sm">
                              <span><span className="font-medium">{x.name}</span> <span className="text-muted-foreground">· {day(x.startsOn)} – {day(x.endsOn)}</span></span>
                              <span className="flex gap-1">
                                <Button size="icon-sm" variant="ghost" aria-label={t('education.editName', { name: x.name })} onClick={() => setOpen({ kind: 'term', year: y, term: x })}><Pencil className="size-4" /></Button>
                                <Button size="icon-sm" variant="ghost" aria-label={t('education.deleteName', { name: x.name })}
                                  onClick={async () => { try { await removeTerm.mutateAsync(x.id); toast.success(t('education.termDeleted')); } catch (e) { toast.error(errorMessage(e)); } }}>
                                  <Trash2 className="size-4" /></Button>
                              </span>
                            </li>
                          ))}
                        </ul>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </Panel>

            <div className="grid gap-10 md:grid-cols-2">
              <Panel id="gr-h" title={t('education.gradeLevels')} action={<Button size="sm" variant="outline" onClick={() => setOpen({ kind: 'grade' })}><Plus className="size-4" />{t('education.newGrade')}</Button>}>
                <ul className="divide-y rounded-lg border bg-card">
                  {grades.data!.map((g) => (
                    <li key={g.id} className={cn('flex items-center justify-between gap-2 px-4 py-2 text-sm', !g.active && 'opacity-60')}>
                      <span>{gradeName(g)}{!g.active && <span className="ml-2 text-xs text-muted-foreground">{t('education.switchedOff')}</span>}</span>
                      <Button size="icon-sm" variant="ghost" aria-label={t('education.editName', { name: g.nameEn })} onClick={() => setOpen({ kind: 'grade', grade: g })}><Pencil className="size-4" /></Button>
                    </li>
                  ))}
                </ul>
              </Panel>
              <Panel id="sb-h" title={t('education.subjects')} action={<Button size="sm" variant="outline" onClick={() => setOpen({ kind: 'subject' })}><Plus className="size-4" />{t('education.newSubject')}</Button>}>
                <ul className="divide-y rounded-lg border bg-card">
                  {subjects.data!.map((s) => (
                    <li key={s.id} className={cn('flex items-center justify-between gap-2 px-4 py-2 text-sm', !s.active && 'opacity-60')}>
                      <span><span className="mr-2 font-mono text-xs text-muted-foreground">{s.code}</span>{subjectName(s)}</span>
                      <Button size="icon-sm" variant="ghost" aria-label={t('education.editName', { name: s.nameEn })} onClick={() => setOpen({ kind: 'subject', subject: s })}><Pencil className="size-4" /></Button>
                    </li>
                  ))}
                </ul>
              </Panel>
            </div>

            <Panel id="sc-h" title={t('education.schoolDetails')}>
              <SchoolForm org={slug} settings={settings.data!} />
            </Panel>
          </div>
        )}
      {open?.kind === 'year' && <YearDialog org={slug} year={open.year} onClose={() => setOpen(null)} />}
      {open?.kind === 'term' && <TermDialog org={slug} year={open.year} term={open.term} onClose={() => setOpen(null)} />}
      {open?.kind === 'grade' && <GradeDialog org={slug} grade={open.grade} next={(grades.data?.length ?? 0) + 1} onClose={() => setOpen(null)} />}
      {open?.kind === 'subject' && <SubjectDialog org={slug} subject={open.subject} onClose={() => setOpen(null)} />}
    </>
  );
}
