'use client';

import { CalendarX2, Check, ClipboardCheck, School } from 'lucide-react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { Suspense, useEffect, useState } from 'react';
import { toast } from 'sonner';

import { MARK_ACTIVE, MARK_LABEL, pct, studentName } from '@/components/education/ui';
import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { ReportCard, ReportTable, downloadCsv } from '@/components/reports/report-kit';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { type AttendanceSheet, eduApi, isoToday, type Mark, MARKS, useAttendance, useAttendanceSummary, useClasses } from '@/lib/api/education';
import { useOrgMutation } from '@/lib/api/queries';
import { ago } from '@/lib/format';
import { useOrg } from '@/lib/org-context';
import { cn } from '@/lib/utils';

/** The register for one class and day: a mark per student (everyone present to start with), saved in one go. */
function Register({ org, sheet }: { org: string; sheet: AttendanceSheet }) {
  const { t } = useT();
  const [marks, setMarks] = useState<Record<string, Mark>>({});
  useEffect(() => {
    setMarks(Object.fromEntries(sheet.rows.map((r) => [r.student.id, r.status ?? 'PRESENT'])));
  }, [sheet]);
  const save = useOrgMutation(org, () => eduApi.takeAttendance(org, sheet.classRef.id, {
    day: sheet.day, entries: sheet.rows.map((r) => ({ studentId: r.student.id, status: marks[r.student.id] ?? 'PRESENT' })),
  }));
  const counts = MARKS.map((m) => [m, Object.values(marks).filter((x) => x === m).length] as const);
  if (sheet.rows.length === 0) return <EmptyState icon={School} title={t('education.emptyClassTitle')} body={t('education.noRosterBody')} />;
  return (
    <section aria-labelledby="reg-h" className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 id="reg-h" className="text-base font-semibold">{t('education.registerOf', { name: sheet.classRef.name })}</h2>
        <p className="text-sm text-muted-foreground">
          {sheet.marked && sheet.recordedBy && sheet.recordedAt ? t('education.takenBy', { name: sheet.recordedBy.name, ago: ago(sheet.recordedAt) }) : t('education.notTakenYet')}
        </p>
      </div>
      <ul className="divide-y rounded-lg border bg-card">
        {sheet.rows.map((r) => (
          <li key={r.student.id} className="flex flex-wrap items-center justify-between gap-2 px-4 py-2.5">
            <span className="min-w-0"><span className="block truncate font-medium">{studentName(r.student)}</span><span className="text-xs text-muted-foreground">{r.student.number}</span></span>
            <div role="radiogroup" aria-label={t('education.markFor', { name: studentName(r.student) })} className="flex gap-1">
              {MARKS.map((m) => {
                const on = marks[r.student.id] === m;
                return (
                  <button key={m} type="button" role="radio" aria-checked={on} disabled={!sheet.canEdit}
                    onClick={() => setMarks((x) => ({ ...x, [r.student.id]: m }))}
                    className={cn('h-9 min-w-11 rounded-md border px-2 text-xs font-medium transition-colors disabled:opacity-60',
                      on ? MARK_ACTIVE[m] : 'bg-background text-muted-foreground hover:bg-muted')}>
                    {MARK_LABEL[m]}
                  </button>
                );
              })}
            </div>
          </li>
        ))}
      </ul>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-muted-foreground tabular">{counts.map(([m, n]) => `${MARK_LABEL[m]} ${n}`).join(' · ')}</p>
        {sheet.canEdit && (
          <Button disabled={save.isPending} onClick={async () => {
            try { await save.mutateAsync(undefined); toast.success(t('education.attendanceSaved')); } catch (e) { toast.error(errorMessage(e)); }
          }}><Check className="size-4" />{sheet.marked ? t('education.saveChanges') : t('education.saveAttendance')}</Button>
        )}
      </div>
    </section>
  );
}

/** Each student's attendance in the class over the last 30 days, with a CSV. */
function Summary({ org, classId }: { org: string; classId: string }) {
  const { t } = useT();
  const from = isoToday(-29);
  const to = isoToday();
  const { data, error, isPending, refetch } = useAttendanceSummary(org, classId, from, to);
  if (error) return <ErrorState error={error} retry={() => refetch()} />;
  if (isPending) return <ListSkeleton rows={3} />;
  const title = t('education.last30Days', { name: data.classRef.name });
  const csv = () => downloadCsv(`attendance_${data.classRef.name}_${from}_${to}.csv`,
    [t('education.number'), t('education.name'), t('education.mark_PRESENT'), t('education.mark_LATE'), t('education.mark_ABSENT'), t('education.mark_EXCUSED'), t('education.ratePct')],
    data.rows.map((r) => [r.student.number, r.student.nameEn, r.counts.present, r.counts.late, r.counts.absent, r.counts.excused, r.counts.rate == null ? null : r.counts.rate.toFixed(1)]));
  return (
    <ReportCard id="att-sum" title={title} subtitle={t('education.daysTaken', { count: data.days })} onCsv={csv}>
      <ReportTable caption={title} rowKey={(r) => r.student.id} rows={data.rows} columns={[
        { label: t('education.name'), value: (r) => studentName(r.student) },
        { label: t('education.mark_PRESENT'), value: (r) => r.counts.present, numeric: true, total: data.total.present },
        { label: t('education.mark_LATE'), value: (r) => r.counts.late, numeric: true, total: data.total.late },
        { label: t('education.mark_ABSENT'), value: (r) => r.counts.absent, numeric: true, total: data.total.absent },
        { label: t('education.mark_EXCUSED'), value: (r) => r.counts.excused, numeric: true, total: data.total.excused },
        { label: t('education.attendanceRate'), value: (r) => pct(r.counts.rate), numeric: true, total: pct(data.total.rate) },
      ]} />
    </ReportCard>
  );
}

function AttendancePage() {
  const { slug } = useOrg();
  const { t } = useT();
  const router = useRouter();
  const params = useSearchParams();
  const classes = useClasses(slug);
  const classId = params.get('class') ?? classes.data?.[0]?.id ?? '';
  const day = params.get('day') ?? isoToday();
  const sheet = useAttendance(slug, classId, day);
  const go = (c: string, d: string) => router.replace(`/${slug}/education/attendance?class=${c}&day=${d}`);

  return (
    <>
      <PageHeader title={t('education.attendanceTitle')} subtitle={t('education.attendanceSubtitle')} />
      {classes.error ? <ErrorState error={classes.error} retry={() => classes.refetch()} /> : classes.isPending ? <ListSkeleton /> : classes.data.length === 0 ? (
        <EmptyState icon={ClipboardCheck} title={t('education.noClassesTitle')} body={t('education.noClassesTeacher')}
          action={<Button asChild size="sm" variant="outline"><Link href={`/${slug}/education/classes`}>{t('education.navClasses')}</Link></Button>} />
      ) : (
        <div className="space-y-8">
          <div className="grid grid-cols-2 gap-3 sm:flex sm:items-end">
            <div className="space-y-1">
              <Label htmlFor="a-c" className="text-xs text-muted-foreground">{t('education.class')}</Label>
              <Select value={classId} onValueChange={(v) => go(v, day)}>
                <SelectTrigger id="a-c" className="w-full sm:w-44"><SelectValue /></SelectTrigger>
                <SelectContent>{classes.data.map((c) => <SelectItem key={c.id} value={c.id}>{c.name}</SelectItem>)}</SelectContent>
              </Select>
            </div>
            <div className="space-y-1">
              <Label htmlFor="a-d" className="text-xs text-muted-foreground">{t('education.day')}</Label>
              <Input id="a-d" type="date" max={isoToday()} value={day} onChange={(e) => e.target.value && go(classId, e.target.value)} />
            </div>
          </div>
          {sheet.error ? <ErrorState error={sheet.error} retry={() => sheet.refetch()} /> : sheet.isPending ? <ListSkeleton rows={6} />
            : !sheet.data.canEdit && !sheet.data.marked ? <EmptyState icon={CalendarX2} title={t('education.cantTakeTitle')} body={t('education.cantTakeBody')} />
              : <Register org={slug} sheet={sheet.data} />}
          {classId && <Summary org={slug} classId={classId} />}
        </div>
      )}
    </>
  );
}

export default function Attendance() {
  return <Suspense fallback={<ListSkeleton />}><AttendancePage /></Suspense>;
}
