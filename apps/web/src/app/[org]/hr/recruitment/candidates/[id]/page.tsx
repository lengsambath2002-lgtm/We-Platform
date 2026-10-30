'use client';

import { ArrowLeft, CalendarPlus, Download, Pencil, Plus, Trash2, Upload, UserCheck, X } from 'lucide-react';
import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useRef, useState } from 'react';
import { toast } from 'sonner';

import { PersonAvatar } from '@/components/common/person';
import { ErrorState, ListSkeleton } from '@/components/common/states';
import {
  CandidateDialog, FeedbackForm, HireDialog, InterviewDialog, Rating, RejectDialog, StageBadge, orgDateTime,
} from '@/components/hr/recruitment-ui';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { type Stage, recruitmentApi, useCandidate } from '@/lib/api/recruitment';
import { useOrgMutation } from '@/lib/api/queries';
import { useSession } from '@/lib/auth/session';
import { bytes, day } from '@/lib/format';
import { useOrg } from '@/lib/org-context';

const MOVABLE: Stage[] = ['NEW', 'SCREENING', 'INTERVIEW', 'OFFER'];

/** A candidate: details and CV, the stage, interviews with feedback, hiring, and (once hired) onboarding. */
export default function CandidatePage() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const router = useRouter();
  const { id } = useParams<{ id: string }>();
  const { user } = useSession();
  const { data: d, error, isPending, refetch } = useCandidate(slug, id);
  const [dialog, setDialog] = useState<'edit' | 'interview' | 'reject' | 'hire' | null>(null);
  const [newStep, setNewStep] = useState('');
  const fileInput = useRef<HTMLInputElement>(null);
  const run = useOrgMutation(slug, (fn: () => Promise<unknown>) => fn());
  const act = async (fn: () => Promise<unknown>, done?: string) => {
    try { await run.mutateAsync(fn); if (done) toast.success(done); } catch (e) { toast.error(errorMessage(e)); }
  };
  if (error) return <ErrorState error={error} retry={() => refetch()} />;
  if (isPending) return <ListSkeleton rows={6} />;
  const c = d.candidate;
  const open = c.stage !== 'HIRED' && c.stage !== 'REJECTED';

  return (
    <>
      <Link href={`/${slug}/hr/recruitment/jobs/${d.job.id}`} className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="size-4" />{d.job.title}</Link>
      <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2"><h1 className="text-xl font-semibold tracking-tight">{c.name}</h1><StageBadge stage={c.stage} /><Rating value={c.rating} /></div>
          <p className="mt-1 text-sm text-muted-foreground">{[c.email, c.phone, t(`hr.source_${c.source}`)].filter(Boolean).join(' · ')}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          {d.canManage && <Button variant="outline" size="sm" onClick={() => setDialog('edit')}><Pencil className="size-4" />{t('common.edit')}</Button>}
          {d.canManage && open && <Button variant="outline" size="sm" onClick={() => setDialog('interview')}><CalendarPlus className="size-4" />{t('hr.scheduleInterview')}</Button>}
          {d.canManage && open && <Button variant="outline" size="sm" onClick={() => setDialog('reject')}><X className="size-4" />{t('hr.reject')}</Button>}
          {d.canHire && <Button size="sm" onClick={() => setDialog('hire')}><UserCheck className="size-4" />{t('hr.hire')}</Button>}
          {can('hr.recruit.manage') && c.stage !== 'HIRED' && <Button variant="ghost" size="sm" aria-label={t('hr.removeCandidate')} onClick={() => {
            if (!window.confirm(t('hr.removeCandidateConfirm', { name: c.name }))) return;
            act(() => recruitmentApi.deleteCandidate(slug, c.id), t('hr.candidateRemoved')).then(() => router.replace(`/${slug}/hr/recruitment/jobs/${d.job.id}`));
          }}><Trash2 className="size-4" /></Button>}
        </div>
      </div>

      {c.stage === 'REJECTED' && d.rejectedReason && <p role="status" className="mb-4 rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{t('hr.rejectedBecause', { reason: d.rejectedReason })}</p>}
      {c.stage === 'HIRED' && (
        <p role="status" className="mb-4 rounded-md border border-emerald-300 bg-emerald-50 px-3 py-2 text-sm text-emerald-900 dark:border-emerald-800 dark:bg-emerald-950 dark:text-emerald-200">
          {t('hr.hiredLine', { date: d.startDate ? day(d.startDate) : '—' })} {d.invited && t(d.joined ? 'hr.hasJoined' : 'hr.invitedNotJoined')}
        </p>
      )}

      <div className="grid gap-4 lg:grid-cols-3">
        <div className="space-y-4 lg:col-span-2">
          {d.canManage && open && (
            <section className="flex flex-wrap items-center gap-3 rounded-lg border bg-card p-4">
              <span className="text-sm font-medium">{t('hr.moveTo')}</span>
              <Select value={MOVABLE.includes(c.stage) ? c.stage : 'NEW'} onValueChange={(v) => act(() => recruitmentApi.move(slug, c.id, v as Stage), t('hr.stageChanged'))}>
                <SelectTrigger className="w-44" aria-label={t('hr.moveTo')}><SelectValue /></SelectTrigger>
                <SelectContent>{MOVABLE.map((s) => <SelectItem key={s} value={s}>{t(`hr.stage_${s}`)}</SelectItem>)}</SelectContent>
              </Select>
            </section>
          )}

          <section aria-labelledby="iv-h" className="rounded-lg border bg-card p-4">
            <h2 id="iv-h" className="mb-3 text-base font-semibold">{t('hr.interviews')}</h2>
            {d.interviews.length === 0 ? <p className="text-sm text-muted-foreground">{t('hr.noInterviewsYet')}</p> : (
              <ul className="space-y-4">
                {d.interviews.map((i) => {
                  const mine = i.panel.find((p) => p.person.id === user?.id);
                  return (
                    <li key={i.id} className="space-y-2">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <p className="text-sm font-medium">{orgDateTime(i.startsAt)} · {t('hr.mOnly', { m: i.durationMinutes })}{i.location ? ` · ${i.location}` : ''}</p>
                        {d.canManage && open && <Button variant="ghost" size="sm" onClick={() => window.confirm(t('hr.cancelInterviewConfirm')) && act(() => recruitmentApi.cancelInterview(slug, i.id), t('hr.interviewCancelled'))}>{t('hr.cancelInterview')}</Button>}
                      </div>
                      <ul className="space-y-2">
                        {i.panel.map((p) => (
                          <li key={p.person.id} className="flex items-start gap-2 text-sm">
                            <PersonAvatar person={p.person} className="mt-0.5 size-6" />
                            <div className="min-w-0 flex-1">
                              <p><span className="font-medium">{p.person.name}</span>{' · '}
                                {p.recommendation ? <span className="font-medium">{t(`hr.rec_${p.recommendation}`)}</span>
                                  : <span className="text-muted-foreground">{t(p.submittedAt ? 'hr.feedbackHidden' : 'hr.feedbackPending')}</span>}
                              </p>
                              {p.notes && <p className="whitespace-pre-line break-words text-muted-foreground">{p.notes}</p>}
                            </div>
                          </li>
                        ))}
                      </ul>
                      {i.canGiveFeedback && <FeedbackForm org={slug} interviewId={i.id} current={{ recommendation: mine?.recommendation ?? null, notes: mine?.notes ?? null }} />}
                    </li>
                  );
                })}
              </ul>
            )}
          </section>

          {c.stage === 'HIRED' && (
            <section aria-labelledby="ob-h" className="rounded-lg border bg-card p-4">
              <h2 id="ob-h" className="mb-3 text-base font-semibold">{t('hr.onboarding')}</h2>
              <ul className="space-y-1">
                {d.onboarding.map((s) => (
                  <li key={s.id} className="flex items-center gap-3 rounded-md px-1 py-1.5 hover:bg-muted/50">
                    <Checkbox checked={s.done} disabled={!d.canManage} aria-label={s.title} onCheckedChange={(x) => act(() => recruitmentApi.setStepDone(slug, s.id, x === true))} />
                    <span className={s.done ? 'flex-1 text-sm text-muted-foreground line-through' : 'flex-1 text-sm'}>{s.title}</span>
                    {s.dueDate && <span className="text-xs text-muted-foreground tabular">{day(s.dueDate)}</span>}
                    {can('hr.recruit.manage') && <Button variant="ghost" size="icon" className="size-7" aria-label={t('common.remove')} onClick={() => act(() => recruitmentApi.removeStep(slug, s.id))}><Trash2 className="size-3.5" /></Button>}
                  </li>
                ))}
              </ul>
              {can('hr.recruit.manage') && (
                <form className="mt-3 flex gap-2" onSubmit={(e) => { e.preventDefault(); if (newStep.trim()) act(() => recruitmentApi.addStep(slug, c.id, { title: newStep.trim(), dueDate: null })).then(() => setNewStep('')); }}>
                  <Input value={newStep} maxLength={200} onChange={(e) => setNewStep(e.target.value)} placeholder={t('hr.addStepHint')} aria-label={t('hr.addStep')} />
                  <Button type="submit" variant="outline" size="sm"><Plus className="size-4" />{t('hr.addStep')}</Button>
                </form>
              )}
            </section>
          )}
        </div>

        <aside className="space-y-4">
          <section className="rounded-lg border bg-card p-4">
            <h2 className="mb-2 text-sm font-semibold">{t('hr.cv')}</h2>
            {d.cvName ? (
              <Button variant="outline" size="sm" className="w-full justify-start" onClick={() => recruitmentApi.downloadCv(slug, c.id, d.cvName!).catch((e) => toast.error(errorMessage(e)))}>
                <Download className="size-4" /><span className="truncate">{d.cvName}</span>{d.cvSize ? <span className="ml-auto text-xs text-muted-foreground">{bytes(d.cvSize)}</span> : null}
              </Button>
            ) : <p className="text-sm text-muted-foreground">{t('hr.noCv')}</p>}
            {d.canManage && (
              <>
                <input ref={fileInput} type="file" hidden accept=".pdf,.doc,.docx,image/*" onChange={(e) => { const f = e.target.files?.[0]; if (f) act(() => recruitmentApi.uploadCv(slug, c.id, f), t('hr.cvUploaded')); e.target.value = ''; }} />
                <Button variant="ghost" size="sm" className="mt-2" onClick={() => fileInput.current?.click()}><Upload className="size-4" />{t(d.cvName ? 'hr.replaceCv' : 'hr.uploadCv')}</Button>
              </>
            )}
          </section>
          {d.notes && (
            <section className="rounded-lg border bg-card p-4">
              <h2 className="mb-2 text-sm font-semibold">{t('hr.candidateNotes')}</h2>
              <p className="whitespace-pre-line break-words text-sm">{d.notes}</p>
            </section>
          )}
        </aside>
      </div>

      {dialog === 'edit' && <CandidateDialog org={slug} jobId={d.job.id} detail={d} onClose={() => setDialog(null)} />}
      {dialog === 'interview' && <InterviewDialog org={slug} candidateId={c.id} onClose={() => setDialog(null)} />}
      {dialog === 'reject' && <RejectDialog org={slug} candidateId={c.id} onClose={() => setDialog(null)} />}
      {dialog === 'hire' && <HireDialog org={slug} detail={d} onClose={() => setDialog(null)} />}
    </>
  );
}
