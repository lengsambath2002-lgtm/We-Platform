'use client';

import { ChevronRight, Columns3, List, Lock, Plus, Search, Settings, Users } from 'lucide-react';
import Link from 'next/link';
import { useParams, useRouter, useSearchParams } from 'next/navigation';
import { Suspense, useMemo, useState } from 'react';

import { PRIORITIES, PRIORITY_LABEL } from '@/components/common/badges';
import { ErrorState, ListSkeleton } from '@/components/common/states';
import { Board } from '@/components/projects/board';
import { ProjectMembers } from '@/components/projects/project-members';
import { ProjectSettings } from '@/components/projects/project-settings';
import { NewTaskDialog } from '@/components/tasks/new-task-dialog';
import { TaskRow } from '@/components/tasks/task-row';
import { useAssignees } from '@/components/tasks/use-assignees';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Progress } from '@/components/ui/progress';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { EmptyState } from '@/components/common/states';
import { useProject, useTasks } from '@/lib/api/queries';
import type { TaskStatus } from '@/lib/api/types';
import { useOrg } from '@/lib/org-context';

const VIEWS = ['board', 'list', 'members', 'settings'] as const;
type View = (typeof VIEWS)[number];

function ProjectPage() {
  const { slug } = useOrg();
  const { key } = useParams<{ key: string }>();
  const router = useRouter();
  const params = useSearchParams();
  const view = (VIEWS as readonly string[]).includes(params.get('view') ?? '') ? (params.get('view') as View) : 'board';
  const project = useProject(slug, key);
  const tasks = useTasks(slug, key);
  const assignees = useAssignees(slug, project.data);
  const [newIn, setNewIn] = useState<TaskStatus | null>(null);
  const [q, setQ] = useState('');
  const [assignee, setAssignee] = useState('all');
  const [priority, setPriority] = useState('all');

  const shown = useMemo(() => (tasks.data ?? []).filter((t) => !t.parentId
    && (!q || `${t.key} ${t.title}`.toLowerCase().includes(q.toLowerCase()))
    && (assignee === 'all' || (assignee === 'none' ? !t.assignee : t.assignee?.id === assignee))
    && (priority === 'all' || t.priority === priority)), [tasks.data, q, assignee, priority]);

  if (project.error) return <ErrorState error={project.error} retry={() => project.refetch()} />;
  if (project.isPending) return <ListSkeleton />;
  const p = project.data;
  const setView = (v: string) => router.replace(`/${slug}/projects/${key}${v === 'board' ? '' : `?view=${v}`}`, { scroll: false });
  const filtered = q || assignee !== 'all' || priority !== 'all';

  return (
    <>
      <nav aria-label="Breadcrumb" className="mb-2 flex items-center gap-1 text-sm text-muted-foreground">
        <Link href={`/${slug}/projects`} className="hover:text-foreground">Projects</Link>
        <ChevronRight className="size-3.5" /><span className="font-mono text-xs">{p.key}</span>
      </nav>
      <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h1 className="flex items-center gap-2 text-xl font-semibold tracking-tight">
            <span className="truncate">{p.name}</span>
            {p.visibility === 'PRIVATE' && <Lock className="size-4 text-muted-foreground" aria-label="Private project" />}
          </h1>
          {p.description && <p className="mt-1 line-clamp-2 max-w-2xl text-sm text-muted-foreground">{p.description}</p>}
          <div className="mt-2 flex items-center gap-2 text-xs text-muted-foreground">
            <Progress value={p.progress} className="h-1.5 w-32" /><span className="tabular">{p.progress}% done</span>
            {p.lead && <span>· Lead {p.lead.name}</span>}
          </div>
        </div>
        {p.canEdit && <Button size="sm" onClick={() => setNewIn('TODO')}><Plus className="size-4" />New task</Button>}
      </div>

      <Tabs value={view} onValueChange={setView}>
        <TabsList className="mb-4">
          <TabsTrigger value="board"><Columns3 className="size-4" />Board</TabsTrigger>
          <TabsTrigger value="list"><List className="size-4" />List</TabsTrigger>
          <TabsTrigger value="members"><Users className="size-4" />Members</TabsTrigger>
          <TabsTrigger value="settings"><Settings className="size-4" />Settings</TabsTrigger>
        </TabsList>

        {(view === 'board' || view === 'list') && (
          <div className="mb-4 flex flex-wrap gap-2">
            <div className="relative w-full sm:w-64">
              <Search className="pointer-events-none absolute left-2.5 top-2.5 size-4 text-muted-foreground" />
              <Input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search tasks" className="pl-8" aria-label="Search tasks" />
            </div>
            <Select value={assignee} onValueChange={setAssignee}>
              <SelectTrigger className="w-44" aria-label="Assignee"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="all">Anyone</SelectItem><SelectItem value="none">Unassigned</SelectItem>
                {assignees.map((a) => <SelectItem key={a.id} value={a.id}>{a.name}</SelectItem>)}
              </SelectContent>
            </Select>
            <Select value={priority} onValueChange={setPriority}>
              <SelectTrigger className="w-36" aria-label="Priority"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="all">Any priority</SelectItem>
                {PRIORITIES.map((x) => <SelectItem key={x} value={x}>{PRIORITY_LABEL[x]}</SelectItem>)}
              </SelectContent>
            </Select>
            {filtered && <Button variant="ghost" onClick={() => { setQ(''); setAssignee('all'); setPriority('all'); }}>Clear</Button>}
          </div>
        )}

        <TabsContent value="board">
          {tasks.error ? <ErrorState error={tasks.error} retry={() => tasks.refetch()} /> : tasks.isPending ? <ListSkeleton /> : (
            <Board org={slug} project={p} tasks={shown} onAdd={setNewIn} />
          )}
        </TabsContent>
        <TabsContent value="list">
          {tasks.error ? <ErrorState error={tasks.error} retry={() => tasks.refetch()} /> : tasks.isPending ? <ListSkeleton /> : shown.length === 0 ? (
            filtered ? <EmptyState icon={Search} title="No tasks match" body="Try another search or filter." />
              : <EmptyState icon={List} title="No tasks yet" body="Tasks you add to this project appear here."
                  action={p.canEdit ? <Button size="sm" onClick={() => setNewIn('TODO')}>Add the first task</Button> : undefined} />
          ) : (
            <div className="overflow-hidden rounded-lg border bg-card">{shown.map((t) => <TaskRow key={t.id} org={slug} task={t} showProject={false} />)}</div>
          )}
        </TabsContent>
        <TabsContent value="members"><ProjectMembers org={slug} project={p} /></TabsContent>
        <TabsContent value="settings"><ProjectSettings org={slug} project={p} /></TabsContent>
      </Tabs>
      {newIn && <NewTaskDialog org={slug} project={p} open onOpenChange={(v) => !v && setNewIn(null)} status={newIn} />}
    </>
  );
}

export default function Page() {
  return <Suspense><ProjectPage /></Suspense>;
}
