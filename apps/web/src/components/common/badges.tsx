import { ArrowDown, ArrowUp, ChevronsUp, Minus } from 'lucide-react';

import { labels, tr } from '@/i18n/core';
import type { ApprovalStatus, Priority, TaskStatus } from '@/lib/api/types';
import { cn } from '@/lib/utils';

export const STATUSES: TaskStatus[] = ['TODO', 'IN_PROGRESS', 'IN_REVIEW', 'DONE'];
export const STATUS_LABEL = labels(STATUSES, (s) => `status.${s}`);

const statusTone: Record<TaskStatus, string> = {
  TODO: 'bg-muted text-muted-foreground',
  IN_PROGRESS: 'bg-primary/10 text-primary',
  IN_REVIEW: 'bg-info/10 text-info',
  DONE: 'bg-success/10 text-success',
};

export function StatusBadge({ status, className }: { status: TaskStatus; className?: string }) {
  return (
    <span className={cn('inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium', statusTone[status], className)}>
      {STATUS_LABEL[status]}
    </span>
  );
}

export const PRIORITIES: Priority[] = ['URGENT', 'HIGH', 'MEDIUM', 'LOW'];
export const PRIORITY_LABEL = labels(PRIORITIES, (p) => `status.${p}`);

export function PriorityIcon({ priority, withLabel }: { priority: Priority; withLabel?: boolean }) {
  const Icon = priority === 'URGENT' ? ChevronsUp : priority === 'HIGH' ? ArrowUp : priority === 'LOW' ? ArrowDown : Minus;
  const tone = priority === 'URGENT' ? 'text-destructive' : priority === 'HIGH' ? 'text-warning' : 'text-muted-foreground';
  return (
    <span className={cn('inline-flex items-center gap-1 text-xs', tone)} title={tr('status.priorityOf', { priority: PRIORITY_LABEL[priority] })}>
      <Icon className="size-3.5" aria-hidden />
      {withLabel ? PRIORITY_LABEL[priority] : <span className="sr-only">{tr('status.priorityOf', { priority: PRIORITY_LABEL[priority] })}</span>}
    </span>
  );
}

export const APPROVAL_LABEL = labels(['PENDING', 'CHANGES_REQUESTED', 'APPROVED', 'REJECTED', 'WITHDRAWN'] as ApprovalStatus[], (a) => `status.${a}`);
const approvalTone: Record<ApprovalStatus, string> = {
  PENDING: 'bg-primary/10 text-primary',
  CHANGES_REQUESTED: 'bg-warning/10 text-warning',
  APPROVED: 'bg-success/10 text-success',
  REJECTED: 'bg-destructive/10 text-destructive',
  WITHDRAWN: 'bg-muted text-muted-foreground',
};

export function ApprovalBadge({ status }: { status: ApprovalStatus }) {
  return (
    <span className={cn('inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium', approvalTone[status])}>
      {APPROVAL_LABEL[status]}
    </span>
  );
}
