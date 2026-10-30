'use client';

// HR payroll: settings, pay set-up, monthly runs and payslips. Money is USD (strings from the API's decimals
// arrive as numbers); tax and NSSF are also given in riel.
import { useQuery } from '@tanstack/react-query';

import { getAccessToken, http } from './client';
import type { PersonRef } from './types';

export interface Bracket { upToKhr: number | null; ratePct: number }

export interface PayrollSettings {
  taxBrackets: Bracket[];
  dependantAllowanceKhr: number;
  nssfFloorKhr: number;
  nssfCapKhr: number;
  nssfPensionEmployeePct: number;
  nssfPensionEmployerPct: number;
  nssfHealthEmployerPct: number;
  nssfRiskEmployerPct: number;
  overtimeRatePct: number;
  updatedAt: string;
}

/** `monthlySalary` null = not set up (left out of pay runs). */
export interface PersonPay {
  person: PersonRef;
  department: string | null;
  monthlySalary: number | null;
  dependants: number;
  nssfEnrolled: boolean;
  bankName: string | null;
  bankAccount: string | null;
}

export type RunStatus = 'DRAFT' | 'APPROVED' | 'PAID';

export interface RunSummary {
  id: string;
  month: string;
  exchangeRate: number;
  status: RunStatus;
  note: string | null;
  totals: { people: number; gross: number; tax: number; nssfEmployee: number; nssfEmployer: number; net: number; employerCost: number };
  createdAt: string;
  approvedBy: PersonRef | null;
  approvedAt: string | null;
  paidBy: PersonRef | null;
  paidAt: string | null;
}

export interface Adjustment { label: string; amount: number }

export interface Payslip {
  id: string;
  runId: string;
  month: string;
  runStatus: RunStatus;
  orgName: string;
  person: PersonRef;
  department: string | null;
  title: string | null;
  monthlySalary: number;
  workingDays: number;
  unpaidLeaveDays: number;
  unpaidLeaveAmount: number;
  overtimeMinutes: number;
  overtimeAmount: number;
  adjustments: Adjustment[];
  gross: number;
  dependants: number;
  exchangeRate: number;
  taxableKhr: number;
  taxKhr: number;
  tax: number;
  nssfEmployee: number;
  nssfEmployer: number;
  net: number;
  bankName: string | null;
  bankAccount: string | null;
  paidAt: string | null;
}

export interface RunDetail { run: RunSummary; payslips: Payslip[]; notSetUp: PersonRef[] }

const p = (org: string) => `/orgs/${encodeURIComponent(org)}/hr/payroll`;

export const payrollKeys = {
  settings: (org: string) => ['org', org, 'hr', 'payroll', 'settings'] as const,
  people: (org: string) => ['org', org, 'hr', 'payroll', 'people'] as const,
  runs: (org: string) => ['org', org, 'hr', 'payroll', 'runs'] as const,
  run: (org: string, id: string) => ['org', org, 'hr', 'payroll', 'run', id] as const,
  mine: (org: string) => ['org', org, 'hr', 'payroll', 'mine'] as const,
  payslip: (org: string, id: string) => ['org', org, 'hr', 'payroll', 'payslip', id] as const,
};

export const usePayrollSettings = (org: string, enabled = true) =>
  useQuery({ queryKey: payrollKeys.settings(org), queryFn: () => http.get<PayrollSettings>(`${p(org)}/settings`), enabled });
export const usePayPeople = (org: string, enabled = true) =>
  useQuery({ queryKey: payrollKeys.people(org), queryFn: () => http.get<PersonPay[]>(`${p(org)}/people`), enabled });
export const usePayRuns = (org: string, enabled = true) =>
  useQuery({ queryKey: payrollKeys.runs(org), queryFn: () => http.get<RunSummary[]>(`${p(org)}/runs`), enabled });
export const usePayRun = (org: string, id: string) =>
  useQuery({ queryKey: payrollKeys.run(org, id), queryFn: () => http.get<RunDetail>(`${p(org)}/runs/${id}`) });
export const useMyPayslips = (org: string) =>
  useQuery({ queryKey: payrollKeys.mine(org), queryFn: () => http.get<Payslip[]>(`${p(org)}/payslips/mine`) });
export const usePayslip = (org: string, id: string) =>
  useQuery({ queryKey: payrollKeys.payslip(org, id), queryFn: () => http.get<Payslip>(`${p(org)}/payslips/${id}`) });

export const payrollApi = {
  saveSettings: (org: string, body: unknown) => http.put<PayrollSettings>(`${p(org)}/settings`, body),
  setPay: (org: string, userId: string, body: unknown) => http.put<PersonPay>(`${p(org)}/people/${userId}`, body),
  createRun: (org: string, body: unknown) => http.post<RunDetail>(`${p(org)}/runs`, body),
  updateRun: (org: string, id: string, body: unknown) => http.put<RunDetail>(`${p(org)}/runs/${id}`, body),
  deleteRun: (org: string, id: string) => http.delete<void>(`${p(org)}/runs/${id}`),
  act: (org: string, id: string, action: 'recalculate' | 'approve' | 'reopen' | 'pay') => http.post<RunDetail>(`${p(org)}/runs/${id}/${action}`),
  setAdjustments: (org: string, payslipId: string, adjustments: Adjustment[]) =>
    http.put<RunDetail>(`${p(org)}/payslips/${payslipId}/adjustments`, { adjustments }),
  /** Downloads the bank transfer list (CSV). */
  async downloadCsv(org: string, id: string, filename: string) {
    const res = await fetch(`/api${p(org)}/runs/${id}/export.csv`, { headers: { Authorization: `Bearer ${getAccessToken() ?? ''}` }, credentials: 'include' });
    if (!res.ok) throw new Error(res.statusText);
    const url = URL.createObjectURL(await res.blob());
    const a = Object.assign(document.createElement('a'), { href: url, download: filename });
    a.click();
    URL.revokeObjectURL(url);
  },
};
