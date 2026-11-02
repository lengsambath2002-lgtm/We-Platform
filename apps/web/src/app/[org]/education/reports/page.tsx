'use client';

import { BarChart3 } from 'lucide-react';
import { useState } from 'react';

import { pct } from '@/components/education/ui';
import { ReportsTabs } from '@/components/common/merged-tabs';
import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState } from '@/components/common/states';
import { ReportCard, ReportSkeleton, ReportTable, downloadCsv } from '@/components/reports/report-kit';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { useT } from '@/i18n/provider';
import { useEduReports, useYears } from '@/lib/api/education';
import { day } from '@/lib/format';
import { usd } from '@/lib/money';
import { useOrg } from '@/lib/org-context';

/** Reports: attendance by class over a period, results by class for a term, and (admins) fees by grade — each with a table and a CSV. */
export default function Reports() {
  const { slug } = useOrg();
  const { t } = useT();
  const years = useYears(slug);
  const terms = years.data?.find((y) => y.current)?.terms ?? [];
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [termId, setTermId] = useState('');
  const { data, error, isPending, isFetching, refetch } = useEduReports(slug, { from: from || undefined, to: to || undefined, termId: termId || undefined });

  return (
    <>
      <ReportsTabs />
      <PageHeader title={t('education.reportsTitle')} subtitle={t('education.reportsSubtitle')} />
      <div className="mb-6 grid grid-cols-2 gap-3 sm:flex sm:items-end">
        <div className="space-y-1">
          <Label htmlFor="r-f" className="text-xs text-muted-foreground">{t('education.from')}</Label>
          <Input id="r-f" type="date" value={from || data?.from || ''} onChange={(e) => setFrom(e.target.value)} />
        </div>
        <div className="space-y-1">
          <Label htmlFor="r-t" className="text-xs text-muted-foreground">{t('education.to')}</Label>
          <Input id="r-t" type="date" value={to || data?.to || ''} onChange={(e) => setTo(e.target.value)} />
        </div>
        {terms.length > 0 && (
          <div className="col-span-2 space-y-1">
            <Label htmlFor="r-term" className="text-xs text-muted-foreground">{t('education.termForResults')}</Label>
            <Select value={termId || data?.term?.id || ''} onValueChange={setTermId}>
              <SelectTrigger id="r-term" className="w-full sm:w-48"><SelectValue placeholder={t('education.chooseTerm')} /></SelectTrigger>
              <SelectContent>{terms.map((x) => <SelectItem key={x.id} value={x.id}>{x.name}</SelectItem>)}</SelectContent>
            </Select>
          </div>
        )}
      </div>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ReportSkeleton /> : data.attendance.length === 0 ? (
        <EmptyState icon={BarChart3} title={t('education.noReportsTitle')} body={t('education.noReportsBody')} />
      ) : (
        <div className="space-y-6">
          <ReportCard id="r-att" title={t('education.attendanceByClass')} subtitle={t('education.periodLabel', { from: day(data.from), to: day(data.to) })} refreshing={isFetching}
            onCsv={() => downloadCsv(`attendance-by-class_${data.from}_${data.to}.csv`,
              [t('education.class'), t('education.gradeLevel'), t('education.daysTakenShort'), t('education.mark_PRESENT'), t('education.mark_LATE'), t('education.mark_ABSENT'), t('education.mark_EXCUSED'), t('education.ratePct')],
              data.attendance.map((r) => [r.classRef.name, r.gradeName, r.days, r.counts.present, r.counts.late, r.counts.absent, r.counts.excused, r.counts.rate == null ? null : r.counts.rate.toFixed(1)]))}>
            <ReportTable caption={t('education.attendanceByClass')} rowKey={(r) => r.classRef.id} rows={data.attendance} columns={[
              { label: t('education.class'), value: (r) => r.classRef.name },
              { label: t('education.gradeLevel'), value: (r) => r.gradeName ?? '—' },
              { label: t('education.daysTakenShort'), value: (r) => r.days, numeric: true },
              { label: t('education.mark_ABSENT'), value: (r) => r.counts.absent, numeric: true, total: data.attendance.reduce((a, r) => a + r.counts.absent, 0) },
              { label: t('education.mark_LATE'), value: (r) => r.counts.late, numeric: true, total: data.attendance.reduce((a, r) => a + r.counts.late, 0) },
              { label: t('education.attendanceRate'), value: (r) => pct(r.counts.rate), numeric: true },
            ]} />
          </ReportCard>
          {data.results && data.term && (
            <ReportCard id="r-res" title={t('education.resultsByClass')} subtitle={data.term.name} refreshing={isFetching}
              onCsv={() => downloadCsv(`results-by-class_${data.term?.name ?? ''}.csv`,
                [t('education.class'), t('education.gradeLevel'), t('education.studentsHeader'), t('education.gradedHeader'), t('education.averagePct'), t('education.passRatePct')],
                data.results!.map((r) => [r.classRef.name, r.gradeName, r.students, r.graded, r.average == null ? null : r.average.toFixed(1), r.passRate == null ? null : r.passRate.toFixed(1)]))}>
              <ReportTable caption={t('education.resultsByClass')} rowKey={(r) => r.classRef.id} rows={data.results} columns={[
                { label: t('education.class'), value: (r) => r.classRef.name },
                { label: t('education.studentsHeader'), value: (r) => r.students, numeric: true },
                { label: t('education.gradedHeader'), value: (r) => r.graded, numeric: true },
                { label: t('education.average'), value: (r) => pct(r.average), numeric: true },
                { label: t('education.passRate'), value: (r) => pct(r.passRate), numeric: true },
              ]} />
            </ReportCard>
          )}
          {data.fees && (
            <ReportCard id="r-fee" title={t('education.feesByGrade')} subtitle={t('education.feesByGradeNote')} refreshing={isFetching}
              onCsv={() => downloadCsv('fees-by-grade.csv',
                [t('education.gradeLevel'), t('education.invoices'), t('education.billedUsd'), t('education.collectedUsd'), t('education.outstandingUsd'), t('education.overdueUsd')],
                data.fees!.map((r) => [r.gradeName ?? '—', r.invoices, r.billed.toFixed(2), r.collected.toFixed(2), r.outstanding.toFixed(2), r.overdue.toFixed(2)]))}>
              {data.fees.length === 0 ? <p className="text-sm text-muted-foreground">{t('education.noInvoicesBody')}</p> : (
                <ReportTable caption={t('education.feesByGrade')} rowKey={(r) => r.gradeId ?? 'none'} rows={data.fees} columns={[
                  { label: t('education.gradeLevel'), value: (r) => r.gradeName ?? t('education.noGrade') },
                  { label: t('education.billed'), value: (r) => usd(r.billed), numeric: true, total: usd(data.fees!.reduce((a, r) => a + r.billed, 0)) },
                  { label: t('education.collected'), value: (r) => usd(r.collected), numeric: true, total: usd(data.fees!.reduce((a, r) => a + r.collected, 0)) },
                  { label: t('education.outstanding'), value: (r) => usd(r.outstanding), numeric: true, total: usd(data.fees!.reduce((a, r) => a + r.outstanding, 0)) },
                  { label: t('education.overdue'), value: (r) => usd(r.overdue), numeric: true, total: usd(data.fees!.reduce((a, r) => a + r.overdue, 0)) },
                ]} />
              )}
            </ReportCard>
          )}
        </div>
      )}
    </>
  );
}
