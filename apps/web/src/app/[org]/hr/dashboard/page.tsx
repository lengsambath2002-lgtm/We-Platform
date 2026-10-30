'use client';

import { Download } from 'lucide-react';
import Link from 'next/link';
import { useState } from 'react';
import { toast } from 'sonner';

import { PageHeader } from '@/components/common/page-header';
import { ErrorState, ListSkeleton, PermissionDenied } from '@/components/common/states';
import { orgToday } from '@/components/hr/attendance-ui';
import { RunBadge, monthText } from '@/components/hr/payroll-ui';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Progress } from '@/components/ui/progress';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { downloadHrReport, useHrDashboard } from '@/lib/api/hr';
import { usd } from '@/lib/money';
import { useOrg } from '@/lib/org-context';

function Tile({ label, value, hint, href }: { label: string; value: string | number; hint?: string; href?: string }) {
  const body = <><p className="text-xs text-muted-foreground">{label}</p><p className="mt-0.5 text-2xl font-semibold tabular">{value}</p>{hint && <p className="text-xs text-muted-foreground">{hint}</p>}</>;
  return href ? <Link href={href} className="block rounded-lg border bg-card p-4 hover:border-primary/40">{body}</Link> : <div className="rounded-lg border bg-card p-4">{body}</div>;
}

/** HR managers: the workforce at a glance, and the HR reports. */
export default function HrDashboardPage() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const manage = can('hr.manage');
  const { data: d, error, isPending, refetch } = useHrDashboard(slug, manage);
  const [month, setMonth] = useState(orgToday().slice(0, 7));
  const [year, setYear] = useState(orgToday().slice(0, 4));
  if (!manage) return <PermissionDenied />;
  const get = (path: string, name: string) => downloadHrReport(slug, path, name).catch((e) => toast.error(errorMessage(e)));
  const most = Math.max(1, ...(d?.departments.map((x) => x.count) ?? [1]));

  return (
    <>
      <PageHeader title={t('hr.dashboardTitle')} subtitle={t('hr.dashboardSubtitle')} />
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={6} /> : (
        <>
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <Tile label={t('hr.headcount')} value={d.headcount} hint={t('hr.joinedRecently', { count: d.joinedLast90Days })} href={`/${slug}/hr/people`} />
            <Tile label={t('hr.inToday')} value={d.today.present} hint={t('hr.todayHint', { late: d.today.late, absent: d.today.absent, notIn: d.today.notIn })} href={`/${slug}/hr/attendance/board`} />
            <Tile label={t('hr.onLeaveToday')} value={d.today.onLeave} hint={t('hr.pendingLeaveHint', { count: d.pendingLeave })} href={`/${slug}/hr/requests`} />
            <Tile label={t('hr.overtimeWaiting')} value={d.pendingOvertime} href={`/${slug}/hr/attendance/board`} />
            <Tile label={t('hr.openingsTitle')} value={d.hiring.openJobs} hint={t('hr.activeCandidates', { count: d.hiring.activeCandidates })} href={`/${slug}/hr/recruitment`} />
            <Tile label={t('hr.onboarding')} value={d.hiring.onboarding} hint={t('hr.inProgress')} href={`/${slug}/hr/recruitment?tab=hires`} />
            <Tile label={t('hr.trainedThisYear')} value={d.trainingCompletedThisYear} href={`/${slug}/hr/training`} />
            {d.payroll ? (
              <Link href={`/${slug}/hr/payroll/${d.payroll.runId}`} className="block rounded-lg border bg-card p-4 hover:border-primary/40">
                <p className="flex items-center justify-between gap-2 text-xs text-muted-foreground">{monthText(d.payroll.month)}<RunBadge status={d.payroll.status} /></p>
                <p className="mt-0.5 text-2xl font-semibold tabular">{usd(d.payroll.net)}</p>
                <p className="text-xs text-muted-foreground">{t('hr.netPayFor', { count: d.payroll.people })}</p>
              </Link>
            ) : <Tile label={t('hr.payrollTitle')} value="—" hint={t('hr.noRuns')} href={`/${slug}/hr/payroll`} />}
          </div>

          <div className="mt-6 grid gap-4 lg:grid-cols-2">
            <section aria-labelledby="dept-h" className="rounded-lg border bg-card p-4">
              <h2 id="dept-h" className="mb-3 text-base font-semibold">{t('hr.byDepartment')}</h2>
              <ul className="space-y-2.5">
                {d.departments.map((x) => (
                  <li key={x.label}>
                    <div className="flex justify-between text-sm"><span>{x.label || t('hr.noDepartment')}</span><span className="tabular text-muted-foreground">{x.count}</span></div>
                    <div className="mt-1 h-2 rounded-full bg-muted" aria-hidden><div className="h-2 rounded-full bg-primary" style={{ width: `${(x.count / most) * 100}%` }} /></div>
                  </li>
                ))}
              </ul>
            </section>
            <section aria-labelledby="rev-h" className="rounded-lg border bg-card p-4">
              <h2 id="rev-h" className="mb-3 text-base font-semibold">{t('hr.reviewsInProgress')}</h2>
              {d.reviews.length === 0 ? <p className="text-sm text-muted-foreground">{t('hr.noOpenCycles')}</p> : (
                <ul className="space-y-3">
                  {d.reviews.map((r) => (
                    <li key={r.cycleId}>
                      <Link href={`/${slug}/hr/reviews/cycles/${r.cycleId}`} className="flex justify-between text-sm hover:underline"><span>{r.name}</span><span className="tabular text-muted-foreground">{t('hr.reviewedOf', { done: r.reviewed, total: r.total })}</span></Link>
                      <Progress className="mt-1" value={r.total ? (r.reviewed / r.total) * 100 : 0} aria-hidden />
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </div>

          <section aria-labelledby="rep-h" className="mt-6 rounded-lg border bg-card p-4">
            <h2 id="rep-h" className="text-base font-semibold">{t('hr.reports')}</h2>
            <p className="mb-4 text-sm text-muted-foreground">{t('hr.reportsBody')}</p>
            <div className="grid gap-4 md:grid-cols-3">
              <div className="space-y-2">
                <p className="text-sm font-medium">{t('hr.reportPeople')}</p>
                <Button variant="outline" size="sm" onClick={() => get('people.csv', 'employees.csv')}><Download className="size-4" />CSV</Button>
              </div>
              <div className="space-y-2">
                <Label htmlFor="rep-month" className="text-sm font-medium">{t('hr.reportAttendance')}</Label>
                <div className="flex gap-2"><Input id="rep-month" type="month" className="w-40" value={month} onChange={(e) => setMonth(e.target.value)} />
                  <Button variant="outline" size="sm" disabled={!month} onClick={() => get(`attendance.csv?month=${month}`, `attendance-${month}.csv`)}><Download className="size-4" />CSV</Button></div>
              </div>
              <div className="space-y-2">
                <Label htmlFor="rep-year" className="text-sm font-medium">{t('hr.reportLeave')}</Label>
                <div className="flex gap-2"><Input id="rep-year" type="number" min={2000} max={2100} className="w-28" value={year} onChange={(e) => setYear(e.target.value)} />
                  <Button variant="outline" size="sm" disabled={!/^\d{4}$/.test(year)} onClick={() => get(`leave.csv?year=${year}`, `leave-${year}.csv`)}><Download className="size-4" />CSV</Button></div>
              </div>
            </div>
          </section>
        </>
      )}
    </>
  );
}
