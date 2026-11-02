'use client';

// Service & Operations: types, query hooks and calls. Money fields are USD numbers as the API computes them.
import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { tr } from '@/i18n/core';

import { getAccessToken, http } from './client';
import type { Page } from './collab';
import type { ActivityItem, ApprovalStatus, PersonRef, Priority } from './types';

export type TicketStatus = 'NEW' | 'TRIAGED' | 'IN_PROGRESS' | 'WAITING' | 'RESOLVED' | 'CLOSED';
export const TICKET_STATUSES: TicketStatus[] = ['NEW', 'TRIAGED', 'IN_PROGRESS', 'WAITING', 'RESOLVED', 'CLOSED'];
export const OPEN_TICKET: TicketStatus[] = ['NEW', 'TRIAGED', 'IN_PROGRESS', 'WAITING'];
export type Category = 'REPAIR' | 'INSTALLATION' | 'MAINTENANCE' | 'INSPECTION' | 'COMPLAINT' | 'OTHER';
export const CATEGORIES: Category[] = ['REPAIR', 'INSTALLATION', 'MAINTENANCE', 'INSPECTION', 'COMPLAINT', 'OTHER'];
export type SlaState = 'ON_TRACK' | 'AT_RISK' | 'BREACHED' | 'MET';
export type WoStatus = 'DRAFT' | 'SCHEDULED' | 'IN_PROGRESS' | 'ON_HOLD' | 'COMPLETED' | 'CANCELLED';
export const WO_STATUSES: WoStatus[] = ['DRAFT', 'SCHEDULED', 'IN_PROGRESS', 'ON_HOLD', 'COMPLETED', 'CANCELLED'];
export type ApprovalState = 'NONE' | 'PENDING' | 'APPROVED' | 'REJECTED';
export type AssetStatus = 'ACTIVE' | 'IN_REPAIR' | 'RETIRED';
export const ASSET_STATUSES: AssetStatus[] = ['ACTIVE', 'IN_REPAIR', 'RETIRED'];
export type StaffRole = 'DISPATCHER' | 'TECHNICIAN';
export type LineKind = 'PART' | 'LABOUR';

export interface Ref { id: string; name: string }

export interface SvcSettings { approvalThreshold: number | null; labourRate: number }
export interface SlaPolicy { priority: Priority; responseMinutes: number; resolutionMinutes: number }
export interface Me { role: 'ADMIN' | StaffRole; manager: boolean; admin: boolean; technician: boolean; userId: string }

export interface Staff {
  id: string;
  person: PersonRef;
  email: string;
  orgRole: string | null;
  role: StaffRole;
  skills: string[];
  phone: string | null;
  active: boolean;
  openWorkOrders: number;
}

export interface SvcCustomer {
  id: string;
  name: string;
  contactName: string | null;
  phone: string | null;
  email: string | null;
  address: string | null;
  notes: string | null;
  archived: boolean;
  sites: number;
  assets: number;
  openTickets: number;
  createdAt: string;
}
export interface Site { id: string; customerId: string; name: string; address: string | null; notes: string | null }

export interface Asset {
  id: string;
  customer: Ref | null;
  site: Ref | null;
  name: string;
  serial: string | null;
  model: string | null;
  manufacturer: string | null;
  installedOn: string | null;
  warrantyEnd: string | null;
  underWarranty: boolean;
  status: AssetStatus;
  notes: string | null;
  nextMaintenance: string | null;
  createdAt: string;
}

export interface TicketSummary {
  id: string;
  number: string;
  title: string;
  category: Category;
  priority: Priority;
  status: TicketStatus;
  customer: Ref | null;
  assignee: PersonRef | null;
  sla: SlaState;
  responseDueAt: string;
  resolutionDueAt: string;
  createdAt: string;
  updatedAt: string;
}

export interface SvcFile { id: string; name: string; contentType: string; size: number; createdAt: string; addedBy: PersonRef | null }
export interface SvcComment { id: string; author: PersonRef | null; body: string; createdAt: string }

export interface WorkOrderSummary {
  id: string;
  number: string;
  title: string;
  priority: Priority;
  status: WoStatus;
  approvalState: ApprovalState;
  customer: Ref | null;
  asset: Ref | null;
  technicians: PersonRef[];
  scheduledStart: string | null;
  scheduledEnd: string | null;
  total: number;
  preventive: boolean;
  checklistDone: number;
  checklistTotal: number;
  createdAt: string;
}

export interface Ticket {
  summary: TicketSummary;
  description: string | null;
  site: Ref | null;
  asset: Ref | null;
  requesterName: string | null;
  requesterPhone: string | null;
  createdBy: PersonRef | null;
  responseSla: SlaState;
  resolutionSla: SlaState;
  respondedAt: string | null;
  resolvedAt: string | null;
  closedAt: string | null;
  nextStatuses: TicketStatus[];
  attachments: SvcFile[];
  workOrders: WorkOrderSummary[];
  canEdit: boolean;
  canAssign: boolean;
  canCreateWorkOrder: boolean;
}

export interface WoLine { id: string; position: number; kind: LineKind; description: string; quantity: number; unitPrice: number; amount: number }
export interface CheckItem { id: string; position: number; label: string; done: boolean; doneBy: PersonRef | null; doneAt: string | null }

export interface WorkOrder {
  summary: WorkOrderSummary;
  description: string | null;
  site: Ref | null;
  ticket: Ref | null;
  ticketNumber: string | null;
  plan: Ref | null;
  planDue: string | null;
  partsTotal: number;
  labourTotal: number;
  approvalId: string | null;
  approvalStatus: ApprovalStatus | null;
  approvedTotal: number | null;
  approvalThreshold: number | null;
  needsApproval: boolean;
  holdReason: string | null;
  startedAt: string | null;
  completedAt: string | null;
  completedBy: PersonRef | null;
  completionNotes: string | null;
  signedBy: string | null;
  cancelReason: string | null;
  createdBy: PersonRef | null;
  checklist: CheckItem[];
  lines: WoLine[];
  canEdit: boolean;
  canSchedule: boolean;
  canStart: boolean;
  canHold: boolean;
  canComplete: boolean;
  canCancel: boolean;
  canEditLines: boolean;
  canCheck: boolean;
}

export interface Plan {
  id: string;
  asset: Ref | null;
  customer: Ref | null;
  title: string;
  intervalCount: number;
  intervalUnit: 'DAYS' | 'MONTHS';
  nextDue: string;
  leadDays: number;
  generateOn: string;
  technician: PersonRef | null;
  checklist: string[];
  active: boolean;
  lastGeneratedAt: string | null;
}

export interface CustomerDetail { customer: SvcCustomer; sites: Site[]; assets: Asset[]; tickets: TicketSummary[]; workOrders: WorkOrderSummary[] }
export interface AssetDetail { asset: Asset; workOrders: WorkOrderSummary[]; tickets: TicketSummary[]; plans: Plan[] }

export interface WeekCount { week: string; opened: number; resolved: number }
export interface SvcOverview {
  scope: 'ALL' | 'MINE';
  ticketsByStatus: Record<TicketStatus, number>;
  openTickets: number;
  breached: number;
  atRisk: number;
  breachedTickets: TicketSummary[];
  workOrdersThisWeek: WorkOrderSummary[];
  awaitingApproval: number;
  weeks: WeekCount[];
  upcomingMaintenance: Plan[];
}

export interface TechnicianReport { technician: PersonRef; completed: number; labourHours: number; labour: number; parts: number; total: number }
export interface SlaReport { priority: Priority; tickets: number; responseMet: number; responseBreached: number; resolutionMet: number; resolutionBreached: number; open: number }
export interface SvcReport {
  from: string;
  to: string;
  ticketsOpened: number;
  ticketsResolved: number;
  workOrdersCompleted: number;
  completedValue: number;
  technicians: TechnicianReport[];
  sla: SlaReport[];
  categories: { category: Category; tickets: number }[];
}

export interface TicketFilter { q?: string; status?: string; priority?: string; category?: string; assignee?: string; sla?: string; customerId?: string; page?: number; size?: number }
export interface WoFilter { q?: string; status?: string; technician?: string; ticketId?: string; assetId?: string; from?: string; to?: string; page?: number; size?: number }

const s = (org: string) => `/orgs/${encodeURIComponent(org)}/service`;
const qs = (o: object) => {
  const p = new URLSearchParams();
  Object.entries(o).forEach(([k, v]) => v !== undefined && v !== null && v !== '' && v !== false && p.set(k, String(v)));
  const q = p.toString();
  return q ? `?${q}` : '';
};

const k = (org: string, ...rest: unknown[]) => ['org', org, 'service', ...rest] as const;

export const useSvcMe = (org: string) => useQuery({ queryKey: k(org, 'me'), queryFn: () => http.get<Me>(`${s(org)}/me`) });
export const useSvcOverview = (org: string) => useQuery({ queryKey: k(org, 'overview'), queryFn: () => http.get<SvcOverview>(`${s(org)}/overview`) });
export const useSvcSettings = (org: string) => useQuery({ queryKey: k(org, 'settings'), queryFn: () => http.get<SvcSettings>(`${s(org)}/settings`) });
export const useSla = (org: string) => useQuery({ queryKey: k(org, 'sla'), queryFn: () => http.get<SlaPolicy[]>(`${s(org)}/sla`) });
export const useStaff = (org: string, enabled = true) =>
  useQuery({ queryKey: k(org, 'staff'), queryFn: () => http.get<Staff[]>(`${s(org)}/staff`), enabled });
export const useSvcCustomers = (org: string, f: { q?: string; archived?: boolean; page?: number; size?: number }, enabled = true) =>
  useQuery({ queryKey: k(org, 'customers', f), queryFn: () => http.get<Page<SvcCustomer>>(`${s(org)}/customers${qs(f)}`), placeholderData: keepPreviousData, enabled });
export const useSvcCustomer = (org: string, id: string | null | undefined) =>
  useQuery({ queryKey: k(org, 'customer', id), queryFn: () => http.get<CustomerDetail>(`${s(org)}/customers/${id}`), enabled: !!id });
export const useAssets = (org: string, f: { q?: string; customerId?: string; status?: string; warranty?: boolean; page?: number; size?: number }, enabled = true) =>
  useQuery({ queryKey: k(org, 'assets', f), queryFn: () => http.get<Page<Asset>>(`${s(org)}/assets${qs(f)}`), placeholderData: keepPreviousData, enabled });
export const useAsset = (org: string, id: string) => useQuery({ queryKey: k(org, 'asset', id), queryFn: () => http.get<AssetDetail>(`${s(org)}/assets/${id}`) });
export const usePlans = (org: string) => useQuery({ queryKey: k(org, 'plans'), queryFn: () => http.get<Plan[]>(`${s(org)}/maintenance`) });
export const useTickets = (org: string, f: TicketFilter) =>
  useQuery({ queryKey: k(org, 'tickets', f), queryFn: () => http.get<Page<TicketSummary>>(`${s(org)}/tickets${qs(f)}`), placeholderData: keepPreviousData });
/** `id` '' = no ticket (nothing is fetched: the bare path is the ticket list). */
export const useTicket = (org: string, id: string) =>
  useQuery({ queryKey: k(org, 'ticket', id), queryFn: () => http.get<Ticket>(`${s(org)}/tickets/${id}`), enabled: !!id });
export const useTicketComments = (org: string, id: string) =>
  useQuery({ queryKey: k(org, 'ticket', id, 'comments'), queryFn: () => http.get<SvcComment[]>(`${s(org)}/tickets/${id}/comments`) });
export const useTicketHistory = (org: string, id: string) =>
  useQuery({ queryKey: k(org, 'ticket', id, 'history'), queryFn: () => http.get<ActivityItem[]>(`${s(org)}/tickets/${id}/activity`) });
export const useWorkOrders = (org: string, f: WoFilter) =>
  useQuery({ queryKey: k(org, 'work-orders', f), queryFn: () => http.get<Page<WorkOrderSummary>>(`${s(org)}/work-orders${qs(f)}`), placeholderData: keepPreviousData });
export const useWorkOrder = (org: string, id: string) =>
  useQuery({ queryKey: k(org, 'work-order', id), queryFn: () => http.get<WorkOrder>(`${s(org)}/work-orders/${id}`), enabled: !!id });
export const useWorkOrderHistory = (org: string, id: string) =>
  useQuery({ queryKey: k(org, 'work-order', id, 'history'), queryFn: () => http.get<ActivityItem[]>(`${s(org)}/work-orders/${id}/activity`) });
export const useSvcReport = (org: string, from: string, to: string) =>
  useQuery({ queryKey: k(org, 'report', from, to), queryFn: () => http.get<SvcReport>(`${s(org)}/reports${qs({ from, to })}`), placeholderData: keepPreviousData });

export const svcApi = {
  saveSettings: (org: string, body: unknown) => http.put<SvcSettings>(`${s(org)}/settings`, body),
  saveSla: (org: string, body: unknown) => http.put<SlaPolicy[]>(`${s(org)}/sla`, body),
  saveStaff: (org: string, id: string | null, body: unknown) =>
    id ? http.put<Staff>(`${s(org)}/staff/${id}`, body) : http.post<Staff>(`${s(org)}/staff`, body),
  saveCustomer: (org: string, id: string | null, body: unknown) =>
    id ? http.put<SvcCustomer>(`${s(org)}/customers/${id}`, body) : http.post<SvcCustomer>(`${s(org)}/customers`, body),
  archiveCustomer: (org: string, id: string, archived: boolean) => http.post<SvcCustomer>(`${s(org)}/customers/${id}/${archived ? 'archive' : 'restore'}`),
  addSite: (org: string, customerId: string, body: unknown) => http.post<Site>(`${s(org)}/customers/${customerId}/sites`, body),
  updateSite: (org: string, id: string, body: unknown) => http.put<Site>(`${s(org)}/sites/${id}`, body),
  removeSite: (org: string, id: string) => http.delete<void>(`${s(org)}/sites/${id}`),
  saveAsset: (org: string, id: string | null, body: unknown) =>
    id ? http.put<Asset>(`${s(org)}/assets/${id}`, body) : http.post<Asset>(`${s(org)}/assets`, body),
  savePlan: (org: string, id: string | null, body: unknown) =>
    id ? http.put<Plan>(`${s(org)}/maintenance/${id}`, body) : http.post<Plan>(`${s(org)}/maintenance`, body),
  generate: (org: string) => http.post<{ created: number; workOrders: WorkOrderSummary[] }>(`${s(org)}/maintenance/generate`),
  saveTicket: (org: string, id: string | null, body: unknown) =>
    id ? http.put<Ticket>(`${s(org)}/tickets/${id}`, body) : http.post<Ticket>(`${s(org)}/tickets`, body),
  ticketStatus: (org: string, id: string, status: TicketStatus) => http.post<Ticket>(`${s(org)}/tickets/${id}/status`, { status }),
  assign: (org: string, id: string, assigneeId: string | null) => http.post<Ticket>(`${s(org)}/tickets/${id}/assign`, { assigneeId }),
  comment: (org: string, id: string, body: string) => http.post<SvcComment>(`${s(org)}/tickets/${id}/comments`, { body }),
  attach: (org: string, id: string, file: File) => {
    const fd = new FormData();
    fd.append('file', file);
    return http.post<Ticket>(`${s(org)}/tickets/${id}/attachments`, fd);
  },
  detach: (org: string, id: string, fileId: string) => http.delete<Ticket>(`${s(org)}/tickets/${id}/attachments/${fileId}`),
  saveWorkOrder: (org: string, id: string | null, body: unknown) =>
    id ? http.put<WorkOrder>(`${s(org)}/work-orders/${id}`, body) : http.post<WorkOrder>(`${s(org)}/work-orders`, body),
  woAction: (org: string, id: string, action: 'schedule' | 'start', body?: unknown) => http.post<WorkOrder>(`${s(org)}/work-orders/${id}/${action}`, body),
  hold: (org: string, id: string, reason: string) => http.post<WorkOrder>(`${s(org)}/work-orders/${id}/hold`, { reason }),
  cancel: (org: string, id: string, reason: string) => http.post<WorkOrder>(`${s(org)}/work-orders/${id}/cancel`, { reason }),
  complete: (org: string, id: string, body: { notes: string | null; signedBy: string }) => http.post<WorkOrder>(`${s(org)}/work-orders/${id}/complete`, body),
  check: (org: string, id: string, itemId: string, done: boolean) => http.post<WorkOrder>(`${s(org)}/work-orders/${id}/checklist/${itemId}`, { done }),
  setLines: (org: string, id: string, lines: unknown[]) => http.put<WorkOrder>(`${s(org)}/work-orders/${id}/lines`, { lines }),
};

/** Downloads a ticket's file through the API with the bearer token (files are never public URLs). */
export async function downloadTicketFile(org: string, ticketId: string, fileId: string, name: string) {
  const res = await fetch(`/api${s(org)}/tickets/${ticketId}/attachments/${fileId}`, { headers: { Authorization: `Bearer ${getAccessToken() ?? ''}` }, credentials: 'include' });
  if (!res.ok) throw new Error(tr('service.downloadFailed'));
  const href = URL.createObjectURL(await res.blob());
  const a = Object.assign(document.createElement('a'), { href, download: name });
  a.click();
  URL.revokeObjectURL(href);
}
