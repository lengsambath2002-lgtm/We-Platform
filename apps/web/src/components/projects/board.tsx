'use client';

import {
  DndContext,
  type DragEndEvent,
  type DragStartEvent,
  DragOverlay,
  KeyboardSensor,
  PointerSensor,
  closestCorners,
  useDroppable,
  useSensor,
  useSensors,
} from '@dnd-kit/core';
import { SortableContext, sortableKeyboardCoordinates, useSortable, verticalListSortingStrategy } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { useQueryClient } from '@tanstack/react-query';
import { ListChecks, MessageSquare, MoreHorizontal, Plus } from 'lucide-react';
import Link from 'next/link';
import { useState } from 'react';
import { toast } from 'sonner';

import { PriorityIcon, STATUSES, STATUS_LABEL } from '@/components/common/badges';
import { PersonAvatar } from '@/components/common/person';
import { DueDate } from '@/components/tasks/task-row';
import { Button } from '@/components/ui/button';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';
import { errorMessage } from '@/lib/api/client';
import { keys, orgApi } from '@/lib/api/queries';
import type { Project, Task, TaskStatus } from '@/lib/api/types';
import { cn } from '@/lib/utils';

function Card({ org, task, canEdit, onMove, overlay }: { org: string; task: Task; canEdit: boolean; onMove: (t: Task, s: TaskStatus) => void; overlay?: boolean }) {
  return (
    <div className={cn('group rounded-md border bg-card p-3 shadow-xs', overlay && 'rotate-1 shadow-lg')}>
      <div className="flex items-start gap-2">
        <Link href={`/${org}/tasks/${task.id}`} className="min-w-0 flex-1 text-sm font-medium leading-snug hover:underline" onPointerDown={(e) => e.stopPropagation()}>
          {task.title}
        </Link>
        {canEdit && (
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="icon" className="-mr-1 -mt-1 size-7 opacity-60 group-hover:opacity-100" aria-label={`Move ${task.key}`} onPointerDown={(e) => e.stopPropagation()}>
                <MoreHorizontal className="size-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuLabel className="text-xs text-muted-foreground">Move to</DropdownMenuLabel>
              {STATUSES.filter((s) => s !== task.status).map((s) => <DropdownMenuItem key={s} onSelect={() => onMove(task, s)}>{STATUS_LABEL[s]}</DropdownMenuItem>)}
            </DropdownMenuContent>
          </DropdownMenu>
        )}
      </div>
      <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1">
        <span className="font-mono text-[11px] text-muted-foreground">{task.key}</span>
        <PriorityIcon priority={task.priority} />
        <DueDate date={task.dueDate} done={task.status === 'DONE'} />
        {task.subtasks > 0 && <span className="inline-flex items-center gap-1 text-xs text-muted-foreground"><ListChecks className="size-3.5" />{task.subtasksDone}/{task.subtasks}</span>}
        {task.comments > 0 && <span className="inline-flex items-center gap-1 text-xs text-muted-foreground"><MessageSquare className="size-3.5" />{task.comments}</span>}
        <span className="ml-auto"><PersonAvatar person={task.assignee} /></span>
      </div>
    </div>
  );
}

function Sortable({ org, task, canEdit, onMove }: { org: string; task: Task; canEdit: boolean; onMove: (t: Task, s: TaskStatus) => void }) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: task.id, disabled: !canEdit, data: { status: task.status } });
  return (
    <li ref={setNodeRef} style={{ transform: CSS.Translate.toString(transform), transition }} className={cn(isDragging && 'opacity-40')}
      {...attributes} {...listeners} aria-roledescription="Draggable task" aria-label={`${task.key} ${task.title}`}>
      <Card org={org} task={task} canEdit={canEdit} onMove={onMove} />
    </li>
  );
}

function Column({ status, children, count, onAdd }: { status: TaskStatus; children: React.ReactNode; count: number; onAdd?: () => void }) {
  const { setNodeRef, isOver } = useDroppable({ id: `col:${status}`, data: { status } });
  return (
    <section aria-label={STATUS_LABEL[status]} className={cn('flex w-[280px] shrink-0 flex-col rounded-lg bg-muted/60 sm:w-auto sm:min-w-0', isOver && 'ring-2 ring-primary/40')}>
      <header className="flex items-center justify-between px-3 py-2">
        <h3 className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">{STATUS_LABEL[status]} <span className="ml-1 tabular">{count}</span></h3>
        {onAdd && <Button variant="ghost" size="icon" className="size-7" onClick={onAdd} aria-label={`Add task to ${STATUS_LABEL[status]}`}><Plus className="size-4" /></Button>}
      </header>
      <ul ref={setNodeRef} className="flex min-h-24 flex-1 flex-col gap-2 px-2 pb-2">{children}</ul>
    </section>
  );
}

/** Kanban board: drag between columns (mouse, touch, keyboard) or use each card's "Move to" menu. */
export function Board({ org, project, tasks, onAdd }: { org: string; project: Project; tasks: Task[]; onAdd: (s: TaskStatus) => void }) {
  const qc = useQueryClient();
  const [dragging, setDragging] = useState<Task | null>(null);
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }));
  const byStatus = (s: TaskStatus) => tasks.filter((t) => t.status === s);

  const move = async (task: Task, status: TaskStatus, index: number) => {
    const key = keys.tasks(org, project.key);
    const before = qc.getQueryData<Task[]>(key);
    qc.setQueryData<Task[]>(key, (list = []) => {
      const rest = list.filter((t) => t.id !== task.id);
      const col = rest.filter((t) => t.status === status);
      const other = rest.filter((t) => t.status !== status);
      col.splice(Math.min(index, col.length), 0, { ...task, status });
      return [...other, ...col];
    });
    try {
      await orgApi.moveTask(org, task.id, status, index);
      qc.invalidateQueries({ queryKey: ['org', org] });
    } catch (e) {
      qc.setQueryData(key, before);
      toast.error(errorMessage(e));
    }
  };

  const onDragEnd = (e: DragEndEvent) => {
    setDragging(null);
    const task = tasks.find((t) => t.id === e.active.id);
    if (!task || !e.over) return;
    const overStatus = (e.over.data.current?.status ?? task.status) as TaskStatus;
    const col = byStatus(overStatus).filter((t) => t.id !== task.id);
    const overIndex = col.findIndex((t) => t.id === e.over!.id);
    const index = overIndex < 0 ? col.length : overIndex;
    if (overStatus === task.status && byStatus(task.status).findIndex((t) => t.id === task.id) === index) return;
    void move(task, overStatus, index);
  };

  return (
    <DndContext sensors={sensors} collisionDetection={closestCorners}
      onDragStart={(e: DragStartEvent) => setDragging(tasks.find((t) => t.id === e.active.id) ?? null)} onDragEnd={onDragEnd} onDragCancel={() => setDragging(null)}>
      <div className="relative -mx-4 flex gap-3 overflow-x-auto px-4 pb-2 sm:mx-0 sm:grid sm:grid-cols-2 sm:overflow-visible sm:px-0 xl:grid-cols-4">
        {STATUSES.map((s) => {
          const col = byStatus(s);
          return (
            <Column key={s} status={s} count={col.length} onAdd={project.canEdit ? () => onAdd(s) : undefined}>
              <SortableContext items={col.map((t) => t.id)} strategy={verticalListSortingStrategy}>
                {col.map((t) => <Sortable key={t.id} org={org} task={t} canEdit={project.canEdit} onMove={(task, st) => void move(task, st, byStatus(st).length)} />)}
              </SortableContext>
              {col.length === 0 && <li className="rounded-md border border-dashed px-3 py-6 text-center text-xs text-muted-foreground">No tasks</li>}
            </Column>
          );
        })}
      </div>
      <DragOverlay>{dragging && <Card org={org} task={dragging} canEdit={false} onMove={() => undefined} overlay />}</DragOverlay>
    </DndContext>
  );
}
