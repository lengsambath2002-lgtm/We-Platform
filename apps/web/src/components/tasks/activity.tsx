import { PersonAvatar } from '@/components/common/person';
import { STATUS_LABEL } from '@/components/common/badges';
import type { ActivityItem, TaskStatus } from '@/lib/api/types';
import { ago } from '@/lib/format';

const s = (v: unknown) => (typeof v === 'string' ? v : '');
const status = (v: unknown) => STATUS_LABEL[v as TaskStatus] ?? s(v);

/** A plain sentence for an activity entry. */
export function describe(a: ActivityItem): string {
  const d = a.data;
  switch (a.verb) {
    case 'task.created': return `created ${s(d.key)} ${s(d.title)}`;
    case 'task.subtask.added': return `added subtask ${s(d.key)}`;
    case 'task.commented': return `commented: “${s(d.excerpt)}”`;
    case 'task.attached': return `attached ${s(d.name)}`;
    case 'task.detached': return 'removed an attachment';
    case 'task.deleted': return `deleted ${s(d.key)} ${s(d.title)}`;
    case 'task.updated': {
      const parts: string[] = [];
      const st = d.status as { from?: string; to?: string } | undefined;
      if (st) parts.push(`moved it from ${status(st.from)} to ${status(st.to)}`);
      if (d.assignee) parts.push((d.assignee as { to: string }).to === 'none' ? 'unassigned it' : 'changed the assignee');
      if (d.priority) parts.push(`set priority to ${s((d.priority as { to: string }).to).toLowerCase()}`);
      if (d.dueDate) parts.push((d.dueDate as { to: string }).to === 'none' ? 'removed the due date' : `set the due date to ${s((d.dueDate as { to: string }).to)}`);
      if (d.title) parts.push('renamed it');
      if (d.description) parts.push('edited the description');
      return parts.join(', ') || 'updated it';
    }
    case 'project.created': return `created project ${s(d.name)}`;
    case 'project.updated': return 'updated the project';
    case 'project.members': return 'changed the project members';
    case 'approval.requested': return `requested approval (${s(d.workflow)})`;
    case 'approval.step.approved': return 'approved a step';
    case 'approval.approved': return 'approved the request';
    case 'approval.rejected': return `rejected the request${d.comment ? `: “${s(d.comment)}”` : ''}`;
    case 'approval.changes_requested': return `requested changes: “${s(d.comment)}”`;
    case 'approval.resubmitted': return 'resubmitted for approval';
    case 'approval.withdrawn': return 'withdrew the approval request';
    default: return a.verb;
  }
}

export function ActivityTimeline({ items, empty = 'No activity yet.' }: { items: ActivityItem[]; empty?: string }) {
  if (items.length === 0) return <p className="py-4 text-sm text-muted-foreground">{empty}</p>;
  return (
    <ol className="relative space-y-4 border-l pl-5">
      {items.map((a) => (
        <li key={a.id} className="relative">
          <span className="absolute -left-[29px] top-0.5"><PersonAvatar person={a.actor} className="size-5" /></span>
          <p className="text-sm"><span className="font-medium">{a.actor?.name ?? 'System'}</span> {describe(a)}</p>
          <p className="text-xs text-muted-foreground">{ago(a.at)}</p>
        </li>
      ))}
    </ol>
  );
}
