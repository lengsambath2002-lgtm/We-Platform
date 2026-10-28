'use client';

import { useQueryClient } from '@tanstack/react-query';
import { parseISO } from 'date-fns';
import { BellOff, Check, CheckCheck, ChevronLeft, ChevronRight, Inbox } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { toast } from 'sonner';

import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { Switch } from '@/components/ui/switch';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import type { MessageKey } from '@/i18n/messages';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { collabApi, collabKeys, type NotificationKind, useNotificationPrefs, useNotificationsPage } from '@/lib/api/collab';
import { orgApi } from '@/lib/api/queries';
import type { NotificationItem } from '@/lib/api/types';
import { ago, fmt } from '@/lib/format';
import { useOrg } from '@/lib/org-context';
import { cn } from '@/lib/utils';

const SIZE = 20;

const KIND_LABEL: Record<NotificationKind, MessageKey> = {
  'task.assigned': 'collab.kindTaskAssigned',
  'task.mentioned': 'collab.kindTaskMentioned',
  'task.commented': 'collab.kindTaskCommented',
  'task.status': 'collab.kindTaskStatus',
  'project.added': 'collab.kindProjectAdded',
  'event.invited': 'collab.kindEventInvited',
  'approval.requested': 'collab.kindApprovalRequested',
  'approval.decided': 'collab.kindApprovalDecided',
};

/** Which kinds of in-app notification I get in this organisation. */
function Preferences({ org, orgName }: { org: string; orgName: string }) {
  const { t } = useT();
  const qc = useQueryClient();
  const { data, error, isPending, refetch } = useNotificationPrefs(org);
  const [saving, setSaving] = useState(false);
  const toggle = async (kind: NotificationKind, on: boolean) => {
    if (!data) return;
    const muted = data.kinds.filter((k) => (k.kind === kind ? !on : !k.inApp)).map((k) => k.kind);
    setSaving(true);
    try {
      qc.setQueryData(collabKeys.notificationPrefs(org), await collabApi.setNotificationPrefs(org, muted));
      toast.success(t('collab.prefsSaved'));
    } catch (e) { toast.error(errorMessage(e)); } finally { setSaving(false); }
  };
  return (
    <section aria-labelledby="prefs-h" className="h-fit rounded-lg border bg-card p-4">
      <h2 id="prefs-h" className="text-sm font-semibold">{t('collab.prefsTitle')}</h2>
      <p className="mt-1 text-sm text-muted-foreground">{t('collab.prefsBody', { org: orgName })}</p>
      {error ? <ErrorState error={error} retry={() => refetch()} className="mt-3" /> : isPending ? (
        <div className="mt-3 space-y-2">{Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-7" />)}</div>
      ) : (
        <ul className="mt-3 space-y-1">
          {data.kinds.map((k) => (
            <li key={k.kind} className="flex items-center justify-between gap-3 py-1.5">
              <label htmlFor={`kind-${k.kind}`} className="text-sm">{t(KIND_LABEL[k.kind])}</label>
              <Switch id={`kind-${k.kind}`} checked={k.inApp} disabled={saving} onCheckedChange={(v) => void toggle(k.kind, v)} />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

export default function NotificationsPage() {
  const { slug, org } = useOrg();
  const { t } = useT();
  const router = useRouter();
  const qc = useQueryClient();
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [page, setPage] = useState(0);
  const { data, error, isPending, isFetching, refetch } = useNotificationsPage(slug, unreadOnly, page, SIZE);
  const pages = data ? Math.max(1, Math.ceil(data.total / SIZE)) : 1;
  const refresh = () => qc.invalidateQueries({ queryKey: ['org', slug, 'notifications'] });
  const read = async (id?: string) => {
    try { await orgApi.readNotifications(slug, id); await refresh(); if (!id) toast.success(t('collab.allMarkedRead')); }
    catch (e) { toast.error(errorMessage(e)); }
  };
  const openItem = (n: NotificationItem) => {
    if (!n.readAt) void read(n.id);
    if (n.link) router.push(n.link);
  };
  const filter = (v: string) => { setUnreadOnly(v === 'unread'); setPage(0); };

  return (
    <>
      <PageHeader title={t('collab.notificationsTitle')} subtitle={t('collab.notificationsSubtitle', { org: org.name })}
        actions={(data?.unread ?? 0) > 0 && <Button size="sm" variant="outline" onClick={() => void read()}><CheckCheck className="size-4" />{t('collab.markAllRead')}</Button>} />
      <div className="grid gap-4 lg:grid-cols-[1fr_320px]">
        <div className="min-w-0 space-y-3">
          <Tabs value={unreadOnly ? 'unread' : 'all'} onValueChange={filter}>
            <TabsList>
              <TabsTrigger value="all">{t('collab.all')}</TabsTrigger>
              <TabsTrigger value="unread">{data?.unread ? t('collab.unreadCount', { count: data.unread }) : t('collab.unread')}</TabsTrigger>
            </TabsList>
          </Tabs>
          {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={6} /> : data.items.length === 0 ? (
            unreadOnly
              ? <EmptyState icon={BellOff} title={t('collab.nothingUnread')} body={t('collab.nothingUnreadBody')} />
              : <EmptyState icon={Inbox} title={t('collab.nothingYet')} body={t('collab.nothingYetBody')} />
          ) : (
            <>
              <ul className={cn('divide-y overflow-hidden rounded-lg border bg-card', isFetching && 'opacity-70')} aria-busy={isFetching}>
                {data.items.map((n) => (
                  <li key={n.id} className={cn('flex items-start gap-2 px-3 py-3 sm:px-4', !n.readAt && 'bg-accent/40')}>
                    <span className={cn('mt-2 size-2 shrink-0 rounded-full', n.readAt ? 'bg-transparent' : 'bg-primary')} aria-hidden />
                    <button type="button" onClick={() => openItem(n)} disabled={!n.link && !!n.readAt}
                      className="min-w-0 flex-1 rounded text-left focus-visible:outline-2 focus-visible:outline-ring disabled:cursor-default">
                      <span className={cn('block break-words text-sm', !n.readAt && 'font-medium')}>{n.title}</span>
                      {n.body && <span className="mt-0.5 line-clamp-2 block break-words text-sm text-muted-foreground">{n.body}</span>}
                      <time dateTime={n.createdAt} title={fmt(parseISO(n.createdAt), 'd MMM yyyy HH:mm')} className="mt-0.5 block text-xs text-muted-foreground">{ago(n.createdAt)}</time>
                    </button>
                    {!n.readAt && (
                      <Button variant="ghost" size="icon" className="size-8 shrink-0" aria-label={t('collab.markRead')} title={t('collab.markRead')} onClick={() => void read(n.id)}>
                        <Check className="size-4" />
                      </Button>
                    )}
                  </li>
                ))}
              </ul>
              {pages > 1 && (
                <nav className="flex items-center justify-between gap-2" aria-label={t('collab.pageOf', { page: page + 1, pages })}>
                  <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}><ChevronLeft className="size-4" />{t('collab.newer')}</Button>
                  <span className="text-sm text-muted-foreground tabular">{t('collab.pageOf', { page: page + 1, pages })}</span>
                  <Button variant="outline" size="sm" disabled={page + 1 >= pages} onClick={() => setPage((p) => p + 1)}>{t('collab.older')}<ChevronRight className="size-4" /></Button>
                </nav>
              )}
            </>
          )}
        </div>
        <Preferences org={slug} orgName={org.name} />
      </div>
    </>
  );
}
