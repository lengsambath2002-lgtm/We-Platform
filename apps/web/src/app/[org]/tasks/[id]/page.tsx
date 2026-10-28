'use client';

import { Archive, ChevronRight, ListChecks, MoreHorizontal, Pencil, Plus, Trash2 } from 'lucide-react';
import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useState } from 'react';
import { toast } from 'sonner';

import { RecordApprovals } from '@/components/approvals/record-approvals';
import { StatusBadge } from '@/components/common/badges';
import { ErrorState, ListSkeleton } from '@/components/common/states';
import { ActivityTimeline } from '@/components/tasks/activity';
import { Attachments } from '@/components/tasks/attachments';
import { Comments } from '@/components/tasks/comments';
import { NewTaskDialog } from '@/components/tasks/new-task-dialog';
import { TaskProperties } from '@/components/tasks/task-properties';
import { TaskRow } from '@/components/tasks/task-row';
import { useAssignees } from '@/components/tasks/use-assignees';
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Button } from '@/components/ui/button';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';
import { Input } from '@/components/ui/input';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Textarea } from '@/components/ui/textarea';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { orgApi, useOrgMutation, useProject, useTask, useTaskActivity } from '@/lib/api/queries';
import { useOrg } from '@/lib/org-context';

export default function TaskPage() {
  const { t } = useT();
  const { slug } = useOrg();
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const { data, error, isPending, refetch } = useTask(slug, id);
  const project = useProject(slug, data?.task.projectKey ?? '');
  const assignees = useAssignees(slug, project.data);
  const activity = useTaskActivity(slug, id);
  const [editing, setEditing] = useState<'title' | 'description' | null>(null);
  const [draft, setDraft] = useState('');
  const [subtask, setSubtask] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const update = useOrgMutation(slug, (body: Record<string, unknown>) => orgApi.updateTask(slug, id, body));
  const remove = useOrgMutation(slug, () => orgApi.deleteTask(slug, id));

  if (error) return <ErrorState error={error} retry={() => refetch()} />;
  if (isPending) return <ListSkeleton />;
  const { task, subtasks, attachments, canEdit } = data;
  const archived = project.data?.status === 'ARCHIVED';

  const saveText = async () => {
    const field = editing!;
    const value = draft.trim();
    if (field === 'title' && !value) return toast.error(t('tasks.titleRequired'));
    try {
      await update.mutateAsync(field === 'description' && !value ? { clear: ['description'] } : { [field]: value });
      setEditing(null);
    } catch (e) { toast.error(errorMessage(e)); }
  };
  const startEdit = (f: 'title' | 'description') => { setDraft((f === 'title' ? task.title : task.description) ?? ''); setEditing(f); };

  return (
    <div className="mx-auto max-w-6xl">
      <nav aria-label={t('projects.breadcrumb')} className="mb-2 flex flex-wrap items-center gap-1 text-sm text-muted-foreground">
        <Link href={`/${slug}/projects`} className="hover:text-foreground">{t('projects.title')}</Link><ChevronRight className="size-3.5" />
        <Link href={`/${slug}/projects/${task.projectKey}`} className="hover:text-foreground">{task.projectName}</Link><ChevronRight className="size-3.5" />
        {task.parentId && <><Link href={`/${slug}/tasks/${task.parentId}`} className="hover:text-foreground">{t('tasks.parentTask')}</Link><ChevronRight className="size-3.5" /></>}
        <span className="font-mono text-xs">{task.key}</span>
      </nav>

      {archived && <p role="status" className="mb-4 flex items-center gap-2 rounded-md border bg-muted px-3 py-2 text-sm text-muted-foreground"><Archive className="size-4 shrink-0" />{t('collab.archivedTask')}</p>}
      <div className="mb-6 flex items-start gap-3">
        <div className="min-w-0 flex-1">
          {editing === 'title' ? (
            <form onSubmit={(e) => { e.preventDefault(); void saveText(); }} className="flex gap-2">
              <Input autoFocus value={draft} onChange={(e) => setDraft(e.target.value)} maxLength={200} aria-label={t('tasks.title')} className="text-lg font-semibold"
                onKeyDown={(e) => e.key === 'Escape' && setEditing(null)} />
              <Button type="submit" size="sm" disabled={update.isPending}>{t('common.save')}</Button>
              <Button type="button" size="sm" variant="ghost" onClick={() => setEditing(null)}>{t('common.cancel')}</Button>
            </form>
          ) : (
            <h1 className="flex items-start gap-2 text-xl font-semibold tracking-tight">
              <span className="break-words">{task.title}</span>
              {canEdit && <Button variant="ghost" size="icon" className="size-7 shrink-0" aria-label={t('tasks.editTitle')} onClick={() => startEdit('title')}><Pencil className="size-3.5" /></Button>}
            </h1>
          )}
          <div className="mt-1 flex items-center gap-2"><StatusBadge status={task.status} /><span className="font-mono text-xs text-muted-foreground">{task.key}</span></div>
        </div>
        {canEdit && (
          <DropdownMenu>
            <DropdownMenuTrigger asChild><Button variant="outline" size="icon" aria-label={t('tasks.moreActions')}><MoreHorizontal className="size-4" /></Button></DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              {!task.parentId && <DropdownMenuItem onSelect={() => setSubtask(true)}><Plus className="size-4" />{t('tasks.addSubtask')}</DropdownMenuItem>}
              <DropdownMenuItem className="text-destructive focus:text-destructive" onSelect={() => setConfirmDelete(true)}><Trash2 className="size-4" />{t('tasks.deleteTask')}</DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        )}
      </div>

      <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
        <div className="order-2 min-w-0 space-y-8 lg:order-1">
          <section aria-labelledby="desc-h">
            <div className="mb-2 flex items-center justify-between">
              <h2 id="desc-h" className="text-base font-semibold">{t('tasks.description')}</h2>
              {canEdit && editing !== 'description' && <Button variant="ghost" size="sm" onClick={() => startEdit('description')}><Pencil className="size-3.5" />{t('common.edit')}</Button>}
            </div>
            {editing === 'description' ? (
              <div className="space-y-2">
                <Textarea autoFocus rows={8} value={draft} onChange={(e) => setDraft(e.target.value)} maxLength={20000} aria-label={t('tasks.description')} />
                <div className="flex gap-2">
                  <Button size="sm" onClick={saveText} disabled={update.isPending}>{t('common.save')}</Button>
                  <Button size="sm" variant="ghost" onClick={() => setEditing(null)}>{t('common.cancel')}</Button>
                </div>
              </div>
            ) : task.description ? <p className="whitespace-pre-wrap break-words text-sm leading-relaxed">{task.description}</p>
              : <p className="text-sm text-muted-foreground">{t('tasks.noDescription')}</p>}
          </section>

          {!task.parentId && (
            <section aria-labelledby="sub-h">
              <div className="mb-2 flex items-center justify-between">
                <h2 id="sub-h" className="flex items-center gap-2 text-base font-semibold"><ListChecks className="size-4" />{t('tasks.subtasks')}
                  {subtasks.length > 0 && <span className="text-sm font-normal text-muted-foreground tabular">{subtasks.filter((s) => s.status === 'DONE').length}/{subtasks.length}</span>}
                </h2>
                {canEdit && <Button variant="ghost" size="sm" onClick={() => setSubtask(true)}><Plus className="size-4" />{t('common.add')}</Button>}
              </div>
              {subtasks.length ? <div className="overflow-hidden rounded-lg border bg-card">{subtasks.map((s) => <TaskRow key={s.id} org={slug} task={s} showProject={false} />)}</div>
                : <p className="text-sm text-muted-foreground">{t('tasks.subtasksHint')}</p>}
            </section>
          )}

          <Attachments org={slug} taskId={task.id} files={attachments} canEdit={canEdit} />

          <Tabs defaultValue="comments">
            <TabsList><TabsTrigger value="comments">{t('tasks.commentsTab', { count: task.comments })}</TabsTrigger><TabsTrigger value="activity">{t('tasks.activity')}</TabsTrigger></TabsList>
            <TabsContent value="comments" className="pt-3"><Comments org={slug} taskId={task.id} canComment={data.canComment !== false} hideHeading /></TabsContent>
            <TabsContent value="activity" className="pt-3">
              {activity.error ? <ErrorState error={activity.error} retry={() => activity.refetch()} /> : activity.isPending ? <ListSkeleton rows={3} /> : <ActivityTimeline items={activity.data} />}
            </TabsContent>
          </Tabs>
        </div>

        <aside className="order-1 space-y-4 lg:order-2">
          <div className="rounded-lg border bg-card px-4 py-2"><TaskProperties org={slug} task={task} canEdit={canEdit} assignees={assignees} /></div>
          <RecordApprovals org={slug} recordId={task.id} canRequest={canEdit} />
        </aside>
      </div>

      {subtask && project.data && <NewTaskDialog org={slug} project={project.data} open onOpenChange={(v) => !v && setSubtask(false)} parentId={task.id} />}
      <AlertDialog open={confirmDelete} onOpenChange={setConfirmDelete}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('tasks.deleteTitle', { key: task.key })}</AlertDialogTitle>
            <AlertDialogDescription>
              {subtasks.length ? t('tasks.deleteBodySubtasks', { count: subtasks.length }) : t('tasks.deleteBody')}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t('common.cancel')}</AlertDialogCancel>
            <AlertDialogAction className="bg-destructive text-white hover:bg-destructive/90" onClick={async () => {
              try { await remove.mutateAsync(undefined); toast.success(t('tasks.deleted', { key: task.key })); router.replace(`/${slug}/projects/${task.projectKey}`); }
              catch (e) { toast.error(errorMessage(e)); }
            }}>{t('common.delete')}</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
