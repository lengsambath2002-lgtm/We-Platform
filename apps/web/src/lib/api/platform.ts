'use client';

// The platform console (/admin), for super admins: every organisation and account, and what was done to them.
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { http } from './client';

export interface Page<T> { items: T[]; page: number; size: number; total: number }
export interface PlatformPerson { id: string; name: string; email: string }

export type OrgStatus = 'ACTIVE' | 'SUSPENDED' | 'DELETED';
export type AccountStatus = 'ACTIVE' | 'DISABLED' | 'LOCKED';

export interface PlatformStats {
  organisations: number;
  suspendedOrganisations: number;
  deletedOrganisations: number;
  newOrganisations30d: number;
  users: number;
  disabledUsers: number;
  newUsers30d: number;
  activeUsers30d: number;
  superAdmins: number;
  modules: { key: string; nameEn: string; nameKm: string; organisations: number }[];
}

export interface PlatformOrg {
  id: string;
  slug: string;
  name: string;
  status: OrgStatus;
  members: number;
  owners: PlatformPerson[];
  modules: string[];
  createdAt: string;
  lastActivityAt: string | null;
}

export interface PlatformOrgDetail {
  org: PlatformOrg;
  createdBy: PlatformPerson | null;
  suspendedAt: string | null;
  suspendedBy: PlatformPerson | null;
  suspendedReason: string | null;
  deletedAt: string | null;
  deletedBy: PlatformPerson | null;
  members: { userId: string; name: string; email: string; role: string; status: string; disabled: boolean; joinedAt: string }[];
  modules: { key: string; nameEn: string; nameKm: string; available: boolean; enabled: boolean }[];
  subscription: import('./billing').SubscriptionBrief | null;
}

export interface PlatformUser {
  id: string;
  name: string;
  email: string;
  avatarUrl: string | null;
  superAdmin: boolean;
  status: AccountStatus;
  organisations: number;
  createdAt: string;
  lastSeenAt: string | null;
}

export interface PlatformUserDetail {
  user: PlatformUser;
  locale: string;
  disabledAt: string | null;
  disabledReason: string | null;
  signedInDevices: number;
  organisations: { id: string; slug: string; name: string; role: string; orgStatus: OrgStatus }[];
}

export interface PlatformAuditEntry {
  id: string;
  actor: PlatformPerson | null;
  action: string;
  targetType: 'organisation' | 'user';
  targetId: string;
  targetName: string | null;
  data: Record<string, unknown>;
  at: string;
}

const qs = (params: Record<string, string | number | undefined>) => {
  const p = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) if (v !== undefined && v !== '') p.set(k, String(v));
  return p.toString();
};

export const platformKeys = {
  all: ['platform'] as const,
  stats: ['platform', 'stats'] as const,
  orgs: (q: string, status: string, page: number) => ['platform', 'orgs', q, status, page] as const,
  org: (id: string) => ['platform', 'org', id] as const,
  users: (q: string, status: string, page: number) => ['platform', 'users', q, status, page] as const,
  user: (id: string) => ['platform', 'user', id] as const,
  audit: (target: string, page: number) => ['platform', 'audit', target, page] as const,
};

export const usePlatformStats = () =>
  useQuery({ queryKey: platformKeys.stats, queryFn: () => http.get<PlatformStats>('/platform/stats') });

export const usePlatformOrgs = (q: string, status: string, page: number, size: number) =>
  useQuery({
    queryKey: platformKeys.orgs(q, status, page),
    queryFn: () => http.get<Page<PlatformOrg>>(`/platform/orgs?${qs({ q, status: status === 'all' ? undefined : status, page, size })}`),
    placeholderData: keepPreviousData,
  });

export const usePlatformOrg = (id: string) =>
  useQuery({ queryKey: platformKeys.org(id), queryFn: () => http.get<PlatformOrgDetail>(`/platform/orgs/${id}`) });

export const usePlatformUsers = (q: string, status: string, page: number, size: number) =>
  useQuery({
    queryKey: platformKeys.users(q, status, page),
    queryFn: () => http.get<Page<PlatformUser>>(`/platform/users?${qs({ q, status: status === 'all' ? undefined : status, page, size })}`),
    placeholderData: keepPreviousData,
  });

export const usePlatformUser = (id: string) =>
  useQuery({ queryKey: platformKeys.user(id), queryFn: () => http.get<PlatformUserDetail>(`/platform/users/${id}`) });

/** `target`: an organisation's or account's id, or '' for everything. */
export const usePlatformAudit = (target: string, page: number, size: number) =>
  useQuery({
    queryKey: platformKeys.audit(target, page),
    queryFn: () => http.get<Page<PlatformAuditEntry>>(`/platform/audit?${qs({ target, page, size })}`),
    placeholderData: keepPreviousData,
  });

export const platformApi = {
  suspendOrg: (id: string, reason: string) => http.post<PlatformOrgDetail>(`/platform/orgs/${id}/suspend`, { reason }),
  reactivateOrg: (id: string) => http.post<PlatformOrgDetail>(`/platform/orgs/${id}/reactivate`),
  restoreOrg: (id: string) => http.post<PlatformOrgDetail>(`/platform/orgs/${id}/restore`),
  setOrgModule: (id: string, key: string, enabled: boolean) =>
    http.put<PlatformOrgDetail>(`/platform/orgs/${id}/modules/${key}`, { enabled }),
  disableUser: (id: string, reason: string) => http.post<PlatformUserDetail>(`/platform/users/${id}/disable`, { reason }),
  enableUser: (id: string) => http.post<PlatformUserDetail>(`/platform/users/${id}/enable`),
  signOutUser: (id: string) => http.post<PlatformUserDetail>(`/platform/users/${id}/sign-out`),
  sendPasswordReset: (id: string) => http.post<PlatformUserDetail>(`/platform/users/${id}/password-reset`),
  setSuperAdmin: (id: string, enabled: boolean) => http.put<PlatformUserDetail>(`/platform/users/${id}/super-admin`, { enabled }),
};

/** A console action: afterwards every console list and count is fetched again. */
export function usePlatformMutation<V, R>(fn: (v: V) => Promise<R>) {
  const qc = useQueryClient();
  return useMutation({ mutationFn: fn, onSuccess: () => qc.invalidateQueries({ queryKey: platformKeys.all }) });
}
