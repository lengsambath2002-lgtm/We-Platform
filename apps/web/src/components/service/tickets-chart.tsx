'use client';

import { parseISO } from 'date-fns';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';

import {
  ChartFigure, ChartTooltip, Legend, ReportCard, ReportTable, type Series, axisTick, downloadCsv, gridStroke,
} from '@/components/reports/report-kit';
import { useT } from '@/i18n/provider';
import type { WeekCount } from '@/lib/api/service';
import { fmt } from '@/lib/format';

/** Tickets opened against tickets resolved, week by week, with the numbers as a table and a CSV. */
export function TicketsChart({ weeks }: { weeks: WeekCount[] }) {
  const { t } = useT();
  const series: Series[] = [
    { key: 'opened', label: t('service.opened'), color: '--rv-created' },
    { key: 'resolved', label: t('service.resolved'), color: '--rv-completed' },
  ];
  const title = t('service.chartTitle');
  const week = (iso: string) => fmt(parseISO(iso), 'd MMM');
  const totals = { opened: weeks.reduce((a, w) => a + w.opened, 0), resolved: weeks.reduce((a, w) => a + w.resolved, 0) };
  const csv = () => downloadCsv(`tickets-per-week_${weeks[0]?.week ?? ''}.csv`, [t('service.weekOf'), t('service.opened'), t('service.resolved')],
    weeks.map((w) => [w.week, w.opened, w.resolved]));
  return (
    <ReportCard id="svc-chart" title={title} subtitle={t('service.chartSubtitle')} onCsv={csv}>
      <Legend series={series} totals={totals} />
      <ChartFigure title={title}>
        <ResponsiveContainer width="100%" height={220}>
          <BarChart data={weeks} margin={{ top: 8, right: 8, bottom: 0, left: 0 }} barGap={2} barCategoryGap="28%">
            <CartesianGrid vertical={false} stroke={gridStroke} />
            <XAxis dataKey="week" tickFormatter={week} tick={axisTick} tickLine={false} axisLine={{ stroke: 'var(--rv-axis)' }} />
            <YAxis tick={axisTick} tickLine={false} axisLine={false} width={32} allowDecimals={false} />
            <Tooltip cursor={{ fill: 'var(--muted)', opacity: 0.5 }}
              content={(p) => <ChartTooltip {...p} series={series} title={(r) => t('service.weekOfDate', { date: week(String(r.week)) })} />} />
            {series.map((s) => <Bar key={s.key} dataKey={s.key} name={s.label} fill={`var(${s.color})`} radius={[4, 4, 0, 0]} maxBarSize={24} isAnimationActive={false} />)}
          </BarChart>
        </ResponsiveContainer>
      </ChartFigure>
      <ReportTable caption={title} rowKey={(w) => w.week} rows={weeks} columns={[
        { label: t('service.weekOf'), value: (w) => fmt(parseISO(w.week), 'd MMM yyyy') },
        { label: t('service.opened'), value: (w) => w.opened, numeric: true, total: totals.opened },
        { label: t('service.resolved'), value: (w) => w.resolved, numeric: true, total: totals.resolved },
      ]} />
    </ReportCard>
  );
}
