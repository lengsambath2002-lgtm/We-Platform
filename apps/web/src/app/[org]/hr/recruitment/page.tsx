'use client';

import { BriefcaseBusiness, CalendarClock, Plus, Trash2, UserCheck } from 'lucide-react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { Suspense, useEffect, useState } from 'react';
import { toast } from 'sonner';

import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton, PermissionDenied } from '@/components/common/states';
import { JobDialog, StageBadge, orgDateTime } from '@/components/hr/recruitment-ui';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Progress } from '@/components/ui/progress';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { type TemplateStep, STAGES, recruitmentApi, useHires, useJobs, useMyInterviews, useOnboardingTemplate } from '@/lib/api/recruitment';
import { useOrgMutation } from '@/lib/api/queries';
import { day } from '@/lib/format';
import { useOrg } from '@/lib/org-context';

function Openings({ org, manage }: { org: string; manage: boolean }) {
  const { t } = useT();
  const { data, error, isPending, refetch } = useJobs(org);
  const [creating, setCreating] = useState(false);
  return (
    <section aria-labelledby="jobs-h">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <div><h2 id="jobs-h" className="text-base font-semibold">{t('hr.openingsTitle')}</h2><p className="text-sm text-muted-foreground">{t(manage ? 'hr.openingsBody' : 'hr.openingsBodyManager')}</p></div>
        {manage && <Button size="sm" onClick={() => setCreating(true)}><Plus className="size-4" />{t('hr.newJob')}</Button>}
      </div>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={3} />
        : data.length === 0 ? <EmptyState icon={BriefcaseBusiness} title={t('hr.noJobs')} body={manage ? t('hr.noJobsBody') : undefined} /> : (
          <ul className="grid gap-3 md:grid-cols-2">
            {data.map((j) => (
              <li key={j.id}>
                <Link href={`/${org}/hr/recruitment/jobs/${j.id}`} className="block rounded-lg border bg-card p-4 hover:border-primary/40">
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0"><p className="truncate font-medium">{j.title}</p><p className="truncate text-xs text-muted-foreground">{[j.department, j.location, j.hiringManager?.name].filter(Boolean).join(' · ')}</p></div>
                    <span className="shrink-0 rounded-full bg-muted px-2 py-0.5 text-xs">{t(`hr.job_${j.status}`)}</span>
                  </div>
                  <div className="mt-3 flex flex-wrap gap-1.5">
                    {STAGES.filter((s) => j.stages[s]).map((s) => <span key={s} className="inline-flex items-center gap-1 text-xs"><StageBadge stage={s} /><span className="tabular">{j.stages[s]}</span></span>)}
                    {Object.keys(j.stages).length === 0 && <span className="text-xs text-muted-foreground">{t('hr.noCandidatesYet')}</span>}
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        )}
      {creating && <JobDialog org={org} job="new" onClose={() => setCreating(false)} />}
    </section>
  );
}

function MyInterviews({ org }: { org: string }) {
  const { t } = useT();
  const { data, error, isPending, refetch } = useMyInterviews(org);
  return (
    <section aria-labelledby="iv-h">
      <h2 id="iv-h" className="mb-3 text-base font-semibold">{t('hr.myInterviews')}</h2>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={2} />
        : data.length === 0 ? <EmptyState icon={CalendarClock} title={t('hr.noInterviews')} /> : (
          <ul className="divide-y rounded-lg border bg-card">
            {data.map((i) => (
                <li key={i.id}>
                  <Link href={`/${org}/hr/recruitment/candidates/${i.candidateId}`} className="flex flex-wrap items-center gap-3 px-4 py-3 hover:bg-muted/50">
                    <span className="min-w-0 flex-1"><span className="block font-medium">{i.candidateName}</span><span className="block text-xs text-muted-foreground">{i.jobTitle}{i.location ? ` · ${i.location}` : ''}</span></span>
                    <span className="text-sm tabular">{orgDateTime(i.startsAt)}</span>
                  </Link>
                </li>
            ))}
          </ul>
        )}
    </section>
  );
}

function Hires({ org }: { org: string }) {
  const { t } = useT();
  const { data, error, isPending, refetch } = useHires(org);
  return (
    <section aria-labelledby="hires-h">
      <h2 id="hires-h" className="text-base font-semibold">{t('hr.hiresTitle')}</h2>
      <p className="mb-3 text-sm text-muted-foreground">{t('hr.hiresBody')}</p>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={2} />
        : data.length === 0 ? <EmptyState icon={UserCheck} title={t('hr.noHires')} /> : (
          <ul className="divide-y rounded-lg border bg-card">
            {data.map((h) => (
              <li key={h.candidate.id}>
                <Link href={`/${org}/hr/recruitment/candidates/${h.candidate.id}`} className="flex flex-wrap items-center gap-3 px-4 py-3 hover:bg-muted/50">
                  <span className="min-w-0 flex-1">
                    <span className="block font-medium">{h.candidate.name}</span>
                    <span className="block text-xs text-muted-foreground">{h.jobTitle}{h.startDate ? ` · ${t('hr.startsOn', { date: day(h.startDate) })}` : ''} · {t(h.joined ? 'hr.joined' : 'hr.notJoinedYet')}</span>
                  </span>
                  <span className="w-40">
                    <span className="mb-1 block text-right text-xs text-muted-foreground tabular">{t('hr.stepsDone', { done: h.done, total: h.total })}</span>
                    <Progress value={h.total ? (h.done / h.total) * 100 : 0} aria-label={t('hr.stepsDone', { done: h.done, total: h.total })} />
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        )}
    </section>
  );
}

function TemplateEditor({ org }: { org: string }) {
  const { t } = useT();
  const { data, error, isPending, refetch } = useOnboardingTemplate(org);
  const [steps, setSteps] = useState<TemplateStep[]>([]);
  useEffect(() => { if (data) setSteps(data.items); }, [data]);
  const save = useOrgMutation(org, (items: TemplateStep[]) => recruitmentApi.saveTemplate(org, items));
  if (error) return <ErrorState error={error} retry={() => refetch()} />;
  if (isPending) return <ListSkeleton rows={4} />;
  return (
    <section aria-labelledby="tpl-h">
      <h2 id="tpl-h" className="text-base font-semibold">{t('hr.onboardingSteps')}</h2>
      <p className="mb-3 text-sm text-muted-foreground">{t('hr.onboardingStepsBody')}</p>
      <ul className="space-y-2">
        {steps.map((s, i) => (
          <li key={i} className="grid grid-cols-[1fr_6.5rem_auto] items-center gap-2">
            <Input aria-label={t('hr.stepTitle')} maxLength={200} value={s.title} onChange={(e) => setSteps(steps.map((x, j) => (j === i ? { ...x, title: e.target.value } : x)))} />
            <Input aria-label={t('hr.stepDays')} type="number" min={-60} max={180} value={String(s.dueDays)} onChange={(e) => setSteps(steps.map((x, j) => (j === i ? { ...x, dueDays: Number(e.target.value) } : x)))} />
            <Button variant="ghost" size="icon" className="size-9" aria-label={t('common.remove')} onClick={() => setSteps(steps.filter((_, j) => j !== i))}><Trash2 className="size-4" /></Button>
          </li>
        ))}
      </ul>
      <p className="mt-1 text-xs text-muted-foreground">{t('hr.stepDaysHint')}</p>
      <div className="mt-3 flex gap-2">
        <Button variant="outline" size="sm" disabled={steps.length >= 30} onClick={() => setSteps([...steps, { title: '', dueDays: 0 }])}><Plus className="size-4" />{t('hr.addStep')}</Button>
        <Button size="sm" disabled={save.isPending} onClick={async () => {
          if (steps.some((s) => !s.title.trim())) { toast.error(t('hr.errStepTitle')); return; }
          try { await save.mutateAsync(steps.map((s) => ({ title: s.title.trim(), dueDays: s.dueDays }))); toast.success(t('hr.stepsSaved')); } catch (e) { toast.error(errorMessage(e)); }
        }}>{t('common.save')}</Button>
      </div>
    </section>
  );
}

/** Recruitment: openings, my interviews, hires with their onboarding, and (HR) the onboarding steps. */
function Recruitment() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const params = useSearchParams();
  const router = useRouter();
  if (!can('hr.recruit')) return <PermissionDenied />;
  const manage = can('hr.manage');
  const tabs = ['openings', 'interviews', ...(manage ? ['hires', 'onboarding'] : [])];
  const tab = tabs.includes(params.get('tab') ?? '') ? params.get('tab')! : 'openings';
  return (
    <>
      <PageHeader title={t('hr.recruitmentTitle')} subtitle={t('hr.recruitmentSubtitle')} />
      <Tabs value={tab} onValueChange={(v) => router.replace(`/${slug}/hr/recruitment${v === 'openings' ? '' : `?tab=${v}`}`, { scroll: false })}>
        <TabsList className="mb-4">
          <TabsTrigger value="openings">{t('hr.openingsTitle')}</TabsTrigger>
          <TabsTrigger value="interviews">{t('hr.myInterviews')}</TabsTrigger>
          {manage && <TabsTrigger value="hires">{t('hr.hiresTitle')}</TabsTrigger>}
          {manage && <TabsTrigger value="onboarding">{t('hr.onboardingSteps')}</TabsTrigger>}
        </TabsList>
        <TabsContent value="openings"><Openings org={slug} manage={manage} /></TabsContent>
        <TabsContent value="interviews"><MyInterviews org={slug} /></TabsContent>
        {manage && <TabsContent value="hires"><Hires org={slug} /></TabsContent>}
        {manage && <TabsContent value="onboarding"><TemplateEditor org={slug} /></TabsContent>}
      </Tabs>
    </>
  );
}

export default function Page() {
  return <Suspense><Recruitment /></Suspense>;
}
