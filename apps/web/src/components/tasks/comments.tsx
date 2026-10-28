'use client';

import { MessageSquare } from 'lucide-react';
import { useMemo, useState } from 'react';
import { toast } from 'sonner';

import { PersonAvatar } from '@/components/common/person';
import { ErrorState, ListSkeleton } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { orgApi, useComments, useMembers, useOrgMutation } from '@/lib/api/queries';
import { ago } from '@/lib/format';

/** Renders "@Name" of a known person as a highlighted mention. */
function Body({ text, names }: { text: string; names: string[] }) {
  if (!names.length) return <>{text}</>;
  const re = new RegExp(`(@(?:${names.map((n) => n.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|')}))`, 'g');
  return <>{text.split(re).map((part, i) => (i % 2 ? <span key={i} className="font-medium text-primary">{part}</span> : part))}</>;
}

/** Comments with @mentions: a mentioned person gets a notification (the server checks they can see the task). */
export function Comments({ org, taskId, canComment, hideHeading }: { org: string; taskId: string; canComment: boolean; hideHeading?: boolean }) {
  const { t } = useT();
  const { data = [], error, isPending, refetch } = useComments(org, taskId);
  const members = useMembers(org);
  const [body, setBody] = useState('');
  const people = useMemo(() => (members.data ?? []).filter((m) => m.status === 'ACTIVE'), [members.data]);
  const query = /(?:^|\s)@([^\s@]*)$/.exec(body)?.[1];
  const suggestions = query === undefined ? [] : people.filter((p) => p.name.toLowerCase().startsWith(query.toLowerCase())).slice(0, 5);
  const post = useOrgMutation(org, (text: string) =>
    orgApi.comment(org, taskId, text, people.filter((p) => text.includes(`@${p.name}`)).map((p) => p.userId)));

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
                <p className="text-sm"><span className="font-medium">{c.author.name}</span> <span className="text-xs text-muted-foreground">{ago(c.createdAt)}</span></p>
                <p className="mt-1 whitespace-pre-wrap break-words text-sm"><Body text={c.body} names={people.map((p) => p.name)} /></p>
              </div>
            </li>
          ))}
        </ul>
      )}
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
    </section>
  );
}
