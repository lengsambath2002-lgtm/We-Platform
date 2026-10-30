'use client';

// Employee records: personal details, contracts, documents, salary history and what expires soon.
import { useQuery } from '@tanstack/react-query';

import { api, getAccessToken, http } from './client';
import type { PersonRef } from './types';

export const CONTRACT_KINDS = ['PROBATION', 'FIXED_TERM', 'UNLIMITED', 'INTERNSHIP', 'OTHER'] as const;
export type ContractKind = (typeof CONTRACT_KINDS)[number];
export const DOC_CATEGORIES = ['ID', 'CONTRACT', 'CERTIFICATE', 'MEDICAL', 'OTHER'] as const;
export type DocCategory = (typeof DOC_CATEGORIES)[number];

export interface Personal {
  nickname: string | null; duty: string | null; category: string | null; gender: 'MALE' | 'FEMALE' | 'OTHER' | null; dateOfBirth: string | null;
  nationality: string | null; idType: 'NATIONAL_ID' | 'PASSPORT' | 'OTHER' | null; idNumber: string | null; idExpiresOn: string | null;
  nssfNumber: string | null; homeAddress: string | null; personalEmail: string | null; personalPhone: string | null;
}
export interface Contract {
  id: string; kind: ContractKind; startsOn: string; endsOn: string | null; probationEndsOn: string | null; monthlySalary: number | null;
  note: string | null; fileName: string | null; status: 'UPCOMING' | 'ACTIVE' | 'ENDED'; createdBy: PersonRef | null; createdAt: string;
}
export interface EmployeeDoc { id: string; category: DocCategory; title: string; expiresOn: string | null; fileName: string | null; sizeBytes: number; uploadedBy: PersonRef | null; createdAt: string; canDelete: boolean }
export interface Records { personal: Personal; contracts: Contract[]; documents: EmployeeDoc[]; shown: boolean; canEdit: boolean; self: boolean }
export interface ExpiringItem { kind: 'CONTRACT_END' | 'PROBATION_END' | 'ID_EXPIRY' | 'DOCUMENT_EXPIRY'; person: PersonRef; date: string; days: number; label: string | null }
export interface PayChange { id: string; monthlySalary: number; previous: number | null; effectiveOn: string; reason: string | null; changedBy: PersonRef | null; createdAt: string }

const h = (org: string) => `/orgs/${encodeURIComponent(org)}/hr`;

export const useRecords = (org: string, userId: string) =>
  useQuery({ queryKey: ['org', org, 'hr', 'records', userId], queryFn: () => http.get<Records>(`${h(org)}/people/${userId}/records`) });
export const useExpiring = (org: string, enabled = true) =>
  useQuery({ queryKey: ['org', org, 'hr', 'expiring'], queryFn: () => http.get<ExpiringItem[]>(`${h(org)}/records/expiring?days=30`), enabled });
export const usePayHistory = (org: string, userId: string, enabled = true) =>
  useQuery({ queryKey: ['org', org, 'hr', 'pay-history', userId], queryFn: () => http.get<PayChange[]>(`${h(org)}/payroll/people/${userId}/history`), enabled });

export const recordsApi = {
  personal: (org: string, userId: string, body: unknown) => http.put<Records>(`${h(org)}/people/${userId}/personal`, body),
  addContract: (org: string, userId: string, body: unknown) => http.post<Records>(`${h(org)}/people/${userId}/contracts`, body),
  updateContract: (org: string, id: string, body: unknown) => http.put<Records>(`${h(org)}/contracts/${id}`, body),
  deleteContract: (org: string, id: string) => http.delete<Records>(`${h(org)}/contracts/${id}`),
  contractFile: (org: string, id: string, file: File) => {
    const fd = new FormData();
    fd.append('file', file);
    return api<Records>(`${h(org)}/contracts/${id}/file`, { method: 'POST', body: fd });
  },
  addDocument: (org: string, userId: string, file: File, category: DocCategory, title: string, expiresOn: string | null) => {
    const fd = new FormData();
    fd.append('file', file);
    fd.append('category', category);
    fd.append('title', title);
    if (expiresOn) fd.append('expiresOn', expiresOn);
    return api<Records>(`${h(org)}/people/${userId}/documents`, { method: 'POST', body: fd });
  },
  deleteDocument: (org: string, id: string) => http.delete<Records>(`${h(org)}/documents/${id}`),
  /** Downloads a contract's or document's file through the API (with the session). */
  async download(org: string, kind: 'contracts' | 'documents', id: string, filename: string) {
    const res = await fetch(`/api${h(org)}/${kind}/${id}/file`, { headers: { Authorization: `Bearer ${getAccessToken() ?? ''}` }, credentials: 'include' });
    if (!res.ok) throw new Error(res.statusText);
    const url = URL.createObjectURL(await res.blob());
    Object.assign(document.createElement('a'), { href: url, download: filename }).click();
    URL.revokeObjectURL(url);
  },
};
