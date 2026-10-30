'use client';

import { Pencil, Presentation, UserPlus } from 'lucide-react';
import { useState } from 'react';

import { PersonAvatar } from '@/components/common/person';
import { TeacherDialog } from '@/components/education/people-dialogs';
import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { useT } from '@/i18n/provider';
import { type Teacher, useTeachers } from '@/lib/api/education';
import { useOrg } from '@/lib/org-context';
import { cn } from '@/lib/utils';

/** Teachers: members with a teacher profile, their homeroom classes and how many courses they teach this year. */
export default function Teachers() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const { data, error, isPending, refetch } = useTeachers(slug);
  const [editing, setEditing] = useState<Teacher | 'new' | null>(null);
  const manage = can('education.manage');

  return (
    <>
      <PageHeader title={t('education.teachersTitle')} subtitle={t('education.teachersSubtitle')}
        actions={manage && <Button size="sm" onClick={() => setEditing('new')}><UserPlus className="size-4" />{t('education.addTeacher')}</Button>} />
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={4} />
        : data.length === 0 ? <EmptyState icon={Presentation} title={t('education.noTeachersTitle')} body={t('education.noTeachersBody')}
          action={manage && <Button size="sm" variant="outline" onClick={() => setEditing('new')}>{t('education.addTeacher')}</Button>} />
          : (
            <ul className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
              {data.map((x) => (
                <li key={x.id} className={cn('flex items-start gap-3 rounded-lg border bg-card p-4', !x.active && 'opacity-60')}>
                  <PersonAvatar person={{ name: x.name, avatarUrl: x.avatarUrl }} className="size-9" />
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-medium">{x.name}{!x.active && <span className="ml-2 text-xs font-normal text-muted-foreground">{t('education.notTeaching')}</span>}</p>
                    <p className="truncate text-sm text-muted-foreground">{[x.specialty, x.phone].filter(Boolean).join(' · ') || x.email}</p>
                    <p className="mt-1 text-xs text-muted-foreground">
                      {x.homerooms.length ? t('education.homeroomOf', { classes: x.homerooms.join(', ') }) : t('education.noHomeroomClass')} · {t('education.coursesCount', { count: x.courses })}
                    </p>
                  </div>
                  {manage && <Button variant="ghost" size="icon-sm" aria-label={t('education.editName', { name: x.name })} onClick={() => setEditing(x)}><Pencil className="size-4" /></Button>}
                </li>
              ))}
            </ul>
          )}
      {editing && <TeacherDialog org={slug} teacher={editing === 'new' ? undefined : editing} teachers={data ?? []} onClose={() => setEditing(null)} />}
    </>
  );
}
