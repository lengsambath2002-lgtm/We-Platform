'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { Paperclip } from 'lucide-react';
import { useRouter } from 'next/navigation';
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
import { Textarea } from '@/components/ui/textarea';
import { useT } from '@/i18n/provider';
import { bizApi, type Expense, useCategories } from '@/lib/api/business';
import { errorMessage } from '@/lib/api/client';
import { useOrgMutation } from '@/lib/api/queries';
import { khr } from '@/lib/money';

import { categoryName } from './ui';

const MONEY = /^\d{1,10}(\.\d{1,2})?$/;
const ACCEPT = 'image/png,image/jpeg,image/webp,application/pdf';

const makeSchema = (t: ReturnType<typeof useT>['t']) => z.object({
  spentOn: z.string().min(1, t('business.errDate')),
  categoryId: z.string().min(1, t('business.errCategory')),
  supplier: z.string().max(160),
  amount: z.string().refine((v) => MONEY.test(v.trim()) && Number(v) > 0, t('business.errMoney')),
  vatAmount: z.string().refine((v) => !v.trim() || MONEY.test(v.trim()), t('business.errMoney')),
  notes: z.string().max(2000),
}).refine((v) => !v.vatAmount.trim() || Number(v.vatAmount) <= Number(v.amount), { path: ['vatAmount'], message: t('business.errVatOverAmount') });
type Values = z.infer<ReturnType<typeof makeSchema>>;

/**
 * Records an expense (or edits a draft): date, category, supplier, amount with the VAT part, notes and a
 * receipt. "Submit for approval" sends it to the approver; "Save draft" keeps it for later.
 */
export function ExpenseDialog({ org, expense, open, onOpenChange }: { org: string; expense?: Expense; open: boolean; onOpenChange: (v: boolean) => void }) {
  const { t } = useT();
  const router = useRouter();
  const schema = useMemo(() => makeSchema(t), [t]);
  const categories = useCategories(org);
  const [error, setError] = useState<string | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const today = new Date();
  const iso = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      spentOn: expense?.spentOn ?? iso, categoryId: expense?.category?.id ?? '', supplier: expense?.supplier ?? '',
      amount: expense ? Number(expense.amount).toFixed(2) : '', vatAmount: expense?.vatAmount != null ? Number(expense.vatAmount).toFixed(2) : '',
      notes: expense?.notes ?? '',
    },
  });
  const save = useOrgMutation(org, async ({ v, submit }: { v: Values; submit: boolean }) => {
    const body = {
      spentOn: v.spentOn, categoryId: v.categoryId, supplier: v.supplier.trim() || null, amount: v.amount.trim(),
      vatAmount: v.vatAmount.trim() || null, notes: v.notes.trim() || null, submit: false,
    };
    let e = await bizApi.saveExpense(org, expense?.id ?? null, body);
    if (file) e = await bizApi.attachReceipt(org, e.id, file);
    if (submit && e.canSubmit) e = await bizApi.submitExpense(org, e.id);
    return e;
  });
  const send = (submit: boolean) => form.handleSubmit(async (v) => {
    setError(null);
    try {
      const e = await save.mutateAsync({ v, submit });
      toast.success(t(submit ? 'business.expenseSubmitted' : 'business.expenseSaved'));
      onOpenChange(false);
      if (!expense) router.push(`/${org}/business/expenses/${e.id}`);
    } catch (e) {
      const msg = applyServerErrors(form, e);
      setError(msg ?? errorMessage(e));
    }
  });
  const amount = Number(form.watch('amount'));
  const usable = (categories.data ?? []).filter((c) => c.active || c.id === expense?.category?.id);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{expense ? t('business.editExpense') : t('business.newExpense')}</DialogTitle>
          <DialogDescription>{t('business.expenseBody')}</DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={send(true)} className="space-y-4" noValidate>
            {error && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}
            <div className="grid grid-cols-2 gap-4">
              <FormField control={form.control} name="spentOn" render={({ field }) => (
                <FormItem><FormLabel>{t('business.spentOn')}</FormLabel><FormControl><Input type="date" {...field} /></FormControl><FormMessage /></FormItem>
              )} />
              <FormField control={form.control} name="categoryId" render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('business.category')}</FormLabel>
                  <Select value={field.value} onValueChange={field.onChange} disabled={categories.isPending}>
                    <FormControl><SelectTrigger className="w-full min-w-0"><SelectValue placeholder={t('business.chooseCategory')} /></SelectTrigger></FormControl>
                    <SelectContent>{usable.map((c) => <SelectItem key={c.id} value={c.id}>{categoryName(c)}</SelectItem>)}</SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )} />
            </div>
            <FormField control={form.control} name="supplier" render={({ field }) => (
              <FormItem><FormLabel>{t('business.supplier')} <span className="font-normal text-muted-foreground">{t('common.optional')}</span></FormLabel><FormControl><Input {...field} /></FormControl><FormMessage /></FormItem>
            )} />
            <div className="grid grid-cols-2 gap-4">
              <FormField control={form.control} name="amount" render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('business.amountUsd')}</FormLabel>
                  <FormControl><Input inputMode="decimal" {...field} /></FormControl>
                  {amount > 0 && <p className="text-xs text-muted-foreground tabular">{khr(amount)}</p>}
                  <FormMessage />
                </FormItem>
              )} />
              <FormField control={form.control} name="vatAmount" render={({ field }) => (
                <FormItem><FormLabel>{t('business.vatIncluded')} <span className="font-normal text-muted-foreground">{t('common.optional')}</span></FormLabel><FormControl><Input inputMode="decimal" {...field} /></FormControl><FormMessage /></FormItem>
              )} />
            </div>
            <FormField control={form.control} name="notes" render={({ field }) => (
              <FormItem><FormLabel>{t('business.notes')}</FormLabel><FormControl><Textarea rows={2} {...field} /></FormControl><FormMessage /></FormItem>
            )} />
            <div className="space-y-1">
              <label htmlFor="receipt" className="text-sm font-medium">{t('business.receipt')} <span className="font-normal text-muted-foreground">{t('common.optional')}</span></label>
              <label htmlFor="receipt" className="flex cursor-pointer items-center gap-2 rounded-md border border-dashed px-3 py-2 text-sm text-muted-foreground hover:bg-muted/50">
                <Paperclip className="size-4 shrink-0" /><span className="truncate">{file?.name ?? expense?.receipt?.name ?? t('business.chooseReceipt')}</span>
              </label>
              <input id="receipt" type="file" accept={ACCEPT} className="sr-only" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
            </div>
            <DialogFooter className="gap-2">
              <Button type="button" variant="outline" disabled={save.isPending} onClick={send(false)}>{expense && !expense.canSubmit ? t('common.save') : t('business.saveDraft')}</Button>
              {(!expense || expense.canSubmit) && <Button type="submit" disabled={save.isPending}>{t('business.submitForApproval')}</Button>}
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
