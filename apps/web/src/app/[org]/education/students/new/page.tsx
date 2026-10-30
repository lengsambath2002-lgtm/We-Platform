'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { Plus, Trash2 } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useMemo, useState } from 'react';
import { useFieldArray, useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { guardianFields, StudentFields } from '@/components/education/people-dialogs';
import { Crumb, ErrorLine, Optional, RELATIONSHIP_LABEL } from '@/components/education/ui';
import { applyServerErrors } from '@/components/forms/server-errors';
import { PageHeader } from '@/components/common/page-header';
import { PermissionDenied } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { useT } from '@/i18n/provider';
import { eduApi, isoToday, type Relationship, RELATIONSHIPS, useClasses } from '@/lib/api/education';
import { useOrgMutation } from '@/lib/api/queries';
import { useOrg } from '@/lib/org-context';

type T = ReturnType<typeof useT>['t'];
const NONE = '__none';
const opt = (v: string) => (v.trim() ? v.trim() : null);

const schema = (t: T) => z.object({
  nameEn: z.string().trim().min(1, t('education.errName')).max(120),
  nameKm: z.string().max(120),
  gender: z.enum(['FEMALE', 'MALE', 'OTHER']),
  dateOfBirth: z.string().min(1, t('education.errDate')),
  admittedOn: z.string().min(1, t('education.errDate')),
  address: z.string().max(500),
  medicalNotes: z.string().max(1000),
  classId: z.string(),
  guardians: z.array(z.object({
    ...guardianFields(t),
    relationship: z.enum(RELATIONSHIPS as [Relationship, ...Relationship[]]),
  }).refine((g) => g.phone.trim() || g.email.trim(), { path: ['phone'], message: t('education.errContact') })).min(1).max(4),
  primary: z.number(),
}).refine((v) => !v.dateOfBirth || !v.admittedOn || v.dateOfBirth < v.admittedOn, { path: ['admittedOn'], message: t('education.errAdmitted') });
type Values = z.infer<ReturnType<typeof schema>>;

const blankGuardian = (relationship: Relationship) => ({ name: '', phone: '', email: '', occupation: '', address: '', relationship });

/** Student registration: the student, their parents or guardians (one is the main contact), and a class of the current year. */
export default function RegisterStudent() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const router = useRouter();
  const s = useMemo(() => schema(t), [t]);
  const [error, setError] = useState<string | null>(null);
  const classes = useClasses(slug);
  const form = useForm<Values>({
    resolver: zodResolver(s),
    defaultValues: {
      nameEn: '', nameKm: '', gender: 'FEMALE', dateOfBirth: '', admittedOn: isoToday(), address: '', medicalNotes: '', classId: NONE,
      guardians: [blankGuardian('MOTHER')], primary: 0,
    },
  });
  const guardians = useFieldArray({ control: form.control, name: 'guardians' });
  const save = useOrgMutation(slug, (v: Values) => eduApi.register(slug, {
    nameEn: v.nameEn.trim(), nameKm: opt(v.nameKm), gender: v.gender, dateOfBirth: v.dateOfBirth, admittedOn: v.admittedOn,
    address: opt(v.address), medicalNotes: opt(v.medicalNotes), classId: v.classId === NONE ? null : v.classId,
    guardians: v.guardians.map((g, i) => ({
      guardian: { name: g.name.trim(), phone: opt(g.phone), email: opt(g.email), occupation: opt(g.occupation), address: opt(g.address) },
      relationship: g.relationship, primary: i === v.primary,
    })),
  }));
  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try {
      const d = await save.mutateAsync(v);
      toast.success(t('education.registered', { number: d.student.number }));
      router.push(`/${slug}/education/students/${d.student.id}`);
    } catch (e) { setError(applyServerErrors(form, e)); }
  });
  if (!can('education.manage')) return <PermissionDenied />;
  const primary = form.watch('primary');

  return (
    <div className="max-w-3xl">
      <Crumb href={`/${slug}/education/students`} label={t('education.studentsTitle')} current={t('education.registerStudent')} />
      <PageHeader title={t('education.registerStudent')} subtitle={t('education.registerBody')} />
      <Form {...form}>
        <form onSubmit={onSubmit} className="space-y-8" noValidate>
          <ErrorLine error={error} />
          <section aria-labelledby="st-h" className="space-y-4 rounded-lg border bg-card p-4 sm:p-6">
            <h2 id="st-h" className="text-base font-semibold">{t('education.studentDetails')}</h2>
            <StudentFields form={form} />
            <FormField control={form.control} name="classId" render={({ field }) => (
              <FormItem>
                <FormLabel>{t('education.class')} <Optional /></FormLabel>
                <Select value={field.value} onValueChange={field.onChange}>
                  <FormControl><SelectTrigger className="w-full sm:w-64"><SelectValue /></SelectTrigger></FormControl>
                  <SelectContent>
                    <SelectItem value={NONE}>{t('education.noClassYet')}</SelectItem>
                    {(classes.data ?? []).map((c) => <SelectItem key={c.id} value={c.id}>{c.name}{c.gradeName ? ` · ${c.gradeName}` : ''}</SelectItem>)}
                  </SelectContent>
                </Select>
                <FormMessage />
              </FormItem>
            )} />
          </section>
          <section aria-labelledby="gd-h" className="space-y-4 rounded-lg border bg-card p-4 sm:p-6">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h2 id="gd-h" className="text-base font-semibold">{t('education.parentsGuardians')}</h2>
              {guardians.fields.length < 4 && (
                <Button type="button" variant="outline" size="sm" onClick={() => guardians.append(blankGuardian('FATHER'))}><Plus className="size-4" />{t('education.addAnother')}</Button>
              )}
            </div>
            {guardians.fields.map((g, i) => (
              <fieldset key={g.id} className="space-y-4 rounded-md border p-4">
                <legend className="px-1 text-sm font-medium">{t('education.guardianN', { n: i + 1 })}</legend>
                <div className="grid gap-4 sm:grid-cols-2">
                  <FormField control={form.control} name={`guardians.${i}.name`} render={({ field }) => (
                    <FormItem><FormLabel>{t('education.guardianName')}</FormLabel><FormControl><Input {...field} /></FormControl><FormMessage /></FormItem>
                  )} />
                  <FormField control={form.control} name={`guardians.${i}.relationship`} render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t('education.relationship')}</FormLabel>
                      <Select value={field.value} onValueChange={field.onChange}>
                        <FormControl><SelectTrigger className="w-full"><SelectValue /></SelectTrigger></FormControl>
                        <SelectContent>{RELATIONSHIPS.map((r) => <SelectItem key={r} value={r}>{RELATIONSHIP_LABEL[r]}</SelectItem>)}</SelectContent>
                      </Select>
                    </FormItem>
                  )} />
                </div>
                <div className="grid gap-4 sm:grid-cols-2">
                  <FormField control={form.control} name={`guardians.${i}.phone`} render={({ field }) => (
                    <FormItem><FormLabel>{t('education.phone')}</FormLabel><FormControl><Input type="tel" inputMode="tel" {...field} /></FormControl><FormMessage /></FormItem>
                  )} />
                  <FormField control={form.control} name={`guardians.${i}.email`} render={({ field }) => (
                    <FormItem><FormLabel>{t('education.email')} <Optional /></FormLabel><FormControl><Input type="email" {...field} /></FormControl><FormMessage /></FormItem>
                  )} />
                </div>
                <div className="grid gap-4 sm:grid-cols-2">
                  <FormField control={form.control} name={`guardians.${i}.occupation`} render={({ field }) => (
                    <FormItem><FormLabel>{t('education.occupation')} <Optional /></FormLabel><FormControl><Input {...field} /></FormControl><FormMessage /></FormItem>
                  )} />
                  <FormField control={form.control} name={`guardians.${i}.address`} render={({ field }) => (
                    <FormItem><FormLabel>{t('education.address')} <Optional /></FormLabel><FormControl><Input {...field} /></FormControl><FormMessage /></FormItem>
                  )} />
                </div>
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <label className="flex items-center gap-2 text-sm">
                    <Checkbox checked={primary === i} onCheckedChange={(c) => c === true && form.setValue('primary', i)} />{t('education.mainContact')}
                  </label>
                  {guardians.fields.length > 1 && (
                    <Button type="button" variant="ghost" size="sm" className="text-destructive"
                      onClick={() => { guardians.remove(i); if (primary >= i && primary > 0) form.setValue('primary', primary - 1); }}>
                      <Trash2 className="size-4" />{t('education.remove')}
                    </Button>
                  )}
                </div>
              </fieldset>
            ))}
          </section>
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => router.back()}>{t('common.cancel')}</Button>
            <Button type="submit" disabled={form.formState.isSubmitting}>{form.formState.isSubmitting ? t('common.saving') : t('education.register')}</Button>
          </div>
        </form>
      </Form>
    </div>
  );
}
