'use client';

import { FileText, GraduationCap, Pencil, Phone, Plus, Receipt, UserMinus, X } from 'lucide-react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useState } from 'react';
import { toast } from 'sonner';

import { RecordApprovals } from '@/components/approvals/record-approvals';
import { InvoiceDialog } from '@/components/education/fee-dialogs';
import { GraduateDialog, GuardianLinkDialog, StudentDialog, WithdrawalDialog } from '@/components/education/people-dialogs';
import {
  Crumb, FeeBadge, Field, GENDER_LABEL, Money, Panel, pct, RELATIONSHIP_LABEL, StudentBadge, studentName, WithdrawalBadge,
} from '@/components/education/ui';
import { ErrorState, ListSkeleton } from '@/components/common/states';
import { ActivityTimeline } from '@/components/tasks/activity';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { eduApi, useStudent, useStudentHistory, useYears } from '@/lib/api/education';
import { useOrgMutation } from '@/lib/api/queries';
import { day } from '@/lib/format';
import { useOrg } from '@/lib/org-context';

type Open = 'edit' | 'guardian' | 'withdraw' | 'graduate' | 'invoice' | null;

/** A student: details, parents and guardians, class history, attendance this year, fees (admins), withdrawal and history. */
export default function StudentPage() {
  const { slug } = useOrg();
  const { t } = useT();
  const { id } = useParams<{ id: string }>();
  const { data, error, isPending, refetch } = useStudent(slug, id);
  const history = useStudentHistory(slug, id);
  const years = useYears(slug);
  const [open, setOpen] = useState<Open>(null);
  const [term, setTerm] = useState('');
  const unlink = useOrgMutation(slug, (guardianId: string) => eduApi.unlinkGuardian(slug, id, guardianId));
  const base = `/${slug}/education`;

  if (error) return <ErrorState error={error} retry={() => refetch()} />;
  if (isPending) return <ListSkeleton />;
  const s = data.student;
  const name = studentName(s);
  const terms = (years.data ?? []).flatMap((y) => y.terms.map((x) => ({ ...x, label: `${y.name} · ${x.name}` })));
  const pending = data.withdrawals.find((w) => w.status === 'PENDING');

  return (
    <>
      <Crumb href={`${base}/students`} label={t('education.studentsTitle')} current={name} />
      <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h1 className="flex flex-wrap items-center gap-2 text-xl font-semibold tracking-tight">{name} <StudentBadge status={s.status} /></h1>
          <p className="mt-1 text-sm text-muted-foreground">{[s.number, data.currentClass && t('education.inClass', { name: data.currentClass.name }), data.gradeName].filter(Boolean).join(' · ')}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          {data.canEdit && <Button variant="outline" size="sm" onClick={() => setOpen('edit')}><Pencil className="size-4" />{t('education.edit')}</Button>}
          {data.canRequestWithdrawal && <Button variant="outline" size="sm" onClick={() => setOpen('withdraw')}><UserMinus className="size-4" />{t('education.requestWithdrawal')}</Button>}
          {data.canGraduate && <Button variant="outline" size="sm" onClick={() => setOpen('graduate')}><GraduationCap className="size-4" />{t('education.markGraduated')}</Button>}
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
        <div className="min-w-0 space-y-8">
          <Panel id="det-h" title={t('education.studentDetails')}>
            <dl className="grid gap-4 rounded-lg border bg-card p-4 sm:grid-cols-2">
              <Field label={t('education.studentNameEn')}>{s.nameEn}</Field>
              <Field label={t('education.studentNameKm')}><span lang="km">{s.nameKm ?? '—'}</span></Field>
              <Field label={t('education.gender')}>{GENDER_LABEL[s.gender]}</Field>
              <Field label={t('education.dateOfBirth')}>{day(s.dateOfBirth)}</Field>
              <Field label={t('education.admittedOn')}>{day(s.admittedOn)}</Field>
              {s.leftOn && <Field label={s.status === 'GRADUATED' ? t('education.graduatedOn') : t('education.leftOn')}>{day(s.leftOn)}</Field>}
              <Field label={t('education.address')} wide>{s.address ?? '—'}</Field>
              {s.medicalNotes && <Field label={t('education.medicalNotes')} wide>{s.medicalNotes}</Field>}
              {s.leaveReason && <Field label={t('education.reason')} wide>{s.leaveReason}</Field>}
            </dl>
          </Panel>

          <Panel id="gd-h" title={t('education.parentsGuardians')}
            action={data.canEdit && <Button variant="outline" size="sm" onClick={() => setOpen('guardian')}><Plus className="size-4" />{t('education.addGuardian')}</Button>}>
            <ul className="divide-y rounded-lg border bg-card">
              {data.guardians.map((g) => (
                <li key={g.id} className="flex flex-wrap items-start justify-between gap-3 px-4 py-3">
                  <div className="min-w-0">
                    <p className="font-medium">{g.name} <span className="text-sm font-normal text-muted-foreground">· {RELATIONSHIP_LABEL[g.relationship]}{g.primary ? ` · ${t('education.mainContact')}` : ''}</span></p>
                    <p className="text-sm text-muted-foreground">{[g.phone, g.email, g.occupation].filter(Boolean).join(' · ')}</p>
                  </div>
                  <div className="flex gap-1">
                    {g.phone && <Button asChild variant="ghost" size="icon-sm" aria-label={t('education.callName', { name: g.name })}><a href={`tel:${g.phone.replace(/\s/g, '')}`}><Phone className="size-4" /></a></Button>}
                    {data.canEdit && data.guardians.length > 1 && (
                      <Button variant="ghost" size="icon-sm" aria-label={t('education.removeGuardian', { name: g.name })}
                        onClick={async () => { try { await unlink.mutateAsync(g.id); toast.success(t('education.guardianRemoved')); } catch (e) { toast.error(errorMessage(e)); } }}>
                        <X className="size-4" />
                      </Button>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          </Panel>

          {data.invoices && (
            <Panel id="fee-h" title={t('education.navFees')}
              action={s.status === 'ACTIVE' && <Button variant="outline" size="sm" onClick={() => setOpen('invoice')}><Receipt className="size-4" />{t('education.newInvoice')}</Button>}>
              {data.invoices.length === 0 ? <p className="rounded-lg border border-dashed px-4 py-6 text-center text-sm text-muted-foreground">{t('education.noInvoicesStudent')}</p> : (
                <ul className="divide-y rounded-lg border bg-card">
                  {data.invoices.map((i) => (
                    <li key={i.id}>
                      <Link href={`${base}/fees/invoices/${i.id}`} className="flex items-center justify-between gap-3 px-4 py-3 hover:bg-muted/50">
                        <span className="min-w-0"><span className="block font-medium">{i.number} · {i.description}</span>
                          <span className="block text-xs text-muted-foreground">{t('education.dueOnDate', { date: day(i.dueOn) })}</span></span>
                        <span className="flex shrink-0 flex-col items-end gap-1"><Money value={i.amount} inline className="text-sm" /><FeeBadge status={i.status} overdue={i.overdue} /></span>
                      </Link>
                    </li>
                  ))}
                </ul>
              )}
            </Panel>
          )}

          <Panel id="hist-h" title={t('education.history')}>
            {history.error ? <ErrorState error={history.error} retry={() => history.refetch()} /> : history.isPending ? <ListSkeleton rows={2} />
              : <ActivityTimeline items={history.data} />}
          </Panel>
        </div>

        <aside className="space-y-6">
          <section aria-labelledby="att-h" className="rounded-lg border bg-card p-4">
            <h2 id="att-h" className="text-sm font-semibold">{t('education.attendanceThisYear')}</h2>
            <p className="mt-2 text-2xl font-semibold tabular">{pct(data.attendance.rate)}</p>
            <dl className="mt-2 grid grid-cols-2 gap-x-3 gap-y-1 text-sm">
              <dt className="text-muted-foreground">{t('education.mark_PRESENT')}</dt><dd className="text-right tabular">{data.attendance.present}</dd>
              <dt className="text-muted-foreground">{t('education.mark_LATE')}</dt><dd className="text-right tabular">{data.attendance.late}</dd>
              <dt className="text-muted-foreground">{t('education.mark_ABSENT')}</dt><dd className="text-right tabular">{data.attendance.absent}</dd>
              <dt className="text-muted-foreground">{t('education.mark_EXCUSED')}</dt><dd className="text-right tabular">{data.attendance.excused}</dd>
            </dl>
          </section>
          {data.balance !== null && (
            <section aria-labelledby="bal-h" className="rounded-lg border bg-card p-4">
              <h2 id="bal-h" className="text-sm font-semibold">{t('education.owes')}</h2>
              <Money value={data.balance} className="mt-2 text-2xl" strong />
            </section>
          )}
          <section aria-labelledby="rc-h" className="space-y-2 rounded-lg border bg-card p-4">
            <h2 id="rc-h" className="text-sm font-semibold">{t('education.reportCard')}</h2>
            <Select value={term} onValueChange={setTerm}>
              <SelectTrigger className="w-full" aria-label={t('education.term')}><SelectValue placeholder={t('education.chooseTerm')} /></SelectTrigger>
              <SelectContent>{terms.map((x) => <SelectItem key={x.id} value={x.id}>{x.label}</SelectItem>)}</SelectContent>
            </Select>
            <Button asChild size="sm" variant="outline" className="w-full" disabled={!term}>
              <Link href={term ? `${base}/students/${id}/report-card?term=${term}` : '#'} aria-disabled={!term}><FileText className="size-4" />{t('education.openReportCard')}</Link>
            </Button>
          </section>
          <section aria-labelledby="cls-h" className="rounded-lg border bg-card p-4">
            <h2 id="cls-h" className="text-sm font-semibold">{t('education.classes')}</h2>
            {data.enrollments.length === 0 ? <p className="mt-2 text-sm text-muted-foreground">{t('education.noClassYet')}</p> : (
              <ul className="mt-2 space-y-2 text-sm">
                {data.enrollments.map((e) => (
                  <li key={`${e.classRef?.id}`} className="flex justify-between gap-2">
                    <span>{e.classRef ? <Link href={`${base}/classes/${e.classRef.id}`} className="text-primary hover:underline">{e.classRef.name}</Link> : '—'} <span className="text-muted-foreground">· {e.yearName}</span></span>
                    {e.leftOn && <span className="text-xs text-muted-foreground">{t('education.leftOnDate', { date: day(e.leftOn) })}</span>}
                  </li>
                ))}
              </ul>
            )}
          </section>
          {data.withdrawals.length > 0 && (
            <section aria-labelledby="wd-h" className="space-y-3 rounded-lg border bg-card p-4">
              <h2 id="wd-h" className="text-sm font-semibold">{t('education.withdrawal')}</h2>
              <ul className="space-y-2 text-sm">
                {data.withdrawals.map((w) => (
                  <li key={w.id} className="space-y-1">
                    <div className="flex items-center justify-between gap-2"><span>{t('education.leavesOnDate', { date: day(w.effectiveOn) })}</span><WithdrawalBadge w={w} /></div>
                    <p className="text-muted-foreground">{w.reason}</p>
                  </li>
                ))}
              </ul>
            </section>
          )}
          {pending && <RecordApprovals org={slug} recordId={pending.id} canRequest={false} moduleKey="education" recordType="edu_withdrawal" />}
        </aside>
      </div>

      {open === 'edit' && <StudentDialog org={slug} student={s} onClose={() => setOpen(null)} />}
      {open === 'guardian' && <GuardianLinkDialog org={slug} studentId={id} onClose={() => setOpen(null)} />}
      {open === 'withdraw' && <WithdrawalDialog org={slug} studentId={id} name={name} onClose={() => setOpen(null)} />}
      {open === 'graduate' && <GraduateDialog org={slug} studentId={id} name={name} onClose={() => setOpen(null)} />}
      {open === 'invoice' && <InvoiceDialog org={slug} studentId={id} onClose={() => setOpen(null)} />}
    </>
  );
}
