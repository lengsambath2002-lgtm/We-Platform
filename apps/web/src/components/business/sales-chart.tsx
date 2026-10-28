'use client';

import { parseISO } from 'date-fns';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';

import {
  ChartFigure, Legend, ReportCard, ReportTable, type Series, axisTick, downloadCsv, gridStroke,
} from '@/components/reports/report-kit';
import { useT } from '@/i18n/provider';
import type { MonthTotals } from '@/lib/api/business';
import { fmt } from '@/lib/format';
import { khr, usd } from '@/lib/money';

/** Tooltip: the month, then each series in USD (value strong, label secondary, keyed by a short swatch). */
function MoneyTooltip({ active, payload, series }: { active?: boolean; payload?: readonly { payload?: unknown }[]; series: Series[] }) {
  if (!active || !payload?.length) return null;
  const row = payload[0].payload as MonthTotals;
  return (
    <div className="min-w-40 rounded-md border bg-popover px-3 py-2 text-xs text-popover-foreground shadow-md">
      <p className="mb-1 font-medium">{fmt(parseISO(row.month), 'MMMM yyyy')}</p>
      <ul className="space-y-0.5">
        {series.map((s) => (
          <li key={s.key} className="flex items-center gap-2">
            <span aria-hidden className="size-2.5 rounded-sm" style={{ background: `var(${s.color})` }} />
            <span className="text-muted-foreground">{s.label}</span>
            <span className="ml-auto pl-3 font-semibold tabular">{usd(Number(row[s.key as keyof MonthTotals] ?? 0))}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

/**
 * Sales (invoices issued) against approved expenses, month by month, with the numbers as a table and a CSV.
 * People who don't run the business see sales only (expenses are null for them).
 */
export function SalesExpensesChart({ months }: { months: MonthTotals[] }) {
  const { t } = useT();
  const withExpenses = months.some((m) => m.expenses !== null);
  const series: Series[] = [
    { key: 'sales', label: t('business.sales'), color: '--rv-sales' },
    ...(withExpenses ? [{ key: 'expenses', label: t('business.expenses'), color: '--rv-expenses' }] : []),
  ];
  const title = withExpenses ? t('business.chartTitle') : t('business.chartTitleSales');
  const month = (iso: string) => fmt(parseISO(iso), 'MMM');
  const totalSales = months.reduce((a, m) => a + m.sales, 0);
  const totalExpenses = months.reduce((a, m) => a + (m.expenses ?? 0), 0);
  const csv = () => downloadCsv(`sales-expenses_${months[0]?.month ?? ''}.csv`,
    [t('business.month'), t('business.salesUsd'), ...(withExpenses ? [t('business.expensesUsd')] : [])],
    months.map((m) => [m.month.slice(0, 7), m.sales.toFixed(2), ...(withExpenses ? [(m.expenses ?? 0).toFixed(2)] : [])]));

  return (
    <ReportCard id="biz-chart" title={title} subtitle={t('business.chartSubtitle')} onCsv={csv}>
      {withExpenses && <Legend series={series} />}
      <ChartFigure title={title}>
        <ResponsiveContainer width="100%" height={220}>
          <BarChart data={months} margin={{ top: 8, right: 8, bottom: 0, left: 0 }} barGap={2} barCategoryGap="28%">
            <CartesianGrid vertical={false} stroke={gridStroke} />
            <XAxis dataKey="month" tickFormatter={month} tick={axisTick} tickLine={false} axisLine={{ stroke: 'var(--rv-axis)' }} />
            <YAxis tick={axisTick} tickLine={false} axisLine={false} width={56} tickFormatter={(v: number) => usd(v).replace(/\.00$/, '')} />
            <Tooltip cursor={{ fill: 'var(--muted)', opacity: 0.5 }} content={(p) => <MoneyTooltip {...p} series={series} />} />
            {series.map((s) => (
              <Bar key={s.key} dataKey={s.key} name={s.label} fill={`var(${s.color})`} radius={[4, 4, 0, 0]} maxBarSize={28} isAnimationActive={false} />
            ))}
          </BarChart>
        </ResponsiveContainer>
      </ChartFigure>
      <ReportTable caption={title} rowKey={(m) => m.month} rows={months} columns={[
        { label: t('business.month'), value: (m) => fmt(parseISO(m.month), 'MMM yyyy') },
        { label: t('business.sales'), value: (m) => usd(m.sales), numeric: true, total: usd(totalSales) },
        ...(withExpenses ? [{ label: t('business.expenses'), value: (m: MonthTotals) => usd(m.expenses ?? 0), numeric: true, total: usd(totalExpenses) }] : []),
      ]} />
      <p className="text-xs text-muted-foreground">{t('business.chartNote', { sales: khr(totalSales) })}</p>
    </ReportCard>
  );
}
