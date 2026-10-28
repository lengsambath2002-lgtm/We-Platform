'use client';

import { CheckSquare } from 'lucide-react';
import { useRouter, useSearchParams } from 'next/navigation';
import { Suspense, useMemo } from 'react';
import { differenceInCalendarDays, parseISO } from 'date-fns';

import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { TaskRow } from '@/components/tasks/task-row';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useMyTasks } from '@/lib/api/queries';
import type { Task } from '@/lib/api/types';
import { useOrg } from '@/lib/org-context';

type Filter = 'open' | 'overdue' | 'week' | 'done';

function groups(tasks: Task[]) {
  const g: Record<string, Task[]> = { Overdue: [], Today: [], 'This week': [], Later: [], 'No due date': [] };
  for (const t of tasks) {
    if (!t.dueDate) g['No due date']!.push(t);
    else {
      const d = differenceInCalendarDays(parseISO(t.dueDate), new Date());
      (d < 0 ? g.Overdue : d === 0 ? g.Today : d <= 7 ? g['This week'] : g.Later)!.push(t);
    }
  }
  return Object.entries(g).filter(([, v]) => v.length);
}

function MyTasks() {
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
      <PageHeader title="My Tasks" subtitle={`${counts.open} open · ${counts.done} done`} />
      <Tabs value={filter} onValueChange={(v) => router.replace(`/${slug}/my-tasks${v === 'open' ? '' : `?filter=${v}`}`)} className="mb-4">
        <TabsList>
          <TabsTrigger value="open">Open</TabsTrigger>
          <TabsTrigger value="overdue">Overdue</TabsTrigger>
          <TabsTrigger value="week">This week</TabsTrigger>
          <TabsTrigger value="done">Done</TabsTrigger>
        </TabsList>
      </Tabs>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton /> : shown.length === 0 ? (
        <EmptyState icon={CheckSquare} title={filter === 'done' ? 'Nothing done yet' : 'Nothing here'}
          body={filter === 'open' ? 'Tasks assigned to you in any project show up here.' : 'No tasks match this filter.'} />
      ) : filter === 'open' ? (
        <div className="space-y-5">
          {groups(shown).map(([name, list]) => (
            <section key={name} aria-label={name}>
              <h2 className={`mb-2 text-xs font-semibold uppercase tracking-wider ${name === 'Overdue' ? 'text-destructive' : 'text-muted-foreground'}`}>{name} · {list.length}</h2>
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
