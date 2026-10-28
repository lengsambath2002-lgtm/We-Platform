'use client';

// Single sign-on: the /oauth/authorize page's calls and the account page's connected apps;
// Retail & Commerce: the organisation's linked VirtualBiz shops.
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { http } from './client';

/** What the authorize page does next (see the API's AuthorizeCheck). */
export interface AuthorizeCheck {
  outcome: 'INVALID' | 'REDIRECT' | 'SIGN_IN' | 'CONSENT' | 'CONTINUE';
  error: 'invalid_client' | 'invalid_redirect_uri' | null;
  clientId: string | null;
  clientName: string | null;
  scopes: string[];
  redirectUrl: string | null;
}

/** The authorize request's query, passed through as received. */
export type AuthorizeParams = Record<string, string>;

export interface ConnectedApp {
  clientId: string;
  name: string;
  scopes: string[];
  grantedAt: string;
}

export interface LinkedShop {
  businessId: string;
  businessName: string;
  linkedAt: string;
  linkedBy: { id: string; name: string } | null;
}

export interface RetailShops {
  configured: boolean;
  virtualbizUrl: string | null;
  orgId: string;
  canManage: boolean;
  shops: LinkedShop[];
}

export const ssoApi = {
  check: (params: AuthorizeParams) => http.get<AuthorizeCheck>(`/oidc/authorize/check?${new URLSearchParams(params)}`),
  decide: (request: AuthorizeParams, approve: boolean) =>
    http.post<{ redirectUrl: string }>('/oidc/authorize', { request, approve }),
};

const appsKey = ['me', 'connected-apps'] as const;
const shopsKey = (org: string) => ['org', org, 'retail', 'shops'] as const;

export const useConnectedApps = () => useQuery({ queryKey: appsKey, queryFn: () => http.get<ConnectedApp[]>('/me/connected-apps') });

export function useRevokeApp() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (clientId: string) => http.delete<void>(`/me/connected-apps/${encodeURIComponent(clientId)}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: appsKey }),
  });
}

export const useRetailShops = (org: string, enabled = true) =>
  useQuery({ queryKey: shopsKey(org), queryFn: () => http.get<RetailShops>(`/orgs/${encodeURIComponent(org)}/retail/shops`), enabled });

export function useUnlinkShop(org: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (businessId: string) => http.delete<void>(`/orgs/${encodeURIComponent(org)}/retail/shops/${businessId}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: shopsKey(org) }),
  });
}

/** VirtualBiz pages that start single sign-on (contract §5). */
export const virtualbiz = {
  open: (base: string, businessId: string) => `${base}/platform/open?business=${encodeURIComponent(businessId)}`,
  connect: (base: string, orgId: string) => `${base}/platform/connect?org=${encodeURIComponent(orgId)}`,
};
