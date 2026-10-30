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
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { useT } from '@/i18n/provider';
import {
  eduApi, GENDERS, type Guardian, isoToday, type Relationship, RELATIONSHIPS, type StudentView, type Teacher, useGuardians,
} from '@/lib/api/education';
import { useMembers, useOrgMutation } from '@/lib/api/queries';

import { FormDialog } from './form-dialog';
import { GENDER_LABEL, Optional, RELATIONSHIP_LABEL } from './ui';

type T = ReturnType<typeof useT>['t'];
const PHONE = /^$|^[0-9+()\-. ]{6,30}$/;
const opt = (v: string) => (v.trim() ? v.trim() : null);

// ── Teacher ───────────────────────────────────────────────────────────────────────

const teacherSchema = (t: T, adding: boolean) => z.object({
  userId: adding ? z.string().min(1, t('education.errMember')) : z.string(),
  phone: z.string().refine((v) => PHONE.test(v.trim()), t('education.errPhone')),
  specialty: z.string().max(120),
  active: z.boolean(),
});
type TeacherValues = z.infer<ReturnType<typeof teacherSchema>>;

/** Adds a member as a teacher (members who aren't guests and aren't teachers yet), or edits a teacher's profile. */
export function TeacherDialog({ org, teacher, teachers, onClose }: { org: string; teacher?: Teacher; teachers: Teacher[]; onClose: () => void }) {
  const { t } = useT();
  const schema = useMemo(() => teacherSchema(t, !teacher), [t, teacher]);
  const [error, setError] = useState<string | null>(null);
  const members = useMembers(org);
  const taken = new Set(teachers.filter((x) => x.active).map((x) => x.userId));
  const candidates = (members.data ?? []).filter((m) => m.status === 'ACTIVE' && m.role !== 'GUEST' && !taken.has(m.userId));
  const form = useForm<TeacherValues>({
    resolver: zodResolver(schema),
    defaultValues: { userId: teacher?.userId ?? '', phone: teacher?.phone ?? '', specialty: teacher?.specialty ?? '', active: teacher?.active ?? true },
  });
  const save = useOrgMutation(org, (v: TeacherValues) => eduApi.saveTeacher(org, teacher?.id ?? null, {
    userId: teacher ? undefined : v.userId, phone: opt(v.phone), specialty: opt(v.specialty), active: v.active,
  }));
  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try { await save.mutateAsync(v); toast.success(t('education.teacherSaved')); onClose(); } catch (e) { setError(applyServerErrors(form, e)); }
  });
  return (
    <FormDialog open onOpenChange={(o) => !o && onClose()} title={teacher ? t('education.editTeacher', { name: teacher.name }) : t('education.addTeacher')}
      description={teacher ? undefined : t('education.addTeacherBody')} form={form} onSubmit={onSubmit} error={error}>
      {!teacher && (
        <FormField control={form.control} name="userId" render={({ field }) => (
          <FormItem>
            <FormLabel>{t('education.member')}</FormLabel>
            <Select value={field.value} onValueChange={field.onChange}>
              <FormControl><SelectTrigger className="w-full"><SelectValue placeholder={t('education.chooseMember')} /></SelectTrigger></FormControl>
              <SelectContent>
                {candidates.length === 0 && <div className="px-2 py-1.5 text-sm text-muted-foreground">{t('education.noCandidates')}</div>}
                {candidates.map((m) => <SelectItem key={m.userId} value={m.userId}>{m.name} · {m.email}</SelectItem>)}
              </SelectContent>
            </Select>
            <FormMessage />
          </FormItem>
        )} />
      )}
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField control={form.control} name="phone" render={({ field }) => (
          <FormItem><FormLabel>{t('education.phone')} <Optional /></FormLabel><FormControl><Input type="tel" inputMode="tel" {...field} /></FormControl><FormMessage /></FormItem>
        )} />
        <FormField control={form.control} name="specialty" render={({ field }) => (
          <FormItem><FormLabel>{t('education.specialty')} <Optional /></FormLabel><FormControl><Input {...field} /></FormControl><FormMessage /></FormItem>
        )} />
      </div>
      {teacher && (
        <FormField control={form.control} name="active" render={({ field }) => (
          <FormItem className="flex items-start gap-2 space-y-0">
            <FormControl><Checkbox checked={field.value} onCheckedChange={(c) => field.onChange(c === true)} /></FormControl>
            <div><FormLabel>{t('education.teaching')}</FormLabel><FormDescription>{t('education.teachingHint')}</FormDescription></div>
          </FormItem>
        )} />
      )}
    </FormDialog>
  );
}

// ── Guardian ──────────────────────────────────────────────────────────────────────

export const guardianFields = (t: T) => ({
  name: z.string().trim().min(1, t('education.errName')).max(120),
  phone: z.string().refine((v) => PHONE.test(v.trim()), t('education.errPhone')),
  email: z.string().max(160).refine((v) => !v.trim() || z.string().email().safeParse(v.trim()).success, t('education.errEmail')),
  occupation: z.string().max(120),
  address: z.string().max(500),
});
const guardianSchema = (t: T) => z.object(guardianFields(t))
  .refine((v) => v.phone.trim() || v.email.trim(), { path: ['phone'], message: t('education.errContact') });
type GuardianValues = z.infer<ReturnType<typeof guardianSchema>>;

export const guardianBody = (v: { name: string; phone: string; email: string; occupation: string; address: string }) => ({
  name: v.name.trim(), phone: opt(v.phone), email: opt(v.email), occupation: opt(v.occupation), address: opt(v.address),
});

export function GuardianDialog({ org, guardian, onClose }: { org: string; guardian?: Guardian; onClose: () => void }) {
  const { t } = useT();
  const schema = useMemo(() => guardianSchema(t), [t]);
  const [error, setError] = useState<string | null>(null);
  const form = useForm<GuardianValues>({
    resolver: zodResolver(schema),
    defaultValues: { name: guardian?.name ?? '', phone: guardian?.phone ?? '', email: guardian?.email ?? '', occupation: guardian?.occupation ?? '', address: guardian?.address ?? '' },
  });
  const save = useOrgMutation(org, (v: GuardianValues) => eduApi.saveGuardian(org, guardian?.id ?? null, guardianBody(v)));
  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try { await save.mutateAsync(v); toast.success(t('education.guardianSaved')); onClose(); } catch (e) { setError(applyServerErrors(form, e)); }
  });
  return (
    <FormDialog open onOpenChange={(o) => !o && onClose()} title={guardian ? t('education.editGuardian') : t('education.newGuardian')}
      description={t('education.guardianBody')} form={form} onSubmit={onSubmit} error={error}>
      <GuardianFields form={form} prefix="" />
    </FormDialog>
  );
}

/** Name, phone, email, occupation and address of a guardian (field names under `prefix`). */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function GuardianFields({ form, prefix }: { form: any; prefix: string }) {
  const { t } = useT();
  const n = (f: string) => `${prefix}${f}`;
  return (
    <>
      <FormField control={form.control} name={n('name')} render={({ field }) => (
        <FormItem><FormLabel>{t('education.guardianName')}</FormLabel><FormControl><Input {...field} /></FormControl><FormMessage /></FormItem>
      )} />
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField control={form.control} name={n('phone')} render={({ field }) => (
          <FormItem><FormLabel>{t('education.phone')}</FormLabel><FormControl><Input type="tel" inputMode="tel" {...field} /></FormControl><FormMessage /></FormItem>
        )} />
        <FormField control={form.control} name={n('email')} render={({ field }) => (
          <FormItem><FormLabel>{t('education.email')} <Optional /></FormLabel><FormControl><Input type="email" inputMode="email" {...field} /></FormControl><FormMessage /></FormItem>
        )} />
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField control={form.control} name={n('occupation')} render={({ field }) => (
          <FormItem><FormLabel>{t('education.occupation')} <Optional /></FormLabel><FormControl><Input {...field} /></FormControl><FormMessage /></FormItem>
        )} />
        <FormField control={form.control} name={n('address')} render={({ field }) => (
          <FormItem><FormLabel>{t('education.address')} <Optional /></FormLabel><FormControl><Input {...field} /></FormControl><FormMessage /></FormItem>
        )} />
      </div>
    </>
  );
}

// ── Link a guardian to a student ──────────────────────────────────────────────────

const linkSchema = (t: T) => z.object({
  mode: z.enum(['existing', 'new']),
  guardianId: z.string(),
  ...guardianFields(t),
  relationship: z.enum(RELATIONSHIPS as [Relationship, ...Relationship[]]),
  primary: z.boolean(),
}).superRefine((v, ctx) => {
  if (v.mode === 'existing' && !v.guardianId) ctx.addIssue({ code: 'custom', path: ['guardianId'], message: t('education.errGuardian') });
  if (v.mode === 'new' && !v.name.trim()) ctx.addIssue({ code: 'custom', path: ['name'], message: t('education.errName') });
  if (v.mode === 'new' && !v.phone.trim() && !v.email.trim()) ctx.addIssue({ code: 'custom', path: ['phone'], message: t('education.errContact') });
});
type LinkValues = z.infer<ReturnType<typeof linkSchema>>;

/** Adds a parent or guardian to a student: someone already on file, or a new contact. */
export function GuardianLinkDialog({ org, studentId, onClose }: { org: string; studentId: string; onClose: () => void }) {
  const { t } = useT();
  const schema = useMemo(() => linkSchema(t), [t]);
  const [error, setError] = useState<string | null>(null);
  const [q, setQ] = useState('');
  const found = useGuardians(org, { q: q.trim() || undefined, size: 20 });
  const form = useForm<LinkValues>({
    resolver: zodResolver(schema),
    defaultValues: { mode: 'existing', guardianId: '', name: '', phone: '', email: '', occupation: '', address: '', relationship: 'MOTHER', primary: false },
  });
  const mode = form.watch('mode');
  const save = useOrgMutation(org, (v: LinkValues) => eduApi.linkGuardian(org, studentId, {
    guardianId: v.mode === 'existing' ? v.guardianId : null, guardian: v.mode === 'new' ? guardianBody(v) : null,
    relationship: v.relationship, primary: v.primary,
  }));
  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try { await save.mutateAsync(v); toast.success(t('education.guardianLinked')); onClose(); } catch (e) { setError(applyServerErrors(form, e)); }
  });
  return (
    <FormDialog open onOpenChange={(o) => !o && onClose()} title={t('education.addGuardian')} form={form} onSubmit={onSubmit} error={error}>
      <FormField control={form.control} name="mode" render={({ field }) => (
        <FormItem>
          <Select value={field.value} onValueChange={field.onChange}>
            <FormControl><SelectTrigger className="w-full" aria-label={t('education.guardianMode')}><SelectValue /></SelectTrigger></FormControl>
            <SelectContent>
              <SelectItem value="existing">{t('education.existingGuardian')}</SelectItem>
              <SelectItem value="new">{t('education.newGuardian')}</SelectItem>
            </SelectContent>
          </Select>
        </FormItem>
      )} />
      {mode === 'existing' ? (
        <>
          <Input type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder={t('education.searchGuardians')} aria-label={t('education.searchGuardians')} />
          <FormField control={form.control} name="guardianId" render={({ field }) => (
            <FormItem>
              <ul className="max-h-48 divide-y overflow-y-auto rounded-md border" role="listbox" aria-label={t('education.navParents')}>
                {(found.data?.items ?? []).map((g) => (
                  <li key={g.id}>
                    <button type="button" role="option" aria-selected={field.value === g.id} onClick={() => field.onChange(g.id)}
                      className={`flex w-full flex-col items-start px-3 py-2 text-left text-sm hover:bg-muted/50 ${field.value === g.id ? 'bg-primary/10' : ''}`}>
                      <span className="font-medium">{g.name}</span>
                      <span className="text-xs text-muted-foreground">{[g.phone, g.email].filter(Boolean).join(' · ')}</span>
                    </button>
                  </li>
                ))}
                {found.data && found.data.items.length === 0 && <li className="px-3 py-2 text-sm text-muted-foreground">{t('education.noGuardiansFound')}</li>}
              </ul>
              <FormMessage />
            </FormItem>
          )} />
        </>
      ) : <GuardianFields form={form} prefix="" />}
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField control={form.control} name="relationship" render={({ field }) => (
          <FormItem>
            <FormLabel>{t('education.relationship')}</FormLabel>
            <Select value={field.value} onValueChange={field.onChange}>
              <FormControl><SelectTrigger className="w-full"><SelectValue /></SelectTrigger></FormControl>
              <SelectContent>{RELATIONSHIPS.map((r) => <SelectItem key={r} value={r}>{RELATIONSHIP_LABEL[r]}</SelectItem>)}</SelectContent>
            </Select>
          </FormItem>
        )} />
        <FormField control={form.control} name="primary" render={({ field }) => (
          <FormItem className="flex items-center gap-2 space-y-0 sm:pt-7">
            <FormControl><Checkbox checked={field.value} onCheckedChange={(c) => field.onChange(c === true)} /></FormControl>
            <FormLabel>{t('education.mainContact')}</FormLabel>
          </FormItem>
        )} />
      </div>
    </FormDialog>
  );
}

// ── Student details ───────────────────────────────────────────────────────────────

export const studentFields = (t: T) => ({
  nameEn: z.string().trim().min(1, t('education.errName')).max(120),
  nameKm: z.string().max(120),
  gender: z.enum(['FEMALE', 'MALE', 'OTHER']),
  dateOfBirth: z.string().min(1, t('education.errDate')),
  admittedOn: z.string().min(1, t('education.errDate')),
  address: z.string().max(500),
  medicalNotes: z.string().max(1000),
});
const studentSchema = (t: T) => z.object(studentFields(t))
  .refine((v) => !v.dateOfBirth || !v.admittedOn || v.dateOfBirth < v.admittedOn, { path: ['admittedOn'], message: t('education.errAdmitted') });
type StudentValues = z.infer<ReturnType<typeof studentSchema>>;

export const studentBody = (v: StudentValues) => ({
  nameEn: v.nameEn.trim(), nameKm: opt(v.nameKm), gender: v.gender, dateOfBirth: v.dateOfBirth, admittedOn: v.admittedOn,
  address: opt(v.address), medicalNotes: opt(v.medicalNotes),
});

/** The student's own fields (names, gender, dates, address, medical notes). */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function StudentFields({ form }: { form: any }) {
  const { t } = useT();
  return (
    <>
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField control={form.control} name="nameEn" render={({ field }) => (
          <FormItem><FormLabel>{t('education.studentNameEn')}</FormLabel><FormControl><Input autoFocus {...field} /></FormControl><FormMessage /></FormItem>
        )} />
        <FormField control={form.control} name="nameKm" render={({ field }) => (
          <FormItem><FormLabel>{t('education.studentNameKm')} <Optional /></FormLabel><FormControl><Input lang="km" {...field} /></FormControl><FormMessage /></FormItem>
        )} />
      </div>
      <div className="grid gap-4 sm:grid-cols-3">
        <FormField control={form.control} name="gender" render={({ field }) => (
          <FormItem>
            <FormLabel>{t('education.gender')}</FormLabel>
            <Select value={field.value} onValueChange={field.onChange}>
              <FormControl><SelectTrigger className="w-full"><SelectValue /></SelectTrigger></FormControl>
              <SelectContent>{GENDERS.map((g) => <SelectItem key={g} value={g}>{GENDER_LABEL[g]}</SelectItem>)}</SelectContent>
            </Select>
          </FormItem>
        )} />
        <FormField control={form.control} name="dateOfBirth" render={({ field }) => (
          <FormItem><FormLabel>{t('education.dateOfBirth')}</FormLabel><FormControl><Input type="date" max={isoToday()} {...field} /></FormControl><FormMessage /></FormItem>
        )} />
        <FormField control={form.control} name="admittedOn" render={({ field }) => (
          <FormItem><FormLabel>{t('education.admittedOn')}</FormLabel><FormControl><Input type="date" {...field} /></FormControl><FormMessage /></FormItem>
        )} />
      </div>
      <FormField control={form.control} name="address" render={({ field }) => (
        <FormItem><FormLabel>{t('education.address')} <Optional /></FormLabel><FormControl><Input {...field} /></FormControl><FormMessage /></FormItem>
      )} />
      <FormField control={form.control} name="medicalNotes" render={({ field }) => (
        <FormItem><FormLabel>{t('education.medicalNotes')} <Optional /></FormLabel><FormControl><Textarea rows={2} {...field} /></FormControl>
          <FormDescription>{t('education.medicalHint')}</FormDescription><FormMessage /></FormItem>
      )} />
    </>
  );
}

export function StudentDialog({ org, student, onClose }: { org: string; student: StudentView; onClose: () => void }) {
  const { t } = useT();
  const schema = useMemo(() => studentSchema(t), [t]);
  const [error, setError] = useState<string | null>(null);
  const form = useForm<StudentValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      nameEn: student.nameEn, nameKm: student.nameKm ?? '', gender: student.gender, dateOfBirth: student.dateOfBirth, admittedOn: student.admittedOn,
      address: student.address ?? '', medicalNotes: student.medicalNotes ?? '',
    },
  });
  const save = useOrgMutation(org, (v: StudentValues) => eduApi.updateStudent(org, student.id, studentBody(v)));
  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try { await save.mutateAsync(v); toast.success(t('education.studentSaved')); onClose(); } catch (e) { setError(applyServerErrors(form, e)); }
  });
  return (
    <FormDialog open onOpenChange={(o) => !o && onClose()} title={t('education.editStudent')} form={form} onSubmit={onSubmit} error={error} wide>
      <StudentFields form={form} />
    </FormDialog>
  );
}

// ── Withdrawal and graduation ─────────────────────────────────────────────────────

const withdrawalSchema = (t: T) => z.object({
  effectiveOn: z.string().min(1, t('education.errDate')),
  reason: z.string().trim().min(1, t('education.errReason')).max(500),
});
type WithdrawalValues = z.infer<ReturnType<typeof withdrawalSchema>>;

/** Asks for a student's withdrawal; the request goes to the approvers (an Admin by default). */
export function WithdrawalDialog({ org, studentId, name, onClose }: { org: string; studentId: string; name: string; onClose: () => void }) {
  const { t } = useT();
  const schema = useMemo(() => withdrawalSchema(t), [t]);
  const [error, setError] = useState<string | null>(null);
  const form = useForm<WithdrawalValues>({ resolver: zodResolver(schema), defaultValues: { effectiveOn: isoToday(), reason: '' } });
  const save = useOrgMutation(org, (v: WithdrawalValues) => eduApi.requestWithdrawal(org, studentId, { effectiveOn: v.effectiveOn, reason: v.reason.trim() }));
  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try {
      const w = await save.mutateAsync(v);
      toast.success(w.status === 'APPROVED' ? t('education.withdrawnNow') : t('education.withdrawalSent'));
      onClose();
    } catch (e) { setError(applyServerErrors(form, e)); }
  });
  return (
    <FormDialog open onOpenChange={(o) => !o && onClose()} title={t('education.requestWithdrawal')} description={t('education.withdrawalBody', { name })}
      form={form} onSubmit={onSubmit} error={error} submitLabel={t('education.sendRequest')}>
      <FormField control={form.control} name="effectiveOn" render={({ field }) => (
        <FormItem><FormLabel>{t('education.leavesOn')}</FormLabel><FormControl><Input type="date" {...field} /></FormControl><FormMessage /></FormItem>
      )} />
      <FormField control={form.control} name="reason" render={({ field }) => (
        <FormItem><FormLabel>{t('education.reason')}</FormLabel><FormControl><Textarea rows={3} {...field} /></FormControl><FormMessage /></FormItem>
      )} />
    </FormDialog>
  );
}

const graduateSchema = (t: T) => z.object({ leftOn: z.string().min(1, t('education.errDate')) });
type GraduateValues = z.infer<ReturnType<typeof graduateSchema>>;

export function GraduateDialog({ org, studentId, name, onClose }: { org: string; studentId: string; name: string; onClose: () => void }) {
  const { t } = useT();
  const schema = useMemo(() => graduateSchema(t), [t]);
  const [error, setError] = useState<string | null>(null);
  const form = useForm<GraduateValues>({ resolver: zodResolver(schema), defaultValues: { leftOn: isoToday() } });
  const save = useOrgMutation(org, (v: GraduateValues) => eduApi.graduate(org, studentId, v.leftOn));
  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try { await save.mutateAsync(v); toast.success(t('education.graduated')); onClose(); } catch (e) { setError(applyServerErrors(form, e)); }
  });
  return (
    <FormDialog open onOpenChange={(o) => !o && onClose()} title={t('education.markGraduated')} description={t('education.graduateBody', { name })}
      form={form} onSubmit={onSubmit} error={error} submitLabel={t('education.markGraduated')}>
      <FormField control={form.control} name="leftOn" render={({ field }) => (
        <FormItem><FormLabel>{t('education.graduatedOn')}</FormLabel><FormControl><Input type="date" {...field} /></FormControl><FormMessage /></FormItem>
      )} />
    </FormDialog>
  );
}
