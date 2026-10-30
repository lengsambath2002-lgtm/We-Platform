'use client';

import { ArrowLeft, Printer } from 'lucide-react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useState } from 'react';
import { toast } from 'sonner';

import { KhqrCode } from '@/components/billing/khqr';
import { ErrorState, ListSkeleton } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { tr } from '@/i18n/core';
import type { MessageKey } from '@/i18n/messages';
import { useT } from '@/i18n/provider';
import { invoiceApi, useInvoice } from '@/lib/api/billing';
import { errorMessage } from '@/lib/api/client';
import { useOrgMutation } from '@/lib/api/queries';
import { day } from '@/lib/format';
import { usd } from '@/lib/money';
import { useOrg } from '@/lib/org-context';

/** One invoice from the platform, printable, with how to pay (bank transfer, KHQR) and "We paid". */
export default function InvoicePage() {
  const { slug, org, can } = useOrg();
  const { t } = useT();
  const { id } = useParams<{ id: string }>();
  const { data: i, error, isPending, refetch } = useInvoice(slug, id);
  const [reference, setReference] = useState('');
  const claim = useOrgMutation(slug, (r: string) => invoiceApi.claim(slug, id, r));
  if (error) return <ErrorState error={error} retry={() => refetch()} />;
  if (isPending) return <ListSkeleton />;
  const p = i.payTo;
  const status = i.status === 'OPEN' ? (i.overdue ? 'billing.inv_OVERDUE' : 'billing.inv_OPEN') : `billing.inv_${i.status}`;

  return (
    <div className="mx-auto max-w-3xl">
      <div className="mb-4 flex items-center gap-2 print:hidden">
        <Link href={`/${slug}/admin/billing`} className="inline-flex flex-1 items-center gap-1 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="size-4" />{t('billing.title')}</Link>
        <Button variant="outline" size="sm" onClick={() => window.print()}><Printer className="size-4" />{t('billing.print')}</Button>
      </div>
      <article className="space-y-6 rounded-lg border bg-card p-6 print:border-0">
        <header className="flex flex-wrap justify-between gap-4">
          <div>
            <p className="text-lg font-semibold">{p.sellerName}</p>
            <p className="text-sm text-muted-foreground">{p.sellerAddress}</p>
            {p.sellerTaxId && <p className="text-sm text-muted-foreground">{t('billing.taxId', { id: p.sellerTaxId })}</p>}
          </div>
          <div className="text-right">
            <h1 className="text-xl font-semibold">{t('billing.invoice')} {i.number}</h1>
            <p className="text-sm">{tr(status as MessageKey)}</p>
            <p className="text-sm text-muted-foreground">{t('billing.issued', { date: day(i.issuedAt) })} · {t('billing.due', { date: day(i.dueAt) })}</p>
          </div>
        </header>
        <p className="text-sm"><span className="text-muted-foreground">{t('billing.billTo')}</span> <span className="font-medium">{org.name}</span></p>
        <table className="w-full text-sm">
          <thead><tr className="border-b text-left text-muted-foreground"><th className="py-2 font-normal">{t('billing.item')}</th><th className="py-2 text-right font-normal">{t('billing.qty')}</th><th className="py-2 text-right font-normal">{t('billing.unit')}</th><th className="py-2 text-right font-normal">{t('billing.amount')}</th></tr></thead>
          <tbody><tr className="border-b">
            <td className="py-2">{t('billing.line', { plan: i.planCode, cycle: tr(`billing.cycle_${i.billingCycle}` as MessageKey), from: day(i.periodStart), to: day(i.periodEnd) })}</td>
            <td className="py-2 text-right tabular">{i.seats}</td><td className="py-2 text-right tabular">{usd(i.unitPrice)}</td><td className="py-2 text-right tabular">{usd(i.amount)}</td>
          </tr></tbody>
          <tfoot><tr><td colSpan={3} className="py-2 text-right font-medium">{t('billing.total')}</td><td className="py-2 text-right text-base font-semibold tabular">{usd(i.amount)}</td></tr></tfoot>
        </table>

        {i.status === 'OPEN' && (
          <section aria-labelledby="pay-h" className="grid gap-6 rounded-md bg-muted/40 p-4 sm:grid-cols-[1fr_auto]">
            <div className="space-y-2 text-sm">
              <h2 id="pay-h" className="font-semibold">{t('billing.howToPay')}</h2>
              {p.bankName && <p>{t('billing.bank', { bank: p.bankName })}<br />{p.bankAccountName && <>{p.bankAccountName}<br /></>}<span className="font-mono">{p.bankAccountNumber}</span></p>}
              <p className="text-muted-foreground">{t('billing.payNote', { number: i.number })}</p>
              {i.claimedAt ? <p className="font-medium">{t('billing.claimed', { reference: i.claimReference ?? '' })}</p> : can('billing.manage') && (
                <form className="flex flex-wrap items-end gap-2 print:hidden" onSubmit={(e) => {
                  e.preventDefault();
                  if (!reference.trim()) return;
                  claim.mutateAsync(reference.trim()).then(() => toast.success(t('billing.claimSent'))).catch((x) => toast.error(errorMessage(x)));
                }}>
                  <div className="min-w-48 flex-1 space-y-1"><Label htmlFor="ref">{t('billing.reference')}</Label><Input id="ref" maxLength={120} value={reference} onChange={(e) => setReference(e.target.value)} /></div>
                  <Button type="submit" disabled={claim.isPending || !reference.trim()}>{t('billing.wePaid')}</Button>
                </form>
              )}
            </div>
            {i.khqr && <KhqrCode value={i.khqr} label={t('billing.khqrLabel', { amount: usd(i.amount) })} />}
          </section>
        )}
        {i.status === 'PAID' && <p className="text-sm">{t('billing.paidOn', { date: day(i.paidAt!), method: i.paymentMethod ?? '', reference: i.paymentReference ?? '' })}</p>}
        {i.status === 'VOID' && <p className="text-sm text-muted-foreground">{t('billing.voided', { reason: i.voidReason ?? '' })}</p>}
      </article>
    </div>
  );
}
