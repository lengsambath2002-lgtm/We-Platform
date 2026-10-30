'use client';

// Retail & Commerce: suppliers and purchase orders. Stock itself stays in VirtualBiz.
import { useQuery } from '@tanstack/react-query';

import { http } from './client';
import type { PersonRef } from './types';

export type PoStatus = 'DRAFT' | 'ORDERED' | 'PARTIAL' | 'RECEIVED' | 'CANCELLED';
export const PO_STATUSES: PoStatus[] = ['DRAFT', 'ORDERED', 'PARTIAL', 'RECEIVED', 'CANCELLED'];
export const PAY_METHODS = ['CASH', 'BANK_TRANSFER', 'KHQR', 'CARD', 'OTHER'] as const;

export interface Supplier {
  id: string;
  name: string;
  contactName: string | null;
  email: string | null;
  phone: string | null;
  address: string | null;
  taxId: string | null;
  paymentTermsDays: number;
  notes: string | null;
  archived: boolean;
  owed: number;
  spentThisYear: number;
  openOrders: number;
}

export interface OrderSummary {
  id: string;
  number: string | null;
  supplierId: string;
  supplierName: string;
  status: PoStatus;
  orderDate: string | null;
  expectedDate: string | null;
  dueDate: string | null;
  overdue: boolean;
  total: number;
  paid: number;
  balance: number;
  shopName: string | null;
}

export interface OrderLine { id: string; description: string; sku: string | null; quantity: number; unitCost: number; received: number; amount: number }
export interface Receipt { id: string; receivedOn: string; lines: { lineId: string; description: string; sku: string | null; quantity: number }[]; note: string | null; stockRecorded: boolean; by: PersonRef; createdAt: string }
export interface Payment { id: string; paidOn: string; amount: number; method: string; reference: string | null; by: PersonRef }

export interface Order {
  summary: OrderSummary;
  shopBusinessId: string | null;
  notes: string | null;
  lines: OrderLine[];
  receipts: Receipt[];
  payments: Payment[];
  cancelReason: string | null;
  createdBy: PersonRef;
  createdAt: string;
  canEdit: boolean;
  canReceive: boolean;
  canPay: boolean;
}

export interface PurchasingOverview {
  owed: number;
  overdue: number;
  awaitingDelivery: number;
  notInStock: number;
  orderedThisMonth: number;
  spendThisYear: { supplierId: string; supplierName: string; orders: number; ordered: number; paid: number; owed: number }[];
}

const r = (org: string) => `/orgs/${encodeURIComponent(org)}/retail`;

export const purchasingKeys = {
  overview: (org: string) => ['org', org, 'retail', 'purchasing'] as const,
  suppliers: (org: string, archived: boolean) => ['org', org, 'retail', 'suppliers', archived] as const,
  supplier: (org: string, id: string) => ['org', org, 'retail', 'supplier', id] as const,
  orders: (org: string, status: string, supplierId: string) => ['org', org, 'retail', 'orders', status, supplierId] as const,
  order: (org: string, id: string) => ['org', org, 'retail', 'order', id] as const,
};

export const usePurchasingOverview = (org: string, enabled = true) =>
  useQuery({ queryKey: purchasingKeys.overview(org), queryFn: () => http.get<PurchasingOverview>(`${r(org)}/purchasing`), enabled });
export const useSuppliers = (org: string, archived = false) =>
  useQuery({ queryKey: purchasingKeys.suppliers(org, archived), queryFn: () => http.get<Supplier[]>(`${r(org)}/suppliers?archived=${archived}`) });
export const useSupplier = (org: string, id: string) =>
  useQuery({ queryKey: purchasingKeys.supplier(org, id), queryFn: () => http.get<Supplier>(`${r(org)}/suppliers/${id}`) });
export const useOrders = (org: string, status = '', supplierId = '') =>
  useQuery({
    queryKey: purchasingKeys.orders(org, status, supplierId),
    queryFn: () => http.get<OrderSummary[]>(`${r(org)}/purchase-orders?${new URLSearchParams({ ...(status ? { status } : {}), ...(supplierId ? { supplierId } : {}) })}`),
  });
export const useOrder = (org: string, id: string) =>
  useQuery({ queryKey: purchasingKeys.order(org, id), queryFn: () => http.get<Order>(`${r(org)}/purchase-orders/${id}`) });

export const purchasingApi = {
  saveSupplier: (org: string, id: string | null, body: unknown) => (id ? http.put<Supplier>(`${r(org)}/suppliers/${id}`, body) : http.post<Supplier>(`${r(org)}/suppliers`, body)),
  archiveSupplier: (org: string, id: string, archived: boolean) => http.put<Supplier>(`${r(org)}/suppliers/${id}/archived`, { archived }),
  saveOrder: (org: string, id: string | null, body: unknown) => (id ? http.put<Order>(`${r(org)}/purchase-orders/${id}`, body) : http.post<Order>(`${r(org)}/purchase-orders`, body)),
  deleteOrder: (org: string, id: string) => http.delete<void>(`${r(org)}/purchase-orders/${id}`),
  place: (org: string, id: string) => http.post<Order>(`${r(org)}/purchase-orders/${id}/place`),
  cancel: (org: string, id: string, reason: string) => http.post<Order>(`${r(org)}/purchase-orders/${id}/cancel`, { reason }),
  receive: (org: string, id: string, body: unknown) => http.post<Order>(`${r(org)}/purchase-orders/${id}/receipts`, body),
  stockRecorded: (org: string, receiptId: string, recorded: boolean) => http.put<Order>(`${r(org)}/receipts/${receiptId}/stock-recorded`, { recorded }),
  pay: (org: string, id: string, body: unknown) => http.post<Order>(`${r(org)}/purchase-orders/${id}/payments`, body),
};
