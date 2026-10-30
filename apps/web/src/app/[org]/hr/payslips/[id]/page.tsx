'use client';

import { ArrowLeft, Printer } from 'lucide-react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import type { ReactNode } from 'react';

import { ErrorState, ListSkeleton } from '@/components/common/states';
import { duration } from '@/components/hr/attendance-ui';
import { monthText, riel } from '@/components/hr/payroll-ui';
import { Button } from '@/components/ui/button';
import { useT } from '@/i18n/provider';
import { usePayslip } from '@/lib/api/payroll';
import { day } from '@/lib/format';
import { usd } from '@/lib/money';
import { useOrg } from '@/lib/org-context';

const Line = ({ label, value, strong, muted }: { label: ReactNode; value: string; strong?: boolean; muted?: boolean }) => (
  <div className={`flex items-baseline justify-between gap-4 py-1.5 ${strong ? 'font-semibold' : ''} ${muted ? 'text-slate-500' : ''}`}>
    <dt className="min-w-0">{label}</dt><dd className="tabular whitespace-nowrap">{value}</dd>
  </div>
);

/** A payslip as a clean A4 page; "Print / Save as PDF" uses the browser. The person (once paid) or HR. */
export default function PayslipPage() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const { id } = useParams<{ id: string }>();
  const { data: s, error, isPending, refetch } = usePayslip(slug, id);
  const back = can('hr.payroll') && s ? `/${slug}/hr/payroll/${s.runId}` : `/${slug}/hr/payslips`;
  if (error) return <ErrorState error={error} retry={() => refetch()} />;
  if (isPending) return <ListSkeleton />;

  return (
    <div className="mx-auto max-w-[210mm]">
      <style>{'@page { size: A4; margin: 14mm; } @media print { html, body { background: #fff !important; } }'}</style>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2 print:hidden">
        <Button variant="ghost" size="sm" asChild><Link href={back}><ArrowLeft className="size-4" />{t('common.back')}</Link></Button>
        <Button size="sm" onClick={() => window.print()}><Printer className="size-4" />{t('hr.printSave')}</Button>
      </div>
      <article aria-label={t('hr.payslip')} className="rounded-lg border bg-white p-6 text-[13px] leading-relaxed text-slate-900 shadow-sm sm:p-10 print:rounded-none print:border-0 print:p-0 print:shadow-none">
        <header className="flex flex-wrap items-start justify-between gap-6 border-b border-slate-200 pb-6">
          <div>
            <p className="text-lg font-semibold">{s.orgName}</p>
            <p className="text-slate-600">{t('hr.payslipFor', { month: monthText(s.month) })}</p>
          </div>
          <div className="text-right">
            <p className="text-2xl font-semibold tracking-tight">{t('hr.payslip')}</p>
            {s.runStatus !== 'PAID' && <p className="font-medium text-amber-700">{t(`hr.run_${s.runStatus}`)}</p>}
            {s.paidAt && <p className="text-slate-600">{t('hr.paidOn', { date: day(s.paidAt) })}</p>}
          </div>
        </header>
        <section className="grid gap-4 border-b border-slate-200 py-5 sm:grid-cols-2">
          <div>
            <p className="font-semibold">{s.person.name}</p>
            <p className="text-slate-600">{[s.title, s.department].filter(Boolean).join(' · ')}</p>
          </div>
          <dl className="text-slate-600 sm:text-right">
            <div>{t('hr.workingDaysLine', { count: s.workingDays })}</div>
            <div>{t('hr.dependantsLine', { count: s.dependants })}</div>
            {(s.bankName || s.bankAccount) && <div>{[s.bankName, s.bankAccount].filter(Boolean).join(' · ')}</div>}
          </dl>
        </section>
        <div className="grid gap-8 py-5 sm:grid-cols-2">
          <section>
            <h2 className="mb-1 font-semibold">{t('hr.earnings')}</h2>
            <dl className="divide-y divide-slate-100">
              <Line label={t('hr.monthlySalary')} value={usd(s.monthlySalary)} />
              {s.unpaidLeaveDays > 0 && <Line label={t('hr.unpaidLeaveLine', { count: s.unpaidLeaveDays })} value={`−${usd(s.unpaidLeaveAmount)}`} />}
              {s.overtimeMinutes > 0 && <Line label={t('hr.overtimeLine', { time: duration(s.overtimeMinutes) })} value={usd(s.overtimeAmount)} />}
              {s.adjustments.filter((a) => a.amount > 0).map((a, i) => <Line key={i} label={a.label} value={usd(a.amount)} />)}
              <Line label={t('hr.grossPay')} value={usd(s.gross)} strong />
            </dl>
          </section>
          <section>
            <h2 className="mb-1 font-semibold">{t('hr.deductions')}</h2>
            <dl className="divide-y divide-slate-100">
              <Line label={<>{t('hr.taxOnSalary')} <span className="text-slate-500">({riel(s.taxKhr)})</span></>} value={usd(s.tax)} />
              <Line label={t('hr.nssfPension')} value={usd(s.nssfEmployee)} />
              {s.adjustments.filter((a) => a.amount < 0).map((a, i) => <Line key={i} label={a.label} value={usd(-a.amount)} />)}
            </dl>
          </section>
        </div>
        <div className="flex flex-wrap items-baseline justify-between gap-4 rounded-md bg-slate-50 px-4 py-3">
          <span className="text-base font-semibold">{t('hr.netPay')}</span>
          <span className="text-xl font-semibold tabular">{usd(s.net)}</span>
        </div>
        <footer className="mt-5 space-y-1 text-xs text-slate-500">
          <p>{t('hr.taxNote', { taxable: riel(s.taxableKhr), rate: riel(s.exchangeRate) })}</p>
          <p>{t('hr.employerNssfNote', { amount: usd(s.nssfEmployer) })}</p>
        </footer>
      </article>
    </div>
  );
}
