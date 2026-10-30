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
