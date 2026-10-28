'use client';

import { endOfWeek, format, startOfWeek } from 'date-fns';
import { CalendarCheck2, CalendarPlus, Palmtree } from 'lucide-react';
import Link from 'next/link';
import { useState } from 'react';

import { PageHeader } from '@/components/common/page-header';
import { PersonAvatar } from '@/components/common/person';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { BalanceCards, LeaveList, rangeText, typeName } from '@/components/hr/leave-ui';
import { RequestLeaveDialog } from '@/components/hr/request-leave-dialog';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { useT } from '@/i18n/provider';
import { type Absence, useAbsences, useBalances, useMyLeave } from '@/lib/api/hr';
import { useOrg } from '@/lib/org-context';

const iso = (d: Date) => format(d, 'yyyy-MM-dd');

function AbsenceList({ items, empty }: { items: Absence[]; empty: string }) {
  const { slug } = useOrg();
  if (items.length === 0) return <p className="py-2 text-sm text-muted-foreground">{empty}</p>;
  return (
    <ul className="space-y-2">
      {items.map((a) => (
        <li key={a.leaveId}>
          <Link href={`/${slug}/hr/leave/${a.leaveId}`} className="flex items-center gap-3 rounded-md p-1.5 hover:bg-muted/50">
            <PersonAvatar person={a.person} className="size-7" />
            <span className="min-w-0 flex-1">
              <span className="block truncate text-sm font-medium">{a.person.name}</span>
              <span className="block truncate text-xs text-muted-foreground">{a.type ? `${typeName(a.type)} · ` : ''}{rangeText(a)}</span>
            </span>
          </Link>
        </li>
      ))}
    </ul>
  );
}

/** HR home: my balances, my requests, "Request leave", and who is away today and this week. */
export default function HrHome() {
  const { slug } = useOrg();
  const { t } = useT();
  const thisYear = new Date().getFullYear();
  const [year, setYear] = useState(thisYear);
  const [asking, setAsking] = useState(false);
  const balances = useBalances(slug, '', year);
  const mine = useMyLeave(slug);
  const now = new Date();
  const from = iso(startOfWeek(now, { weekStartsOn: 1 }));
  const to = iso(endOfWeek(now, { weekStartsOn: 1 }));
  const away = useAbsences(slug, from, to);
  const today = iso(now);
  const awayToday = (away.data ?? []).filter((a) => a.startDate <= today && a.endDate >= today);
  const awayLater = (away.data ?? []).filter((a) => !(a.startDate <= today && a.endDate >= today));

  return (
    <>
      <PageHeader title={t('hr.homeTitle')} subtitle={t('hr.homeSubtitle')}
        actions={<Button size="sm" onClick={() => setAsking(true)}><CalendarPlus className="size-4" />{t('hr.requestLeave')}</Button>} />
      <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
        <div className="min-w-0 space-y-6">
          <section aria-labelledby="bal-h">
            <div className="mb-3 flex items-center justify-between gap-2">
              <h2 id="bal-h" className="text-base font-semibold">{t('hr.myBalances')}</h2>
              <Select value={String(year)} onValueChange={(v) => setYear(Number(v))}>
                <SelectTrigger size="sm" className="w-28" aria-label={t('hr.year')}><SelectValue /></SelectTrigger>
                <SelectContent>{[thisYear - 1, thisYear, thisYear + 1].map((y) => <SelectItem key={y} value={String(y)}>{y}</SelectItem>)}</SelectContent>
              </Select>
            </div>
            {balances.error ? <ErrorState error={balances.error} retry={() => balances.refetch()} />
              : balances.isPending ? <ListSkeleton rows={2} />
                : balances.data.length === 0 ? <EmptyState icon={Palmtree} title={t('hr.noTypesTitle')} body={t('hr.noTypesBody')} />
                  : <BalanceCards balances={balances.data} />}
          </section>
          <section aria-labelledby="mine-h">
            <h2 id="mine-h" className="mb-3 text-base font-semibold">{t('hr.myRequests')}</h2>
            {mine.error ? <ErrorState error={mine.error} retry={() => mine.refetch()} />
              : mine.isPending ? <ListSkeleton rows={3} />
                : mine.data.length === 0 ? (
                  <EmptyState icon={CalendarCheck2} title={t('hr.noRequestsTitle')} body={t('hr.noRequestsBody')}
                    action={<Button size="sm" variant="outline" onClick={() => setAsking(true)}>{t('hr.requestLeave')}</Button>} />
                ) : <LeaveList org={slug} leaves={mine.data} />}
          </section>
        </div>
        <aside className="space-y-4" aria-label={t('hr.whoIsAway')}>
          <section aria-labelledby="today-h" className="rounded-lg border bg-card p-4">
            <h2 id="today-h" className="mb-2 text-sm font-semibold">{t('hr.awayToday')}</h2>
            {away.error ? <ErrorState error={away.error} retry={() => away.refetch()} /> : away.isPending ? <ListSkeleton rows={2} />
              : <AbsenceList items={awayToday} empty={t('hr.nobodyAwayToday')} />}
          </section>
          <section aria-labelledby="week-h" className="rounded-lg border bg-card p-4">
            <h2 id="week-h" className="mb-2 text-sm font-semibold">{t('hr.awayThisWeek', { from: rangeText({ startDate: from, endDate: to, halfStart: false, halfEnd: false }) })}</h2>
            {away.error ? null : away.isPending ? <ListSkeleton rows={2} />
              : <AbsenceList items={awayLater} empty={t('hr.nobodyElseThisWeek')} />}
            <Link href={`/${slug}/calendar?date=${today}`} className="mt-3 block text-xs text-primary hover:underline">{t('hr.seeCalendar')}</Link>
          </section>
        </aside>
      </div>
      {asking && <RequestLeaveDialog org={slug} open onOpenChange={setAsking} />}
    </>
  );
}
