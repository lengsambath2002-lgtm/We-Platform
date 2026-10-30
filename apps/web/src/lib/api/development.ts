'use client';

// HR performance reviews and training.
import { useQuery } from '@tanstack/react-query';

import { http } from './client';
import type { PersonRef } from './types';

export type CycleStatus = 'DRAFT' | 'OPEN' | 'CLOSED';
export type ReviewStatus = 'SELF' | 'MANAGER' | 'DONE' | 'ACKNOWLEDGED';

export interface Cycle {
  id: string;
  name: string;
  periodStart: string;
  periodEnd: string;
  dueDate: string;
  questions: string[];
  status: CycleStatus;
  total: number;
  selfDone: number;
  reviewed: number;
  acknowledged: number;
  launchedAt: string | null;
  closedAt: string | null;
}

export interface ReviewRow {
  id: string;
  cycleId: string;
  cycleName: string;
  dueDate: string;
  cycleStatus: CycleStatus;
  employee: PersonRef;
  reviewer: PersonRef;
  status: ReviewStatus;
  overallRating: number | null;
}

export interface Answer { rating: number | null; comment: string | null }

export interface Review {
  id: string;
  cycle: Cycle;
  employee: PersonRef;
  reviewer: PersonRef;
  status: ReviewStatus;
  selfAnswers: Answer[] | null;
  selfSummary: string | null;
  selfSubmittedAt: string | null;
  managerAnswers: Answer[] | null;
  managerSummary: string | null;
  overallRating: number | null;
  managerSubmittedAt: string | null;
  acknowledgedAt: string | null;
  canEditSelf: boolean;
  canEditReview: boolean;
  canAcknowledge: boolean;
  canChangeReviewer: boolean;
}

export type EnrolmentStatus = 'ENROLLED' | 'COMPLETED' | 'CANCELLED';

export interface Course {
  id: string;
  title: string;
  description: string | null;
  provider: string | null;
  location: string | null;
  startsOn: string | null;
  endsOn: string | null;
  hours: number | null;
  capacity: number | null;
  selfEnrol: boolean;
  status: 'OPEN' | 'CLOSED';
  enrolled: number;
  completed: number;
  myStatus: EnrolmentStatus | null;
  myEnrolmentId: string | null;
  canEnrol: boolean;
}

export interface Enrolment { id: string; person: PersonRef; status: EnrolmentStatus; enrolledBy: PersonRef | null; completedAt: string | null; createdAt: string }

const h = (org: string) => `/orgs/${encodeURIComponent(org)}/hr`;

export const developmentKeys = {
  cycles: (org: string) => ['org', org, 'hr', 'dev', 'cycles'] as const,
  cycle: (org: string, id: string) => ['org', org, 'hr', 'dev', 'cycle', id] as const,
  myReviews: (org: string) => ['org', org, 'hr', 'dev', 'reviews', 'mine'] as const,
  toReview: (org: string) => ['org', org, 'hr', 'dev', 'reviews', 'to-review'] as const,
  review: (org: string, id: string) => ['org', org, 'hr', 'dev', 'review', id] as const,
  courses: (org: string) => ['org', org, 'hr', 'dev', 'courses'] as const,
  course: (org: string, id: string) => ['org', org, 'hr', 'dev', 'course', id] as const,
  myTraining: (org: string) => ['org', org, 'hr', 'dev', 'training', 'mine'] as const,
};

export const useCycles = (org: string, enabled = true) =>
  useQuery({ queryKey: developmentKeys.cycles(org), queryFn: () => http.get<Cycle[]>(`${h(org)}/review-cycles`), enabled });
export const useCycle = (org: string, id: string) =>
  useQuery({ queryKey: developmentKeys.cycle(org, id), queryFn: () => http.get<{ cycle: Cycle; reviews: ReviewRow[] }>(`${h(org)}/review-cycles/${id}`) });
export const useMyReviews = (org: string) =>
  useQuery({ queryKey: developmentKeys.myReviews(org), queryFn: () => http.get<ReviewRow[]>(`${h(org)}/reviews/mine`) });
export const useToReview = (org: string) =>
  useQuery({ queryKey: developmentKeys.toReview(org), queryFn: () => http.get<ReviewRow[]>(`${h(org)}/reviews/to-review`) });
export const useReview = (org: string, id: string) =>
  useQuery({ queryKey: developmentKeys.review(org, id), queryFn: () => http.get<Review>(`${h(org)}/reviews/${id}`) });
export const useCourses = (org: string) =>
  useQuery({ queryKey: developmentKeys.courses(org), queryFn: () => http.get<Course[]>(`${h(org)}/training/courses`) });
export const useCourse = (org: string, id: string) =>
  useQuery({ queryKey: developmentKeys.course(org, id), queryFn: () => http.get<{ course: Course; enrolments: Enrolment[]; canManage: boolean }>(`${h(org)}/training/courses/${id}`) });
export const useMyTraining = (org: string) =>
  useQuery({ queryKey: developmentKeys.myTraining(org), queryFn: () => http.get<{ enrolmentId: string; course: Course; status: EnrolmentStatus; completedAt: string | null }[]>(`${h(org)}/training/mine`) });

export const developmentApi = {
  saveCycle: (org: string, id: string | null, body: unknown) => (id ? http.put<Cycle>(`${h(org)}/review-cycles/${id}`, body) : http.post<Cycle>(`${h(org)}/review-cycles`, body)),
  launch: (org: string, id: string) => http.post<unknown>(`${h(org)}/review-cycles/${id}/launch`),
  close: (org: string, id: string) => http.post<Cycle>(`${h(org)}/review-cycles/${id}/close`),
  saveSelf: (org: string, id: string, body: unknown) => http.put<Review>(`${h(org)}/reviews/${id}/self`, body),
  saveReview: (org: string, id: string, body: unknown) => http.put<Review>(`${h(org)}/reviews/${id}/review`, body),
  acknowledge: (org: string, id: string) => http.post<Review>(`${h(org)}/reviews/${id}/acknowledge`),
  changeReviewer: (org: string, id: string, reviewerId: string) => http.put<Review>(`${h(org)}/reviews/${id}/reviewer`, { reviewerId }),
  saveCourse: (org: string, id: string | null, body: unknown) =>
    (id ? http.put<{ course: Course }>(`${h(org)}/training/courses/${id}`, body) : http.post<{ course: Course }>(`${h(org)}/training/courses`, body)),
  enrolMe: (org: string, id: string) => http.post<unknown>(`${h(org)}/training/courses/${id}/enrol`),
  enrol: (org: string, id: string, userIds: string[]) => http.post<unknown>(`${h(org)}/training/courses/${id}/enrolments`, { userIds }),
  setEnrolment: (org: string, id: string, status: EnrolmentStatus) => http.put<unknown>(`${h(org)}/training/enrolments/${id}`, { status }),
};
