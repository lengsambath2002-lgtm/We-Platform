import { CalendarClock, MessageSquare } from 'lucide-react';
import Link from 'next/link';

import { PriorityIcon, StatusBadge } from '@/components/common/badges';
import { PersonAvatar } from '@/components/common/person';
import { tr } from '@/i18n/core';
import type { Task } from '@/lib/api/types';
import { dueLabel } from '@/lib/format';
import { cn } from '@/lib/utils';

export function DueDate({ date, done, className }: { date: string | null; done?: boolean; className?: string }) {
  const d = dueLabel(date, done);
  if (!d) return null;
  return (
    <span className={cn('inline-flex items-center gap-1 text-xs tabular', d.overdue ? 'font-medium text-destructive' : d.soon ? 'text-warning' : 'text-muted-foreground', className)}>
      <CalendarClock className="size-3.5" aria-hidden />
      {d.overdue ? tr('common.overdue', { date: d.text }) : d.text}
    </span>
  );
}

/** One task in a list: key, title, project, status, priority, due date, assignee. */
export function TaskRow({ org, task, showProject = true }: { org: string; task: Task; showProject?: boolean }) {
  return (
    <Link href={`/${org}/tasks/${task.id}`}
      className="flex items-center gap-3 border-b px-3 py-2.5 last:border-b-0 hover:bg-muted/50 focus-visible:bg-muted/50 focus-visible:outline-none">
      <PriorityIcon priority={task.priority} />
      <span className="hidden w-16 shrink-0 font-mono text-xs text-muted-foreground sm:inline">{task.key}</span>
      <span className="min-w-0 flex-1">
        <span className={cn('block truncate text-sm', task.status === 'DONE' && 'text-muted-foreground line-through')}>{task.title}</span>
        <span className="flex flex-wrap items-center gap-x-3 gap-y-0.5 sm:hidden">
          <span className="font-mono text-[11px] text-muted-foreground">{task.key}</span>
          <DueDate date={task.dueDate} done={task.status === 'DONE'} />
        </span>
      </span>
      {showProject && <span className="hidden max-w-40 truncate text-xs text-muted-foreground md:inline">{task.projectName}</span>}
      {task.comments > 0 && (
        <span className="hidden items-center gap-1 text-xs text-muted-foreground md:inline-flex"><MessageSquare className="size-3.5" />{task.comments}</span>
      )}
      <DueDate date={task.dueDate} done={task.status === 'DONE'} className="hidden w-32 justify-end sm:inline-flex" />
      <StatusBadge status={task.status} className="hidden sm:inline-flex" />
      <PersonAvatar person={task.assignee} />
    </Link>
  );
}
