'use client';

import { CheckCircle2 } from 'lucide-react';
import { Bar, BarChart, CartesianGrid, LabelList, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';

import { STATUS_LABEL } from '@/components/common/badges';
import { EmptyState, ErrorState } from '@/components/common/states';
import { useT } from '@/i18n/provider';
import { type ProjectOpen, type ReportFilter, useOpenWork } from '@/lib/api/reports';

import { ChartFigure, ChartTooltip, Legend, ReportCard, ReportSkeleton, ReportTable, type Series, axisTick, clip, downloadCsv, gridStroke, useNumber, useWide } from './report-kit';

/** 2. Open work: open tasks by project and status, with overdue — where is work piling up? */
export function OpenWorkReport({ org, filter }: { org: string; filter: ReportFilter }) {
  const { t } = useT();
  const num = useNumber();
  const wide = useWide();
  const { data, error, isPending, isPlaceholderData, refetch } = useOpenWork(org, filter);
  const title = t('reports.openTitle');
  const series: Series[] = [
    { key: 'todo', label: STATUS_LABEL.TODO, color: '--rv-todo' },
    { key: 'inProgress', label: STATUS_LABEL.IN_PROGRESS, color: '--rv-in-progress' },
    { key: 'inReview', label: STATUS_LABEL.IN_REVIEW, color: '--rv-in-review' },
  ];
  const tooltipSeries: Series[] = [...series, { key: 'overdue', label: t('reports.overdue'), color: '--rv-overdue' }];
  const rows = data?.projects ?? [];
  const empty = data && rows.length === 0;
  const sum = (k: keyof ProjectOpen) => rows.reduce((n, r) => n + Number(r[k]), 0);
  const csv = data && !empty ? () => downloadCsv(`open-work_${data.asOf}.csv`,
    [t('reports.projectCol'), STATUS_LABEL.TODO, STATUS_LABEL.IN_PROGRESS, STATUS_LABEL.IN_REVIEW, t('reports.open'), t('reports.overdue')],
    rows.map((r) => [`${r.key} ${r.name}`, r.todo, r.inProgress, r.inReview, r.open, r.overdue])) : undefined;

  return (
    <ReportCard id="report-open" title={title} subtitle={t('reports.openSubtitle')} refreshing={isPlaceholderData} onCsv={csv}>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ReportSkeleton /> : empty ? (
        <EmptyState icon={CheckCircle2} title={t('reports.openEmpty')} body={t('reports.openEmptyBody')} />
      ) : (
        <>
          <Legend series={series} totals={{ todo: sum('todo'), inProgress: sum('inProgress'), inReview: sum('inReview') }} />
          <ChartFigure title={title}>
            <ResponsiveContainer width="100%" height={rows.length * 36 + 32}>
              <BarChart data={rows} layout="vertical" margin={{ top: 0, right: 84, bottom: 0, left: 0 }} barSize={20}>
                <CartesianGrid horizontal={false} stroke={gridStroke} />
                <XAxis type="number" allowDecimals={false} tick={axisTick} tickLine={false} axisLine={false} tickFormatter={num} />
                <YAxis type="category" dataKey="name" width={wide ? 150 : 96} tick={axisTick} tickLine={false} axisLine={{ stroke: 'var(--rv-axis)' }}
                  tickFormatter={(n: string) => clip(n, wide ? 22 : 12)} />
                <Tooltip cursor={{ fill: 'var(--muted)', opacity: 0.5 }} content={(p) => <ChartTooltip {...p} series={tooltipSeries} title={(r) => String(r.name)} />} />
                {series.map((s, i) => (
                  <Bar key={s.key} dataKey={s.key} name={s.label} stackId="open" fill={`var(${s.color})`} stroke="var(--card)" strokeWidth={2}
                    radius={i === series.length - 1 ? [0, 4, 4, 0] : 0} isAnimationActive={false}>
                    {i === series.length - 1 && (
                      <LabelList dataKey="overdue" content={(p) => {
                        const r = rows[Number(p.index)];
                        if (!r) return null;
                        const text = r.overdue > 0 ? `${num(r.open)} · ${t('reports.overdueCount', { count: num(r.overdue) })}` : num(r.open);
                        return <text x={Number(p.x) + Number(p.width) + 6} y={Number(p.y) + Number(p.height) / 2} dy="0.35em" fontSize={12}
                          fill={r.overdue > 0 ? 'var(--destructive)' : 'var(--muted-foreground)'}>{text}</text>;
                      }} />
                    )}
                  </Bar>
                ))}
              </BarChart>
            </ResponsiveContainer>
          </ChartFigure>
          <ReportTable caption={title} rowKey={(r) => r.key} rows={rows} columns={[
            { label: t('reports.projectCol'), value: (r) => <span title={r.name}>{r.name}</span> },
            { label: STATUS_LABEL.TODO, value: (r) => num(r.todo), numeric: true, total: num(sum('todo')) },
            { label: STATUS_LABEL.IN_PROGRESS, value: (r) => num(r.inProgress), numeric: true, total: num(sum('inProgress')) },
            { label: STATUS_LABEL.IN_REVIEW, value: (r) => num(r.inReview), numeric: true, total: num(sum('inReview')) },
            { label: t('reports.open'), value: (r) => num(r.open), numeric: true, total: num(data.open) },
            { label: t('reports.overdue'), value: (r) => <span className={r.overdue > 0 ? 'font-medium text-destructive' : undefined}>{num(r.overdue)}</span>, numeric: true, total: num(data.overdue) },
          ]} />
        </>
      )}
    </ReportCard>
  );
}
