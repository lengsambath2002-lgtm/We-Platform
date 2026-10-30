'use client';

import { AlertTriangle, CalendarCheck, ClipboardCheck, ClipboardList, GraduationCap, Settings2, UserMinus } from 'lucide-react';
import Link from 'next/link';

import { AttendanceChart } from '@/components/education/attendance-chart';
import { KIND_LABEL, Money, Panel, pct, studentName, subjectName, WithdrawalBadge } from '@/components/education/ui';
import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { useT } from '@/i18n/provider';
import { isoToday, useEduOverview } from '@/lib/api/education';
import { day } from '@/lib/format';
import { useOrg } from '@/lib/org-context';
import { cn } from '@/lib/utils';

function Stat({ href, label, value, note, tone }: { href: string; label: string; value: React.ReactNode; note?: string; tone?: 'danger' }) {
  return (
    <Link href={href} className="rounded-lg border bg-card p-4 hover:bg-muted/40">
      <p className="text-sm text-muted-foreground">{label}</p>
      <div className={cn('mt-1 text-2xl font-semibold tabular', tone === 'danger' && 'text-destructive')}>{value}</div>
      {note && <p className="mt-1 text-xs text-muted-foreground">{note}</p>}
    </Link>
  );
}

/** The education home: the school (or a teacher's classes) at a glance, what needs doing today, and the attendance trend. */
export default function EducationHome() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const { data, error, isPending, refetch } = useEduOverview(slug);
  const base = `/${slug}/education`;
  const manage = can('education.manage');

  return (
    <>
      <PageHeader title={t('education.homeTitle')} subtitle={data?.currentYear ? t('education.homeSubtitle', { year: data.currentYear.name }) : t('education.homeSubtitleNoYear')}
        actions={manage ? <Button asChild size="sm"><Link href={`${base}/students/new`}><GraduationCap className="size-4" />{t('education.registerStudent')}</Link></Button>
          : <Button asChild size="sm"><Link href={`${base}/attendance`}><ClipboardCheck className="size-4" />{t('education.takeAttendance')}</Link></Button>} />
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? (
        <div className="space-y-6"><div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">{[0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-24" />)}</div><ListSkeleton rows={4} /></div>
      ) : (
        <div className="space-y-8">
          {!data.currentYear && (
            <EmptyState icon={Settings2} title={t('education.noYearTitle')} body={manage ? t('education.noYearBody') : t('education.noYearTeacher')}
              action={manage && <Button asChild size="sm"><Link href={`${base}/settings`}>{t('education.setUpYear')}</Link></Button>} />
          )}
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <Stat href={`${base}/students`} label={data.admin ? t('education.activeStudents') : t('education.myStudents')} value={data.activeStudents}
              note={t('education.inClasses', { count: data.classes })} />
            <Stat href={`${base}/attendance`} label={t('education.attendanceToday')} value={pct(data.attendanceToday)}
              note={t('education.registersTaken', { done: data.classesMarkedToday, total: data.classes })} />
            {data.admin && data.outstanding !== null ? (
              <>
                <Stat href={`${base}/fees?status=OPEN`} label={t('education.feesOwed')} value={<Money value={data.outstanding} />} />
                <Stat href={`${base}/fees?status=OVERDUE`} label={t('education.feesOverdue')} value={<Money value={data.overdue ?? 0} />}
                  tone={(data.overdue ?? 0) > 0 ? 'danger' : undefined} note={t('education.overdueInvoices', { count: data.overdueInvoices ?? 0 })} />
              </>
            ) : (
              <>
                <Stat href={`${base}/teachers`} label={t('education.teachers')} value={data.teachers} />
                <Stat href={`${base}/exams`} label={t('education.upcomingAssessments')} value={data.upcoming.length} />
              </>
            )}
          </div>
          <div className="grid gap-8 lg:grid-cols-2">
            <Panel id="unmarked-h" title={t('education.registersToTake')}
              action={<Link href={`${base}/attendance`} className="text-sm text-primary hover:underline">{t('education.navAttendance')}</Link>}>
              {data.unmarkedToday.length === 0 ? (
                <EmptyState icon={CalendarCheck} title={t('education.allMarkedTitle')} body={t('education.allMarkedBody')} />
              ) : (
                <ul className="divide-y rounded-lg border bg-card">
                  {data.unmarkedToday.map((c) => (
                    <li key={c.id}>
                      <Link href={`${base}/attendance?class=${c.id}&day=${isoToday()}`} className="flex items-center justify-between gap-3 px-4 py-3 hover:bg-muted/50">
                        <span className="min-w-0"><span className="block font-medium">{c.name}</span>
                          <span className="block truncate text-xs text-muted-foreground">{[c.gradeName, c.homeroom?.name].filter(Boolean).join(' · ')}</span></span>
                        <span className="shrink-0 text-sm text-primary">{t('education.takeIt')}</span>
                      </Link>
                    </li>
                  ))}
                </ul>
              )}
            </Panel>
            <Panel id="upcoming-h" title={t('education.upcomingAssessments')}
              action={<Link href={`${base}/exams`} className="text-sm text-primary hover:underline">{t('education.navExams')}</Link>}>
              {data.upcoming.length === 0 ? (
                <EmptyState icon={ClipboardList} title={t('education.noUpcomingTitle')} body={t('education.noUpcomingBody')} />
              ) : (
                <ul className="divide-y rounded-lg border bg-card">
                  {data.upcoming.map((a) => (
                    <li key={a.id}>
                      <Link href={`${base}/gradebook?course=${a.course.id}&term=${a.term.id}`} className="flex items-center justify-between gap-3 px-4 py-3 hover:bg-muted/50">
                        <span className="min-w-0"><span className="block truncate font-medium">{a.name} · {subjectName(a.course.subject)}</span>
                          <span className="block text-xs text-muted-foreground">{a.course.classRef.name} · {KIND_LABEL[a.kind]}</span></span>
                        <span className="shrink-0 text-sm text-muted-foreground">{day(a.heldOn)}</span>
                      </Link>
                    </li>
                  ))}
                </ul>
              )}
            </Panel>
          </div>
          <AttendanceChart weeks={data.weeks} />
          {data.pendingWithdrawals.length > 0 && (
            <Panel id="wd-h" title={data.admin ? t('education.withdrawalsWaiting') : t('education.myWithdrawals')}>
              <ul className="divide-y rounded-lg border bg-card">
                {data.pendingWithdrawals.map((w) => (
                  <li key={w.id}>
                    <Link href={w.approvalId ? `/${slug}/approvals/${w.approvalId}` : `${base}/students/${w.student.id}`}
                      className="flex items-center justify-between gap-3 px-4 py-3 hover:bg-muted/50">
                      <span className="flex min-w-0 items-center gap-2"><UserMinus className="size-4 shrink-0 text-muted-foreground" />
                        <span className="min-w-0"><span className="block truncate font-medium">{studentName(w.student)} · {w.student.number}</span>
                          <span className="block truncate text-xs text-muted-foreground">{w.reason}</span></span></span>
                      <WithdrawalBadge w={w} />
                    </Link>
                  </li>
                ))}
              </ul>
            </Panel>
          )}
          {data.admin && (data.overdueInvoices ?? 0) > 0 && (
            <p className="flex items-center gap-2 text-sm text-destructive"><AlertTriangle className="size-4" />
              <Link href={`${base}/fees?status=OVERDUE`} className="hover:underline">{t('education.overdueNotice', { count: data.overdueInvoices ?? 0 })}</Link></p>
          )}
        </div>
      )}
    </>
  );
}
