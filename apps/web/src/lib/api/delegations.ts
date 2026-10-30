'use client';

// Acting approvers: while someone is away, another member decides their approval steps for them.
import { useQuery } from '@tanstack/react-query';

import { http } from './client';

export interface Delegation {
  id: string;
  user: { id: string; name: string };
  delegate: { id: string; name: string };
  startsOn: string;
  endsOn: string;
  reason: string | null;
  /** Covers today. */
  active: boolean;
  canRevoke: boolean;
}
export interface Delegations { mine: Delegation[]; all: Delegation[]; canManage: boolean }

const d = (org: string) => `/orgs/${encodeURIComponent(org)}/approvals/delegations`;

export const useDelegations = (org: string) =>
  useQuery({ queryKey: ['org', org, 'approvals', 'delegations'], queryFn: () => http.get<Delegations>(d(org)) });

export const delegationsApi = {
  create: (org: string, body: { userId: string | null; delegateId: string; startsOn: string; endsOn: string; reason: string | null }) =>
    http.post<Delegation>(d(org), body),
  end: (org: string, id: string) => http.delete<void>(`${d(org)}/${id}`),
};
