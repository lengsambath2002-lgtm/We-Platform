'use client';

// General Business: types, query hooks and calls. Money fields are USD numbers as the API computes them.
import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { tr } from '@/i18n/core';

import { getAccessToken, http } from './client';
import type { Page } from './collab';
import type { ActivityItem, ApprovalStatus, PersonRef } from './types';

export type QuoteStatus = 'DRAFT' | 'SENT' | 'ACCEPTED' | 'DECLINED';
export const QUOTE_STATUSES: QuoteStatus[] = ['DRAFT', 'SENT', 'ACCEPTED', 'DECLINED'];
export type InvoiceStatus = 'DRAFT' | 'ISSUED' | 'PARTIALLY_PAID' | 'PAID' | 'VOID';
export const INVOICE_STATUSES: InvoiceStatus[] = ['DRAFT', 'ISSUED', 'PARTIALLY_PAID', 'PAID', 'VOID'];
export type ExpenseStatus = 'DRAFT' | 'PENDING' | 'APPROVED' | 'REJECTED' | 'PAID';
export const EXPENSE_STATUSES: ExpenseStatus[] = ['DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'PAID'];
export type PaymentMethod = 'CASH' | 'BANK_TRANSFER' | 'KHQR' | 'CARD' | 'OTHER';
export const PAYMENT_METHODS: PaymentMethod[] = ['CASH', 'BANK_TRANSFER', 'KHQR', 'CARD', 'OTHER'];

export interface BizSettings {
  legalName: string | null;
  address: string | null;
  taxId: string | null;
  phone: string | null;
  email: string | null;
  invoiceFooter: string | null;
  paymentTermsDays: number;
  vatDefault: boolean;
  vatRate: number;
  orgName: string;
}

export interface CustomerRef { id: string; name: string; archived: boolean }

export interface Customer {
  id: string;
  kind: 'COMPANY' | 'INDIVIDUAL';
  name: string;
  contactName: string | null;
  email: string | null;
  phone: string | null;
  address: string | null;
  taxId: string | null;
  notes: string | null;
  archived: boolean;
  /** owed on issued invoices */
  balance: number;
  createdAt: string;
}

export interface CustomerDetail {
  customer: Customer;
  overdue: number;
  invoiced: number;
  received: number;
  quotes: QuoteSummary[];
  invoices: InvoiceSummary[];
  canArchive: boolean;
}

export interface Product {
  id: string;
  nameEn: string;
  nameKm: string | null;
  unit: string;
  unitPrice: number;
  vat: boolean;
  active: boolean;
}

export interface Line {
  id: string;
  position: number;
  productId: string | null;
  description: string;
  unit: string | null;
  quantity: number;
  unitPrice: number;
  discountPct: number;
  vat: boolean;
  discount: number;
  net: number;
}

export interface QuoteSummary {
  id: string;
  number: string;
  customer: CustomerRef;
  status: QuoteStatus;
  expired: boolean;
  issueDate: string;
  validUntil: string | null;
  total: number;
  invoiceId: string | null;
  createdBy: PersonRef | null;
}

export interface Quote extends Omit<QuoteSummary, 'createdBy'> {
  vatEnabled: boolean;
  vatRate: number;
  lines: Line[];
  subtotal: number;
  discountTotal: number;
  vatTotal: number;
  notes: string | null;
  invoiceNumber: string | null;
  createdBy: PersonRef | null;
  createdAt: string;
  canEdit: boolean;
  canSend: boolean;
  canDecide: boolean;
  canConvert: boolean;
  canDelete: boolean;
}

export interface InvoiceSummary {
  id: string;
  /** null for a draft */
  number: string | null;
  customer: CustomerRef;
  status: InvoiceStatus;
  overdue: boolean;
  issueDate: string | null;
  dueDate: string | null;
  total: number;
  paid: number;
  balance: number;
  createdBy: PersonRef | null;
}

export interface Payment {
  id: string;
  paidOn: string;
  amount: number;
  method: PaymentMethod;
  reference: string | null;
  recordedBy: PersonRef | null;
  createdAt: string;
}

export interface Invoice {
  id: string;
  number: string | null;
  customer: Customer;
  status: InvoiceStatus;
  overdue: boolean;
  issueDate: string | null;
  dueDate: string | null;
  vatEnabled: boolean;
  vatRate: number;
  lines: Line[];
  subtotal: number;
  discountTotal: number;
  vatTotal: number;
  total: number;
  paid: number;
  balance: number;
  notes: string | null;
  quoteId: string | null;
  quoteNumber: string | null;
  payments: Payment[];
  voidReason: string | null;
  voidedAt: string | null;
  createdBy: PersonRef | null;
  createdAt: string;
  issuedAt: string | null;
  canEdit: boolean;
  canIssue: boolean;
  canPay: boolean;
  canVoid: boolean;
  canDelete: boolean;
  canRemovePayments: boolean;
}

export interface Category { id: string; nameEn: string; nameKm: string; active: boolean }

export interface Expense {
  id: string;
  person: PersonRef | null;
  spentOn: string;
  category: Category | null;
  supplier: string | null;
  amount: number;
  vatAmount: number | null;
  notes: string | null;
  receipt: { id: string; name: string; contentType: string; size: number } | null;
  status: ExpenseStatus;
  approvalId: string | null;
  approvalStatus: ApprovalStatus | null;
  paidAt: string | null;
  createdAt: string;
  canEdit: boolean;
  canSubmit: boolean;
  canDelete: boolean;
  canMarkPaid: boolean;
}

export interface MonthTotals { month: string; sales: number; expenses: number | null }

export interface Overview {
  receivable: number;
  openInvoices: number;
  overdue: number;
  overdueInvoices: number;
  salesThisMonth: number;
  /** null unless the caller manages the business */
  expensesThisMonth: number | null;
  months: MonthTotals[];
  recentInvoices: InvoiceSummary[];
  dueSoon: InvoiceSummary[];
  waitingExpenses: Expense[];
}

export interface DocFilter { q?: string; status?: string; customerId?: string; page?: number; size?: number }
export interface ExpenseFilter { status?: string; mine?: boolean; page?: number; size?: number }

const b = (org: string) => `/orgs/${encodeURIComponent(org)}/business`;
const qs = (o: object) => {
  const p = new URLSearchParams();
  Object.entries(o).forEach(([k, v]) => v !== undefined && v !== null && v !== '' && v !== false && p.set(k, String(v)));
  const s = p.toString();
  return s ? `?${s}` : '';
};

export const bizKeys = {
  all: (org: string) => ['org', org, 'business'] as const,
  overview: (org: string) => ['org', org, 'business', 'overview'] as const,
  settings: (org: string) => ['org', org, 'business', 'settings'] as const,
  customers: (org: string, f: object) => ['org', org, 'business', 'customers', f] as const,
  customer: (org: string, id: string) => ['org', org, 'business', 'customer', id] as const,
  products: (org: string, all: boolean) => ['org', org, 'business', 'products', all] as const,
  quotes: (org: string, f: DocFilter) => ['org', org, 'business', 'quotes', f] as const,
  quote: (org: string, id: string) => ['org', org, 'business', 'quote', id] as const,
  invoices: (org: string, f: DocFilter) => ['org', org, 'business', 'invoices', f] as const,
  invoice: (org: string, id: string) => ['org', org, 'business', 'invoice', id] as const,
  invoiceHistory: (org: string, id: string) => ['org', org, 'business', 'invoice', id, 'history'] as const,
  categories: (org: string) => ['org', org, 'business', 'categories'] as const,
  expenses: (org: string, f: ExpenseFilter) => ['org', org, 'business', 'expenses', f] as const,
  expense: (org: string, id: string) => ['org', org, 'business', 'expense', id] as const,
};

export const useBizOverview = (org: string) =>
  useQuery({ queryKey: bizKeys.overview(org), queryFn: () => http.get<Overview>(`${b(org)}/overview`) });
export const useBizSettings = (org: string) =>
  useQuery({ queryKey: bizKeys.settings(org), queryFn: () => http.get<BizSettings>(`${b(org)}/settings`) });
export const useCustomers = (org: string, f: { q?: string; archived?: boolean; page?: number; size?: number }) =>
  useQuery({ queryKey: bizKeys.customers(org, f), queryFn: () => http.get<Page<Customer>>(`${b(org)}/customers${qs(f)}`), placeholderData: keepPreviousData });
export const useCustomer = (org: string, id: string) =>
  useQuery({ queryKey: bizKeys.customer(org, id), queryFn: () => http.get<CustomerDetail>(`${b(org)}/customers/${id}`) });
export const useProducts = (org: string, all = false) =>
  useQuery({ queryKey: bizKeys.products(org, all), queryFn: () => http.get<Product[]>(`${b(org)}/products${qs({ all })}`) });
export const useQuotes = (org: string, f: DocFilter) =>
  useQuery({ queryKey: bizKeys.quotes(org, f), queryFn: () => http.get<Page<QuoteSummary>>(`${b(org)}/quotes${qs(f)}`), placeholderData: keepPreviousData });
export const useQuote = (org: string, id: string) =>
  useQuery({ queryKey: bizKeys.quote(org, id), queryFn: () => http.get<Quote>(`${b(org)}/quotes/${id}`), enabled: !!id });
export const useInvoices = (org: string, f: DocFilter) =>
  useQuery({ queryKey: bizKeys.invoices(org, f), queryFn: () => http.get<Page<InvoiceSummary>>(`${b(org)}/invoices${qs(f)}`), placeholderData: keepPreviousData });
export const useInvoice = (org: string, id: string) =>
  useQuery({ queryKey: bizKeys.invoice(org, id), queryFn: () => http.get<Invoice>(`${b(org)}/invoices/${id}`), enabled: !!id });
export const useInvoiceHistory = (org: string, id: string) =>
  useQuery({ queryKey: bizKeys.invoiceHistory(org, id), queryFn: () => http.get<ActivityItem[]>(`${b(org)}/invoices/${id}/activity`) });
export const useCategories = (org: string) =>
  useQuery({ queryKey: bizKeys.categories(org), queryFn: () => http.get<Category[]>(`${b(org)}/expense-categories`) });
export const useExpenses = (org: string, f: ExpenseFilter) =>
  useQuery({ queryKey: bizKeys.expenses(org, f), queryFn: () => http.get<Page<Expense>>(`${b(org)}/expenses${qs(f)}`), placeholderData: keepPreviousData });
export const useExpense = (org: string, id: string) =>
  useQuery({ queryKey: bizKeys.expense(org, id), queryFn: () => http.get<Expense>(`${b(org)}/expenses/${id}`) });

export const bizApi = {
  saveSettings: (org: string, body: unknown) => http.put<BizSettings>(`${b(org)}/settings`, body),
  saveCustomer: (org: string, id: string | null, body: unknown) =>
    id ? http.put<Customer>(`${b(org)}/customers/${id}`, body) : http.post<Customer>(`${b(org)}/customers`, body),
  archiveCustomer: (org: string, id: string, archived: boolean) => http.post<Customer>(`${b(org)}/customers/${id}/${archived ? 'archive' : 'restore'}`),
  saveProduct: (org: string, id: string | null, body: unknown) =>
    id ? http.put<Product>(`${b(org)}/products/${id}`, body) : http.post<Product>(`${b(org)}/products`, body),
  saveQuote: (org: string, id: string | null, body: unknown) =>
    id ? http.put<Quote>(`${b(org)}/quotes/${id}`, body) : http.post<Quote>(`${b(org)}/quotes`, body),
  deleteQuote: (org: string, id: string) => http.delete<void>(`${b(org)}/quotes/${id}`),
  markQuote: (org: string, id: string, action: 'send' | 'accept' | 'decline') => http.post<Quote>(`${b(org)}/quotes/${id}/${action}`),
  convertQuote: (org: string, id: string) => http.post<Invoice>(`${b(org)}/quotes/${id}/convert`),
  saveInvoice: (org: string, id: string | null, body: unknown) =>
    id ? http.put<Invoice>(`${b(org)}/invoices/${id}`, body) : http.post<Invoice>(`${b(org)}/invoices`, body),
  deleteInvoice: (org: string, id: string) => http.delete<void>(`${b(org)}/invoices/${id}`),
  issueInvoice: (org: string, id: string) => http.post<Invoice>(`${b(org)}/invoices/${id}/issue`),
  pay: (org: string, id: string, body: unknown) => http.post<Invoice>(`${b(org)}/invoices/${id}/payments`, body),
  removePayment: (org: string, id: string, paymentId: string) => http.delete<Invoice>(`${b(org)}/invoices/${id}/payments/${paymentId}`),
  voidInvoice: (org: string, id: string, reason: string) => http.post<Invoice>(`${b(org)}/invoices/${id}/void`, { reason }),
  saveCategory: (org: string, id: string | null, body: unknown) =>
    id ? http.put<Category>(`${b(org)}/expense-categories/${id}`, body) : http.post<Category>(`${b(org)}/expense-categories`, body),
  saveExpense: (org: string, id: string | null, body: unknown) =>
    id ? http.put<Expense>(`${b(org)}/expenses/${id}`, body) : http.post<Expense>(`${b(org)}/expenses`, body),
  deleteExpense: (org: string, id: string) => http.delete<void>(`${b(org)}/expenses/${id}`),
  submitExpense: (org: string, id: string) => http.post<Expense>(`${b(org)}/expenses/${id}/submit`),
  expensePaid: (org: string, id: string) => http.post<Expense>(`${b(org)}/expenses/${id}/paid`),
  attachReceipt: (org: string, id: string, file: File) => {
    const fd = new FormData();
    fd.append('file', file);
    return http.post<Expense>(`${b(org)}/expenses/${id}/receipt`, fd);
  },
  removeReceipt: (org: string, id: string) => http.delete<Expense>(`${b(org)}/expenses/${id}/receipt`),
};

/** Opens the receipt through the API with the bearer token (files are never public URLs). */
export async function downloadReceipt(org: string, id: string, name: string) {
  const res = await fetch(`/api${b(org)}/expenses/${id}/receipt`, { headers: { Authorization: `Bearer ${getAccessToken() ?? ''}` }, credentials: 'include' });
  if (!res.ok) throw new Error(tr('business.receiptFailed'));
  const href = URL.createObjectURL(await res.blob());
  const a = Object.assign(document.createElement('a'), { href, download: name });
  a.click();
  URL.revokeObjectURL(href);
}
