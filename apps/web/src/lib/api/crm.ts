'use client';

// General Business: the sales pipeline (deals) and the finance reports.
import { useQuery } from '@tanstack/react-query';

import type { CustomerRef } from './business';
import { getAccessToken, http } from './client';
import type { PersonRef } from './types';

export const DEAL_STAGES = ['LEAD', 'QUALIFIED', 'PROPOSAL', 'NEGOTIATION', 'WON', 'LOST'] as const;
export type DealStage = (typeof DEAL_STAGES)[number];
export const OPEN_STAGES: DealStage[] = ['LEAD', 'QUALIFIED', 'PROPOSAL', 'NEGOTIATION'];
export const DEAL_SOURCES = ['REFERRAL', 'WEBSITE', 'SOCIAL', 'EVENT', 'COLD_CALL', 'OTHER'] as const;
export type DealSource = (typeof DEAL_SOURCES)[number];
export const ACTIVITY_KINDS = ['CALL', 'MEETING', 'EMAIL', 'NOTE'] as const;
export type ActivityKind = (typeof ACTIVITY_KINDS)[number];

export interface Deal {
  id: string;
  title: string;
  customer: CustomerRef | null;
  company: string | null;
  contactName: string | null;
  email: string | null;
  phone: string | null;
  value: number;
  stage: DealStage;
  probability: number;
  source: DealSource;
  expectedClose: string | null;
  owner: PersonRef;
  nextStep: string | null;
  nextStepDate: string | null;
  followUpDue: boolean;
  quote: { id: string; number: string | null; status: string } | null;
  lostReason: string | null;
  closedAt: string | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
  canEdit: boolean;
}

export interface DealActivity { id: string; kind: ActivityKind; body: string; happenedOn: string; by: PersonRef; createdAt: string }

export interface Pipeline {
  stages: { stage: DealStage; count: number; value: number; weighted: number }[];
  openValue: number;
  weighted: number;
  wonThisMonth: number;
  wonCountThisMonth: number;
  winRate: number | null;
  deals: Deal[];
}

export interface Aging { current: number; days1to30: number; days31to60: number; days61to90: number; over90: number; total: number }

export interface FinanceReport {
  year: number;
  months: { month: string; sales: number; collected: number; expenses: number; profit: number }[];
  totals: { sales: number; collected: number; expenses: number; profit: number };
  aging: Aging;
  agingByCustomer: { customerId: string; customer: string; aging: Aging }[];
  customers: { customerId: string; customer: string; invoices: number; invoiced: number; paid: number }[];
  categories: { categoryId: string; nameEn: string; nameKm: string; count: number; total: number }[];
}

const b = (org: string) => `/orgs/${encodeURIComponent(org)}/business`;

export const crmKeys = {
  pipeline: (org: string) => ['org', org, 'business', 'deals'] as const,
  deal: (org: string, id: string) => ['org', org, 'business', 'deal', id] as const,
  report: (org: string, year: number) => ['org', org, 'business', 'report', year] as const,
};

export const usePipeline = (org: string) => useQuery({ queryKey: crmKeys.pipeline(org), queryFn: () => http.get<Pipeline>(`${b(org)}/deals`) });
export const useDeal = (org: string, id: string) =>
  useQuery({ queryKey: crmKeys.deal(org, id), queryFn: () => http.get<{ deal: Deal; activities: DealActivity[] }>(`${b(org)}/deals/${id}`) });
export const useFinanceReport = (org: string, year: number, enabled = true) =>
  useQuery({ queryKey: crmKeys.report(org, year), queryFn: () => http.get<FinanceReport>(`${b(org)}/reports?year=${year}`), enabled });

export const crmApi = {
  saveDeal: (org: string, id: string | null, body: unknown) =>
    (id ? http.put<{ deal: Deal }>(`${b(org)}/deals/${id}`, body) : http.post<{ deal: Deal }>(`${b(org)}/deals`, body)),
  deleteDeal: (org: string, id: string) => http.delete<void>(`${b(org)}/deals/${id}`),
  move: (org: string, id: string, stage: DealStage, reason?: string) => http.post<unknown>(`${b(org)}/deals/${id}/stage`, { stage, reason: reason ?? null }),
  addActivity: (org: string, id: string, body: unknown) => http.post<unknown>(`${b(org)}/deals/${id}/activities`, body),
  convert: (org: string, id: string) => http.post<unknown>(`${b(org)}/deals/${id}/convert`),
  startQuote: (org: string, id: string) => http.post<{ id: string }>(`${b(org)}/deals/${id}/quote`),
  async downloadReport(org: string, which: 'months' | 'aging' | 'customers' | 'expenses', year: number) {
    const res = await fetch(`/api${b(org)}/reports/${which}.csv?year=${year}`, { headers: { Authorization: `Bearer ${getAccessToken() ?? ''}` }, credentials: 'include' });
    if (!res.ok) throw new Error(res.statusText);
    const url = URL.createObjectURL(await res.blob());
    Object.assign(document.createElement('a'), { href: url, download: `${which}-${year}.csv` }).click();
    URL.revokeObjectURL(url);
  },
};
