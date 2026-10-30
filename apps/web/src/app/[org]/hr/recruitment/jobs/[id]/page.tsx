'use client';

import { ArrowLeft, CalendarClock, FileText, Pencil, UserPlus } from 'lucide-react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useState } from 'react';

import { ErrorState, ListSkeleton } from '@/components/common/states';
import { CandidateDialog, JobDialog, Rating, StageBadge, orgDateTime } from '@/components/hr/recruitment-ui';
import { Button } from '@/components/ui/button';
import { useT } from '@/i18n/provider';
import { STAGES, useCandidates, useJob } from '@/lib/api/recruitment';
import { useOrg } from '@/lib/org-context';

/** One opening: its details and the candidates by stage (a board that scrolls sideways on phones). */
export default function JobPage() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const { id } = useParams<{ id: string }>();
  const job = useJob(slug, id);
  const list = useCandidates(slug, id);
  const [editing, setEditing] = useState(false);
  const [adding, setAdding] = useState(false);
  const back = <Link href={`/${slug}/hr/recruitment`} className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="size-4" />{t('hr.recruitmentTitle')}</Link>;
  if (job.error || list.error) return <>{back}<ErrorState error={job.error ?? list.error} retry={() => { job.refetch(); list.refetch(); }} /></>;
  if (job.isPending || list.isPending) return <>{back}<ListSkeleton rows={6} /></>;
  const j = job.data;

  return (
    <>
      {back}
      <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2"><h1 className="text-xl font-semibold tracking-tight">{j.title}</h1><span className="rounded-full bg-muted px-2 py-0.5 text-xs">{t(`hr.job_${j.status}`)}</span></div>
          <p className="mt-1 text-sm text-muted-foreground">
            {[j.department, j.location, j.employmentType && t(`hr.emp_${j.employmentType}`), t('hr.openingsCount', { count: j.openings }),
              j.hiringManager && t('hr.hiringManagerIs', { name: j.hiringManager.name })].filter(Boolean).join(' · ')}
          </p>
        </div>
        <div className="flex gap-2">
          {can('hr.recruit.manage') && <Button variant="outline" size="sm" onClick={() => setEditing(true)}><Pencil className="size-4" />{t('common.edit')}</Button>}
          {j.status !== 'CLOSED' && <Button size="sm" onClick={() => setAdding(true)}><UserPlus className="size-4" />{t('hr.addCandidate')}</Button>}
        </div>
      </div>
      {j.description && <p className="mb-5 whitespace-pre-line rounded-lg border bg-card p-4 text-sm">{j.description}</p>}

      <div className="-mx-4 overflow-x-auto px-4 pb-2 sm:mx-0 sm:px-0">
        <div className="grid min-w-[60rem] grid-cols-6 gap-3">
          {STAGES.map((s) => {
            const here = list.data.filter((c) => c.stage === s);
            return (
              <section key={s} aria-label={t(`hr.stage_${s}`)} className="rounded-lg bg-muted/40 p-2">
                <h2 className="mb-2 flex items-center justify-between px-1 text-xs font-medium"><StageBadge stage={s} /><span className="tabular text-muted-foreground">{here.length}</span></h2>
                <ul className="space-y-2">
                  {here.map((c) => (
                    <li key={c.id}>
                      <Link href={`/${slug}/hr/recruitment/candidates/${c.id}`} className="block rounded-md border bg-card p-2.5 text-sm shadow-sm hover:border-primary/40">
                        <span className="block truncate font-medium">{c.name}</span>
                        <span className="mt-1 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                          <Rating value={c.rating} />
                          {c.hasCv && <FileText className="size-3" aria-label={t('hr.hasCv')} />}
                          {c.nextInterview && <span className="inline-flex items-center gap-1"><CalendarClock className="size-3" />{orgDateTime(c.nextInterview)}</span>}
                        </span>
                      </Link>
                    </li>
                  ))}
                </ul>
              </section>
            );
          })}
        </div>
      </div>
      {editing && <JobDialog org={slug} job={j} onClose={() => setEditing(false)} />}
      {adding && <CandidateDialog org={slug} jobId={id} onClose={() => setAdding(false)} />}
    </>
  );
}
