'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { Plus, Trash2, UserPlus } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useMemo, useState } from 'react';
import { useFieldArray, useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { applyServerErrors } from '@/components/forms/server-errors';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { useT } from '@/i18n/provider';
import { bizApi, type Invoice, type Line, type Quote, useBizSettings, useCustomers, useProducts } from '@/lib/api/business';
import { useOrgMutation } from '@/lib/api/queries';
import { documentTotals, khr, usd } from '@/lib/money';

import { CustomerDialog } from './dialogs';
import { productName } from './ui';

type T = ReturnType<typeof useT>['t'];
const NONE = 'none';
const MONEY = /^\d{1,10}(\.\d{1,2})?$/;
const QTY = /^\d{1,6}(\.\d{1,3})?$/;
const PCT = /^(100(\.0{1,2})?|\d{1,2}(\.\d{1,2})?)$/;

const makeSchema = (t: T, kind: 'quote' | 'invoice') => z.object({
  customerId: z.string().min(1, t('business.errCustomer')),
  issueDate: z.string(),
  endDate: z.string(),
  vatEnabled: z.boolean(),
  notes: z.string().max(4000),
  lines: z.array(z.object({
    productId: z.string(),
    description: z.string().max(500),
    unit: z.string().max(20),
    quantity: z.string().refine((v) => QTY.test(v.trim()) && Number(v) > 0, t('business.errQty')),
    unitPrice: z.string().refine((v) => MONEY.test(v.trim()), t('business.errMoney')),
    discountPct: z.string().refine((v) => !v.trim() || PCT.test(v.trim()), t('business.errPct')),
    vat: z.boolean(),
  }).refine((l) => l.description.trim() || l.productId !== NONE, { path: ['description'], message: t('business.errDescription') }))
    .min(1, t('business.errNoLines')).max(100),
})
  .refine((v) => kind === 'invoice' || !!v.issueDate, { path: ['issueDate'], message: t('business.errDate') })
  .refine((v) => !v.issueDate || !v.endDate || v.endDate >= v.issueDate, { path: ['endDate'], message: t('business.errEndBeforeStart') });
type Values = z.infer<ReturnType<typeof makeSchema>>;
type LineValues = Values['lines'][number];

const blankLine = (vat: boolean): LineValues => ({ productId: NONE, description: '', unit: '', quantity: '1', unitPrice: '', discountPct: '', vat });
const fromLine = (l: Line): LineValues => ({
  productId: l.productId ?? NONE, description: l.description, unit: l.unit ?? '', quantity: String(Number(l.quantity)),
  unitPrice: Number(l.unitPrice).toFixed(2), discountPct: Number(l.discountPct) ? String(Number(l.discountPct)) : '', vat: l.vat,
});
const todayIso = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

/**
 * Creates or edits a quote or a draft invoice: customer, dates, lines (a product or free text, quantity, price,
 * discount, VAT) and notes. Totals update as you type, with the same rounding as the server — which computes
 * the real ones when it saves.
 */
export function DocumentForm({ org, kind, quote, invoice, customerId }: {
  org: string; kind: 'quote' | 'invoice'; quote?: Quote; invoice?: Invoice; customerId?: string;
}) {
  const { t } = useT();
  const router = useRouter();
  const schema = useMemo(() => makeSchema(t, kind), [t, kind]);
  const settings = useBizSettings(org);
  const customers = useCustomers(org, { size: 100 });
  const products = useProducts(org);
  const [error, setError] = useState<string | null>(null);
  const [newCustomer, setNewCustomer] = useState(false);
  const existing = quote ?? invoice;
  const vatDefault = settings.data?.vatDefault ?? true;
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      customerId: quote?.customer.id ?? invoice?.customer.id ?? customerId ?? '',
      issueDate: quote?.issueDate ?? invoice?.issueDate ?? (kind === 'quote' ? todayIso() : ''),
      endDate: quote?.validUntil ?? invoice?.dueDate ?? '',
      vatEnabled: existing?.vatEnabled ?? true,
      notes: existing?.notes ?? '',
      lines: existing ? existing.lines.map(fromLine) : [blankLine(true)],
    },
  });
  // A new document starts with the organisation's VAT default once the settings are known.
  const [seeded, setSeeded] = useState(!!existing);
  if (!seeded && settings.data) {
    setSeeded(true);
    form.setValue('vatEnabled', settings.data.vatDefault);
  }
  const lines = useFieldArray({ control: form.control, name: 'lines' });
  const v = form.watch();
  const totals = documentTotals(v.lines.map((l) => ({ quantity: Number(l.quantity), unitPrice: Number(l.unitPrice), discountPct: Number(l.discountPct || 0), vat: l.vat })),
    v.vatEnabled, settings.data?.vatRate ?? 10);
  const save = useOrgMutation(org, (x: Values): Promise<{ id: string }> => {
    const body = {
      customerId: x.customerId, vatEnabled: x.vatEnabled, notes: x.notes.trim() || null,
      lines: x.lines.map((l) => ({
        productId: l.productId === NONE ? null : l.productId, description: l.description.trim() || null, unit: l.unit.trim() || null,
        quantity: l.quantity.trim(), unitPrice: l.unitPrice.trim(), discountPct: l.discountPct.trim() || '0', vat: l.vat,
      })),
      ...(kind === 'quote' ? { issueDate: x.issueDate, validUntil: x.endDate || null } : { issueDate: x.issueDate || null, dueDate: x.endDate || null }),
    };
    return kind === 'quote' ? bizApi.saveQuote(org, quote?.id ?? null, body) : bizApi.saveInvoice(org, invoice?.id ?? null, body);
  });
  const onSubmit = form.handleSubmit(async (x) => {
    setError(null);
    try {
      const saved = await save.mutateAsync(x);
      toast.success(t(kind === 'quote' ? 'business.quoteSaved' : 'business.invoiceSaved'));
      router.push(`/${org}/business/${kind === 'quote' ? 'quotes' : 'invoices'}/${saved.id}`);
    } catch (e) { setError(applyServerErrors(form, e)); }
  });
  const pickProduct = (i: number, id: string) => {
    form.setValue(`lines.${i}.productId`, id);
    const p = products.data?.find((x) => x.id === id);
    if (!p) return;
    form.setValue(`lines.${i}.description`, productName(p));
    form.setValue(`lines.${i}.unit`, p.unit);
    form.setValue(`lines.${i}.unitPrice`, Number(p.unitPrice).toFixed(2));
    form.setValue(`lines.${i}.vat`, p.vat);
  };
  const activeCustomers = (customers.data?.items ?? []).filter((c) => !c.archived);
  const current = existing && !activeCustomers.some((c) => c.id === existing.customer.id) ? existing.customer : null;

  return (
    <Form {...form}>
      <form onSubmit={onSubmit} className="space-y-6" noValidate>
        {error && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}
        <section className="grid gap-4 rounded-lg border bg-card p-4 sm:grid-cols-2 lg:grid-cols-4">
          <FormField control={form.control} name="customerId" render={({ field }) => (
            <FormItem className="sm:col-span-2">
              <FormLabel>{t('business.customer')}</FormLabel>
              <div className="flex gap-2">
                <Select value={field.value} onValueChange={field.onChange} disabled={customers.isPending}>
                  <FormControl><SelectTrigger className="w-full min-w-0"><SelectValue placeholder={t('business.chooseCustomer')} /></SelectTrigger></FormControl>
                  <SelectContent>
                    {current && <SelectItem value={current.id}>{current.name}</SelectItem>}
                    {activeCustomers.map((c) => <SelectItem key={c.id} value={c.id}>{c.name}</SelectItem>)}
                  </SelectContent>
                </Select>
                <Button type="button" variant="outline" size="icon" onClick={() => setNewCustomer(true)} aria-label={t('business.newCustomer')} title={t('business.newCustomer')}><UserPlus className="size-4" /></Button>
              </div>
              <FormMessage />
            </FormItem>
          )} />
          <FormField control={form.control} name="issueDate" render={({ field }) => (
            <FormItem>
              <FormLabel>{kind === 'quote' ? t('business.quoteDate') : t('business.issueDate')}</FormLabel>
              <FormControl><Input type="date" {...field} /></FormControl>
              {kind === 'invoice' && !field.value && <p className="text-xs text-muted-foreground">{t('business.issueDateHint')}</p>}
              <FormMessage />
            </FormItem>
          )} />
          <FormField control={form.control} name="endDate" render={({ field }) => (
            <FormItem>
              <FormLabel>{kind === 'quote' ? t('business.validUntil') : t('business.dueDate')} <span className="font-normal text-muted-foreground">{t('common.optional')}</span></FormLabel>
              <FormControl><Input type="date" min={v.issueDate || undefined} {...field} /></FormControl>
              {kind === 'invoice' && !field.value && <p className="text-xs text-muted-foreground">{t('business.dueDateHint', { days: settings.data?.paymentTermsDays ?? 30 })}</p>}
              <FormMessage />
            </FormItem>
          )} />
        </section>

        <section aria-labelledby="lines-h" className="space-y-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h2 id="lines-h" className="text-base font-semibold">{t('business.lines')}</h2>
            <FormField control={form.control} name="vatEnabled" render={({ field }) => (
              <FormItem className="flex items-center gap-2"><FormControl><Checkbox checked={field.value} onCheckedChange={(x) => field.onChange(x === true)} /></FormControl><FormLabel className="!mt-0 font-normal">{t('business.chargeVat', { rate: settings.data?.vatRate ?? 10 })}</FormLabel></FormItem>
            )} />
          </div>
          <div className="hidden grid-cols-[minmax(0,2fr)_minmax(0,3fr)_5rem_7rem_5rem_3rem_7rem_2.5rem] gap-2 px-1 text-xs font-medium text-muted-foreground lg:grid">
            <span>{t('business.product')}</span><span>{t('business.description')}</span><span>{t('business.qty')}</span><span>{t('business.unitPrice')}</span>
            <span>{t('business.discountPct')}</span><span>{t('business.vat')}</span><span className="text-right">{t('business.amount')}</span><span />
          </div>
          <ol className="space-y-3 lg:space-y-2">
            {lines.fields.map((f, i) => (
              <li key={f.id} className="grid grid-cols-2 gap-2 rounded-lg border bg-card p-3 lg:grid-cols-[minmax(0,2fr)_minmax(0,3fr)_5rem_7rem_5rem_3rem_7rem_2.5rem] lg:items-start lg:border-0 lg:bg-transparent lg:p-0">
                <div className="col-span-2 lg:col-span-1">
                  <Label className="mb-1 text-xs text-muted-foreground lg:sr-only" htmlFor={`l${i}-p`}>{t('business.product')}</Label>
                  <Select value={v.lines[i]?.productId ?? NONE} onValueChange={(x) => pickProduct(i, x)}>
                    <SelectTrigger id={`l${i}-p`} className="w-full min-w-0"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value={NONE}>{t('business.freeText')}</SelectItem>
                      {(products.data ?? []).map((p) => <SelectItem key={p.id} value={p.id}>{productName(p)} · {usd(p.unitPrice)}</SelectItem>)}
                    </SelectContent>
                  </Select>
                </div>
                <FormField control={form.control} name={`lines.${i}.description`} render={({ field }) => (
                  <FormItem className="col-span-2 gap-1 lg:col-span-1"><FormLabel className="text-xs text-muted-foreground lg:sr-only">{t('business.description')}</FormLabel><FormControl><Input {...field} /></FormControl><FormMessage /></FormItem>
                )} />
                <FormField control={form.control} name={`lines.${i}.quantity`} render={({ field }) => (
                  <FormItem className="gap-1"><FormLabel className="text-xs text-muted-foreground lg:sr-only">{t('business.qty')}</FormLabel><FormControl><Input inputMode="decimal" className="tabular" {...field} /></FormControl><FormMessage /></FormItem>
                )} />
                <FormField control={form.control} name={`lines.${i}.unitPrice`} render={({ field }) => (
                  <FormItem className="gap-1"><FormLabel className="text-xs text-muted-foreground lg:sr-only">{t('business.unitPrice')}</FormLabel><FormControl><Input inputMode="decimal" className="tabular" placeholder="0.00" {...field} /></FormControl><FormMessage /></FormItem>
                )} />
                <FormField control={form.control} name={`lines.${i}.discountPct`} render={({ field }) => (
                  <FormItem className="gap-1"><FormLabel className="text-xs text-muted-foreground lg:sr-only">{t('business.discountPct')}</FormLabel><FormControl><Input inputMode="decimal" className="tabular" placeholder="0" {...field} /></FormControl><FormMessage /></FormItem>
                )} />
                <FormField control={form.control} name={`lines.${i}.vat`} render={({ field }) => (
                  <FormItem className="flex items-center gap-2 self-end pb-2 lg:self-center lg:pb-0 lg:pt-2.5">
                    <FormControl><Checkbox checked={field.value} disabled={!v.vatEnabled} onCheckedChange={(x) => field.onChange(x === true)} aria-label={t('business.lineVat', { n: i + 1 })} /></FormControl>
                    <span className="text-xs text-muted-foreground lg:sr-only">{t('business.vat')}</span>
                  </FormItem>
                )} />
                <p className="self-center text-right text-sm font-medium tabular lg:pt-2" aria-label={t('business.amount')}>{usd(totals.nets[i] ?? 0)}</p>
                <Button type="button" variant="ghost" size="icon" className="justify-self-end text-muted-foreground" disabled={lines.fields.length === 1}
                  onClick={() => lines.remove(i)} aria-label={t('business.removeLine', { n: i + 1 })}><Trash2 className="size-4" /></Button>
              </li>
            ))}
          </ol>
          <Button type="button" variant="outline" size="sm" onClick={() => lines.append(blankLine(v.vatEnabled && vatDefault))}><Plus className="size-4" />{t('business.addLine')}</Button>
          {form.formState.errors.lines?.root?.message && <p className="text-sm text-destructive">{form.formState.errors.lines.root.message}</p>}
        </section>

        <div className="grid gap-6 md:grid-cols-[1fr_320px]">
          <FormField control={form.control} name="notes" render={({ field }) => (
            <FormItem><FormLabel>{t('business.notesTerms')}</FormLabel><FormControl><Textarea rows={4} {...field} /></FormControl><FormMessage /></FormItem>
          )} />
          <dl className="space-y-1.5 rounded-lg border bg-card p-4 text-sm" aria-live="polite">
            <div className="flex justify-between gap-2"><dt className="text-muted-foreground">{t('business.subtotal')}</dt><dd className="tabular">{usd(totals.subtotal)}</dd></div>
            {totals.discount > 0 && <div className="flex justify-between gap-2"><dt className="text-muted-foreground">{t('business.discountIncluded')}</dt><dd className="tabular">{usd(totals.discount)}</dd></div>}
            <div className="flex justify-between gap-2"><dt className="text-muted-foreground">{t('business.vatRate', { rate: settings.data?.vatRate ?? 10 })}</dt><dd className="tabular">{usd(totals.vat)}</dd></div>
            <div className="flex justify-between gap-2 border-t pt-2 text-base font-semibold"><dt>{t('business.total')}</dt><dd className="tabular">{usd(totals.total)}</dd></div>
            <div className="flex justify-between gap-2 text-xs text-muted-foreground"><dt>{t('business.inRiel')}</dt><dd className="tabular">{khr(totals.total)}</dd></div>
          </dl>
        </div>

        <div className="flex flex-wrap justify-end gap-2">
          <Button type="button" variant="outline" onClick={() => router.back()}>{t('common.cancel')}</Button>
          <Button type="submit" disabled={form.formState.isSubmitting}>
            {form.formState.isSubmitting ? t('common.saving') : kind === 'quote' ? t('business.saveQuote') : t('business.saveDraft')}
          </Button>
        </div>
      </form>
      {newCustomer && <CustomerDialog org={org} open onOpenChange={setNewCustomer} onSaved={(c) => form.setValue('customerId', c.id, { shouldValidate: true })} />}
    </Form>
  );
}
