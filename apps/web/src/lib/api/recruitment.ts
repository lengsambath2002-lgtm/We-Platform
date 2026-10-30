'use client';

// HR recruitment: job openings, candidates, interviews, hiring and onboarding.
import { useQuery } from '@tanstack/react-query';

import { api, getAccessToken, http } from './client';
import type { EmploymentType } from './hr';
import type { PersonRef } from './types';

export type JobStatus = 'OPEN' | 'ON_HOLD' | 'CLOSED';
export const STAGES = ['NEW', 'SCREENING', 'INTERVIEW', 'OFFER', 'HIRED', 'REJECTED'] as const;
export type Stage = (typeof STAGES)[number];
export const SOURCES = ['REFERRAL', 'JOB_BOARD', 'WEBSITE', 'WALK_IN', 'AGENCY', 'OTHER'] as const;
export type Source = (typeof SOURCES)[number];
export const RECOMMENDATIONS = ['STRONG_YES', 'YES', 'NO', 'STRONG_NO'] as const;
export type Recommendation = (typeof RECOMMENDATIONS)[number];

export interface Job {
  id: string;
  title: string;
  department: string | null;
  employmentType: EmploymentType | null;
  location: string | null;
  description: string | null;
  openings: number;
  hiringManager: PersonRef | null;
  status: JobStatus;
  stages: Partial<Record<Stage, number>>;
  createdAt: string;
  canManage: boolean;
}

export interface CandidateSummary {
  id: string;
  jobId: string;
  name: string;
  email: string | null;
  phone: string | null;
  source: Source;
  stage: Stage;
  rating: number | null;
  hasCv: boolean;
  nextInterview: string | null;
  createdAt: string;
}

export interface PanelEntry { person: PersonRef; recommendation: Recommendation | null; notes: string | null; submittedAt: string | null }

export interface Interview {
  id: string;
  candidateId: string;
  candidateName: string;
  jobTitle: string;
  startsAt: string;
  durationMinutes: number;
  location: string | null;
  panel: PanelEntry[];
  canGiveFeedback: boolean;
}

export interface OnboardingStep { id: string; title: string; dueDate: string | null; done: boolean; doneAt: string | null; doneBy: PersonRef | null }

export interface CandidateDetail {
  candidate: CandidateSummary;
  job: Job;
  notes: string | null;
  rejectedReason: string | null;
  startDate: string | null;
  cvName: string | null;
  cvSize: number | null;
  invited: boolean;
  joined: boolean;
  interviews: Interview[];
  onboarding: OnboardingStep[];
  canManage: boolean;
  canHire: boolean;
}

export interface Hire { candidate: CandidateSummary; jobTitle: string; startDate: string | null; done: number; total: number; joined: boolean }

export interface TemplateStep { title: string; dueDays: number }

const r = (org: string) => `/orgs/${encodeURIComponent(org)}/hr/recruitment`;

export const recruitmentKeys = {
  jobs: (org: string) => ['org', org, 'hr', 'rec', 'jobs'] as const,
  job: (org: string, id: string) => ['org', org, 'hr', 'rec', 'job', id] as const,
  candidates: (org: string, jobId: string) => ['org', org, 'hr', 'rec', 'candidates', jobId] as const,
  candidate: (org: string, id: string) => ['org', org, 'hr', 'rec', 'candidate', id] as const,
  myInterviews: (org: string) => ['org', org, 'hr', 'rec', 'interviews'] as const,
  hires: (org: string) => ['org', org, 'hr', 'rec', 'hires'] as const,
  template: (org: string) => ['org', org, 'hr', 'rec', 'template'] as const,
};

export const useJobs = (org: string) => useQuery({ queryKey: recruitmentKeys.jobs(org), queryFn: () => http.get<Job[]>(`${r(org)}/jobs`) });
export const useJob = (org: string, id: string) => useQuery({ queryKey: recruitmentKeys.job(org, id), queryFn: () => http.get<Job>(`${r(org)}/jobs/${id}`) });
export const useCandidates = (org: string, jobId: string) =>
  useQuery({ queryKey: recruitmentKeys.candidates(org, jobId), queryFn: () => http.get<CandidateSummary[]>(`${r(org)}/jobs/${jobId}/candidates`) });
export const useCandidate = (org: string, id: string) =>
  useQuery({ queryKey: recruitmentKeys.candidate(org, id), queryFn: () => http.get<CandidateDetail>(`${r(org)}/candidates/${id}`) });
export const useMyInterviews = (org: string) =>
  useQuery({ queryKey: recruitmentKeys.myInterviews(org), queryFn: () => http.get<Interview[]>(`${r(org)}/interviews/mine`) });
export const useHires = (org: string, enabled = true) =>
  useQuery({ queryKey: recruitmentKeys.hires(org), queryFn: () => http.get<Hire[]>(`${r(org)}/hires`), enabled });
export const useOnboardingTemplate = (org: string, enabled = true) =>
  useQuery({ queryKey: recruitmentKeys.template(org), queryFn: () => http.get<{ items: TemplateStep[] }>(`${r(org)}/onboarding-template`), enabled });

export const recruitmentApi = {
  saveJob: (org: string, id: string | null, body: unknown) => (id ? http.put<Job>(`${r(org)}/jobs/${id}`, body) : http.post<Job>(`${r(org)}/jobs`, body)),
  addCandidate: (org: string, jobId: string, body: unknown) => http.post<CandidateDetail>(`${r(org)}/jobs/${jobId}/candidates`, body),
  updateCandidate: (org: string, id: string, body: unknown) => http.put<CandidateDetail>(`${r(org)}/candidates/${id}`, body),
  deleteCandidate: (org: string, id: string) => http.delete<void>(`${r(org)}/candidates/${id}`),
  move: (org: string, id: string, stage: Stage, reason?: string) => http.post<CandidateDetail>(`${r(org)}/candidates/${id}/stage`, { stage, reason: reason ?? null }),
  hire: (org: string, id: string, startDate: string, invite: boolean) => http.post<CandidateDetail>(`${r(org)}/candidates/${id}/hire`, { startDate, invite }),
  uploadCv: (org: string, id: string, file: File) => {
    const fd = new FormData();
    fd.append('file', file);
    return api<CandidateDetail>(`${r(org)}/candidates/${id}/cv`, { method: 'POST', body: fd });
  },
  async downloadCv(org: string, id: string, filename: string) {
    const res = await fetch(`/api${r(org)}/candidates/${id}/cv`, { headers: { Authorization: `Bearer ${getAccessToken() ?? ''}` }, credentials: 'include' });
    if (!res.ok) throw new Error(res.statusText);
    const url = URL.createObjectURL(await res.blob());
    Object.assign(document.createElement('a'), { href: url, download: filename }).click();
    URL.revokeObjectURL(url);
  },
  schedule: (org: string, id: string, body: unknown) => http.post<CandidateDetail>(`${r(org)}/candidates/${id}/interviews`, body),
  cancelInterview: (org: string, interviewId: string) => http.delete<CandidateDetail>(`${r(org)}/interviews/${interviewId}`),
  feedback: (org: string, interviewId: string, body: unknown) => http.put<CandidateDetail>(`${r(org)}/interviews/${interviewId}/feedback`, body),
  addStep: (org: string, candidateId: string, body: unknown) => http.post<CandidateDetail>(`${r(org)}/candidates/${candidateId}/onboarding`, body),
  setStepDone: (org: string, stepId: string, done: boolean) => http.put<CandidateDetail>(`${r(org)}/onboarding/${stepId}`, { done }),
  removeStep: (org: string, stepId: string) => http.delete<CandidateDetail>(`${r(org)}/onboarding/${stepId}`),
  saveTemplate: (org: string, items: TemplateStep[]) => http.put<{ items: TemplateStep[] }>(`${r(org)}/onboarding-template`, { items }),
};
