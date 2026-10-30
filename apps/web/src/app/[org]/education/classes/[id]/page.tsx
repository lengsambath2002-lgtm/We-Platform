'use client';

import { BookOpen, ClipboardCheck, Pencil, Plus, Sheet, Trash2, UserPlus, Users, X } from 'lucide-react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useState } from 'react';
import { toast } from 'sonner';

import { ClassDialog, CourseDialog, EnrollDialog } from '@/components/education/class-dialogs';
import { Crumb, Panel, StudentBadge, studentName, subjectName } from '@/components/education/ui';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { type Course, eduApi, isoToday, useClass } from '@/lib/api/education';
import { useOrgMutation } from '@/lib/api/queries';
import { useOrg } from '@/lib/org-context';

type Open = { kind: 'edit' } | { kind: 'enroll' } | { kind: 'course'; course?: Course } | null;

/** A class: its students (enrol, take out), its courses with their teachers, and shortcuts to attendance and results. */
export default function ClassPage() {
  const { slug } = useOrg();
  const { t } = useT();
  const { id } = useParams<{ id: string }>();
  const { data, error, isPending, refetch } = useClass(slug, id);
  const [open, setOpen] = useState<Open>(null);
  const unenroll = useOrgMutation(slug, (studentId: string) => eduApi.unenroll(slug, id, studentId));
  const removeCourse = useOrgMutation(slug, (courseId: string) => eduApi.removeCourse(slug, courseId));
  const base = `/${slug}/education`;

  if (error) return <ErrorState error={error} retry={() => refetch()} />;
  if (isPending) return <ListSkeleton />;
  const c = data.summary;
  const act = async (fn: () => Promise<unknown>, ok: string) => { try { await fn(); toast.success(ok); } catch (e) { toast.error(errorMessage(e)); } };

  return (
    <>
      <Crumb href={`${base}/classes`} label={t('education.classesTitle')} current={c.name} />
      <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">{t('education.classTitle', { name: c.name })}</h1>
          <p className="mt-1 text-sm text-muted-foreground">{[c.yearName, c.gradeName, c.homeroom && t('education.homeroomIs', { name: c.homeroom.name }), c.room && t('education.roomName', { room: c.room })].filter(Boolean).join(' · ')}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          {data.canTakeAttendance && <Button asChild size="sm"><Link href={`${base}/attendance?class=${c.id}&day=${isoToday()}`}><ClipboardCheck className="size-4" />{t('education.takeAttendance')}</Link></Button>}
          <Button asChild size="sm" variant="outline"><Link href={`${base}/gradebook?class=${c.id}`}><Sheet className="size-4" />{t('education.results')}</Link></Button>
          {data.canManage && <Button size="sm" variant="outline" onClick={() => setOpen({ kind: 'edit' })}><Pencil className="size-4" />{t('education.edit')}</Button>}
        </div>
      </div>
      <div className="grid gap-8 lg:grid-cols-[1fr_380px]">
        <Panel id="st-h" title={t('education.studentsCount', { count: data.students.length })}
          action={data.canManage && <Button size="sm" variant="outline" onClick={() => setOpen({ kind: 'enroll' })}><UserPlus className="size-4" />{t('education.addStudents')}</Button>}>
          {data.students.length === 0 ? (
            <EmptyState icon={Users} title={t('education.emptyClassTitle')} body={data.canManage ? t('education.emptyClassBody') : t('education.emptyClassTeacher')}
              action={data.canManage && <Button size="sm" variant="outline" onClick={() => setOpen({ kind: 'enroll' })}>{t('education.addStudents')}</Button>} />
          ) : (
            <div className="rounded-lg border bg-card">
              <Table>
                <TableHeader><TableRow>
                  <TableHead>{t('education.number')}</TableHead><TableHead>{t('education.name')}</TableHead>
                  <TableHead className="hidden sm:table-cell">{t('education.mainContact')}</TableHead><TableHead />
                </TableRow></TableHeader>
                <TableBody>
                  {data.students.map((s) => (
                    <TableRow key={s.id}>
                      <TableCell className="tabular text-muted-foreground">{s.number}</TableCell>
                      <TableCell><Link href={`${base}/students/${s.id}`} className="font-medium text-primary hover:underline">{studentName(s)}</Link> {s.status !== 'ACTIVE' && <StudentBadge status={s.status} />}</TableCell>
                      <TableCell className="hidden max-w-56 truncate text-muted-foreground sm:table-cell">{[s.primaryGuardian, s.guardianPhone].filter(Boolean).join(' · ') || '—'}</TableCell>
                      <TableCell className="w-10 text-right">
                        {data.canManage && <Button variant="ghost" size="icon-sm" aria-label={t('education.takeOut', { name: studentName(s) })}
                          onClick={() => act(() => unenroll.mutateAsync(s.id), t('education.takenOut'))}><X className="size-4" /></Button>}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </Panel>
        <Panel id="co-h" title={t('education.courses')}
          action={data.canManage && <Button size="sm" variant="outline" onClick={() => setOpen({ kind: 'course' })}><Plus className="size-4" />{t('education.addCourse')}</Button>}>
          {data.courses.length === 0 ? <EmptyState icon={BookOpen} title={t('education.noCoursesTitle')} body={t('education.noCoursesBody')} /> : (
            <ul className="divide-y rounded-lg border bg-card">
              {data.courses.map((x) => (
                <li key={x.id} className="flex items-center justify-between gap-2 px-4 py-3">
                  <div className="min-w-0">
                    <p className="truncate font-medium">{subjectName(x.subject)} <span className="text-xs text-muted-foreground">{x.subject.code}</span></p>
                    <p className="truncate text-sm text-muted-foreground">{x.teacher.name} · {t('education.lessonsPerWeek', { count: x.periods })}</p>
                  </div>
                  <div className="flex shrink-0 gap-1">
                    {data.canManage && <Button variant="ghost" size="icon-sm" aria-label={t('education.changeTeacherOf', { subject: subjectName(x.subject) })} onClick={() => setOpen({ kind: 'course', course: x })}><Pencil className="size-4" /></Button>}
                    {data.canManage && <Button variant="ghost" size="icon-sm" aria-label={t('education.removeCourse', { subject: subjectName(x.subject) })}
                      onClick={() => act(() => removeCourse.mutateAsync(x.id), t('education.courseRemoved'))}><Trash2 className="size-4" /></Button>}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Panel>
      </div>
      {open?.kind === 'edit' && <ClassDialog org={slug} cls={c} onClose={() => setOpen(null)} />}
      {open?.kind === 'enroll' && <EnrollDialog org={slug} classId={c.id} className={c.name} onClose={() => setOpen(null)} />}
      {open?.kind === 'course' && <CourseDialog org={slug} classId={c.id} course={open.course} taken={data.courses.map((x) => x.subject.id)} onClose={() => setOpen(null)} />}
    </>
  );
}
