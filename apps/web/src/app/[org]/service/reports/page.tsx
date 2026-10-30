'use client';

import { BarChart3 } from 'lucide-react';
import { useState } from 'react';

import { PRIORITY_LABEL } from '@/components/common/badges';
import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, PermissionDenied } from '@/components/common/states';
import { ReportCard, ReportSkeleton, ReportTable, downloadCsv } from '@/components/reports/report-kit';
import { CATEGORY_LABEL, Stat } from '@/components/service/ui';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useT } from '@/i18n/provider';
import { useSvcReport } from '@/lib/api/service';
import { khr, usd } from '@/lib/money';
import { useOrg } from '@/lib/org-context';

const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
const pct = (a: number, b: number) => (b ? `${Math.round((a / b) * 100)}%` : '—');

/** Service reports for a period: work completed per technician, SLA results by priority, tickets by category. CSV each. */
export default function ServiceReports() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const now = new Date();
  const [from, setFrom] = useState(iso(new Date(now.getFullYear(), now.getMonth(), now.getDate() - 29)));
  const [to, setTo] = useState(iso(now));
  const { data, error, isPending, isFetching, refetch } = useSvcReport(slug, from, to);
  if (!can('service.manage')) return <PermissionDenied />;
  const base = `/${slug}/service`;
  return (
    <>
      <PageHeader title={t('service.reportsTitle')} subtitle={t('service.reportsSubtitle')} />
      <div className="mb-6 flex flex-wrap items-end gap-3">
        <div className="space-y-1"><Label htmlFor="r-from" className="text-xs text-muted-foreground">{t('service.from')}</Label><Input id="r-from" type="date" value={from} max={to} onChange={(e) => e.target.value && setFrom(e.target.value)} /></div>
        <div className="space-y-1"><Label htmlFor="r-to" className="text-xs text-muted-foreground">{t('service.to')}</Label><Input id="r-to" type="date" value={to} min={from} onChange={(e) => e.target.value && setTo(e.target.value)} /></div>
      </div>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ReportSkeleton /> : (
        <div className="space-y-6">
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <Stat href={`${base}/tickets`} label={t('service.ticketsOpened')} value={data.ticketsOpened} />
            <Stat href={`${base}/tickets?status=RESOLVED`} label={t('service.ticketsResolved')} value={data.ticketsResolved} />
            <Stat href={`${base}/work-orders?status=COMPLETED`} label={t('service.woCompleted')} value={data.workOrdersCompleted} />
            <Stat href={`${base}/work-orders?status=COMPLETED`} label={t('service.completedValue')} value={usd(data.completedValue)} note={khr(data.completedValue)} />
          </div>
          <ReportCard id="r-tech" title={t('service.reportTechnicians')} subtitle={t('service.reportTechniciansNote')} refreshing={isFetching}
            onCsv={() => downloadCsv(`technicians_${from}_${to}.csv`, [t('service.technician'), t('service.woCompleted'), t('service.labourHours'), t('service.labourUsd'), t('service.partsUsd'), t('service.totalUsd')],
              data.technicians.map((r) => [r.technician.name, r.completed, Number(r.labourHours), r.labour.toFixed(2), r.parts.toFixed(2), r.total.toFixed(2)]))}>
            {data.technicians.length === 0 ? <EmptyState icon={BarChart3} title={t('service.noCompletedTitle')} body={t('service.noCompletedBody')} /> : (
              <ReportTable caption={t('service.reportTechnicians')} rowKey={(r) => r.technician.id} rows={data.technicians} columns={[
                { label: t('service.technician'), value: (r) => r.technician.name },
                { label: t('service.woCompleted'), value: (r) => r.completed, numeric: true },
                { label: t('service.labourHours'), value: (r) => Number(r.labourHours), numeric: true },
                { label: t('service.labour'), value: (r) => usd(r.labour), numeric: true },
                { label: t('service.parts'), value: (r) => usd(r.parts), numeric: true },
                { label: t('service.total'), value: (r) => usd(r.total), numeric: true },
              ]} />
            )}
          </ReportCard>
          <ReportCard id="r-sla" title={t('service.reportSla')} subtitle={t('service.reportSlaNote')} refreshing={isFetching}
            onCsv={() => downloadCsv(`sla_${from}_${to}.csv`, [t('service.priority'), t('service.tickets'), t('service.responseMet'), t('service.responseBreached'), t('service.resolutionMet'), t('service.resolutionBreached'), t('service.stillOpen')],
              data.sla.map((r) => [PRIORITY_LABEL[r.priority], r.tickets, r.responseMet, r.responseBreached, r.resolutionMet, r.resolutionBreached, r.open]))}>
            <ReportTable caption={t('service.reportSla')} rowKey={(r) => r.priority} rows={data.sla} columns={[
              { label: t('service.priority'), value: (r) => PRIORITY_LABEL[r.priority] },
              { label: t('service.tickets'), value: (r) => r.tickets, numeric: true, total: data.sla.reduce((a, r) => a + r.tickets, 0) },
              { label: t('service.responseMet'), value: (r) => `${r.responseMet} (${pct(r.responseMet, r.responseMet + r.responseBreached)})`, numeric: true },
              { label: t('service.responseBreached'), value: (r) => r.responseBreached, numeric: true, total: data.sla.reduce((a, r) => a + r.responseBreached, 0) },
              { label: t('service.resolutionMet'), value: (r) => `${r.resolutionMet} (${pct(r.resolutionMet, r.resolutionMet + r.resolutionBreached)})`, numeric: true },
              { label: t('service.resolutionBreached'), value: (r) => r.resolutionBreached, numeric: true, total: data.sla.reduce((a, r) => a + r.resolutionBreached, 0) },
              { label: t('service.stillOpen'), value: (r) => r.open, numeric: true, total: data.sla.reduce((a, r) => a + r.open, 0) },
            ]} />
          </ReportCard>
          <ReportCard id="r-cat" title={t('service.reportCategories')} refreshing={isFetching}
            onCsv={() => downloadCsv(`categories_${from}_${to}.csv`, [t('service.category'), t('service.tickets')], data.categories.map((c) => [CATEGORY_LABEL[c.category], c.tickets]))}>
            <ReportTable caption={t('service.reportCategories')} rowKey={(c) => c.category} rows={data.categories} columns={[
              { label: t('service.category'), value: (c) => CATEGORY_LABEL[c.category] },
              { label: t('service.tickets'), value: (c) => c.tickets, numeric: true, total: data.ticketsOpened },
            ]} />
          </ReportCard>
        </div>
      )}
    </>
  );
}
