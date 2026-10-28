'use client';

import { AlertTriangle, BadgeCheck, CalendarDays, CheckSquare, Clock, FolderKanban, Plus, UserPlus } from 'lucide-react';
import Link from 'next/link';
import type { ReactNode } from 'react';

import { ApprovalBadge } from '@/components/common/badges';
import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState } from '@/components/common/states';
import { ActivityTimeline } from '@/components/tasks/activity';
import { DueDate, TaskRow } from '@/components/tasks/task-row';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Progress } from '@/components/ui/progress';
import { Skeleton } from '@/components/ui/skeleton';
import { useOverview } from '@/lib/api/queries';
import { useSession } from '@/lib/auth/session';
import { ago } from '@/lib/format';
import { useOrg } from '@/lib/org-context';
import { cn } from '@/lib/utils';
import { format, parseISO } from 'date-fns';

function Stat({ href, label, value, icon: Icon, tone }: { href: string; label: string; value: number; icon: typeof Clock; tone?: string }) {
  return (
    <Link href={href} className="rounded-lg border bg-card p-4 transition-colors hover:border-primary/40">
      <div className="flex items-center justify-between text-sm text-muted-foreground">{label}<Icon className={cn('size-4', tone)} aria-hidden /></div>
      <p className={cn('mt-2 text-2xl font-semibold tabular', value > 0 && tone)}>{value}</p>
    </Link>
  );
}

function Panel({ title, action, children, className }: { title: string; action?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <Card className={cn('gap-0 py-0', className)}>
      <CardHeader className="flex flex-row items-center justify-between border-b px-4 py-3">
        <CardTitle className="text-sm font-semibold">{title}</CardTitle>
        {action}
      </CardHeader>
      <CardContent className="p-0">{children}</CardContent>
    </Card>
  );
}

export default function OverviewPage() {
  const { slug, can } = useOrg();
  const { user } = useSession();
  const { data, error, isPending, refetch } = useOverview(slug);
  const first = user?.name.split(' ')[0];

  return (
    <>
      <PageHeader title={`Good ${new Date().getHours() < 12 ? 'morning' : new Date().getHours() < 18 ? 'afternoon' : 'evening'}${first ? `, ${first}` : ''}`}
        subtitle="Here's what needs your attention."
        actions={<>
          {can('org.members.manage') && <Button variant="outline" size="sm" asChild><Link href={`/${slug}/teams?invite=1`}><UserPlus className="size-4" />Invite</Link></Button>}
          {can('projects.create') && <Button size="sm" asChild><Link href={`/${slug}/projects?new=1`}><Plus className="size-4" />New project</Link></Button>}
        </>} />
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? (
        <div className="grid gap-4"><div className="grid grid-cols-2 gap-3 lg:grid-cols-4">{[0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-24" />)}</div><Skeleton className="h-80" /></div>
      ) : (
        <div className="space-y-6">
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <Stat href={`/${slug}/my-tasks`} label="My open tasks" value={data.openTasks} icon={CheckSquare} />
            <Stat href={`/${slug}/my-tasks?filter=overdue`} label="Overdue" value={data.overdueTasks} icon={AlertTriangle} tone="text-destructive" />
            <Stat href={`/${slug}/my-tasks?filter=week`} label="Due this week" value={data.dueThisWeek} icon={Clock} tone="text-warning" />
            <Stat href={`/${slug}/approvals`} label="Waiting for my approval" value={data.pendingApprovals} icon={BadgeCheck} tone="text-primary" />
          </div>

          <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_380px]">
            <div className="space-y-6">
              <Panel title="My tasks" action={<Link href={`/${slug}/my-tasks`} className="text-xs font-medium text-primary hover:underline">View all</Link>}>
                {!data.projectsEnabled ? <EmptyState className="m-4" icon={FolderKanban} title="Projects is switched off" body="An admin can switch it on in Modules." />
                  : data.myTasks.length === 0 ? <EmptyState className="m-4 border-0" icon={CheckSquare} title="Nothing assigned to you" body="Tasks assigned to you in any project show up here." />
                  : <div>{data.myTasks.map((t) => <TaskRow key={t.id} org={slug} task={t} />)}</div>}
              </Panel>
              <Panel title="Project progress" action={<Link href={`/${slug}/projects`} className="text-xs font-medium text-primary hover:underline">All projects</Link>}>
                {data.projects.length === 0 ? (
                  <EmptyState className="m-4 border-0" icon={FolderKanban} title="No active projects" body="Projects you can see will show their progress here."
                    action={can('projects.create') ? <Button size="sm" asChild><Link href={`/${slug}/projects?new=1`}>Create a project</Link></Button> : undefined} />
                ) : (
                  <ul className="divide-y">
                    {data.projects.map((p) => (
                      <li key={p.key}>
                        <Link href={`/${slug}/projects/${p.key}`} className="grid grid-cols-[1fr_auto] items-center gap-x-4 gap-y-1.5 px-4 py-3 hover:bg-muted/50 sm:grid-cols-[minmax(0,1fr)_160px_90px]">
                          <span className="min-w-0"><span className="block truncate text-sm font-medium">{p.name}</span><span className="text-xs text-muted-foreground">{p.open} open of {p.total}</span></span>
                          <span className="col-span-2 flex items-center gap-2 sm:col-span-1"><Progress value={p.progress} className="h-1.5" aria-label={`${p.name} ${p.progress}% done`} /><span className="w-9 text-right text-xs tabular text-muted-foreground">{p.progress}%</span></span>
                          <span className="hidden justify-end sm:flex">{p.dueDate ? <DueDate date={p.dueDate} /> : <span className="text-xs text-muted-foreground">No due date</span>}</span>
                        </Link>
                      </li>
                    ))}
                  </ul>
                )}
              </Panel>
            </div>
            <div className="space-y-6">
              <Panel title="Waiting for my approval" action={<Link href={`/${slug}/approvals`} className="text-xs font-medium text-primary hover:underline">Approvals</Link>}>
                {data.approvals.length === 0 ? <p className="px-4 py-6 text-sm text-muted-foreground">Nothing waiting for you.</p> : (
                  <ul className="divide-y">
                    {data.approvals.map((a) => (
                      <li key={a.id}><Link href={`/${slug}/approvals/${a.id}`} className="block px-4 py-3 hover:bg-muted/50">
                        <span className="block truncate text-sm font-medium">{a.title}</span>
                        <span className="mt-1 flex items-center gap-2 text-xs text-muted-foreground"><ApprovalBadge status={a.status} />{a.requester.name} · {ago(a.updatedAt)}</span>
                      </Link></li>
                    ))}
                  </ul>
                )}
              </Panel>
              <Panel title="Coming up" action={<Link href={`/${slug}/calendar`} className="text-xs font-medium text-primary hover:underline">Calendar</Link>}>
                {data.deadlines.length === 0 && data.events.length === 0 ? <p className="px-4 py-6 text-sm text-muted-foreground">No deadlines or events in the next days.</p> : (
                  <ul className="divide-y">
                    {data.events.map((e) => (
                      <li key={e.id} className="flex items-center gap-3 px-4 py-2.5">
                        <CalendarDays className="size-4 shrink-0 text-info" aria-hidden />
                        <span className="min-w-0 flex-1 truncate text-sm">{e.title}</span>
                        <span className="text-xs tabular text-muted-foreground">{format(parseISO(e.startsAt), e.allDay ? 'EEE d MMM' : 'EEE d MMM, HH:mm')}</span>
                      </li>
                    ))}
                    {data.deadlines.map((t) => (
                      <li key={t.id}><Link href={`/${slug}/tasks/${t.id}`} className="flex items-center gap-3 px-4 py-2.5 hover:bg-muted/50">
                        <CheckSquare className="size-4 shrink-0 text-muted-foreground" aria-hidden />
                        <span className="min-w-0 flex-1 truncate text-sm">{t.title}</span>
                        <DueDate date={t.dueDate} />
                      </Link></li>
                    ))}
                  </ul>
                )}
              </Panel>
              <Panel title="Recent activity"><div className="px-4 py-4 pl-8"><ActivityTimeline items={data.activity} empty="Activity in your projects shows up here." /></div></Panel>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
