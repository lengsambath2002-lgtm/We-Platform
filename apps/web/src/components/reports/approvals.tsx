'use client';

import { BadgeCheck } from 'lucide-react';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';

import { APPROVAL_LABEL } from '@/components/common/badges';
import { EmptyState, ErrorState } from '@/components/common/states';
import { useT } from '@/i18n/provider';
import { type ApprovalStats, type ReportFilter, useApprovalsReport } from '@/lib/api/reports';
import { day } from '@/lib/format';
import { cn } from '@/lib/utils';

import { ChartFigure, ChartTooltip, Legend, ReportCard, ReportSkeleton, ReportTable, type Series, axisTick, clip, downloadCsv, gridStroke, useDuration, useNumber, useWide } from './report-kit';

function Stat({ label, value, hint, alert }: { label: string; value: string; hint?: string; alert?: boolean }) {
  return (
    <div className="rounded-lg border p-3">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className={cn('mt-1 text-2xl font-semibold', alert && 'text-warning')}>{value}</p>
      {hint && <p className="mt-0.5 text-xs text-muted-foreground">{hint}</p>}
    </div>
  );
}

/** 4. Approvals: outcomes, time to decision and long waits, per workflow — are decisions holding work up? */
export function ApprovalsReport({ org, filter }: { org: string; filter: ReportFilter }) {
  const { t } = useT();
  const num = useNumber();
  const dur = useDuration();
  const wide = useWide();
  const { data, error, isPending, isPlaceholderData, refetch } = useApprovalsReport(org, filter);
  const title = t('reports.approvalsTitle');
  // Neighbouring segments are ordered so their colours stay distinguishable (reports.css).
  const series: Series[] = [
    { key: 'approved', label: APPROVAL_LABEL.APPROVED, color: '--rv-approved' },
    { key: 'pending', label: APPROVAL_LABEL.PENDING, color: '--rv-pending' },
    { key: 'changesRequested', label: APPROVAL_LABEL.CHANGES_REQUESTED, color: '--rv-changes' },
    { key: 'withdrawn', label: APPROVAL_LABEL.WITHDRAWN, color: '--rv-withdrawn' },
    { key: 'rejected', label: APPROVAL_LABEL.REJECTED, color: '--rv-rejected' },
  ];
  const rows = (data?.workflows ?? []).map((w) => ({ id: w.id, name: w.name, ...w.stats }));
  const tot = data?.total;
  const empty = data && rows.length === 0;
  const totals = tot ? Object.fromEntries(series.map((s) => [s.key, Number(tot[s.key as keyof ApprovalStats] ?? 0)])) : undefined;
  const staleLabel = data ? t('reports.staleCount', { days: num(data.staleDays) }) : '';
  const charted = rows.filter((r) => r.submitted > 0);
  const csv = data && !empty ? () => downloadCsv(`approvals_${data.from}_${data.to}.csv`,
    [t('reports.workflow'), t('reports.submitted'), ...series.map((s) => s.label), t('reports.csvMedianHours'), t('reports.csvP90Hours'), t('reports.csvStale', { days: data.staleDays })],
    [...rows, { id: 'total', name: t('reports.total'), ...data.total }].map((r) => [r.name, r.submitted, r.approved, r.pending, r.changesRequested, r.withdrawn, r.rejected, r.medianHours, r.p90Hours, r.stale])) : undefined;

  return (
    <ReportCard id="report-approvals" title={title} refreshing={isPlaceholderData} onCsv={csv}
      subtitle={data ? t('reports.approvalsSubtitle', { from: day(data.from), to: day(data.to) }) : undefined}>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ReportSkeleton /> : empty ? (
        <EmptyState icon={BadgeCheck} title={t('reports.approvalsEmpty')} body={t('reports.approvalsEmptyBody')} />
      ) : (
        <>
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <Stat label={t('reports.submitted')} value={num(data.total.submitted)} />
            <Stat label={t('reports.medianTime')} value={data.total.medianHours == null ? '—' : dur(data.total.medianHours)}
              hint={data.total.medianHours == null ? t('reports.noDecision') : t('reports.decidedHint')} />
            <Stat label={t('reports.p90Time')} value={data.total.p90Hours == null ? '—' : dur(data.total.p90Hours)}
              hint={data.total.p90Hours == null ? t('reports.noDecision') : undefined} />
            <Stat label={staleLabel} value={num(data.total.stale)} hint={t('reports.staleHint')} alert={data.total.stale > 0} />
          </div>
          {charted.length > 0 && (
            <>
              <Legend series={series} totals={totals} />
              <ChartFigure title={title}>
                <ResponsiveContainer width="100%" height={charted.length * 36 + 32}>
                  <BarChart data={charted} layout="vertical" margin={{ top: 0, right: 16, bottom: 0, left: 0 }} barSize={20}>
                    <CartesianGrid horizontal={false} stroke={gridStroke} />
                    <XAxis type="number" allowDecimals={false} tick={axisTick} tickLine={false} axisLine={false} tickFormatter={num} />
                    <YAxis type="category" dataKey="name" width={wide ? 150 : 96} tick={axisTick} tickLine={false} axisLine={{ stroke: 'var(--rv-axis)' }}
                      tickFormatter={(n: string) => clip(n, wide ? 22 : 12)} />
                    <Tooltip cursor={{ fill: 'var(--muted)', opacity: 0.5 }} content={(p) => <ChartTooltip {...p} series={series} title={(r) => String(r.name)} />} />
                    {series.map((s) => (
                      <Bar key={s.key} dataKey={s.key} name={s.label} stackId="outcome" fill={`var(${s.color})`} stroke="var(--card)" strokeWidth={2} isAnimationActive={false} />
                    ))}
                  </BarChart>
                </ResponsiveContainer>
              </ChartFigure>
            </>
          )}
          <ReportTable caption={title} rowKey={(r) => r.id} rows={rows} columns={[
            { label: t('reports.workflow'), value: (r) => <span title={r.name}>{r.name}</span> },
            { label: t('reports.submitted'), value: (r) => num(r.submitted), numeric: true, total: num(data.total.submitted) },
            ...series.map((s) => ({
              label: s.label, numeric: true,
              value: (r: (typeof rows)[number]) => num(Number(r[s.key as keyof ApprovalStats] ?? 0)),
              total: num(Number(data.total[s.key as keyof ApprovalStats] ?? 0)),
            })),
            { label: t('reports.median'), value: (r) => dur(r.medianHours), numeric: true, total: dur(data.total.medianHours) },
            { label: t('reports.p90'), value: (r) => dur(r.p90Hours), numeric: true, total: dur(data.total.p90Hours) },
            { label: staleLabel, value: (r) => <span className={r.stale > 0 ? 'font-medium text-warning' : undefined}>{num(r.stale)}</span>, numeric: true, total: num(data.total.stale) },
          ]} />
        </>
      )}
    </ReportCard>
  );
}
