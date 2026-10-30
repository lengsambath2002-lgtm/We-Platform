'use client';

// Offboarding: resignations, departures HR starts, their checklists and the checklist template.
import { useQuery } from '@tanstack/react-query';

import { http } from './client';
import type { Balance } from './hr';
import type { PersonRef } from './types';

export const LEAVE_KINDS = ['RESIGNATION', 'TERMINATION', 'END_OF_CONTRACT', 'RETIREMENT', 'OTHER'] as const;
export type LeaveKind = (typeof LEAVE_KINDS)[number];
export type OffStatus = 'REQUESTED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';

export interface OffSummary { id: string; person: PersonRef; kind: LeaveKind; status: OffStatus; noticeOn: string; lastDay: string; done: number; total: number }
export interface OffItem { id: string; title: string; dueDate: string | null; done: boolean; doneAt: string | null; doneBy: PersonRef | null }
export interface OffDetail {
  summary: OffSummary; reason: string | null; rehire: boolean | null; items: OffItem[];
  handover: { reports: PersonRef[]; waitingApprovals: number; unitsHeaded: string[] } | null;
  leave: Balance[]; requestedBy: PersonRef | null; startedBy: PersonRef | null; completedBy: PersonRef | null; completedAt: string | null;
  canManage: boolean; canCancel: boolean;
}
export interface TemplateItem { title: string; dueDays: number }

const o = (org: string) => `/orgs/${encodeURIComponent(org)}/hr/offboarding`;
const k = (org: string) => ['org', org, 'hr', 'offboarding'] as const;

export const useOffboardings = (org: string, open: boolean, enabled = true) =>
  useQuery({ queryKey: [...k(org), 'list', open], queryFn: () => http.get<OffSummary[]>(`${o(org)}?open=${open}`), enabled });
export const useOffboarding = (org: string, id: string) =>
  useQuery({ queryKey: [...k(org), id], queryFn: () => http.get<OffDetail>(`${o(org)}/${id}`) });
export const useMyOffboarding = (org: string, enabled = true) =>
  useQuery({ queryKey: [...k(org), 'mine'], // The API leaves out an empty `data`: none = null.
  queryFn: async () => (await http.get<OffDetail | null>(`${o(org)}/mine`)) ?? null, enabled });
export const useOffTemplate = (org: string, enabled = true) =>
  useQuery({ queryKey: [...k(org), 'template'], queryFn: () => http.get<TemplateItem[]>(`${o(org)}/template`), enabled });

export const offboardingApi = {
  start: (org: string, body: unknown) => http.post<OffDetail>(o(org), body),
  resign: (org: string, body: { lastDay: string; reason: string | null }) => http.post<OffDetail>(`${o(org)}/resign`, body),
  accept: (org: string, id: string, body: unknown) => http.post<OffDetail>(`${o(org)}/${id}/accept`, body),
  update: (org: string, id: string, body: unknown) => http.put<OffDetail>(`${o(org)}/${id}`, body),
  cancel: (org: string, id: string) => http.post<OffDetail>(`${o(org)}/${id}/cancel`),
  complete: (org: string, id: string) => http.post<OffDetail>(`${o(org)}/${id}/complete`),
  addItem: (org: string, id: string, body: { title: string; dueDate: string | null }) => http.post<OffDetail>(`${o(org)}/${id}/items`, body),
  done: (org: string, itemId: string, done: boolean) => http.put<OffDetail>(`${o(org)}/items/${itemId}/done`, { done }),
  removeItem: (org: string, itemId: string) => http.delete<OffDetail>(`${o(org)}/items/${itemId}`),
  saveTemplate: (org: string, items: TemplateItem[]) => http.put<TemplateItem[]>(`${o(org)}/template`, { items }),
};
