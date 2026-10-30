'use client';

import { ArrowLeft, CheckCircle2, Pencil, UserPlus } from 'lucide-react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useState } from 'react';
import { toast } from 'sonner';

import { PersonAvatar } from '@/components/common/person';
import { ErrorState, ListSkeleton } from '@/components/common/states';
import { CourseDialog, EnrolDialog } from '@/components/hr/development-ui';
import { Button } from '@/components/ui/button';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { developmentApi, useCourse } from '@/lib/api/development';
import { useOrgMutation } from '@/lib/api/queries';
import { day } from '@/lib/format';
import { useOrg } from '@/lib/org-context';

/** A course: its details, signing up or leaving, and (HR) enrolling people and marking them completed. */
export default function CoursePage() {
  const { slug } = useOrg();
  const { t } = useT();
  const { id } = useParams<{ id: string }>();
  const { data, error, isPending, refetch } = useCourse(slug, id);
  const [dialog, setDialog] = useState<'edit' | 'enrol' | null>(null);
  const run = useOrgMutation(slug, (fn: () => Promise<unknown>) => fn());
  const act = async (fn: () => Promise<unknown>, done: string) => {
    try { await run.mutateAsync(fn); toast.success(done); } catch (e) { toast.error(errorMessage(e)); }
  };
  if (error) return <ErrorState error={error} retry={() => refetch()} />;
  if (isPending) return <ListSkeleton rows={5} />;
  const c = data.course;

  return (
    <>
      <Link href={`/${slug}/hr/training`} className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="size-4" />{t('hr.trainingTitle')}</Link>
      <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">{c.title}</h1>
          <p className="text-sm text-muted-foreground">
            {[c.provider, c.location, c.startsOn && (c.endsOn && c.endsOn !== c.startsOn ? `${day(c.startsOn)} – ${day(c.endsOn)}` : day(c.startsOn)),
              c.hours && t('hr.hoursCount', { count: c.hours }), c.capacity ? t('hr.placesTaken', { taken: c.enrolled, capacity: c.capacity }) : null].filter(Boolean).join(' · ')}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          {data.canManage && <Button variant="outline" size="sm" onClick={() => setDialog('edit')}><Pencil className="size-4" />{t('common.edit')}</Button>}
          {data.canManage && <Button variant="outline" size="sm" onClick={() => setDialog('enrol')}><UserPlus className="size-4" />{t('hr.enrolPeople')}</Button>}
          {c.canEnrol && <Button size="sm" onClick={() => act(() => developmentApi.enrolMe(slug, c.id), t('hr.signedUp'))}>{t('hr.signUp')}</Button>}
          {c.myStatus === 'ENROLLED' && c.myEnrolmentId && <Button variant="outline" size="sm" onClick={() => act(() => developmentApi.setEnrolment(slug, c.myEnrolmentId!, 'CANCELLED'), t('hr.leftCourse'))}>{t('hr.leaveCourse')}</Button>}
        </div>
      </div>
      {c.myStatus && <p role="status" className="mb-4 rounded-md bg-muted px-3 py-2 text-sm">{t(`hr.youAre_${c.myStatus}`)}</p>}
      {c.description && <p className="mb-4 whitespace-pre-line rounded-lg border bg-card p-4 text-sm">{c.description}</p>}
      {data.canManage && (
        <section aria-labelledby="en-h" className="rounded-lg border bg-card p-4">
          <h2 id="en-h" className="mb-3 text-base font-semibold">{t('hr.participants')}</h2>
          {data.enrolments.length === 0 ? <p className="text-sm text-muted-foreground">{t('hr.noParticipants')}</p> : (
            <ul className="divide-y">
              {data.enrolments.map((e) => (
                <li key={e.id} className="flex flex-wrap items-center gap-3 py-2">
                  <PersonAvatar person={e.person} className="size-7" />
                  <span className="min-w-0 flex-1 text-sm font-medium">{e.person.name}</span>
                  <span className="rounded-full bg-muted px-2 py-0.5 text-xs">{t(`hr.enrol_${e.status}`)}</span>
                  {e.status === 'ENROLLED' && <Button variant="outline" size="sm" onClick={() => act(() => developmentApi.setEnrolment(slug, e.id, 'COMPLETED'), t('hr.markedCompleted'))}><CheckCircle2 className="size-4" />{t('hr.markCompleted')}</Button>}
                  {e.status === 'ENROLLED' && <Button variant="ghost" size="sm" onClick={() => act(() => developmentApi.setEnrolment(slug, e.id, 'CANCELLED'), t('hr.enrolmentCancelled'))}>{t('common.remove')}</Button>}
                </li>
              ))}
            </ul>
          )}
        </section>
      )}
      {dialog === 'edit' && <CourseDialog org={slug} course={c} onClose={() => setDialog(null)} />}
      {dialog === 'enrol' && <EnrolDialog org={slug} courseId={c.id} already={data.enrolments.filter((e) => e.status !== 'CANCELLED').map((e) => e.person.id)} onClose={() => setDialog(null)} />}
    </>
  );
}
