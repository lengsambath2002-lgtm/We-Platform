import {
  BadgeCheck,
  CreditCard,
  DoorOpen,
  KeyRound,
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
  Handshake,
  HardHat,
  ShoppingCart,
  Sprout,
  Ticket,
  Truck,
  FileSignature,
  FileText,
  FolderKanban,
  GitBranch,
  GraduationCap,
  Hammer,
  Package,
  Receipt,
  LayoutDashboard,
  Network,
  Settings,
  ScrollText,
  Store,
  UserCog,
  UserRound,
  Users,
  Wrench,
  ClipboardCheck,
  Presentation,
  School,
  Wallet,
} from 'lucide-react';

import type { ComponentType, SVGProps } from 'react';

import { VirtualBizMark } from '@/components/common/virtualbiz-mark';
import { currentLocale, tr } from '@/i18n/core';
import type { OrgContext } from '@/lib/api/types';

export interface NavItem {
  href: string;
  label: string;
  icon: ModuleIcon;
  /** Active only on this exact page (a module home with sub-pages under it). */
  exact?: boolean;
  /** Shown indented under the item before it. */
  sub?: boolean;
  /** The small heading of the section this item starts or belongs to (module pages, the settings menu). */
  group?: string;
  /** Other pages (paths under the organisation) that count as this item, e.g. the module reports for Reports. */
  match?: string[];
}

/** Whether {@code item} is the page at {@code path} (a full path, /{org}/…). */
export function isActiveItem(org: string, path: string, item: NavItem) {
  const at = (href: string, exact?: boolean) => {
    const full = `/${org}/${href}`;
    return exact ? path === full : path === full || path.startsWith(`${full}/`);
  };
  return at(item.href, item.exact) || (item.match ?? []).some((m) => at(m));
}

/** The workspace: everyday pages, filtered by the permissions the server sent (it re-checks every request). */
export function globalNav(ctx: OrgContext): NavItem[] {
  const can = (p: string) => ctx.permissions.includes(p);
  const items: (NavItem & { need?: string })[] = [
    { href: 'overview', label: tr('shell.overview'), icon: LayoutDashboard },
    { href: 'my-tasks', label: tr('shell.myTasks'), icon: CheckSquare, need: 'projects.view' },
    { href: 'approvals', label: tr('shell.approvals'), icon: BadgeCheck, need: 'approvals.view' },
    { href: 'calendar', label: tr('shell.calendar'), icon: CalendarDays, need: 'calendar.view' },
    { href: 'projects', label: tr('shell.projects'), icon: FolderKanban, need: 'projects.view' },
    { href: 'documents', label: tr('documents.title'), icon: FileText, need: 'documents.view' },
    { href: 'teams', label: tr('shell.navPeople'), icon: Users, need: 'teams.view', match: ['hr/people'] },
    { href: 'structure', label: tr('structure.nav'), icon: Network, need: 'org.structure.view' },
    { href: 'reports', label: tr('shell.reports'), icon: BarChart3, need: 'reports.view', match: ['business/reports', 'education/reports', 'service/reports'] },
  ];
  return items.filter((i) => !i.need || can(i.need));
}

const enabled = (ctx: OrgContext, key: string) => ctx.modules.some((m) => m.key === key && m.enabled);

/**
 * The settings area's menu: the organisation, people and access, billing, security, and each switched-on app's
 * settings. Someone who may only see billing or the audit log gets just those.
 */
export function settingsNav(ctx: OrgContext): NavItem[] {
  const can = (p: string) => ctx.permissions.includes(p);
  const org = can('org.settings.manage');
  const name = (key: string) => {
    const m = ctx.modules.find((x) => x.key === key);
    return m ? moduleName(m) : key;
  };
  const apps: NavItem[] = [
    ...(enabled(ctx, 'business') && can('business.manage') ? [{ href: 'business/settings', label: name('business'), icon: MODULE_ICON.business }] : []),
    ...(enabled(ctx, 'education') && can('education.manage') ? [{ href: 'education/settings', label: name('education'), icon: MODULE_ICON.education }] : []),
    ...(enabled(ctx, 'hr') && can('hr.manage') ? [{ href: 'hr/settings', label: name('hr'), icon: MODULE_ICON.hr }] : []),
    ...(enabled(ctx, 'service') && can('service.admin') ? [{ href: 'service/settings', label: name('service'), icon: MODULE_ICON.service }] : []),
  ];
  const groups: { title: string; items: NavItem[] }[] = [
    { title: tr('shell.setOrganisation'), items: org ? [
      { href: 'admin/settings', label: tr('shell.setGeneral'), icon: Settings },
      { href: 'admin/modules', label: tr('shell.modules'), icon: Blocks },
      { href: 'admin/workflows', label: tr('shell.workflows'), icon: GitBranch },
    ] : [] },
    { title: tr('shell.setAccess'), items: [
      ...(org ? [{ href: 'admin/members', label: tr('shell.setMembers'), icon: UserCog }] : []),
      ...(org && can('org.roles.manage') ? [{ href: 'admin/roles', label: tr('roles.nav'), icon: KeyRound }] : []),
    ] },
    { title: tr('shell.setBilling'), items: can('billing.view') ? [{ href: 'admin/billing', label: tr('billing.nav'), icon: CreditCard }] : [] },
    { title: tr('shell.setSecurity'), items: can('activity.view') ? [{ href: 'admin/audit', label: tr('collab.auditNav'), icon: ScrollText }] : [] },
    { title: tr('shell.setApps'), items: apps },
  ];
  return groups.flatMap((g) => g.items.map((i, n) => (n === 0 ? { ...i, group: g.title } : i)));
}

/** An icon component: a lucide icon, or a brand mark such as VirtualBiz's. */
export type ModuleIcon = ComponentType<SVGProps<SVGSVGElement>>;

export const MODULE_ICON: Record<string, ModuleIcon> = {
  projects: FolderKanban,
  business: Briefcase,
  education: GraduationCap,
  // Retail & Commerce is VirtualBiz: its own mark.
  retail: VirtualBizMark,
  hr: Users,
  service: Wrench,
};

/** Where a switched-on module lives: built modules (projects, business, education, HR, retail, service) have their own pages, the others a "planned" page. */
export const moduleHref = (key: string) => (['projects', 'business', 'education', 'hr', 'retail', 'service'].includes(key) ? key : `m/${key}`);

/** Modules with a home page and sub-pages listed under it in the sidebar. */
export const subNav = (key: string, ctx: OrgContext): NavItem[] => (key === 'hr' ? hrNav(ctx) : key === 'business' ? businessNav(ctx)
  : key === 'education' ? educationNav(ctx) : key === 'service' ? serviceNav(ctx) : key === 'retail' ? retailNav(ctx) : []);

/**
 * Service & Operations' pages for the sidebar, filtered by permission (the server re-checks): dispatchers and Admins
 * get everything; technicians their dashboard, tickets, bookings and their work orders. (Settings are in the settings
 * area, reports in Reports.)
 */
export function serviceNav(ctx: OrgContext): NavItem[] {
  const can = (p: string) => ctx.permissions.includes(p);
  if (!can('service.view')) return [];
  const manage = can('service.manage');
  const work: NavItem[] = [
    { href: 'service/tickets', label: tr('service.navTickets'), icon: Ticket, match: ['service/requests'] },
    // Bookings live in VirtualBiz, linked through Retail & Commerce; without it the page has nothing to show.
    ...(enabled(ctx, 'retail') ? [{ href: 'service/bookings', label: tr('service.navBookings'), icon: CalendarClock }] : []),
    { href: 'service/work-orders', label: manage ? tr('service.navWorkOrders') : tr('service.navMyWorkOrders'), icon: ClipboardList },
  ];
  const resources: NavItem[] = manage ? [
    { href: 'service/technicians', label: tr('service.navTechnicians'), icon: HardHat },
    { href: 'service/assets', label: tr('service.navAssets'), icon: Cpu, match: ['service/customers'] },
    { href: 'service/maintenance', label: tr('service.navMaintenance'), icon: Hammer },
  ] : [];
  return [
    { href: 'service', label: tr('service.navDashboard'), icon: LayoutDashboard, exact: true },
    ...grouped(tr('shell.grpWork'), work),
    ...grouped(tr('shell.grpResources'), resources),
  ];
}

/** Items with the section heading on the first one. */
function grouped(title: string, items: NavItem[]): NavItem[] {
  return items.map((i, n) => (n === 0 ? { ...i, group: title } : i));
}

/** Purchasing: its overview (with the orders) and the suppliers. */
function buyingNav(): NavItem[] {
  return [
    { href: 'business/purchasing', label: tr('retail.navPurchasing'), icon: ShoppingCart, match: ['business/purchase-orders'] },
    { href: 'business/suppliers', label: tr('retail.navSuppliers'), icon: Truck },
  ];
}

/** General Business's pages for the sidebar, filtered by permission (the server re-checks). */
export function businessNav(ctx: OrgContext): NavItem[] {
  const can = (p: string) => ctx.permissions.includes(p);
  if (!can('business.view')) return [];
  return [
    { href: 'business', label: tr('business.navOverview'), icon: LayoutDashboard, exact: true },
    ...grouped(tr('shell.grpSales'), [
      { href: 'business/deals', label: tr('business.navDeals'), icon: Handshake },
      { href: 'business/customers', label: tr('business.navCustomers'), icon: UserRound },
      { href: 'business/quotes', label: tr('business.navQuotes'), icon: FileSignature },
      { href: 'business/invoices', label: tr('business.navInvoices'), icon: Receipt },
    ]),
    ...grouped(tr('shell.grpPurchasing'), buyingNav()),
    ...grouped(tr('shell.grpOperations'), [
      { href: 'business/products', label: tr('business.navProducts'), icon: Package },
      { href: 'business/expenses', label: tr('business.navExpenses'), icon: Banknote },
    ]),
  ];
}

/**
 * Education's pages for the sidebar, filtered by permission (the server re-checks): admins see everything; teachers
 * their classes' pages, without fees.
 */
export function educationNav(ctx: OrgContext): NavItem[] {
  const can = (p: string) => ctx.permissions.includes(p);
  if (!can('education.view')) return [];
  const manage = can('education.manage');
  return [
    { href: 'education', label: tr('education.navHome'), icon: LayoutDashboard, exact: true },
    ...grouped(tr('shell.grpPeople'), [
      { href: 'education/students', label: tr('education.navStudents'), icon: GraduationCap, match: ['education/parents'] },
      { href: 'education/teachers', label: tr('education.navTeachers'), icon: Presentation },
    ]),
    ...grouped(tr('shell.grpTeaching'), [
      { href: 'education/classes', label: tr('education.navClasses'), icon: School },
      { href: 'education/timetable', label: tr('education.navTimetable'), icon: CalendarClock },
      { href: 'education/attendance', label: tr('education.navAttendance'), icon: ClipboardCheck },
      { href: 'education/exams', label: tr('education.navAssessments'), icon: ClipboardList, match: ['education/gradebook'] },
    ]),
    ...grouped(tr('shell.grpFinance'), manage ? [{ href: 'education/fees', label: tr('education.navFees'), icon: Wallet }] : []),
  ];
}

/** Retail &amp; Commerce: the VirtualBiz shops, and purchasing (suppliers, purchase orders). */
export function retailNav(ctx: OrgContext): NavItem[] {
  const can = (p: string) => ctx.permissions.includes(p);
  if (!can('retail.view')) return [];
  return [
    { href: 'retail', label: tr('retail.navShops'), icon: Store, exact: true },
    // Purchasing lives under General Business; without it, Retail shows it here.
    ...(enabled(ctx, 'business') && can('business.view') ? [] : grouped(tr('shell.grpPurchasing'), buyingNav())),
  ];
}

/** HR's pages for the sidebar, filtered by permission (the server re-checks): what is mine, then what I manage. */
export function hrNav(ctx: OrgContext): NavItem[] {
  const can = (p: string) => ctx.permissions.includes(p);
  if (!can('hr.view')) return [];
  return [
    ...grouped(tr('shell.grpMe'), [
      { href: 'hr', label: tr('hr.navHome'), icon: CalendarCheck2, exact: true, match: ['hr/dashboard', 'hr/leave', 'hr/requests'] },
      { href: 'hr/attendance', label: tr('hr.navAttendance'), icon: Clock },
      { href: 'hr/payslips', label: tr('hr.navPayslips'), icon: Receipt },
      { href: 'hr/reviews', label: tr('hr.navGrowth'), icon: Sprout, match: ['hr/training'] },
    ]),
    ...grouped(tr('shell.grpManage'), [
      ...(can('hr.recruit') ? [{ href: 'hr/recruitment', label: tr('hr.navRecruitment'), icon: Briefcase }] : []),
      ...(can('hr.team') ? [{ href: 'hr/offboarding', label: tr('offboarding.nav'), icon: DoorOpen }] : []),
      ...(can('hr.payroll') ? [{ href: 'hr/payroll', label: tr('hr.navPayroll'), icon: Banknote }] : []),
    ]),
  ];
}

/** A module's name in the current language. */
export const moduleName = (m: { nameEn: string; nameKm: string }) => (currentLocale() === 'km' && m.nameKm ? m.nameKm : m.nameEn);
