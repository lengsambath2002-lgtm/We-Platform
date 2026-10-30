'use client';

// Bulk import of people with their place in the structure (Owners and Admins): preview first, then apply.
import { http } from './client';

export interface ImportRow {
  name: string | null; email: string | null; role: string | null; unitPath: string | null; unitKinds: string | null;
  position: string | null; managerEmail: string | null; secondManagerEmail: string | null; employmentType: string | null;
  startDate: string | null; unitHead: boolean | null;
}
export type ImportAction = 'CREATE' | 'INVITE' | 'UPDATE' | 'ERROR';
export interface RowResult { row: number; email: string | null; name: string | null; action: ImportAction; problems: string[]; notes: string[]; unit: string | null; position: string | null; manager: string | null }
export interface ImportResult { applied: boolean; rows: RowResult[]; creates: number; invites: number; updates: number; errors: number; newUnits: string[]; newPositions: string[] }

const p = (org: string) => `/orgs/${encodeURIComponent(org)}/people-import`;

export const peopleImportApi = {
  preview: (org: string, rows: ImportRow[]) => http.post<ImportResult>(`${p(org)}/preview`, { rows }),
  apply: (org: string, rows: ImportRow[]) => http.post<ImportResult>(p(org), { rows }),
};

/** Column headings understood in the file (any case; spaces, dashes and underscores ignored). */
const COLUMNS: Record<keyof ImportRow, string[]> = {
  name: ['name', 'fullname', 'member', 'employee'],
  email: ['email', 'emailaddress', 'mail'],
  role: ['role', 'access'],
  unitPath: ['unit', 'unitpath', 'department', 'path', 'team'],
  unitKinds: ['unittypes', 'unitkinds', 'types', 'kinds'],
  position: ['position', 'jobtitle', 'title'],
  managerEmail: ['manageremail', 'manager', '1stmanager', 'firstmanager', '1stmanageremail'],
  secondManagerEmail: ['2ndapproveremail', '2ndapprover', 'secondmanageremail', '2ndmanager', '2ndmanageremail', 'secondapprover'],
  employmentType: ['employmenttype', 'employment', 'type'],
  startDate: ['startdate', 'joined', 'joindate', 'start'],
  unitHead: ['unithead', 'head', 'ishead', 'lead'],
};

export const TEMPLATE_HEADERS = ['Name', 'Email', 'Role', 'Unit', 'Unit types', 'Position', 'Manager email', '2nd approver email', 'Employment type', 'Start date', 'Unit head'];
export const TEMPLATE_ROWS = [
  ['Sok Dara', 'dara@example.com', 'Member', 'Mobile Center > Mobile', 'Center > Team', 'Team Lead', 'owner@example.com', '', 'Full time', '2024-01-15', 'yes'],
  ['Chan Lina', 'lina@example.com', 'Member', 'Mobile Center > Mobile', 'Center > Team', 'Software Engineer I', 'dara@example.com', 'owner@example.com', 'Full time', '2025-03-01', ''],
];

const norm = (s: string) => s.toLowerCase().replace(/[\s_\-.]/g, '');

/** Turns parsed CSV rows (the first is the headings) into import rows; null when no Email column is found. */
export function toImportRows(table: string[][]): { rows: ImportRow[]; unknown: string[] } | null {
  const [head, ...body] = table;
  if (!head) return null;
  const index: Partial<Record<keyof ImportRow, number>> = {};
  const unknown: string[] = [];
  head.forEach((h, i) => {
    const k = (Object.keys(COLUMNS) as (keyof ImportRow)[]).find((c) => COLUMNS[c].includes(norm(h)));
    if (k && index[k] === undefined) index[k] = i; else if (h.trim()) unknown.push(h.trim());
  });
  if (index.email === undefined) return null;
  const get = (r: string[], k: keyof ImportRow) => (index[k] === undefined ? null : (r[index[k]!] ?? '').trim() || null);
  const rows = body.map((r) => ({
    name: get(r, 'name'), email: get(r, 'email'), role: get(r, 'role'), unitPath: get(r, 'unitPath'), unitKinds: get(r, 'unitKinds'),
    position: get(r, 'position'), managerEmail: get(r, 'managerEmail'), secondManagerEmail: get(r, 'secondManagerEmail'),
    employmentType: get(r, 'employmentType'), startDate: get(r, 'startDate'),
    unitHead: get(r, 'unitHead') === null ? null : ['yes', 'y', 'true', '1', 'x', 'head'].includes(get(r, 'unitHead')!.toLowerCase()),
  }));
  return { rows, unknown };
}
