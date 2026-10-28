'use client';

import { CalendarSearch, Inbox } from 'lucide-react';
import { useState } from 'react';

import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton, PermissionDenied } from '@/components/common/states';
import { LeaveList, typeName } from '@/components/hr/leave-ui';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { labels } from '@/i18n/core';
import { useT } from '@/i18n/provider';
import { LEAVE_STATUSES, useAllLeave, useLeaveTypes, usePeople } from '@/lib/api/hr';
import { useOrg } from '@/lib/org-context';

const ALL = 'all';
const STATUS_LABEL = labels(LEAVE_STATUSES, (s) => `hr.status_${s}`);

/** HR managers: everyone's leave requests, filtered by status, person, type and year. Decisions stay with the approvers. */
export default function LeaveRequests() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const thisYear = new Date().getFullYear();
  const [status, setStatus] = useState(ALL);
  const [userId, setUserId] = useState(ALL);
  const [typeId, setTypeId] = useState(ALL);
  const [year, setYear] = useState(String(thisYear));
  const manage = can('hr.manage');
  const people = usePeople(slug);
  const types = useLeaveTypes(slug);
  const filter = {
    status: status === ALL ? undefined : status, userId: userId === ALL ? undefined : userId,
    typeId: typeId === ALL ? undefined : typeId, year: year === ALL ? undefined : Number(year),
  };
  const { data, error, isPending, refetch } = useAllLeave(slug, filter, manage);
  if (!manage) return <PermissionDenied />;
  const filtered = status !== ALL || userId !== ALL || typeId !== ALL || year !== String(thisYear);

  return (
    <>
      <PageHeader title={t('hr.requestsTitle')} subtitle={t('hr.requestsSubtitle')} />
      <div className="mb-4 grid grid-cols-2 gap-3 sm:flex sm:flex-wrap sm:items-end">
        <div className="space-y-1">
          <Label htmlFor="f-status" className="text-xs text-muted-foreground">{t('hr.status')}</Label>
          <Select value={status} onValueChange={setStatus}>
            <SelectTrigger id="f-status" className="w-full sm:w-40"><SelectValue /></SelectTrigger>
            <SelectContent><SelectItem value={ALL}>{t('hr.anyStatus')}</SelectItem>{LEAVE_STATUSES.map((s) => <SelectItem key={s} value={s}>{STATUS_LABEL[s]}</SelectItem>)}</SelectContent>
          </Select>
        </div>
        <div className="space-y-1">
          <Label htmlFor="f-person" className="text-xs text-muted-foreground">{t('hr.person')}</Label>
          <Select value={userId} onValueChange={setUserId}>
            <SelectTrigger id="f-person" className="w-full sm:w-48"><SelectValue /></SelectTrigger>
            <SelectContent><SelectItem value={ALL}>{t('hr.everyone')}</SelectItem>{(people.data ?? []).map((p) => <SelectItem key={p.person.id} value={p.person.id}>{p.person.name}</SelectItem>)}</SelectContent>
          </Select>
        </div>
        <div className="space-y-1">
          <Label htmlFor="f-type" className="text-xs text-muted-foreground">{t('hr.leaveType')}</Label>
          <Select value={typeId} onValueChange={setTypeId}>
            <SelectTrigger id="f-type" className="w-full sm:w-44"><SelectValue /></SelectTrigger>
            <SelectContent><SelectItem value={ALL}>{t('hr.anyType')}</SelectItem>{(types.data ?? []).map((x) => <SelectItem key={x.id} value={x.id}>{typeName(x)}</SelectItem>)}</SelectContent>
          </Select>
        </div>
        <div className="space-y-1">
          <Label htmlFor="f-year" className="text-xs text-muted-foreground">{t('hr.year')}</Label>
          <Select value={year} onValueChange={setYear}>
            <SelectTrigger id="f-year" className="w-full sm:w-28"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>{t('hr.anyYear')}</SelectItem>
              {[thisYear + 1, thisYear, thisYear - 1, thisYear - 2].map((y) => <SelectItem key={y} value={String(y)}>{y}</SelectItem>)}
            </SelectContent>
          </Select>
        </div>
        {filtered && <Button variant="ghost" size="sm" className="col-span-2 sm:col-span-1" onClick={() => { setStatus(ALL); setUserId(ALL); setTypeId(ALL); setYear(String(thisYear)); }}>{t('common.clear')}</Button>}
      </div>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={6} />
        : data.length === 0 ? (filtered
          ? <EmptyState icon={CalendarSearch} title={t('hr.noMatchingRequests')} body={t('hr.noMatchingRequestsBody')} />
          : <EmptyState icon={Inbox} title={t('hr.noRequestsYetTitle')} body={t('hr.noRequestsYetBody')} />)
          : <LeaveList org={slug} leaves={data} showPerson />}
    </>
  );
}
