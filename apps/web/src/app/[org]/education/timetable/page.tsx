'use client';

import { CalendarClock, Plus } from 'lucide-react';
import { useRouter, useSearchParams } from 'next/navigation';
import { Suspense, useState } from 'react';

import { PeriodDialog } from '@/components/education/class-dialogs';
import { subjectName, WEEKDAY_LABEL } from '@/components/education/ui';
import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { useT } from '@/i18n/provider';
import { hhmm, type PeriodView, useClasses, useCourses, useTeachers, useTimetable, WEEKDAYS } from '@/lib/api/education';
import { useOrg } from '@/lib/org-context';
import { cn } from '@/lib/utils';

const MINE = 'mine';

function Lesson({ p, showClass, onOpen }: { p: PeriodView; showClass: boolean; onOpen?: () => void }) {
  const { t } = useT();
  const body = (
    <>
      <span className="block text-xs text-muted-foreground tabular">{hhmm(p.startsAt)}–{hhmm(p.endsAt)}</span>
      <span className="block truncate font-medium">{subjectName(p.subject)}</span>
      <span className="block truncate text-xs text-muted-foreground">{[showClass ? p.classRef.name : p.teacher.name, p.room && t('education.roomName', { room: p.room })].filter(Boolean).join(' · ')}</span>
    </>
  );
  const cls = 'block w-full rounded-md border bg-card px-2.5 py-2 text-left text-sm';
  return onOpen ? <button type="button" onClick={onOpen} className={cn(cls, 'hover:bg-muted/50')} aria-label={t('education.editLessonOf', { subject: subjectName(p.subject), day: WEEKDAY_LABEL[String(p.weekday) as '1'], time: hhmm(p.startsAt) })}>{body}</button>
    : <div className={cls}>{body}</div>;
}

/** The weekly timetable of a class, a teacher, or (teachers) their own week. Admins add and change lessons. */
function TimetablePage() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const router = useRouter();
  const params = useSearchParams();
  const manage = can('education.manage');
  const classes = useClasses(slug);
  const teachers = useTeachers(slug);
  const classId = params.get('class') ?? '';
  const teacherId = params.get('teacher') ?? '';
  const view = classId ? `c:${classId}` : teacherId ? `t:${teacherId}` : manage ? (classes.data?.[0] ? `c:${classes.data[0].id}` : MINE) : MINE;
  const filter = view.startsWith('c:') ? { classId: view.slice(2) } : view.startsWith('t:') ? { teacherId: view.slice(2) } : {};
  const { data, error, isPending, refetch } = useTimetable(slug, filter);
  const courses = useCourses(slug, filter.classId);
  const [editing, setEditing] = useState<{ period?: PeriodView; weekday?: number } | null>(null);
  const showClass = !filter.classId;
  const choose = (v: string) => router.replace(`/${slug}/education/timetable${v === MINE ? '' : v.startsWith('c:') ? `?class=${v.slice(2)}` : `?teacher=${v.slice(2)}`}`);

  return (
    <>
      <PageHeader title={t('education.timetableTitle')} subtitle={t('education.timetableSubtitle')}
        actions={manage && (courses.data?.length ?? 0) > 0 && <Button size="sm" onClick={() => setEditing({})}><Plus className="size-4" />{t('education.addLesson')}</Button>} />
      <div className="mb-4 space-y-1">
        <Label htmlFor="tt-v" className="text-xs text-muted-foreground">{t('education.show')}</Label>
        <Select value={view} onValueChange={choose}>
          <SelectTrigger id="tt-v" className="w-full sm:w-64"><SelectValue /></SelectTrigger>
          <SelectContent>
            {!manage && <SelectItem value={MINE}>{t('education.myWeek')}</SelectItem>}
            {(classes.data ?? []).map((c) => <SelectItem key={c.id} value={`c:${c.id}`}>{t('education.classTitle', { name: c.name })}</SelectItem>)}
            {manage && (teachers.data ?? []).filter((x) => x.active).map((x) => <SelectItem key={x.id} value={`t:${x.id}`}>{x.name}</SelectItem>)}
          </SelectContent>
        </Select>
      </div>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={6} />
        : data.length === 0 ? <EmptyState icon={CalendarClock} title={t('education.noLessonsTitle')} body={manage ? t('education.noLessonsBody') : t('education.noLessonsTeacher')}
          action={manage && (courses.data?.length ?? 0) > 0 && <Button size="sm" variant="outline" onClick={() => setEditing({})}>{t('education.addLesson')}</Button>} />
          : (
            <div className="grid gap-4 md:grid-cols-3 xl:grid-cols-6">
              {WEEKDAYS.map((d) => {
                const day = data.filter((p) => p.weekday === d);
                return (
                  <section key={d} aria-labelledby={`wd-${d}`} className="min-w-0 space-y-2">
                    <div className="flex items-center justify-between">
                      <h2 id={`wd-${d}`} className="text-sm font-semibold">{WEEKDAY_LABEL[String(d) as '1']}</h2>
                      {manage && filter.classId && <Button variant="ghost" size="icon-sm" aria-label={t('education.addLessonOn', { day: WEEKDAY_LABEL[String(d) as '1'] })} onClick={() => setEditing({ weekday: d })}><Plus className="size-4" /></Button>}
                    </div>
                    {day.length === 0 ? <p className="rounded-md border border-dashed px-2.5 py-3 text-center text-xs text-muted-foreground">{t('education.noLessonsDay')}</p>
                      : day.map((p) => <Lesson key={p.id} p={p} showClass={showClass} onOpen={p.canEdit ? () => setEditing({ period: p }) : undefined} />)}
                  </section>
                );
              })}
            </div>
          )}
      {editing && <PeriodDialog org={slug} period={editing.period} weekday={editing.weekday} courses={courses.data ?? []} onClose={() => setEditing(null)} />}
    </>
  );
}

export default function Timetable() {
  return <Suspense fallback={<ListSkeleton />}><TimetablePage /></Suspense>;
}
