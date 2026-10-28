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
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { useT } from '@/i18n/provider';
import { type Balance, hrApi, type LeaveType } from '@/lib/api/hr';
import { useOrgMutation } from '@/lib/api/queries';

import { typeName } from './leave-ui';

const halfStep = (n: number) => Number.isFinite(n) && Math.round(n * 2) === n * 2;

const typeSchema = (t: ReturnType<typeof useT>['t']) => z.object({
  nameEn: z.string().trim().min(1, t('hr.errName')).max(60),
  nameKm: z.string().trim().min(1, t('hr.errName')).max(60),
  paid: z.boolean(),
  unlimited: z.boolean(),
  allowanceDays: z.string(),
  halfDays: z.boolean(),
  active: z.boolean(),
}).refine((v) => v.unlimited || (v.allowanceDays !== '' && Number(v.allowanceDays) >= 0 && Number(v.allowanceDays) <= 366 && halfStep(Number(v.allowanceDays))),
  { path: ['allowanceDays'], message: t('hr.errAllowance') });

/** Adds or edits a leave type (names in both languages, paid, yearly days or unlimited, half days, offered). */
export function LeaveTypeDialog({ org, type, onClose }: { org: string; type: LeaveType | 'new'; onClose: () => void }) {
  const { t } = useT();
  const schema = useMemo(() => typeSchema(t), [t]);
  type Values = z.infer<typeof schema>;
  const [error, setError] = useState<string | null>(null);
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: type === 'new'
      ? { nameEn: '', nameKm: '', paid: true, unlimited: false, allowanceDays: '5', halfDays: true, active: true }
      : { nameEn: type.nameEn, nameKm: type.nameKm, paid: type.paid, unlimited: type.allowanceDays === null,
        allowanceDays: type.allowanceDays === null ? '' : String(type.allowanceDays), halfDays: type.halfDays, active: type.active },
  });
  const unlimited = form.watch('unlimited');
  const save = useOrgMutation(org, (v: Values) => hrApi.saveLeaveType(org, type === 'new' ? null : type.id, {
    nameEn: v.nameEn, nameKm: v.nameKm, paid: v.paid, allowanceDays: v.unlimited ? null : Number(v.allowanceDays), halfDays: v.halfDays, active: v.active,
  }));
  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try { await save.mutateAsync(v); toast.success(t('hr.typeSaved')); onClose(); } catch (e) { setError(applyServerErrors(form, e)); }
  });
  const check = (name: 'paid' | 'unlimited' | 'halfDays' | 'active', label: string) => (
    <FormField control={form.control} name={name} render={({ field }) => (
      <FormItem className="flex items-center gap-2"><FormControl><Checkbox checked={field.value} onCheckedChange={(x) => field.onChange(x === true)} /></FormControl><FormLabel className="!mt-0 font-normal">{label}</FormLabel></FormItem>
    )} />
  );
  return (
    <Dialog open onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{type === 'new' ? t('hr.newType') : t('hr.editType', { name: typeName(type) })}</DialogTitle>
          <DialogDescription>{t('hr.typeDialogBody')}</DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={onSubmit} className="space-y-4" noValidate>
            {error && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}
            <FormField control={form.control} name="nameEn" render={({ field }) => (
              <FormItem><FormLabel>{t('hr.nameEn')}</FormLabel><FormControl><Input lang="en" {...field} /></FormControl><FormMessage /></FormItem>
            )} />
            <FormField control={form.control} name="nameKm" render={({ field }) => (
              <FormItem><FormLabel>{t('hr.nameKm')}</FormLabel><FormControl><Input lang="km" {...field} /></FormControl><FormMessage /></FormItem>
            )} />
            {check('unlimited', t('hr.unlimitedDays'))}
            {!unlimited && (
              <FormField control={form.control} name="allowanceDays" render={({ field }) => (
                <FormItem><FormLabel>{t('hr.allowancePerYear')}</FormLabel><FormControl><Input type="number" inputMode="decimal" min={0} max={366} step={0.5} {...field} /></FormControl><FormMessage /></FormItem>
              )} />
            )}
            {check('paid', t('hr.paidLeave'))}
            {check('halfDays', t('hr.allowHalfDays'))}
            {check('active', t('hr.offerType'))}
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

const adjustSchema = (t: ReturnType<typeof useT>['t']) => z.object({
  leaveTypeId: z.string().min(1, t('hr.errType')),
  days: z.string().refine((d) => d !== '' && Number(d) !== 0 && Math.abs(Number(d)) <= 366 && halfStep(Number(d)), t('hr.errAdjustDays')),
  reason: z.string().trim().min(1, t('hr.errReason')).max(500),
});

/** Adds (or with a minus, removes) days of a limited leave type for one person and year; the reason is kept. */
export function AdjustDialog({ org, userId, name, year, balances, onClose }: {
  org: string; userId: string; name: string; year: number; balances: Balance[]; onClose: () => void;
}) {
  const { t } = useT();
  const schema = useMemo(() => adjustSchema(t), [t]);
  type Values = z.infer<typeof schema>;
  const limited = balances.filter((b) => b.allowance !== null);
  const [error, setError] = useState<string | null>(null);
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { leaveTypeId: limited[0]?.type.id ?? '', days: '1', reason: '' } });
  const save = useOrgMutation(org, (v: Values) => hrApi.adjust(org, { userId, year, leaveTypeId: v.leaveTypeId, days: Number(v.days), reason: v.reason }));
  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try { await save.mutateAsync(v); toast.success(t('hr.adjusted')); onClose(); } catch (e) { setError(applyServerErrors(form, e)); }
  });
  return (
    <Dialog open onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader><DialogTitle>{t('hr.adjustTitle', { name, year })}</DialogTitle><DialogDescription>{t('hr.adjustBody')}</DialogDescription></DialogHeader>
        <Form {...form}>
          <form onSubmit={onSubmit} className="space-y-4" noValidate>
            {error && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}
            <FormField control={form.control} name="leaveTypeId" render={({ field }) => (
              <FormItem>
                <FormLabel>{t('hr.leaveType')}</FormLabel>
                <Select value={field.value} onValueChange={field.onChange}>
                  <FormControl><SelectTrigger className="w-full"><SelectValue placeholder={t('hr.chooseType')} /></SelectTrigger></FormControl>
                  <SelectContent>{limited.map((b) => <SelectItem key={b.type.id} value={b.type.id}>{typeName(b.type)} · {t('hr.daysLeft', { count: b.remaining ?? 0 })}</SelectItem>)}</SelectContent>
                </Select>
                <FormMessage />
              </FormItem>
            )} />
            <FormField control={form.control} name="days" render={({ field }) => (
              <FormItem><FormLabel>{t('hr.daysToAdd')}</FormLabel><FormControl><Input type="number" inputMode="decimal" step={0.5} {...field} /></FormControl><p className="text-xs text-muted-foreground">{t('hr.daysToAddHint')}</p><FormMessage /></FormItem>
            )} />
            <FormField control={form.control} name="reason" render={({ field }) => (
              <FormItem><FormLabel>{t('hr.reason')}</FormLabel><FormControl><Textarea rows={2} {...field} /></FormControl><FormMessage /></FormItem>
            )} />
            <DialogFooter>
              <Button type="button" variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
              <Button type="submit" disabled={form.formState.isSubmitting}>{form.formState.isSubmitting ? t('common.saving') : t('hr.adjust')}</Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
