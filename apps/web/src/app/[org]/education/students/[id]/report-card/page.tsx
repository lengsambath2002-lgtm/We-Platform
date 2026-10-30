'use client';

import { ArrowLeft, Printer } from 'lucide-react';
import Link from 'next/link';
import { useParams, useSearchParams } from 'next/navigation';
import { Suspense } from 'react';

import { GENDER_LABEL, num, pct, subjectName } from '@/components/education/ui';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { FileText } from 'lucide-react';
import { useT } from '@/i18n/provider';
import { useReportCard } from '@/lib/api/education';
import { day } from '@/lib/format';
import { useOrg } from '@/lib/org-context';

/**
 * A student's report card for a term on a clean A4 page: the school, the student and class, each subject's result
 * and letter with its teacher, the average, rank and pass / fail, attendance in the term and signature lines.
 */
function ReportCardPage() {
  const { slug } = useOrg();
  const { t, locale } = useT();
  const { id } = useParams<{ id: string }>();
  const termId = useSearchParams().get('term') ?? '';
  const { data, error, isPending, refetch } = useReportCard(slug, id, termId);
  const caps = locale === 'km' ? 'text-xs font-semibold text-slate-500' : 'text-xs font-semibold uppercase tracking-wider text-slate-500';
  const back = `/${slug}/education/students/${id}`;
  if (!termId) return <EmptyState icon={FileText} title={t('education.chooseTerm')} action={<Button asChild size="sm" variant="outline"><Link href={back}>{t('education.backToStudent')}</Link></Button>} />;
  if (error) return <ErrorState error={error} retry={() => refetch()} />;
  if (isPending) return <ListSkeleton />;
  const d = data;
  const s = d.student;

  return (
    <div className="mx-auto max-w-[210mm]">
      <style>{'@page { size: A4; margin: 14mm; } @media print { html, body { background: #fff !important; } }'}</style>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2 print:hidden">
        <Button variant="ghost" size="sm" asChild><Link href={back}><ArrowLeft className="size-4" />{t('education.backToStudent')}</Link></Button>
        <Button size="sm" onClick={() => window.print()}><Printer className="size-4" />{t('education.printSave')}</Button>
      </div>
      <article aria-label={t('education.reportCardLabel')} className="rounded-lg border bg-white p-6 text-[13px] leading-relaxed text-slate-900 shadow-sm sm:p-10 print:rounded-none print:border-0 print:p-0 print:shadow-none">
        <header className="border-b border-slate-200 pb-5 text-center">
          <p className="text-lg font-semibold">{d.school.schoolName ?? d.school.orgName}</p>
          {d.school.address && <p className="text-slate-600">{d.school.address}</p>}
          {d.school.phone && <p className="text-slate-600">{d.school.phone}</p>}
          <p className="mt-3 text-xl font-semibold tracking-tight">{t('education.reportCardHeading')}</p>
          <p className="text-slate-600">{d.yearName} · {d.term.name}</p>
        </header>
        <dl className="grid grid-cols-2 gap-x-6 gap-y-2 border-b border-slate-200 py-5 sm:grid-cols-3">
          <div><dt className={caps}>{t('education.student')}</dt><dd className="font-medium">{s.nameEn}{s.nameKm && <span lang="km" className="block font-normal">{s.nameKm}</span>}</dd></div>
          <div><dt className={caps}>{t('education.number')}</dt><dd>{s.number}</dd></div>
          <div><dt className={caps}>{t('education.gender')}</dt><dd>{GENDER_LABEL[s.gender]}</dd></div>
          <div><dt className={caps}>{t('education.dateOfBirth')}</dt><dd>{day(s.dateOfBirth)}</dd></div>
          <div><dt className={caps}>{t('education.class')}</dt><dd>{d.classRef.name}{d.gradeName ? ` · ${d.gradeName}` : ''}</dd></div>
          <div><dt className={caps}>{t('education.homeroomTeacher')}</dt><dd>{d.homeroom ?? '—'}</dd></div>
        </dl>
        <table className="mt-5 w-full border-collapse text-left">
          <thead>
            <tr className="border-b border-slate-300">
              <th className={`py-2 ${caps}`}>{t('education.subject')}</th>
              <th className={`py-2 ${caps}`}>{t('education.teacher')}</th>
              <th className={`py-2 text-right ${caps}`}>{t('education.result')}</th>
              <th className={`py-2 text-right ${caps}`}>{t('education.letter')}</th>
            </tr>
          </thead>
          <tbody>
            {d.lines.map((l) => (
              <tr key={l.subject.id} className="border-b border-slate-100">
                <td className="py-2">{subjectName(l.subject)}</td>
                <td className="py-2 text-slate-600">{l.teacher ?? '—'}</td>
                <td className="py-2 text-right tabular">{pct(l.result)}</td>
                <td className="py-2 text-right font-medium">{l.letter ?? '—'}</td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr className="border-t-2 border-slate-300 font-semibold">
              <td className="py-2" colSpan={2}>{t('education.termAverage')}</td>
              <td className="py-2 text-right tabular">{pct(d.average)}</td>
              <td className="py-2 text-right">{d.letter ?? '—'}</td>
            </tr>
          </tfoot>
        </table>
        <dl className="mt-5 grid grid-cols-2 gap-x-6 gap-y-2 sm:grid-cols-4">
          <div><dt className={caps}>{t('education.rank')}</dt><dd className="font-medium">{d.rank ? t('education.rankOf', { rank: d.rank, size: d.classSize }) : '—'}</dd></div>
          <div><dt className={caps}>{t('education.outcome')}</dt><dd className="font-medium">{d.passed === null ? '—' : d.passed ? t('education.passed') : t('education.notPassed')}</dd></div>
          <div><dt className={caps}>{t('education.passMark')}</dt><dd>{num(d.school.passMark)}%</dd></div>
          <div><dt className={caps}>{t('education.attendanceRate')}</dt><dd>{pct(d.attendance.rate)}</dd></div>
        </dl>
        <p className="mt-2 text-slate-600">{t('education.attendanceLine', { present: d.attendance.present, late: d.attendance.late, absent: d.attendance.absent, excused: d.attendance.excused })}</p>
        <p className="mt-4 text-xs text-slate-500">{t('education.scaleNote')}</p>
        <div className="mt-12 grid grid-cols-2 gap-10 text-center text-slate-600">
          <div><div className="border-t border-slate-400 pt-2">{t('education.homeroomTeacher')}{d.homeroom ? ` · ${d.homeroom}` : ''}</div></div>
          <div><div className="border-t border-slate-400 pt-2">{t('education.principal')}{d.school.principalName ? ` · ${d.school.principalName}` : ''}</div></div>
        </div>
      </article>
    </div>
  );
}

export default function ReportCardRoute() {
  return <Suspense fallback={<ListSkeleton />}><ReportCardPage /></Suspense>;
}
