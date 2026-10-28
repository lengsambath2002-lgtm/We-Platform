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
import { bizApi, type Customer, type PaymentMethod, PAYMENT_METHODS, type Product } from '@/lib/api/business';
import { useOrgMutation } from '@/lib/api/queries';
import { khr, usd } from '@/lib/money';

import { METHOD_LABEL } from './ui';

type T = ReturnType<typeof useT>['t'];
const PHONE = /^$|^[0-9+()\-. ]{6,30}$/;
const MONEY = /^\d{1,10}(\.\d{1,2})?$/;
const opt = (v: string) => (v.trim() ? v.trim() : null);

function ErrorLine({ error }: { error: string | null }) {
  return error ? <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p> : null;
}

// ── Customer ──────────────────────────────────────────────────────────────────────

const customerSchema = (t: T) => z.object({
  kind: z.enum(['COMPANY', 'INDIVIDUAL']),
  name: z.string().trim().min(1, t('business.errName')).max(200),
  contactName: z.string().max(120),
  email: z.string().max(160).refine((v) => !v.trim() || z.string().email().safeParse(v.trim()).success, t('business.errEmail')),
  phone: z.string().refine((v) => PHONE.test(v.trim()), t('business.errPhone')),
  address: z.string().max(500),
  taxId: z.string().max(40),
  notes: z.string().max(2000),
});
type CustomerValues = z.infer<ReturnType<typeof customerSchema>>;

/** Adds or edits a customer (a company or a person); `onSaved` gets the saved one. */
export function CustomerDialog({ org, customer, open, onOpenChange, onSaved }: {
  org: string; customer?: Customer; open: boolean; onOpenChange: (v: boolean) => void; onSaved?: (c: Customer) => void;
}) {
  const { t } = useT();
  const schema = useMemo(() => customerSchema(t), [t]);
  const [error, setError] = useState<string | null>(null);
  const form = useForm<CustomerValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      kind: customer?.kind ?? 'COMPANY', name: customer?.name ?? '', contactName: customer?.contactName ?? '', email: customer?.email ?? '',
      phone: customer?.phone ?? '', address: customer?.address ?? '', taxId: customer?.taxId ?? '', notes: customer?.notes ?? '',
    },
  });
  const save = useOrgMutation(org, (v: CustomerValues) => bizApi.saveCustomer(org, customer?.id ?? null, {
    kind: v.kind, name: v.name.trim(), contactName: opt(v.contactName), email: opt(v.email), phone: opt(v.phone),
    address: opt(v.address), taxId: opt(v.taxId), notes: opt(v.notes),
  }));
  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try {
      const c = await save.mutateAsync(v);
      toast.success(t('business.customerSaved'));
      onOpenChange(false);
      onSaved?.(c);
    } catch (e) { setError(applyServerErrors(form, e)); }
  });
  const company = form.watch('kind') === 'COMPANY';

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{customer ? t('business.editCustomer') : t('business.newCustomer')}</DialogTitle>
          <DialogDescription>{t('business.customerBody')}</DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={onSubmit} className="space-y-4" noValidate>
            <ErrorLine error={error} />
            <FormField control={form.control} name="kind" render={({ field }) => (
              <FormItem>
                <FormLabel>{t('business.customerKind')}</FormLabel>
                <Select value={field.value} onValueChange={field.onChange}>
                  <FormControl><SelectTrigger className="w-full"><SelectValue /></SelectTrigger></FormControl>
                  <SelectContent>
                    <SelectItem value="COMPANY">{t('business.kind_COMPANY')}</SelectItem>
                    <SelectItem value="INDIVIDUAL">{t('business.kind_INDIVIDUAL')}</SelectItem>
                  </SelectContent>
                </Select>
              </FormItem>
            )} />
            <FormField control={form.control} name="name" render={({ field }) => (
              <FormItem><FormLabel>{company ? t('business.companyName') : t('business.personName')}</FormLabel><FormControl><Input autoFocus {...field} /></FormControl><FormMessage /></FormItem>
            )} />
            {company && (
              <FormField control={form.control} name="contactName" render={({ field }) => (
                <FormItem><FormLabel>{t('business.contactPerson')} <span className="font-normal text-muted-foreground">{t('common.optional')}</span></FormLabel><FormControl><Input {...field} /></FormControl><FormMessage /></FormItem>
              )} />
            )}
            <div className="grid gap-4 sm:grid-cols-2">
              <FormField control={form.control} name="email" render={({ field }) => (
                <FormItem><FormLabel>{t('business.email')}</FormLabel><FormControl><Input type="email" inputMode="email" {...field} /></FormControl><FormMessage /></FormItem>
              )} />
              <FormField control={form.control} name="phone" render={({ field }) => (
                <FormItem><FormLabel>{t('business.phone')}</FormLabel><FormControl><Input type="tel" inputMode="tel" {...field} /></FormControl><FormMessage /></FormItem>
              )} />
            </div>
            <FormField control={form.control} name="address" render={({ field }) => (
              <FormItem><FormLabel>{t('business.address')}</FormLabel><FormControl><Textarea rows={2} {...field} /></FormControl><FormMessage /></FormItem>
            )} />
            <FormField control={form.control} name="taxId" render={({ field }) => (
              <FormItem><FormLabel>{t('business.taxId')}</FormLabel><FormControl><Input {...field} /></FormControl><FormMessage /></FormItem>
            )} />
            <FormField control={form.control} name="notes" render={({ field }) => (
              <FormItem><FormLabel>{t('business.notes')}</FormLabel><FormControl><Textarea rows={2} {...field} /></FormControl><FormMessage /></FormItem>
            )} />
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>{t('common.cancel')}</Button>
              <Button type="submit" disabled={form.formState.isSubmitting}>{form.formState.isSubmitting ? t('common.saving') : t('common.save')}</Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}

// ── Product ───────────────────────────────────────────────────────────────────────

const productSchema = (t: T) => z.object({
  nameEn: z.string().trim().min(1, t('business.errName')).max(160),
  nameKm: z.string().max(160),
  unit: z.string().trim().min(1, t('business.errUnit')).max(20),
  unitPrice: z.string().refine((v) => MONEY.test(v.trim()), t('business.errMoney')),
  vat: z.boolean(),
  active: z.boolean(),
});
type ProductValues = z.infer<ReturnType<typeof productSchema>>;

export function ProductDialog({ org, product, open, onOpenChange }: { org: string; product?: Product; open: boolean; onOpenChange: (v: boolean) => void }) {
  const { t } = useT();
  const schema = useMemo(() => productSchema(t), [t]);
  const [error, setError] = useState<string | null>(null);
  const form = useForm<ProductValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      nameEn: product?.nameEn ?? '', nameKm: product?.nameKm ?? '', unit: product?.unit ?? t('business.defaultUnit'),
      unitPrice: product ? product.unitPrice.toFixed(2) : '', vat: product?.vat ?? true, active: product?.active ?? true,
    },
  });
  const save = useOrgMutation(org, (v: ProductValues) => bizApi.saveProduct(org, product?.id ?? null, {
    nameEn: v.nameEn.trim(), nameKm: opt(v.nameKm), unit: v.unit.trim(), unitPrice: v.unitPrice.trim(), vat: v.vat, active: v.active,
  }));
  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try {
      await save.mutateAsync(v);
      toast.success(t('business.productSaved'));
      onOpenChange(false);
    } catch (e) { setError(applyServerErrors(form, e)); }
  });
  const price = Number(form.watch('unitPrice'));

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{product ? t('business.editProduct') : t('business.newProduct')}</DialogTitle>
          <DialogDescription>{t('business.productBody')}</DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={onSubmit} className="space-y-4" noValidate>
            <ErrorLine error={error} />
            <FormField control={form.control} name="nameEn" render={({ field }) => (
              <FormItem><FormLabel>{t('business.nameEn')}</FormLabel><FormControl><Input autoFocus {...field} /></FormControl><FormMessage /></FormItem>
            )} />
            <FormField control={form.control} name="nameKm" render={({ field }) => (
              <FormItem><FormLabel>{t('business.nameKm')} <span className="font-normal text-muted-foreground">{t('common.optional')}</span></FormLabel><FormControl><Input lang="km" {...field} /></FormControl><FormMessage /></FormItem>
            )} />
            <div className="grid grid-cols-2 gap-4">
              <FormField control={form.control} name="unit" render={({ field }) => (
                <FormItem><FormLabel>{t('business.unit')}</FormLabel><FormControl><Input {...field} /></FormControl><FormMessage /></FormItem>
              )} />
              <FormField control={form.control} name="unitPrice" render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('business.unitPriceUsd')}</FormLabel>
                  <FormControl><Input inputMode="decimal" {...field} /></FormControl>
                  {price > 0 && <p className="text-xs text-muted-foreground tabular">{khr(price)}</p>}
                  <FormMessage />
                </FormItem>
              )} />
            </div>
            <FormField control={form.control} name="vat" render={({ field }) => (
              <FormItem className="flex items-center gap-2"><FormControl><Checkbox checked={field.value} onCheckedChange={(x) => field.onChange(x === true)} /></FormControl><FormLabel className="!mt-0 font-normal">{t('business.vatApplies')}</FormLabel></FormItem>
            )} />
            {product && (
              <FormField control={form.control} name="active" render={({ field }) => (
                <FormItem className="flex items-center gap-2"><FormControl><Checkbox checked={field.value} onCheckedChange={(x) => field.onChange(x === true)} /></FormControl><FormLabel className="!mt-0 font-normal">{t('business.activeProduct')}</FormLabel></FormItem>
              )} />
            )}
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>{t('common.cancel')}</Button>
              <Button type="submit" disabled={form.formState.isSubmitting}>{form.formState.isSubmitting ? t('common.saving') : t('common.save')}</Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}

// ── Payment ───────────────────────────────────────────────────────────────────────

const paymentSchema = (t: T, balance: number) => z.object({
  paidOn: z.string().min(1, t('business.errDate')),
  amount: z.string().refine((v) => MONEY.test(v.trim()) && Number(v) > 0, t('business.errMoney'))
    .refine((v) => Number(v) <= balance + 1e-9, t('business.errOverBalance')),
  method: z.enum(PAYMENT_METHODS as [PaymentMethod, ...PaymentMethod[]]),
  reference: z.string().max(120),
});
type PaymentValues = z.infer<ReturnType<typeof paymentSchema>>;

/** Records money received; the amount starts at the balance and can't go above it. */
export function PaymentDialog({ org, invoiceId, balance, open, onOpenChange }: {
  org: string; invoiceId: string; balance: number; open: boolean; onOpenChange: (v: boolean) => void;
}) {
  const { t } = useT();
  const schema = useMemo(() => paymentSchema(t, balance), [t, balance]);
  const [error, setError] = useState<string | null>(null);
  const today = new Date();
  const iso = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
  const form = useForm<PaymentValues>({
    resolver: zodResolver(schema),
    defaultValues: { paidOn: iso, amount: balance.toFixed(2), method: 'CASH', reference: '' },
  });
  const pay = useOrgMutation(org, (v: PaymentValues) => bizApi.pay(org, invoiceId, { ...v, amount: v.amount.trim(), reference: opt(v.reference) }));
  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try {
      const inv = await pay.mutateAsync(v);
      toast.success(inv.status === 'PAID' ? t('business.paidInFull') : t('business.paymentRecorded'));
      onOpenChange(false);
    } catch (e) { setError(applyServerErrors(form, e)); }
  });
  const amount = Number(form.watch('amount'));

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t('business.recordPayment')}</DialogTitle>
          <DialogDescription>{t('business.paymentBody', { balance: usd(balance) })}</DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={onSubmit} className="space-y-4" noValidate>
            <ErrorLine error={error} />
            <div className="grid grid-cols-2 gap-4">
              <FormField control={form.control} name="paidOn" render={({ field }) => (
                <FormItem><FormLabel>{t('business.paidOn')}</FormLabel><FormControl><Input type="date" {...field} /></FormControl><FormMessage /></FormItem>
              )} />
              <FormField control={form.control} name="amount" render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('business.amountUsd')}</FormLabel>
                  <FormControl><Input inputMode="decimal" {...field} /></FormControl>
                  {amount > 0 && <p className="text-xs text-muted-foreground tabular">{khr(amount)}</p>}
                  <FormMessage />
                </FormItem>
              )} />
            </div>
            <FormField control={form.control} name="method" render={({ field }) => (
              <FormItem>
                <FormLabel>{t('business.method')}</FormLabel>
                <Select value={field.value} onValueChange={field.onChange}>
                  <FormControl><SelectTrigger className="w-full"><SelectValue /></SelectTrigger></FormControl>
                  <SelectContent>{PAYMENT_METHODS.map((m) => <SelectItem key={m} value={m}>{METHOD_LABEL[m]}</SelectItem>)}</SelectContent>
                </Select>
              </FormItem>
            )} />
            <FormField control={form.control} name="reference" render={({ field }) => (
              <FormItem><FormLabel>{t('business.reference')} <span className="font-normal text-muted-foreground">{t('common.optional')}</span></FormLabel><FormControl><Input {...field} /></FormControl><FormMessage /></FormItem>
            )} />
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>{t('common.cancel')}</Button>
              <Button type="submit" disabled={form.formState.isSubmitting}>{t('business.recordPayment')}</Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
