'use client';

import { ArrowLeft } from 'lucide-react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';
import { toast } from 'sonner';

import { PersonAvatar } from '@/components/common/person';
import { ErrorState, ListSkeleton } from '@/components/common/states';
import { AnswersForm, ReviewBadge, Stars } from '@/components/hr/development-ui';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { type Answer, developmentApi, useReview } from '@/lib/api/development';
import { usePeople } from '@/lib/api/hr';
import { useOrgMutation } from '@/lib/api/queries';
import { day } from '@/lib/format';
import { useOrg } from '@/lib/org-context';

const blank = (n: number): Answer[] => Array.from({ length: n }, () => ({ rating: null, comment: null }));

/** One review: the self-assessment, the reviewer's assessment and overall rating, and acknowledging it. */
export default function ReviewPage() {
  const { slug } = useOrg();
  const { t } = useT();
  const { id } = useParams<{ id: string }>();
  const { data: r, error, isPending, refetch } = useReview(slug, id);
  const people = usePeople(slug);
  const [self, setSelf] = useState<Answer[]>([]);
  const [selfSummary, setSelfSummary] = useState('');
  const [mgr, setMgr] = useState<Answer[]>([]);
  const [mgrSummary, setMgrSummary] = useState('');
  const [overall, setOverall] = useState<number | null>(null);
  // Filled once per review: a background refresh must not wipe what someone is typing.
  const filled = useRef<string | null>(null);
  useEffect(() => {
    if (!r || filled.current === r.id) return;
    filled.current = r.id;
    const n = r.cycle.questions.length;
    setSelf(r.selfAnswers ?? blank(n));
    setSelfSummary(r.selfSummary ?? '');
    setMgr(r.managerAnswers ?? blank(n));
    setMgrSummary(r.managerSummary ?? '');
    setOverall(r.overallRating);
  }, [r]);
  const run = useOrgMutation(slug, (fn: () => Promise<unknown>) => fn());
  const act = async (fn: () => Promise<unknown>, done: string) => {
    try { await run.mutateAsync(fn); toast.success(done); } catch (e) { toast.error(errorMessage(e)); }
  };
  if (error) return <ErrorState error={error} retry={() => refetch()} />;
  if (isPending) return <ListSkeleton rows={6} />;
  const q = r.cycle.questions;

  return (
    <>
      <Link href={`/${slug}/hr/reviews`} className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="size-4" />{t('hr.reviewsTitle')}</Link>
      <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-center gap-3">
          <PersonAvatar person={r.employee} className="size-10" />
          <div>
            <div className="flex flex-wrap items-center gap-2"><h1 className="text-xl font-semibold tracking-tight">{r.employee.name}</h1><ReviewBadge status={r.status} /></div>
            <p className="text-sm text-muted-foreground">{r.cycle.name} · {t('hr.reviewedBy', { name: r.reviewer.name })} · {t('hr.dueOn', { date: day(r.cycle.dueDate) })}</p>
          </div>
        </div>
        {r.canAcknowledge && <Button onClick={() => act(() => developmentApi.acknowledge(slug, r.id), t('hr.acknowledged'))}>{t('hr.acknowledge')}</Button>}
      </div>

      {r.canChangeReviewer && (
        <div className="mb-4 space-y-1">
          <Label htmlFor="rv-reviewer" className="text-xs text-muted-foreground">{t('hr.reviewer')}</Label>
          <Select value={r.reviewer.id} onValueChange={(v) => act(() => developmentApi.changeReviewer(slug, r.id, v), t('hr.reviewerChanged'))}>
            <SelectTrigger id="rv-reviewer" className="w-64"><SelectValue /></SelectTrigger>
            <SelectContent>{(people.data ?? []).filter((p) => p.person.id !== r.employee.id).map((p) => <SelectItem key={p.person.id} value={p.person.id}>{p.person.name}</SelectItem>)}</SelectContent>
          </Select>
        </div>
      )}

      <div className="grid gap-4 lg:grid-cols-2">
        <section aria-labelledby="self-h" className="rounded-lg border bg-card p-4">
          <h2 id="self-h" className="mb-3 text-base font-semibold">{t('hr.selfAssessment')}</h2>
          {r.canEditSelf ? (
            <div className="space-y-4">
              <AnswersForm questions={q} answers={self} onChange={setSelf} />
              <div className="space-y-1.5"><Label htmlFor="self-sum">{t('hr.summary')}</Label><Textarea id="self-sum" rows={3} maxLength={5000} value={selfSummary} onChange={(e) => setSelfSummary(e.target.value)} /></div>
              <div className="flex gap-2">
                <Button variant="outline" disabled={run.isPending} onClick={() => act(() => developmentApi.saveSelf(slug, r.id, { answers: self, summary: selfSummary || null, submit: false }), t('hr.draftSaved'))}>{t('hr.saveDraft')}</Button>
                <Button disabled={run.isPending} onClick={() => act(() => developmentApi.saveSelf(slug, r.id, { answers: self, summary: selfSummary || null, submit: true }), t('hr.selfSent'))}>{t('hr.sendToReviewer')}</Button>
              </div>
            </div>
          ) : r.selfSubmittedAt ? (
            <div className="space-y-3"><AnswersForm questions={q} answers={r.selfAnswers ?? []} />{r.selfSummary && <p className="whitespace-pre-line text-sm">{r.selfSummary}</p>}</div>
          ) : <p className="text-sm text-muted-foreground">{t('hr.selfPending', { name: r.employee.name })}</p>}
        </section>

        <section aria-labelledby="mgr-h" className="rounded-lg border bg-card p-4">
          <h2 id="mgr-h" className="mb-3 text-base font-semibold">{t('hr.reviewerAssessment')}</h2>
          {r.canEditReview ? (
            <div className="space-y-4">
              <AnswersForm questions={q} answers={mgr} onChange={setMgr} />
              <div className="space-y-1.5"><Label htmlFor="mgr-sum">{t('hr.summary')}</Label><Textarea id="mgr-sum" rows={3} maxLength={5000} value={mgrSummary} onChange={(e) => setMgrSummary(e.target.value)} /></div>
              <div className="space-y-1"><p className="text-sm font-medium">{t('hr.overallRating')}</p><Stars value={overall} onChange={setOverall} label={t('hr.overallRating')} /></div>
              <div className="flex gap-2">
                <Button variant="outline" disabled={run.isPending} onClick={() => act(() => developmentApi.saveReview(slug, r.id, { answers: mgr, summary: mgrSummary || null, overallRating: overall, submit: false }), t('hr.draftSaved'))}>{t('hr.saveDraft')}</Button>
                <Button disabled={run.isPending} onClick={() => act(() => developmentApi.saveReview(slug, r.id, { answers: mgr, summary: mgrSummary || null, overallRating: overall, submit: true }), t('hr.reviewShared'))}>{t('hr.shareReview')}</Button>
              </div>
            </div>
          ) : r.managerAnswers && r.managerSubmittedAt ? (
            <div className="space-y-3">
              <AnswersForm questions={q} answers={r.managerAnswers} />
              {r.managerSummary && <p className="whitespace-pre-line text-sm">{r.managerSummary}</p>}
              {r.overallRating && <div className="flex items-center gap-2 text-sm font-medium">{t('hr.overallRating')}<Stars value={r.overallRating} label="" /></div>}
            </div>
          ) : <p className="text-sm text-muted-foreground">{t(r.status === 'SELF' ? 'hr.reviewerWaits' : 'hr.reviewerPending', { name: r.reviewer.name })}</p>}
        </section>
      </div>
    </>
  );
}
