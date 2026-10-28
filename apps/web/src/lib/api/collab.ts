'use client';

// Collaboration API: event edits, comment edits, project delete, the notifications page and its
// preferences, and the organisation's audit log.
import { useQuery } from '@tanstack/react-query';

import { http } from './client';
import type { ActivityItem, CalendarEvent, Comment, NotificationItem, PersonRef } from './types';

export interface NotificationsPage {
  items: NotificationItem[];
  unread: number;
  page: number;
  size: number;
  total: number;
}

export type NotificationKind =
  | 'task.assigned' | 'task.mentioned' | 'task.commented' | 'task.status'
  | 'project.added' | 'event.invited' | 'approval.requested' | 'approval.decided';

export interface NotificationPrefs {
  kinds: { kind: NotificationKind; inApp: boolean }[];
}

/** One audit log row; `record` is the record's current name and link (null when it is gone). */
export interface AuditEntry extends ActivityItem {
  module: string;
  recordType: string;
  recordId: string;
  record: { title: string; link: string | null } | null;
}

export interface AuditFilter {
  actorId?: string;
  module?: string;
  recordType?: string;
  verb?: string;
  from?: string;
  to?: string;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}

const o = (org: string) => `/orgs/${encodeURIComponent(org)}`;
const qs = (params: Record<string, string | number | boolean | undefined>) =>
  new URLSearchParams(Object.entries(params).filter(([, v]) => v !== undefined && v !== '').map(([k, v]) => [k, String(v)])).toString();

export const collabKeys = {
  // Under ['org', org, 'notifications'] so the header's invalidation refreshes the page too.
  notificationsPage: (org: string, unread: boolean, page: number) => ['org', org, 'notifications', 'page', unread, page] as const,
  notificationPrefs: (org: string) => ['org', org, 'notification-prefs'] as const,
  audit: (org: string, f: AuditFilter, page: number) => ['org', org, 'audit', f, page] as const,
};

export const useNotificationsPage = (org: string, unread: boolean, page: number, size = 20) =>
  useQuery({
    queryKey: collabKeys.notificationsPage(org, unread, page),
    queryFn: () => http.get<NotificationsPage>(`${o(org)}/notifications?${qs({ page, size, unread })}`),
    placeholderData: (prev) => prev,
  });

export const useNotificationPrefs = (org: string) =>
  useQuery({ queryKey: collabKeys.notificationPrefs(org), queryFn: () => http.get<NotificationPrefs>(`${o(org)}/notifications/preferences`) });

export const useAudit = (org: string, f: AuditFilter, page: number, size = 50, enabled = true) =>
  useQuery({
    enabled,
    queryKey: collabKeys.audit(org, f, page),
    queryFn: () => http.get<Page<AuditEntry & { actor: PersonRef | null }>>(`${o(org)}/activity?${qs({ ...f, page, size })}`),
    placeholderData: (prev) => prev,
  });

export const collabApi = {
  updateEvent: (org: string, id: string, body: unknown) => http.put<CalendarEvent>(`${o(org)}/events/${id}`, body),
  editComment: (org: string, taskId: string, id: string, body: string, mentionIds: string[]) =>
    http.patch<Comment>(`${o(org)}/tasks/${taskId}/comments/${id}`, { body, mentionIds }),
  deleteComment: (org: string, taskId: string, id: string) => http.delete<void>(`${o(org)}/tasks/${taskId}/comments/${id}`),
  deleteProject: (org: string, key: string) => http.delete<void>(`${o(org)}/projects/${key}`),
  setNotificationPrefs: (org: string, muted: NotificationKind[]) =>
    http.put<NotificationPrefs>(`${o(org)}/notifications/preferences`, { muted }),
};
