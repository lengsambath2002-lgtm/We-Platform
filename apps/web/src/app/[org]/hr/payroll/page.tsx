'use client';

import { Banknote, Pencil, Plus, Users } from 'lucide-react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { Suspense, useState } from 'react';

import { PageHeader } from '@/components/common/page-header';
import { PersonAvatar } from '@/components/common/person';
import { EmptyState, ErrorState, ListSkeleton, PermissionDenied } from '@/components/common/states';
import { NewRunDialog, PayDialog, PayrollSettingsForm, RunBadge, monthText } from '@/components/hr/payroll-ui';
import { Button } from '@/components/ui/button';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useT } from '@/i18n/provider';
import { type PersonPay, usePayPeople, usePayRuns, usePayrollSettings } from '@/lib/api/payroll';
import { usd } from '@/lib/money';
import { useOrg } from '@/lib/org-context';

function Runs({ org }: { org: string }) {
  const { t } = useT();
  const { data, error, isPending, refetch } = usePayRuns(org);
  const [creating, setCreating] = useState(false);
  return (
    <section aria-labelledby="runs-h">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <div><h2 id="runs-h" className="text-base font-semibold">{t('hr.payRuns')}</h2><p className="text-sm text-muted-foreground">{t('hr.payRunsBody')}</p></div>
        <Button size="sm" onClick={() => setCreating(true)}><Plus className="size-4" />{t('hr.newRun')}</Button>
      </div>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={3} />
        : data.length === 0 ? <EmptyState icon={Banknote} title={t('hr.noRuns')} body={t('hr.noRunsBody')} /> : (
          <div className="overflow-x-auto rounded-lg border bg-card">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('hr.payMonth')}</TableHead>
                  <TableHead>{t('hr.colStatus')}</TableHead>
                  <TableHead className="text-right">{t('hr.navPeople')}</TableHead>
                  <TableHead className="text-right">{t('hr.netPay')}</TableHead>
                  <TableHead className="text-right">{t('hr.employerCost')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.map((r) => (
                  <TableRow key={r.id}>
                    <TableCell><Link href={`/${org}/hr/payroll/${r.id}`} className="font-medium text-primary hover:underline">{monthText(r.month)}</Link></TableCell>
                    <TableCell><RunBadge status={r.status} /></TableCell>
                    <TableCell className="text-right tabular">{r.totals.people}</TableCell>
                    <TableCell className="text-right tabular">{usd(r.totals.net)}</TableCell>
                    <TableCell className="text-right tabular">{usd(r.totals.employerCost)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
      {creating && <NewRunDialog org={org} onClose={() => setCreating(false)} />}
    </section>
  );
}

function People({ org }: { org: string }) {
  const { t } = useT();
  const { data, error, isPending, refetch } = usePayPeople(org);
  const [editing, setEditing] = useState<PersonPay | null>(null);
  return (
    <section aria-labelledby="pp-h">
      <h2 id="pp-h" className="text-base font-semibold">{t('hr.payPeople')}</h2>
      <p className="mb-3 text-sm text-muted-foreground">{t('hr.payPeopleBody')}</p>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={4} />
        : data.length === 0 ? <EmptyState icon={Users} title={t('hr.nobodyToShow')} /> : (
          <div className="overflow-x-auto rounded-lg border bg-card">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('hr.person')}</TableHead>
                  <TableHead className="text-right">{t('hr.monthlySalary')}</TableHead>
                  <TableHead className="text-right">{t('hr.dependants')}</TableHead>
                  <TableHead>{t('hr.nssf')}</TableHead>
                  <TableHead>{t('hr.bankAccount')}</TableHead>
                  <TableHead className="w-12"><span className="sr-only">{t('common.edit')}</span></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.map((r) => (
                  <TableRow key={r.person.id}>
                    <TableCell className="min-w-48">
                      <div className="flex items-center gap-3"><PersonAvatar person={r.person} className="size-7" />
                        <span className="min-w-0"><span className="block truncate text-sm font-medium">{r.person.name}</span>{r.department && <span className="block truncate text-xs text-muted-foreground">{r.department}</span>}</span></div>
                    </TableCell>
                    <TableCell className="text-right tabular">{r.monthlySalary === null ? <span className="text-muted-foreground">{t('hr.notSetUp')}</span> : usd(r.monthlySalary)}</TableCell>
                    <TableCell className="text-right tabular">{r.monthlySalary === null ? '' : r.dependants}</TableCell>
                    <TableCell className="text-sm">{r.monthlySalary === null ? '' : t(r.nssfEnrolled ? 'hr.yes' : 'hr.no')}</TableCell>
                    <TableCell className="text-sm text-muted-foreground">{[r.bankName, r.bankAccount].filter(Boolean).join(' · ')}</TableCell>
                    <TableCell><Button variant="ghost" size="icon" className="size-8" aria-label={t('hr.payOf', { name: r.person.name })} onClick={() => setEditing(r)}><Pencil className="size-4" /></Button></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
      {editing && <PayDialog org={org} row={editing} onClose={() => setEditing(null)} />}
    </section>
  );
}

function Settings({ org }: { org: string }) {
  const { t } = useT();
  const { data, error, isPending, refetch } = usePayrollSettings(org);
  return (
    <section aria-labelledby="pset-h">
      <h2 id="pset-h" className="mb-3 text-base font-semibold">{t('hr.payrollSettings')}</h2>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={4} /> : <PayrollSettingsForm org={org} settings={data} />}
    </section>
  );
}

/** HR managers: monthly pay runs, what each person is paid, and the statutory settings (`?tab=people|settings`). */
function Payroll() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const params = useSearchParams();
  const router = useRouter();
  if (!can('hr.manage')) return <PermissionDenied />;
  const tab = params.get('tab') === 'people' ? 'people' : params.get('tab') === 'settings' ? 'settings' : 'runs';
  return (
    <>
      <PageHeader title={t('hr.payrollTitle')} subtitle={t('hr.payrollSubtitle')} />
      <Tabs value={tab} onValueChange={(v) => router.replace(`/${slug}/hr/payroll${v === 'runs' ? '' : `?tab=${v}`}`, { scroll: false })}>
        <TabsList className="mb-4"><TabsTrigger value="runs">{t('hr.payRuns')}</TabsTrigger><TabsTrigger value="people">{t('hr.payPeople')}</TabsTrigger><TabsTrigger value="settings">{t('hr.payrollSettings')}</TabsTrigger></TabsList>
        <TabsContent value="runs"><Runs org={slug} /></TabsContent>
        <TabsContent value="people"><People org={slug} /></TabsContent>
        <TabsContent value="settings"><Settings org={slug} /></TabsContent>
      </Tabs>
    </>
  );
}

export default function Page() {
  return <Suspense><Payroll /></Suspense>;
}
