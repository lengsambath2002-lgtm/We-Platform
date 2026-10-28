'use client';

import { ArrowLeft, Printer } from 'lucide-react';
import Link from 'next/link';
import { useParams } from 'next/navigation';

import { ErrorState, ListSkeleton } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { useT } from '@/i18n/provider';
import { useBizSettings, useInvoice } from '@/lib/api/business';
import { day } from '@/lib/format';
import { khr, KHR_PER_USD, usd } from '@/lib/money';
import { useOrg } from '@/lib/org-context';

const qty = (n: number) => String(Number(n));

/**
 * The invoice as a clean A4 page: the company, the customer, the lines and totals in USD with the riel total,
 * and the payment details from the business settings. "Print / Save as PDF" uses the browser; the app's
 * navigation is hidden when printing.
 */
export default function PrintInvoice() {
  const { slug } = useOrg();
  const { t, locale } = useT();
  // Letter-spaced capitals suit Latin labels; spacing breaks Khmer clusters apart.
  const caps = locale === 'km' ? 'text-xs font-semibold text-slate-500' : 'text-xs font-semibold uppercase tracking-wider text-slate-500';
  const { id } = useParams<{ id: string }>();
  const inv = useInvoice(slug, id);
  const settings = useBizSettings(slug);
  if (inv.error || settings.error) return <ErrorState error={inv.error ?? settings.error} retry={() => { inv.refetch(); settings.refetch(); }} />;
  if (inv.isPending || settings.isPending) return <ListSkeleton />;
  const d = inv.data;
  const s = settings.data;
  const c = d.customer;
  const stamp = d.status === 'DRAFT' ? t('business.draftStamp') : d.status === 'VOID' ? t('business.voidStamp') : null;

  return (
    <div className="mx-auto max-w-[210mm]">
      <style>{'@page { size: A4; margin: 14mm; } @media print { html, body { background: #fff !important; } }'}</style>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2 print:hidden">
        <Button variant="ghost" size="sm" asChild><Link href={`/${slug}/business/invoices/${id}`}><ArrowLeft className="size-4" />{t('business.backToInvoice')}</Link></Button>
        <Button size="sm" onClick={() => window.print()}><Printer className="size-4" />{t('business.printSave')}</Button>
      </div>
      <article aria-label={t('business.printLabel')} className="relative overflow-hidden rounded-lg border bg-white p-6 text-[13px] leading-relaxed text-slate-900 shadow-sm sm:p-10 print:rounded-none print:border-0 print:p-0 print:shadow-none">
        {stamp && <p aria-hidden className="pointer-events-none absolute right-6 top-6 rotate-12 rounded border-2 border-red-600 px-3 py-1 text-lg font-bold uppercase tracking-widest text-red-600 opacity-70">{stamp}</p>}
        <header className="flex flex-wrap items-start justify-between gap-6 border-b border-slate-200 pb-6">
          <div className="min-w-0 max-w-[60%]">
            <p className="text-lg font-semibold">{s.legalName ?? s.orgName}</p>
            {s.address && <p className="whitespace-pre-line text-slate-600">{s.address}</p>}
            {(s.phone || s.email) && <p className="text-slate-600">{[s.phone, s.email].filter(Boolean).join(' · ')}</p>}
            {s.taxId && <p className="text-slate-600">{t('business.vatTin')}: {s.taxId}</p>}
          </div>
          <div className="text-right">
            <p className="text-2xl font-semibold tracking-tight">{t('business.invoiceHeading')}</p>
            <p className="font-medium">{d.number ?? t('business.draftInvoice')}</p>
            <dl className="mt-2 grid grid-cols-[auto_auto] justify-end gap-x-3 text-slate-600">
              <dt>{t('business.issueDate')}</dt><dd className="text-slate-900">{d.issueDate ? day(d.issueDate) : '—'}</dd>
              <dt>{t('business.dueDate')}</dt><dd className="text-slate-900">{d.dueDate ? day(d.dueDate) : '—'}</dd>
            </dl>
          </div>
        </header>
        <section className="py-6">
          <p className={caps}>{t('business.billTo')}</p>
          <p className="mt-1 font-semibold">{c.name}</p>
          {c.contactName && <p>{c.contactName}</p>}
          {c.address && <p className="whitespace-pre-line text-slate-600">{c.address}</p>}
          {(c.phone || c.email) && <p className="text-slate-600">{[c.phone, c.email].filter(Boolean).join(' · ')}</p>}
          {c.taxId && <p className="text-slate-600">{t('business.vatTin')}: {c.taxId}</p>}
        </section>
        <table className="w-full border-collapse">
          <thead>
            <tr className={`border-b-2 border-slate-300 text-left ${caps}`}>
              <th className="py-2 pr-2 font-semibold">{t('business.description')}</th>
              <th className="px-2 py-2 text-right font-semibold">{t('business.qty')}</th>
              <th className="px-2 py-2 text-right font-semibold">{t('business.unitPrice')}</th>
              <th className="hidden px-2 py-2 text-right font-semibold sm:table-cell print:table-cell">{t('business.discountPct')}</th>
              <th className="py-2 pl-2 text-right font-semibold">{t('business.amount')}</th>
            </tr>
          </thead>
          <tbody>
            {d.lines.map((l) => (
              <tr key={l.id} className="border-b border-slate-200 align-top">
                <td className="break-words py-2 pr-2">{l.description}{d.vatEnabled && !l.vat && <span className="text-slate-500"> ({t('business.vatExempt')})</span>}</td>
                <td className="whitespace-nowrap px-2 py-2 text-right tabular">{qty(l.quantity)}{l.unit ? ` ${l.unit}` : ''}</td>
                <td className="whitespace-nowrap px-2 py-2 text-right tabular">{usd(l.unitPrice)}</td>
                <td className="hidden whitespace-nowrap px-2 py-2 text-right tabular sm:table-cell print:table-cell">{Number(l.discountPct) ? `${Number(l.discountPct)}%` : '—'}</td>
                <td className="whitespace-nowrap py-2 pl-2 text-right tabular">{usd(l.net)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <dl className="ml-auto mt-4 w-full max-w-72 space-y-1">
          <div className="flex justify-between gap-4"><dt className="text-slate-600">{t('business.subtotal')}</dt><dd className="tabular">{usd(d.subtotal)}</dd></div>
          <div className="flex justify-between gap-4"><dt className="text-slate-600">{d.vatEnabled ? t('business.vatRate', { rate: Number(d.vatRate) }) : t('business.noVat')}</dt><dd className="tabular">{usd(d.vatTotal)}</dd></div>
          <div className="flex justify-between gap-4 border-t-2 border-slate-300 pt-1 text-base font-semibold"><dt>{t('business.totalUsd')}</dt><dd className="tabular">{usd(d.total)}</dd></div>
          <div className="flex justify-between gap-4"><dt className="text-slate-600">{t('business.totalKhr')}</dt><dd className="tabular">{khr(d.total)}</dd></div>
          {d.paid > 0 && <div className="flex justify-between gap-4"><dt className="text-slate-600">{t('business.paid')}</dt><dd className="tabular">−{usd(d.paid)}</dd></div>}
          {d.status !== 'DRAFT' && d.status !== 'VOID' && <div className="flex justify-between gap-4 font-semibold"><dt>{t('business.balanceDue')}</dt><dd className="tabular">{usd(d.balance)}</dd></div>}
        </dl>
        <p className="mt-2 text-right text-xs text-slate-500">{t('business.rateNote', { rate: KHR_PER_USD.toLocaleString('en-US') })}</p>
        {d.notes && (
          <section className="mt-6">
            <p className={caps}>{t('business.notesTerms')}</p>
            <p className="mt-1 whitespace-pre-wrap break-words">{d.notes}</p>
          </section>
        )}
        {s.invoiceFooter && (
          <footer className="mt-8 border-t border-slate-200 pt-4">
            <p className={caps}>{t('business.paymentDetails')}</p>
            <p className="mt-1 whitespace-pre-wrap break-words text-slate-700">{s.invoiceFooter}</p>
          </footer>
        )}
      </article>
    </div>
  );
}
