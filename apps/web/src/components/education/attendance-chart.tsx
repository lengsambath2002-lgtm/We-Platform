'use client';

import { parseISO } from 'date-fns';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';

import { ChartFigure, ReportCard, ReportTable, axisTick, downloadCsv, gridStroke } from '@/components/reports/report-kit';
import { useT } from '@/i18n/provider';
import type { WeekRate } from '@/lib/api/education';
import { fmt } from '@/lib/format';

import { pct } from './ui';

function RateTooltip({ active, payload }: { active?: boolean; payload?: readonly { payload?: unknown }[] }) {
  const { t } = useT();
  if (!active || !payload?.length) return null;
  const w = payload[0].payload as WeekRate;
  return (
    <div className="min-w-40 rounded-md border bg-popover px-3 py-2 text-xs text-popover-foreground shadow-md">
      <p className="mb-1 font-medium">{t('education.weekOf', { date: fmt(parseISO(w.week), 'd MMM') })}</p>
      <p className="flex justify-between gap-3"><span className="text-muted-foreground">{t('education.attendanceRate')}</span><span className="font-semibold tabular">{pct(w.rate)}</span></p>
      <p className="mt-1 text-muted-foreground tabular">{t('education.weekCounts', { present: w.present, late: w.late, absent: w.absent, excused: w.excused })}</p>
    </div>
  );
}

/**
 * The attendance rate week by week ((present + late) of the days that count; excused days don't count), with the
 * counts as a table and a CSV. Weeks without a register show no bar.
 */
export function AttendanceChart({ weeks }: { weeks: WeekRate[] }) {
  const { t } = useT();
  const title = t('education.chartTitle');
  const data = weeks.map((w) => ({ ...w, value: w.rate ?? 0 }));
  const csv = () => downloadCsv(`attendance-by-week_${weeks[0]?.week ?? ''}.csv`,
    [t('education.week'), t('education.mark_PRESENT'), t('education.mark_LATE'), t('education.mark_ABSENT'), t('education.mark_EXCUSED'), t('education.ratePct')],
    weeks.map((w) => [w.week, w.present, w.late, w.absent, w.excused, w.rate == null ? null : w.rate.toFixed(1)]));
  const sum = (k: 'present' | 'late' | 'absent' | 'excused') => weeks.reduce((a, w) => a + w[k], 0);
  const p = sum('present');
  const l = sum('late');
  const a = sum('absent');
  const overall = p + l + a === 0 ? null : Math.round(((p + l) * 1000) / (p + l + a)) / 10;
  return (
    <ReportCard id="edu-chart" title={title} subtitle={t('education.chartSubtitle')} onCsv={csv}>
      <ChartFigure title={title}>
        <ResponsiveContainer width="100%" height={220}>
          <BarChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }} barCategoryGap="28%">
            <CartesianGrid vertical={false} stroke={gridStroke} />
            <XAxis dataKey="week" tickFormatter={(iso: string) => fmt(parseISO(iso), 'd MMM')} tick={axisTick} tickLine={false} axisLine={{ stroke: 'var(--rv-axis)' }} />
            <YAxis domain={[0, 100]} tick={axisTick} tickLine={false} axisLine={false} width={40} tickFormatter={(v: number) => `${v}%`} />
            <Tooltip cursor={{ fill: 'var(--muted)', opacity: 0.5 }} content={(props) => <RateTooltip {...props} />} />
            <Bar dataKey="value" name={t('education.attendanceRate')} fill="var(--rv-attendance)" radius={[4, 4, 0, 0]} maxBarSize={32} isAnimationActive={false} />
          </BarChart>
        </ResponsiveContainer>
      </ChartFigure>
      <ReportTable caption={title} rowKey={(w) => w.week} rows={weeks} columns={[
        { label: t('education.week'), value: (w) => fmt(parseISO(w.week), 'd MMM yyyy') },
        { label: t('education.mark_PRESENT'), value: (w) => w.present, numeric: true, total: p },
        { label: t('education.mark_LATE'), value: (w) => w.late, numeric: true, total: l },
        { label: t('education.mark_ABSENT'), value: (w) => w.absent, numeric: true, total: a },
        { label: t('education.mark_EXCUSED'), value: (w) => w.excused, numeric: true, total: sum('excused') },
        { label: t('education.attendanceRate'), value: (w) => pct(w.rate), numeric: true, total: pct(overall) },
      ]} />
    </ReportCard>
  );
}
