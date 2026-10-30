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
import { eduApi, type FeeItem, isoToday, PAY_METHODS, type PayMethod, useFeeItems, useGrades, useStudents, useYears } from '@/lib/api/education';
import { useOrgMutation } from '@/lib/api/queries';
import { khr, usd } from '@/lib/money';

import { FormDialog } from './form-dialog';
import { gradeName, METHOD_LABEL, Optional, studentName } from './ui';

type T = ReturnType<typeof useT>['t'];
const MONEY = /^\d{1,10}(\.\d{1,2})?$/;
const ALL = '__all';
const NONE = '__none';

// ── Fee item ──────────────────────────────────────────────────────────────────────

const itemSchema = (t: T) => z.object({
  yearId: z.string().min(1, t('education.errYear')),
  gradeId: z.string(),
  nameEn: z.string().trim().min(1, t('education.errName')).max(120),
  nameKm: z.string().max(120),
  amount: z.string().refine((v) => MONEY.test(v.trim()) && Number(v) > 0, t('education.errMoney')),
  dueOn: z.string().min(1, t('education.errDate')),
  active: z.boolean(),
});
type ItemValues = z.infer<ReturnType<typeof itemSchema>>;

export function FeeItemDialog({ org, item, yearId, onClose }: { org: string; item?: FeeItem; yearId?: string; onClose: () => void }) {
  const { t } = useT();
  const schema = useMemo(() => itemSchema(t), [t]);
  const [error, setError] = useState<string | null>(null);
  const years = useYears(org);
  const grades = useGrades(org);
  const form = useForm<ItemValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      yearId: item?.yearId ?? yearId ?? '', gradeId: item?.gradeId ?? ALL, nameEn: item?.nameEn ?? '', nameKm: item?.nameKm ?? '',
      amount: item ? item.amount.toFixed(2) : '', dueOn: item?.dueOn ?? '', active: item?.active ?? true,
    },
  });
  const save = useOrgMutation(org, (v: ItemValues) => eduApi.saveFeeItem(org, item?.id ?? null, {
    yearId: v.yearId, gradeId: v.gradeId === ALL ? null : v.gradeId, nameEn: v.nameEn.trim(), nameKm: v.nameKm.trim() || null,
    amount: v.amount.trim(), dueOn: v.dueOn, active: v.active,
  }));
  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try { await save.mutateAsync(v); toast.success(t('education.feeSaved')); onClose(); } catch (e) { setError(applyServerErrors(form, e)); }
  });
  const amount = Number(form.watch('amount'));
  return (
    <FormDialog open onOpenChange={(o) => !o && onClose()} title={item ? t('education.editFee') : t('education.newFee')}
      description={t('education.feeBody')} form={form} onSubmit={onSubmit} error={error}>
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField control={form.control} name="yearId" render={({ field }) => (
          <FormItem>
            <FormLabel>{t('education.academicYear')}</FormLabel>
            <Select value={field.value} onValueChange={field.onChange}>
              <FormControl><SelectTrigger className="w-full"><SelectValue placeholder={t('education.chooseYear')} /></SelectTrigger></FormControl>
              <SelectContent>{(years.data ?? []).map((y) => <SelectItem key={y.id} value={y.id}>{y.name}</SelectItem>)}</SelectContent>
            </Select>
            <FormMessage />
          </FormItem>
        )} />
        <FormField control={form.control} name="gradeId" render={({ field }) => (
          <FormItem>
            <FormLabel>{t('education.forGrade')}</FormLabel>
            <Select value={field.value} onValueChange={field.onChange}>
              <FormControl><SelectTrigger className="w-full"><SelectValue /></SelectTrigger></FormControl>
              <SelectContent>
                <SelectItem value={ALL}>{t('education.everyGrade')}</SelectItem>
                {(grades.data ?? []).filter((g) => g.active).map((g) => <SelectItem key={g.id} value={g.id}>{gradeName(g)}</SelectItem>)}
              </SelectContent>
            </Select>
          </FormItem>
        )} />
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField control={form.control} name="nameEn" render={({ field }) => (
          <FormItem><FormLabel>{t('education.feeName')}</FormLabel><FormControl><Input placeholder={t('education.feePlaceholder')} {...field} /></FormControl><FormMessage /></FormItem>
        )} />
        <FormField control={form.control} name="nameKm" render={({ field }) => (
          <FormItem><FormLabel>{t('education.nameKm')} <Optional /></FormLabel><FormControl><Input lang="km" {...field} /></FormControl><FormMessage /></FormItem>
        )} />
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField control={form.control} name="amount" render={({ field }) => (
          <FormItem>
            <FormLabel>{t('education.amountUsd')}</FormLabel>
            <FormControl><Input inputMode="decimal" {...field} /></FormControl>
            {amount > 0 && <p className="text-xs text-muted-foreground tabular">{khr(amount)}</p>}
            <FormMessage />
          </FormItem>
        )} />
        <FormField control={form.control} name="dueOn" render={({ field }) => (
          <FormItem><FormLabel>{t('education.dueOn')}</FormLabel><FormControl><Input type="date" {...field} /></FormControl><FormMessage /></FormItem>
        )} />
      </div>
      {item && (
        <FormField control={form.control} name="active" render={({ field }) => (
          <FormItem className="flex items-center gap-2 space-y-0">
            <FormControl><Checkbox checked={field.value} onCheckedChange={(c) => field.onChange(c === true)} /></FormControl>
            <FormLabel>{t('education.inUse')}</FormLabel>
          </FormItem>
        )} />
      )}
    </FormDialog>
  );
}

// ── One invoice ───────────────────────────────────────────────────────────────────

const invoiceSchema = (t: T) => z.object({
  studentId: z.string().min(1, t('education.errStudent')),
  feeItemId: z.string(),
  description: z.string().max(200),
  amount: z.string().refine((v) => !v.trim() || (MONEY.test(v.trim()) && Number(v) > 0), t('education.errMoney')),
  dueOn: z.string(),
}).superRefine((v, ctx) => {
  if (v.feeItemId === NONE && !v.description.trim()) ctx.addIssue({ code: 'custom', path: ['description'], message: t('education.errDescription') });
  if (v.feeItemId === NONE && !v.amount.trim()) ctx.addIssue({ code: 'custom', path: ['amount'], message: t('education.errMoney') });
});
type InvoiceValues = z.infer<ReturnType<typeof invoiceSchema>>;

/** Bills one student: a fee item (its name, amount and due date fill empty fields) or something else. */
export function InvoiceDialog({ org, studentId, onClose, onSaved }: { org: string; studentId?: string; onClose: () => void; onSaved?: (id: string) => void }) {
  const { t } = useT();
  const schema = useMemo(() => invoiceSchema(t), [t]);
  const [error, setError] = useState<string | null>(null);
  const [q, setQ] = useState('');
  const students = useStudents(org, { q: q.trim() || undefined, status: 'ACTIVE', size: 20 });
  const items = useFeeItems(org);
  const form = useForm<InvoiceValues>({
    resolver: zodResolver(schema),
    defaultValues: { studentId: studentId ?? '', feeItemId: NONE, description: '', amount: '', dueOn: '' },
  });
  const save = useOrgMutation(org, (v: InvoiceValues) => eduApi.createInvoice(org, {
    studentId: v.studentId, feeItemId: v.feeItemId === NONE ? null : v.feeItemId, description: v.description.trim() || null,
    amount: v.amount.trim() || null, dueOn: v.dueOn || null,
  }));
  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try { const inv = await save.mutateAsync(v); toast.success(t('education.invoiceCreated', { number: inv.summary.number })); onClose(); onSaved?.(inv.summary.id); }
    catch (e) { setError(applyServerErrors(form, e)); }
  });
  const item = (items.data ?? []).find((i) => i.id === form.watch('feeItemId'));
  return (
    <FormDialog open onOpenChange={(o) => !o && onClose()} title={t('education.newInvoice')} description={t('education.invoiceBody')}
      form={form} onSubmit={onSubmit} error={error} submitLabel={t('education.createInvoice')}>
      {!studentId && (
        <FormField control={form.control} name="studentId" render={({ field }) => (
          <FormItem>
            <FormLabel>{t('education.student')}</FormLabel>
            <Input type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder={t('education.searchStudents')} aria-label={t('education.searchStudents')} />
            <Select value={field.value} onValueChange={field.onChange}>
              <FormControl><SelectTrigger className="w-full" aria-label={t('education.student')}><SelectValue placeholder={t('education.chooseStudent')} /></SelectTrigger></FormControl>
              <SelectContent>{(students.data?.items ?? []).map((s) => <SelectItem key={s.id} value={s.id}>{s.number} · {studentName(s)}</SelectItem>)}</SelectContent>
            </Select>
            <FormMessage />
          </FormItem>
        )} />
      )}
      <FormField control={form.control} name="feeItemId" render={({ field }) => (
        <FormItem>
          <FormLabel>{t('education.feeItem')}</FormLabel>
          <Select value={field.value} onValueChange={field.onChange}>
            <FormControl><SelectTrigger className="w-full"><SelectValue /></SelectTrigger></FormControl>
            <SelectContent>
              <SelectItem value={NONE}>{t('education.otherFee')}</SelectItem>
              {(items.data ?? []).filter((i) => i.active).map((i) => <SelectItem key={i.id} value={i.id}>{i.nameEn} · {usd(i.amount)}</SelectItem>)}
            </SelectContent>
          </Select>
        </FormItem>
      )} />
      <FormField control={form.control} name="description" render={({ field }) => (
        <FormItem><FormLabel>{t('education.description')} {item && <Optional />}</FormLabel><FormControl><Input placeholder={item?.nameEn} {...field} /></FormControl><FormMessage /></FormItem>
      )} />
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField control={form.control} name="amount" render={({ field }) => (
          <FormItem><FormLabel>{t('education.amountUsd')} {item && <Optional />}</FormLabel><FormControl><Input inputMode="decimal" placeholder={item ? item.amount.toFixed(2) : undefined} {...field} /></FormControl><FormMessage /></FormItem>
        )} />
        <FormField control={form.control} name="dueOn" render={({ field }) => (
          <FormItem><FormLabel>{t('education.dueOn')} <Optional /></FormLabel><FormControl><Input type="date" min={isoToday()} {...field} /></FormControl>
            <FormDescription>{t('education.dueHint')}</FormDescription><FormMessage /></FormItem>
        )} />
      </div>
    </FormDialog>
  );
}

// ── Payment and void ──────────────────────────────────────────────────────────────

const paymentSchema = (t: T, balance: number) => z.object({
  paidOn: z.string().min(1, t('education.errDate')),
  amount: z.string().refine((v) => MONEY.test(v.trim()) && Number(v) > 0, t('education.errMoney'))
    .refine((v) => Number(v) <= balance + 1e-9, t('education.errOverBalance')),
  method: z.enum(PAY_METHODS as [PayMethod, ...PayMethod[]]),
  reference: z.string().max(120),
});
type PaymentValues = z.infer<ReturnType<typeof paymentSchema>>;

/** Records money received; the amount starts at the balance and can't go above it. */
export function PaymentDialog({ org, invoiceId, balance, onClose }: { org: string; invoiceId: string; balance: number; onClose: () => void }) {
  const { t } = useT();
  const schema = useMemo(() => paymentSchema(t, balance), [t, balance]);
  const [error, setError] = useState<string | null>(null);
  const form = useForm<PaymentValues>({ resolver: zodResolver(schema), defaultValues: { paidOn: isoToday(), amount: balance.toFixed(2), method: 'CASH', reference: '' } });
  const pay = useOrgMutation(org, (v: PaymentValues) => eduApi.pay(org, invoiceId, { ...v, amount: v.amount.trim(), reference: v.reference.trim() || null }));
  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try {
      const inv = await pay.mutateAsync(v);
      toast.success(inv.summary.status === 'PAID' ? t('education.paidInFull') : t('education.paymentRecorded'));
      onClose();
    } catch (e) { setError(applyServerErrors(form, e)); }
  });
  const amount = Number(form.watch('amount'));
  return (
    <FormDialog open onOpenChange={(o) => !o && onClose()} title={t('education.recordPayment')} description={t('education.paymentBody', { balance: usd(balance) })}
      form={form} onSubmit={onSubmit} error={error} submitLabel={t('education.recordPayment')}>
      <div className="grid grid-cols-2 gap-4">
        <FormField control={form.control} name="paidOn" render={({ field }) => (
          <FormItem><FormLabel>{t('education.paidOn')}</FormLabel><FormControl><Input type="date" max={isoToday()} {...field} /></FormControl><FormMessage /></FormItem>
        )} />
        <FormField control={form.control} name="amount" render={({ field }) => (
          <FormItem>
            <FormLabel>{t('education.amountUsd')}</FormLabel>
            <FormControl><Input inputMode="decimal" {...field} /></FormControl>
            {amount > 0 && <p className="text-xs text-muted-foreground tabular">{khr(amount)}</p>}
            <FormMessage />
          </FormItem>
        )} />
      </div>
      <FormField control={form.control} name="method" render={({ field }) => (
        <FormItem>
          <FormLabel>{t('education.method')}</FormLabel>
          <Select value={field.value} onValueChange={field.onChange}>
            <FormControl><SelectTrigger className="w-full"><SelectValue /></SelectTrigger></FormControl>
            <SelectContent>{PAY_METHODS.map((m) => <SelectItem key={m} value={m}>{METHOD_LABEL[m]}</SelectItem>)}</SelectContent>
          </Select>
        </FormItem>
      )} />
      <FormField control={form.control} name="reference" render={({ field }) => (
        <FormItem><FormLabel>{t('education.reference')} <Optional /></FormLabel><FormControl><Input {...field} /></FormControl><FormMessage /></FormItem>
      )} />
    </FormDialog>
  );
}

const voidSchema = (t: T) => z.object({ reason: z.string().trim().min(1, t('education.errReason')).max(500) });
type VoidValues = z.infer<ReturnType<typeof voidSchema>>;

export function VoidDialog({ org, invoiceId, number, onClose }: { org: string; invoiceId: string; number: string; onClose: () => void }) {
  const { t } = useT();
  const schema = useMemo(() => voidSchema(t), [t]);
  const [error, setError] = useState<string | null>(null);
  const form = useForm<VoidValues>({ resolver: zodResolver(schema), defaultValues: { reason: '' } });
  const save = useOrgMutation(org, (v: VoidValues) => eduApi.voidInvoice(org, invoiceId, v.reason.trim()));
  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try { await save.mutateAsync(v); toast.success(t('education.invoiceVoided')); onClose(); } catch (e) { setError(applyServerErrors(form, e)); }
  });
  return (
    <FormDialog open onOpenChange={(o) => !o && onClose()} title={t('education.voidInvoice', { number })} description={t('education.voidBody')}
      form={form} onSubmit={onSubmit} error={error} submitLabel={t('education.void')}>
      <FormField control={form.control} name="reason" render={({ field }) => (
        <FormItem><FormLabel>{t('education.reason')}</FormLabel><FormControl><Textarea rows={2} {...field} /></FormControl><FormMessage /></FormItem>
      )} />
    </FormDialog>
  );
}

export { NONE as NO_ITEM };
