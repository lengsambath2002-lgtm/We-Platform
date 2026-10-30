'use client';

import { GraduationCap, Plus } from 'lucide-react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { Suspense, useState } from 'react';

import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { CourseDialog } from '@/components/hr/development-ui';
import { Button } from '@/components/ui/button';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useT } from '@/i18n/provider';
import { type Course, useCourses, useMyTraining } from '@/lib/api/development';
import { day } from '@/lib/format';
import { useOrg } from '@/lib/org-context';

function CourseLine({ org, c, status }: { org: string; c: Course; status?: string | null }) {
  const { t } = useT();
  return (
    <Link href={`/${org}/hr/training/${c.id}`} className="flex flex-wrap items-center gap-3 px-4 py-3 hover:bg-muted/50">
      <span className="min-w-0 flex-1">
        <span className="block font-medium">{c.title}</span>
        <span className="block text-xs text-muted-foreground">
          {[c.provider, c.startsOn && day(c.startsOn), c.hours && t('hr.hoursCount', { count: c.hours }),
            c.capacity ? t('hr.placesTaken', { taken: c.enrolled, capacity: c.capacity }) : t('hr.enrolledCount', { count: c.enrolled })].filter(Boolean).join(' · ')}
        </span>
      </span>
      {status ? <span className="rounded-full bg-muted px-2 py-0.5 text-xs">{t(`hr.enrol_${status as 'ENROLLED'}`)}</span>
        : c.status === 'CLOSED' && <span className="rounded-full bg-muted px-2 py-0.5 text-xs">{t('hr.job_CLOSED')}</span>}
    </Link>
  );
}

/** Training: the courses on offer, my training record, and (HR) new courses. */
function Training() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const params = useSearchParams();
  const router = useRouter();
  const courses = useCourses(slug);
  const mine = useMyTraining(slug);
  const [creating, setCreating] = useState(false);
  const tab = params.get('tab') === 'mine' ? 'mine' : 'courses';
  return (
    <>
      <PageHeader title={t('hr.trainingTitle')} subtitle={t('hr.trainingSubtitle')}
        actions={can('hr.manage') && <Button size="sm" onClick={() => setCreating(true)}><Plus className="size-4" />{t('hr.newCourse')}</Button>} />
      <Tabs value={tab} onValueChange={(v) => router.replace(`/${slug}/hr/training${v === 'courses' ? '' : '?tab=mine'}`, { scroll: false })}>
        <TabsList className="mb-4"><TabsTrigger value="courses">{t('hr.courses')}</TabsTrigger><TabsTrigger value="mine">{t('hr.myTraining')}</TabsTrigger></TabsList>
        <TabsContent value="courses">
          {courses.error ? <ErrorState error={courses.error} retry={() => courses.refetch()} /> : courses.isPending ? <ListSkeleton rows={3} />
            : courses.data.length === 0 ? <EmptyState icon={GraduationCap} title={t('hr.noCourses')} /> : (
              <ul className="divide-y rounded-lg border bg-card">{courses.data.map((c) => <li key={c.id}><CourseLine org={slug} c={c} status={c.myStatus} /></li>)}</ul>
            )}
        </TabsContent>
        <TabsContent value="mine">
          {mine.error ? <ErrorState error={mine.error} retry={() => mine.refetch()} /> : mine.isPending ? <ListSkeleton rows={2} />
            : mine.data.length === 0 ? <EmptyState icon={GraduationCap} title={t('hr.noTraining')} body={t('hr.noTrainingBody')} /> : (
              <ul className="divide-y rounded-lg border bg-card">{mine.data.map((m) => <li key={m.enrolmentId}><CourseLine org={slug} c={m.course} status={m.status} /></li>)}</ul>
            )}
        </TabsContent>
      </Tabs>
      {creating && <CourseDialog org={slug} course="new" onClose={() => setCreating(false)} />}
    </>
  );
}

export default function Page() {
  return <Suspense><Training /></Suspense>;
}
