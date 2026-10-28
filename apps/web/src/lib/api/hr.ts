'use client';

// HR & Workforce: types, query hooks and calls (kept apart from queries.ts).
import { useQuery } from '@tanstack/react-query';

import { http } from './client';
import type { ApprovalStatus, PersonRef } from './types';

export type EmploymentType = 'FULL_TIME' | 'PART_TIME' | 'CONTRACT' | 'INTERN';
export const EMPLOYMENT_TYPES: EmploymentType[] = ['FULL_TIME', 'PART_TIME', 'CONTRACT', 'INTERN'];
export type LeaveStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN' | 'CANCELLED';
export const LEAVE_STATUSES: LeaveStatus[] = ['PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'CANCELLED'];

export interface PersonSummary {
  person: PersonRef;
  email: string;
  title: string | null;
  department: string | null;
  manager: PersonRef | null;
  workPhone: string | null;
}

/** Employment details and the emergency contact are null unless `privateShown` (the person or HR). */
export interface Profile {
  person: PersonRef;
  email: string;
  role: string | null;
  title: string | null;
  department: string | null;
  manager: PersonRef | null;
  workPhone: string | null;
  privateShown: boolean;
  employmentType: EmploymentType | null;
  startDate: string | null;
  emergencyName: string | null;
  emergencyPhone: string | null;
  emergencyRelation: string | null;
  reports: PersonRef[];
  canEdit: boolean;
  canEditContact: boolean;
}

export interface LeaveType {
  id: string;
  nameEn: string;
  nameKm: string;
  paid: boolean;
  /** null = unlimited */
  allowanceDays: number | null;
  halfDays: boolean;
  active: boolean;
}

/** `allowance` and `remaining` are null for unlimited types. */
export interface Balance {
  type: LeaveType;
  year: number;
  allowance: number | null;
  adjusted: number;
  used: number;
  pending: number;
  remaining: number | null;
}

export interface Adjustment {
  id: string;
  type: LeaveType;
  year: number;
  days: number;
  reason: string;
  by: PersonRef | null;
  at: string;
}

export interface Leave {
  id: string;
  person: PersonRef;
  type: LeaveType;
  startDate: string;
  endDate: string;
  halfStart: boolean;
  halfEnd: boolean;
  days: number;
  /** null unless the caller is the person, their manager or HR */
  reason: string | null;
  status: LeaveStatus;
  approvalId: string | null;
  approvalStatus: ApprovalStatus | null;
  cancelReason: string | null;
  createdAt: string;
  canWithdraw: boolean;
  canCancel: boolean;
}

export interface Absence {
  leaveId: string;
  person: PersonRef;
  startDate: string;
  endDate: string;
  halfStart: boolean;
  halfEnd: boolean;
  /** only for HR and the person */
  type: LeaveType | null;
}

export interface LeaveFilter {
  status?: string;
  userId?: string;
  typeId?: string;
  year?: number;
}

const h = (org: string) => `/orgs/${encodeURIComponent(org)}/hr`;
const qs = (o: object) => {
  const p = new URLSearchParams();
  Object.entries(o).forEach(([k, v]) => v !== undefined && v !== null && v !== '' && p.set(k, String(v)));
  const s = p.toString();
  return s ? `?${s}` : '';
};

export interface Holiday {
  id: string;
  date: string;
  nameEn: string;
  nameKm: string;
}

export const hrKeys = {
  all: (org: string) => ['org', org, 'hr'] as const,
  people: (org: string) => ['org', org, 'hr', 'people'] as const,
  profile: (org: string, id: string) => ['org', org, 'hr', 'profile', id] as const,
  types: (org: string) => ['org', org, 'hr', 'types'] as const,
  balances: (org: string, userId: string, year: number) => ['org', org, 'hr', 'balances', userId, year] as const,
  adjustments: (org: string, userId: string, year: number) => ['org', org, 'hr', 'adjustments', userId, year] as const,
  mine: (org: string) => ['org', org, 'hr', 'mine'] as const,
  leaves: (org: string, f: LeaveFilter) => ['org', org, 'hr', 'leaves', f] as const,
  leave: (org: string, id: string) => ['org', org, 'hr', 'leave', id] as const,
  absences: (org: string, from: string, to: string) => ['org', org, 'hr', 'absences', from, to] as const,
  holidays: (org: string, year: number) => ['org', org, 'hr', 'holidays', year] as const,
};

export const usePeople = (org: string) =>
  useQuery({ queryKey: hrKeys.people(org), queryFn: () => http.get<PersonSummary[]>(`${h(org)}/people`) });
export const useProfile = (org: string, userId: string) =>
  useQuery({ queryKey: hrKeys.profile(org, userId), queryFn: () => http.get<Profile>(`${h(org)}/people/${userId}`), enabled: !!userId });
export const useLeaveTypes = (org: string) =>
  useQuery({ queryKey: hrKeys.types(org), queryFn: () => http.get<LeaveType[]>(`${h(org)}/leave-types`) });
/** `userId` '' = mine. */
export const useBalances = (org: string, userId: string, year: number, enabled = true) =>
  useQuery({ queryKey: hrKeys.balances(org, userId, year), queryFn: () => http.get<Balance[]>(`${h(org)}/balances${qs({ userId, year })}`), enabled });
export const useAdjustments = (org: string, userId: string, year: number) =>
  useQuery({ queryKey: hrKeys.adjustments(org, userId, year), queryFn: () => http.get<Adjustment[]>(`${h(org)}/balances/${userId}/adjustments?year=${year}`), enabled: !!userId });
export const useMyLeave = (org: string) =>
  useQuery({ queryKey: hrKeys.mine(org), queryFn: () => http.get<Leave[]>(`${h(org)}/leave/mine`) });
export const useAllLeave = (org: string, f: LeaveFilter, enabled = true) =>
  useQuery({ queryKey: hrKeys.leaves(org, f), queryFn: () => http.get<Leave[]>(`${h(org)}/leave${qs(f)}`), enabled });
export const useLeave = (org: string, id: string) =>
  useQuery({ queryKey: hrKeys.leave(org, id), queryFn: () => http.get<Leave>(`${h(org)}/leave/${id}`) });
export const useAbsences = (org: string, from: string, to: string) =>
  useQuery({ queryKey: hrKeys.absences(org, from, to), queryFn: () => http.get<Absence[]>(`${h(org)}/absences?from=${from}&to=${to}`) });

export const useHolidays = (org: string, year: number) =>
  useQuery({ queryKey: hrKeys.holidays(org, year), queryFn: () => http.get<Holiday[]>(`${h(org)}/holidays?year=${year}`) });

export const hrApi = {
  updateProfile: (org: string, userId: string, body: unknown) => http.put<Profile>(`${h(org)}/people/${userId}`, body),
  updateContact: (org: string, body: unknown) => http.put<Profile>(`${h(org)}/me/contact`, body),
  saveLeaveType: (org: string, id: string | null, body: unknown) =>
    id ? http.put<LeaveType>(`${h(org)}/leave-types/${id}`, body) : http.post<LeaveType>(`${h(org)}/leave-types`, body),
  adjust: (org: string, body: unknown) => http.post<Balance[]>(`${h(org)}/balances/adjust`, body),
  requestLeave: (org: string, body: unknown) => http.post<Leave>(`${h(org)}/leave`, body),
  withdraw: (org: string, id: string) => http.post<Leave>(`${h(org)}/leave/${id}/withdraw`),
  cancel: (org: string, id: string, reason: string | null) => http.post<Leave>(`${h(org)}/leave/${id}/cancel`, { reason }),
  addHoliday: (org: string, body: unknown) => http.post<Holiday>(`${h(org)}/holidays`, body),
  removeHoliday: (org: string, id: string) => http.delete<void>(`${h(org)}/holidays/${id}`),
  importCambodia: (org: string, year: number) => http.post<{ added: number; kept: number }>(`${h(org)}/holidays/cambodia/${year}`),
};

/** Working days between two yyyy-MM-dd dates (Mon–Fri, not public holidays), as the API counts them. */
export function workingDays(start: string, end: string, halfStart: boolean, halfEnd: boolean, holidays: ReadonlySet<string> = new Set()): number {
  if (!start || !end || end < start) return 0;
  let total = 0;
  const last = new Date(`${end}T00:00:00`);
  for (let d = new Date(`${start}T00:00:00`); d <= last; d.setDate(d.getDate() + 1)) {
    const wd = d.getDay();
    if (wd === 0 || wd === 6) continue;
    const iso = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    if (holidays.has(iso)) continue;
    total += (halfStart && iso === start) || (halfEnd && iso === end) ? 0.5 : 1;
  }
  return total;
}
