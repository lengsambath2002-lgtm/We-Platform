'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { useRouter } from 'next/navigation';
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
import { hrApi, useBalances, useLeaveTypes, workingDays } from '@/lib/api/hr';
import { useOrgMutation } from '@/lib/api/queries';

import { daysText, typeName } from './leave-ui';

const makeSchema = (t: ReturnType<typeof useT>['t']) => z.object({
  leaveTypeId: z.string().min(1, t('hr.errType')),
  startDate: z.string().min(1, t('hr.errStart')),
  endDate: z.string().min(1, t('hr.errEnd')),
  halfStart: z.boolean(),
  halfEnd: z.boolean(),
  reason: z.string().max(2000),
})
  .refine((v) => !v.startDate || !v.endDate || v.endDate >= v.startDate, { path: ['endDate'], message: t('hr.errEndBeforeStart') })
  .refine((v) => !(v.startDate === v.endDate && v.halfStart && v.halfEnd), { path: ['halfEnd'], message: t('hr.errBothHalves') })
  .refine((v) => !v.startDate || !v.endDate || v.endDate < v.startDate || workingDays(v.startDate, v.endDate, v.halfStart, v.halfEnd) > 0,
    { path: ['startDate'], message: t('hr.errWeekend') });
type Values = z.infer<ReturnType<typeof makeSchema>>;

/**
 * Asks for leave: type, first and last day (with half days when the type allows), reason. Shows the working
 * days it will take and what is left; the server checks the same and sends it to the approver.
 */
export function RequestLeaveDialog({ org, open, onOpenChange }: { org: string; open: boolean; onOpenChange: (v: boolean) => void }) {
  const { t } = useT();
  const router = useRouter();
  const schema = useMemo(() => makeSchema(t), [t]);
  const [error, setError] = useState<string | null>(null);
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { leaveTypeId: '', startDate: '', endDate: '', halfStart: false, halfEnd: false, reason: '' },
  });
  const v = form.watch();
  // Balances of the year the leave starts in (a request counts in that year).
  const year = /^\d{4}/.test(v.startDate) ? Number(v.startDate.slice(0, 4)) : new Date().getFullYear();
  const typeList = useLeaveTypes(org);
  const balances = useBalances(org, '', year);
  const types = (typeList.data ?? []).filter((x) => x.active)
    .map((type) => ({ type, remaining: type.allowanceDays === null ? null : balances.data?.find((b) => b.type.id === type.id)?.remaining }));
  const chosen = types.find((b) => b.type.id === v.leaveTypeId);
  const days = workingDays(v.startDate, v.endDate, v.halfStart, v.halfEnd);
  const tooMany = chosen?.remaining != null && days > chosen.remaining;
  const send = useOrgMutation(org, (x: Values) => hrApi.requestLeave(org, { ...x, reason: x.reason || null }));
  const onSubmit = form.handleSubmit(async (x) => {
    setError(null);
    try {
      const l = await send.mutateAsync(x);
      toast.success(t('hr.requestSent'));
      onOpenChange(false);
      form.reset();
      router.push(`/${org}/hr/leave/${l.id}`);
    } catch (e) { setError(applyServerErrors(form, e)); }
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{t('hr.requestLeave')}</DialogTitle>
          <DialogDescription>{t('hr.requestBody')}</DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={onSubmit} className="space-y-4" noValidate>
            {error && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}
            <FormField control={form.control} name="leaveTypeId" render={({ field }) => (
              <FormItem>
                <FormLabel>{t('hr.leaveType')}</FormLabel>
                <Select value={field.value} disabled={typeList.isPending} onValueChange={(x) => { field.onChange(x); if (!types.find((b) => b.type.id === x)?.type.halfDays) { form.setValue('halfStart', false); form.setValue('halfEnd', false); } }}>
                  <FormControl><SelectTrigger className="w-full"><SelectValue placeholder={t('hr.chooseType')} /></SelectTrigger></FormControl>
                  <SelectContent>
                    {types.map((b) => (
                      <SelectItem key={b.type.id} value={b.type.id}>
                        {typeName(b.type)}{b.remaining !== undefined && <span className="text-muted-foreground"> · {b.remaining === null ? t('hr.unlimited') : t('hr.daysLeft', { count: b.remaining })}</span>}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <FormMessage />
              </FormItem>
            )} />
            <div className="grid gap-4 sm:grid-cols-2">
              <FormField control={form.control} name="startDate" render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('hr.firstDay')}</FormLabel>
                  <FormControl><Input type="date" {...field} onChange={(e) => { field.onChange(e); if (!form.getValues('endDate') || form.getValues('endDate') < e.target.value) form.setValue('endDate', e.target.value); }} /></FormControl>
                  <FormMessage />
                </FormItem>
              )} />
              <FormField control={form.control} name="endDate" render={({ field }) => (
                <FormItem><FormLabel>{t('hr.lastDay')}</FormLabel><FormControl><Input type="date" min={v.startDate || undefined} {...field} /></FormControl><FormMessage /></FormItem>
              )} />
            </div>
            {chosen?.type.halfDays && (
              <div className="space-y-2">
                <FormField control={form.control} name="halfStart" render={({ field }) => (
                  <FormItem className="flex items-center gap-2"><FormControl><Checkbox checked={field.value} onCheckedChange={(x) => field.onChange(x === true)} /></FormControl><FormLabel className="!mt-0 font-normal">{t(v.startDate && v.startDate === v.endDate ? 'hr.onlyAfternoon' : 'hr.halfStart')}</FormLabel></FormItem>
                )} />
                <FormField control={form.control} name="halfEnd" render={({ field }) => (
                  <FormItem><div className="flex items-center gap-2"><FormControl><Checkbox checked={field.value} onCheckedChange={(x) => field.onChange(x === true)} /></FormControl><FormLabel className="!mt-0 font-normal">{t(v.startDate && v.startDate === v.endDate ? 'hr.onlyMorning' : 'hr.halfEnd')}</FormLabel></div><FormMessage /></FormItem>
                )} />
              </div>
            )}
            {days > 0 && (
              <p className={tooMany ? 'rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive' : 'rounded-md bg-muted px-3 py-2 text-sm'} aria-live="polite">
                {t('hr.takes', { days: daysText(days) })}
                {chosen?.remaining != null && ` · ${tooMany ? t('hr.notEnoughLeft', { count: chosen.remaining }) : t('hr.leavesLeft', { count: chosen.remaining - days })}`}
              </p>
            )}
            <FormField control={form.control} name="reason" render={({ field }) => (
              <FormItem><FormLabel>{t('hr.reason')} <span className="font-normal text-muted-foreground">{t('common.optional')}</span></FormLabel><FormControl><Textarea rows={2} {...field} /></FormControl><FormMessage /></FormItem>
            )} />
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>{t('common.cancel')}</Button>
              <Button type="submit" disabled={form.formState.isSubmitting || tooMany}>{form.formState.isSubmitting ? t('hr.sending') : t('hr.sendRequest')}</Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
