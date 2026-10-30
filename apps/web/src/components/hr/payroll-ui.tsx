'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { parse } from 'date-fns';
import { Plus, Trash2 } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useMemo, useState } from 'react';
import { useFieldArray, useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { applyServerErrors } from '@/components/forms/server-errors';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { currentLocale, tr } from '@/i18n/core';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { type Payslip, type PayrollSettings, type PersonPay, type RunStatus, payrollApi } from '@/lib/api/payroll';
import { useOrgMutation } from '@/lib/api/queries';
import { fmt } from '@/lib/format';
import { cn } from '@/lib/utils';

/** "1,234,000 ៛". */
export const riel = (n: number) => `${new Intl.NumberFormat(currentLocale() === 'km' ? 'km-KH' : 'en-US', { maximumFractionDigits: 0 }).format(n)} ៛`;

/** "November 2026" for "2026-11". */
export const monthText = (m: string) => fmt(parse(m, 'yyyy-MM', new Date()), 'MMMM yyyy');

const TONE: Record<RunStatus, string> = {
  DRAFT: 'bg-muted text-foreground',
  APPROVED: 'bg-sky-50 text-sky-700 dark:bg-sky-950 dark:text-sky-300',
  PAID: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300',
};

export function RunBadge({ status }: { status: RunStatus }) {
  return <span className={cn('inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium', TONE[status])}>{tr(`hr.run_${status}`)}</span>;
}

const error = (msg: string | null) => msg && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{msg}</p>;

const makeRunSchema = (t: ReturnType<typeof useT>['t']) => z.object({
  month: z.string().regex(/^\d{4}-(0[1-9]|1[0-2])$/, t('hr.errMonth')),
  exchangeRate: z.coerce.number().min(1, t('hr.errRate')).max(100000),
  note: z.string().max(500),
});

/** Starts a month's pay run (as a draft) and opens it. */
export function NewRunDialog({ org, onClose }: { org: string; onClose: () => void }) {
  const { t } = useT();
  const router = useRouter();
  const schema = useMemo(() => makeRunSchema(t), [t]);
  const [err, setErr] = useState<string | null>(null);
  const now = new Date();
  const form = useForm<z.input<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: { month: `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`, exchangeRate: 4100, note: '' },
  });
  const create = useOrgMutation(org, (v: unknown) => payrollApi.createRun(org, v));
  const onSubmit = form.handleSubmit(async (raw) => {
    setErr(null);
    try {
      const d = await create.mutateAsync({ ...schema.parse(raw), note: raw.note || null });
      onClose();
      router.push(`/${org}/hr/payroll/${d.run.id}`);
    } catch (e) { setErr(applyServerErrors(form, e)); }
  });
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader><DialogTitle>{t('hr.newRun')}</DialogTitle><DialogDescription>{t('hr.newRunBody')}</DialogDescription></DialogHeader>
        <Form {...form}>
          <form onSubmit={onSubmit} className="space-y-4" noValidate>
            {error(err)}
            <FormField control={form.control} name="month" render={({ field }) => (
              <FormItem><FormLabel>{t('hr.payMonth')}</FormLabel><FormControl><Input type="month" {...field} /></FormControl><FormMessage /></FormItem>
            )} />
            <FormField control={form.control} name="exchangeRate" render={({ field }) => (
              <FormItem>
                <FormLabel>{t('hr.exchangeRate')}</FormLabel>
                <FormControl><Input type="number" min={1} step="0.01" inputMode="decimal" {...field} value={String(field.value ?? '')} /></FormControl>
                <FormDescription>{t('hr.exchangeRateHint')}</FormDescription>
                <FormMessage />
              </FormItem>
            )} />
            <FormField control={form.control} name="note" render={({ field }) => (
              <FormItem><FormLabel>{t('hr.runNote')}</FormLabel><FormControl><Input maxLength={500} {...field} /></FormControl><FormMessage /></FormItem>
            )} />
            <DialogFooter>
              <Button type="button" variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
              <Button type="submit" disabled={form.formState.isSubmitting}>{t('hr.prepareRun')}</Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}

const makePaySchema = (t: ReturnType<typeof useT>['t']) => z.object({
  monthlySalary: z.string().trim().regex(/^\d{1,10}(\.\d{1,2})?$/, t('hr.errSalary')),
  dependants: z.string().regex(/^([0-9]|1[0-9]|20)$/, t('hr.errDependants')),
  nssfEnrolled: z.boolean(),
  bankName: z.string().max(60),
  bankAccount: z.string().max(40),
});

/** What one person is paid. */
export function PayDialog({ org, row, onClose }: { org: string; row: PersonPay; onClose: () => void }) {
  const { t } = useT();
  const schema = useMemo(() => makePaySchema(t), [t]);
  const [err, setErr] = useState<string | null>(null);
  const form = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: { monthlySalary: row.monthlySalary === null ? '' : Number(row.monthlySalary).toFixed(2), dependants: String(row.dependants), nssfEnrolled: row.nssfEnrolled, bankName: row.bankName ?? '', bankAccount: row.bankAccount ?? '' },
  });
  const save = useOrgMutation(org, (v: unknown) => payrollApi.setPay(org, row.person.id, v));
  const onSubmit = form.handleSubmit(async (v) => {
    setErr(null);
    try {
      await save.mutateAsync({ ...v, monthlySalary: v.monthlySalary.trim(), dependants: Number(v.dependants), bankName: v.bankName || null, bankAccount: v.bankAccount || null });
      toast.success(t('hr.paySaved'));
      onClose();
    } catch (e) { setErr(applyServerErrors(form, e)); }
  });
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader><DialogTitle>{t('hr.payOf', { name: row.person.name })}</DialogTitle></DialogHeader>
        <Form {...form}>
          <form onSubmit={onSubmit} className="space-y-4" noValidate>
            {error(err)}
            <div className="grid grid-cols-2 gap-4">
              <FormField control={form.control} name="monthlySalary" render={({ field }) => (
                <FormItem><FormLabel>{t('hr.monthlySalary')}</FormLabel><FormControl><Input inputMode="decimal" placeholder="0.00" {...field} /></FormControl><FormMessage /></FormItem>
              )} />
              <FormField control={form.control} name="dependants" render={({ field }) => (
                <FormItem><FormLabel>{t('hr.dependants')}</FormLabel><FormControl><Input type="number" min={0} max={20} inputMode="numeric" {...field} /></FormControl><FormMessage /></FormItem>
              )} />
            </div>
            <p className="-mt-2 text-xs text-muted-foreground">{t('hr.dependantsHint')}</p>
            <FormField control={form.control} name="nssfEnrolled" render={({ field }) => (
              <FormItem className="flex items-center gap-2"><FormControl><Checkbox checked={field.value} onCheckedChange={(x) => field.onChange(x === true)} /></FormControl><FormLabel className="!mt-0 font-normal">{t('hr.nssfEnrolled')}</FormLabel></FormItem>
            )} />
            <div className="grid grid-cols-2 gap-4">
              <FormField control={form.control} name="bankName" render={({ field }) => (
                <FormItem><FormLabel>{t('hr.bankName')}</FormLabel><FormControl><Input maxLength={60} {...field} /></FormControl><FormMessage /></FormItem>
              )} />
              <FormField control={form.control} name="bankAccount" render={({ field }) => (
                <FormItem><FormLabel>{t('hr.bankAccount')}</FormLabel><FormControl><Input maxLength={40} {...field} /></FormControl><FormMessage /></FormItem>
              )} />
            </div>
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

const makeAdjSchema = (t: ReturnType<typeof useT>['t']) => z.object({
  lines: z.array(z.object({
    label: z.string().trim().min(1, t('hr.errAdjLabel')).max(80),
    amount: z.string().trim().regex(/^-?\d{1,6}(\.\d{1,2})?$/, t('hr.errAdjAmount')).refine((v) => Number(v) !== 0, t('hr.errAdjAmount')),
  })).max(20),
});

/** HR's extra lines on a draft payslip: positive = taxable allowance, negative = deducted after tax. */
export function AdjustmentsDialog({ org, slip, onClose }: { org: string; slip: Payslip; onClose: () => void }) {
  const { t } = useT();
  const schema = useMemo(() => makeAdjSchema(t), [t]);
  const [err, setErr] = useState<string | null>(null);
  const form = useForm<z.infer<typeof schema>>({ resolver: zodResolver(schema), defaultValues: { lines: slip.adjustments.map((a) => ({ label: a.label, amount: Number(a.amount).toFixed(2) })) } });
  const lines = useFieldArray({ control: form.control, name: 'lines' });
  const save = useOrgMutation(org, (v: { label: string; amount: number }[]) => payrollApi.setAdjustments(org, slip.id, v));
  const onSubmit = form.handleSubmit(async (v) => {
    setErr(null);
    try {
      await save.mutateAsync(v.lines.map((l) => ({ label: l.label, amount: Number(l.amount) })));
      toast.success(t('hr.adjustmentsSaved'));
      onClose();
    } catch (e) { setErr(applyServerErrors(form, e)); }
  });
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader><DialogTitle>{t('hr.adjustmentsOf', { name: slip.person.name })}</DialogTitle><DialogDescription>{t('hr.adjustmentsBody')}</DialogDescription></DialogHeader>
        <Form {...form}>
          <form onSubmit={onSubmit} className="space-y-3" noValidate>
            {error(err)}
            {lines.fields.length === 0 && <p className="text-sm text-muted-foreground">{t('hr.noAdjustmentLines')}</p>}
            {lines.fields.map((f, i) => (
              <div key={f.id} className="grid grid-cols-[1fr_8rem_auto] items-start gap-2">
                <FormField control={form.control} name={`lines.${i}.label`} render={({ field }) => (
                  <FormItem><FormLabel className="sr-only">{t('hr.adjLabel')}</FormLabel><FormControl><Input placeholder={t('hr.adjLabelHint')} maxLength={80} {...field} /></FormControl><FormMessage /></FormItem>
                )} />
                <FormField control={form.control} name={`lines.${i}.amount`} render={({ field }) => (
                  <FormItem><FormLabel className="sr-only">{t('hr.adjAmount')}</FormLabel><FormControl><Input inputMode="decimal" placeholder="0.00" {...field} /></FormControl><FormMessage /></FormItem>
                )} />
                <Button type="button" variant="ghost" size="icon" className="size-9" aria-label={t('common.remove')} onClick={() => lines.remove(i)}><Trash2 className="size-4" /></Button>
              </div>
            ))}
            <Button type="button" variant="outline" size="sm" onClick={() => lines.append({ label: '', amount: '' })}><Plus className="size-4" />{t('hr.addAdjustment')}</Button>
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

/** HR managers: Tax on Salary brackets, the dependant allowance, NSSF and overtime rates. */
export function PayrollSettingsForm({ org, settings }: { org: string; settings: PayrollSettings }) {
  const { t } = useT();
  const [s, setS] = useState(settings);
  const [err, setErr] = useState<string | null>(null);
  const save = useOrgMutation(org, (v: unknown) => payrollApi.saveSettings(org, v));
  const num = (v: string) => (v === '' ? 0 : Number(v));
  const field = (key: keyof PayrollSettings, label: string, step = '1') => (
    <div className="space-y-1.5">
      <Label htmlFor={`ps-${key}`}>{label}</Label>
      <Input id={`ps-${key}`} type="number" step={step} min={0} inputMode="decimal" value={String(s[key])} onChange={(e) => setS({ ...s, [key]: num(e.target.value) })} />
    </div>
  );
  return (
    <form className="space-y-6" noValidate onSubmit={async (e) => {
      e.preventDefault();
      setErr(null);
      try { setS(await save.mutateAsync(s)); toast.success(t('hr.payrollSettingsSaved')); } catch (x) { setErr(errorMessage(x)); }
    }}>
      <p className="rounded-md border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-900 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-200">{t('hr.ratesWarning')}</p>
      {error(err)}
      <section className="space-y-3">
        <div><h3 className="text-sm font-semibold">{t('hr.taxBrackets')}</h3><p className="text-sm text-muted-foreground">{t('hr.taxBracketsBody')}</p></div>
        {s.taxBrackets.map((b, i) => {
          const last = i === s.taxBrackets.length - 1;
          return (
            <div key={i} className="grid grid-cols-[1fr_7rem_auto] items-end gap-2">
              <div className="space-y-1.5">
                <Label htmlFor={`b-up-${i}`} className="text-xs text-muted-foreground">{t('hr.bracketUpTo')}</Label>
                {last ? <p className="flex h-9 items-center text-sm text-muted-foreground">{t('hr.bracketAbove')}</p>
                  : <Input id={`b-up-${i}`} type="number" min={1} step={1} value={String(b.upToKhr ?? '')} onChange={(e) => setS({ ...s, taxBrackets: s.taxBrackets.map((x, j) => (j === i ? { ...x, upToKhr: num(e.target.value) } : x)) })} />}
              </div>
              <div className="space-y-1.5">
                <Label htmlFor={`b-rate-${i}`} className="text-xs text-muted-foreground">{t('hr.bracketRate')}</Label>
                <Input id={`b-rate-${i}`} type="number" min={0} max={100} step="0.1" value={String(b.ratePct)} onChange={(e) => setS({ ...s, taxBrackets: s.taxBrackets.map((x, j) => (j === i ? { ...x, ratePct: num(e.target.value) } : x)) })} />
              </div>
              <Button type="button" variant="ghost" size="icon" className="size-9" disabled={last || s.taxBrackets.length <= 2} aria-label={t('common.remove')}
                onClick={() => setS({ ...s, taxBrackets: s.taxBrackets.filter((_, j) => j !== i) })}><Trash2 className="size-4" /></Button>
            </div>
          );
        })}
        <Button type="button" variant="outline" size="sm" disabled={s.taxBrackets.length >= 10} onClick={() => {
          const b = [...s.taxBrackets];
          const prev = b.length > 1 ? (b[b.length - 2].upToKhr ?? 0) : 0;
          b.splice(b.length - 1, 0, { upToKhr: prev + 1_000_000, ratePct: b[b.length - 1].ratePct });
          setS({ ...s, taxBrackets: b });
        }}><Plus className="size-4" />{t('hr.addBracket')}</Button>
      </section>
      <section className="grid gap-4 sm:grid-cols-2">
        {field('dependantAllowanceKhr', t('hr.dependantAllowance'))}
        {field('overtimeRatePct', t('hr.overtimeRate'), '1')}
        {field('nssfFloorKhr', t('hr.nssfFloor'))}
        {field('nssfCapKhr', t('hr.nssfCap'))}
        {field('nssfPensionEmployeePct', t('hr.nssfPensionEmployee'), '0.01')}
        {field('nssfPensionEmployerPct', t('hr.nssfPensionEmployer'), '0.01')}
        {field('nssfHealthEmployerPct', t('hr.nssfHealth'), '0.01')}
        {field('nssfRiskEmployerPct', t('hr.nssfRisk'), '0.01')}
      </section>
      <Button type="submit" disabled={save.isPending}>{t(save.isPending ? 'common.saving' : 'common.save')}</Button>
    </form>
  );
}
