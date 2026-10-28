import { PRIORITY_LABEL, STATUS_LABEL } from '@/components/common/badges';
import { PersonAvatar } from '@/components/common/person';
import { tr } from '@/i18n/core';
import type { ActivityItem, Priority, TaskStatus } from '@/lib/api/types';
import { ago, day } from '@/lib/format';

const s = (v: unknown) => (typeof v === 'string' ? v : '');
const status = (v: unknown) => STATUS_LABEL[v as TaskStatus] ?? s(v);
const priority = (v: unknown) => (PRIORITY_LABEL[v as Priority] ?? s(v)).toLowerCase();

/** A plain sentence for an activity entry, in the current language. */
export function describe(a: ActivityItem): string {
  const d = a.data;
  switch (a.verb) {
    case 'task.created': return tr('tasks.actCreated', { key: s(d.key), title: s(d.title) });
    case 'task.subtask.added': return tr('tasks.actSubtaskAdded', { key: s(d.key) });
    case 'task.commented': return tr('tasks.actCommented', { excerpt: s(d.excerpt) });
    case 'task.attached': return tr('tasks.actAttached', { name: s(d.name) });
    case 'task.detached': return tr('tasks.actDetached');
    case 'task.deleted': return tr('tasks.actDeleted', { key: s(d.key), title: s(d.title) });
    case 'task.updated': {
      const parts: string[] = [];
      const st = d.status as { from?: string; to?: string } | undefined;
      if (st) parts.push(tr('tasks.actMoved', { from: status(st.from), to: status(st.to) }));
      if (d.assignee) parts.push((d.assignee as { to: string }).to === 'none' ? tr('tasks.actUnassigned') : tr('tasks.actAssigneeChanged'));
      if (d.priority) parts.push(tr('tasks.actPriority', { priority: priority((d.priority as { to: string }).to) }));
      if (d.dueDate) parts.push((d.dueDate as { to: string }).to === 'none' ? tr('tasks.actDueRemoved') : tr('tasks.actDueSet', { date: day(s((d.dueDate as { to: string }).to)) }));
      if (d.title) parts.push(tr('tasks.actRenamed'));
      if (d.description) parts.push(tr('tasks.actDescription'));
      return parts.join(', ') || tr('tasks.actUpdated');
    }
    case 'project.created': return tr('tasks.actProjectCreated', { name: s(d.name) });
    case 'project.updated': return tr('tasks.actProjectUpdated');
    case 'project.members': return tr('tasks.actProjectMembers');
    case 'approval.requested': return tr('tasks.actApprovalRequested', { workflow: s(d.workflow) });
    case 'approval.step.approved': return tr('tasks.actStepApproved');
    case 'approval.approved': return tr('tasks.actApproved');
    case 'approval.rejected': return d.comment ? tr('tasks.actRejectedWithComment', { comment: s(d.comment) }) : tr('tasks.actRejected');
    case 'approval.changes_requested': return tr('tasks.actChangesRequested', { comment: s(d.comment) });
    case 'approval.resubmitted': return tr('tasks.actResubmitted');
    case 'approval.withdrawn': return tr('tasks.actWithdrawn');
    default: return a.verb;
  }
}

export function ActivityTimeline({ items, empty }: { items: ActivityItem[]; empty?: string }) {
  if (items.length === 0) return <p className="py-4 text-sm text-muted-foreground">{empty ?? tr('tasks.noActivity')}</p>;
  return (
    <ol className="relative space-y-4 border-l pl-5">
      {items.map((a) => (
        <li key={a.id} className="relative">
          <span className="absolute -left-[29px] top-0.5"><PersonAvatar person={a.actor} className="size-5" /></span>
          <p className="text-sm"><span className="font-medium">{a.actor?.name ?? tr('tasks.system')}</span> {describe(a)}</p>
          <p className="text-xs text-muted-foreground">{ago(a.at)}</p>
        </li>
      ))}
    </ol>
  );
}
