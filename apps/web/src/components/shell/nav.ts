import {
  BadgeCheck,
  Banknote,
  BarChart3,
  Blocks,
  Briefcase,
  CalendarCheck2,
  CalendarDays,
  CalendarClock,
  CheckSquare,
  Clock,
  ClipboardList,
  Cpu,
  HardHat,
  LifeBuoy,
  Ticket,
  Contact,
  FileSignature,
  FileText,
  FolderKanban,
  GitBranch,
  GraduationCap,
  Hammer,
  Inbox,
  Package,
  Receipt,
  LayoutDashboard,
  Settings,
  type LucideIcon,
  ScrollText,
  SlidersHorizontal,
  Store,
  UserCog,
  UserRound,
  Users,
  UsersRound,
  Wrench,
  ClipboardCheck,
  HeartHandshake,
  LineChart,
  Presentation,
  School,
  Sheet,
  Wallet,
} from 'lucide-react';

import { currentLocale, tr } from '@/i18n/core';
import type { OrgContext } from '@/lib/api/types';

export interface NavItem {
  href: string;
  label: string;
  icon: LucideIcon;
  /** Active only on this exact page (a module home with sub-pages under it). */
  exact?: boolean;
  /** Shown indented under the item before it. */
  sub?: boolean;
}

/** Global navigation, filtered by the permissions the server sent (it re-checks every request). */
export function globalNav(ctx: OrgContext): NavItem[] {
  const can = (p: string) => ctx.permissions.includes(p);
  const items: (NavItem & { need?: string })[] = [
    { href: 'overview', label: tr('shell.overview'), icon: LayoutDashboard },
    { href: 'my-tasks', label: tr('shell.myTasks'), icon: CheckSquare, need: 'projects.view' },
    { href: 'projects', label: tr('shell.projects'), icon: FolderKanban, need: 'projects.view' },
    { href: 'calendar', label: tr('shell.calendar'), icon: CalendarDays, need: 'calendar.view' },
    { href: 'teams', label: tr('shell.teams'), icon: Users, need: 'teams.view' },
    { href: 'approvals', label: tr('shell.approvals'), icon: BadgeCheck, need: 'approvals.view' },
    { href: 'documents', label: tr('documents.title'), icon: FileText, need: 'documents.view' },
    { href: 'reports', label: tr('shell.reports'), icon: BarChart3, need: 'reports.view' },
  ];
  return items.filter((i) => !i.need || can(i.need));
}

export function adminNav(ctx: OrgContext): NavItem[] {
  if (!ctx.permissions.includes('org.settings.manage')) return [];
  return [
    { href: 'admin/modules', label: tr('shell.modules'), icon: Blocks },
    { href: 'admin/workflows', label: tr('shell.workflows'), icon: GitBranch },
    { href: 'admin/members', label: tr('shell.membersRoles'), icon: UserCog },
    { href: 'admin/settings', label: tr('settings.title'), icon: Settings },
    ...(ctx.permissions.includes('activity.view') ? [{ href: 'admin/audit', label: tr('collab.auditNav'), icon: ScrollText }] : []),
  ];
}

export const MODULE_ICON: Record<string, LucideIcon> = {
  projects: FolderKanban,
  business: Briefcase,
  education: GraduationCap,
  retail: Store,
  hr: Users,
  service: Wrench,
};

/** Where a switched-on module lives: built modules (projects, business, education, HR, retail, service) have their own pages, the others a "planned" page. */
export const moduleHref = (key: string) => (['projects', 'business', 'education', 'hr', 'retail', 'service'].includes(key) ? key : `m/${key}`);

/** Modules with a home page and sub-pages listed under it in the sidebar. */
export const subNav = (key: string, ctx: OrgContext): NavItem[] => (key === 'hr' ? hrNav(ctx) : key === 'business' ? businessNav(ctx)
  : key === 'education' ? educationNav(ctx) : key === 'service' ? serviceNav(ctx) : []);

/**
 * Service & Operations' pages for the sidebar and its tabs, filtered by permission (the server re-checks):
 * dispatchers and Admins get everything; technicians their dashboard, tickets, bookings and their work orders.
 */
export function serviceNav(ctx: OrgContext): NavItem[] {
  const can = (p: string) => ctx.permissions.includes(p);
  if (!can('service.view')) return [];
  const manage = can('service.manage');
  return [
    { href: 'service', label: tr('service.navDashboard'), icon: LayoutDashboard, exact: true },
    ...(manage ? [{ href: 'service/requests', label: tr('service.navRequests'), icon: LifeBuoy }] : []),
    { href: 'service/tickets', label: tr('service.navTickets'), icon: Ticket },
    { href: 'service/bookings', label: tr('service.navBookings'), icon: CalendarClock },
    { href: 'service/work-orders', label: manage ? tr('service.navWorkOrders') : tr('service.navMyWorkOrders'), icon: ClipboardList },
    ...(manage ? [
      { href: 'service/technicians', label: tr('service.navTechnicians'), icon: HardHat },
      { href: 'service/assets', label: tr('service.navAssets'), icon: Cpu },
      { href: 'service/maintenance', label: tr('service.navMaintenance'), icon: Hammer },
      { href: 'service/reports', label: tr('service.navReports'), icon: BarChart3 },
    ] : []),
    ...(can('service.admin') ? [{ href: 'service/settings', label: tr('service.navSettings'), icon: SlidersHorizontal }] : []),
  ];
}

/** General Business's pages for the sidebar and its tabs, filtered by permission (the server re-checks). */
export function businessNav(ctx: OrgContext): NavItem[] {
  const can = (p: string) => ctx.permissions.includes(p);
  if (!can('business.view')) return [];
  return [
    { href: 'business', label: tr('business.navOverview'), icon: LayoutDashboard, exact: true },
    { href: 'business/customers', label: tr('business.navCustomers'), icon: UserRound },
    { href: 'business/products', label: tr('business.navProducts'), icon: Package },
    { href: 'business/quotes', label: tr('business.navQuotes'), icon: FileSignature },
    { href: 'business/invoices', label: tr('business.navInvoices'), icon: Receipt },
    { href: 'business/expenses', label: tr('business.navExpenses'), icon: Banknote },
    ...(can('business.manage') ? [{ href: 'business/settings', label: tr('business.navSettings'), icon: SlidersHorizontal }] : []),
  ];
}

/**
 * Education's pages for the sidebar and its tabs, filtered by permission (the server re-checks): admins see
 * everything; teachers their classes' pages, without fees and the academic structure.
 */
export function educationNav(ctx: OrgContext): NavItem[] {
  const can = (p: string) => ctx.permissions.includes(p);
  if (!can('education.view')) return [];
  const manage = can('education.manage');
  return [
    { href: 'education', label: tr('education.navHome'), icon: LayoutDashboard, exact: true },
    { href: 'education/students', label: tr('education.navStudents'), icon: GraduationCap },
    { href: 'education/parents', label: tr('education.navParents'), icon: HeartHandshake },
    { href: 'education/teachers', label: tr('education.navTeachers'), icon: Presentation },
    { href: 'education/classes', label: tr('education.navClasses'), icon: School },
    { href: 'education/attendance', label: tr('education.navAttendance'), icon: ClipboardCheck },
    { href: 'education/exams', label: tr('education.navExams'), icon: ClipboardList },
    { href: 'education/gradebook', label: tr('education.navGradebook'), icon: Sheet },
    ...(manage ? [{ href: 'education/fees', label: tr('education.navFees'), icon: Wallet }] : []),
    { href: 'education/timetable', label: tr('education.navTimetable'), icon: CalendarClock },
    { href: 'education/reports', label: tr('education.navReports'), icon: LineChart },
    ...(manage ? [{ href: 'education/settings', label: tr('education.navSettings'), icon: SlidersHorizontal }] : []),
  ];
}

/** HR's pages for the sidebar and HR's tabs, filtered by permission (the server re-checks). */
export function hrNav(ctx: OrgContext): NavItem[] {
  const can = (p: string) => ctx.permissions.includes(p);
  if (!can('hr.view')) return [];
  return [
    { href: 'hr', label: tr('hr.navHome'), icon: CalendarCheck2, exact: true },
    { href: 'hr/attendance', label: tr('hr.navAttendance'), icon: Clock, exact: true },
    { href: 'hr/people', label: tr('hr.navPeople'), icon: Contact },
    { href: 'hr/payslips', label: tr('hr.navPayslips'), icon: Receipt },
    ...(can('hr.manage') ? [
      { href: 'hr/attendance/board', label: tr('hr.navTeamAttendance'), icon: UsersRound },
      { href: 'hr/requests', label: tr('hr.navRequests'), icon: Inbox },
      { href: 'hr/payroll', label: tr('hr.navPayroll'), icon: Banknote },
      { href: 'hr/settings', label: tr('hr.navSettings'), icon: SlidersHorizontal },
    ] : []),
  ];
}

/** A module's name in the current language. */
export const moduleName = (m: { nameEn: string; nameKm: string }) => (currentLocale() === 'km' && m.nameKm ? m.nameKm : m.nameEn);
