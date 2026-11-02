'use client';

import { ChevronDown, ChevronRight, ChevronsLeft, ChevronsRight, Settings } from 'lucide-react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { type ReactNode, useState } from 'react';

import { Button } from '@/components/ui/button';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { useT } from '@/i18n/provider';
import type { OrgContext } from '@/lib/api/types';
import { cn } from '@/lib/utils';

import { globalNav, isActiveItem, MODULE_ICON, moduleHref, moduleName, type NavItem, settingsNav, subNav } from './nav';

const rowClass = (active: boolean, collapsed: boolean) => cn(
  'flex h-9 items-center gap-3 rounded-md px-2.5 text-sm font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground',
  active && 'bg-accent text-accent-foreground hover:bg-accent hover:text-accent-foreground',
  collapsed && 'justify-center px-0',
);

/** With the sidebar collapsed, the label shows as a tooltip. */
function WithTip({ collapsed, label, children }: { collapsed: boolean; label: string; children: ReactNode }) {
  if (!collapsed) return <>{children}</>;
  return (
    <Tooltip>
      <TooltipTrigger asChild>{children}</TooltipTrigger>
      <TooltipContent side="right">{label}</TooltipContent>
    </Tooltip>
  );
}

function Item({ org, item, collapsed, onNavigate, active }: { org: string; item: NavItem; collapsed: boolean; onNavigate?: () => void; active: boolean }) {
  return (
    <WithTip collapsed={collapsed} label={item.label}>
      <Link href={`/${org}/${item.href}`} onClick={onNavigate} aria-current={active ? 'page' : undefined} className={rowClass(active, collapsed)}>
        <item.icon className={cn('size-[18px] shrink-0', active && 'text-primary')} aria-hidden />
        {collapsed ? <span className="sr-only">{item.label}</span> : <span className="truncate">{item.label}</span>}
      </Link>
    </WithTip>
  );
}

function SectionTitle({ collapsed, children }: { collapsed: boolean; children: ReactNode }) {
  return collapsed
    ? <div className="mx-2 mt-4 mb-2 border-t" />
    : <p className="mt-5 mb-1 px-2.5 text-[11px] font-semibold tracking-wider text-muted-foreground/80 uppercase">{children}</p>;
}

/**
 * One app (module): its row opens its home; the app you are in shows its pages below, in small sections. Others
 * open with the chevron.
 */
function App({ org, ctx, m, collapsed, onNavigate, path }: {
  org: string; ctx: OrgContext; m: OrgContext['modules'][number]; collapsed: boolean; onNavigate?: () => void; path: string;
}) {
  const { t } = useT();
  const home = moduleHref(m.key);
  const inside = path === `/${org}/${home}` || path.startsWith(`/${org}/${home}/`);
  const [open, setOpen] = useState<boolean | null>(null);
  const expanded = !collapsed && (open ?? inside);
  const settings = settingsNav(ctx);
  // The module's pages, without its home (the row itself) unless the home starts a section; settings pages live
  // in the settings area.
  const pages = subNav(m.key, ctx).filter((i, n) => (n > 0 || i.group) && !settings.some((s) => s.href === i.href));
  const Icon = MODULE_ICON[m.key] ?? MODULE_ICON.business;
  const label = moduleName(m);
  const homeActive = inside && !pages.some((p) => isActiveItem(org, path, p));
  return (
    <li>
      <div className="flex items-center">
        <WithTip collapsed={collapsed} label={label}>
          <Link href={`/${org}/${home}`} onClick={onNavigate} aria-current={homeActive ? 'page' : undefined}
            className={cn(rowClass(homeActive, collapsed), 'min-w-0 flex-1', inside && !homeActive && 'text-foreground')}>
            <Icon className={cn('size-[18px] shrink-0', inside && 'text-primary')} aria-hidden />
            {collapsed ? <span className="sr-only">{label}</span> : <span className="truncate">{label}</span>}
          </Link>
        </WithTip>
        {!collapsed && pages.length > 0 && (
          <button type="button" onClick={() => setOpen(!expanded)} aria-expanded={expanded} aria-label={t('shell.toggleApp', { name: label })}
            className="grid size-7 shrink-0 place-items-center rounded-md text-muted-foreground hover:bg-muted hover:text-foreground">
            {expanded ? <ChevronDown className="size-3.5" /> : <ChevronRight className="size-3.5" />}
          </button>
        )}
      </div>
      {expanded && (
        <ul className="mt-0.5 mb-2 ml-[1.15rem] space-y-0.5 border-l pl-2">
          {pages.map((p) => {
            const active = isActiveItem(org, path, p);
            return (
              <li key={p.href}>
                {p.group && <p className="mt-2 mb-0.5 px-2 text-[10.5px] font-medium tracking-wide text-muted-foreground/70 uppercase">{p.group}</p>}
                <Link href={`/${org}/${p.href}`} onClick={onNavigate} aria-current={active ? 'page' : undefined}
                  className={cn('flex h-8 items-center gap-2.5 rounded-md px-2 text-[13px] text-muted-foreground transition-colors hover:bg-muted hover:text-foreground',
                    active && 'bg-accent font-medium text-accent-foreground hover:bg-accent hover:text-accent-foreground')}>
                  <p.icon className={cn('size-4 shrink-0', active && 'text-primary')} aria-hidden /><span className="truncate">{p.label}</span>
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </li>
  );
}

/** The navigation column; also used inside the phone menu (never collapsed there). */
export function SidebarNav({ ctx, collapsed = false, onNavigate }: { ctx: OrgContext; collapsed?: boolean; onNavigate?: () => void }) {
  const { t } = useT();
  const path = usePathname();
  const org = ctx.org.slug;
  const apps = ctx.modules.filter((m) => m.kind === 'INDUSTRY' && m.enabled);
  const settings = settingsNav(ctx);
  const inSettings = settings.some((i) => isActiveItem(org, path, i));
  return (
    <nav aria-label={t('shell.mainNav')} className="flex min-h-0 flex-1 flex-col">
      <div className="flex-1 overflow-y-auto px-2 py-3">
      {!collapsed && <p className="mb-1 px-2.5 text-[11px] font-semibold tracking-wider text-muted-foreground/80 uppercase">{t('shell.navWorkspace')}</p>}
      <ul className="space-y-0.5">
        {globalNav(ctx).map((i) => (
          <li key={i.href}><Item org={org} item={i} collapsed={collapsed} onNavigate={onNavigate} active={!inSettings && isActiveItem(org, path, i)} /></li>
        ))}
      </ul>
      {apps.length > 0 && (
        <>
          <SectionTitle collapsed={collapsed}>{t('shell.navApps')}</SectionTitle>
          <ul className="space-y-0.5">
            {apps.map((m) => <App key={m.key} org={org} ctx={ctx} m={m} collapsed={collapsed} onNavigate={onNavigate} path={inSettings ? '' : path} />)}
          </ul>
        </>
      )}
      </div>
      {settings.length > 0 && (
        <div className="border-t px-2 py-2">
          <Item org={org} collapsed={collapsed} onNavigate={onNavigate} active={inSettings}
            item={{ href: settings[0].href, label: t('shell.navSettings'), icon: Settings }} />
        </div>
      )}
    </nav>
  );
}

export function Sidebar({ ctx, collapsed, setCollapsed }: { ctx: OrgContext; collapsed: boolean; setCollapsed: (v: boolean) => void }) {
  const { t } = useT();
  return (
    <aside
      className={cn('sticky top-0 hidden h-dvh shrink-0 flex-col border-r bg-sidebar transition-[width] duration-200 lg:flex print:hidden', collapsed ? 'w-16' : 'w-62')}
    >
      <div className={cn('flex h-14 items-center gap-2 border-b px-4', collapsed && 'justify-center px-0')}>
        <span className="grid size-7 shrink-0 place-items-center rounded-md bg-primary text-xs font-bold text-primary-foreground">BP</span>
        {!collapsed && <span className="truncate text-sm font-semibold">Business Platform</span>}
      </div>
      <SidebarNav ctx={ctx} collapsed={collapsed} />
      <div className="border-t p-2">
        <Button variant="ghost" size="sm" className={cn('w-full justify-start gap-2 text-muted-foreground', collapsed && 'justify-center')}
          onClick={() => setCollapsed(!collapsed)} aria-label={t(collapsed ? 'shell.expandSidebar' : 'shell.collapseSidebar')}>
          {collapsed ? <ChevronsRight className="size-4" /> : <><ChevronsLeft className="size-4" /> {t('shell.collapse')}</>}
        </Button>
      </div>
    </aside>
  );
}
