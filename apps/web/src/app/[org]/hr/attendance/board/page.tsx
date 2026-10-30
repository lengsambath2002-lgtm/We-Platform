'use client';

import { Timer, Users } from 'lucide-react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { Suspense } from 'react';

import { PageHeader } from '@/components/common/page-header';
import { PersonAvatar } from '@/components/common/person';
import { EmptyState, ErrorState, ListSkeleton, PermissionDenied } from '@/components/common/states';
import { DayBadge, OvertimeList, duration, orgTime, orgToday } from '@/components/hr/attendance-ui';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useT } from '@/i18n/provider';
import { useAllOvertime, useAttendanceBoard } from '@/lib/api/hr';
import { useOrg } from '@/lib/org-context';

/** HR managers: everyone's day (`?date=yyyy-MM-dd`, default today), and overtime waiting for a decision. */
function Board() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const params = useSearchParams();
  const router = useRouter();
  const manage = can('hr.manage');
  const date = /^\d{4}-\d{2}-\d{2}$/.test(params.get('date') ?? '') ? params.get('date')! : orgToday();
  const { data, error, isPending, refetch } = useAttendanceBoard(slug, date, manage);
  const waiting = useAllOvertime(slug, 'PENDING', manage);
  if (!manage) return <PermissionDenied />;
  const count = (s: string[]) => (data ?? []).filter((r) => s.includes(r.day.status)).length;

  return (
    <>
      <PageHeader title={t('hr.boardTitle')} subtitle={data ? t('hr.boardSummary', {
        present: count(['PRESENT', 'LATE']), late: count(['LATE']), absent: count(['ABSENT']), leave: count(['LEAVE']),
      }) : t('hr.boardSubtitle')} />
      <div className="mb-4 space-y-1">
        <Label htmlFor="board-date" className="text-xs text-muted-foreground">{t('hr.colDay')}</Label>
        <Input id="board-date" type="date" className="w-44" value={date} max={orgToday()}
          onChange={(e) => e.target.value && router.replace(`/${slug}/hr/attendance/board?date=${e.target.value}`, { scroll: false })} />
      </div>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={6} />
        : data.length === 0 ? <EmptyState icon={Users} title={t('hr.nobodyToShow')} /> : (
          <div className="overflow-x-auto rounded-lg border bg-card">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('hr.person')}</TableHead>
                  <TableHead>{t('hr.colStatus')}</TableHead>
                  <TableHead>{t('hr.colIn')}</TableHead>
                  <TableHead>{t('hr.colOut')}</TableHead>
                  <TableHead className="text-right">{t('hr.colWorked')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.map((r) => (
                  <TableRow key={r.person.id}>
                    <TableCell className="min-w-52">
                      <Link href={`/${slug}/hr/attendance/${r.person.id}?month=${date.slice(0, 7)}`} className="flex items-center gap-3 hover:underline">
                        <PersonAvatar person={r.person} className="size-7" />
                        <span className="min-w-0">
                          <span className="block truncate text-sm font-medium">{r.person.name}</span>
                          <span className="block truncate text-xs text-muted-foreground">{[r.department, r.schedule.name].filter(Boolean).join(' · ')}</span>
                        </span>
                      </Link>
                    </TableCell>
                    <TableCell>
                      <DayBadge status={r.day.status} />
                      {r.day.lateMinutes > 0 && <span className="ml-2 text-xs text-amber-700 dark:text-amber-300">{t('hr.lateBy', { time: duration(r.day.lateMinutes) })}</span>}
                    </TableCell>
                    <TableCell className="tabular">{orgTime(r.day.clockIn)}</TableCell>
                    <TableCell className="tabular">{orgTime(r.day.clockOut)}</TableCell>
                    <TableCell className="text-right tabular">{r.day.workedMinutes ? duration(r.day.workedMinutes) : ''}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
      <section aria-labelledby="otw-h" className="mt-6">
        <h2 id="otw-h" className="text-base font-semibold">{t('hr.overtimeWaiting')}</h2>
        <p className="mb-3 text-sm text-muted-foreground">{t('hr.overtimeWaitingBody')}</p>
        {waiting.error ? <ErrorState error={waiting.error} retry={() => waiting.refetch()} /> : waiting.isPending ? <ListSkeleton rows={2} />
          : waiting.data.length === 0 ? <EmptyState icon={Timer} title={t('hr.noOvertime')} /> : <OvertimeList items={waiting.data} showPerson />}
      </section>
    </>
  );
}

export default function Page() {
  return <Suspense><Board /></Suspense>;
}
