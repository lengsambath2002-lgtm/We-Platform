'use client';

// The organisation structure: units (branch → … → team), positions with a planned headcount, and who sits where.
import { useQuery } from '@tanstack/react-query';

import { http } from './client';

export const UNIT_KINDS = ['BRANCH', 'DIVISION', 'DEPARTMENT', 'CENTER', 'SECTION', 'TEAM'] as const;
export type UnitKind = (typeof UNIT_KINDS)[number];

export interface PersonBrief { userId: string; membershipId: string; name: string; avatarUrl: string | null }

export interface Unit {
  id: string;
  parentId: string | null;
  kind: UnitKind;
  name: string;
  code: string | null;
  head: PersonBrief | null;
  address: string | null;
  phone: string | null;
  archived: boolean;
  /** Active members placed directly in the unit. */
  people: number;
}

export interface Position {
  id: string;
  unitId: string | null;
  title: string;
  code: string | null;
  grade: string | null;
  reportsToId: string | null;
  headcount: number;
  filled: number;
  description: string | null;
  archived: boolean;
}

export interface Assignment { id: string; person: PersonBrief; title: string | null; unitId: string | null; positionId: string | null; primary: boolean }

export interface Structure { units: Unit[]; positions: Position[]; assignments: Assignment[]; canManage: boolean }

export interface Placement { unitId: string | null; positionId: string | null }

const o = (org: string) => `/orgs/${encodeURIComponent(org)}`;

export const structureKeys = {
  all: (org: string) => ['org', org, 'structure'] as const,
};

export const useStructure = (org: string, enabled = true) =>
  useQuery({ queryKey: structureKeys.all(org), queryFn: () => http.get<Structure>(`${o(org)}/structure`), enabled });

export const structureApi = {
  saveUnit: (org: string, id: string | null, body: unknown) => (id ? http.put<Unit>(`${o(org)}/structure/units/${id}`, body) : http.post<Unit>(`${o(org)}/structure/units`, body)),
  archiveUnit: (org: string, id: string, archived: boolean) => http.post<Unit>(`${o(org)}/structure/units/${id}/${archived ? 'archive' : 'restore'}`),
  deleteUnit: (org: string, id: string) => http.delete<void>(`${o(org)}/structure/units/${id}`),
  savePosition: (org: string, id: string | null, body: unknown) => (id ? http.put<Position>(`${o(org)}/structure/positions/${id}`, body) : http.post<Position>(`${o(org)}/structure/positions`, body)),
  archivePosition: (org: string, id: string, archived: boolean) => http.post<Position>(`${o(org)}/structure/positions/${id}/${archived ? 'archive' : 'restore'}`),
  deletePosition: (org: string, id: string) => http.delete<void>(`${o(org)}/structure/positions/${id}`),
  place: (org: string, membershipId: string, body: { primary: Placement | null; additional: Placement[] }) =>
    http.put<Assignment[]>(`${o(org)}/members/${membershipId}/placements`, body),
};

/** "Phnom Penh › Sales › Team A": a unit's path from the top. */
export function unitPath(units: Unit[], id: string | null): string {
  const byId = new Map(units.map((u) => [u.id, u]));
  const names: string[] = [];
  const seen = new Set<string>();
  for (let at = id ? byId.get(id) : undefined; at && !seen.has(at.id); at = at.parentId ? byId.get(at.parentId) : undefined) {
    seen.add(at.id);
    names.unshift(at.name);
  }
  return names.join(' › ');
}

/** Units in tree order (parents before children, siblings by name), each with its depth. */
export function unitTree(units: Unit[], includeArchived = false): { unit: Unit; depth: number }[] {
  const shown = units.filter((u) => includeArchived || !u.archived);
  const ids = new Set(shown.map((u) => u.id));
  const children = new Map<string | null, Unit[]>();
  shown.forEach((u) => {
    const parent = u.parentId && ids.has(u.parentId) ? u.parentId : null;
    children.set(parent, [...(children.get(parent) ?? []), u]);
  });
  const out: { unit: Unit; depth: number }[] = [];
  const walk = (parent: string | null, depth: number) => {
    (children.get(parent) ?? []).sort((a, b) => a.name.localeCompare(b.name)).forEach((u) => {
      out.push({ unit: u, depth });
      if (depth < 30) walk(u.id, depth + 1);
    });
  };
  walk(null, 0);
  return out;
}
