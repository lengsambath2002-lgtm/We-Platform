'use client';

import { parseISO } from 'date-fns';
import { AlertTriangle, ArrowRight, BadgeCheck, CalendarDays, CheckSquare, Clock, FolderKanban, Plus, UserPlus } from 'lucide-react';
import Link from 'next/link';
import type { ReactNode } from 'react';

import { ApprovalBadge } from '@/components/common/badges';
import { PageHeader } from '@/components/common/page-header';
import { PersonAvatar } from '@/components/common/person';
import { VirtualBizMark } from '@/components/common/virtualbiz-mark';
import { MODULE_ICON, moduleHref, moduleName } from '@/components/shell/nav';
import { EmptyState, ErrorState } from '@/components/common/states';
import { ActivityTimeline } from '@/components/tasks/activity';
import { DueDate, TaskRow } from '@/components/tasks/task-row';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Progress } from '@/components/ui/progress';
import { Skeleton } from '@/components/ui/skeleton';
import { useT } from '@/i18n/provider';
import { useAbsences } from '@/lib/api/hr';
import { useMembers, useOverview } from '@/lib/api/queries';
import { useSession } from '@/lib/auth/session';
import { ago, day, fmt } from '@/lib/format';
import { useOrg } from '@/lib/org-context';
import { cn } from '@/lib/utils';

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

/** A quiet one-line "nothing here" inside a panel, instead of a big empty box. */
function Quiet({ icon: Icon, children, action }: { icon: typeof Clock; children: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex flex-wrap items-center gap-3 px-4 py-5 text-sm text-muted-foreground">
      <span className="grid size-8 shrink-0 place-items-center rounded-full bg-muted"><Icon className="size-4" aria-hidden /></span>
      <span className="min-w-0 flex-1">{children}</span>
      {action}
    </div>
  );
}

const linkClass = 'text-xs font-medium text-primary hover:underline';
const isoDay = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

/** The apps this organisation uses, each a door to its home. */
function Apps({ slug }: { slug: string }) {
  const { modules } = useOrg();
  const { t } = useT();
  const apps = modules.filter((m) => m.kind === 'INDUSTRY' && m.enabled);
  if (apps.length === 0) return null;
  return (
    <Panel title={t('overview.yourApps')}>
      <ul className="grid gap-px bg-border sm:grid-cols-2">
        {apps.map((m, n) => {
          const Icon = MODULE_ICON[m.key] ?? MODULE_ICON.business;
          return (
            <li key={m.key} className={cn('bg-card', n === apps.length - 1 && apps.length % 2 === 1 && 'sm:col-span-2')}>
              <Link href={`/${slug}/${moduleHref(m.key)}`} className="group flex items-center gap-3 px-4 py-3.5 hover:bg-muted/50">
                {Icon === VirtualBizMark ? <VirtualBizMark className="size-9 shrink-0" aria-hidden />
                  : <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-primary/10 text-primary"><Icon className="size-[18px]" aria-hidden /></span>}
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-medium">{moduleName(m)}</span>
                  <span className="block truncate text-xs text-muted-foreground">{t(`overview.app_${m.key}` as 'overview.app_business')}</span>
                </span>
                <ArrowRight className="size-4 shrink-0 text-muted-foreground opacity-0 transition-opacity group-hover:opacity-100" aria-hidden />
              </Link>
            </li>
          );
        })}
      </ul>
    </Panel>
  );
}

/** Who is on leave today (with HR). */
function OutToday({ slug }: { slug: string }) {
  const { t } = useT();
  const today = isoDay(new Date());
  const { data } = useAbsences(slug, today, today);
  return (
    <Panel title={t('overview.outToday')} action={<Link href={`/${slug}/calendar`} className={linkClass}>{t('overview.calendar')}</Link>}>
      {!data ? <div className="p-4"><Skeleton className="h-8" /></div> : data.length === 0 ? <p className="px-4 py-5 text-sm text-muted-foreground">{t('overview.everyoneIn')}</p> : (
        <ul className="divide-y">
          {data.slice(0, 6).map((a) => (
            <li key={a.leaveId} className="flex items-center gap-3 px-4 py-2.5">
              <PersonAvatar person={a.person} className="size-7" />
              <span className="min-w-0 flex-1 truncate text-sm">{a.person.name}</span>
              <span className="text-xs text-muted-foreground tabular">{a.endDate === today ? t('overview.backTomorrow') : t('overview.backAfter', { date: day(a.endDate) })}</span>
            </li>
          ))}
          {data.length > 6 && <li className="px-4 py-2 text-xs text-muted-foreground">{t('overview.andMore', { count: data.length - 6 })}</li>}
        </ul>
      )}
    </Panel>
  );
}

/** People who joined in the last 30 days. */
function NewPeople({ slug }: { slug: string }) {
  const { t } = useT();
  const { data } = useMembers(slug);
  const since = Date.now() - 30 * 86_400_000;
  const fresh = (data ?? []).filter((m) => m.status === 'ACTIVE' && new Date(m.joinedAt).getTime() >= since)
    .sort((a, b) => b.joinedAt.localeCompare(a.joinedAt));
  if (!data || fresh.length === 0) return null;
  return (
    <Panel title={t('overview.newPeople')} action={<Link href={`/${slug}/teams`} className={linkClass}>{t('overview.people')}</Link>}>
      <div className="px-4 py-3.5">
        <div className="flex -space-x-2">
          {fresh.slice(0, 8).map((m) => <PersonAvatar key={m.membershipId} person={m} className="size-8 ring-2 ring-card" />)}
        </div>
        <p className="mt-2.5 text-sm text-muted-foreground">
          {fresh.length > 2
            ? t('overview.joinedRecently', { names: fresh.slice(0, 2).map((m) => m.name).join(', '), count: fresh.length - 2 })
            : t('overview.joinedOnly', { names: fresh.map((m) => m.name).join(', ') })}
        </p>
      </div>
    </Panel>
  );
}

export default function OverviewPage() {
  const { slug, can, modules } = useOrg();
  const { user } = useSession();
  const hrOn = modules.some((m) => m.key === 'hr' && m.enabled);
  const { data, error, isPending, refetch } = useOverview(slug);
  const { t } = useT();
  const first = user?.name.split(' ')[0];
  const hour = new Date().getHours();
  const part = hour < 12 ? 'morning' : hour < 18 ? 'afternoon' : 'evening';

  return (
    <>
      <PageHeader title={first ? t(`overview.${part}Name`, { name: first }) : t(`overview.${part}`)}
        subtitle={t('overview.subtitle')}
        actions={<>
          {can('org.members.manage') && <Button variant="outline" size="sm" asChild><Link href={`/${slug}/teams?invite=1`}><UserPlus className="size-4" />{t('overview.invite')}</Link></Button>}
          {can('projects.create') && <Button size="sm" asChild><Link href={`/${slug}/projects?new=1`}><Plus className="size-4" />{t('overview.newProject')}</Link></Button>}
        </>} />
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? (
        <div className="grid gap-4"><div className="grid grid-cols-2 gap-3 lg:grid-cols-4">{[0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-24" />)}</div><Skeleton className="h-80" /></div>
      ) : (
        <div className="space-y-6">
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <Stat href={`/${slug}/my-tasks`} label={t('overview.statOpenTasks')} value={data.openTasks} icon={CheckSquare} />
            <Stat href={`/${slug}/my-tasks?filter=overdue`} label={t('overview.statOverdue')} value={data.overdueTasks} icon={AlertTriangle} tone="text-destructive" />
            <Stat href={`/${slug}/my-tasks?filter=week`} label={t('overview.statDueThisWeek')} value={data.dueThisWeek} icon={Clock} tone="text-warning" />
            <Stat href={`/${slug}/approvals`} label={t('overview.statWaitingApproval')} value={data.pendingApprovals} icon={BadgeCheck} tone="text-primary" />
          </div>

          <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_380px]">
            <div className="space-y-6">
              <Panel title={t('overview.myTasks')} action={<Link href={`/${slug}/my-tasks`} className="text-xs font-medium text-primary hover:underline">{t('overview.viewAll')}</Link>}>
                {!data.projectsEnabled ? <EmptyState className="m-4" icon={FolderKanban} title={t('overview.projectsOff')} body={t('overview.projectsOffBody')} />
                  : data.myTasks.length === 0 ? <Quiet icon={CheckSquare}><span className="block font-medium text-foreground">{t('overview.nothingAssigned')}</span>{t('overview.nothingAssignedBody')}</Quiet>
                  : <div>{data.myTasks.map((t) => <TaskRow key={t.id} org={slug} task={t} />)}</div>}
              </Panel>
              <Apps slug={slug} />
              <Panel title={t('overview.projectProgress')} action={<Link href={`/${slug}/projects`} className="text-xs font-medium text-primary hover:underline">{t('overview.allProjects')}</Link>}>
                {data.projects.length === 0 ? (
                  <Quiet icon={FolderKanban}
                    action={can('projects.create') ? <Button size="sm" variant="outline" asChild><Link href={`/${slug}/projects?new=1`}>{t('overview.createProject')}</Link></Button> : undefined}>
                    <span className="block font-medium text-foreground">{t('overview.noProjects')}</span>{t('overview.noProjectsBody')}
                  </Quiet>
                ) : (
                  <ul className="divide-y">
                    {data.projects.map((p) => (
                      <li key={p.key}>
                        <Link href={`/${slug}/projects/${p.key}`} className="grid grid-cols-[1fr_auto] items-center gap-x-4 gap-y-1.5 px-4 py-3 hover:bg-muted/50 sm:grid-cols-[minmax(0,1fr)_160px_90px]">
                          <span className="min-w-0"><span className="block truncate text-sm font-medium">{p.name}</span><span className="text-xs text-muted-foreground">{t('overview.openOf', { open: p.open, total: p.total })}</span></span>
                          <span className="col-span-2 flex items-center gap-2 sm:col-span-1"><Progress value={p.progress} className="h-1.5" aria-label={t('overview.percentDone', { name: p.name, progress: p.progress })} /><span className="w-9 text-right text-xs tabular text-muted-foreground">{p.progress}%</span></span>
                          <span className="hidden justify-end sm:flex">{p.dueDate ? <DueDate date={p.dueDate} /> : <span className="text-xs text-muted-foreground">{t('overview.noDueDate')}</span>}</span>
                        </Link>
                      </li>
                    ))}
                  </ul>
                )}
              </Panel>
            </div>
            <div className="space-y-6">
              <Panel title={t('overview.waitingApproval')} action={<Link href={`/${slug}/approvals`} className="text-xs font-medium text-primary hover:underline">{t('overview.approvals')}</Link>}>
                {data.approvals.length === 0 ? <p className="px-4 py-6 text-sm text-muted-foreground">{t('overview.nothingWaiting')}</p> : (
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
              {hrOn && <OutToday slug={slug} />}
              <NewPeople slug={slug} />
              <Panel title={t('overview.comingUp')} action={<Link href={`/${slug}/calendar`} className="text-xs font-medium text-primary hover:underline">{t('overview.calendar')}</Link>}>
                {data.deadlines.length === 0 && data.events.length === 0 ? <p className="px-4 py-6 text-sm text-muted-foreground">{t('overview.nothingComing')}</p> : (
                  <ul className="divide-y">
                    {data.events.map((e) => (
                      <li key={e.id} className="flex items-center gap-3 px-4 py-2.5">
                        <CalendarDays className="size-4 shrink-0 text-info" aria-hidden />
                        <span className="min-w-0 flex-1 truncate text-sm">{e.title}</span>
                        <span className="text-xs tabular text-muted-foreground">{fmt(parseISO(e.startsAt), e.allDay ? 'EEE d MMM' : 'EEE d MMM, HH:mm')}</span>
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
              {data.activity.length > 0 && <Panel title={t('overview.recentActivity')}><div className="px-4 py-4 pl-8"><ActivityTimeline items={data.activity} empty={t('overview.activityEmpty')} /></div></Panel>}
            </div>
          </div>
        </div>
      )}
    </>
  );
}
