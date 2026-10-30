'use client';

import { ArrowLeft } from 'lucide-react';
import Link from 'next/link';
import { useParams, useRouter, useSearchParams } from 'next/navigation';
import { Suspense, useState } from 'react';
import { toast } from 'sonner';

import { PageHeader } from '@/components/common/page-header';
import { ErrorState, ListSkeleton } from '@/components/common/states';
import { CorrectDayDialog, TimesheetTable, TimesheetTotals, hhmm, orgToday, weekdays } from '@/components/hr/attendance-ui';
import { MonthPicker } from '@/components/hr/month-picker';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { type AttendanceDay, attendanceApi, useSchedules, useTimesheet } from '@/lib/api/hr';
import { useOrgMutation } from '@/lib/api/queries';
import { useOrg } from '@/lib/org-context';

const DEFAULT = 'default';

/** One person's month: their manager or HR (HR also corrects days and sets their schedule). */
function PersonAttendance() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const { userId } = useParams<{ userId: string }>();
  const params = useSearchParams();
  const router = useRouter();
  const month = /^\d{4}-\d{2}$/.test(params.get('month') ?? '') ? params.get('month')! : orgToday().slice(0, 7);
  const sheet = useTimesheet(slug, userId, month);
  const manage = can('hr.manage');
  const schedules = useSchedules(slug);
  const [correcting, setCorrecting] = useState<AttendanceDay | null>(null);
  const assign = useOrgMutation(slug, (id: string | null) => attendanceApi.assignSchedule(slug, userId, id));
  const fallback = schedules.data?.find((s) => s.isDefault);

  return (
    <>
      <Link href={`/${slug}/hr/${manage ? 'attendance/board' : 'people'}`} className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="size-4" />{t(manage ? 'hr.boardTitle' : 'hr.navPeople')}
      </Link>
      {sheet.error ? <ErrorState error={sheet.error} retry={() => sheet.refetch()} /> : sheet.isPending ? <ListSkeleton rows={8} /> : (
        <>
          <PageHeader title={t('hr.timesheetOf', { name: sheet.data.person.name })}
            subtitle={`${sheet.data.schedule.name} · ${hhmm(sheet.data.schedule.startTime)}–${hhmm(sheet.data.schedule.endTime)} · ${weekdays(sheet.data.schedule.workDays)}`}
            actions={<MonthPicker month={month} onChange={(m) => router.replace(`/${slug}/hr/attendance/${userId}?month=${m}`, { scroll: false })} />} />
          {manage && schedules.data && (
            <div className="mb-4 space-y-1">
              <Label htmlFor="person-schedule" className="text-xs text-muted-foreground">{t('hr.schedule')}</Label>
              <Select value={sheet.data.schedule.isDefault ? DEFAULT : sheet.data.schedule.id}
                onValueChange={async (v) => {
                  try { await assign.mutateAsync(v === DEFAULT ? null : v); toast.success(t('hr.scheduleChanged')); } catch (e) { toast.error(errorMessage(e)); }
                }}>
                <SelectTrigger id="person-schedule" className="w-full sm:w-72"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value={DEFAULT}>{t('hr.orgDefault', { name: fallback?.name ?? '' })}</SelectItem>
                  {schedules.data.filter((s) => !s.isDefault).map((s) => <SelectItem key={s.id} value={s.id}>{s.name}</SelectItem>)}
                </SelectContent>
              </Select>
            </div>
          )}
          <TimesheetTotals sheet={sheet.data} />
          <TimesheetTable sheet={sheet.data} onCorrect={sheet.data.canEdit ? setCorrecting : undefined} />
        </>
      )}
      {correcting && <CorrectDayDialog org={slug} userId={userId} day={correcting} onClose={() => setCorrecting(null)} />}
    </>
  );
}

export default function Page() {
  return <Suspense><PersonAttendance /></Suspense>;
}
