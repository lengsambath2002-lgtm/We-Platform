'use client';

import { Palmtree, Pencil, Plus, SlidersHorizontal, UserRound } from 'lucide-react';
import { useRouter, useSearchParams } from 'next/navigation';
import { Suspense, useState } from 'react';

import { PageHeader } from '@/components/common/page-header';
import { PersonAvatar } from '@/components/common/person';
import { EmptyState, ErrorState, ListSkeleton, PermissionDenied } from '@/components/common/states';
import { Holidays } from '@/components/hr/holidays';
import { BalanceCards, daysText, typeName } from '@/components/hr/leave-ui';
import { AdjustDialog, LeaveTypeDialog } from '@/components/hr/settings-dialogs';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useT } from '@/i18n/provider';
import { type LeaveType, useAdjustments, useBalances, useLeaveTypes, usePeople } from '@/lib/api/hr';
import { ago } from '@/lib/format';
import { useOrg } from '@/lib/org-context';

function LeaveTypes({ org }: { org: string }) {
  const { t } = useT();
  const { data, error, isPending, refetch } = useLeaveTypes(org);
  const [editing, setEditing] = useState<LeaveType | 'new' | null>(null);
  return (
    <section aria-labelledby="types-h">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <div>
          <h2 id="types-h" className="text-base font-semibold">{t('hr.leaveTypes')}</h2>
          <p className="text-sm text-muted-foreground">{t('hr.leaveTypesBody')}</p>
        </div>
        <Button size="sm" onClick={() => setEditing('new')}><Plus className="size-4" />{t('hr.newType')}</Button>
      </div>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={3} />
        : data.length === 0 ? <EmptyState icon={Palmtree} title={t('hr.noTypesTitle')} body={t('hr.noTypesBody')} /> : (
          <ul className="divide-y rounded-lg border bg-card">
            {data.map((x) => (
              <li key={x.id} className="flex items-center gap-3 px-4 py-3">
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium">{typeName(x)} <span className="font-normal text-muted-foreground" lang={x.nameKm === typeName(x) ? 'en' : 'km'}>· {x.nameKm === typeName(x) ? x.nameEn : x.nameKm}</span></p>
                  <p className="text-xs text-muted-foreground">
                    {[x.allowanceDays === null ? t('hr.unlimited') : t('hr.perYear', { days: daysText(x.allowanceDays) }), t(x.paid ? 'hr.paid' : 'hr.unpaid'),
                      t(x.halfDays ? 'hr.halfDaysAllowed' : 'hr.wholeDaysOnly'), ...(x.active ? [] : [t('hr.notOffered')])].join(' · ')}
                  </p>
                </div>
                <Button variant="ghost" size="icon" className="size-8 shrink-0" aria-label={t('hr.editType', { name: typeName(x) })} onClick={() => setEditing(x)}><Pencil className="size-4" /></Button>
              </li>
            ))}
          </ul>
        )}
      {editing && <LeaveTypeDialog org={org} type={editing} onClose={() => setEditing(null)} />}
    </section>
  );
}

function Balances({ org }: { org: string }) {
  const { t } = useT();
  const params = useSearchParams();
  const router = useRouter();
  const people = usePeople(org);
  const thisYear = new Date().getFullYear();
  const [year, setYear] = useState(thisYear);
  const userId = params.get('person') ?? '';
  const person = people.data?.find((p) => p.person.id === userId);
  const balances = useBalances(org, userId, year, !!userId);
  const history = useAdjustments(org, userId, year);
  const [adjusting, setAdjusting] = useState(false);
  const choose = (id: string) => router.replace(`/${org}/hr/settings?tab=balances&person=${id}`, { scroll: false });

  return (
    <section aria-labelledby="bal-h">
      <h2 id="bal-h" className="text-base font-semibold">{t('hr.balances')}</h2>
      <p className="mb-3 text-sm text-muted-foreground">{t('hr.balancesBody')}</p>
      <div className="mb-4 grid grid-cols-[1fr_auto] gap-3 sm:flex sm:items-end">
        <div className="min-w-0 space-y-1">
          <Label htmlFor="b-person" className="text-xs text-muted-foreground">{t('hr.person')}</Label>
          <Select value={userId} onValueChange={choose}>
            <SelectTrigger id="b-person" className="w-full sm:w-64"><SelectValue placeholder={t('hr.choosePerson')} /></SelectTrigger>
            <SelectContent>{(people.data ?? []).map((p) => <SelectItem key={p.person.id} value={p.person.id}>{p.person.name}</SelectItem>)}</SelectContent>
          </Select>
        </div>
        <div className="space-y-1">
          <Label htmlFor="b-year" className="text-xs text-muted-foreground">{t('hr.year')}</Label>
          <Select value={String(year)} onValueChange={(v) => setYear(Number(v))}>
            <SelectTrigger id="b-year" className="w-28"><SelectValue /></SelectTrigger>
            <SelectContent>{[thisYear - 1, thisYear, thisYear + 1].map((y) => <SelectItem key={y} value={String(y)}>{y}</SelectItem>)}</SelectContent>
          </Select>
        </div>
        {userId && balances.data && balances.data.some((b) => b.allowance !== null) && (
          <Button size="sm" className="col-span-2 sm:col-span-1" onClick={() => setAdjusting(true)}><SlidersHorizontal className="size-4" />{t('hr.adjustBalance')}</Button>
        )}
      </div>
      {!userId ? <EmptyState icon={UserRound} title={t('hr.choosePersonTitle')} body={t('hr.choosePersonBody')} />
        : balances.error ? <ErrorState error={balances.error} retry={() => balances.refetch()} />
          : balances.isPending ? <ListSkeleton rows={2} /> : (
            <div className="space-y-4">
              <BalanceCards balances={balances.data} />
              <div>
                <h3 className="mb-2 text-sm font-semibold">{t('hr.adjustmentHistory')}</h3>
                {history.error ? <ErrorState error={history.error} retry={() => history.refetch()} /> : history.isPending ? <ListSkeleton rows={1} />
                  : history.data.length === 0 ? <p className="text-sm text-muted-foreground">{t('hr.noAdjustments', { year })}</p> : (
                    <ul className="divide-y rounded-lg border bg-card">
                      {history.data.map((a) => (
                        <li key={a.id} className="flex items-start gap-3 px-4 py-3 text-sm">
                          <PersonAvatar person={a.by} className="mt-0.5" />
                          <div className="min-w-0 flex-1">
                            <p><span className="font-medium tabular">{a.days > 0 ? `+${a.days}` : a.days}</span> {typeName(a.type)}</p>
                            <p className="break-words text-muted-foreground">{a.reason}</p>
                            <p className="text-xs text-muted-foreground">{a.by?.name} · {ago(a.at)}</p>
                          </div>
                        </li>
                      ))}
                    </ul>
                  )}
              </div>
            </div>
          )}
      {adjusting && person && balances.data && (
        <AdjustDialog org={org} userId={userId} name={person.person.name} year={year} balances={balances.data} onClose={() => setAdjusting(false)} />
      )}
    </section>
  );
}

/** HR managers: leave types and balance adjustments (`?tab=balances&person=<id>` opens a person's balances). */
function HrSettings() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const params = useSearchParams();
  const router = useRouter();
  if (!can('hr.manage')) return <PermissionDenied />;
  const tab = params.get('tab') === 'balances' || params.get('person') ? 'balances' : params.get('tab') === 'holidays' ? 'holidays' : 'types';
  return (
    <>
      <PageHeader title={t('hr.settingsTitle')} subtitle={t('hr.settingsSubtitle')} />
      <Tabs value={tab} onValueChange={(v) => router.replace(`/${slug}/hr/settings${v === 'types' ? '' : `?tab=${v}`}`, { scroll: false })}>
        <TabsList className="mb-4"><TabsTrigger value="types">{t('hr.leaveTypes')}</TabsTrigger><TabsTrigger value="balances">{t('hr.balances')}</TabsTrigger><TabsTrigger value="holidays">{t('hr.holidays')}</TabsTrigger></TabsList>
        <TabsContent value="types"><LeaveTypes org={slug} /></TabsContent>
        <TabsContent value="balances"><Balances org={slug} /></TabsContent>
        <TabsContent value="holidays"><Holidays org={slug} /></TabsContent>
      </Tabs>
    </>
  );
}

export default function Page() {
  return <Suspense><HrSettings /></Suspense>;
}
