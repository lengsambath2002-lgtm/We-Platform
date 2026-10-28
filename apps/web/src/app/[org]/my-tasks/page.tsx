'use client';

import { CheckSquare } from 'lucide-react';
import { useRouter, useSearchParams } from 'next/navigation';
import { differenceInCalendarDays, parseISO } from 'date-fns';
import { Suspense, useMemo } from 'react';

import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { TaskRow } from '@/components/tasks/task-row';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useT } from '@/i18n/provider';
import { useMyTasks } from '@/lib/api/queries';
import type { Task } from '@/lib/api/types';
import { useOrg } from '@/lib/org-context';

type Filter = 'open' | 'overdue' | 'week' | 'done';

type Group = 'groupOverdue' | 'groupToday' | 'groupWeek' | 'groupLater' | 'groupNoDue';

function groups(tasks: Task[]) {
  const g: Record<Group, Task[]> = { groupOverdue: [], groupToday: [], groupWeek: [], groupLater: [], groupNoDue: [] };
  for (const t of tasks) {
    if (!t.dueDate) g.groupNoDue.push(t);
    else {
      const d = differenceInCalendarDays(parseISO(t.dueDate), new Date());
      (d < 0 ? g.groupOverdue : d === 0 ? g.groupToday : d <= 7 ? g.groupWeek : g.groupLater).push(t);
    }
  }
  return (Object.entries(g) as [Group, Task[]][]).filter(([, v]) => v.length);
}

function MyTasks() {
  const { t } = useT();
  const { slug } = useOrg();
  const router = useRouter();
  const filter = (useSearchParams().get('filter') as Filter) || 'open';
  const { data = [], error, isPending, refetch } = useMyTasks(slug);
  const shown = useMemo(() => {
    const open = data.filter((t) => t.status !== 'DONE');
    if (filter === 'done') return data.filter((t) => t.status === 'DONE');
    if (filter === 'overdue') return open.filter((t) => t.dueDate && differenceInCalendarDays(parseISO(t.dueDate), new Date()) < 0);
    if (filter === 'week') return open.filter((t) => { if (!t.dueDate) return false; const d = differenceInCalendarDays(parseISO(t.dueDate), new Date()); return d >= 0 && d <= 7; });
    return open;
  }, [data, filter]);
  const counts = { open: data.filter((t) => t.status !== 'DONE').length, done: data.filter((t) => t.status === 'DONE').length };

  return (
    <>
      <PageHeader title={t('tasks.myTasks')} subtitle={t('tasks.myTasksSubtitle', counts)} />
      <Tabs value={filter} onValueChange={(v) => router.replace(`/${slug}/my-tasks${v === 'open' ? '' : `?filter=${v}`}`)} className="mb-4">
        <TabsList>
          <TabsTrigger value="open">{t('tasks.tabOpen')}</TabsTrigger>
          <TabsTrigger value="overdue">{t('tasks.tabOverdue')}</TabsTrigger>
          <TabsTrigger value="week">{t('tasks.tabWeek')}</TabsTrigger>
          <TabsTrigger value="done">{t('tasks.tabDone')}</TabsTrigger>
        </TabsList>
      </Tabs>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton /> : shown.length === 0 ? (
        <EmptyState icon={CheckSquare} title={filter === 'done' ? t('tasks.nothingDone') : t('tasks.nothingHere')}
          body={filter === 'open' ? t('tasks.openEmptyBody') : t('tasks.filterEmptyBody')} />
      ) : filter === 'open' ? (
        <div className="space-y-5">
          {groups(shown).map(([name, list]) => (
            <section key={name} aria-label={t(`tasks.${name}`)}>
              <h2 className={`mb-2 text-xs font-semibold uppercase tracking-wider ${name === 'groupOverdue' ? 'text-destructive' : 'text-muted-foreground'}`}>{t(`tasks.${name}`)} · {list.length}</h2>
              <div className="overflow-hidden rounded-lg border bg-card">{list.map((t) => <TaskRow key={t.id} org={slug} task={t} />)}</div>
            </section>
          ))}
        </div>
      ) : (
        <div className="overflow-hidden rounded-lg border bg-card">{shown.map((t) => <TaskRow key={t.id} org={slug} task={t} />)}</div>
      )}
    </>
  );
}

export default function Page() {
  return <Suspense><MyTasks /></Suspense>;
}
