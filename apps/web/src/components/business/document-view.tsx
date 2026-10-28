'use client';

import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useT } from '@/i18n/provider';
import type { Line } from '@/lib/api/business';
import { khr, usd } from '@/lib/money';

const qty = (n: number) => String(Number(n));

/** A document's lines (a table on wide screens, stacked rows on phones) and its totals as the server computed them. */
export function DocumentLines({ lines, vatEnabled, vatRate, subtotal, discountTotal, vatTotal, total, paid, balance }: {
  lines: Line[]; vatEnabled: boolean; vatRate: number; subtotal: number; discountTotal: number; vatTotal: number; total: number;
  paid?: number; balance?: number;
}) {
  const { t } = useT();
  return (
    <section aria-label={t('business.lines')} className="rounded-lg border bg-card">
      <div className="hidden md:block">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t('business.description')}</TableHead>
              <TableHead className="text-right">{t('business.qty')}</TableHead>
              <TableHead className="text-right">{t('business.unitPrice')}</TableHead>
              <TableHead className="text-right">{t('business.discountPct')}</TableHead>
              {vatEnabled && <TableHead className="text-center">{t('business.vat')}</TableHead>}
              <TableHead className="text-right">{t('business.amount')}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {lines.map((l) => (
              <TableRow key={l.id}>
                <TableCell className="max-w-80 whitespace-normal break-words">{l.description}</TableCell>
                <TableCell className="text-right tabular">{qty(l.quantity)}{l.unit ? ` ${l.unit}` : ''}</TableCell>
                <TableCell className="text-right tabular">{usd(l.unitPrice)}</TableCell>
                <TableCell className="text-right tabular">{Number(l.discountPct) ? `${Number(l.discountPct)}%` : '—'}</TableCell>
                {vatEnabled && <TableCell className="text-center text-xs">{l.vat ? t('business.yes') : t('business.exempt')}</TableCell>}
                <TableCell className="text-right font-medium tabular">{usd(l.net)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
      <ul className="divide-y md:hidden">
        {lines.map((l) => (
          <li key={l.id} className="flex items-start justify-between gap-3 px-4 py-3 text-sm">
            <span className="min-w-0">
              <span className="block break-words font-medium">{l.description}</span>
              <span className="block text-xs text-muted-foreground tabular">
                {qty(l.quantity)}{l.unit ? ` ${l.unit}` : ''} × {usd(l.unitPrice)}{Number(l.discountPct) ? ` − ${Number(l.discountPct)}%` : ''}
                {vatEnabled && !l.vat ? ` · ${t('business.vatExempt')}` : ''}
              </span>
            </span>
            <span className="shrink-0 font-medium tabular">{usd(l.net)}</span>
          </li>
        ))}
      </ul>
      <dl className="ml-auto max-w-sm space-y-1.5 border-t p-4 text-sm">
        <div className="flex justify-between gap-2"><dt className="text-muted-foreground">{t('business.subtotal')}</dt><dd className="tabular">{usd(subtotal)}</dd></div>
        {discountTotal > 0 && <div className="flex justify-between gap-2"><dt className="text-muted-foreground">{t('business.discountIncluded')}</dt><dd className="tabular">{usd(discountTotal)}</dd></div>}
        <div className="flex justify-between gap-2"><dt className="text-muted-foreground">{vatEnabled ? t('business.vatRate', { rate: Number(vatRate) }) : t('business.noVat')}</dt><dd className="tabular">{usd(vatTotal)}</dd></div>
        <div className="flex justify-between gap-2 border-t pt-2 text-base font-semibold"><dt>{t('business.total')}</dt><dd className="tabular">{usd(total)}</dd></div>
        <div className="flex justify-between gap-2 text-xs text-muted-foreground"><dt>{t('business.inRiel')}</dt><dd className="tabular">{khr(total)}</dd></div>
        {paid !== undefined && paid > 0 && <div className="flex justify-between gap-2"><dt className="text-muted-foreground">{t('business.paid')}</dt><dd className="tabular">−{usd(paid)}</dd></div>}
        {balance !== undefined && (
          <div className="flex justify-between gap-2 font-semibold"><dt>{t('business.balanceDue')}</dt><dd className="text-right tabular">{usd(balance)}<span className="block text-xs font-normal text-muted-foreground">{khr(balance)}</span></dd></div>
        )}
      </dl>
    </section>
  );
}
