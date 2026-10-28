'use client';

import { ChevronsLeft, ChevronsRight } from 'lucide-react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';

import { Button } from '@/components/ui/button';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import type { OrgContext } from '@/lib/api/types';
import { cn } from '@/lib/utils';

import { adminNav, globalNav, MODULE_ICON, type NavItem } from './nav';

function Item({ org, item, collapsed, onNavigate }: { org: string; item: NavItem; collapsed: boolean; onNavigate?: () => void }) {
  const path = usePathname();
  const href = `/${org}/${item.href}`;
  const active = path === href || path.startsWith(`${href}/`);
  const link = (
    <Link
      href={href}
      onClick={onNavigate}
      aria-current={active ? 'page' : undefined}
      className={cn(
        'flex h-9 items-center gap-3 rounded-md px-2.5 text-sm font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground',
        active && 'bg-accent text-accent-foreground hover:bg-accent hover:text-accent-foreground',
        collapsed && 'justify-center px-0',
      )}
    >
      <item.icon className="size-[18px] shrink-0" aria-hidden />
      {collapsed ? <span className="sr-only">{item.label}</span> : <span className="truncate">{item.label}</span>}
    </Link>
  );
  if (!collapsed) return link;
  return (
    <Tooltip>
      <TooltipTrigger asChild>{link}</TooltipTrigger>
      <TooltipContent side="right">{item.label}</TooltipContent>
    </Tooltip>
  );
}

function Section({ title, collapsed, children }: { title: string; collapsed: boolean; children: React.ReactNode }) {
  return (
    <div className="mt-5">
      {collapsed ? <div className="mx-2 mb-2 border-t" /> : <p className="mb-1 px-2.5 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">{title}</p>}
      <div className="space-y-0.5">{children}</div>
    </div>
  );
}

/** The navigation column; also used inside the phone menu (never collapsed there). */
export function SidebarNav({ ctx, collapsed = false, onNavigate }: { ctx: OrgContext; collapsed?: boolean; onNavigate?: () => void }) {
  const org = ctx.org.slug;
  const modules = ctx.modules.filter((m) => m.kind === 'INDUSTRY' && m.enabled);
  const admin = adminNav(ctx);
  return (
    <nav aria-label="Main" className="flex-1 overflow-y-auto px-2 py-3">
      <div className="space-y-0.5">
        {globalNav(ctx).map((i) => <Item key={i.href} org={org} item={i} collapsed={collapsed} onNavigate={onNavigate} />)}
      </div>
      {modules.length > 0 && (
        <Section title="Modules" collapsed={collapsed}>
          {modules.map((m) => (
            <Item key={m.key} org={org} collapsed={collapsed} onNavigate={onNavigate}
              item={{ href: `m/${m.key}`, label: m.nameEn, icon: MODULE_ICON[m.key] ?? MODULE_ICON.business }} />
          ))}
        </Section>
      )}
      {admin.length > 0 && (
        <Section title="Administration" collapsed={collapsed}>
          {admin.map((i) => <Item key={i.href} org={org} item={i} collapsed={collapsed} onNavigate={onNavigate} />)}
        </Section>
      )}
    </nav>
  );
}

export function Sidebar({ ctx, collapsed, setCollapsed }: { ctx: OrgContext; collapsed: boolean; setCollapsed: (v: boolean) => void }) {
  return (
    <aside
      className={cn('sticky top-0 hidden h-dvh shrink-0 flex-col border-r bg-sidebar transition-[width] duration-200 lg:flex', collapsed ? 'w-16' : 'w-62')}
    >
      <div className={cn('flex h-14 items-center gap-2 border-b px-4', collapsed && 'justify-center px-0')}>
        <span className="grid size-7 shrink-0 place-items-center rounded-md bg-primary text-xs font-bold text-primary-foreground">BP</span>
        {!collapsed && <span className="truncate text-sm font-semibold">Business Platform</span>}
      </div>
      <SidebarNav ctx={ctx} collapsed={collapsed} />
      <div className="border-t p-2">
        <Button variant="ghost" size="sm" className={cn('w-full justify-start gap-2 text-muted-foreground', collapsed && 'justify-center')}
          onClick={() => setCollapsed(!collapsed)} aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}>
          {collapsed ? <ChevronsRight className="size-4" /> : <><ChevronsLeft className="size-4" /> Collapse</>}
        </Button>
      </div>
    </aside>
  );
}
