'use client';

// Account security (password, signed-in devices, password reset) and organisation settings
// (rename, leave, delete, resend an invitation).
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { api, http } from './client';
import { keys } from './queries';
import type { Invitation, OrgSummary, Session } from './types';

/** One signed-in device; `id` stays the same across token refreshes. */
export interface DeviceSession {
  id: string;
  userAgent: string | null;
  ip: string | null;
  signedInAt: string;
  lastUsedAt: string;
  current: boolean;
}

/** An invitation just created or resent: `emailed` when the API also sent it by email. */
export type SentInvitation = Invitation & { emailed: boolean };

const o = (org: string) => `/orgs/${encodeURIComponent(org)}`;
const sessionsKey = ['me', 'sessions'] as const;

export const accountApi = {
  changePassword: (currentPassword: string, newPassword: string) =>
    http.post<void>('/me/password', { currentPassword, newPassword }),
  forgotPassword: (email: string) => http.post<void>('/auth/forgot-password', { email }),
  checkResetToken: (token: string) => http.post<{ valid: boolean }>('/auth/reset-password/check', { token }),
  resetPassword: (token: string, password: string) => http.post<Session>('/auth/reset-password', { token, password }),
  renameOrg: (org: string, name: string) => http.patch<OrgSummary>(o(org), { name }),
  leaveOrg: (org: string) => http.post<void>(`${o(org)}/leave`),
  /** The organisation's exact name confirms the deletion. */
  deleteOrg: (org: string, name: string) => api<void>(o(org), { method: 'DELETE', body: JSON.stringify({ name }) }),
  resendInvitation: (org: string, id: string) => http.post<SentInvitation>(`${o(org)}/invitations/${id}/resend`),
};

export const useDeviceSessions = () =>
  useQuery({ queryKey: sessionsKey, queryFn: () => http.get<DeviceSession[]>('/me/sessions') });

export function useSignOutDevice() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => http.delete<void>(`/me/sessions/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: sessionsKey }),
  });
}

export function useSignOutOtherDevices() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => http.post<{ count: number }>('/me/sessions/sign-out-others'),
    onSuccess: () => qc.invalidateQueries({ queryKey: sessionsKey }),
  });
}

/** Leaving or deleting an organisation: forget its cached data and refresh my list of organisations. */
export function useLeaveOrDeleteOrg() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ org, action, name }: { org: string; action: 'leave' | 'delete'; name?: string }) =>
      action === 'leave' ? accountApi.leaveOrg(org) : accountApi.deleteOrg(org, name ?? ''),
    onSuccess: (_, { org }) => {
      qc.removeQueries({ queryKey: ['org', org] });
      try { if (localStorage.getItem('bp-last-org') === org) localStorage.removeItem('bp-last-org'); } catch { /* ignore */ }
      return qc.invalidateQueries({ queryKey: keys.orgs });
    },
  });
}

/** A short device description from a user agent: "Chrome on macOS". */
export function describeAgent(ua: string | null): { browser: string | null; os: string | null; mobile: boolean } {
  if (!ua) return { browser: null, os: null, mobile: false };
  const browser = /Edg\//.test(ua) ? 'Edge' : /OPR\/|Opera/.test(ua) ? 'Opera' : /Firefox\//.test(ua) ? 'Firefox'
    : /Chrome\/|CriOS\//.test(ua) ? 'Chrome' : /Safari\//.test(ua) ? 'Safari' : /okhttp|Dalvik|Expo|CFNetwork/i.test(ua) ? 'App' : null;
  const os = /iPhone|iPad|iPod/.test(ua) ? 'iOS' : /Android/.test(ua) ? 'Android' : /Mac OS X|Macintosh/.test(ua) ? 'macOS'
    : /Windows/.test(ua) ? 'Windows' : /CrOS/.test(ua) ? 'ChromeOS' : /Linux/.test(ua) ? 'Linux' : null;
  return { browser, os, mobile: /Mobi|iPhone|Android/.test(ua) };
}
