'use client';

// Every API call the web app makes, as TanStack Query hooks with shared keys.
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { http } from './client';
import type {
  ActivityItem,
  ApprovalDetail,
  ApprovalSummary,
  CalendarEvent,
  Comment,
  Deadline,
  FileRef,
  Invitation,
  Member,
  NotificationItem,
  OrgContext,
  OrgSummary,
  Overview,
  Period,
  Project,
  ProjectMember,
  Task,
  TaskDetail,
  Team,
  Workflow,
} from './types';

export const keys = {
  orgs: ['orgs'] as const,
  context: (org: string) => ['org', org, 'context'] as const,
  overview: (org: string) => ['org', org, 'overview'] as const,
  members: (org: string) => ['org', org, 'members'] as const,
  invitations: (org: string) => ['org', org, 'invitations'] as const,
  teams: (org: string) => ['org', org, 'teams'] as const,
  projects: (org: string) => ['org', org, 'projects'] as const,
  project: (org: string, key: string) => ['org', org, 'project', key] as const,
  projectMembers: (org: string, key: string) => ['org', org, 'project', key, 'members'] as const,
  tasks: (org: string, key: string) => ['org', org, 'project', key, 'tasks'] as const,
  myTasks: (org: string) => ['org', org, 'my-tasks'] as const,
  task: (org: string, id: string) => ['org', org, 'task', id] as const,
  comments: (org: string, id: string) => ['org', org, 'task', id, 'comments'] as const,
  activity: (org: string, id: string) => ['org', org, 'task', id, 'activity'] as const,
  recordApprovals: (org: string, id: string) => ['org', org, 'record', id, 'approvals'] as const,
  workflows: (org: string) => ['org', org, 'workflows'] as const,
  approvals: (org: string, box: string) => ['org', org, 'approvals', box] as const,
  approval: (org: string, id: string) => ['org', org, 'approval', id] as const,
  calendar: (org: string, from: string, to: string, mine: boolean) => ['org', org, 'calendar', from, to, mine] as const,
  notifications: (org: string) => ['org', org, 'notifications'] as const,
};

const o = (org: string) => `/orgs/${encodeURIComponent(org)}`;

export const useOrgs = () => useQuery({ queryKey: keys.orgs, queryFn: () => http.get<OrgSummary[]>('/orgs') });
export const useOrgContext = (org: string) =>
  useQuery({ queryKey: keys.context(org), queryFn: () => http.get<OrgContext>(`${o(org)}/context`) });
export const useOverview = (org: string) =>
  useQuery({ queryKey: keys.overview(org), queryFn: () => http.get<Overview>(`${o(org)}/overview`) });
export const useMembers = (org: string) =>
  useQuery({ queryKey: keys.members(org), queryFn: () => http.get<Member[]>(`${o(org)}/members`) });
export const useInvitations = (org: string, enabled = true) =>
  useQuery({ queryKey: keys.invitations(org), queryFn: () => http.get<Invitation[]>(`${o(org)}/invitations`), enabled });
export const useTeams = (org: string) =>
  useQuery({ queryKey: keys.teams(org), queryFn: () => http.get<Team[]>(`${o(org)}/teams`) });
export const useProjects = (org: string) =>
  useQuery({ queryKey: keys.projects(org), queryFn: () => http.get<Project[]>(`${o(org)}/projects`) });
export const useProject = (org: string, key: string) =>
  useQuery({ queryKey: keys.project(org, key), queryFn: () => http.get<Project>(`${o(org)}/projects/${key}`), enabled: !!key });
export const useProjectMembers = (org: string, key: string) =>
  useQuery({ queryKey: keys.projectMembers(org, key), queryFn: () => http.get<ProjectMember[]>(`${o(org)}/projects/${key}/members`), enabled: !!key });
export const useTasks = (org: string, key: string) =>
  useQuery({ queryKey: keys.tasks(org, key), queryFn: () => http.get<Task[]>(`${o(org)}/projects/${key}/tasks`) });
export const useMyTasks = (org: string) =>
  useQuery({ queryKey: keys.myTasks(org), queryFn: () => http.get<Task[]>(`${o(org)}/my-tasks`) });
export const useTask = (org: string, id: string) =>
  useQuery({ queryKey: keys.task(org, id), queryFn: () => http.get<TaskDetail>(`${o(org)}/tasks/${id}`) });
export const useComments = (org: string, id: string) =>
  useQuery({ queryKey: keys.comments(org, id), queryFn: () => http.get<Comment[]>(`${o(org)}/tasks/${id}/comments`) });
export const useTaskActivity = (org: string, id: string) =>
  useQuery({ queryKey: keys.activity(org, id), queryFn: () => http.get<ActivityItem[]>(`${o(org)}/tasks/${id}/activity`) });
export const useRecordApprovals = (org: string, id: string, moduleKey = 'projects', recordType = 'task') =>
  useQuery({
    queryKey: keys.recordApprovals(org, id),
    queryFn: () => http.get<ApprovalSummary[]>(`${o(org)}/approvals/record/${moduleKey}/${recordType}/${id}`),
  });
export const useWorkflows = (org: string) =>
  useQuery({ queryKey: keys.workflows(org), queryFn: () => http.get<Workflow[]>(`${o(org)}/workflows`) });
export const useApprovals = (org: string, box: string) =>
  useQuery({ queryKey: keys.approvals(org, box), queryFn: () => http.get<ApprovalSummary[]>(`${o(org)}/approvals?box=${box}`) });
export const useApproval = (org: string, id: string) =>
  useQuery({ queryKey: keys.approval(org, id), queryFn: () => http.get<ApprovalDetail>(`${o(org)}/approvals/${id}`), enabled: !!id });
export const useCalendar = (org: string, from: string, to: string, mine: boolean) =>
  useQuery({
    queryKey: keys.calendar(org, from, to, mine),
    queryFn: () => http.get<{ events: CalendarEvent[]; deadlines: Deadline[]; periods: Period[] }>(`${o(org)}/calendar?from=${from}&to=${to}&mine=${mine}`),
  });
export const useNotifications = (org: string) =>
  useQuery({
    queryKey: keys.notifications(org),
    queryFn: () => http.get<{ items: NotificationItem[]; unread: number }>(`${o(org)}/notifications`),
    refetchInterval: 30_000,
  });

/** A mutation that refreshes everything under this organisation when it succeeds. */
export function useOrgMutation<V, R>(org: string, fn: (v: V) => Promise<R>) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['org', org] }),
  });
}

export const orgApi = {
  createProject: (org: string, body: unknown) => http.post<Project>(`${o(org)}/projects`, body),
  updateProject: (org: string, key: string, body: unknown) => http.patch<Project>(`${o(org)}/projects/${key}`, body),
  setProjectMembers: (org: string, key: string, body: unknown) => http.put<ProjectMember[]>(`${o(org)}/projects/${key}/members`, body),
  createTask: (org: string, key: string, body: unknown) => http.post<Task>(`${o(org)}/projects/${key}/tasks`, body),
  updateTask: (org: string, id: string, body: unknown) => http.patch<Task>(`${o(org)}/tasks/${id}`, body),
  moveTask: (org: string, id: string, status: string, index: number) => http.post<Task>(`${o(org)}/tasks/${id}/move`, { status, index }),
  deleteTask: (org: string, id: string) => http.delete<void>(`${o(org)}/tasks/${id}`),
  comment: (org: string, id: string, body: string, mentionIds: string[]) =>
    http.post<Comment>(`${o(org)}/tasks/${id}/comments`, { body, mentionIds }),
  attach: (org: string, id: string, file: File) => {
    const fd = new FormData();
    fd.append('file', file);
    return http.post<FileRef>(`${o(org)}/tasks/${id}/attachments`, fd);
  },
  detach: (org: string, id: string, fileId: string) => http.delete<void>(`${o(org)}/tasks/${id}/attachments/${fileId}`),
  submitApproval: (org: string, body: unknown) => http.post<ApprovalDetail>(`${o(org)}/approvals`, body),
  decide: (org: string, id: string, action: 'approve' | 'reject' | 'request-changes' | 'resubmit' | 'withdraw', comment?: string) =>
    http.post<ApprovalDetail>(`${o(org)}/approvals/${id}/${action}`, action === 'withdraw' ? undefined : { comment }),
  invite: (org: string, email: string, role: string) => http.post<Invitation>(`${o(org)}/invitations`, { email, role }),
  revokeInvitation: (org: string, id: string) => http.delete<void>(`${o(org)}/invitations/${id}`),
  updateMember: (org: string, id: string, body: unknown) => http.patch<Member>(`${o(org)}/members/${id}`, body),
  setModule: (org: string, key: string, enabled: boolean) => http.put<OrgContext>(`${o(org)}/modules/${key}`, { enabled }),
  saveWorkflow: (org: string, id: string | null, body: unknown) =>
    id ? http.put<Workflow>(`${o(org)}/workflows/${id}`, body) : http.post<Workflow>(`${o(org)}/workflows`, body),
  createEvent: (org: string, body: unknown) => http.post<CalendarEvent>(`${o(org)}/events`, body),
  deleteEvent: (org: string, id: string) => http.delete<void>(`${o(org)}/events/${id}`),
  readNotifications: (org: string, id?: string) => http.post<void>(`${o(org)}/notifications/read${id ? `?id=${id}` : ''}`),
  saveTeam: (org: string, id: string | null, body: unknown) =>
    id ? http.put<Team>(`${o(org)}/teams/${id}`, body) : http.post<Team>(`${o(org)}/teams`, body),
};
