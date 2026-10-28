'use client';

// Reports: aggregates computed by the API over what the caller may see (GET /api/orgs/{org}/reports/*).
import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { http } from './client';

export interface WeekFlow { weekStart: string; created: number; completed: number }
export interface TaskFlow { from: string; to: string; created: number; completed: number; weeks: WeekFlow[] }

export interface ProjectOpen { key: string; name: string; todo: number; inProgress: number; inReview: number; open: number; overdue: number }
export interface OpenWork { asOf: string; open: number; overdue: number; projects: ProjectOpen[] }

/** `id` and `name` are null for unassigned work. */
export interface AssigneeLoad { id: string | null; name: string | null; open: number; overdue: number }
export interface Workload { asOf: string; open: number; overdue: number; people: AssigneeLoad[] }

export interface ApprovalStats {
  submitted: number; approved: number; rejected: number; changesRequested: number; pending: number; withdrawn: number;
  medianHours: number | null; p90Hours: number | null; stale: number;
}
export interface WorkflowStats { id: string; name: string; stats: ApprovalStats }
export interface ApprovalsReport { from: string; to: string; staleDays: number; total: ApprovalStats; workflows: WorkflowStats[] }

/** The filter every report on the page shares. Dates are inclusive, `yyyy-MM-dd`; no project = all I can see. */
export interface ReportFilter { from: string; to: string; project?: string; staleDays: number }

const base = (org: string) => `/orgs/${encodeURIComponent(org)}/reports`;
const qs = (p: Record<string, string | number | undefined>) =>
  new URLSearchParams(Object.entries(p).filter(([, v]) => v !== undefined && v !== '').map(([k, v]) => [k, String(v)])).toString();

export const reportKeys = {
  all: (org: string) => ['org', org, 'reports'] as const,
  one: (org: string, name: string, params: Record<string, unknown>) => ['org', org, 'reports', name, params] as const,
};

// Refetching keeps the previous numbers on screen (dimmed by the page) instead of flashing a skeleton.
const opts = { placeholderData: keepPreviousData, staleTime: 60_000 } as const;

export const useTaskFlow = (org: string, f: ReportFilter, enabled = true) => {
  const p = { from: f.from, to: f.to, project: f.project };
  return useQuery({ queryKey: reportKeys.one(org, 'task-flow', p), queryFn: () => http.get<TaskFlow>(`${base(org)}/task-flow?${qs(p)}`), enabled, ...opts });
};

export const useOpenWork = (org: string, f: ReportFilter, enabled = true) => {
  const p = { project: f.project };
  return useQuery({ queryKey: reportKeys.one(org, 'open-work', p), queryFn: () => http.get<OpenWork>(`${base(org)}/open-work?${qs(p)}`), enabled, ...opts });
};

export const useWorkload = (org: string, f: ReportFilter, enabled = true) => {
  const p = { project: f.project };
  return useQuery({ queryKey: reportKeys.one(org, 'workload', p), queryFn: () => http.get<Workload>(`${base(org)}/workload?${qs(p)}`), enabled, ...opts });
};

export const useApprovalsReport = (org: string, f: ReportFilter) => {
  const p = { from: f.from, to: f.to, project: f.project, staleDays: f.staleDays };
  return useQuery({ queryKey: reportKeys.one(org, 'approvals', p), queryFn: () => http.get<ApprovalsReport>(`${base(org)}/approvals?${qs(p)}`), ...opts });
};
