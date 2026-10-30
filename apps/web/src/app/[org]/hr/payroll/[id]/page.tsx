'use client';

import { ArrowLeft, CheckCircle2, Download, ListPlus, RefreshCw, RotateCcw, Trash2, Wallet } from 'lucide-react';
import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useState } from 'react';
import { toast } from 'sonner';

import { PersonAvatar } from '@/components/common/person';
import { ErrorState, ListSkeleton, PermissionDenied } from '@/components/common/states';
import { AdjustmentsDialog, RunBadge, monthText, riel } from '@/components/hr/payroll-ui';
import { Button } from '@/components/ui/button';
import { Table, TableBody, TableCell, TableFooter, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { type Payslip, payrollApi, usePayRun } from '@/lib/api/payroll';
import { useOrgMutation } from '@/lib/api/queries';
import { ago } from '@/lib/format';
import { usd } from '@/lib/money';
import { useOrg } from '@/lib/org-context';

/** One month's pay run: totals, the payslips, and the next step (recalculate, approve, reopen, mark paid). */
export default function PayRunPage() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const router = useRouter();
  const { id } = useParams<{ id: string }>();
  const { data, error, isPending, refetch } = usePayRun(slug, id);
  const act = useOrgMutation(slug, (a: 'recalculate' | 'approve' | 'reopen' | 'pay') => payrollApi.act(slug, id, a));
  const remove = useOrgMutation(slug, () => payrollApi.deleteRun(slug, id));
  const [adjusting, setAdjusting] = useState<Payslip | null>(null);
  if (!can('hr.payroll')) return <PermissionDenied />;
  const back = <Link href={`/${slug}/hr/payroll`} className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="size-4" />{t('hr.payrollTitle')}</Link>;
  if (error) return <>{back}<ErrorState error={error} retry={() => refetch()} /></>;
  if (isPending) return <>{back}<ListSkeleton rows={6} /></>;
  const { run, payslips, notSetUp } = data;
  const draft = run.status === 'DRAFT';
  const go = async (a: 'recalculate' | 'approve' | 'reopen' | 'pay', done: string, confirm?: string) => {
    if (confirm && !window.confirm(confirm)) return;
    try { await act.mutateAsync(a); toast.success(done); } catch (e) { toast.error(errorMessage(e)); }
  };

  return (
    <>
      {back}
      <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2"><h1 className="text-xl font-semibold tracking-tight">{t('hr.payrollFor', { month: monthText(run.month) })}</h1><RunBadge status={run.status} /></div>
          <p className="mt-1 text-sm text-muted-foreground">
            {t('hr.rateLine', { rate: riel(run.exchangeRate) })}
            {run.approvedBy && run.approvedAt && <> · {t('hr.approvedByLine', { name: run.approvedBy.name, when: ago(run.approvedAt) })}</>}
            {run.paidBy && run.paidAt && <> · {t('hr.paidByLine', { name: run.paidBy.name, when: ago(run.paidAt) })}</>}
          </p>
          {run.note && <p className="mt-1 text-sm">{run.note}</p>}
        </div>
        <div className="flex flex-wrap gap-2">
          {draft && <Button variant="outline" size="sm" disabled={act.isPending} onClick={() => go('recalculate', t('hr.recalculated'))}><RefreshCw className="size-4" />{t('hr.recalculate')}</Button>}
          {draft && <Button size="sm" disabled={act.isPending || payslips.length === 0} onClick={() => go('approve', t('hr.runApproved'), t('hr.approveConfirm'))}><CheckCircle2 className="size-4" />{t('hr.approveRun')}</Button>}
          {run.status === 'APPROVED' && <Button variant="outline" size="sm" disabled={act.isPending} onClick={() => go('reopen', t('hr.runReopened'))}><RotateCcw className="size-4" />{t('hr.reopenRun')}</Button>}
          {run.status === 'APPROVED' && <Button size="sm" disabled={act.isPending} onClick={() => go('pay', t('hr.runPaid'), t('hr.payConfirm', { count: payslips.length }))}><Wallet className="size-4" />{t('hr.markPaid')}</Button>}
          {run.status !== 'DRAFT' && <Button variant="outline" size="sm" onClick={() => payrollApi.downloadCsv(slug, id, `payroll-${run.month}.csv`).catch((e) => toast.error(errorMessage(e)))}><Download className="size-4" />{t('hr.bankList')}</Button>}
          {draft && <Button variant="ghost" size="sm" disabled={remove.isPending} onClick={async () => {
            if (!window.confirm(t('hr.deleteRunConfirm'))) return;
            try { await remove.mutateAsync(undefined); toast.success(t('hr.runDeleted')); router.replace(`/${slug}/hr/payroll`); } catch (e) { toast.error(errorMessage(e)); }
          }}><Trash2 className="size-4" />{t('common.delete')}</Button>}
        </div>
      </div>

      <div className="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
        {[[t('hr.grossPay'), run.totals.gross], [t('hr.taxOnSalary'), run.totals.tax], [t('hr.netPay'), run.totals.net], [t('hr.employerCost'), run.totals.employerCost]].map(([label, v]) => (
          <div key={label as string} className="rounded-lg border bg-card p-3"><p className="text-xs text-muted-foreground">{label}</p><p className="mt-0.5 text-lg font-semibold tabular">{usd(v as number)}</p></div>
        ))}
      </div>

      {draft && notSetUp.length > 0 && (
        <p role="status" className="mb-4 rounded-md border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-900 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-200">
          {t('hr.notSetUpLine', { names: notSetUp.map((p) => p.name).join(', ') })} <Link href={`/${slug}/hr/payroll?tab=people`} className="font-medium underline">{t('hr.setUpPay')}</Link>
        </p>
      )}

      <div className="overflow-x-auto rounded-lg border bg-card">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t('hr.person')}</TableHead>
              <TableHead className="text-right">{t('hr.grossPay')}</TableHead>
              <TableHead className="text-right">{t('hr.nssf')}</TableHead>
              <TableHead className="text-right">{t('hr.taxOnSalary')}</TableHead>
              <TableHead className="text-right">{t('hr.netPay')}</TableHead>
              <TableHead className="w-28"><span className="sr-only">{t('hr.payslip')}</span></TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {payslips.map((s) => (
              <TableRow key={s.id}>
                <TableCell className="min-w-48">
                  <div className="flex items-center gap-3"><PersonAvatar person={s.person} className="size-7" />
                    <span className="min-w-0">
                      <Link href={`/${slug}/hr/payslips/${s.id}`} className="block truncate text-sm font-medium text-primary hover:underline">{s.person.name}</Link>
                      <span className="block truncate text-xs text-muted-foreground">
                        {[s.unpaidLeaveDays > 0 && t('hr.unpaidDaysShort', { count: s.unpaidLeaveDays }), s.overtimeMinutes > 0 && t('hr.overtimeShort', { amount: usd(s.overtimeAmount) }),
                          s.adjustments.length > 0 && t('hr.adjustmentsShort', { count: s.adjustments.length })].filter(Boolean).join(' · ')}
                      </span>
                    </span>
                  </div>
                </TableCell>
                <TableCell className="text-right tabular">{usd(s.gross)}</TableCell>
                <TableCell className="text-right tabular">{usd(s.nssfEmployee)}</TableCell>
                <TableCell className="text-right tabular" title={riel(s.taxKhr)}>{usd(s.tax)}</TableCell>
                <TableCell className="text-right font-medium tabular">{usd(s.net)}</TableCell>
                <TableCell className="text-right">{draft && <Button variant="ghost" size="sm" onClick={() => setAdjusting(s)}><ListPlus className="size-4" />{t('hr.adjust')}</Button>}</TableCell>
              </TableRow>
            ))}
          </TableBody>
          <TableFooter>
            <TableRow>
              <TableCell className="font-medium">{t('hr.totalPeople', { count: run.totals.people })}</TableCell>
              <TableCell className="text-right tabular">{usd(run.totals.gross)}</TableCell>
              <TableCell className="text-right tabular">{usd(run.totals.nssfEmployee)}</TableCell>
              <TableCell className="text-right tabular">{usd(run.totals.tax)}</TableCell>
              <TableCell className="text-right font-semibold tabular">{usd(run.totals.net)}</TableCell>
              <TableCell />
            </TableRow>
          </TableFooter>
        </Table>
      </div>
      {adjusting && <AdjustmentsDialog org={slug} slip={adjusting} onClose={() => setAdjusting(null)} />}
    </>
  );
}
