'use client';

// Custom roles: a named set of permissions from the platform's catalogue, granted to members organisation-wide or
// for one unit and everything below it. Owners and Admins only.
import { useQuery } from '@tanstack/react-query';

import { http } from './client';
import type { PersonBrief } from './structure';

export interface CatalogueEntry { code: string; module: string | null; scoped: boolean; available: boolean }
export interface CustomRole { id: string; name: string; description: string | null; permissions: string[]; scopable: boolean; holders: number }
export interface RoleGrant { id: string; roleId: string; person: PersonBrief; unitId: string | null; unitName: string | null; createdAt: string }
export interface RolesView { catalogue: CatalogueEntry[]; roles: CustomRole[]; grants: RoleGrant[] }

const r = (org: string) => `/orgs/${encodeURIComponent(org)}/roles`;

export const useRoles = (org: string, enabled = true) =>
  useQuery({ queryKey: ['org', org, 'roles'], queryFn: () => http.get<RolesView>(r(org)), enabled });

export const rolesApi = {
  save: (org: string, id: string | null, body: unknown) => (id ? http.put<CustomRole>(`${r(org)}/${id}`, body) : http.post<CustomRole>(r(org), body)),
  remove: (org: string, id: string) => http.delete<void>(`${r(org)}/${id}`),
  grant: (org: string, id: string, body: { membershipId: string; unitId: string | null }) => http.post<RoleGrant>(`${r(org)}/${id}/grants`, body),
  revoke: (org: string, grantId: string) => http.delete<void>(`${r(org)}/grants/${grantId}`),
};
