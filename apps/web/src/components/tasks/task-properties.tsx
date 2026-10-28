'use client';

import { toast } from 'sonner';

import { PRIORITIES, PRIORITY_LABEL, STATUSES, STATUS_LABEL } from '@/components/common/badges';
import { PersonAvatar } from '@/components/common/person';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { orgApi, useOrgMutation } from '@/lib/api/queries';
import type { PersonRef, Task } from '@/lib/api/types';
import { day } from '@/lib/format';

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="grid grid-cols-[96px_1fr] items-center gap-2 py-1.5">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="min-w-0 text-sm">{children}</dd>
    </div>
  );
}

/** Status, assignee, priority and dates; each change saves straight away. */
export function TaskProperties({ org, task, canEdit, assignees }: { org: string; task: Task; canEdit: boolean; assignees: PersonRef[] }) {
  const { t } = useT();
  const update = useOrgMutation(org, (body: Record<string, unknown>) => orgApi.updateTask(org, task.id, body));
  const save = (body: Record<string, unknown>) => update.mutateAsync(body).catch((e) => toast.error(errorMessage(e)));
  const people = task.assignee && !assignees.some((a) => a.id === task.assignee!.id) ? [task.assignee, ...assignees] : assignees;

  return (
    <dl className="divide-y">
      <Row label={t('tasks.status')}>
        {canEdit ? (
          <Select value={task.status} onValueChange={(v) => save({ status: v })}>
            <SelectTrigger size="sm" className="w-full" aria-label={t('tasks.status')}><SelectValue /></SelectTrigger>
            <SelectContent>{STATUSES.map((s) => <SelectItem key={s} value={s}>{STATUS_LABEL[s]}</SelectItem>)}</SelectContent>
          </Select>
        ) : STATUS_LABEL[task.status]}
      </Row>
      <Row label={t('tasks.assignee')}>
        {canEdit ? (
          <Select value={task.assignee?.id ?? 'none'} onValueChange={(v) => save(v === 'none' ? { clear: ['assignee'] } : { assigneeId: v })}>
            <SelectTrigger size="sm" className="w-full" aria-label={t('tasks.assignee')}><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="none">{t('common.unassigned')}</SelectItem>
              {people.map((p) => <SelectItem key={p.id} value={p.id}>{p.name}</SelectItem>)}
            </SelectContent>
          </Select>
        ) : <span className="flex items-center gap-2"><PersonAvatar person={task.assignee} />{task.assignee?.name ?? t('common.unassigned')}</span>}
      </Row>
      <Row label={t('tasks.priority')}>
        {canEdit ? (
          <Select value={task.priority} onValueChange={(v) => save({ priority: v })}>
            <SelectTrigger size="sm" className="w-full" aria-label={t('tasks.priority')}><SelectValue /></SelectTrigger>
            <SelectContent>{PRIORITIES.map((p) => <SelectItem key={p} value={p}>{PRIORITY_LABEL[p]}</SelectItem>)}</SelectContent>
          </Select>
        ) : PRIORITY_LABEL[task.priority]}
      </Row>
      <Row label={t('tasks.startDate')}>
        {canEdit ? <Input type="date" className="h-8" aria-label={t('tasks.startDate')} defaultValue={task.startDate ?? ''} key={`s${task.startDate}`}
          onBlur={(e) => e.target.value !== (task.startDate ?? '') && save(e.target.value ? { startDate: e.target.value } : { clear: ['startDate'] })} />
          : task.startDate ? day(task.startDate) : '—'}
      </Row>
      <Row label={t('tasks.dueDate')}>
        {canEdit ? <Input type="date" className="h-8" aria-label={t('tasks.dueDate')} defaultValue={task.dueDate ?? ''} key={`d${task.dueDate}`}
          onBlur={(e) => e.target.value !== (task.dueDate ?? '') && save(e.target.value ? { dueDate: e.target.value } : { clear: ['dueDate'] })} />
          : task.dueDate ? day(task.dueDate) : '—'}
      </Row>
      <Row label={t('tasks.reporter')}><span className="flex items-center gap-2"><PersonAvatar person={task.reporter} />{task.reporter.name}</span></Row>
      <Row label={t('tasks.createdAt')}><span className="text-muted-foreground">{day(task.createdAt)}</span></Row>
    </dl>
  );
}
