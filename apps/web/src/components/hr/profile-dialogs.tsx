'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { applyServerErrors } from '@/components/forms/server-errors';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { labels } from '@/i18n/core';
import { useT } from '@/i18n/provider';
import { EMPLOYMENT_TYPES, type EmploymentType, hrApi, type Profile, usePeople } from '@/lib/api/hr';
import { useOrgMutation } from '@/lib/api/queries';
import { unitTree, useStructure } from '@/lib/api/structure';

export const EMPLOYMENT_LABEL = labels(EMPLOYMENT_TYPES, (e) => `hr.emp_${e}`);
const NONE = '__none__';
const PHONE = /^$|^[0-9+()\-. ]{6,30}$/;

const contactSchema = (t: ReturnType<typeof useT>['t']) => z.object({
  workPhone: z.string().trim().regex(PHONE, t('hr.errPhone')),
  emergencyName: z.string().trim().max(80),
  emergencyPhone: z.string().trim().regex(PHONE, t('hr.errPhone')),
  emergencyRelation: z.string().trim().max(40),
});

const profileSchema = (t: ReturnType<typeof useT>['t']) => contactSchema(t).extend({
  title: z.string().trim().max(80),
  unitId: z.string(),
  positionId: z.string(),
  managerId: z.string(),
  employmentType: z.string(),
  startDate: z.string(),
});

function ContactFields({ form }: { form: ReturnType<typeof useForm<z.infer<ReturnType<typeof contactSchema>>>> }) {
  const { t } = useT();
  return (
    <>
      <FormField control={form.control} name="workPhone" render={({ field }) => (
        <FormItem><FormLabel>{t('hr.workPhone')}</FormLabel><FormControl><Input type="tel" inputMode="tel" {...field} /></FormControl><FormMessage /></FormItem>
      )} />
      <fieldset className="space-y-3 rounded-md border p-3">
        <legend className="px-1 text-sm font-medium">{t('hr.emergencyContact')}</legend>
        <FormField control={form.control} name="emergencyName" render={({ field }) => (
          <FormItem><FormLabel>{t('hr.emergencyName')}</FormLabel><FormControl><Input {...field} /></FormControl><FormMessage /></FormItem>
        )} />
        <div className="grid gap-3 sm:grid-cols-2">
          <FormField control={form.control} name="emergencyPhone" render={({ field }) => (
            <FormItem><FormLabel>{t('hr.emergencyPhone')}</FormLabel><FormControl><Input type="tel" inputMode="tel" {...field} /></FormControl><FormMessage /></FormItem>
          )} />
          <FormField control={form.control} name="emergencyRelation" render={({ field }) => (
            <FormItem><FormLabel>{t('hr.emergencyRelation')}</FormLabel><FormControl><Input placeholder={t('hr.relationExample')} {...field} /></FormControl><FormMessage /></FormItem>
          )} />
        </div>
      </fieldset>
    </>
  );
}

/** What anyone may change on their own profile: work phone and emergency contact. */
export function ContactDialog({ org, profile, onClose }: { org: string; profile: Profile; onClose: () => void }) {
  const { t } = useT();
  const schema = useMemo(() => contactSchema(t), [t]);
  const [error, setError] = useState<string | null>(null);
  const form = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: { workPhone: profile.workPhone ?? '', emergencyName: profile.emergencyName ?? '', emergencyPhone: profile.emergencyPhone ?? '', emergencyRelation: profile.emergencyRelation ?? '' },
  });
  const save = useOrgMutation(org, (v: z.infer<typeof schema>) => hrApi.updateContact(org, v));
  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try { await save.mutateAsync(v); toast.success(t('hr.profileSaved')); onClose(); } catch (e) { setError(applyServerErrors(form, e)); }
  });
  return (
    <Dialog open onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader><DialogTitle>{t('hr.editContact')}</DialogTitle><DialogDescription>{t('hr.editContactBody')}</DialogDescription></DialogHeader>
        <Form {...form}>
          <form onSubmit={onSubmit} className="space-y-4" noValidate>
            {error && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}
            <ContactFields form={form} />
            <DialogFooter>
              <Button type="button" variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
              <Button type="submit" disabled={form.formState.isSubmitting}>{form.formState.isSubmitting ? t('common.saving') : t('common.save')}</Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}

/** HR managers: the whole profile — unit and position (or job title), manager, employment, start date and contacts. */
export function ProfileDialog({ org, profile, onClose }: { org: string; profile: Profile; onClose: () => void }) {
  const { t } = useT();
  const schema = useMemo(() => profileSchema(t), [t]);
  const people = usePeople(org);
  const structure = useStructure(org);
  const managers = (people.data ?? []).filter((p) => p.person.id !== profile.person.id);
  const [error, setError] = useState<string | null>(null);
  type Values = z.infer<typeof schema>;
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      title: profile.title ?? '', unitId: profile.unitId ?? NONE, positionId: profile.positionId ?? NONE, managerId: profile.manager?.id ?? NONE,
      employmentType: profile.employmentType ?? NONE, startDate: profile.startDate ?? '',
      workPhone: profile.workPhone ?? '', emergencyName: profile.emergencyName ?? '', emergencyPhone: profile.emergencyPhone ?? '', emergencyRelation: profile.emergencyRelation ?? '',
    },
  });
  const save = useOrgMutation(org, (v: Values) => hrApi.updateProfile(org, profile.person.id, {
    ...v, unitId: v.unitId === NONE ? null : v.unitId, positionId: v.positionId === NONE ? null : v.positionId,
    managerId: v.managerId === NONE ? null : v.managerId, employmentType: v.employmentType === NONE ? null : v.employmentType,
    startDate: v.startDate || null,
  }));
  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try { await save.mutateAsync(v); toast.success(t('hr.profileSaved')); onClose(); } catch (e) { setError(applyServerErrors(form, e)); }
  });
  return (
    <Dialog open onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-lg">
        <DialogHeader><DialogTitle>{t('hr.editProfileOf', { name: profile.person.name })}</DialogTitle><DialogDescription>{t('hr.editProfileBody')}</DialogDescription></DialogHeader>
        <Form {...form}>
          <form onSubmit={onSubmit} className="space-y-4" noValidate>
            {error && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}
            <div className="grid gap-4 sm:grid-cols-2">
              <FormField control={form.control} name="unitId" render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('structure.unit')}</FormLabel>
                  <Select value={field.value} onValueChange={(u) => {
                    field.onChange(u);
                    const pos = structure.data?.positions.find((p) => p.id === form.getValues('positionId'));
                    if (pos?.unitId && pos.unitId !== u) form.setValue('positionId', NONE);
                  }}>
                    <FormControl><SelectTrigger className="w-full"><SelectValue /></SelectTrigger></FormControl>
                    <SelectContent>
                      <SelectItem value={NONE}>{t('structure.noUnit')}</SelectItem>
                      {unitTree(structure.data?.units ?? []).filter(({ unit }) => !unit.archived || unit.id === profile.unitId)
                        .map(({ unit, depth }) => <SelectItem key={unit.id} value={unit.id}>{'— '.repeat(depth)}{unit.name}</SelectItem>)}
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )} />
              <FormField control={form.control} name="positionId" render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('structure.position')}</FormLabel>
                  <Select value={field.value} onValueChange={(p) => {
                    field.onChange(p);
                    const pos = structure.data?.positions.find((x) => x.id === p);
                    if (pos?.unitId) form.setValue('unitId', pos.unitId);
                  }}>
                    <FormControl><SelectTrigger className="w-full"><SelectValue /></SelectTrigger></FormControl>
                    <SelectContent>
                      <SelectItem value={NONE}>{t('structure.noPosition')}</SelectItem>
                      {(structure.data?.positions ?? []).filter((p) => (!p.archived || p.id === profile.positionId)
                        && (form.watch('unitId') === NONE || !p.unitId || p.unitId === form.watch('unitId')))
                        .sort((a, b) => a.title.localeCompare(b.title))
                        .map((p) => <SelectItem key={p.id} value={p.id}>{p.title}</SelectItem>)}
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )} />
            </div>
            {form.watch('positionId') === NONE && (
              <FormField control={form.control} name="title" render={({ field }) => (
                <FormItem><FormLabel>{t('hr.jobTitle')}</FormLabel><FormControl><Input {...field} /></FormControl><FormMessage /></FormItem>
              )} />
            )}
            <FormField control={form.control} name="managerId" render={({ field }) => (
              <FormItem>
                <FormLabel>{t('hr.manager')}</FormLabel>
                <Select value={field.value} onValueChange={field.onChange}>
                  <FormControl><SelectTrigger className="w-full"><SelectValue /></SelectTrigger></FormControl>
                  <SelectContent>
                    <SelectItem value={NONE}>{t('hr.noManager')}</SelectItem>
                    {managers.map((p) => <SelectItem key={p.person.id} value={p.person.id}>{p.person.name}</SelectItem>)}
                  </SelectContent>
                </Select>
                <FormMessage />
              </FormItem>
            )} />
            <div className="grid gap-4 sm:grid-cols-2">
              <FormField control={form.control} name="employmentType" render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('hr.employmentType')}</FormLabel>
                  <Select value={field.value} onValueChange={field.onChange}>
                    <FormControl><SelectTrigger className="w-full"><SelectValue /></SelectTrigger></FormControl>
                    <SelectContent>
                      <SelectItem value={NONE}>{t('hr.notSet')}</SelectItem>
                      {EMPLOYMENT_TYPES.map((e: EmploymentType) => <SelectItem key={e} value={e}>{EMPLOYMENT_LABEL[e]}</SelectItem>)}
                    </SelectContent>
                  </Select>
                </FormItem>
              )} />
              <FormField control={form.control} name="startDate" render={({ field }) => (
                <FormItem><FormLabel>{t('hr.startDate')}</FormLabel><FormControl><Input type="date" {...field} /></FormControl><FormMessage /></FormItem>
              )} />
            </div>
            <ContactFields form={form as never} />
            <DialogFooter>
              <Button type="button" variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
              <Button type="submit" disabled={form.formState.isSubmitting}>{form.formState.isSubmitting ? t('common.saving') : t('common.save')}</Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
