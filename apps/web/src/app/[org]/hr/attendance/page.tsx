'use client';

import { Timer } from 'lucide-react';
import { useRouter, useSearchParams } from 'next/navigation';
import { Suspense, useState } from 'react';
import { toast } from 'sonner';

import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { OvertimeDialog, OvertimeList, TimesheetTable, TimesheetTotals, TodayCard, orgToday } from '@/components/hr/attendance-ui';
import { MonthPicker } from '@/components/hr/month-picker';
import { Button } from '@/components/ui/button';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { attendanceApi, useMyOvertime, useTimesheet } from '@/lib/api/hr';
import { useOrgMutation } from '@/lib/api/queries';
import { useOrg } from '@/lib/org-context';

/** My attendance: today's clock-in and clock-out, my month (`?month=yyyy-MM`) and my overtime requests. */
function MyAttendance() {
  const { slug } = useOrg();
  const { t } = useT();
  const params = useSearchParams();
  const router = useRouter();
  const month = /^\d{4}-\d{2}$/.test(params.get('month') ?? '') ? params.get('month')! : orgToday().slice(0, 7);
  const sheet = useTimesheet(slug, '', month);
  const overtime = useMyOvertime(slug);
  const [asking, setAsking] = useState(false);
  const withdraw = useOrgMutation(slug, (id: string) => attendanceApi.withdrawOvertime(slug, id));

  return (
    <>
      <PageHeader title={t('hr.attTitle')} subtitle={t('hr.attSubtitle')}
        actions={<Button size="sm" variant="outline" onClick={() => setAsking(true)}><Timer className="size-4" />{t('hr.requestOvertime')}</Button>} />
      <div className="grid gap-6 lg:grid-cols-[1fr_300px]">
        <section aria-labelledby="month-h" className="min-w-0">
          <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
            <h2 id="month-h" className="text-base font-semibold">{t('hr.myMonth')}</h2>
            <MonthPicker month={month} onChange={(m) => router.replace(`/${slug}/hr/attendance?month=${m}`, { scroll: false })} />
          </div>
          {sheet.error ? <ErrorState error={sheet.error} retry={() => sheet.refetch()} /> : sheet.isPending ? <ListSkeleton rows={8} /> : (
            <>
              <TimesheetTotals sheet={sheet.data} />
              <TimesheetTable sheet={sheet.data} />
            </>
          )}
        </section>
        {/* On phones, clocking in comes first. */}
        <aside className="order-first space-y-4 lg:order-none">
          <TodayCard org={slug} />
          <section aria-labelledby="ot-h">
            <h2 id="ot-h" className="mb-2 text-sm font-semibold">{t('hr.overtime')}</h2>
            {overtime.error ? <ErrorState error={overtime.error} retry={() => overtime.refetch()} /> : overtime.isPending ? <ListSkeleton rows={2} />
              : overtime.data.length === 0 ? <EmptyState icon={Timer} title={t('hr.noOvertime')} body={t('hr.noOvertimeBody')} />
                : <OvertimeList items={overtime.data} onWithdraw={async (o) => {
                  try { await withdraw.mutateAsync(o.id); toast.success(t('hr.otWithdrawn')); } catch (e) { toast.error(errorMessage(e)); }
                }} />}
          </section>
        </aside>
      </div>
      {asking && <OvertimeDialog org={slug} onClose={() => setAsking(false)} />}
    </>
  );
}

export default function Page() {
  return <Suspense><MyAttendance /></Suspense>;
}
