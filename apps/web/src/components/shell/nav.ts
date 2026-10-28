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
  ShoppingCart,
  UserCog,
  Users,
  Wrench,
} from 'lucide-react';

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
    { href: 'overview', label: 'Overview', icon: LayoutDashboard },
    { href: 'my-tasks', label: 'My Tasks', icon: CheckSquare, need: 'projects.view' },
    { href: 'projects', label: 'Projects', icon: FolderKanban, need: 'projects.view' },
    { href: 'calendar', label: 'Calendar', icon: CalendarDays, need: 'calendar.view' },
    { href: 'teams', label: 'Teams', icon: Users, need: 'teams.view' },
    { href: 'approvals', label: 'Approvals', icon: BadgeCheck, need: 'approvals.view' },
  ];
  return items.filter((i) => !i.need || can(i.need));
}

export function adminNav(ctx: OrgContext): NavItem[] {
  if (!ctx.permissions.includes('org.settings.manage')) return [];
  return [
    { href: 'admin/modules', label: 'Modules', icon: Blocks },
    { href: 'admin/workflows', label: 'Workflows', icon: GitBranch },
    { href: 'admin/members', label: 'Members & roles', icon: UserCog },
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
