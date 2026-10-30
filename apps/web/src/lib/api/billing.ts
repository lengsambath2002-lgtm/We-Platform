'use client';

// SaaS plans and an organisation's subscription. Prices are USD per member (not guests) per month or year.
import { useQuery } from '@tanstack/react-query';

import { http } from './client';

export interface Plan {
  code: string; nameEn: string; nameKm: string; priceMonthly: number; priceYearly: number; maxMembers: number | null;
  storageGb: number; modules: string[]; trialDays: number; publicPlan: boolean; sortOrder: number;
}
export type SubStatus = 'TRIALING' | 'ACTIVE' | 'PAST_DUE' | 'CANCELLED' | 'COMPLIMENTARY';
export type Cycle = 'MONTHLY' | 'YEARLY';
export interface Subscription {
  plan: Plan; status: SubStatus; billingCycle: Cycle; trialEndsAt: string | null; currentPeriodEnd: string | null; cancelAtPeriodEnd: boolean;
  usage: { members: number; maxMembers: number | null; storageBytes: number; storageLimitBytes: number };
  nextAmount: number; plans: Plan[]; canManage: boolean;
}
export interface SubscriptionBrief { orgId: string; planCode: string; status: SubStatus; billingCycle: Cycle; trialEndsAt: string | null; currentPeriodEnd: string | null; note: string | null }

const o = (org: string) => `/orgs/${encodeURIComponent(org)}/subscription`;

export const useSubscription = (org: string, enabled = true) =>
  useQuery({ queryKey: ['org', org, 'subscription'], queryFn: () => http.get<Subscription>(o(org)), enabled });
export const usePlans = () => useQuery({ queryKey: ['plans'], queryFn: () => http.get<Plan[]>('/plans') });
export const usePlatformPlans = () => useQuery({ queryKey: ['platform', 'plans'], queryFn: () => http.get<Plan[]>('/platform/plans') });

export const billingApi = {
  change: (org: string, planCode: string, cycle: Cycle) => http.put<Subscription>(o(org), { planCode, cycle }),
  cancel: (org: string) => http.post<Subscription>(`${o(org)}/cancel`),
  savePlan: (code: string, body: unknown) => http.put<Plan>(`/platform/plans/${code}`, body),
  setSubscription: (orgId: string, body: unknown) => http.put<unknown>(`/platform/orgs/${orgId}/subscription`, body),
};

/** Days left until {@code iso} (rounded up; 0 when past). */
export const daysUntil = (iso: string | null) => (iso ? Math.max(0, Math.ceil((new Date(iso).getTime() - Date.now()) / 86400000)) : 0);

export interface ModuleInfo { key: string; nameEn: string; nameKm: string; kind: string; status: string; sortOrder: number }
/** The platform's module registry (for plan editing). */
export const useModuleCatalogue = () => useQuery({ queryKey: ['modules'], queryFn: () => http.get<ModuleInfo[]>('/modules') });

export interface PayTo { sellerName: string; sellerAddress: string; sellerTaxId: string | null; bankName: string | null; bankAccountName: string | null; bankAccountNumber: string | null; khqr: boolean }
export type InvoiceStatus = 'OPEN' | 'PAID' | 'VOID';
export interface Invoice {
  id: string; orgId: string; orgName: string; number: string; planCode: string; billingCycle: Cycle; seats: number; unitPrice: number; amount: number;
  periodStart: string; periodEnd: string; status: InvoiceStatus; issuedAt: string; dueAt: string; overdue: boolean; khqr: string | null;
  claimedAt: string | null; claimReference: string | null; paidAt: string | null; paymentMethod: string | null; paymentReference: string | null;
  voidReason: string | null; payTo: PayTo;
}

export const useInvoices = (org: string, enabled = true) =>
  useQuery({ queryKey: ['org', org, 'invoices'], queryFn: () => http.get<Invoice[]>(`/orgs/${encodeURIComponent(org)}/invoices`), enabled });
export const useInvoice = (org: string, id: string) =>
  useQuery({ queryKey: ['org', org, 'invoices', id], queryFn: () => http.get<Invoice>(`/orgs/${encodeURIComponent(org)}/invoices/${id}`) });
export const usePlatformInvoices = (status: string) =>
  useQuery({ queryKey: ['platform', 'invoices', status], queryFn: () => http.get<Invoice[]>(`/platform/invoices${status ? `?status=${status}` : ''}`) });

export const invoiceApi = {
  claim: (org: string, id: string, reference: string) => http.post<Invoice>(`/orgs/${encodeURIComponent(org)}/invoices/${id}/claim`, { reference }),
  record: (id: string, method: string, reference: string | null) => http.post<Invoice>(`/platform/invoices/${id}/payment`, { method, reference }),
  void: (id: string, reason: string) => http.post<Invoice>(`/platform/invoices/${id}/void`, { reason }),
};
