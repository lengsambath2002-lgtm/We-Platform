'use client';

import { parseISO } from 'date-fns';
import { ClipboardPlus, Download, Paperclip, Pencil, Trash2, Upload } from 'lucide-react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useRef, useState } from 'react';
import { toast } from 'sonner';

import { PriorityIcon } from '@/components/common/badges';
import { PersonAvatar } from '@/components/common/person';
import { ErrorState, ListSkeleton } from '@/components/common/states';
import { TicketDialog } from '@/components/service/dialogs';
import { WorkOrderList } from '@/components/service/lists';
import { CATEGORY_LABEL, Crumb, Field, SlaBadge, TICKET_LABEL, TicketBadge } from '@/components/service/ui';
import { ActivityTimeline } from '@/components/tasks/activity';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { useOrgMutation } from '@/lib/api/queries';
import {
  type TicketStatus, downloadTicketFile, svcApi, useStaff, useTicket, useTicketComments, useTicketHistory,
} from '@/lib/api/service';
import { ago, bytes, fmt } from '@/lib/format';
import { useOrg } from '@/lib/org-context';

const NONE = 'none';
const MAX = 20 * 1024 * 1024;
const when = (iso: string | null) => (iso ? fmt(parseISO(iso), 'd MMM yyyy, HH:mm') : '—');

/** One ticket: status moves, assignee, SLA targets, comments, files, linked work orders and its history. */
export default function TicketPage() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const { id } = useParams<{ id: string }>();
  const { data: x, error, isPending, refetch } = useTicket(slug, id);
  const comments = useTicketComments(slug, id);
  const history = useTicketHistory(slug, id);
  const staff = useStaff(slug, can('service.manage'));
  const [editing, setEditing] = useState(false);
  const [body, setBody] = useState('');
  const file = useRef<HTMLInputElement>(null);
  const move = useOrgMutation(slug, (s: TicketStatus) => svcApi.ticketStatus(slug, id, s));
  const assign = useOrgMutation(slug, (u: string | null) => svcApi.assign(slug, id, u));
  const post = useOrgMutation(slug, (text: string) => svcApi.comment(slug, id, text));
  const attach = useOrgMutation(slug, (f: File) => svcApi.attach(slug, id, f));
  const detach = useOrgMutation(slug, (fileId: string) => svcApi.detach(slug, id, fileId));
  if (error) return <ErrorState error={error} retry={() => refetch()} />;
  if (isPending) return <ListSkeleton />;
  const s = x.summary;
  const run = async (fn: () => Promise<unknown>, done?: string) => {
    try { await fn(); if (done) toast.success(done); } catch (e) { toast.error(errorMessage(e)); }
  };
  const moveLabel = (to: TicketStatus) => (to === 'IN_PROGRESS' && (s.status === 'RESOLVED' || s.status === 'CLOSED') ? t('service.reopen') : t('service.moveTo', { status: TICKET_LABEL[to] }));

  return (
    <div className="mx-auto max-w-6xl">
      <Crumb href={`/${slug}/service/tickets`} label={t('service.navTickets')} current={s.number} />
      <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h1 className="break-words text-xl font-semibold tracking-tight">{s.number} · {s.title}</h1>
          <p className="mt-1 flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
            <TicketBadge status={s.status} /><SlaBadge sla={s.sla} /><PriorityIcon priority={s.priority} withLabel /> {CATEGORY_LABEL[s.category]}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          {x.nextStatuses.map((to, i) => (
            <Button key={to} size="sm" variant={i === 0 ? 'default' : 'outline'} disabled={move.isPending}
              onClick={() => run(() => move.mutateAsync(to), t('service.movedTo', { status: TICKET_LABEL[to] }))}>{moveLabel(to)}</Button>
          ))}
          {x.canCreateWorkOrder && (
            <Button size="sm" variant="outline" asChild><Link href={`/${slug}/service/work-orders/new?ticket=${s.id}`}><ClipboardPlus className="size-4" />{t('service.createWorkOrder')}</Link></Button>
          )}
          {x.canEdit && <Button size="sm" variant="ghost" onClick={() => setEditing(true)}><Pencil className="size-4" />{t('common.edit')}</Button>}
        </div>
      </div>
      <div className="grid gap-6 md:grid-cols-[1fr_300px]">
        <div className="min-w-0 space-y-6">
          <section className="rounded-lg border bg-card p-4">
            <h2 className="mb-2 text-sm font-semibold">{t('service.details')}</h2>
            <p className="whitespace-pre-wrap text-sm">{x.description ?? <span className="text-muted-foreground">{t('service.noDetails')}</span>}</p>
          </section>
          <section aria-labelledby="wo-h" className="space-y-3">
            <h2 id="wo-h" className="text-base font-semibold">{t('service.workOrders')}</h2>
            {x.workOrders.length === 0
              ? <p className="text-sm text-muted-foreground">{t('service.noTicketWorkOrders')}</p>
              : <WorkOrderList org={slug} items={x.workOrders} />}
          </section>
          <section aria-labelledby="files-h" className="space-y-3 rounded-lg border bg-card p-4">
            <div className="flex items-center justify-between gap-2">
              <h2 id="files-h" className="flex items-center gap-2 text-sm font-semibold"><Paperclip className="size-4" />{t('service.files')}</h2>
              <Button size="sm" variant="outline" disabled={attach.isPending} onClick={() => file.current?.click()}><Upload className="size-4" />{t('service.addFile')}</Button>
              <input ref={file} type="file" className="sr-only" aria-label={t('service.addFile')} onChange={async (e) => {
                for (const f of Array.from(e.target.files ?? [])) {
                  if (f.size > MAX) { toast.error(t('service.tooLarge', { name: f.name })); continue; }
                  await run(() => attach.mutateAsync(f), t('service.fileAdded'));
                }
                e.target.value = '';
              }} />
            </div>
            {x.attachments.length === 0 ? <p className="text-sm text-muted-foreground">{t('service.noFiles')}</p> : (
              <ul className="divide-y">
                {x.attachments.map((f) => (
                  <li key={f.id} className="flex items-center gap-2 py-2 text-sm">
                    <button type="button" className="inline-flex min-w-0 flex-1 items-center gap-2 text-left text-primary hover:underline"
                      onClick={() => downloadTicketFile(slug, s.id, f.id, f.name).catch((e: Error) => toast.error(e.message))}>
                      <Download className="size-4 shrink-0" /><span className="truncate">{f.name}</span>
                    </button>
                    <span className="shrink-0 text-xs text-muted-foreground">{bytes(f.size)}</span>
                    <Button size="icon-sm" variant="ghost" aria-label={t('service.removeFile', { name: f.name })} onClick={() => run(() => detach.mutateAsync(f.id))}><Trash2 className="size-4" /></Button>
                  </li>
                ))}
              </ul>
            )}
          </section>
          <section aria-labelledby="comments-h" className="space-y-3">
            <h2 id="comments-h" className="text-base font-semibold">{t('service.comments')}</h2>
            {comments.error ? <ErrorState error={comments.error} retry={() => comments.refetch()} /> : comments.isPending ? <ListSkeleton rows={2} /> : (
              <ul className="space-y-3">
                {comments.data.length === 0 && <li className="text-sm text-muted-foreground">{t('service.noComments')}</li>}
                {comments.data.map((c) => (
                  <li key={c.id} className="flex gap-3">
                    <PersonAvatar person={c.author} className="size-7" />
                    <div className="min-w-0 flex-1 rounded-lg border bg-card px-3 py-2">
                      <p className="text-xs text-muted-foreground"><span className="font-medium text-foreground">{c.author?.name}</span> · {ago(c.createdAt)}</p>
                      <p className="mt-1 whitespace-pre-wrap break-words text-sm">{c.body}</p>
                    </div>
                  </li>
                ))}
              </ul>
            )}
            <form className="space-y-2" onSubmit={async (e) => {
              e.preventDefault();
              if (!body.trim()) return;
              await run(async () => { await post.mutateAsync(body.trim()); setBody(''); });
            }}>
              <Textarea aria-label={t('service.comment')} rows={2} value={body} placeholder={t('service.commentPlaceholder')} onChange={(e) => setBody(e.target.value)} />
              <div className="flex justify-end"><Button type="submit" size="sm" disabled={post.isPending || !body.trim()}>{t('service.comment')}</Button></div>
            </form>
          </section>
          <section aria-labelledby="hist-h" className="space-y-3">
            <h2 id="hist-h" className="text-base font-semibold">{t('service.history')}</h2>
            {history.error ? <ErrorState error={history.error} retry={() => history.refetch()} /> : history.isPending ? <ListSkeleton rows={3} />
              : <ActivityTimeline items={[...history.data].reverse()} />}
          </section>
        </div>
        <aside className="h-fit space-y-4 rounded-lg border bg-card p-4">
          <dl className="grid gap-4">
            <Field label={t('service.assignee')}>
              {x.canAssign ? (
                <Select value={s.assignee?.id ?? NONE} onValueChange={(v) => run(() => assign.mutateAsync(v === NONE ? null : v), t('service.assigned'))}>
                  <SelectTrigger aria-label={t('service.assignee')} className="w-full"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value={NONE}>{t('common.unassigned')}</SelectItem>
                    {s.assignee && !(staff.data ?? []).some((m) => m.person.id === s.assignee!.id) && <SelectItem value={s.assignee.id}>{s.assignee.name}</SelectItem>}
                    {(staff.data ?? []).filter((m) => m.active).map((m) => <SelectItem key={m.id} value={m.person.id}>{m.person.name}</SelectItem>)}
                  </SelectContent>
                </Select>
              ) : s.assignee ? <span className="inline-flex items-center gap-2"><PersonAvatar person={s.assignee} className="size-5" />{s.assignee.name}</span> : t('common.unassigned')}
            </Field>
            <Field label={t('service.responseTarget')}><span className="flex flex-wrap items-center gap-2"><SlaBadge sla={x.responseSla} />{x.respondedAt ? t('service.respondedAt', { date: when(x.respondedAt) }) : t('service.dueBy', { date: when(s.responseDueAt) })}</span></Field>
            <Field label={t('service.resolutionTarget')}><span className="flex flex-wrap items-center gap-2"><SlaBadge sla={x.resolutionSla} />{x.resolvedAt ? t('service.resolvedAt', { date: when(x.resolvedAt) }) : t('service.dueBy', { date: when(s.resolutionDueAt) })}</span></Field>
            <Field label={t('service.customer')}>{s.customer ? (can('service.manage') ? <Link className="text-primary hover:underline" href={`/${slug}/service/customers/${s.customer.id}`}>{s.customer.name}</Link> : s.customer.name) : '—'}</Field>
            <Field label={t('service.site')}>{x.site?.name ?? '—'}</Field>
            <Field label={t('service.asset')}>{x.asset ? (can('service.manage') ? <Link className="text-primary hover:underline" href={`/${slug}/service/assets/${x.asset.id}`}>{x.asset.name}</Link> : x.asset.name) : '—'}</Field>
            <Field label={t('service.requester')}>{[x.requesterName, x.requesterPhone].filter(Boolean).join(' · ') || '—'}</Field>
            <Field label={t('service.takenBy')}>{x.createdBy ? `${x.createdBy.name} · ${when(s.createdAt)}` : when(s.createdAt)}</Field>
          </dl>
        </aside>
      </div>
      {editing && <TicketDialog org={slug} ticket={x} open onOpenChange={setEditing} />}
    </div>
  );
}
