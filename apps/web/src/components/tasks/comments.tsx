'use client';

import { parseISO } from 'date-fns';
import { Lock, MessageSquare, MoreHorizontal, Pencil, Trash2 } from 'lucide-react';
import { useMemo, useState } from 'react';
import { toast } from 'sonner';

import { PersonAvatar } from '@/components/common/person';
import { ErrorState, ListSkeleton } from '@/components/common/states';
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Button } from '@/components/ui/button';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';
import { Textarea } from '@/components/ui/textarea';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { collabApi } from '@/lib/api/collab';
import { orgApi, useComments, useMembers, useOrgMutation } from '@/lib/api/queries';
import type { Comment } from '@/lib/api/types';
import { ago, fmt } from '@/lib/format';

/** Renders "@Name" of a known person as a highlighted mention. */
function Body({ text, names }: { text: string; names: string[] }) {
  if (!names.length) return <>{text}</>;
  const re = new RegExp(`(@(?:${names.map((n) => n.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|')}))`, 'g');
  return <>{text.split(re).map((part, i) => (i % 2 ? <span key={i} className="font-medium text-primary">{part}</span> : part))}</>;
}

/**
 * Comments with @mentions: a mentioned person gets a notification (the server checks they can see the task).
 * The author can edit their comment (people newly mentioned by the edit are told); the author, the project
 * lead or an admin can delete it. `canComment` is false while the project is archived.
 */
export function Comments({ org, taskId, canComment, hideHeading }: { org: string; taskId: string; canComment: boolean; hideHeading?: boolean }) {
  const { t } = useT();
  const { data = [], error, isPending, refetch } = useComments(org, taskId);
  const members = useMembers(org);
  const [body, setBody] = useState('');
  const people = useMemo(() => (members.data ?? []).filter((m) => m.status === 'ACTIVE'), [members.data]);
  const query = /(?:^|\s)@([^\s@]*)$/.exec(body)?.[1];
  const suggestions = query === undefined ? [] : people.filter((p) => p.name.toLowerCase().startsWith(query.toLowerCase())).slice(0, 5);
  const mentionIds = (text: string) => people.filter((p) => text.includes(`@${p.name}`)).map((p) => p.userId);
  const post = useOrgMutation(org, (text: string) => orgApi.comment(org, taskId, text, mentionIds(text)));
  const edit = useOrgMutation(org, ({ id, text }: { id: string; text: string }) => collabApi.editComment(org, taskId, id, text, mentionIds(text)));
  const remove = useOrgMutation(org, (id: string) => collabApi.deleteComment(org, taskId, id));
  const [editing, setEditing] = useState<{ id: string; text: string } | null>(null);
  const [deleting, setDeleting] = useState<Comment | null>(null);
  const saveEdit = async () => {
    if (!editing || !editing.text.trim()) return;
    try { await edit.mutateAsync({ id: editing.id, text: editing.text.trim() }); setEditing(null); toast.success(t('collab.commentUpdated')); }
    catch (e) { toast.error(errorMessage(e)); }
  };

  const pick = (name: string) => setBody((b) => b.replace(/@([^\s@]*)$/, `@${name} `));
  const submit = async () => {
    if (!body.trim()) return;
    try { await post.mutateAsync(body.trim()); setBody(''); } catch (e) { toast.error(errorMessage(e)); }
  };

  return (
    <section aria-labelledby="comments-h" className="space-y-4">
      <h2 id="comments-h" className={hideHeading ? 'sr-only' : 'flex items-center gap-2 text-base font-semibold'}><MessageSquare className="size-4" />{t('tasks.comments')}</h2>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={2} /> : data.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t('tasks.noComments')}{canComment && ` ${t('tasks.commentHint')}`}</p>
      ) : (
        <ul className="space-y-4">
          {data.map((c) => (
            <li key={c.id} className="flex gap-3">
              <PersonAvatar person={c.author} className="size-8" />
              <div className="min-w-0 flex-1">
                <div className="flex items-start justify-between gap-2">
                  <p className="text-sm">
                    <span className="font-medium">{c.author.name}</span> <span className="text-xs text-muted-foreground">{ago(c.createdAt)}</span>
                    {c.editedAt && <span className="text-xs text-muted-foreground" title={fmt(parseISO(c.editedAt), 'd MMM yyyy HH:mm')}> · {t('collab.edited')}</span>}
                  </p>
                  {(c.canEdit || c.canDelete) && editing?.id !== c.id && (
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button variant="ghost" size="icon" className="-my-1 size-7 shrink-0" aria-label={t('collab.commentActions')}><MoreHorizontal className="size-4" /></Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        {c.canEdit && <DropdownMenuItem onSelect={() => setEditing({ id: c.id, text: c.body })}><Pencil className="size-4" />{t('collab.editComment')}</DropdownMenuItem>}
                        {c.canDelete && <DropdownMenuItem className="text-destructive focus:text-destructive" onSelect={() => setDeleting(c)}><Trash2 className="size-4" />{t('collab.deleteComment')}</DropdownMenuItem>}
                      </DropdownMenuContent>
                    </DropdownMenu>
                  )}
                </div>
                {editing?.id === c.id ? (
                  <div className="mt-1 space-y-2">
                    <Textarea autoFocus value={editing.text} rows={3} aria-label={t('collab.editComment')} onChange={(e) => setEditing({ id: c.id, text: e.target.value })}
                      onKeyDown={(e) => { if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) void saveEdit(); if (e.key === 'Escape') setEditing(null); }} />
                    <div className="flex gap-2">
                      <Button size="sm" onClick={saveEdit} disabled={!editing.text.trim() || edit.isPending}>{edit.isPending ? t('common.saving') : t('common.save')}</Button>
                      <Button size="sm" variant="ghost" onClick={() => setEditing(null)}>{t('common.cancel')}</Button>
                    </div>
                  </div>
                ) : (
                  <p className="mt-1 whitespace-pre-wrap break-words text-sm"><Body text={c.body} names={people.map((p) => p.name)} /></p>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
      {!canComment && <p className="flex items-center gap-2 rounded-md bg-muted px-3 py-2 text-sm text-muted-foreground"><Lock className="size-4" />{t('collab.commentsClosed')}</p>}
      {canComment && (
        <div className="relative space-y-2">
          <Textarea value={body} onChange={(e) => setBody(e.target.value)} rows={3} placeholder={t('tasks.commentPlaceholder')} aria-label={t('tasks.comment')}
            onKeyDown={(e) => { if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) void submit(); }} />
          {suggestions.length > 0 && (
            <ul role="listbox" aria-label={t('tasks.mention')} className="absolute left-2 top-full z-10 mt-1 w-60 rounded-md border bg-popover p-1 shadow-md">
              {suggestions.map((p) => (
                <li key={p.userId}>
                  <button type="button" className="flex w-full items-center gap-2 rounded px-2 py-1.5 text-left text-sm hover:bg-muted" onClick={() => pick(p.name)}>
                    <PersonAvatar person={p} className="size-5" />{p.name}
                  </button>
                </li>
              ))}
            </ul>
          )}
          <div className="flex items-center justify-between">
            <span className="hidden text-xs text-muted-foreground sm:inline">{t('tasks.sendHint')}</span>
            <Button size="sm" onClick={submit} disabled={!body.trim() || post.isPending}>{post.isPending ? t('tasks.sending') : t('tasks.comment')}</Button>
          </div>
        </div>
      )}
      <AlertDialog open={!!deleting} onOpenChange={(v) => !v && setDeleting(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('collab.deleteCommentTitle')}</AlertDialogTitle>
            <AlertDialogDescription>{t('collab.deleteCommentBody')}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t('common.cancel')}</AlertDialogCancel>
            <AlertDialogAction className="bg-destructive text-white hover:bg-destructive/90" onClick={async () => {
              if (!deleting) return;
              try { await remove.mutateAsync(deleting.id); toast.success(t('collab.commentDeleted')); } catch (e) { toast.error(errorMessage(e)); }
            }}>{t('common.delete')}</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </section>
  );
}
