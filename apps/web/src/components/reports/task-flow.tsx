'use client';

import { parseISO } from 'date-fns';
import { TrendingUp } from 'lucide-react';
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';

import { EmptyState, ErrorState } from '@/components/common/states';
import { useT } from '@/i18n/provider';
import { type ReportFilter, useTaskFlow } from '@/lib/api/reports';
import { day, fmt } from '@/lib/format';

import { ChartFigure, ChartTooltip, Legend, ReportCard, ReportSkeleton, ReportTable, type Series, axisTick, downloadCsv, gridStroke, useNumber } from './report-kit';

/** 1. Task flow: created vs completed per week — is the backlog growing or shrinking? */
export function TaskFlowReport({ org, filter }: { org: string; filter: ReportFilter }) {
  const { t } = useT();
  const num = useNumber();
  const { data, error, isPending, isPlaceholderData, refetch } = useTaskFlow(org, filter);
  const title = t('reports.flowTitle');
  const series: Series[] = [
    { key: 'created', label: t('reports.created'), color: '--rv-created' },
    { key: 'completed', label: t('reports.completed'), color: '--rv-completed' },
  ];
  const week = (iso: string) => fmt(parseISO(iso), 'd MMM');
  const empty = data && data.created === 0 && data.completed === 0;
  const csv = data && !empty ? () => downloadCsv(`task-flow_${data.from}_${data.to}.csv`,
    [t('reports.csvWeekStart'), t('reports.created'), t('reports.completed')],
    data.weeks.map((w) => [w.weekStart, w.created, w.completed])) : undefined;

  return (
    <ReportCard id="report-flow" title={title} refreshing={isPlaceholderData}
      subtitle={data ? t('reports.flowSubtitle', { from: day(data.from), to: day(data.to) }) : undefined} onCsv={csv}>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ReportSkeleton /> : empty ? (
        <EmptyState icon={TrendingUp} title={t('reports.flowEmpty')} body={t('reports.flowEmptyBody')} />
      ) : (
        <>
          <Legend series={series} mark="line" totals={{ created: data.created, completed: data.completed }} />
          <ChartFigure title={title}>
            <ResponsiveContainer width="100%" height={240}>
              <LineChart data={data.weeks} margin={{ top: 8, right: 12, bottom: 0, left: 0 }}>
                <CartesianGrid vertical={false} stroke={gridStroke} />
                <XAxis dataKey="weekStart" tickFormatter={week} tick={axisTick} tickLine={false} axisLine={{ stroke: 'var(--rv-axis)' }} minTickGap={16} />
                <YAxis allowDecimals={false} tick={axisTick} tickLine={false} axisLine={false} width={36} tickFormatter={num} />
                <Tooltip cursor={{ stroke: 'var(--rv-axis)' }}
                  content={(p) => <ChartTooltip {...p} series={series} title={(r) => `${t('reports.weekOf')} ${day(String(r.weekStart))}`} />} />
                {series.map((s) => (
                  <Line key={s.key} dataKey={s.key} name={s.label} type="linear" stroke={`var(${s.color})`} strokeWidth={2}
                    dot={{ r: 4, fill: `var(${s.color})`, stroke: 'var(--card)', strokeWidth: 2 }}
                    activeDot={{ r: 5, fill: `var(${s.color})`, stroke: 'var(--card)', strokeWidth: 2 }} isAnimationActive={false} />
                ))}
              </LineChart>
            </ResponsiveContainer>
          </ChartFigure>
          <ReportTable caption={title} rowKey={(w) => w.weekStart} rows={data.weeks} columns={[
            { label: t('reports.weekOf'), value: (w) => day(w.weekStart) },
            { label: t('reports.created'), value: (w) => num(w.created), numeric: true, total: num(data.created) },
            { label: t('reports.completed'), value: (w) => num(w.completed), numeric: true, total: num(data.completed) },
          ]} />
          <p className="text-xs text-muted-foreground">{t('reports.partialNote')}</p>
        </>
      )}
    </ReportCard>
  );
}
