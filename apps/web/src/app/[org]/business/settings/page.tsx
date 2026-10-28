'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { Plus, Tags } from 'lucide-react';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { categoryName } from '@/components/business/ui';
import { applyServerErrors } from '@/components/forms/server-errors';
import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton, PermissionDenied } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { useT } from '@/i18n/provider';
import { bizApi, type BizSettings, type Category, useBizSettings, useCategories } from '@/lib/api/business';
import { useOrgMutation } from '@/lib/api/queries';
import { useOrg } from '@/lib/org-context';
import { cn } from '@/lib/utils';

type T = ReturnType<typeof useT>['t'];
const opt = (v: string) => (v.trim() ? v.trim() : null);

const settingsSchema = (t: T) => z.object({
  legalName: z.string().max(200),
  address: z.string().max(500),
  taxId: z.string().max(40),
  phone: z.string().refine((v) => /^$|^[0-9+()\-. ]{6,30}$/.test(v.trim()), t('business.errPhone')),
  email: z.string().max(160).refine((v) => !v.trim() || z.string().email().safeParse(v.trim()).success, t('business.errEmail')),
  invoiceFooter: z.string().max(2000),
  paymentTermsDays: z.string().refine((v) => /^\d{1,3}$/.test(v.trim()) && Number(v) <= 365, t('business.errTerms')),
  vatDefault: z.boolean(),
});
type SettingsValues = z.infer<ReturnType<typeof settingsSchema>>;

function SettingsForm({ org, settings }: { org: string; settings: BizSettings }) {
  const { t } = useT();
  const schema = useMemo(() => settingsSchema(t), [t]);
  const [error, setError] = useState<string | null>(null);
  const form = useForm<SettingsValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      legalName: settings.legalName ?? '', address: settings.address ?? '', taxId: settings.taxId ?? '', phone: settings.phone ?? '',
      email: settings.email ?? '', invoiceFooter: settings.invoiceFooter ?? '', paymentTermsDays: String(settings.paymentTermsDays), vatDefault: settings.vatDefault,
    },
  });
  const save = useOrgMutation(org, (v: SettingsValues) => bizApi.saveSettings(org, {
    legalName: opt(v.legalName), address: opt(v.address), taxId: opt(v.taxId), phone: opt(v.phone), email: opt(v.email),
    invoiceFooter: opt(v.invoiceFooter), paymentTermsDays: Number(v.paymentTermsDays), vatDefault: v.vatDefault,
  }));
  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try { await save.mutateAsync(v); toast.success(t('business.settingsSaved')); } catch (e) { setError(applyServerErrors(form, e)); }
  });
  return (
    <Form {...form}>
      <form onSubmit={onSubmit} className="space-y-4 rounded-lg border bg-card p-4" noValidate>
        {error && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}
        <FormField control={form.control} name="legalName" render={({ field }) => (
          <FormItem><FormLabel>{t('business.legalName')}</FormLabel><FormControl><Input placeholder={settings.orgName} {...field} /></FormControl><FormDescription>{t('business.legalNameHint')}</FormDescription><FormMessage /></FormItem>
        )} />
        <FormField control={form.control} name="address" render={({ field }) => (
          <FormItem><FormLabel>{t('business.address')}</FormLabel><FormControl><Textarea rows={2} {...field} /></FormControl><FormMessage /></FormItem>
        )} />
        <div className="grid gap-4 sm:grid-cols-3">
          <FormField control={form.control} name="taxId" render={({ field }) => (
            <FormItem><FormLabel>{t('business.vatTin')}</FormLabel><FormControl><Input {...field} /></FormControl><FormMessage /></FormItem>
          )} />
          <FormField control={form.control} name="phone" render={({ field }) => (
            <FormItem><FormLabel>{t('business.phone')}</FormLabel><FormControl><Input type="tel" {...field} /></FormControl><FormMessage /></FormItem>
          )} />
          <FormField control={form.control} name="email" render={({ field }) => (
            <FormItem><FormLabel>{t('business.email')}</FormLabel><FormControl><Input type="email" {...field} /></FormControl><FormMessage /></FormItem>
          )} />
        </div>
        <FormField control={form.control} name="invoiceFooter" render={({ field }) => (
          <FormItem><FormLabel>{t('business.invoiceFooter')}</FormLabel><FormControl><Textarea rows={3} placeholder={t('business.invoiceFooterPlaceholder')} {...field} /></FormControl><FormDescription>{t('business.invoiceFooterHint')}</FormDescription><FormMessage /></FormItem>
        )} />
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField control={form.control} name="paymentTermsDays" render={({ field }) => (
            <FormItem><FormLabel>{t('business.paymentTerms')}</FormLabel><FormControl><Input inputMode="numeric" className="w-28" {...field} /></FormControl><FormDescription>{t('business.paymentTermsHint')}</FormDescription><FormMessage /></FormItem>
          )} />
          <FormField control={form.control} name="vatDefault" render={({ field }) => (
            <FormItem className="flex items-center gap-2 sm:pt-7"><FormControl><Checkbox checked={field.value} onCheckedChange={(x) => field.onChange(x === true)} /></FormControl><FormLabel className="!mt-0 font-normal">{t('business.vatDefault', { rate: settings.vatRate })}</FormLabel></FormItem>
          )} />
        </div>
        <div className="flex justify-end"><Button type="submit" disabled={form.formState.isSubmitting}>{form.formState.isSubmitting ? t('common.saving') : t('common.save')}</Button></div>
      </form>
    </Form>
  );
}

const categorySchema = (t: T) => z.object({
  nameEn: z.string().trim().min(1, t('business.errName')).max(60),
  nameKm: z.string().trim().min(1, t('business.errName')).max(60),
  active: z.boolean(),
});
type CategoryValues = z.infer<ReturnType<typeof categorySchema>>;

function CategoryDialog({ org, category, onClose }: { org: string; category: Category | null; onClose: () => void }) {
  const { t } = useT();
  const schema = useMemo(() => categorySchema(t), [t]);
  const [error, setError] = useState<string | null>(null);
  const form = useForm<CategoryValues>({ resolver: zodResolver(schema), defaultValues: { nameEn: category?.nameEn ?? '', nameKm: category?.nameKm ?? '', active: category?.active ?? true } });
  const save = useOrgMutation(org, (v: CategoryValues) => bizApi.saveCategory(org, category?.id ?? null, v));
  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try { await save.mutateAsync(v); toast.success(t('business.categorySaved')); onClose(); } catch (e) { setError(applyServerErrors(form, e)); }
  });
  return (
    <Dialog open onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{category ? t('business.editCategory') : t('business.newCategory')}</DialogTitle>
          <DialogDescription>{t('business.categoryBody')}</DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={onSubmit} className="space-y-4" noValidate>
            {error && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}
            <FormField control={form.control} name="nameEn" render={({ field }) => (
              <FormItem><FormLabel>{t('business.nameEn')}</FormLabel><FormControl><Input autoFocus {...field} /></FormControl><FormMessage /></FormItem>
            )} />
            <FormField control={form.control} name="nameKm" render={({ field }) => (
              <FormItem><FormLabel>{t('business.nameKm')}</FormLabel><FormControl><Input lang="km" {...field} /></FormControl><FormMessage /></FormItem>
            )} />
            {category && (
              <FormField control={form.control} name="active" render={({ field }) => (
                <FormItem className="flex items-center gap-2"><FormControl><Checkbox checked={field.value} onCheckedChange={(x) => field.onChange(x === true)} /></FormControl><FormLabel className="!mt-0 font-normal">{t('business.categoryActive')}</FormLabel></FormItem>
              )} />
            )}
            <DialogFooter>
              <Button type="button" variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
              <Button type="submit" disabled={form.formState.isSubmitting}>{t('common.save')}</Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}

/** Admins: the company details invoices print, the defaults, and the expense categories. */
export default function BusinessSettings() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const manage = can('business.manage');
  const settings = useBizSettings(slug);
  const categories = useCategories(slug);
  const [editing, setEditing] = useState<Category | 'new' | null>(null);
  if (!manage) return <PermissionDenied />;

  return (
    <div className="mx-auto max-w-3xl space-y-8">
      <PageHeader title={t('business.settingsTitle')} subtitle={t('business.settingsSubtitle')} />
      <section aria-labelledby="co-h" className="space-y-3">
        <h2 id="co-h" className="text-base font-semibold">{t('business.companyDetails')}</h2>
        {settings.error ? <ErrorState error={settings.error} retry={() => settings.refetch()} /> : settings.isPending ? <ListSkeleton rows={4} />
          : <SettingsForm org={slug} settings={settings.data} />}
      </section>
      <section aria-labelledby="cat-h" className="space-y-3">
        <div className="flex items-center justify-between gap-2">
          <h2 id="cat-h" className="text-base font-semibold">{t('business.expenseCategories')}</h2>
          <Button size="sm" variant="outline" onClick={() => setEditing('new')}><Plus className="size-4" />{t('business.newCategory')}</Button>
        </div>
        {categories.error ? <ErrorState error={categories.error} retry={() => categories.refetch()} /> : categories.isPending ? <ListSkeleton rows={4} />
          : categories.data.length === 0 ? <EmptyState icon={Tags} title={t('business.noCategoriesTitle')} body={t('business.noCategoriesBody')} />
            : (
              <ul className="divide-y rounded-lg border bg-card">
                {categories.data.map((c) => (
                  <li key={c.id} className={cn('flex items-center justify-between gap-3 px-4 py-2.5', !c.active && 'opacity-60')}>
                    <span className="min-w-0">
                      <span className="block truncate text-sm font-medium">{categoryName(c)}</span>
                      <span className="block truncate text-xs text-muted-foreground">{c.nameEn} · {c.nameKm}{!c.active ? ` · ${t('business.inactive')}` : ''}</span>
                    </span>
                    <Button size="sm" variant="ghost" onClick={() => setEditing(c)} aria-label={t('business.editNamed', { name: categoryName(c) })}>{t('common.edit')}</Button>
                  </li>
                ))}
              </ul>
            )}
      </section>
      {editing && <CategoryDialog org={slug} category={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
    </div>
  );
}
