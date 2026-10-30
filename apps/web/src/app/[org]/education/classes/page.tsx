'use client';

import { Plus, School, Settings2 } from 'lucide-react';
import Link from 'next/link';
import { useState } from 'react';

import { ClassDialog } from '@/components/education/class-dialogs';
import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { useT } from '@/i18n/provider';
import { useClasses, useYears } from '@/lib/api/education';
import { useOrg } from '@/lib/org-context';

/** Classes of an academic year (the current one first): admins all of them, teachers the ones they teach. */
export default function Classes() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const years = useYears(slug);
  const [yearId, setYearId] = useState('');
  const current = years.data?.find((y) => y.current);
  const year = yearId || current?.id || '';
  const { data, error, isPending, refetch } = useClasses(slug, year || undefined);
  const [adding, setAdding] = useState(false);
  const manage = can('education.manage');
  const base = `/${slug}/education`;

  return (
    <>
      <PageHeader title={t('education.classesTitle')} subtitle={data ? t('education.count', { count: data.length }) : undefined}
        actions={manage && year && <Button size="sm" onClick={() => setAdding(true)}><Plus className="size-4" />{t('education.newClass')}</Button>} />
      {(years.data?.length ?? 0) > 1 && (
        <div className="mb-4 space-y-1">
          <Label htmlFor="c-y" className="text-xs text-muted-foreground">{t('education.academicYear')}</Label>
          <Select value={year} onValueChange={setYearId}>
            <SelectTrigger id="c-y" className="w-48"><SelectValue /></SelectTrigger>
            <SelectContent>{years.data!.map((y) => <SelectItem key={y.id} value={y.id}>{y.name}</SelectItem>)}</SelectContent>
          </Select>
        </div>
      )}
      {error || years.error ? <ErrorState error={error ?? years.error} retry={() => { refetch(); years.refetch(); }} /> : isPending || years.isPending ? <ListSkeleton rows={5} />
        : !year ? <EmptyState icon={Settings2} title={t('education.noYearTitle')} body={manage ? t('education.noYearBody') : t('education.noYearTeacher')}
          action={manage && <Button asChild size="sm"><Link href={`${base}/settings`}>{t('education.setUpYear')}</Link></Button>} />
          : data.length === 0 ? <EmptyState icon={School} title={t('education.noClassesTitle')} body={manage ? t('education.noClassesBody') : t('education.noClassesTeacher')}
            action={manage && <Button size="sm" variant="outline" onClick={() => setAdding(true)}>{t('education.newClass')}</Button>} />
            : (
              <ul className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
                {data.map((c) => (
                  <li key={c.id}>
                    <Link href={`${base}/classes/${c.id}`} className="block rounded-lg border bg-card p-4 hover:bg-muted/40">
                      <div className="flex items-baseline justify-between gap-2">
                        <p className="text-lg font-semibold">{c.name}</p>
                        {c.mine && <span className="rounded-full bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary">{t('education.mine')}</span>}
                      </div>
                      <p className="text-sm text-muted-foreground">{[c.gradeName, c.room && t('education.roomName', { room: c.room })].filter(Boolean).join(' · ')}</p>
                      <p className="mt-2 text-sm">{c.homeroom ? t('education.homeroomIs', { name: c.homeroom.name }) : t('education.noHomeroom')}</p>
                      <p className="mt-1 text-xs text-muted-foreground tabular">
                        {c.capacity ? t('education.studentsOfCapacity', { count: c.students, capacity: c.capacity }) : t('education.studentsCount', { count: c.students })} · {t('education.coursesCount', { count: c.courses })}
                      </p>
                    </Link>
                  </li>
                ))}
              </ul>
            )}
      {adding && <ClassDialog org={slug} yearId={year} onClose={() => setAdding(false)} />}
    </>
  );
}
