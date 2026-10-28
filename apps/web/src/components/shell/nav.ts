import {
  BadgeCheck,
  Blocks,
  Briefcase,
  CalendarDays,
  CheckSquare,
  FolderKanban,
  GitBranch,
  GraduationCap,
  LayoutDashboard,
  type LucideIcon,
  ScrollText,
  ShoppingCart,
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
  ];
  return items.filter((i) => !i.need || can(i.need));
}

export function adminNav(ctx: OrgContext): NavItem[] {
  if (!ctx.permissions.includes('org.settings.manage')) return [];
  return [
    { href: 'admin/modules', label: tr('shell.modules'), icon: Blocks },
    { href: 'admin/workflows', label: tr('shell.workflows'), icon: GitBranch },
    { href: 'admin/members', label: tr('shell.membersRoles'), icon: UserCog },
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

/** A module's name in the current language. */
export const moduleName = (m: { nameEn: string; nameKm: string }) => (currentLocale() === 'km' && m.nameKm ? m.nameKm : m.nameEn);
