'use client';

import { Bell, Blocks, Check, ChevronsUpDown, LogOut, Menu, Monitor, Moon, Plus, Search, ShieldCheck, Sun, UserRound } from 'lucide-react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useTheme } from 'next-themes';
import { useEffect, useState } from 'react';

import { PersonAvatar } from '@/components/common/person';
import { Button } from '@/components/ui/button';
import {
  CommandDialog,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@/components/ui/command';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { Sheet, SheetContent, SheetTitle } from '@/components/ui/sheet';
import { LOCALES, toLocale } from '@/i18n/core';
import type { MessageKey } from '@/i18n/messages';
import { useT } from '@/i18n/provider';
import { orgApi, useMyTasks, useNotifications, useOrgs, useProjects } from '@/lib/api/queries';
import type { OrgContext } from '@/lib/api/types';
import { useSession } from '@/lib/auth/session';
import { ago } from '@/lib/format';
import { cn } from '@/lib/utils';

import { LOCALE_NAME, useChangeLocale } from './language-switch';
import { MODULE_ICON, moduleHref, moduleName } from './nav';
import { SidebarNav } from './sidebar';
import { useQueryClient } from '@tanstack/react-query';

function OrgSwitcher({ ctx }: { ctx: OrgContext }) {
  const { t } = useT();
  const { data: orgs = [] } = useOrgs();
  const router = useRouter();
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" className="h-9 max-w-[220px] gap-2 px-2" aria-label={t('shell.switchOrg')}>
          <span className="grid size-6 shrink-0 place-items-center rounded bg-muted text-[11px] font-semibold">{ctx.org.name.slice(0, 1)}</span>
          <span className="truncate text-sm font-medium">{ctx.org.name}</span>
          <ChevronsUpDown className="size-3.5 shrink-0 text-muted-foreground" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="w-64">
        <DropdownMenuLabel className="text-xs text-muted-foreground">{t('shell.organisations')}</DropdownMenuLabel>
        {orgs.map((o) => (
          <DropdownMenuItem key={o.id} onSelect={() => router.push(`/${o.slug}/overview`)}>
            <span className="truncate">{o.name}</span>
            {o.slug === ctx.org.slug && <Check className="ml-auto size-4" />}
          </DropdownMenuItem>
        ))}
        <DropdownMenuSeparator />
        <DropdownMenuItem onSelect={() => router.push('/onboarding')}>
          <Plus className="size-4" /> {t('shell.createOrg')}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

function ModuleSwitcher({ ctx }: { ctx: OrgContext }) {
  const { t } = useT();
  const org = ctx.org.slug;
  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button variant="ghost" size="sm" className="hidden gap-2 sm:inline-flex" aria-label={t('shell.modules')}>
          <Blocks className="size-4" /> <span className="hidden md:inline">{t('shell.modules')}</span>
        </Button>
      </PopoverTrigger>
      <PopoverContent align="start" className="w-72 p-2">
        <p className="px-2 pb-2 text-xs font-medium text-muted-foreground">{t('shell.modulesIn', { org: ctx.org.name })}</p>
        <ul className="space-y-0.5">
          {ctx.modules.map((m) => {
            const Icon = MODULE_ICON[m.key] ?? Blocks;
            const href = `/${org}/${moduleHref(m.key)}`;
            const body = (
              <>
                <Icon className="size-4 shrink-0" aria-hidden />
                <span className="flex-1 truncate">{moduleName(m)}</span>
                <span className="text-[11px] text-muted-foreground">{t(!m.available ? 'shell.comingSoon' : m.enabled ? 'shell.on' : 'shell.off')}</span>
              </>
            );
            return (
              <li key={m.key}>
                {m.enabled ? (
                  <Link href={href} className="flex items-center gap-2 rounded-md px-2 py-1.5 text-sm hover:bg-muted">{body}</Link>
                ) : (
                  <div className="flex items-center gap-2 rounded-md px-2 py-1.5 text-sm text-muted-foreground" aria-disabled>{body}</div>
                )}
              </li>
            );
          })}
        </ul>
        {ctx.permissions.includes('org.modules.manage') && (
          <Button asChild variant="outline" size="sm" className="mt-2 w-full">
            <Link href={`/${org}/admin/modules`}>{t('shell.manageModules')}</Link>
          </Button>
        )}
      </PopoverContent>
    </Popover>
  );
}

const PAGES: [string, MessageKey][] = [
  ['overview', 'shell.overview'],
  ['my-tasks', 'shell.myTasks'],
  ['projects', 'shell.projects'],
  ['calendar', 'shell.calendar'],
  ['teams', 'shell.teams'],
  ['approvals', 'shell.approvals'],
];

function GlobalSearch({ ctx }: { ctx: OrgContext }) {
  const { t } = useT();
  const [open, setOpen] = useState(false);
  const router = useRouter();
  const org = ctx.org.slug;
  const projectsOn = ctx.permissions.includes('projects.view');
  const projects = useProjects(org);
  const tasks = useMyTasks(org);
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        setOpen((v) => !v);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);
  const go = (href: string) => {
    setOpen(false);
    router.push(href);
  };
  return (
    <>
      <Button variant="outline" size="sm" onClick={() => setOpen(true)}
        className="h-9 w-9 justify-start gap-2 px-0 text-muted-foreground sm:w-56 sm:px-3" aria-label={t('common.search')}>
        <Search className="mx-auto size-4 sm:mx-0" />
        <span className="hidden flex-1 text-left sm:inline">{t('shell.searchPlaceholder')}</span>
        <kbd className="hidden rounded border bg-muted px-1.5 text-[10px] sm:inline">⌘K</kbd>
      </Button>
      <CommandDialog open={open} onOpenChange={setOpen} title={t('common.search')} description={t('shell.searchDescription')}>
        <CommandInput placeholder={t('shell.searchInput')} />
        <CommandList>
          <CommandEmpty>{t('shell.nothingFound')}</CommandEmpty>
          {projectsOn && (projects.data?.length ?? 0) > 0 && (
            <CommandGroup heading={t('shell.projects')}>
              {projects.data!.map((p) => (
                <CommandItem key={p.id} value={`${p.key} ${p.name}`} onSelect={() => go(`/${org}/projects/${p.key}`)}>
                  <span className="font-mono text-xs text-muted-foreground">{p.key}</span> {p.name}
                </CommandItem>
              ))}
            </CommandGroup>
          )}
          {projectsOn && (tasks.data?.length ?? 0) > 0 && (
            <CommandGroup heading={t('shell.myTasksGroup')}>
              {tasks.data!.map((task) => (
                <CommandItem key={task.id} value={`${task.key} ${task.title}`} onSelect={() => go(`/${org}/tasks/${task.id}`)}>
                  <span className="font-mono text-xs text-muted-foreground">{task.key}</span> {task.title}
                </CommandItem>
              ))}
            </CommandGroup>
          )}
          <CommandGroup heading={t('shell.pages')}>
            {PAGES.map(([p, key]) => (
              <CommandItem key={p} value={`${p} ${t(key)}`} onSelect={() => go(`/${org}/${p}`)}>
                {t(key)}
              </CommandItem>
            ))}
          </CommandGroup>
        </CommandList>
      </CommandDialog>
    </>
  );
}

function Notifications({ org }: { org: string }) {
  const { t } = useT();
  const { data } = useNotifications(org);
  const qc = useQueryClient();
  const router = useRouter();
  const unread = data?.unread ?? 0;
  const [open, setOpen] = useState(false);
  const read = async (id?: string) => {
    await orgApi.readNotifications(org, id);
    qc.invalidateQueries({ queryKey: ['org', org, 'notifications'] });
  };
  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button variant="ghost" size="icon" className="relative" aria-label={unread ? t('shell.notificationsUnread', { count: unread }) : t('shell.notifications')}>
          <Bell className="size-[18px]" />
          {unread > 0 && (
            <span className="absolute right-1 top-1 grid min-w-4 place-items-center rounded-full bg-destructive px-1 text-[10px] font-semibold leading-4 text-white">
              {unread > 99 ? '99+' : unread}
            </span>
          )}
        </Button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-[22rem] p-0">
        <div className="flex items-center justify-between border-b px-3 py-2">
          <p className="text-sm font-semibold">{t('shell.notifications')}</p>
          {unread > 0 && <Button variant="ghost" size="sm" className="h-7 text-xs" onClick={() => read()}>{t('shell.markAllRead')}</Button>}
        </div>
        <ul className="max-h-96 overflow-y-auto">
          {(data?.items ?? []).length === 0 && <li className="px-3 py-8 text-center text-sm text-muted-foreground">{t('shell.allCaughtUp')}</li>}
          {(data?.items ?? []).map((n) => (
            <li key={n.id}>
              <button type="button" onClick={() => { void read(n.id); if (n.link) { setOpen(false); router.push(n.link); } }}
                className={cn('flex w-full gap-2 px-3 py-2.5 text-left hover:bg-muted', !n.readAt && 'bg-accent/40')}>
                <span className={cn('mt-1.5 size-2 shrink-0 rounded-full', n.readAt ? 'bg-transparent' : 'bg-primary')} />
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-medium">{n.title}</span>
                  {n.body && <span className="line-clamp-2 block text-xs text-muted-foreground">{n.body}</span>}
                  <span className="text-[11px] text-muted-foreground">{ago(n.createdAt)}</span>
                </span>
              </button>
            </li>
          ))}
        </ul>
        <div className="border-t p-1">
          <Button asChild variant="ghost" size="sm" className="w-full">
            <Link href={`/${org}/notifications`} onClick={() => setOpen(false)}>{t('collab.seeAll')}</Link>
          </Button>
        </div>
      </PopoverContent>
    </Popover>
  );
}

/** One choice in a compact segmented row inside the account menu; choosing keeps the menu open. */
function MenuChoice({ on, label, onPick, children }: { on: boolean; label: string; onPick: () => void; children: React.ReactNode }) {
  return (
    <DropdownMenuItem role="menuitemradio" aria-checked={on} aria-label={label} title={label}
      onSelect={(e) => { e.preventDefault(); onPick(); }}
      className={cn('h-7 flex-1 justify-center gap-1.5 rounded-md px-2 text-xs text-muted-foreground focus:text-foreground',
        on && 'bg-background text-foreground shadow-xs focus:bg-background')}>
      {children}
    </DropdownMenuItem>
  );
}

/** A labelled row with a segmented control on the right (theme, language). */
function MenuRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-3 px-2 py-1.5">
      <span className="text-sm text-foreground/80">{label}</span>
      <div role="group" aria-label={label} className="flex w-36 items-center gap-0.5 rounded-lg bg-muted p-0.5">{children}</div>
    </div>
  );
}

/** The account menu: who you are, your account and (for super admins) the console, theme, language, sign out. */
function UserMenu() {
  const { t, locale } = useT();
  const { user, signOut } = useSession();
  const { theme, setTheme } = useTheme();
  const changeLocale = useChangeLocale();
  const router = useRouter();
  if (!user) return null;
  const themes = [
    { value: 'system', label: t('shell.themeDevice'), icon: Monitor },
    { value: 'light', label: t('shell.themeLight'), icon: Sun },
    { value: 'dark', label: t('shell.themeDark'), icon: Moon },
  ];
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" className="rounded-full" aria-label={t('shell.accountMenu')}>
          <PersonAvatar person={user} className="size-8" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" sideOffset={8} className="w-72 rounded-xl p-1.5">
        <div className="flex items-center gap-3 px-2 pt-1.5 pb-2.5">
          <PersonAvatar person={user} className="size-10 text-sm" />
          <div className="min-w-0 flex-1 leading-tight">
            <p className="truncate text-sm font-semibold">{user.name}</p>
            <p className="mt-0.5 truncate text-xs text-muted-foreground">{user.email}</p>
          </div>
        </div>
        <DropdownMenuSeparator />
        <DropdownMenuItem className="h-9 gap-2.5" onSelect={() => router.push('/account')}><UserRound className="size-4" />{t('shell.account')}</DropdownMenuItem>
        {user.superAdmin && (
          <DropdownMenuItem className="h-9 gap-2.5" onSelect={() => router.push('/admin')}>
            <ShieldCheck className="size-4" />{t('shell.platformAdmin')}
          </DropdownMenuItem>
        )}
        <DropdownMenuSeparator />
        <MenuRow label={t('shell.theme')}>
          {themes.map((x) => (
            <MenuChoice key={x.value} on={theme === x.value} label={x.label} onPick={() => setTheme(x.value)}><x.icon className="size-3.5" /></MenuChoice>
          ))}
        </MenuRow>
        <MenuRow label={t('common.language')}>
          {LOCALES.map((l) => (
            <MenuChoice key={l} on={locale === l} label={LOCALE_NAME[l]} onPick={() => void changeLocale(toLocale(l))}>
              <span lang={l}>{l === 'en' ? 'EN' : LOCALE_NAME[l]}</span>
            </MenuChoice>
          ))}
        </MenuRow>
        <DropdownMenuSeparator />
        <DropdownMenuItem className="h-9 gap-2.5" onSelect={async () => { await signOut(); router.replace('/sign-in'); }}>
          <LogOut className="size-4" />{t('shell.signOut')}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

export function Header({ ctx }: { ctx: OrgContext }) {
  const { t } = useT();
  const [menu, setMenu] = useState(false);
  return (
    <header className="sticky top-0 z-30 flex h-14 print:hidden items-center gap-2 border-b bg-background/95 px-3 backdrop-blur supports-[backdrop-filter]:bg-background/80 sm:px-4">
      <Button variant="ghost" size="icon" className="lg:hidden" onClick={() => setMenu(true)} aria-label={t('shell.openMenu')}>
        <Menu className="size-5" />
      </Button>
      <OrgSwitcher ctx={ctx} />
      <ModuleSwitcher ctx={ctx} />
      <div className="ml-auto flex items-center gap-1 sm:gap-2">
        <GlobalSearch ctx={ctx} />
        <Notifications org={ctx.org.slug} />
        <UserMenu />
      </div>
      <Sheet open={menu} onOpenChange={setMenu}>
        <SheetContent side="left" className="w-72 p-0">
          <SheetTitle className="flex h-14 items-center gap-2 border-b px-4 text-sm">
            <span className="grid size-7 place-items-center rounded-md bg-primary text-xs font-bold text-primary-foreground">BP</span>
            Business Platform
          </SheetTitle>
          <SidebarNav ctx={ctx} onNavigate={() => setMenu(false)} />
        </SheetContent>
      </Sheet>
    </header>
  );
}
