'use client';

import { Users } from 'lucide-react';
import { Bar, BarChart, CartesianGrid, LabelList, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';

import { EmptyState, ErrorState } from '@/components/common/states';
import { useT } from '@/i18n/provider';
import { type ReportFilter, useWorkload } from '@/lib/api/reports';

import { ChartFigure, ChartTooltip, Legend, ReportCard, ReportSkeleton, ReportTable, type Series, axisTick, clip, downloadCsv, gridStroke, useNumber, useWide } from './report-kit';

/** 3. Workload: open tasks per assignee (unassigned too), overdue in the status colour — who is overloaded? */
export function WorkloadReport({ org, filter }: { org: string; filter: ReportFilter }) {
  const { t } = useT();
  const num = useNumber();
  const wide = useWide();
  const { data, error, isPending, isPlaceholderData, refetch } = useWorkload(org, filter);
  const title = t('reports.workloadTitle');
  const series: Series[] = [
    { key: 'notOverdue', label: t('reports.notOverdue'), color: '--rv-not-overdue' },
    { key: 'overdue', label: t('reports.overdue'), color: '--rv-overdue' },
  ];
  const rows = (data?.people ?? []).map((p) => ({ ...p, label: p.name ?? t('reports.unassigned'), notOverdue: p.open - p.overdue }));
  const empty = data && rows.length === 0;
  const csv = data && !empty ? () => downloadCsv(`workload_${data.asOf}.csv`,
    [t('reports.assignee'), t('reports.open'), t('reports.overdue')], rows.map((r) => [r.label, r.open, r.overdue])) : undefined;

  return (
    <ReportCard id="report-workload" title={title} subtitle={t('reports.workloadSubtitle')} refreshing={isPlaceholderData} onCsv={csv}>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ReportSkeleton /> : empty ? (
        <EmptyState icon={Users} title={t('reports.openEmpty')} body={t('reports.openEmptyBody')} />
      ) : (
        <>
          <Legend series={series} totals={{ notOverdue: data.open - data.overdue, overdue: data.overdue }} />
          <ChartFigure title={title}>
            <ResponsiveContainer width="100%" height={rows.length * 36 + 32}>
              <BarChart data={rows} layout="vertical" margin={{ top: 0, right: 40, bottom: 0, left: 0 }} barSize={20}>
                <CartesianGrid horizontal={false} stroke={gridStroke} />
                <XAxis type="number" allowDecimals={false} tick={axisTick} tickLine={false} axisLine={false} tickFormatter={num} />
                <YAxis type="category" dataKey="label" width={wide ? 150 : 96} tick={axisTick} tickLine={false} axisLine={{ stroke: 'var(--rv-axis)' }}
                  tickFormatter={(n: string) => clip(n, wide ? 22 : 12)} />
                <Tooltip cursor={{ fill: 'var(--muted)', opacity: 0.5 }} content={(p) => <ChartTooltip {...p} series={series} title={(r) => String(r.label)} />} />
                {series.map((s, i) => (
                  <Bar key={s.key} dataKey={s.key} name={s.label} stackId="load" fill={`var(${s.color})`} stroke="var(--card)" strokeWidth={2}
                    radius={i === series.length - 1 ? [0, 4, 4, 0] : 0} isAnimationActive={false}>
                    {i === series.length - 1 && (
                      <LabelList dataKey="open" content={(p) => (
                        <text x={Number(p.x) + Number(p.width) + 6} y={Number(p.y) + Number(p.height) / 2} dy="0.35em" fontSize={12}
                          fill="var(--muted-foreground)">{num(Number(p.value))}</text>
                      )} />
                    )}
                  </Bar>
                ))}
              </BarChart>
            </ResponsiveContainer>
          </ChartFigure>
          <ReportTable caption={title} rowKey={(r) => r.id ?? 'unassigned'} rows={rows} columns={[
            { label: t('reports.assignee'), value: (r) => r.id ? r.label : <span className="text-muted-foreground">{r.label}</span> },
            { label: t('reports.open'), value: (r) => num(r.open), numeric: true, total: num(data.open) },
            { label: t('reports.overdue'), value: (r) => <span className={r.overdue > 0 ? 'font-medium text-destructive' : undefined}>{num(r.overdue)}</span>, numeric: true, total: num(data.overdue) },
          ]} />
        </>
      )}
    </ReportCard>
  );
}
