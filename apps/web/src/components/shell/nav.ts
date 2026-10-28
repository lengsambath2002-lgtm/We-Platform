import {
  BadgeCheck,
  BarChart3,
  Blocks,
  Briefcase,
  CalendarCheck2,
  CalendarDays,
  CheckSquare,
  Contact,
  FileText,
  FolderKanban,
  GitBranch,
  GraduationCap,
  Inbox,
  LayoutDashboard,
  Settings,
  type LucideIcon,
  ScrollText,
  ShoppingCart,
  SlidersHorizontal,
  UserCog,
  Users,
  Wrench,
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
  retail: ShoppingCart,
  hr: Users,
  service: Wrench,
};

/** Where a switched-on module lives: built modules have their own pages, the others a "planned" page. */
export const moduleHref = (key: string) => (key === 'projects' ? 'projects' : key === 'hr' ? 'hr' : `m/${key}`);

/** HR's pages for the sidebar and HR's tabs, filtered by permission (the server re-checks). */
export function hrNav(ctx: OrgContext): NavItem[] {
  const can = (p: string) => ctx.permissions.includes(p);
  if (!can('hr.view')) return [];
  return [
    { href: 'hr', label: tr('hr.navHome'), icon: CalendarCheck2, exact: true },
    { href: 'hr/people', label: tr('hr.navPeople'), icon: Contact },
    ...(can('hr.manage') ? [
      { href: 'hr/requests', label: tr('hr.navRequests'), icon: Inbox },
      { href: 'hr/settings', label: tr('hr.navSettings'), icon: SlidersHorizontal },
    ] : []),
  ];
}

/** A module's name in the current language. */
export const moduleName = (m: { nameEn: string; nameKm: string }) => (currentLocale() === 'km' && m.nameKm ? m.nameKm : m.nameEn);
